package com.locusmimic.app.xposed.hooks

import android.location.Location
import android.util.Log
import com.locusmimic.app.xposed.utils.LocationUtil
import com.locusmimic.app.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface

/** Replaces locations delivered through listeners and framework/vendor LocationResult objects. */
internal class LocationCallbackHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader
) {
    private val tag = "[LocationApiHooks]"

    fun initHooks() {
        hookLocationListenerTransport()
        hookFusedLocationResults()
    }

    private fun hookLocationListenerTransport() {
        runCatching {
            val transportClass = Class.forName(
                "android.location.LocationManager\$LocationListenerTransport",
                false,
                classLoader
            )
            val methods = transportClass.declaredMethods.filter { it.name == "onLocationChanged" }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    if (!PreferencesUtil.snapshot().isPlaying) return@intercept chain.proceed()

                    val (newArgs, replaced) = replaceLocationArguments(chain.args)
                    if (replaced) chain.proceed(newArgs) else chain.proceed()
                }
            }
            if (methods.isEmpty()) {
                module.log(
                    Log.WARN,
                    tag,
                    "No LocationListenerTransport#onLocationChanged overloads found"
                )
            }
        }.onFailure {
            module.log(Log.WARN, tag, "Could not hook LocationListenerTransport: ${it.message}")
        }
    }

    private fun hookFusedLocationResults() {
        listOf(
            "android.location.LocationResult",
            "com.google.android.gms.location.LocationResult",
            "com.huawei.hms.location.LocationResult"
        ).forEach { className ->
            runCatching {
                val resultClass = Class.forName(className, false, classLoader)
                resultClass.declaredMethods
                    .filter { it.name == "getLastLocation" || it.name == "getLocations" }
                    .forEach { method ->
                        module.hook(method).intercept { chain ->
                            val result = chain.proceed()
                            if (PreferencesUtil.snapshot().isPlaying) {
                                replaceLocationResult(result)
                            } else {
                                result
                            }
                        }
                    }
            }.onFailure {
                logLocationEvent { "Optional location result class unavailable: $className" }
            }
        }
    }

    private fun replaceLocationArguments(args: List<Any?>): Pair<Array<Any?>, Boolean> {
        var replaced = false
        val newArgs = args.toTypedArray()

        args.forEachIndexed { index, arg ->
            val replacement = replaceLocationResult(arg)
            if (replacement !== arg) {
                newArgs[index] = replacement
                replaced = true
            }
        }

        return newArgs to replaced
    }

    private fun replaceLocationResult(value: Any?): Any? = when (value) {
        is Location -> LocationUtil.createFakeLocation(value)
        is List<*> -> {
            var replaced = false
            val result = value.map { item ->
                if (item is Location) {
                    replaced = true
                    LocationUtil.createFakeLocation(item)
                } else {
                    item
                }
            }
            if (replaced) result else value
        }
        else -> value
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
