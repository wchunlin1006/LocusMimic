package com.locusmimic.app.manager.ui.map

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Converts between LocusMimic's WGS-84 storage and Baidu Map's BD-09 display. */
object CoordinateTransform {
    private const val X_PI = PI * 3000.0 / 180.0
    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323

    fun wgs84ToBd09(point: GeoPoint): GeoPoint {
        val gcj = wgs84ToGcj02(point.latitude, point.longitude)
        return gcj02ToBd09(gcj.latitude, gcj.longitude)
    }

    fun bd09ToWgs84(point: GeoPoint): GeoPoint {
        val gcj = bd09ToGcj02(point.latitude, point.longitude)
        return gcj02ToWgs84(gcj.latitude, gcj.longitude)
    }

    private fun bd09ToGcj02(latitude: Double, longitude: Double): GeoPoint {
        val x = longitude - 0.0065
        val y = latitude - 0.006
        val z = sqrt(x * x + y * y) - 0.00002 * sin(y * X_PI)
        val theta = atan2(y, x) - 0.000003 * cos(x * X_PI)
        return GeoPoint(z * sin(theta), z * cos(theta))
    }

    private fun gcj02ToBd09(latitude: Double, longitude: Double): GeoPoint {
        val z = sqrt(longitude * longitude + latitude * latitude) +
            0.00002 * sin(latitude * X_PI)
        val theta = atan2(latitude, longitude) + 0.000003 * cos(longitude * X_PI)
        return GeoPoint(
            z * sin(theta) + 0.006,
            z * cos(theta) + 0.0065
        )
    }

    private fun gcj02ToWgs84(latitude: Double, longitude: Double): GeoPoint {
        if (isOutsideChina(latitude, longitude)) return GeoPoint(latitude, longitude)

        val transformed = wgs84ToGcj02(latitude, longitude)
        return GeoPoint(
            latitude = latitude * 2 - transformed.latitude,
            longitude = longitude * 2 - transformed.longitude
        )
    }

    private fun wgs84ToGcj02(latitude: Double, longitude: Double): GeoPoint {
        if (isOutsideChina(latitude, longitude)) return GeoPoint(latitude, longitude)

        var latitudeOffset = transformLatitude(longitude - 105.0, latitude - 35.0)
        var longitudeOffset = transformLongitude(longitude - 105.0, latitude - 35.0)
        val radLatitude = latitude / 180.0 * PI
        var magic = sin(radLatitude)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        latitudeOffset = latitudeOffset * 180.0 / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        longitudeOffset = longitudeOffset * 180.0 / (A / sqrtMagic * cos(radLatitude) * PI)
        return GeoPoint(latitude + latitudeOffset, longitude + longitudeOffset)
    }

    private fun transformLatitude(x: Double, y: Double): Double {
        var result = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y +
            0.1 * x * y + 0.2 * sqrt(abs(x))
        result += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        result += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        result += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return result
    }

    private fun transformLongitude(x: Double, y: Double): Double {
        var result = 300.0 + x + 2.0 * y + 0.1 * x * x +
            0.1 * x * y + 0.1 * sqrt(abs(x))
        result += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        result += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        result += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return result
    }

    private fun isOutsideChina(latitude: Double, longitude: Double): Boolean =
        longitude !in 72.004..137.8347 || latitude !in 0.8293..55.8271
}
