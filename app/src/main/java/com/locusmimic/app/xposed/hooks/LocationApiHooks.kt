package com.locusmimic.app.xposed.hooks

import android.util.Log
import io.github.libxposed.api.XposedInterface

/**
 * Application-process hook orchestrator.
 *
 * Each child module owns one location concern so compatibility fixes can be made without
 * changing unrelated hooks. Keep the initialization order stable because libxposed installs
 * interceptors in call order.
 */
class LocationApiHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader
) {
    private val tag = "[LocationApiHooks]"

    fun initHooks() {
        LocationObjectHooks(module, classLoader).initHooks()
        LocationManagerHooks(module, classLoader).initHooks()
        LocationCallbackHooks(module, classLoader).initHooks()
        LocationIdentityHooks(module, classLoader).initHooks()
        module.log(Log.INFO, tag, "Instantiated hooks successfully")
    }
}
