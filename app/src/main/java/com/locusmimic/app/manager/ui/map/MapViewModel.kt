package com.locusmimic.app.manager.ui.map

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.locusmimic.app.R
import com.locusmimic.app.data.model.FavoriteLocation
import com.locusmimic.app.data.repository.PreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Sealed classes to represent different dialog states
 */
sealed class DialogState {
    object Hidden : DialogState()
    object Visible : DialogState()
}

/**
 * Sealed class to represent different loading states
 */
sealed class LoadingState {
    object Loading : LoadingState()
    object Loaded : LoadingState()
}

data class PlaceSearchResult(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * ViewModel for the Map screen that manages map-related state and operations.
 */
class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesRepository = PreferencesRepository(application)
    private var activeSearchQuery: String? = null
    private var activeReverseLocation: GeoPoint? = null
    private var reverseGeocodeJob: Job? = null
    private var mapActive = false

    /**
     * Represents field input state with value and validation error message
     */
    data class InputFieldState(val value: String = "", @StringRes val errorMessageRes: Int? = null)

    /**
     * Represents the UI state for the favorites input dialog
     */
    data class FavoritesInputState(
        val name: InputFieldState = InputFieldState(),
        val latitude: InputFieldState = InputFieldState(),
        val longitude: InputFieldState = InputFieldState()
    )

    /**
     * Represents the complete UI state for the Map screen
     */
    data class MapUiState(
        val isPlaying: Boolean = false,
        val lastClickedLocation: GeoPoint? = null,
        val userLocation: GeoPoint? = null,
        val loadingState: LoadingState = LoadingState.Loading,
        val mapZoom: Double? = null,
        val goToPointDialogState: DialogState = DialogState.Hidden,
        val addToFavoritesState: FavoritesInputState = FavoritesInputState(),
        val addToFavoritesDialogState: DialogState = DialogState.Hidden,
        val goToPointState: Pair<InputFieldState, InputFieldState> = InputFieldState() to InputFieldState(),
        val placeSearchQuery: String = "",
        val isPlaceSearchLoading: Boolean = false,
        val placeSearchResults: List<PlaceSearchResult> = emptyList(),
        @StringRes val placeSearchErrorMessageRes: Int? = null,
        val selectedLocationAddress: String? = null,
        // Stored independently from the formatted address because providers can return a
        // separator-free full address that cannot be safely shortened by string parsing.
        val selectedLocationPoiTitle: String? = null,
        val isSelectedLocationAddressLoading: Boolean = false,
        @StringRes val selectedLocationAddressMessageRes: Int? = null,
    ) {
        val isFabClickable: Boolean
            get() = lastClickedLocation != null
    }

    // Private mutable state
    private val _uiState = MutableStateFlow(MapUiState())

    // Public immutable state
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    // Events
    private val _goToPointEvent = MutableSharedFlow<GeoPoint>()
    val goToPointEvent: SharedFlow<GeoPoint> = _goToPointEvent.asSharedFlow()

    private val _centerMapEvent = MutableSharedFlow<Unit>()
    val centerMapEvent: SharedFlow<Unit> = _centerMapEvent.asSharedFlow()

    private val placeSearchRequests = Channel<String>(Channel.CONFLATED)
    val placeSearchEvent = placeSearchRequests.receiveAsFlow()

    private val reverseGeocodeRequests = Channel<GeoPoint>(Channel.CONFLATED)
    val reverseGeocodeEvent = reverseGeocodeRequests.receiveAsFlow()

    init {
        viewModelScope.launch {
            // Load initial isPlaying state
            preferencesRepository.getIsPlayingFlow().collectLatest { isPlaying ->
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }
        }

        viewModelScope.launch {
            // Load initial lastClickedLocation
            preferencesRepository.getLastClickedLocationFlow().collectLatest { location ->
                val geoPoint = location?.let { GeoPoint(it.latitude, it.longitude) }
                _uiState.update {
                    it.copy(
                        lastClickedLocation = geoPoint,
                        selectedLocationAddress = null,
                        selectedLocationPoiTitle = null,
                        isSelectedLocationAddressLoading = false,
                        selectedLocationAddressMessageRes = null
                    )
                }
                if (geoPoint != null && mapActive) {
                    resolveSelectedLocationAddress(geoPoint)
                }
            }
        }
    }

    fun togglePlaying() {
        val currentIsPlaying = !_uiState.value.isPlaying
        _uiState.update { it.copy(isPlaying = currentIsPlaying) }

        viewModelScope.launch {
            preferencesRepository.saveIsPlaying(currentIsPlaying)
        }
    }

    fun updateUserLocation(location: GeoPoint) {
        _uiState.update { it.copy(userLocation = location) }
    }

    fun onMapEntered() {
        mapActive = true
        _uiState.value.lastClickedLocation?.let(::resolveSelectedLocationAddress)
    }

    fun onMapExited() {
        mapActive = false
        activeSearchQuery = null
        activeReverseLocation = null
        reverseGeocodeJob?.cancel()
        reverseGeocodeJob = null
        while (placeSearchRequests.tryReceive().isSuccess) Unit
        while (reverseGeocodeRequests.tryReceive().isSuccess) Unit
        _uiState.update {
            it.copy(
                isPlaceSearchLoading = false,
                isSelectedLocationAddressLoading = false
            )
        }
    }

    fun updateClickedLocation(geoPoint: GeoPoint?) {
        _uiState.update {
            it.copy(
                lastClickedLocation = geoPoint,
                selectedLocationAddress = null,
                selectedLocationPoiTitle = null,
                isSelectedLocationAddressLoading = false,
                selectedLocationAddressMessageRes = null
            )
        }

        viewModelScope.launch {
            geoPoint?.let {
                preferencesRepository.saveLastClickedLocation(
                    it.latitude,
                    it.longitude
                )
            } ?: preferencesRepository.clearLastClickedLocation()
        }
    }

    fun addFavoriteLocation(favoriteLocation: FavoriteLocation) {
        viewModelScope.launch {
            preferencesRepository.addFavorite(favoriteLocation)
        }
    }

    // Update specific fields in the FavoritesInputState
    fun updateAddToFavoritesField(fieldName: String, newValue: String) {
        val currentState = _uiState.value.addToFavoritesState
        val errorMessageRes = when (fieldName) {
            "name" -> if (newValue.isBlank()) R.string.validation_name_required else null
            "latitude" -> validateInput(newValue, -90.0..90.0, R.string.validation_latitude_range)
            "longitude" -> validateInput(newValue, -180.0..180.0, R.string.validation_longitude_range)
            else -> null
        }

        val updatedState = when (fieldName) {
            "name" -> currentState.copy(name = currentState.name.copy(value = newValue, errorMessageRes = errorMessageRes))
            "latitude" -> currentState.copy(latitude = currentState.latitude.copy(value = newValue, errorMessageRes = errorMessageRes))
            "longitude" -> currentState.copy(longitude = currentState.longitude.copy(value = newValue, errorMessageRes = errorMessageRes))
            else -> currentState
        }

        _uiState.update { it.copy(addToFavoritesState = updatedState) }
    }

    // Go to point logic
    fun goToPoint(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            _goToPointEvent.emit(GeoPoint(latitude, longitude))
        }
    }

    fun updatePlaceSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                placeSearchQuery = query,
                placeSearchResults = emptyList(),
                placeSearchErrorMessageRes = null
            )
        }
    }

    fun clearPlaceSearch() {
        _uiState.update {
            it.copy(
                placeSearchQuery = "",
                placeSearchResults = emptyList(),
                placeSearchErrorMessageRes = null,
                isPlaceSearchLoading = false
            )
        }
    }

    fun searchPlace() {
        val requestedQuery = _uiState.value.placeSearchQuery.trim()
        if (requestedQuery.isBlank()) {
            _uiState.update {
                it.copy(
                    placeSearchResults = emptyList(),
                    placeSearchErrorMessageRes = R.string.map_search_empty
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isPlaceSearchLoading = true,
                placeSearchResults = emptyList(),
                placeSearchErrorMessageRes = null
            )
        }

        activeSearchQuery = requestedQuery
        if (placeSearchRequests.trySend(requestedQuery).isFailure) {
            _uiState.update {
                it.copy(
                    isPlaceSearchLoading = false,
                    placeSearchResults = emptyList(),
                    placeSearchErrorMessageRes = R.string.map_search_error
                )
            }
        }
    }

    fun onPlaceSearchCompleted(query: String, results: List<PlaceSearchResult>) {
        if (!mapActive || activeSearchQuery != query) return
        if (_uiState.value.placeSearchQuery.trim() != query) return

        val places = results
            .distinctBy { "${it.latitude}:${it.longitude}" }
            .take(PLACE_SEARCH_RESULT_LIMIT)
        _uiState.update {
            it.copy(
                isPlaceSearchLoading = false,
                placeSearchResults = places,
                placeSearchErrorMessageRes = if (places.isEmpty()) {
                    R.string.map_search_no_results
                } else {
                    null
                }
            )
        }
    }

    fun onPlaceSearchFailed(query: String) {
        if (!mapActive || activeSearchQuery != query) return
        _uiState.update {
            it.copy(
                isPlaceSearchLoading = false,
                placeSearchResults = emptyList(),
                placeSearchErrorMessageRes = R.string.map_search_error
            )
        }
    }

    fun selectPlaceSearchResult(result: PlaceSearchResult) {
        _uiState.update {
            it.copy(
                placeSearchQuery = result.name,
                placeSearchResults = emptyList(),
                placeSearchErrorMessageRes = null,
                isPlaceSearchLoading = false
            )
        }
        goToPoint(result.latitude, result.longitude)
    }

    // Update specific fields in the GoToPointDialog state
    fun updateGoToPointField(fieldName: String, newValue: String) {
        val (latitudeField, longitudeField) = _uiState.value.goToPointState
        val updatedGoToPointState = when (fieldName) {
            "latitude" -> latitudeField.copy(value = newValue) to longitudeField
            "longitude" -> latitudeField to longitudeField.copy(value = newValue)
            else -> latitudeField to longitudeField
        }

        _uiState.update { it.copy(goToPointState = updatedGoToPointState) }
    }

    // Center map
    fun triggerCenterMapEvent() {
        viewModelScope.launch {
            _centerMapEvent.emit(Unit)
        }
    }

    fun setLoadingStarted() {
        _uiState.update { it.copy(loadingState = LoadingState.Loading) }
    }

    // Set loading finished
    fun setLoadingFinished() {
        _uiState.update { it.copy(loadingState = LoadingState.Loaded) }
    }

    // Dialog show/hide logic
    fun showGoToPointDialog() {
        _uiState.update { it.copy(goToPointDialogState = DialogState.Visible) }
    }

    fun hideGoToPointDialog() {
        _uiState.update { it.copy(goToPointDialogState = DialogState.Hidden) }
        clearGoToPointInputs()
    }

    fun showAddToFavoritesDialog() {
        _uiState.update { state ->
            // Prefer the independently returned POI title over the full address. It is the
            // concise human-readable name already displayed as the selected location title.
            val poiName = state.selectedLocationPoiTitle.orEmpty()
            state.copy(
                addToFavoritesDialogState = DialogState.Visible,
                addToFavoritesState = state.addToFavoritesState.copy(
                    name = InputFieldState(value = poiName)
                )
            )
        }
    }

    fun hideAddToFavoritesDialog() {
        _uiState.update { it.copy(addToFavoritesDialogState = DialogState.Hidden) }
        clearAddToFavoritesInputs()
    }

    // Helper for input validation
    private fun validateInput(
        input: String, range: ClosedRange<Double>, @StringRes errorMessageRes: Int
    ): Int? {
        val value = input.toDoubleOrNull()
        return if (value == null || value !in range) errorMessageRes else null
    }

    // Validate GoToPoint inputs
    fun validateAndGo(onSuccess: (latitude: Double, longitude: Double) -> Unit) {
        val (latField, lonField) = _uiState.value.goToPointState
        val latitudeError = validateInput(latField.value, -90.0..90.0, R.string.validation_latitude_range)
        val longitudeError = validateInput(lonField.value, -180.0..180.0, R.string.validation_longitude_range)

        val updatedGoToPointState = latField.copy(errorMessageRes = latitudeError) to lonField.copy(errorMessageRes = longitudeError)
        _uiState.update { it.copy(goToPointState = updatedGoToPointState) }

        if (latitudeError == null && longitudeError == null) {
            onSuccess(latField.value.toDouble(), lonField.value.toDouble())
        }
    }

    // Clear GoToPoint inputs
    fun clearGoToPointInputs() {
        _uiState.update {
            it.copy(goToPointState = InputFieldState() to InputFieldState())
        }
    }

    // Prefill AddToFavorites latitude/longitude with marker values (if available)
    fun prefillCoordinatesFromMarker(latitude: Double?, longitude: Double?) {
        if (latitude != null && longitude != null) {
            val latField = InputFieldState(value = latitude.toString())
            val lngField = InputFieldState(value = longitude.toString())

            _uiState.update { currentState ->
                val favState = currentState.addToFavoritesState
                currentState.copy(
                    addToFavoritesState = favState.copy(
                        latitude = latField,
                        longitude = lngField
                    )
                )
            }
        }
    }

    // Validate and add favorite location
    fun validateAndAddFavorite(onSuccess: (name: String, latitude: Double, longitude: Double) -> Unit) {
        val currentState = _uiState.value.addToFavoritesState

        val latitudeError = validateInput(currentState.latitude.value, -90.0..90.0, R.string.validation_latitude_range)
        val longitudeError = validateInput(currentState.longitude.value, -180.0..180.0, R.string.validation_longitude_range)
        val nameError = if (currentState.name.value.isBlank()) R.string.validation_name_required else null

        val updatedState = currentState.copy(
            name = currentState.name.copy(errorMessageRes = nameError),
            latitude = currentState.latitude.copy(errorMessageRes = latitudeError),
            longitude = currentState.longitude.copy(errorMessageRes = longitudeError)
        )

        _uiState.update { it.copy(addToFavoritesState = updatedState) }

        if (nameError == null && latitudeError == null && longitudeError == null) {
            onSuccess(currentState.name.value, currentState.latitude.value.toDouble(), currentState.longitude.value.toDouble())
        }
    }

    // Clear AddToFavorites inputs
    fun clearAddToFavoritesInputs() {
        _uiState.update { it.copy(addToFavoritesState = FavoritesInputState()) }
    }

    // Update map zoom level
    fun updateMapZoom(zoom: Double) {
        _uiState.update { it.copy(mapZoom = zoom) }
    }

    private fun resolveSelectedLocationAddress(geoPoint: GeoPoint) {
        activeReverseLocation = geoPoint
        reverseGeocodeJob?.cancel()
        _uiState.update {
            it.copy(
                isSelectedLocationAddressLoading = true,
                selectedLocationAddress = null,
                selectedLocationPoiTitle = null,
                selectedLocationAddressMessageRes = null
            )
        }

        reverseGeocodeJob = viewModelScope.launch {
            // A quick series of map taps should resolve only the final coordinate, avoiding
            // redundant network requests and stale callbacks.
            delay(REVERSE_GEOCODE_DEBOUNCE_MS)
            if (!mapActive || activeReverseLocation != geoPoint) return@launch

            if (reverseGeocodeRequests.trySend(geoPoint).isFailure) {
                _uiState.update {
                    it.copy(
                        isSelectedLocationAddressLoading = false,
                        selectedLocationAddressMessageRes = R.string.map_status_address_unavailable
                    )
                }
            }
        }
    }

    fun onReverseGeocodeCompleted(
        location: GeoPoint,
        address: String?,
        poiTitle: String?
    ) {
        if (!mapActive || activeReverseLocation != location) return
        if (_uiState.value.lastClickedLocation != location) return

        val displayAddress = address?.trim()?.takeIf(String::isNotEmpty)
        _uiState.update {
            it.copy(
                isSelectedLocationAddressLoading = false,
                selectedLocationAddress = displayAddress,
                selectedLocationPoiTitle = poiTitle?.trim()?.takeIf(String::isNotEmpty),
                selectedLocationAddressMessageRes = if (displayAddress == null) {
                    R.string.map_status_address_unavailable
                } else {
                    null
                }
            )
        }
    }

    fun onReverseGeocodeFailed(location: GeoPoint) {
        if (!mapActive || activeReverseLocation != location) return
        if (_uiState.value.lastClickedLocation != location) return
        _uiState.update {
            it.copy(
                isSelectedLocationAddressLoading = false,
                selectedLocationAddress = null,
                selectedLocationPoiTitle = null,
                selectedLocationAddressMessageRes = R.string.map_status_address_unavailable
            )
        }
    }

    override fun onCleared() {
        onMapExited()
        placeSearchRequests.close()
        reverseGeocodeRequests.close()
        super.onCleared()
    }

    private companion object {
        const val PLACE_SEARCH_RESULT_LIMIT = 5
        const val REVERSE_GEOCODE_DEBOUNCE_MS = 400L
    }
}
