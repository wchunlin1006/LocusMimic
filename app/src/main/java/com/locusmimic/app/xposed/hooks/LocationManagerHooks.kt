package com.locusmimic.app.xposed.hooks

import android.location.Location
import android.util.Log
import com.locusmimic.app.xposed.utils.LocationUtil
import com.locusmimic.app.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface

/** Hooks synchronous location requests made through android.location.LocationManager. */
internal class LocationManagerHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader
) {
    private val tag = "[LocationApiHooks]"

    fun initHooks() {
        try {
            val locationManagerClass = Class.forName(
                "android.location.LocationManager",
                false,
                classLoader
            )
            val method = locationManagerClass.getDeclaredMethod(
                "getLastKnownLocation",
                String::class.java
            )

            module.hook(method).intercept { chain ->
                val provider = chain.getArg(0) as String
                if (PreferencesUtil.snapshot().isPlaying) {
                    val fakeLocation = LocationUtil.createFakeLocation(provider = provider)
                    logLocationEvent { "getLastKnownLocation($provider): $fakeLocation" }
                    fakeLocation
                } else {
                    chain.proceed() as? Location
                }
            }
        } catch (e: Exception) {
            module.log(Log.ERROR, tag, "Error hooking LocationManager - ${e.message}")
        }
    }

    private inline fun logLocationEvent(message: () -> String) {
        if (LOG_LOCATION_EVENTS) {
            module.log(Log.INFO, tag, message())
        }
    }

    private companion object {
        const val LOG_LOCATION_EVENTS = false
    }
}
