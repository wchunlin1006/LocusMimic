// LocationUtil.kt
package com.locusmimic.app.xposed.utils

import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.locusmimic.app.data.DEFAULT_ACCURACY
import com.locusmimic.app.data.DEFAULT_ALTITUDE
import com.locusmimic.app.data.DEFAULT_MEAN_SEA_LEVEL
import com.locusmimic.app.data.DEFAULT_MEAN_SEA_LEVEL_ACCURACY
import com.locusmimic.app.data.DEFAULT_RANDOMIZE_RADIUS
import com.locusmimic.app.data.DEFAULT_SPEED
import com.locusmimic.app.data.DEFAULT_SPEED_ACCURACY
import com.locusmimic.app.data.DEFAULT_VERTICAL_ACCURACY
import com.locusmimic.app.data.PI
import com.locusmimic.app.data.RADIUS_EARTH
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.Random
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object LocationUtil {
    private const val TAG = "[LocationUtil]"

    @Volatile
    var logger: ((priority: Int, tag: String, message: String) -> Unit)? = null

    private fun log(message: String, priority: Int = Log.INFO) {
        logger?.invoke(priority, TAG, message)
    }

    private const val DEBUG: Boolean = false

    private val random: Random = Random()
    @Volatile private var canAttemptMockProviderHide: Boolean = true
    @Volatile private var lastAppliedConfig: PreferencesUtil.PreferencesSnapshot? = null
    @Volatile private var lastAppliedAtNanos: Long = Long.MIN_VALUE
    private var randomizedAtNanos: Long = Long.MIN_VALUE
    private var randomizedBaseLatitude: Double = Double.NaN
    private var randomizedBaseLongitude: Double = Double.NaN
    private var randomizedRadius: Double = Double.NaN
    private var randomizedPoint: Pair<Double, Double>? = null

    @Volatile var latitude: Double = 0.0
    @Volatile var longitude: Double = 0.0
    @Volatile var accuracy: Float = 0F
    @Volatile var altitude: Double = 0.0
    @Volatile var verticalAccuracy: Float = 0F
    @Volatile var meanSeaLevel: Double = 0.0
    @Volatile var meanSeaLevelAccuracy: Float = 0F
    @Volatile var speed: Float = 0F
    @Volatile var speedAccuracy: Float = 0F

    @Synchronized
    fun createFakeLocation(originalLocation: Location? = null, provider: String = LocationManager.GPS_PROVIDER): Location {
        updateLocation()

        val fakeLocation = if (originalLocation == null) {
            Location(provider).apply {
                time = System.currentTimeMillis() - 300
            }
        } else {
            Location(originalLocation.provider).apply {
                time = originalLocation.time
                accuracy = originalLocation.accuracy
                bearing = originalLocation.bearing
                bearingAccuracyDegrees = originalLocation.bearingAccuracyDegrees
                elapsedRealtimeNanos = originalLocation.elapsedRealtimeNanos
                verticalAccuracyMeters = originalLocation.verticalAccuracyMeters
            }
        }

        fakeLocation.latitude = latitude
        fakeLocation.longitude = longitude

        if (accuracy != 0F) {
            fakeLocation.accuracy = accuracy
        }

        if (altitude != 0.0) {
            fakeLocation.altitude = altitude
        }

        if (verticalAccuracy != 0F) {
            fakeLocation.verticalAccuracyMeters = verticalAccuracy
        }

        if (speed != 0F) {
            fakeLocation.speed = speed
        }

        if (speedAccuracy != 0F) {
            fakeLocation.speedAccuracyMetersPerSecond = speedAccuracy
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (meanSeaLevel != 0.0) {
                fakeLocation.mslAltitudeMeters = meanSeaLevel
            }

            if (meanSeaLevelAccuracy != 0F) {
                fakeLocation.mslAltitudeAccuracyMeters = meanSeaLevelAccuracy
            }
        }

        attemptHideMockProvider(fakeLocation)

        return fakeLocation
    }

    private fun attemptHideMockProvider(fakeLocation: Location) {
        if (!canAttemptMockProviderHide) return
        try {
            HiddenApiBypass.invoke(fakeLocation.javaClass, fakeLocation, "setIsFromMockProvider", false)
            if (DEBUG) {
                log("invoked hidden API - setIsFromMockProvider: false)")
            }
        } catch (e: Exception) {
            // A missing/blocked hidden API will fail the same way for every Location instance.
            // Circuit-break after the first failure instead of paying exception cost per update.
            canAttemptMockProviderHide = false
            log("Not possible to hide mock provider - ${e.message}", priority = Log.ERROR)
        }
    }

    fun updateLocation(config: PreferencesUtil.PreferencesSnapshot = PreferencesUtil.snapshot()) {
        val now = SystemClock.elapsedRealtimeNanos()
        val randomizationStillFresh = !config.useRandomize ||
            now - lastAppliedAtNanos < RANDOMIZATION_INTERVAL_NANOS
        if (config === lastAppliedConfig && randomizationStillFresh) return

        synchronized(this) {
            val recheckedNow = SystemClock.elapsedRealtimeNanos()
            val recheckedRandomizationStillFresh = !config.useRandomize ||
                recheckedNow - lastAppliedAtNanos < RANDOMIZATION_INTERVAL_NANOS
            if (config === lastAppliedConfig && recheckedRandomizationStillFresh) return

            try {
            config.lastClickedLocation?.let {
                if (config.useRandomize) {
                    val randomLocation = getStableRandomLocation(
                        it.latitude,
                        it.longitude,
                        config.randomizeRadius
                    )
                    latitude = randomLocation.first
                    longitude = randomLocation.second
                } else {
                    latitude = it.latitude
                    longitude = it.longitude
                }

                accuracy = if (config.useAccuracy) {
                    config.accuracy.toFloat()
                } else {
                    DEFAULT_ACCURACY.toFloat()
                }

                altitude = if (config.useAltitude) {
                    config.altitude
                } else {
                    DEFAULT_ALTITUDE
                }

                verticalAccuracy = if (config.useVerticalAccuracy) {
                    config.verticalAccuracy
                } else {
                    DEFAULT_VERTICAL_ACCURACY
                }

                meanSeaLevel = if (config.useMeanSeaLevel) {
                    config.meanSeaLevel
                } else {
                    DEFAULT_MEAN_SEA_LEVEL
                }

                meanSeaLevelAccuracy = if (config.useMeanSeaLevelAccuracy) {
                    config.meanSeaLevelAccuracy
                } else {
                    DEFAULT_MEAN_SEA_LEVEL_ACCURACY
                }

                speed = if (config.useSpeed) {
                    config.speed
                } else {
                    DEFAULT_SPEED
                }

                speedAccuracy = if (config.useSpeedAccuracy) {
                    config.speedAccuracy
                } else {
                    DEFAULT_SPEED_ACCURACY
                }

                if (DEBUG) {
                    log("Updated fake location values to:")
                    log("\tCoordinates: (latitude = $latitude, longitude = $longitude)")
                    log("\tAccuracy: $accuracy")
                    log("\tAltitude: $altitude")
                    log("\tVertical Accuracy: $verticalAccuracy")
                    log("\tMean Sea Level: $meanSeaLevel")
                    log("\tMean Sea Level Accuracy: $meanSeaLevelAccuracy")
                    log("\tSpeed: $speed")
                    log("\tSpeed Accuracy: $speedAccuracy")
                }
            } ?: run {
                if (DEBUG) {
                    log("Last clicked location is null")
                }
            }
                lastAppliedConfig = config
                lastAppliedAtNanos = recheckedNow
            } catch (e: Exception) {
                log("Error - ${e.message}", priority = Log.ERROR)
            }
        }
    }

    private fun getStableRandomLocation(lat: Double, lon: Double, radiusInMeters: Double): Pair<Double, Double> {
        val now = SystemClock.elapsedRealtimeNanos()
        val cached = randomizedPoint
        if (cached != null &&
            lat == randomizedBaseLatitude &&
            lon == randomizedBaseLongitude &&
            radiusInMeters == randomizedRadius &&
            now - randomizedAtNanos < RANDOMIZATION_INTERVAL_NANOS
        ) {
            return cached
        }

        return getRandomLocation(lat, lon, radiusInMeters).also {
            randomizedBaseLatitude = lat
            randomizedBaseLongitude = lon
            randomizedRadius = radiusInMeters
            randomizedAtNanos = now
            randomizedPoint = it
        }
    }

    // Calculates a random point within a circle around the fake location that has the radius set by by the user. Uses Haversine's formula.
    private fun getRandomLocation(lat: Double, lon: Double, radiusInMeters: Double): Pair<Double, Double> {
        val radiusInRadians = radiusInMeters / RADIUS_EARTH

        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)

        val sinLat = sin(latRad)
        val cosLat = cos(latRad)

        // Generate two random numbers
        val rand1 = random.nextDouble()
        val rand2 = random.nextDouble()

        // Random distance and bearing
        val distance = radiusInRadians * sqrt(rand1)
        val bearing = 2 * PI * rand2

        val sinDistance = sin(distance)
        val cosDistance = cos(distance)

        val newLatRad = asin(sinLat * cosDistance + cosLat * sinDistance * cos(bearing))
        val newLonRad = lonRad + atan2(
            sin(bearing) * sinDistance * cosLat,
            cosDistance - sinLat * sin(newLatRad)
        )

        // Convert back to degrees
        val newLat = Math.toDegrees(newLatRad)
        var newLon = Math.toDegrees(newLonRad)

        // Normalize longitude to be between -180 and 180 degrees
        newLon = ((newLon + 180) % 360 + 360) % 360 - 180

        // Clamp latitude to -90 to 90 degrees
        val finalLat = newLat.coerceIn(-90.0, 90.0)

        return Pair(finalLat, newLon)
    }

    private const val RANDOMIZATION_INTERVAL_NANOS = 1_000_000_000L
}
