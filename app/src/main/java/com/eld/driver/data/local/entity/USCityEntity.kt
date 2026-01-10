package com.eld.driver.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for US cities with population >= 5,000.
 * Used for FMCSA-compliant offline location formatting.
 *
 * Contains approximately 7,000 US cities from Census Bureau data.
 * Format output: "{X} miles {direction} of {city}, {state}"
 */
@Entity(
    tableName = "us_cities",
    indices = [
        Index(value = ["latitude"]),
        Index(value = ["longitude"]),
        Index(value = ["latitude", "longitude"]),
        Index(value = ["state"])
    ]
)
data class USCityEntity(
    @PrimaryKey
    val id: Int,
    val name: String,           // City name (e.g., "Chicago")
    val state: String,          // State abbreviation (e.g., "IL")
    val latitude: Double,       // City center latitude
    val longitude: Double,      // City center longitude
    val population: Int         // City population (filtered >= 5000)
)
