package com.locusmimic.app.manager.ui.map

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.locusmimic.app.R
import com.locusmimic.app.data.model.FavoriteLocation
import com.locusmimic.app.manager.App
import com.locusmimic.app.manager.ui.map.components.MapViewContainer
import com.locusmimic.app.manager.ui.favorites.FavoritesViewModel
import com.locusmimic.app.manager.ui.settings.LocationMode
import com.locusmimic.app.manager.ui.settings.SettingsBottomSheet
import com.locusmimic.app.manager.ui.settings.SettingsViewModel
import com.locusmimic.app.manager.ui.targetapps.TargetAppsBottomSheet
import com.locusmimic.app.BuildConfig
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// The HTML prototype uses solid, quiet surfaces. Keeping map controls fully opaque also avoids
// Web-map tile seams becoming visible through Compose overlays.
private val MapOverlaySurfaceColor = Color(0xFFFFFFFF)
private val MapOverlayBorderColor = Color(0xFFD8E7EB)
// Top controls sit directly on the map: use a quiet translucent surface and a hairline border
// instead of an elevation shadow, so the map remains the visual ground plane.
private val MapTopControlSurfaceColor = Color(0xF2FFFFFF)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    navController: NavController,
    mapViewModel: MapViewModel
) {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by mapViewModel.uiState.collectAsStateWithLifecycle()
    val xposedService by App.serviceState.collectAsStateWithLifecycle()
    val favoritesViewModel: FavoritesViewModel = viewModel()
    val favorites by favoritesViewModel.favorites.collectAsStateWithLifecycle()
    val settingsViewModel: SettingsViewModel = viewModel()
    val locationMode by settingsViewModel.locationMode.collectAsStateWithLifecycle()
    val isPlaying = uiState.isPlaying
    val isFabClickable = uiState.isFabClickable
    val showGoToPointDialog = uiState.goToPointDialogState == DialogState.Visible
    val showAddToFavoritesDialog = uiState.addToFavoritesDialogState == DialogState.Visible
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showFavoritesPanel by remember { mutableStateOf(false) }
    var favoritePendingDeletion by remember { mutableStateOf<FavoriteLocation?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSponsorSheet by remember { mutableStateOf(false) }
    var showTargetAppsSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    val fakeLocationSet = stringResource(R.string.toast_fake_location_set)
    val fakeLocationUnset = stringResource(R.string.toast_unset_fake_location)
    val dismissSearch = {
        focusManager.clearFocus(force = true)
        mapViewModel.clearPlaceSearch()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFFF4FAF8),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                MapViewContainer(
                    mapViewModel,
                    onMapInteraction = {
                        dismissSearch()
                        showOptionsMenu = false
                        showFavoritesPanel = false
                    }
                )
                // Visual-only readability layer. It intentionally has no pointer input, so map
                // pan and zoom gestures continue through this overlay to the WebView map.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(360.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x30F7FDFD),
                                    Color(0xD8F7FDFD),
                                    Color(0xFFF7FDFD)
                                )
                            )
                        )
                )
                MapTopControls(
                    query = uiState.placeSearchQuery,
                    isLoading = uiState.isPlaceSearchLoading,
                    results = uiState.placeSearchResults,
                    errorMessageRes = uiState.placeSearchErrorMessageRes,
                    onQueryChange = mapViewModel::updatePlaceSearchQuery,
                    onSearch = mapViewModel::searchPlace,
                    onClear = mapViewModel::clearPlaceSearch,
                    onResultClick = mapViewModel::selectPlaceSearchResult,
                    showOptionsMenu = showOptionsMenu,
                    onShowOptionsMenuChange = { showOptionsMenu = it },
                    onCenterMap = mapViewModel::triggerCenterMapEvent,
                    onGoToPoint = mapViewModel::showGoToPointDialog,
                    onAddFavorite = mapViewModel::showAddToFavoritesDialog,
                    favorites = favorites,
                    showFavoritesPanel = showFavoritesPanel,
                    onShowFavoritesPanelChange = { showFavoritesPanel = it },
                    onSelectFavorite = { favorite ->
                        favoritesViewModel.selectFavorite(favorite)
                        showFavoritesPanel = false
                    },
                    onRemoveFavorite = { favorite -> favoritePendingDeletion = favorite },
                    onOpenTargetApps = { showTargetAppsSheet = true },
                    onOpenSettings = { showSettingsSheet = true },
                    onClearLocation = { mapViewModel.updateClickedLocation(null) },
                    clearLocationEnabled = isFabClickable,
                    onShowAbout = { showAboutDialog = true },
                    onDismissSearch = dismissSearch,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(start = 18.dp, top = 0.dp, end = 18.dp)
                )
                if (
                    xposedService == null &&
                    locationMode != LocationMode.MOCK_PROVIDER &&
                    !showOptionsMenu &&
                    !showFavoritesPanel &&
                    uiState.placeSearchResults.isEmpty() &&
                    uiState.placeSearchErrorMessageRes == null
                ) {
                    ModuleInactiveBanner(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(start = 18.dp, top = 72.dp, end = 18.dp)
                    )
                }
                if (uiState.placeSearchResults.isEmpty() && uiState.placeSearchErrorMessageRes == null) {
                    MapLocationInfo(
                        lastClickedLocation = uiState.lastClickedLocation,
                        selectedLocationAddress = uiState.selectedLocationAddress,
                        selectedLocationPoiTitle = uiState.selectedLocationPoiTitle,
                        isAddressLoading = uiState.isSelectedLocationAddressLoading,
                        addressMessageRes = uiState.selectedLocationAddressMessageRes,
                        isPlaying = isPlaying,
                        modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 24.dp, end = 24.dp, bottom = 164.dp)
                    )
                }
                MapPlayButton(
                    isPlaying = isPlaying,
                    enabled = isFabClickable,
                    onClick = {
                        dismissSearch()
                        val wasPlaying = uiState.isPlaying
                        mapViewModel.togglePlaying()
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (!wasPlaying) fakeLocationSet else fakeLocationUnset
                            )
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 30.dp)
                )
                MapTargetAppsButton(
                    onClick = { showTargetAppsSheet = true },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 42.dp, bottom = 47.dp)
                )
                MapMyLocationButton(
                    onClick = {
                        dismissSearch()
                        mapViewModel.triggerCenterMapEvent()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 42.dp, bottom = 47.dp)
                )
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 159.dp),
                    snackbar = { data ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xEE173E4D),
                            contentColor = Color.White,
                            shadowElevation = 8.dp
                        ) {
                            Text(
                                text = data.visuals.message,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
                            )
                        }
                    }
                )
            }
        }

        if (showGoToPointDialog) {
            GoToPointBottomSheet(
                mapViewModel = mapViewModel,
                onDismissRequest = mapViewModel::hideGoToPointDialog
            )
        }

        if (showAddToFavoritesDialog) {
            val lastClickedLocation = uiState.lastClickedLocation

            LaunchedEffect(lastClickedLocation) {
                mapViewModel.prefillCoordinatesFromMarker(
                    lastClickedLocation?.latitude,
                    lastClickedLocation?.longitude
                )
            }

            AddToFavoritesBottomSheet(
                mapViewModel = mapViewModel,
                onDismissRequest = { mapViewModel.hideAddToFavoritesDialog() },
                onAddFavorite = { name, address, latitude, longitude ->
                    val favorite = FavoriteLocation(name, latitude, longitude, address)
                    mapViewModel.addFavoriteLocation(favorite)
                    mapViewModel.hideAddToFavoritesDialog()
                }
            )
        }

    if (showAboutDialog) {
        LocusMimicAboutSheet(
            onDismissRequest = { showAboutDialog = false },
            onShowSponsor = {
                showAboutDialog = false
                showSponsorSheet = true
            }
        )
    }
    if (showSponsorSheet) {
        LocusMimicSponsorSheet(onDismissRequest = { showSponsorSheet = false })
    }
    if (showTargetAppsSheet) {
        TargetAppsBottomSheet(onDismissRequest = { showTargetAppsSheet = false })
    }
    if (showSettingsSheet) {
        SettingsBottomSheet(onDismissRequest = { showSettingsSheet = false })
    }
    favoritePendingDeletion?.let { favorite ->
        AlertDialog(
            onDismissRequest = { favoritePendingDeletion = null },
            title = { Text(stringResource(R.string.favorites_delete_title)) },
            text = { Text(stringResource(R.string.favorites_delete_message, favorite.name)) },
            confirmButton = {
                TextButton(onClick = {
                    favoritesViewModel.removeFavorite(favorite)
                    favoritePendingDeletion = null
                }) {
                    Text(stringResource(R.string.action_delete), color = Color(0xFFC13B42))
                }
            },
            dismissButton = {
                TextButton(onClick = { favoritePendingDeletion = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun ModuleInactiveBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE67C68), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFFFE8E3),
        contentColor = Color(0xFF8B2E20),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = stringResource(R.string.module_inactive_banner),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MapTopControls(
    query: String,
    isLoading: Boolean,
    results: List<PlaceSearchResult>,
    errorMessageRes: Int?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onResultClick: (PlaceSearchResult) -> Unit,
    showOptionsMenu: Boolean,
    onShowOptionsMenuChange: (Boolean) -> Unit,
    onCenterMap: () -> Unit,
    onGoToPoint: () -> Unit,
    onAddFavorite: () -> Unit,
    favorites: List<FavoriteLocation>,
    showFavoritesPanel: Boolean,
    onShowFavoritesPanelChange: (Boolean) -> Unit,
    onSelectFavorite: (FavoriteLocation) -> Unit,
    onRemoveFavorite: (FavoriteLocation) -> Unit,
    onOpenTargetApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearLocation: () -> Unit,
    clearLocationEnabled: Boolean,
    onShowAbout: () -> Unit,
    onDismissSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        MapSearchPanel(
            query = query,
            isLoading = isLoading,
            results = results,
            errorMessageRes = errorMessageRes,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onClear = onClear,
            onResultClick = onResultClick,
            modifier = Modifier.fillMaxWidth(),
            searchFieldModifier = Modifier
                .fillMaxWidth()
                .padding(end = 132.dp)
        )

        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = MapTopControlSurfaceColor,
                contentColor = Color(0xFF174150),
                border = androidx.compose.foundation.BorderStroke(1.dp, MapOverlayBorderColor)
            ) {
                IconButton(onClick = {
                    onDismissSearch()
                    onShowOptionsMenuChange(false)
                    onShowFavoritesPanelChange(!showFavoritesPanel)
                }) {
                    Icon(Icons.Outlined.StarBorder, contentDescription = stringResource(R.string.screen_favorites))
                }
            }
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = MapTopControlSurfaceColor,
                contentColor = Color(0xFF174150),
                border = androidx.compose.foundation.BorderStroke(1.dp, MapOverlayBorderColor)
            ) {
                IconButton(onClick = {
                    onDismissSearch()
                    onShowOptionsMenuChange(true)
                }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.cd_options),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        if (showOptionsMenu) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 68.dp)
                    .width(194.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                contentColor = Color(0xFF274552),
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    MapOptionsMenuItem(
                        icon = PrototypeMenuIcon.GoToCoordinate,
                        label = stringResource(R.string.map_go_to_point)
                    ) { onShowOptionsMenuChange(false); onGoToPoint() }
                    MapOptionsMenuItem(
                        icon = PrototypeMenuIcon.AddFavorite,
                        label = stringResource(R.string.map_add_to_favorites)
                    ) { onShowOptionsMenuChange(false); onAddFavorite() }
                    MapOptionsMenuItem(
                        icon = PrototypeMenuIcon.ClearLocation,
                        label = stringResource(R.string.map_clear_location),
                        enabled = clearLocationEnabled
                    ) { onShowOptionsMenuChange(false); onClearLocation() }
                    MapOptionsMenuItem(
                        icon = PrototypeMenuIcon.Settings,
                        label = stringResource(R.string.screen_settings)
                    ) { onShowOptionsMenuChange(false); onOpenSettings() }
                    MapOptionsMenuItem(
                        icon = PrototypeMenuIcon.About,
                        label = stringResource(R.string.screen_about)
                    ) { onShowOptionsMenuChange(false); onShowAbout() }
                }
            }
        }
        if (showFavoritesPanel) {
            FavoriteLocationsPanel(
                favorites = favorites,
                onSelectFavorite = onSelectFavorite,
                onRemoveFavorite = onRemoveFavorite,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 66.dp)
            )
        }
    }
}

