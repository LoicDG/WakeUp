package com.loic.wakeup.domain

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A WGS84 position in decimal degrees. Pure Kotlin so distance checks can be unit tested. */
data class GeoPoint(val latitude: Double, val longitude: Double) {

    /**
     * Great-circle (haversine) distance in meters. Within a few meters of the ellipsoidal
     * distance at the ~100 m scale the failsafe works at — far below GPS error.
     */
    fun distanceTo(other: GeoPoint): Double {
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** `"45.50170, -73.56730"` — the same shape Google Maps copies, so it round-trips through [parse]. */
    fun format(): String = String.format(Locale.US, "%.5f, %.5f", latitude, longitude)

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_008.8

        private val COORDINATES = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*[,;\s]\s*(-?\d+(?:\.\d+)?)\s*$""")

        /**
         * Parses `"lat, lng"` (comma, semicolon or whitespace separated, dot decimals — what map
         * apps copy). Null when malformed or out of range.
         */
        fun parse(text: String): GeoPoint? {
            val (lat, lng) = COORDINATES.matchEntire(text)?.destructured ?: return null
            val point = GeoPoint(lat.toDoubleOrNull() ?: return null, lng.toDoubleOrNull() ?: return null)
            return point.takeIf { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
        }
    }
}
