// SystemServicesHooks.kt
package com.locusmimic.app.xposed.hooks

import android.location.Location
import android.location.LocationManager
import android.net.wifi.WifiInfo
import android.os.Build
import android.telephony.CellInfo
import android.util.Log
import com.locusmimic.app.xposed.utils.LocationUtil
import com.locusmimic.app.xposed.utils.PreferencesUtil
import dalvik.system.PathClassLoader
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class SystemServicesHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader
) {
    private val tag = "[SystemServicesHooks]"

    private companion object {
        const val LOG_SYSTEM_LOCATION_EVENTS = false
        const val ENABLE_RISKY_SYSTEM_IDENTITY_HOOKS = false
        const val MAX_PACKAGE_SCAN_DEPTH = 5
        const val FUSED_PROVIDER = "fused"
    }

    private val hookedWifiServiceClasses = Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())

    fun initHooks() {
        hookLastLocation(classLoader)
        hookCurrentLocation(classLoader)
        hookLocationDispatch(classLoader)
        hookMiuiLocationServices(classLoader)
        if (ENABLE_RISKY_SYSTEM_IDENTITY_HOOKS) {
            hookWifiServices(classLoader)
            hookGnssRegistration(classLoader)
            hookGeofence(classLoader)
        } else {
            module.log(Log.INFO, tag, "Skipping GNSS/Wi-Fi/geofence hooks in stability mode.")
        }
        module.log(Log.INFO, tag, "Instantiated hooks successfully")
    }

    private inline fun logSystemLocationEvent(message: () -> String) {
        if (LOG_SYSTEM_LOCATION_EVENTS) {
            module.log(Log.INFO, tag, message())
        }
    }

    private fun hookLastLocation(classLoader: ClassLoader) {
        val serviceClass = findClass(
            classLoader,
            "com.android.server.location.LocationManagerService",
            "com.android.server.LocationManagerService"
        ) ?: return

        hookAll(serviceClass, "getLastLocation") { chain ->
            val result = chain.proceed()
            if (shouldSpoofArgs(chain.args)) {
                val original = result as? Location
                logSystemLocationEvent { "Replaced getLastLocation result." }
                LocationUtil.createFakeLocation(original)
            } else {
                result
            }
        }
    }

    private fun hookCurrentLocation(classLoader: ClassLoader) {
        val serviceClass = findClass(
            classLoader,
            "com.android.server.location.LocationManagerService",
            "com.android.server.LocationManagerService"
        ) ?: return

        hookAll(serviceClass, "getCurrentLocation") { chain ->
            if (shouldSpoofArgs(chain.args)) {
                logSystemLocationEvent { "Blocked getCurrentLocation request for spoofed target." }
                defaultReturnValue(chain.executable as? Method)
            } else {
                chain.proceed()
            }
        }
    }

    private fun hookLocationDispatch(classLoader: ClassLoader) {
        // Android 12+ dispatches updates through a per-client registration before older
        // LocationManagerService receiver callbacks. Hook both generations, but only after the
        // receiving package can be attributed to a selected target.
        hookProviderRegistrationCallbacks(classLoader)
        hookReceiverCallbacks(classLoader)
    }

    private fun hookProviderRegistrationCallbacks(classLoader: ClassLoader) {
        val registrationClass = findClass(
            classLoader,
            "com.android.server.location.provider.LocationProviderManager\$LocationRegistration",
            "com.android.server.location.provider.LocationProviderManager\$Registration"
        ) ?: return

        hookAll(registrationClass, "acceptLocationChange") { chain ->
            interceptTargetedLocationCallback(chain, "LocationRegistration.acceptLocationChange")
        }
    }

    private fun hookMiuiLocationServices(classLoader: ClassLoader) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (!isXiaomiFamilyDevice()) {
            module.log(Log.INFO, tag, "Skipping MIUI location hooks on non-Xiaomi device.")
            return
        }

        val miuiClass = findClass(
            classLoader,
            "com.android.server.location.MiuiBlurLocationManagerImpl",
            "com.android.server.location.MiuiBlurLocationManager"
        ) ?: return

        hookAll(miuiClass, "getBlurryLocation") { chain ->
            val result = chain.proceed()
            if (shouldSpoofArgs(chain.args)) {
                logSystemLocationEvent { "Replaced MIUI blurry location result." }
                replaceLocationLikeResult(result, chain.executable as? Method)
            } else {
                result
            }
        }

        hookAll(miuiClass, "getBlurryCellLocation") { chain ->
            val result = chain.proceed()
            if (shouldSpoofArgs(chain.args)) {
                logSystemLocationEvent { "Cleared MIUI blurry cell location result." }
                null
            } else {
                result
            }
        }

        hookAll(miuiClass, "getBlurryCellInfos") { chain ->
            val result = chain.proceed()
            if (shouldSpoofArgs(chain.args)) {
                logSystemLocationEvent { "Cleared MIUI blurry cell info result." }
                emptyList<CellInfo>()
            } else {
                result
            }
        }

        hookAll(miuiClass, "handleGpsLocationChangedLocked") { chain ->
            if (shouldSpoofArgs(chain.args)) {
                logSystemLocationEvent { "Blocked MIUI GPS location refresh while spoofing." }
                defaultReturnValue(chain.executable as? Method)
            } else {
                chain.proceed()
            }
        }
    }

    private fun hookReceiverCallbacks(classLoader: ClassLoader) {
        val receiverClass = findClass(
            classLoader,
            "com.android.server.location.LocationManagerService\$Receiver",
            "com.android.server.LocationManagerService\$Receiver"
        ) ?: return

        hookAll(receiverClass, "callLocationChangedLocked") { chain ->
            interceptTargetedLocationCallback(chain, "Receiver.callLocationChangedLocked")
        }
    }

    private fun interceptTargetedLocationCallback(chain: Chain, source: String): Any? {
        val config = PreferencesUtil.snapshot()
        if (!config.enableSystemHooks || !config.isPlaying) return chain.proceed()

        val receivingPackages = linkedSetOf<String>().apply {
            addAll(collectPackageNames(chain.thisObject))
            chain.args.forEach { addAll(collectPackageNames(it)) }
        }
        if (receivingPackages.none(config.targetApps::contains)) return chain.proceed()

        val args = chain.args
        var replaced = false
        val newArgs = args.toTypedArray()

        args.forEachIndexed { index, arg ->
            when (arg) {
                is Location -> {
                    newArgs[index] = LocationUtil.createFakeLocation(arg)
                    replaced = true
                }

                is List<*> -> {
                    var listReplaced = false
                    val replacement = arg.map { item ->
                        if (item is Location) {
                            listReplaced = true
                            LocationUtil.createFakeLocation(item)
                        } else {
                            item
                        }
                    }
                    if (listReplaced) {
                        newArgs[index] = replacement
                        replaced = true
                    }
                }

                else -> {
                    if (replaceLocationFields(arg)) {
                        replaced = true
                    }
                }
            }
        }

        if (!replaced) return chain.proceed()

        logSystemLocationEvent { "Replaced $source location payload." }
        return chain.proceed(newArgs)
    }

    private fun hookGnssRegistration(classLoader: ClassLoader) {
        val serviceClasses = listOfNotNull(
            findClass(classLoader, "com.android.server.location.gnss.GnssManagerService"),
            findClass(
                classLoader,
                "com.android.server.location.LocationManagerService",
                "com.android.server.LocationManagerService"
            )
        ).distinct()

        val methodsToBlock = listOf(
            "addGnssBatchingCallback",
            "addGnssMeasurementsListener",
            "addGnssNavigationMessageListener",
            "addGnssAntennaInfoListener",
            "registerGnssStatusCallback",
            "registerGnssNmeaCallback"
        )

        serviceClasses.forEach { serviceClass ->
            methodsToBlock.forEach { methodName ->
                hookAll(serviceClass, methodName) { chain ->
                    if (shouldSpoofArgs(chain.args)) {
                        logSystemLocationEvent { "Blocked $methodName while spoofing is enabled." }
                        defaultReturnValue(chain.executable as? Method)
                    } else {
                        chain.proceed()
                    }
                }
            }
        }
    }

    private fun hookWifiServices(classLoader: ClassLoader) {
        val systemServiceManagerClass = findClass(
            classLoader,
            "com.android.server.SystemServiceManager"
        ) ?: return

        hookAll(systemServiceManagerClass, "loadClassFromLoader") { chain ->
            val result = chain.proceed()
            val serviceName = chain.args.getOrNull(0) as? String
            if (serviceName == "com.android.server.wifi.WifiService") {
                val serviceClassLoader = chain.args.getOrNull(1) as? PathClassLoader
                if (serviceClassLoader != null) {
                    val wifiServiceClass = findClass(
                        serviceClassLoader,
                        "com.android.server.wifi.WifiServiceImpl"
                    )
                    if (wifiServiceClass != null) {
                        hookWifiServiceImpl(wifiServiceClass)
                    }
                }
            }
            result
        }
    }

    private fun hookWifiServiceImpl(wifiServiceClass: Class<*>) {
        if (!hookedWifiServiceClasses.add(wifiServiceClass)) return

        hookAll(wifiServiceClass, "getScanResults") { chain ->
            val result = chain.proceed()
            if (shouldSpoofArgs(chain.args)) {
                logSystemLocationEvent { "Cleared Wi-Fi scan results while spoofing." }
                emptyList<Any>()
            } else {
                result
            }
        }

        hookAll(wifiServiceClass, "getConnectionInfo") { chain ->
            val result = chain.proceed()
            if (shouldSpoofArgs(chain.args)) {
                // TODO: These Wi-Fi identity values are hardcoded as a temporary fallback.
                // Expose them as user-configurable settings in the manager app.
                logSystemLocationEvent { "Replaced Wi-Fi connection info while spoofing." }
                WifiInfo.Builder()
                    .setBssid("02:00:00:00:00:00")
                    .setSsid("AndroidAP".toByteArray())
                    .setRssi(-60)
                    .setNetworkId(0)
                    .build()
            } else {
                result
            }
        }
    }

    private fun hookGeofence(classLoader: ClassLoader) {
        val serviceClass = findClass(
            classLoader,
            "com.android.server.location.LocationManagerService",
            "com.android.server.LocationManagerService"
        ) ?: return

        hookAll(serviceClass, "requestGeofence") { chain ->
            if (shouldSpoofArgs(chain.args)) {
                logSystemLocationEvent { "Blocked geofence registration while spoofing is enabled." }
                defaultReturnValue(chain.executable as? Method)
            } else {
                chain.proceed()
            }
        }
    }

    private fun isXiaomiFamilyDevice(): Boolean {
        val markers = listOf("xiaomi", "redmi", "poco")
        val buildInfo = listOf(
            Build.MANUFACTURER.orEmpty(),
            Build.BRAND.orEmpty(),
            Build.PRODUCT.orEmpty(),
            Build.DEVICE.orEmpty()
        )
        return buildInfo.any { info ->
            val lower = info.lowercase()
            markers.any(lower::contains)
        }
    }

    private fun hookAll(clazz: Class<*>, methodName: String, hooker: Hooker) {
        val methods = clazz.declaredMethods.filter { it.name == methodName }
        if (methods.isEmpty()) {
            module.log(Log.WARN, tag, "No method named $methodName on ${clazz.name}")
            return
        }

        var hooked = 0
        methods.forEach { method ->
            try {
                module.hook(method).intercept(hooker)
                hooked++
            } catch (e: Throwable) {
                module.log(Log.ERROR, tag, "Failed hooking ${clazz.name}#$methodName: ${e.message}")
            }
        }

        if (hooked > 0) {
            module.log(Log.INFO, tag, "Hooked ${clazz.name}#$methodName ($hooked overloads).")
        }
    }

    private fun findClass(classLoader: ClassLoader, vararg names: String): Class<*>? {
        names.forEach { name ->
            try {
                return Class.forName(name, false, classLoader)
            } catch (_: Throwable) {
                // Try the next framework class name. AOSP moved these across releases.
            }
        }
        module.log(Log.WARN, tag, "None of these classes were found: ${names.joinToString()}")
        return null
    }

    private fun findField(clazz: Class<*>, fieldName: String): Field? {
        var currentClass: Class<*>? = clazz
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField(fieldName).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                currentClass = currentClass.superclass
            }
        }
        return null
    }

    // Name-based attribution for pull/query style calls: only spoof while playing and when a target
    // package can be recovered from the call arguments (caller identity, work source, request, etc.).
    private fun shouldSpoofArgs(args: List<Any?>?): Boolean {
        val config = PreferencesUtil.snapshot()
        if (!config.enableSystemHooks || !config.isPlaying) return false
        return args?.asSequence()
            ?.flatMap { collectPackageNames(it).asSequence() }
            ?.distinct()
            ?.any(config.targetApps::contains) == true
    }

    private fun collectPackageNames(value: Any?): Set<String> {
        return collectPackageNames(value, mutableSetOf(), 0)
    }

    private fun collectPackageNames(value: Any?, visited: MutableSet<Int>, depth: Int): Set<String> {
        if (value == null || depth > MAX_PACKAGE_SCAN_DEPTH) return emptySet()
        if (value is String) return setOfNotNull(value.takeIf(::looksLikePackageName))

        val identity = System.identityHashCode(value)
        if (!visited.add(identity)) return emptySet()

        val packageNames = linkedSetOf<String>()

        if (value is Iterable<*>) {
            value.forEach { packageNames += collectPackageNames(it, visited, depth + 1) }
            return packageNames
        }

        if (value is Map<*, *>) {
            value.forEach { (key, mapValue) ->
                packageNames += collectPackageNames(key, visited, depth + 1)
                packageNames += collectPackageNames(mapValue, visited, depth + 1)
            }
            return packageNames
        }

        packageNames += collectWorkSourcePackageNames(value)

        listOf(
            "mPackageName",
            "packageName",
            "callingPackage",
            "mCallingPackage",
            "mCallerPackageName",
            "callerPackageName",
            "mOpPackageName",
            "opPackageName"
        ).forEach { fieldName ->
            val packageName = findField(value.javaClass, fieldName)?.get(value) as? String
            packageName?.takeIf(::looksLikePackageName)?.let(packageNames::add)
        }

        listOf(
            "getPackageName",
            "getCallingPackage",
            "getCallerPackageName",
            "getOpPackageName"
        ).forEach { methodName ->
            val packageName = runCatching {
                findMethod(value.javaClass, methodName)?.invoke(value) as? String
            }.getOrNull()
            packageName?.takeIf(::looksLikePackageName)?.let(packageNames::add)
        }

        listOf(
            "mIdentity",
            "mCallerIdentity",
            "callerIdentity",
            "identity",
            "mCallingIdentity",
            "callingIdentity",
            "mAttributionSource",
            "attributionSource",
            "mNext",
            "next",
            "mWorkSource",
            "workSource",
            "mRequest",
            "request",
            "mLocationRequest",
            "locationRequest",
            "mListener",
            "listener",
            "mKey",
            "key",
            "mOwner",
            "owner",
            "mRegistration",
            "registration"
        ).forEach { fieldName ->
            packageNames += collectPackageNames(findField(value.javaClass, fieldName)?.get(value), visited, depth + 1)
        }

        listOf(
            "getAttributionSource",
            "getNext",
            "getWorkSource",
            "getLocationRequest",
            "getListener",
            "getKey",
            "getOwner"
        ).forEach { methodName ->
            val nestedValue = runCatching {
                findMethod(value.javaClass, methodName)?.invoke(value)
            }.getOrNull()
            packageNames += collectPackageNames(nestedValue, visited, depth + 1)
        }

        return packageNames
    }

    private fun collectWorkSourcePackageNames(value: Any): Set<String> {
        if (value.javaClass.name != "android.os.WorkSource") return emptySet()

        val packageNames = linkedSetOf<String>()
        val size = runCatching {
            findMethod(value.javaClass, "size")?.invoke(value) as? Int
        }.getOrNull() ?: return emptySet()

        repeat(size) { index ->
            val name = runCatching {
                findMethod(value.javaClass, "getName", Integer.TYPE)?.invoke(value, index) as? String
            }.getOrNull()
            name?.takeIf(::looksLikePackageName)?.let(packageNames::add)
        }

        return packageNames
    }

    private fun findMethod(clazz: Class<*>, methodName: String, vararg parameterTypes: Class<*>): Method? {
        var currentClass: Class<*>? = clazz
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredMethod(methodName, *parameterTypes).apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                currentClass = currentClass.superclass
            }
        }

        return clazz.methods.firstOrNull {
            it.name == methodName && it.parameterTypes.contentEquals(parameterTypes)
        }?.apply { isAccessible = true }
    }

    private fun looksLikePackageName(value: String?): Boolean {
        return value != null && "." in value && !value.startsWith("android.location.")
    }

    private fun replaceLocationFields(value: Any?): Boolean {
        if (value == null) return false
        var replaced = false

        val locationsField = findField(value.javaClass, "mLocations")
        when (val originalLocations = locationsField?.get(value)) {
            is Iterable<*> -> {
                originalLocations.forEach { item ->
                    if (item is Location) {
                        item.set(LocationUtil.createFakeLocation(item))
                        replaced = true
                    }
                }
            }

            is Array<*> -> {
                originalLocations.forEach { item ->
                    if (item is Location) {
                        item.set(LocationUtil.createFakeLocation(item))
                        replaced = true
                    }
                }
            }
        }

        val locationField = findField(value.javaClass, "mLocation")
        val originalLocation = locationField?.get(value) as? Location
        if (originalLocation != null) {
            originalLocation.set(LocationUtil.createFakeLocation(originalLocation))
            replaced = true
        }

        return replaced
    }

    private fun replaceLocationLikeResult(result: Any?, method: Method?): Any? {
        if (result is Location) {
            return LocationUtil.createFakeLocation(result)
        }

        if (result != null) {
            if (replaceLocationFields(result)) {
                return result
            }

            if (result is List<*>) {
                return result.map { item ->
                    if (item is Location) LocationUtil.createFakeLocation(item) else item
                }
            }

            runCatching {
                val sizeMethod = result.javaClass.methods.firstOrNull { it.name == "size" && it.parameterTypes.isEmpty() }
                val getMethod = result.javaClass.methods.firstOrNull { it.name == "get" && it.parameterTypes.size == 1 }
                val size = sizeMethod?.invoke(result) as? Int ?: return@runCatching
                if (size > 0) {
                    val originalLocation = getMethod?.invoke(result, 0) as? Location ?: return@runCatching
                    val fakeLocation = LocationUtil.createFakeLocation(originalLocation)
                    originalLocation.latitude = fakeLocation.latitude
                    originalLocation.longitude = fakeLocation.longitude
                    originalLocation.altitude = fakeLocation.altitude
                    originalLocation.accuracy = fakeLocation.accuracy
                    originalLocation.speed = fakeLocation.speed
                }
            }.onFailure {
                module.log(Log.ERROR, tag, "Could not inspect MIUI location container: ${it.message}")
            }

            return result
        }

        return if (method?.returnType?.let { Location::class.java.isAssignableFrom(it) } == true) {
            LocationUtil.createFakeLocation(provider = FUSED_PROVIDER)
        } else {
            null
        }
    }

    private fun defaultReturnValue(method: Method?): Any? {
        return when (method?.returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0F
            java.lang.Double.TYPE -> 0.0
            else -> null
        }
    }

}