@Composable
private fun MapOptionsMenuItem(
    icon: PrototypeMenuIcon,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        PrototypeMenuIcon(icon, if (enabled) Color(0xFF65808A) else Color(0xFFB3C1C5), Modifier.size(25.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Color(0xFF274552) else Color(0xFF9AA9AE)
        )
    }
}

private enum class PrototypeMenuIcon { GoToCoordinate, AddFavorite, ClearLocation, Settings, About }

/** Small outlined glyphs redrawn for the prototype instead of reusing mixed Material icons. */
@Composable
private fun PrototypeMenuIcon(icon: PrototypeMenuIcon, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension / 24f
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        val stroke = Stroke(width = 1.7f * unit)
        when (icon) {
            PrototypeMenuIcon.GoToCoordinate -> {
                drawCircle(color, radius = 8.6f * unit, center = point(12f, 12f), style = stroke)
                drawLine(color, point(5.5f, 12f), point(17.5f, 12f), strokeWidth = 1.7f * unit)
                drawLine(color, point(14f, 8.5f), point(17.5f, 12f), strokeWidth = 1.7f * unit)
                drawLine(color, point(14f, 15.5f), point(17.5f, 12f), strokeWidth = 1.7f * unit)
            }
            PrototypeMenuIcon.AddFavorite -> {
                val star = Path().apply {
                    moveTo(12f * unit, 3.3f * unit)
                    lineTo(14.6f * unit, 8.7f * unit)
                    lineTo(20.5f * unit, 9.5f * unit)
                    lineTo(16.2f * unit, 13.6f * unit)
                    lineTo(17.2f * unit, 19.4f * unit)
                    lineTo(12f * unit, 16.7f * unit)
                    lineTo(6.8f * unit, 19.4f * unit)
                    lineTo(7.8f * unit, 13.6f * unit)
                    lineTo(3.5f * unit, 9.5f * unit)
                    lineTo(9.4f * unit, 8.7f * unit)
                    close()
                }
                drawPath(star, color, style = stroke)
                drawLine(color, point(19.5f, 17.5f), point(19.5f, 22f), strokeWidth = 1.7f * unit)
                drawLine(color, point(17.3f, 19.7f), point(21.7f, 19.7f), strokeWidth = 1.7f * unit)
            }
            PrototypeMenuIcon.ClearLocation -> {
                drawCircle(color, radius = 8.6f * unit, center = point(12f, 12f), style = stroke)
                drawLine(color, point(8.5f, 8.5f), point(15.5f, 15.5f), strokeWidth = 1.7f * unit)
                drawLine(color, point(15.5f, 8.5f), point(8.5f, 15.5f), strokeWidth = 1.7f * unit)
            }
            PrototypeMenuIcon.Settings -> {
                listOf(6f to 9f, 12f to 15f, 18f to 10f).forEach { (y, x) ->
                    drawLine(color, point(4f, y), point(20f, y), strokeWidth = 1.5f * unit)
                    drawCircle(color, radius = 2.1f * unit, center = point(x, y), style = stroke)
                }
            }
            PrototypeMenuIcon.About -> {
                drawCircle(color, radius = 8.6f * unit, center = point(12f, 12f), style = stroke)
                drawCircle(color, radius = 1.05f * unit, center = point(12f, 7.8f))
                drawLine(color, point(12f, 10.7f), point(12f, 16.7f), strokeWidth = 1.7f * unit)
            }
        }
    }
}

