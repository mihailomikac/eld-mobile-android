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
    @SerializedName("hasInspections") val hasInspections: Boolean,
    @SerializedName("violationCount") val violationCount: Int = 0,
    @SerializedName("violations") val violations: List<HosViolationDto> = emptyList(),
    @SerializedName("formMannerErrorCount") val formMannerErrorCount: Int = 0,
    @SerializedName("formMannerErrors") val formMannerErrors: List<String> = emptyList(),
    @SerializedName("inspectionCount") val inspectionCount: Int = 0
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

    val hasViolations: Boolean
        get() = violationCount > 0

    val hasFormMannerErrors: Boolean
        get() = formMannerErrorCount > 0

    /**
     * Check if this log is for today's date.
     * Cannot certify today's log per FMCSA rules.
     */
    val isToday: Boolean
        get() {
            return try {
                val logDate = date.substringBefore("T")
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                logDate == today
            } catch (e: Exception) {
                false
            }
        }

    /**
     * Determines if this log can be certified.
     * Cannot certify if already certified or if it's today's log.
     */
    val canCertify: Boolean
        get() = !isCertified && !isToday
}

/**
 * Co-Driver basic info
 */
data class DriverBasicInfoDto(
    @SerializedName("id") val id: Int,
    @SerializedName("driverName") val driverName: String?
)

/**
 * Response wrapper for driver events API
 */
data class DriverEventsData(
    @SerializedName("dutyStatusEvents") val dutyStatusEvents: List<DutyStatusEventDto>,
    @SerializedName("tickEvents") val tickEvents: List<TickEventDto>,
    @SerializedName("date") val date: String,
    @SerializedName("summary") val summary: DutyStatusSummary?,
    @SerializedName("coDriversInfo") val coDriversInfo: List<DriverBasicInfoDto>? = null,
    @SerializedName("coDriversDutyEvents") val coDriversDutyEvents: Map<Int, List<DutyStatusEventDto>>? = null,
    @SerializedName("vehicle") val vehicle: String?,
    @SerializedName("vehicleInfo") val vehicleInfo: VehicleInfoDto? = null,
    @SerializedName("isCertified") val isCertified: Boolean,
    @SerializedName("hos") val hos: HosDataDto? = null,
    @SerializedName("violations") val violations: List<HosViolationDto> = emptyList(),
    @SerializedName("lastSyncTime") val lastSyncTime: String? = null,
    @SerializedName("cycleRule") val cycleRule: String? = null,
    @SerializedName("formMannerErrors") val formMannerErrors: List<String> = emptyList()
) {
    // Helper property for backward compatibility with code that uses coDriver
    val coDriver: String?
        get() = coDriversInfo?.firstOrNull()?.driverName
}

/**
 * Vehicle Info DTO
 */
data class VehicleInfoDto(
    @SerializedName("id") val id: Int,
    @SerializedName("vehicleNumber") val vehicleNumber: String?,
    @SerializedName("vin") val vin: String?
)

/**
 * HOS Data DTO - current HOS clock values
 */
data class HosDataDto(
    @SerializedName("breakMinutes") val breakMinutes: Int,
    @SerializedName("driveMinutes") val driveMinutes: Int,
    @SerializedName("shiftMinutes") val shiftMinutes: Int,
    @SerializedName("cycleMinutes") val cycleMinutes: Int,
    @SerializedName("cycleLeftForTomorrow") val cycleLeftForTomorrow: Int?,
    @SerializedName("lastSyncTime") val lastSyncTime: String?,
    @SerializedName("isEdited") val isEdited: Boolean = false
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
    @SerializedName("note") val note: String? = null,
    @SerializedName("isActive") val isActive: Boolean,
    @SerializedName("actualStartTime") val actualStartTime: Long? = null,
    @SerializedName("actualEndTime") val actualEndTime: Long? = null,
    /** FMCSA Event Record Origin (1=Auto, 2=Driver, 3=Carrier, 4=Unidentified) */
    @SerializedName("eventRecordOrigin") val eventRecordOrigin: Int? = null,
    /** FMCSA Event Record Status (1=Active, 2=Changed, 3=Requested, 4=Rejected) */
    @SerializedName("eventRecordStatus") val eventRecordStatus: Int? = null
) {
    val durationFormatted: String
        get() {
            val minutes = durationMinutes ?: 0
            val hours = minutes / 60
            val mins = minutes % 60
            return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        }

    /**
     * Format start time from UTC to local time for display.
     * Uses device timezone by default.
     */
    val timeFormatted: String
        get() = getTimeFormatted(TimeZone.getDefault())

    /**
     * Format start time from UTC to specified timezone for display.
     */
    fun getTimeFormatted(tz: TimeZone): String {
        return try {
            // Input is in UTC
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")

            // Output in specified timezone
            val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            outputFormat.timeZone = tz

            val cleanTime = startTime.substringBefore("Z").substringBefore("+").take(19)
            val date = inputFormat.parse(cleanTime)
            date?.let { outputFormat.format(it) } ?: startTime
        } catch (e: Exception) {
            startTime.substringAfter("T").take(5)
        }
    }

    /**
     * Get start time as decimal hours in device timezone (for graph).
     * Uses device timezone by default.
     */
    val startTimeLocalHours: Float
        get() = getStartTimeHours(TimeZone.getDefault())

    /**
     * Get start time as decimal hours in specified timezone (for graph).
     */
    fun getStartTimeHours(tz: TimeZone): Float {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")

            val cleanTime = startTime.substringBefore("Z").substringBefore("+").take(19)
            val date = inputFormat.parse(cleanTime)

            if (date != null) {
                val calendar = Calendar.getInstance(tz)
                calendar.time = date
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
     * Get end time as decimal hours in device timezone (for graph).
     * Uses device timezone by default.
     */
    val endTimeLocalHours: Float?
        get() = getEndTimeHours(TimeZone.getDefault())

    /**
     * Get end time as decimal hours in specified timezone (for graph).
     */
    fun getEndTimeHours(tz: TimeZone): Float? {
        if (endTime == null) return null
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")

            val cleanTime = endTime.substringBefore("Z").substringBefore("+").take(19)
            val date = inputFormat.parse(cleanTime)

            if (date != null) {
                val calendar = Calendar.getInstance(tz)
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
