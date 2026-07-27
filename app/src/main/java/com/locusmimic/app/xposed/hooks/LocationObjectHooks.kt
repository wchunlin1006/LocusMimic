package com.locusmimic.app.xposed.hooks

import android.os.Build
import android.util.Log
import com.locusmimic.app.xposed.utils.LocationUtil
import com.locusmimic.app.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface

/** Hooks fields and mock-origin flags read directly from android.location.Location. */
internal class LocationObjectHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader
) {
    private val tag = "[LocationApiHooks]"

    fun initHooks() {
        try {
            val locationClass = Class.forName("android.location.Location", false, classLoader)

            module.hook(locationClass.getDeclaredMethod("getLatitude")).intercept { chain ->
                val config = PreferencesUtil.snapshot()
                if (config.isPlaying) {
                    LocationUtil.updateLocation(config)
                    logLocationEvent { "getLatitude(): ${LocationUtil.latitude}" }
                    LocationUtil.latitude
                } else {
                    chain.proceed()
                }
            }

            module.hook(locationClass.getDeclaredMethod("getLongitude")).intercept { chain ->
                val config = PreferencesUtil.snapshot()
                if (config.isPlaying) {
                    LocationUtil.updateLocation(config)
                    logLocationEvent { "getLongitude(): ${LocationUtil.longitude}" }
                    LocationUtil.longitude
                } else {
                    chain.proceed()
                }
            }

            module.hook(locationClass.getDeclaredMethod("getAccuracy")).intercept { chain ->
                val config = PreferencesUtil.snapshot()
                if (config.isPlaying && config.useAccuracy) {
                    LocationUtil.updateLocation(config)
                    logLocationEvent { "getAccuracy(): ${LocationUtil.accuracy}" }
                    LocationUtil.accuracy
                } else {
                    chain.proceed()
                }
            }

            module.hook(locationClass.getDeclaredMethod("getAltitude")).intercept { chain ->
                val config = PreferencesUtil.snapshot()
                if (config.isPlaying && config.useAltitude) {
                    LocationUtil.updateLocation(config)
                    logLocationEvent { "getAltitude(): ${LocationUtil.altitude}" }
                    LocationUtil.altitude
                } else {
                    chain.proceed()
                }
            }

            module.hook(locationClass.getDeclaredMethod("getVerticalAccuracyMeters")).intercept { chain ->
                val config = PreferencesUtil.snapshot()
                if (config.isPlaying && config.useVerticalAccuracy) {
                    LocationUtil.updateLocation(config)
                    logLocationEvent {
                        "getVerticalAccuracyMeters(): ${LocationUtil.verticalAccuracy}"
                    }
                    LocationUtil.verticalAccuracy
                } else {
                    chain.proceed()
                }
            }

            module.hook(locationClass.getDeclaredMethod("getSpeed")).intercept { chain ->
                val config = PreferencesUtil.snapshot()
                if (config.isPlaying && config.useSpeed) {
                    LocationUtil.updateLocation(config)
                    logLocationEvent { "getSpeed(): ${LocationUtil.speed}" }
                    LocationUtil.speed
                } else {
                    chain.proceed()
                }
            }

            module.hook(locationClass.getDeclaredMethod("getSpeedAccuracyMetersPerSecond")).intercept { chain ->
                val config = PreferencesUtil.snapshot()
                if (config.isPlaying && config.useSpeedAccuracy) {
                    LocationUtil.updateLocation(config)
                    logLocationEvent {
                        "getSpeedAccuracyMetersPerSecond(): ${LocationUtil.speedAccuracy}"
                    }
                    LocationUtil.speedAccuracy
                } else {
                    chain.proceed()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                module.hook(locationClass.getDeclaredMethod("getMslAltitudeMeters")).intercept { chain ->
                    val config = PreferencesUtil.snapshot()
                    if (config.isPlaying && config.useMeanSeaLevel) {
                        LocationUtil.updateLocation(config)
                        logLocationEvent { "getMslAltitudeMeters(): ${LocationUtil.meanSeaLevel}" }
                        LocationUtil.meanSeaLevel
                    } else {
                        chain.proceed()
                    }
                }

                module.hook(locationClass.getDeclaredMethod("getMslAltitudeAccuracyMeters")).intercept { chain ->
                    val config = PreferencesUtil.snapshot()
                    if (config.isPlaying && config.useMeanSeaLevelAccuracy) {
                        LocationUtil.updateLocation(config)
                        logLocationEvent {
                            "getMslAltitudeAccuracyMeters(): ${LocationUtil.meanSeaLevelAccuracy}"
                        }
                        LocationUtil.meanSeaLevelAccuracy
                    } else {
                        chain.proceed()
                    }
                }
            } else {
                module.log(Log.INFO, tag, "MSL altitude APIs not available on this API level")
            }

            hookMockLocationFlags(locationClass)
        } catch (e: Exception) {
            module.log(Log.ERROR, tag, "Error hooking Location class - ${e.message}")
        }
    }

    private fun hookMockLocationFlags(locationClass: Class<*>) {
        listOf("isFromMockProvider", "isMock").forEach { methodName ->
            runCatching {
                val method = locationClass.getDeclaredMethod(methodName)
                module.hook(method).intercept { chain ->
                    if (PreferencesUtil.snapshot().isPlaying) {
                        false
                    } else {
                        chain.proceed()
                    }
                }
            }.onFailure {
                logLocationEvent { "$methodName API not available or could not be hooked: ${it.message}" }
            }
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
