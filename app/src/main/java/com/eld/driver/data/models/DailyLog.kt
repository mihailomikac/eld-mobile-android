package com.eld.driver.data.models

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.*

/**
 * Response wrapper for driver logs API
 */
data class DriverLogsData(
    @SerializedName("logs") val logs: List<DailyLogDto>,
    @SerializedName("totalDays") val totalDays: Int
)

/**
 * Daily Log DTO - matches backend DailyLogDto
 */
data class DailyLogDto(
    @SerializedName("date") val date: String,
    @SerializedName("dayOfWeek") val dayOfWeek: String,
    @SerializedName("month") val month: String,
    @SerializedName("day") val day: Int,
    @SerializedName("recapHours") val recapHours: Int,
    @SerializedName("recapMinutes") val recapMinutes: Int,
    @SerializedName("defectsCount") val defectsCount: Int,
    @SerializedName("distanceMiles") val distanceMiles: Double,
    @SerializedName("isCertified") val isCertified: Boolean,
    @SerializedName("hasInspections") val hasInspections: Boolean
) {
    val dateFormatted: String
        get() = "$dayOfWeek, $month $day"

    val recapFormatted: String
        get() = "$recapHours hr $recapMinutes min"

    val inspectionsFormatted: String
        get() = when {
            defectsCount > 0 -> "$defectsCount defect${if (defectsCount > 1) "s" else ""}"
            hasInspections -> "Inspections OK"
            else -> "No Inspections"
        }

    val distanceFormatted: String
        get() = "${distanceMiles.toInt()} mi"
}

/**
 * Response wrapper for driver events API
 */
data class DriverEventsData(
    @SerializedName("dutyStatusEvents") val dutyStatusEvents: List<DutyStatusEventDto>,
    @SerializedName("tickEvents") val tickEvents: List<TickEventDto>,
    @SerializedName("date") val date: String,
    @SerializedName("summary") val summary: DutyStatusSummary?,
    @SerializedName("coDriver") val coDriver: String?,
    @SerializedName("vehicle") val vehicle: String?,
    @SerializedName("isCertified") val isCertified: Boolean
)

/**
 * Duty Status Event DTO
 */
data class DutyStatusEventDto(
    @SerializedName("id") val id: Int,
    @SerializedName("dutyStatus") val dutyStatus: DutyStatusType,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("endTime") val endTime: String?,
    @SerializedName("durationMinutes") val durationMinutes: Int?,
    @SerializedName("location") val location: String?,
    @SerializedName("latitude") val latitude: Double?,
    @SerializedName("longitude") val longitude: Double?,
    @SerializedName("vehicleId") val vehicleId: Int?,
    @SerializedName("vehicleNumber") val vehicleNumber: String?,
    @SerializedName("odometer") val odometer: Double?,
    @SerializedName("engineHours") val engineHours: Double?,
    @SerializedName("isActive") val isActive: Boolean
) {
    val durationFormatted: String
        get() {
            val minutes = durationMinutes ?: 0
            val hours = minutes / 60
            val mins = minutes % 60
            return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        }

    /**
     * Format start time from UTC to local time for display
     */
    val timeFormatted: String
        get() {
            return try {
                // Input is in UTC
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                inputFormat.timeZone = TimeZone.getTimeZone("UTC")

                // Output in local timezone
                val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                outputFormat.timeZone = TimeZone.getDefault()

                val cleanTime = startTime.substringBefore("Z").substringBefore("+").take(19)
                val date = inputFormat.parse(cleanTime)
                date?.let { outputFormat.format(it) } ?: startTime
            } catch (e: Exception) {
                startTime.substringAfter("T").take(5)
            }
        }

    /**
     * Get start time as decimal hours in LOCAL timezone (for graph)
     */
    val startTimeLocalHours: Float
        get() {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                inputFormat.timeZone = TimeZone.getTimeZone("UTC")

                val cleanTime = startTime.substringBefore("Z").substringBefore("+").take(19)
                val date = inputFormat.parse(cleanTime)

                if (date != null) {
                    val calendar = Calendar.getInstance()
                    calendar.time = date
                    // Calendar automatically converts to local timezone
                    val hours = calendar.get(Calendar.HOUR_OF_DAY)
                    val minutes = calendar.get(Calendar.MINUTE)
                    val seconds = calendar.get(Calendar.SECOND)
                    hours + minutes / 60f + seconds / 3600f
                } else {
                    0f
                }
            } catch (e: Exception) {
                0f
            }
        }

    /**
     * Get end time as decimal hours in LOCAL timezone (for graph)
     */
    val endTimeLocalHours: Float?
        get() {
            if (endTime == null) return null
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                inputFormat.timeZone = TimeZone.getTimeZone("UTC")

                val cleanTime = endTime.substringBefore("Z").substringBefore("+").take(19)
                val date = inputFormat.parse(cleanTime)

                if (date != null) {
                    val calendar = Calendar.getInstance()
                    calendar.time = date
                    val hours = calendar.get(Calendar.HOUR_OF_DAY)
                    val minutes = calendar.get(Calendar.MINUTE)
                    val seconds = calendar.get(Calendar.SECOND)
                    hours + minutes / 60f + seconds / 3600f
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
}

/**
 * Tick Event DTO
 */
data class TickEventDto(
    @SerializedName("id") val id: Int,
    @SerializedName("eventType") val eventType: TickEventType,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("location") val location: String?,
    @SerializedName("latitude") val latitude: Double?,
    @SerializedName("longitude") val longitude: Double?,
    @SerializedName("deviceId") val deviceId: Int?,
    @SerializedName("vehicleId") val vehicleId: Int?,
    @SerializedName("vehicleNumber") val vehicleNumber: String?
)

/**
 * Duty Status Summary
 */
data class DutyStatusSummary(
    @SerializedName("offDutyMinutes") val offDutyMinutes: Int,
    @SerializedName("sleeperBerthMinutes") val sleeperBerthMinutes: Int,
    @SerializedName("drivingMinutes") val drivingMinutes: Int,
    @SerializedName("onDutyNotDrivingMinutes") val onDutyNotDrivingMinutes: Int,
    @SerializedName("totalOnDutyMinutes") val totalOnDutyMinutes: Int
) {
    fun getFormattedTime(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return "${hours}h ${mins}m"
    }
}
