package com.selfness.mapswork.core

import kotlin.math.*

data class GeoPoint(val latitude: Double, val longitude: Double) {
    val isValid: Boolean
        get() = latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    fun distanceMetersTo(other: GeoPoint): Double {
        require(isValid && other.isValid)
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(other.longitude - longitude)
        val haversine = sin(deltaLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(deltaLon / 2).pow(2)
        return 6_371_000.0 * 2 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
    }
}

enum class VisitState { UNKNOWN, INSIDE, OUTSIDE }
enum class Transition { ENTER, EXIT }

data class Shop(
    val id: String,
    val name: String,
    val point: GeoPoint,
    val radiusMeters: Int = 200,
    val enabled: Boolean = true,
    val visitState: VisitState = VisitState.UNKNOWN,
    val lastNotifiedAtMillis: Long? = null,
)

data class ShopDraft(
    val name: String,
    val point: GeoPoint?,
    val radiusMeters: Int = 200,
    val enabled: Boolean = true,
)
