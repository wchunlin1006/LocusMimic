package com.locusmimic.app.manager.control

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.locusmimic.app.data.repository.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ControlReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ControlReceiver"

        const val ACTION_START = "com.locusmimic.app.action.START"
        const val ACTION_STOP = "com.locusmimic.app.action.STOP"
        const val ACTION_SET_LOCATION = "com.locusmimic.app.action.SET_LOCATION"

        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_ACCURACY = "accuracy"
        const val EXTRA_START = "start"

        private const val LAT_MIN = -90.0
        private const val LAT_MAX = 90.0
        private const val LON_MIN = -180.0
        private const val LON_MAX = 180.0
        private const val ACCURACY_MAX_METERS = 100_000f
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val repository = PreferencesRepository(appContext)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_START -> handleStart(intent, repository)
                    ACTION_STOP -> repository.saveIsPlaying(false)
                    ACTION_SET_LOCATION -> handleSetLocation(intent, repository)
                    else -> Log.w(TAG, "Unknown action: $action")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling $action: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleStart(intent: Intent, repository: PreferencesRepository) {
        if (intent.hasExtra(EXTRA_LATITUDE) && intent.hasExtra(EXTRA_LONGITUDE)) {
            val coords = parseCoordinates(intent)
            if (coords != null) {
                repository.saveLastClickedLocation(coords.first, coords.second)
            }
        }
        repository.saveIsPlaying(true)
    }

    private suspend fun handleSetLocation(intent: Intent, repository: PreferencesRepository) {
        val coords = parseCoordinates(intent) ?: return
        repository.saveLastClickedLocation(coords.first, coords.second)

        if (intent.hasExtra(EXTRA_ACCURACY)) {
            val accuracy = intent.getFloatExtra(EXTRA_ACCURACY, Float.NaN)
            if (accuracy.isFinite() && accuracy >= 0f && accuracy <= ACCURACY_MAX_METERS) {
                repository.saveUseAccuracy(true)
                repository.saveAccuracy(accuracy.toDouble())
            } else {
                Log.w(TAG, "Ignoring out-of-range accuracy: $accuracy")
            }
        }

        if (intent.getBooleanExtra(EXTRA_START, false)) {
            repository.saveIsPlaying(true)
        }
    }

    private fun parseCoordinates(intent: Intent): Pair<Double, Double>? {
        val lat = intent.getDoubleExtra(EXTRA_LATITUDE, Double.NaN)
        val lon = intent.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN)
        if (!lat.isFinite() || !lon.isFinite()) {
            Log.w(TAG, "Rejecting non-finite latitude/longitude")
            return null
        }
        if (lat < LAT_MIN || lat > LAT_MAX || lon < LON_MIN || lon > LON_MAX) {
            Log.w(TAG, "Rejecting out-of-range coordinates lat=$lat lon=$lon")
            return null
        }
        return lat to lon
    }
}
