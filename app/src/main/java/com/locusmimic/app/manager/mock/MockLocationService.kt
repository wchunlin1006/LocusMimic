package com.locusmimic.app.manager.mock

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresApi
import com.locusmimic.app.data.JsonCodec
import com.locusmimic.app.R
import com.locusmimic.app.data.DEFAULT_ACCURACY
import com.locusmimic.app.data.KEY_ACCURACY
import com.locusmimic.app.data.KEY_ENABLE_MOCK_PROVIDER
import com.locusmimic.app.data.KEY_IS_PLAYING
import com.locusmimic.app.data.KEY_LAST_CLICKED_LOCATION
import com.locusmimic.app.data.KEY_USE_ACCURACY
import com.locusmimic.app.data.SHARED_PREFS_FILE
import com.locusmimic.app.data.model.LastClickedLocation
import java.util.concurrent.TimeUnit

class MockLocationService : Service() {
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var locationManager: LocationManager
    private lateinit var powerManager: PowerManager
    private lateinit var preferences: SharedPreferences
    @Volatile private var currentState = MockState(false, null, DEFAULT_MOCK_ACCURACY_METERS)
    private var mockLocationRepairAttempted = false
    private val readyProviders = mutableSetOf<String>()
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ ->
        currentState = readState(prefs)
    }

    private val tick = object : Runnable {
        override fun run() {
            val state = currentState
            if (!state.canSpoof || state.location == null) {
                stopMockProviders()
                stopSelf()
                return
            }

            if (pushMockLocation(state.location, state.accuracy)) {
                val interval = if (powerManager.isInteractive) {
                    FOREGROUND_UPDATE_INTERVAL_MS
                } else {
                    SCREEN_OFF_UPDATE_INTERVAL_MS
                }
                handler.postDelayed(this, interval)
            } else {
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        handlerThread = HandlerThread("LocusMimicMockProvider").also { it.start() }
        handler = Handler(handlerThread.looper)
        locationManager = getSystemService(LocationManager::class.java)
        powerManager = getSystemService(PowerManager::class.java)
        preferences = getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE)
        currentState = readState(preferences)
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMockProviders()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                currentState = readState(preferences)
                handler.removeCallbacks(tick)
                mockLocationRepairAttempted = false
                handler.post(tick)
                // Compatibility mode is explicitly enabled by the user and must keep feeding
                // repeated location requests even if Android recreates this foreground service.
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        stopMockProviders()
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        handlerThread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun readState(prefs: SharedPreferences = preferences): MockState {
        val canSpoof = prefs.getBoolean(KEY_IS_PLAYING, false) &&
            prefs.getBoolean(KEY_ENABLE_MOCK_PROVIDER, false)

        val location = prefs.getString(KEY_LAST_CLICKED_LOCATION, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { JsonCodec.decodeLocation(it) }.getOrNull() }

        val accuracy = if (prefs.getBoolean(KEY_USE_ACCURACY, false)) {
            readDouble(prefs, KEY_ACCURACY, DEFAULT_ACCURACY).toFloat()
        } else {
            DEFAULT_ACCURACY.toFloat()
        }.takeIf { it > 0f } ?: DEFAULT_MOCK_ACCURACY_METERS

        return MockState(canSpoof = canSpoof, location = location, accuracy = accuracy)
    }

    private fun pushMockLocation(location: LastClickedLocation, accuracy: Float): Boolean {
        if (pushMockLocationOnce(location, accuracy)) return true

        if (!mockLocationRepairAttempted && repairMockLocationAppOp()) {
            return pushMockLocationOnce(location, accuracy)
        }
        return false
    }

    private fun pushMockLocationOnce(location: LastClickedLocation, accuracy: Float): Boolean {
        var pushed = false
        TARGET_PROVIDERS.forEach { provider ->
            val providerReady = ensureMockProvider(provider)
            if (providerReady) {
                runCatching {
                    locationManager.setTestProviderLocation(provider, buildLocation(provider, location, accuracy))
                    pushed = true
                }.onFailure {
                    Log.w(TAG, "Could not set mock location for $provider: ${it.message}")
                }
            }
        }
        return pushed
    }

    private fun repairMockLocationAppOp(): Boolean {
        mockLocationRepairAttempted = true
        return runCatching {
            val process = ProcessBuilder(
                "su",
                "-c",
                "appops set $packageName android:mock_location allow"
            ).redirectErrorStream(true).start()

            if (!process.waitFor(ROOT_REPAIR_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                Log.w(TAG, "Timed out while requesting root mock-location repair")
                false
            } else if (process.exitValue() == 0) {
                Log.i(TAG, "Repaired mock-location app-op using root")
                true
            } else {
                Log.w(TAG, "Root mock-location repair failed with exit code ${process.exitValue()}")
                false
            }
        }.getOrElse {
            Log.w(TAG, "Root mock-location repair is unavailable: ${it.message}")
            false
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("WrongConstant")
    private fun ensureMockProvider(provider: String): Boolean {
        if (provider in readyProviders) return true

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locationManager.addTestProvider(provider, providerProperties())
            } else {
                locationManager.addTestProvider(
                    provider,
                    false,
                    false,
                    false,
                    false,
                    true,
                    true,
                    true,
                    android.location.Criteria.POWER_LOW,
                    android.location.Criteria.ACCURACY_FINE
                )
            }
        }.recoverCatching {
            // Existing mock providers throw on add; enabling and setting below is enough.
        }.mapCatching {
            locationManager.setTestProviderEnabled(provider, true)
            readyProviders += provider
            true
        }.onFailure {
            Log.e(TAG, "Mock provider $provider is not available: ${it.message}")
        }.getOrDefault(false)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun providerProperties(): ProviderProperties {
        return ProviderProperties.Builder()
            .setAccuracy(ProviderProperties.ACCURACY_FINE)
            .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
            .setHasAltitudeSupport(true)
            .setHasSpeedSupport(true)
            .setHasBearingSupport(true)
            .build()
    }

    private fun buildLocation(
        provider: String,
        location: LastClickedLocation,
        accuracy: Float
    ): Location {
        return Location(provider).apply {
            latitude = location.latitude
            longitude = location.longitude
            this.accuracy = accuracy
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
    }

    private fun stopMockProviders() {
        TARGET_PROVIDERS.forEach { provider ->
            runCatching { locationManager.removeTestProvider(provider) }
        }
        readyProviders.clear()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.mock_provider_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.mock_provider_notification_text))
        .setOngoing(true)
        .setSilent(true)
        .build()

    private fun readDouble(
        prefs: android.content.SharedPreferences,
        key: String,
        default: Double
    ): Double {
        val bits = prefs.getLong(key, java.lang.Double.doubleToRawLongBits(default))
        return java.lang.Double.longBitsToDouble(bits)
    }

    private data class MockState(
        val canSpoof: Boolean,
        val location: LastClickedLocation?,
        val accuracy: Float
    )

    companion object {
        private const val TAG = "MockLocationService"
        private const val ACTION_START = "com.locusmimic.app.action.MOCK_PROVIDER_START"
        private const val ACTION_STOP = "com.locusmimic.app.action.MOCK_PROVIDER_STOP"
        private const val CHANNEL_ID = "locusmimic_mock_provider"
        private const val NOTIFICATION_ID = 2001
        private const val FOREGROUND_UPDATE_INTERVAL_MS = 1_000L
        private const val SCREEN_OFF_UPDATE_INTERVAL_MS = 5_000L
        private const val DEFAULT_MOCK_ACCURACY_METERS = 5f
        private const val ROOT_REPAIR_TIMEOUT_SECONDS = 8L

        private val TARGET_PROVIDERS = listOf(
            LocationManager.GPS_PROVIDER,
            FUSED_PROVIDER
        )

        private const val FUSED_PROVIDER = "fused"

        fun sync(context: Context, enabled: Boolean) {
            val appContext = context.applicationContext
            try {
                if (enabled) {
                    val intent = Intent(appContext, MockLocationService::class.java).setAction(ACTION_START)
                    ContextCompat.startForegroundService(appContext, intent)
                } else {
                    appContext.stopService(Intent(appContext, MockLocationService::class.java))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not sync mock provider service: ${e.message}")
            }
        }
    }
}
