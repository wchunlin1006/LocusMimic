package com.locusmimic.app.manager.ui.permissions

import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.core.content.ContextCompat

class PermissionsViewModel : ViewModel() {

    private val _hasPermissions = mutableStateOf(false)
    val hasPermissions: State<Boolean> get() = _hasPermissions

    private val _permanentlyDenied = mutableStateOf(false)
    val permanentlyDenied: State<Boolean> get() = _permanentlyDenied

    private val _permissionsChecked = mutableStateOf(false)
    val permissionsChecked: State<Boolean> get() = _permissionsChecked

    fun checkPermissions(context: Context) {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        _hasPermissions.value = fineLocationGranted
        _permissionsChecked.value = true
    }

    fun updatePermissionsStatus(granted: Boolean) {
        _hasPermissions.value = granted
    }

    fun checkIfPermanentlyDenied(activity: Activity) {
        val shouldShowRationale = activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
        _permanentlyDenied.value = !shouldShowRationale
    }
}