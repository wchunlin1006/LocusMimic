package com.locusmimic.app.manager.ui.targetapps

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.locusmimic.app.R
import com.locusmimic.app.data.MANAGER_APP_PACKAGE_NAME
import com.locusmimic.app.data.repository.PreferencesRepository
import com.locusmimic.app.manager.App
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TargetAppItem(
    val label: String,
    val packageName: String,
    val isSystemApp: Boolean = false,
    /** A virtual LSPosed scope entry, not an installable Android package. */
    val isScopeOnly: Boolean = false,
    val isSelected: Boolean = false,
    val isPending: Boolean = false,
    val isRelaunching: Boolean = false
)

data class TargetAppsUiState(
    val apps: List<TargetAppItem> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val pendingPackages: Set<String> = emptySet(),
    val relaunchingPackages: Set<String> = emptySet(),
    val searchQuery: String = "",
    val showSystemApps: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isModuleActive: Boolean = true
) {
    val filteredApps: List<TargetAppItem>
        get() {
            val query = searchQuery.trim()
            val searchableApps = apps.filter { showSystemApps || !it.isSystemApp }
            val visibleApps = if (query.isEmpty()) {
                searchableApps
            } else {
                searchableApps.filter {
                    it.label.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
                }
            }
            return visibleApps.sortedWith(
                // Use the scope set as the source of truth so selected applications remain at
                // the top even while an asynchronous scope refresh is updating row state.
                compareBy<TargetAppItem> { !selectedPackages.contains(it.packageName) }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
                    .thenBy { it.packageName }
            )
        }
}

/** One-shot messages surfaced to the UI (e.g. as a snackbar). */
sealed interface TargetAppsEvent {
    data object ModuleNotActive : TargetAppsEvent
    data class ScopeRequestFailed(val message: String) : TargetAppsEvent
    data class Relaunched(val appLabel: String) : TargetAppsEvent
    data class RelaunchFailed(val appLabel: String) : TargetAppsEvent
    data object RootRequired : TargetAppsEvent
}

/**
 * The LSPosed scope is the source of truth for which apps the module is injected into.
 * Tagging an app issues a [XposedService.requestScope] (asynchronous, may need approval);
 * the checkbox only turns on once the request is approved. Untagging calls
 * [XposedService.removeScope] immediately. The scope is mirrored into the [target_apps]
 * remote pref so the selection is also available to features that hook system framework
 * processes (where a target may be selected without itself being in scope).
 */
class TargetAppsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesRepository = PreferencesRepository(application)
    private val packageManager = application.packageManager

    private val _uiState = MutableStateFlow(TargetAppsUiState())
    val uiState: StateFlow<TargetAppsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TargetAppsEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<TargetAppsEvent> = _events.asSharedFlow()

    /**
     * Some ROMs show their "allow app list access" dialog only after the first PackageManager
     * query. That query can legitimately return an empty list. Keep only the newest request so
     * a result started before the user accepts the dialog cannot overwrite a retry result.
     */
    private var installedAppsLoadRequest = 0

    init {
        loadInstalledApps()
        observeService()
        observeSystemAppVisibility()
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setShowSystemApps(show: Boolean) {
        preferencesRepository.saveShowSystemApps(show)
    }

    fun refreshInstalledApps() {
        loadInstalledApps(isRefresh = true)
    }

    private fun observeSystemAppVisibility() {
        viewModelScope.launch {
            preferencesRepository.getShowSystemAppsFlow().collectLatest { show ->
                _uiState.update { it.copy(showSystemApps = show) }
            }
        }
    }

    fun toggleApp(packageName: String) {
        // `system` is LSPosed's virtual System Framework scope. It has no APK entry and
        // must be managed by LSPosed itself rather than through an app-list checkbox.
        if (_uiState.value.apps.any { it.packageName == packageName && it.isScopeOnly }) return
        if (_uiState.value.pendingPackages.contains(packageName)) return

        val service = App.service
        if (service == null) {
            _events.tryEmit(TargetAppsEvent.ModuleNotActive)
            return
        }

        if (_uiState.value.selectedPackages.contains(packageName)) {
            removeFromScope(service, packageName)
        } else {
            addToScope(service, packageName)
        }
    }

    /**
     * Force-stops the target app via root so the module reloads on next launch, then cold-starts
     * it again. Requires the user to grant root (su); failures (e.g. denied prompt) are reported.
     */
    fun relaunchApp(packageName: String) {
        if (_uiState.value.apps.any { it.packageName == packageName && it.isScopeOnly }) return
        if (_uiState.value.relaunchingPackages.contains(packageName)) return
        val label = _uiState.value.apps.firstOrNull { it.packageName == packageName }?.label ?: packageName

        setRelaunching(packageName, true)
        viewModelScope.launch {
            // Probing su both verifies root and triggers the manager's grant prompt if it was
            // never requested before. A denied prompt / missing su binary returns false.
            val hasRoot = withContext(Dispatchers.IO) { hasRootAccess() }
            if (!hasRoot) {
                setRelaunching(packageName, false)
                _events.tryEmit(TargetAppsEvent.RootRequired)
                return@launch
            }

            val killed = withContext(Dispatchers.IO) { runAsRoot("am force-stop $packageName") }
            if (!killed) {
                setRelaunching(packageName, false)
                _events.tryEmit(TargetAppsEvent.RelaunchFailed(label))
                return@launch
            }

            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setRelaunching(packageName, false)
            if (launchIntent != null) {
                getApplication<Application>().startActivity(launchIntent)
                _events.tryEmit(TargetAppsEvent.Relaunched(label))
            } else {
                _events.tryEmit(TargetAppsEvent.RelaunchFailed(label))
            }
        }
    }

    /** Verifies (and, on first use, requests) root by running `su -c id` and checking for uid=0. */
    private fun hasRootAccess(): Boolean = try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor() == 0 && output.contains("uid=0")
    } catch (e: Exception) {
        false
    }

    private fun runAsRoot(command: String): Boolean = try {
        Runtime.getRuntime().exec(arrayOf("su", "-c", command)).waitFor() == 0
    } catch (e: Exception) {
        false
    }

    private fun addToScope(service: XposedService, packageName: String) {
        setPending(packageName, true)

        val callback = object : XposedService.OnScopeEventListener {
            override fun onScopeRequestApproved(approved: List<String>) {
                viewModelScope.launch {
                    setPending(packageName, false)
                    refreshScope()
                }
            }

            override fun onScopeRequestFailed(message: String) {
                viewModelScope.launch {
                    setPending(packageName, false)
                    _events.tryEmit(TargetAppsEvent.ScopeRequestFailed(message))
                }
            }
        }

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { service.requestScope(listOf(packageName), callback) }
            } catch (e: XposedService.ServiceException) {
                setPending(packageName, false)
                _events.tryEmit(TargetAppsEvent.ScopeRequestFailed(e.message ?: e.toString()))
            }
        }
    }

    private fun removeFromScope(service: XposedService, packageName: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { service.removeScope(listOf(packageName)) }
            } catch (e: XposedService.ServiceException) {
                _events.tryEmit(TargetAppsEvent.ScopeRequestFailed(e.message ?: e.toString()))
            }
            refreshScope()
        }
    }

    private fun observeService() {
        viewModelScope.launch {
            App.serviceState.collectLatest { service ->
                if (service == null) {
                    _uiState.update { state ->
                        state.copy(
                            isModuleActive = false,
                            selectedPackages = emptySet(),
                            pendingPackages = emptySet(),
                            apps = state.apps.map { it.copy(isSelected = false, isPending = false) }
                        )
                    }
                } else {
                    _uiState.update { it.copy(isModuleActive = true) }
                    refreshScope()
                }
            }
        }
    }

    /** Re-read the live LSPosed scope and reflect it in the UI + the mirror pref. */
    private suspend fun refreshScope() {
        val service = App.service ?: return
        val scope = withContext(Dispatchers.IO) {
            try {
                service.scope.toSet()
            } catch (e: XposedService.ServiceException) {
                _events.tryEmit(TargetAppsEvent.ScopeRequestFailed(e.message ?: e.toString()))
                _uiState.value.selectedPackages
            }
        }

        _uiState.update { state ->
            state.copy(
                selectedPackages = scope,
                apps = state.apps.map { it.copy(isSelected = scope.contains(it.packageName)) }
            )
        }

        preferencesRepository.saveTargetApps(scope)
    }

    private fun setPending(packageName: String, pending: Boolean) {
        _uiState.update { state ->
            val nextPending = state.pendingPackages.toMutableSet().apply {
                if (pending) add(packageName) else remove(packageName)
            }
            state.copy(
                pendingPackages = nextPending,
                apps = state.apps.map {
                    if (it.packageName == packageName) it.copy(isPending = pending) else it
                }
            )
        }
    }

    private fun setRelaunching(packageName: String, relaunching: Boolean) {
        _uiState.update { state ->
            val nextRelaunching = state.relaunchingPackages.toMutableSet().apply {
                if (relaunching) add(packageName) else remove(packageName)
            }
            state.copy(
                relaunchingPackages = nextRelaunching,
                apps = state.apps.map {
                    if (it.packageName == packageName) it.copy(isRelaunching = relaunching) else it
                }
            )
        }
    }

    private fun loadInstalledApps(isRefresh: Boolean = false, retryAttempt: Int = 0) {
        val request = ++installedAppsLoadRequest
        _uiState.update { state ->
            state.copy(
                isLoading = state.apps.isEmpty(),
                isRefreshing = isRefresh
            )
        }
        viewModelScope.launch {
            val installedApps = runCatching {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    packageManager.getInstalledApplications(PackageManager.MATCH_ALL)
                        .asSequence()
                        .filter { it.packageName != MANAGER_APP_PACKAGE_NAME }
                        .distinctBy { it.packageName }
                        .map {
                            TargetAppItem(
                                label = it.loadLabel(packageManager).toString(),
                                packageName = it.packageName,
                                isSystemApp = it.flags and
                                    (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                            )
                        }
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                        .toList()
                }
            }.getOrElse { error ->
                Log.w("TargetAppsViewModel", "Unable to load installed applications", error)
                emptyList()
            }

            if (request != installedAppsLoadRequest) return@launch

            // The first query is what causes certain OEM systems to ask for app-list access.
            // Retry briefly after an empty first result so accepting the dialog works in the same
            // app session; users can still pull to refresh at any time.
            if (installedApps.isEmpty() && retryAttempt < INITIAL_EMPTY_LIST_RETRIES) {
                delay(INITIAL_EMPTY_LIST_RETRY_DELAY_MS)
                if (request == installedAppsLoadRequest) {
                    loadInstalledApps(isRefresh = isRefresh, retryAttempt = retryAttempt + 1)
                }
                return@launch
            }

            val appsWithSystemFramework = if (installedApps.any { it.packageName == SYSTEM_FRAMEWORK_SCOPE }) {
                installedApps
            } else {
                listOf(
                    TargetAppItem(
                        label = getApplication<Application>().getString(R.string.target_apps_system_framework),
                        packageName = SYSTEM_FRAMEWORK_SCOPE,
                        isSystemApp = true,
                        isScopeOnly = true
                    )
                ) + installedApps
            }

            _uiState.update { state ->
                state.copy(
                    apps = appsWithSystemFramework.map {
                        it.copy(
                            isSelected = state.selectedPackages.contains(it.packageName),
                            isPending = state.pendingPackages.contains(it.packageName),
                            isRelaunching = state.relaunchingPackages.contains(it.packageName)
                        )
                    },
                    isLoading = false,
                    isRefreshing = false
                )
            }
        }
    }

    private companion object {
        const val SYSTEM_FRAMEWORK_SCOPE = "system"
        const val INITIAL_EMPTY_LIST_RETRIES = 5
        const val INITIAL_EMPTY_LIST_RETRY_DELAY_MS = 1_000L
    }
}
