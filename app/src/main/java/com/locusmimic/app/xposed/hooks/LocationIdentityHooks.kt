package com.locusmimic.app.xposed.hooks

import android.net.wifi.WifiInfo
import android.os.Build
import android.util.Log
import com.locusmimic.app.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

/** Blocks network, cell and raw GNSS identity sources while location simulation is active. */
internal class LocationIdentityHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader
) {
    private val tag = "[LocationApiHooks]"

    fun initHooks() {
        hookNetworkIdentitySources()
        hookGnssIdentitySources()
    }

    private fun hookNetworkIdentitySources() {
        hookWhilePlaying("android.net.wifi.WifiManager", "getScanResults") { _ -> emptyList<Any>() }
        hookWhilePlaying("android.net.wifi.WifiManager", "getConnectionInfo") { _ -> buildFakeWifiInfo() }

        hookWhilePlaying("android.telephony.TelephonyManager", "getCellLocation") { _ -> null }
        hookWhilePlaying("android.telephony.TelephonyManager", "getAllCellInfo") { _ -> emptyList<Any>() }
        hookWhilePlaying("android.telephony.TelephonyManager", "getNeighboringCellInfo") { _ -> emptyList<Any>() }
        hookWhilePlaying("android.telephony.TelephonyManager", "requestCellInfoUpdate") { _ -> null }
    }

    private fun hookGnssIdentitySources() {
        listOf(
            "addNmeaListener",
            "registerGnssNmeaCallback",
            "registerGnssStatusCallback",
            "registerGnssMeasurementsCallback",
            "registerGnssNavigationMessageCallback",
            "registerAntennaInfoListener"
        ).forEach { methodName ->
            hookWhilePlaying("android.location.LocationManager", methodName) { method ->
                defaultReturnValue(method)
            }
        }
    }

    private fun buildFakeWifiInfo(): WifiInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return runCatching {
            WifiInfo.Builder()
                .setBssid("02:00:00:00:00:00")
                .setSsid("AndroidAP".toByteArray())
                .setRssi(-60)
                .setNetworkId(0)
                .build()
        }.getOrNull()
    }

    private fun hookWhilePlaying(
        className: String,
        methodName: String,
        replacement: (Method) -> Any?
    ) {
        runCatching {
            val clazz = Class.forName(className, false, classLoader)
            val methods = clazz.declaredMethods.filter { it.name == methodName }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    if (PreferencesUtil.snapshot().isPlaying) {
                        replacement(method)
                    } else {
                        chain.proceed()
                    }
                }
            }
        }.onFailure {
            module.log(Log.WARN, tag, "Could not hook $className#$methodName: ${it.message}")
        }
    }

    private fun defaultReturnValue(method: Method): Any? {
        return when (method.returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0F
            java.lang.Double.TYPE -> 0.0
            else -> null
        }
    }
}
