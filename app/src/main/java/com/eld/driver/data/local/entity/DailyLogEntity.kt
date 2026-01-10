package com.eld.driver.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Room Entity for storing daily logs locally.
 * Synced from server on initial login and periodically.
 */
@Entity(tableName = "daily_logs")
@TypeConverters(DailyLogConverters::class)
data class DailyLogEntity(
    @PrimaryKey
    val date: String,  // Format: yyyy-MM-dd

    val dayOfWeek: String,
    val month: String,
    val day: Int,

    val recapHours: Int,
    val recapMinutes: Int,

    val defectsCount: Int,
    val distanceMiles: Double,

    val isCertified: Boolean,
    val hasInspections: Boolean,

    val violationCount: Int,
    val violationsJson: String,  // JSON serialized list of violations

    val formMannerErrorCount: Int,
    val formMannerErrorsJson: String,  // JSON serialized list of strings

    val inspectionCount: Int,

    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Type converters for DailyLogEntity
 */
class DailyLogConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromViolationsList(violations: List<ViolationData>): String {
        return gson.toJson(violations)
    }

    @TypeConverter
    fun toViolationsList(json: String): List<ViolationData> {
        val type = object : TypeToken<List<ViolationData>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    @TypeConverter
    fun fromStringList(list: List<String>): String {
        return gson.toJson(list)
    }

    @TypeConverter
    fun toStringList(json: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
}

/**
 * Simplified violation data for local storage
 */
data class ViolationData(
    val id: Int,
    val violationType: String,
    val startTime: String,
    val endTime: String?,
    val violationError: String?,
    val overLimitMinutes: Int?,
    val vehicleId: Int?,
    val vehicleNumber: String?,
    val isAcknowledged: Boolean
)
