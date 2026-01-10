package com.eld.driver.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eld.driver.data.local.entity.USCityEntity

/**
 * Data Access Object for US cities database.
 * Provides spatial queries for FMCSA-compliant location lookup.
 *
 * Uses bounding box queries with indexed lat/lng for fast nearest-city lookup.
 */
@Dao
interface USCityDao {

    // ═══════════════════════════════════════════════════════════════
    // SPATIAL QUERIES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get cities within a bounding box for efficient spatial lookup.
     * Uses lat/lng indexes for fast filtering before distance calculation.
     *
     * @param minLat Minimum latitude (south boundary)
     * @param maxLat Maximum latitude (north boundary)
     * @param minLng Minimum longitude (west boundary)
     * @param maxLng Maximum longitude (east boundary)
     * @param minPopulation Minimum population filter (default 5000 per FMCSA)
     */
    @Query("""
        SELECT * FROM us_cities
        WHERE latitude BETWEEN :minLat AND :maxLat
        AND longitude BETWEEN :minLng AND :maxLng
        AND population >= :minPopulation
    """)
    suspend fun getCitiesInBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
        minPopulation: Int = 5000
    ): List<USCityEntity>

    /**
     * Get cities by state abbreviation.
     */
    @Query("SELECT * FROM us_cities WHERE state = :stateAbbrev AND population >= :minPopulation")
    suspend fun getCitiesByState(stateAbbrev: String, minPopulation: Int = 5000): List<USCityEntity>

    // ═══════════════════════════════════════════════════════════════
    // GLOBAL QUERIES (for locations outside US)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get ALL cities for global nearest-city search.
     * Used when bounding box search finds nothing (location outside US).
     */
    @Query("SELECT * FROM us_cities WHERE population >= :minPopulation")
    suspend fun getAllCities(minPopulation: Int = 5000): List<USCityEntity>

    // ═══════════════════════════════════════════════════════════════
    // COUNT & VERIFICATION QUERIES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get total city count (for database verification).
     */
    @Query("SELECT COUNT(*) FROM us_cities WHERE population >= :minPopulation")
    suspend fun getCityCount(minPopulation: Int = 5000): Int

    /**
     * Check if database is populated.
     */
    @Query("SELECT COUNT(*) > 0 FROM us_cities")
    suspend fun hasData(): Boolean

    // ═══════════════════════════════════════════════════════════════
    // INSERT OPERATIONS (for initial data load)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Insert a single city.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(city: USCityEntity)

    /**
     * Insert multiple cities (batch operation for initial load).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cities: List<USCityEntity>)

    // ═══════════════════════════════════════════════════════════════
    // DELETE OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Clear all cities (for re-import).
     */
    @Query("DELETE FROM us_cities")
    suspend fun deleteAll()
}
