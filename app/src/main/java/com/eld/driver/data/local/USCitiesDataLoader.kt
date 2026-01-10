package com.eld.driver.data.local

import android.content.Context
import android.util.Log
import com.eld.driver.data.local.entity.USCityEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Loads US cities data from bundled JSON file into Room database.
 *
 * Data source: US Census Bureau Gazetteer Files
 * https://www.census.gov/geographies/reference-files/time-series/geo/gazetteer-files.html
 *
 * The JSON file should contain cities with population >= 5,000 for FMCSA compliance.
 */
class USCitiesDataLoader(private val context: Context) {

    companion object {
        private const val TAG = "USCitiesDataLoader"
        private const val CITIES_JSON_FILE = "us_cities.json"
    }

    private val database = ELDDatabase.getInstance(context)
    private val usCityDao = database.usCityDao()

    /**
     * Load cities from JSON file if database is empty.
     * Called on app startup.
     */
    suspend fun loadCitiesIfNeeded() {
        withContext(Dispatchers.IO) {
            try {
                val hasData = usCityDao.hasData()
                if (hasData) {
                    val count = usCityDao.getCityCount()
                    Log.d(TAG, "Cities database already populated with $count cities")
                    return@withContext
                }

                Log.d(TAG, "Loading cities from JSON file...")
                val startTime = System.currentTimeMillis()

                val cities = loadCitiesFromJson()
                if (cities.isNotEmpty()) {
                    // Batch insert for performance
                    usCityDao.insertAll(cities)
                    val elapsed = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Loaded ${cities.size} cities in ${elapsed}ms")
                } else {
                    Log.w(TAG, "No cities found in JSON file")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load cities: ${e.message}", e)
            }
        }
    }

    /**
     * Force reload cities from JSON file.
     * Clears existing data and reloads.
     */
    suspend fun reloadCities() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Reloading cities database...")
                usCityDao.deleteAll()
                loadCitiesIfNeeded()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reload cities: ${e.message}", e)
            }
        }
    }

    /**
     * Parse cities from JSON file in assets.
     *
     * Expected JSON format:
     * [
     *   {
     *     "id": 1,
     *     "name": "New York",
     *     "state": "NY",
     *     "latitude": 40.7128,
     *     "longitude": -74.0060,
     *     "population": 8336817
     *   },
     *   ...
     * ]
     */
    private fun loadCitiesFromJson(): List<USCityEntity> {
        val cities = mutableListOf<USCityEntity>()

        try {
            val inputStream = context.assets.open(CITIES_JSON_FILE)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val city = USCityEntity(
                    id = obj.getInt("id"),
                    name = obj.getString("name"),
                    state = obj.getString("state"),
                    latitude = obj.getDouble("latitude"),
                    longitude = obj.getDouble("longitude"),
                    population = obj.getInt("population")
                )
                cities.add(city)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cities JSON: ${e.message}", e)
        }

        return cities
    }

    /**
     * Get database statistics.
     */
    suspend fun getStats(): CityDatabaseStats {
        return withContext(Dispatchers.IO) {
            CityDatabaseStats(
                totalCities = usCityDao.getCityCount(),
                isPopulated = usCityDao.hasData()
            )
        }
    }
}

data class CityDatabaseStats(
    val totalCities: Int,
    val isPopulated: Boolean
)