/** Inline favourite list matching the prototype, backed by the actual saved locations. */
@Composable
private fun FavoriteLocationsPanel(
    favorites: List<FavoriteLocation>,
    onSelectFavorite: (FavoriteLocation) -> Unit,
    onRemoveFavorite: (FavoriteLocation) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(320.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFFFFFFF),
        contentColor = Color(0xFF264653),
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MapOverlayBorderColor)
    ) {
        Column(modifier = Modifier.padding(9.dp)) {
            Text(
                text = stringResource(R.string.screen_favorites),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6C858E),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
            if (favorites.isEmpty()) {
                Text(
                    text = stringResource(R.string.favorites_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF789099),
                    modifier = Modifier.padding(10.dp)
                )
            } else {
                favorites.forEach { favorite ->
                    FavoriteLocationRow(
                        favorite = favorite,
                        onSelect = onSelectFavorite,
                        onRemove = onRemoveFavorite
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteLocationRow(
    favorite: FavoriteLocation,
    onSelect: (FavoriteLocation) -> Unit,
    onRemove: (FavoriteLocation) -> Unit
) {
    val rowShape = RoundedCornerShape(14.dp)
    val deleteWidth = 76.dp
    val density = LocalDensity.current
    val deleteWidthPx = with(density) { deleteWidth.toPx() }
    var deleteVisible by remember(favorite) { mutableStateOf(false) }
    var isDragging by remember(favorite) { mutableStateOf(false) }
    var dragOffset by remember(favorite) { mutableStateOf(0f) }
    val targetOffset = if (isDragging) {
        dragOffset
    } else if (deleteVisible) {
        -deleteWidthPx
    } else {
        0f
    }
    val foregroundOffset by animateFloatAsState(
        targetValue = targetOffset,
        // While dragging, snap to each pointer position. Once released, continue from that
        // exact position to the settled state instead of jumping back before animating.
        animationSpec = if (isDragging) snap() else tween(220, easing = FastOutSlowInEasing),
        label = "favoriteDeleteReveal"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .height(68.dp)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0xFFC13B42))
                .clickable { onRemove(favorite) },
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = stringResource(R.string.action_delete),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(deleteWidth)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .offset { IntOffset(foregroundOffset.roundToInt(), 0) }
                .background(Color.White)
                .pointerInput(favorite, deleteWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                            dragOffset = foregroundOffset
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            dragOffset = (dragOffset + dragAmount).coerceIn(-deleteWidthPx, 0f)
                            change.consume()
                        },
                        onDragEnd = {
                            deleteVisible = dragOffset <= -deleteWidthPx / 2f
                            isDragging = false
                        },
                        onDragCancel = { isDragging = false }
                    )
                }
                .clickable {
                    if (deleteVisible) deleteVisible = false else onSelect(favorite)
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(31.dp),
                shape = CircleShape,
                color = Color(0xFFD9F6F4),
                contentColor = Color(0xFF139F9B)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(17.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = favorite.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = favorite.address.ifBlank {
                        stringResource(R.string.coordinates_lat_lon, favorite.latitude, favorite.longitude)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5F7780),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (favorite.address.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.coordinates_lat_lon, favorite.latitude, favorite.longitude),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF789099),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MapMyLocationButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        MapSideActionButton(MapSideAction.MyLocation, stringResource(R.string.cd_center), onClick)
        Text("我的位置", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF58727D), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun MapTargetAppsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        MapSideActionButton(MapSideAction.TargetApps, stringResource(R.string.screen_target_apps), onClick)
        Text(stringResource(R.string.screen_target_apps), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF58727D), modifier = Modifier.padding(top = 4.dp))
    }
}

private enum class MapSideAction { TargetApps, MyLocation }

@Composable
private fun MapSideActionButton(
    type: MapSideAction,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(49.dp),
        shape = CircleShape,
        color = Color.White,
        contentColor = Color(0xFF174150),
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize().clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(22.dp)) {
                val unit = size.minDimension / 24f
                fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
                val stroke = Stroke(width = 1.8f * unit)
                when (type) {
                    MapSideAction.TargetApps -> {
                        listOf(5f to 5f, 14f to 5f, 5f to 14f, 14f to 14f).forEach { (x, y) ->
                            drawRoundRect(
                                color = Color(0xFF174150),
                                topLeft = point(x, y),
                                size = androidx.compose.ui.geometry.Size(5f * unit, 5f * unit),
                                cornerRadius = CornerRadius(1f * unit),
                                style = stroke
                            )
                        }
                    }
                    MapSideAction.MyLocation -> {
                        drawCircle(Color(0xFF174150), radius = 6.2f * unit, center = point(12f, 12f), style = stroke)
                        drawCircle(Color(0xFF174150), radius = 1.7f * unit, center = point(12f, 12f))
                        drawLine(Color(0xFF174150), point(12f, 2f), point(12f, 5f), strokeWidth = 1.6f * unit)
                        drawLine(Color(0xFF174150), point(12f, 19f), point(12f, 22f), strokeWidth = 1.6f * unit)
                        drawLine(Color(0xFF174150), point(2f, 12f), point(5f, 12f), strokeWidth = 1.6f * unit)
                        drawLine(Color(0xFF174150), point(19f, 12f), point(22f, 12f), strokeWidth = 1.6f * unit)
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GoToPointBottomSheet(
    mapViewModel: MapViewModel,
    onDismissRequest: () -> Unit
) {
    val uiState by mapViewModel.uiState.collectAsStateWithLifecycle()
    val (latitude, longitude) = uiState.goToPointState
    HtmlModalSheet(onDismissRequest, stringResource(R.string.map_go_to_point)) {
        Text("WGS-84 坐标", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF6A858D), modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        PrototypeField(latitude.value, { mapViewModel.updateGoToPointField("latitude", it) }, stringResource(R.string.field_latitude), latitude.errorMessageRes)
        PrototypeField(longitude.value, { mapViewModel.updateGoToPointField("longitude", it) }, stringResource(R.string.field_longitude), longitude.errorMessageRes)
        PrototypePrimaryButton(stringResource(R.string.action_go)) {
            mapViewModel.validateAndGo { lat, lon ->
                mapViewModel.goToPoint(lat, lon)
                mapViewModel.hideGoToPointDialog()
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AddToFavoritesBottomSheet(
    mapViewModel: MapViewModel,
    onDismissRequest: () -> Unit,
    onAddFavorite: (name: String, address: String, latitude: Double, longitude: Double) -> Unit
) {
    val uiState by mapViewModel.uiState.collectAsStateWithLifecycle()
    val fields = uiState.addToFavoritesState
    HtmlModalSheet(onDismissRequest, stringResource(R.string.map_add_to_favorites)) {
        Text("收藏信息", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF6A858D), modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        PrototypeField(fields.name.value, { mapViewModel.updateAddToFavoritesField("name", it) }, stringResource(R.string.field_name), fields.name.errorMessageRes)
        PrototypeField(fields.latitude.value, { mapViewModel.updateAddToFavoritesField("latitude", it) }, stringResource(R.string.field_latitude), fields.latitude.errorMessageRes)
        PrototypeField(fields.longitude.value, { mapViewModel.updateAddToFavoritesField("longitude", it) }, stringResource(R.string.field_longitude), fields.longitude.errorMessageRes)
        PrototypePrimaryButton(stringResource(R.string.map_add_to_favorites)) {
            mapViewModel.validateAndAddFavorite { name, latitude, longitude ->
                onAddFavorite(name, uiState.selectedLocationAddress.orEmpty(), latitude, longitude)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LocusMimicAboutSheet(
    onDismissRequest: () -> Unit,
    onShowSponsor: () -> Unit
) {
    HtmlModalSheet(onDismissRequest, stringResource(R.string.screen_about)) {
        Surface(
            modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFE3F7FC)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(64.dp)
            )
        }
        Text(
            text = "${stringResource(R.string.app_name)} · LocusMimic",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF264653),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
        Text(
            text = "${stringResource(R.string.about_version_label)} ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF607981),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 18.dp)
        )
        Text(
            text = stringResource(R.string.about_dialog_summary),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
            color = Color(0xFF607981)
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
                .height(54.dp)
                .clickable(onClick = onShowSponsor),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFE8F6EE),
            contentColor = Color(0xFF227A4A)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(stringResource(R.string.sponsor_entry_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.sponsor_entry_summary), style = MaterialTheme.typography.bodySmall, color = Color(0xFF5C8570))
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LocusMimicSponsorSheet(onDismissRequest: () -> Unit) {
    HtmlModalSheet(onDismissRequest, stringResource(R.string.sponsor_title)) {
        Text(
            text = stringResource(R.string.sponsor_dialog_summary),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
            color = Color(0xFF607981)
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .padding(top = 18.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White
        ) {
            Image(
                painter = painterResource(R.drawable.wechat_sponsor_qr),
                contentDescription = stringResource(R.string.sponsor_wechat_qr),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(10.dp)
            )
        }
        Text(
            text = stringResource(R.string.sponsor_disclaimer),
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
            color = Color(0xFF71878E),
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HtmlModalSheet(
    onDismissRequest: () -> Unit,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color(0xFFF5FBFC),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 28.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF203F4C), modifier = Modifier.weight(1f))
                Surface(modifier = Modifier.size(32.dp), shape = CircleShape, color = Color(0xFFE4F0F3), contentColor = Color(0xFF55717C)) {
                    IconButton(onClick = onDismissRequest) { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cd_back)) }
                }
            }
            content()
        }
    }
}

@Composable
private fun PrototypeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    errorMessageRes: Int?
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = errorMessageRes != null,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedBorderColor = Color(0xFFD9E8EB),
            unfocusedBorderColor = Color(0xFFD9E8EB)
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
    )
    errorMessageRes?.let {
        Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 14.dp, bottom = 8.dp))
    }
}

@Composable
private fun PrototypePrimaryButton(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF237FE3),
        contentColor = Color.White,
        shadowElevation = 5.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MapSearchPanel(
    query: String,
    isLoading: Boolean,
    results: List<PlaceSearchResult>,
    errorMessageRes: Int?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onResultClick: (PlaceSearchResult) -> Unit,
    modifier: Modifier = Modifier,
    searchFieldModifier: Modifier = Modifier
) {
    var isSearchFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = searchFieldModifier
                .height(58.dp)
                .onFocusChanged { isSearchFocused = it.isFocused },
            singleLine = true,
            shape = RoundedCornerShape(29.dp),
            placeholder = { Text(stringResource(R.string.map_search_placeholder)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.cd_search_place),
                    modifier = Modifier.size(26.dp),
                    tint = Color(0xFF5D6B68)
                )
            },
            trailingIcon = {
                when {
                    isLoading -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )

                    query.isNotEmpty() -> IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.cd_clear_search)
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MapTopControlSurfaceColor,
                unfocusedContainerColor = MapTopControlSurfaceColor,
                focusedBorderColor = MapOverlayBorderColor,
                unfocusedBorderColor = MapOverlayBorderColor,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )

        if (isSearchFocused && (results.isNotEmpty() || errorMessageRes != null)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp
            ) {
                if (results.isNotEmpty()) {
                    Column {
                        results.forEach { result ->
                            PlaceSearchResultRow(result = result, onClick = { onResultClick(result) })
                        }
                    }
                } else if (errorMessageRes != null) {
                    Text(
                        text = stringResource(errorMessageRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MapPlayButton(
    isPlaying: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = CircleShape
    val background = if (enabled) {
        Brush.linearGradient(
            if (isPlaying) listOf(Color(0xFFFF5B76), Color(0xFFE51F48))
            else listOf(Color(0xFF14B9F4), Color(0xFF8751DE))
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0xFFDDE6E3),
                Color(0xFFC7D3D0)
            )
        )
    }
    val outerRingColor = when {
        !enabled -> Color(0x4D96AAA9)
        isPlaying -> Color(0x70FF5B76)
        else -> Color(0x7053D6F6)
    }
    Box(
        modifier = modifier
            .size(122.dp)
            .border(
                1.dp,
                outerRingColor,
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .shadow(18.dp, shape, clip = false)
                .clip(shape)
                .background(background)
                .border(1.dp, Color.White.copy(alpha = 0.65f), CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isPlaying) "停止模拟" else "开始模拟",
                style = MaterialTheme.typography.titleMedium,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color.White else Color(0xFF73817D),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PlaceSearchResultRow(
    result: PlaceSearchResult,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(
            text = result.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = result.address,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(
                R.string.map_status_coordinates,
                result.latitude,
                result.longitude
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MapLocationInfo(
    lastClickedLocation: GeoPoint?,
    selectedLocationAddress: String?,
    selectedLocationPoiTitle: String?,
    isAddressLoading: Boolean,
    addressMessageRes: Int?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val title = when {
        lastClickedLocation == null -> stringResource(R.string.map_status_waiting)
        !selectedLocationPoiTitle.isNullOrBlank() -> selectedLocationPoiTitle
        !selectedLocationAddress.isNullOrBlank() -> selectedLocationAddress.toAddressLeafLabel()
        isAddressLoading -> stringResource(R.string.map_status_address_loading)
        addressMessageRes != null -> stringResource(addressMessageRes)
        else -> stringResource(R.string.map_status_address_loading)
    }
    val address = selectedLocationAddress?.takeIf { it.isNotBlank() }
    val coordinates = lastClickedLocation?.let {
        stringResource(R.string.map_status_coordinates, it.latitude, it.longitude)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isPlaying) stringResource(R.string.map_status_active) else "已选择位置",
            style = MaterialTheme.typography.labelSmall,
            color = if (isPlaying) Color(0xFFF14E66) else Color(0xFF168F8A),
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF183B49),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        address?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF69838D), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        coordinates?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8097A0), maxLines = 1)
        }
    }
}

@Composable
private fun MapStatusPanel(
    lastClickedLocation: GeoPoint?,
    selectedLocationAddress: String?,
    selectedLocationPoiTitle: String?,
    isAddressLoading: Boolean,
    addressMessageRes: Int?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val title = when {
        lastClickedLocation != null -> selectedLocationPoiTitle?.takeIf { it.isNotBlank() }
            ?: selectedLocationAddress?.takeIf { it.isNotBlank() }?.toAddressLeafLabel()
            // Reverse geocoding is asynchronous. Keep the title aligned with the current
            // simulation state while waiting instead of flashing a generic selection label.
            ?: stringResource(
                if (isPlaying) R.string.map_status_active else R.string.map_status_address_loading
            )
        else -> stringResource(R.string.map_status_waiting)
    }
    val coordinatesText = if (lastClickedLocation != null) {
        stringResource(
            R.string.map_status_coordinates,
            lastClickedLocation.latitude,
            lastClickedLocation.longitude
        )
    } else {
        stringResource(R.string.map_status_hint)
    }
    val addressText = when {
        lastClickedLocation == null -> null
        selectedLocationAddress?.isNotBlank() == true -> {
            stringResource(R.string.map_status_address, selectedLocationAddress)
        }
        isAddressLoading -> stringResource(R.string.map_status_address_loading)
        addressMessageRes != null -> stringResource(addressMessageRes)
        else -> null
    }
    // This is the map's only persistent information surface. Match the bottom navigation's
    // cool-grey translucent fill so the map remains visible beneath the rounded card.
    val accentColor = if (isPlaying) Color(0xFFFF4269) else Color(0xFF12A9ED)
    val statusLabel = stringResource(
        if (isPlaying) R.string.map_status_active else R.string.map_status_stopped
    )
    val supportingText = if (isPlaying) {
        selectedLocationAddress?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.map_status_active_hint)
    } else {
        addressText
    }

    val panelShape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, panelShape, clip = false)
            .clip(panelShape)
            .background(MapOverlaySurfaceColor)
            .border(1.dp, MapOverlayBorderColor, panelShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(24.dp),
                color = accentColor.copy(alpha = 0.12f),
                contentColor = accentColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(29.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF123C3A),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accentColor.copy(alpha = 0.12f),
                        contentColor = accentColor
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = coordinatesText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5B7370),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5B7370),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** The POI-formatted address is stored as the final segment after the administrative prefix. */
private fun String.toAddressLeafLabel(): String {
    val trimmed = trim()
    return trimmed.substringAfterLast(" · ").trim().ifBlank { trimmed }
}
