package com.eld.driver.location

import android.content.Context
import android.util.Log
import com.eld.driver.data.local.ELDDatabase
import com.eld.driver.data.local.entity.USCityEntity
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * FMCSA-compliant location formatter.
 *
 * Produces location strings in format: "{X} miles {direction} of {city}, {state}"
 * Example: "5 miles NE of Chicago, IL"
 *
 * Works completely offline using local US city database (~7,000 cities with pop >= 5,000).
 * Per FMCSA 49 CFR 395.8 requirements for ELD location recording.
 */
class FMCSALocationFormatter private constructor(context: Context) {

    companion object {
        private const val TAG = "FMCSALocationFormatter"

        // Earth's radius in miles for Haversine formula
        private const val EARTH_RADIUS_MILES = 3958.8

        // Initial search radius in degrees (~69 miles per degree of latitude)
        private const val INITIAL_SEARCH_RADIUS_DEGREES = 1.0

        // Maximum search expansions (1° -> 2° -> 4° = ~276 miles max)
        private const val MAX_SEARCH_EXPANSIONS = 3

        // Minimum population per FMCSA requirement
        private const val MIN_POPULATION = 5000

        // Cache tolerance in degrees (~100 meters)
        private const val CACHE_TOLERANCE_DEGREES = 0.001

        @Volatile
        private var INSTANCE: FMCSALocationFormatter? = null

        fun getInstance(context: Context): FMCSALocationFormatter {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FMCSALocationFormatter(context.applicationContext)
                    .also { INSTANCE = it }
            }
        }
    }

    private val database = ELDDatabase.getInstance(context)
    private val usCityDao = database.usCityDao()

    // Simple cache for repeated lookups (e.g., vehicle stationary)
    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null
    private var lastFormattedLocation: String? = null

    /**
     * Format location as FMCSA-compliant string.
     *
     * @param latitude GPS latitude in decimal degrees
     * @param longitude GPS longitude in decimal degrees
     * @return Formatted string like "5 miles NE of Chicago, IL" or coordinates as fallback
     */
    suspend fun formatLocation(latitude: Double, longitude: Double): String {
        return try {
            // Check cache first (for stationary vehicle)
            val lastLat = lastLatitude
            val lastLng = lastLongitude
            val cachedResult = lastFormattedLocation

            if (lastLat != null && lastLng != null && cachedResult != null) {
                if (abs(latitude - lastLat) < CACHE_TOLERANCE_DEGREES &&
                    abs(longitude - lastLng) < CACHE_TOLERANCE_DEGREES) {
                    Log.d(TAG, "Using cached location: $cachedResult")
                    return cachedResult
                }
            }

            val result = findNearestCityAndFormat(latitude, longitude)

            // Update cache
            lastLatitude = latitude
            lastLongitude = longitude
            lastFormattedLocation = result

            result
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting location: ${e.message}", e)
            formatCoordinates(latitude, longitude)
        }
    }

    /**
     * Find nearest city and format as FMCSA location string.
     * Always finds the nearest city - even if location is outside US.
     */
    private suspend fun findNearestCityAndFormat(lat: Double, lng: Double): String {
        var searchRadius = INITIAL_SEARCH_RADIUS_DEGREES
        var nearestCity: USCityEntity? = null
        var nearestDistance = Double.MAX_VALUE

        // First try bounding box search (fast for locations within/near US)
        for (attempt in 1..MAX_SEARCH_EXPANSIONS) {
            val cities = usCityDao.getCitiesInBoundingBox(
                minLat = lat - searchRadius,
                maxLat = lat + searchRadius,
                minLng = lng - searchRadius,
                maxLng = lng + searchRadius,
                minPopulation = MIN_POPULATION
            )

            Log.d(TAG, "Search attempt $attempt (radius: $searchRadius°): found ${cities.size} cities")

            for (city in cities) {
                val distance = haversineDistanceMiles(lat, lng, city.latitude, city.longitude)
                if (distance < nearestDistance) {
                    nearestDistance = distance
                    nearestCity = city
                }
            }

            // Found a city - no need to expand further
            if (nearestCity != null) break

            // Double the search radius for next attempt
            searchRadius *= 2
        }

        // If bounding box found nothing, search ALL cities (location is far from US)
        if (nearestCity == null) {
            Log.d(TAG, "Bounding box search found nothing, searching all cities...")
            val allCities = usCityDao.getAllCities(MIN_POPULATION)

            for (city in allCities) {
                val distance = haversineDistanceMiles(lat, lng, city.latitude, city.longitude)
                if (distance < nearestDistance) {
                    nearestDistance = distance
                    nearestCity = city
                }
            }

            Log.d(TAG, "Global search complete: nearest city is ${nearestCity?.name}, ${nearestCity?.state} at ${nearestDistance.roundToInt()} miles")
        }

        // Still no city found - database is empty
        if (nearestCity == null) {
            Log.w(TAG, "No cities in database for ($lat, $lng)")
            return formatCoordinates(lat, lng)
        }

        // Calculate cardinal direction FROM the city TO the point
        val direction = calculateCardinalDirection(
            fromLat = nearestCity.latitude,
            fromLng = nearestCity.longitude,
            toLat = lat,
            toLng = lng
        )

        val distanceRounded = nearestDistance.roundToInt()

        Log.d(TAG, "Nearest city: ${nearestCity.name}, ${nearestCity.state} " +
                "(${nearestDistance.roundToInt()} miles $direction)")

        // Format result per FMCSA requirements
        return if (distanceRounded < 1) {
            // Within 1 mile of city center - just show city
            "${nearestCity.name}, ${nearestCity.state}"
        } else {
            // Standard FMCSA format
            "$distanceRounded mi. $direction of ${nearestCity.name}, ${nearestCity.state}"
        }
    }

    /**
     * Calculate distance between two points using Haversine formula.
     *
     * @return Distance in miles
     */
    private fun haversineDistanceMiles(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)

        val a = sin(dLat / 2).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) * sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_MILES * c
    }

    /**
     * Calculate 8-point cardinal direction.
     *
     * Direction is FROM the city TO the given point.
     * Returns: N, NE, E, SE, S, SW, W, NW
     */
    private fun calculateCardinalDirection(
        fromLat: Double, fromLng: Double,
        toLat: Double, toLng: Double
    ): String {
        val dLat = toLat - fromLat
        val dLng = toLng - fromLng

        // Calculate bearing in degrees (0-360, where 0=N, 90=E, 180=S, 270=W)
        val bearingRad = atan2(dLng * cos(Math.toRadians(fromLat)), dLat)
        val bearingDeg = (Math.toDegrees(bearingRad) + 360) % 360

        // Convert bearing to 8-point cardinal direction
        return when {
            bearingDeg >= 337.5 || bearingDeg < 22.5 -> "N"
            bearingDeg >= 22.5 && bearingDeg < 67.5 -> "NE"
            bearingDeg >= 67.5 && bearingDeg < 112.5 -> "E"
            bearingDeg >= 112.5 && bearingDeg < 157.5 -> "SE"
            bearingDeg >= 157.5 && bearingDeg < 202.5 -> "S"
            bearingDeg >= 202.5 && bearingDeg < 247.5 -> "SW"
            bearingDeg >= 247.5 && bearingDeg < 292.5 -> "W"
            bearingDeg >= 292.5 && bearingDeg < 337.5 -> "NW"
            else -> "N"
        }
    }

    /**
     * Format coordinates as fallback when no city is found.
     * Matches FMCSA requirement for lat/lng display.
     */
    private fun formatCoordinates(lat: Double, lng: Double): String {
        return String.format("%.4f, %.4f", lat, lng)
    }

    /**
     * Clear the location cache.
     * Call when significant location change expected.
     */
    fun clearCache() {
        lastLatitude = null
        lastLongitude = null
        lastFormattedLocation = null
    }

    /**
     * Check if the city database is populated.
     */
    suspend fun isDatabasePopulated(): Boolean {
        return usCityDao.hasData()
    }

    /**
     * Get count of cities in database.
     */
    suspend fun getCityCount(): Int {
        return usCityDao.getCityCount()
    }
}
