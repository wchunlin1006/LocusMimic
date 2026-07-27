package com.locusmimic.app.manager

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import com.locusmimic.app.data.JsonCodec
import com.locusmimic.app.data.DEFAULT_ENABLE_MOCK_PROVIDER
import com.locusmimic.app.data.KEY_ENABLE_MOCK_PROVIDER
import com.locusmimic.app.data.KEY_TARGET_APPS
import com.locusmimic.app.data.MANAGER_APP_PACKAGE_NAME
import com.locusmimic.app.data.REMOTE_PREFS_GROUP
import com.locusmimic.app.data.SHARED_PREFS_FILE
import com.locusmimic.app.data.SYSTEM_HOOK_PACKAGES
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class App : Application(), XposedServiceHelper.OnServiceListener {
    companion object {
        private const val TAG = "LocusMimicApp"
        private val _serviceState = MutableStateFlow<XposedService?>(null)
        private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val serviceState: StateFlow<XposedService?> = _serviceState.asStateFlow()
        val service: XposedService? get() = _serviceState.value   // keep existing callers working
    }

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)   // exactly once
    }

    override fun onServiceBind(service: XposedService) {
        _serviceState.value = service
        val remotePrefs = service.getRemotePreferences(REMOTE_PREFS_GROUP)
        syncLocationModeToRemotePreferences(remotePrefs)
        syncScopeToRemotePreferences(service, remotePrefs)
    }

    override fun onServiceDied(service: XposedService) {
        _serviceState.value = null
    }

    private fun syncLocationModeToRemotePreferences(remotePrefs: SharedPreferences) {
        val localPrefs = getSharedPreferences(SHARED_PREFS_FILE, MODE_PRIVATE)
        remotePrefs.edit()
            .putBoolean(
                KEY_ENABLE_MOCK_PROVIDER,
                localPrefs.getBoolean(KEY_ENABLE_MOCK_PROVIDER, DEFAULT_ENABLE_MOCK_PROVIDER)
            )
            .apply()
    }

    private fun syncScopeToRemotePreferences(
        service: XposedService,
        remotePrefs: SharedPreferences
    ) {
        applicationScope.launch {
            val targetPackages = try {
                service.scope
                    .asSequence()
                    .filterNot(SYSTEM_HOOK_PACKAGES::contains)
                    .filterNot { it == MANAGER_APP_PACKAGE_NAME }
                    .distinct()
                    .sorted()
                    .toList()
            } catch (e: XposedService.ServiceException) {
                Log.w(TAG, "Failed to mirror LSPosed target scope", e)
                return@launch
            }

            remotePrefs.edit()
                .putString(KEY_TARGET_APPS, JsonCodec.encodeStrings(targetPackages))
                .apply()
        }
    }

}
