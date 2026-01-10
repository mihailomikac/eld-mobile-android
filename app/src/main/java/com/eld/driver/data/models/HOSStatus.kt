package com.eld.driver.data.models

import com.google.gson.annotations.SerializedName

/**
 * Request model for sending calculated HOS to backend
 * Must match backend's MobileDriverHosUpdateRequest exactly
 */
data class HOSUpdateRequest(
    @SerializedName("date") val date: String,  // ISO 8601 format: "2025-11-28T00:00:00Z"
    @SerializedName("breakMinutes") val breakMinutes: Int,  // Remaining minutes
    @SerializedName("driveMinutes") val driveMinutes: Int,  // Remaining minutes
    @SerializedName("shiftMinutes") val shiftMinutes: Int,  // Remaining minutes
    @SerializedName("cycleMinutes") val cycleMinutes: Int,  // Remaining minutes
    @SerializedName("cycleLeftForTomorrow") val cycleLeftForTomorrow: Int? = null
)

/**
 * Response model for HOS from backend
 */
data class HOSResponse(
    @SerializedName("date") val date: String,
    @SerializedName("breakMinutes") val breakMinutes: Int,
    @SerializedName("driveMinutes") val driveMinutes: Int,
    @SerializedName("shiftMinutes") val shiftMinutes: Int,
    @SerializedName("cycleMinutes") val cycleMinutes: Int,
    @SerializedName("cycleLeftForTomorrow") val cycleLeftForTomorrow: Int?,
    @SerializedName("lastSyncTime") val lastSyncTime: String?,
    @SerializedName("isEdited") val isEdited: Boolean
)

data class HOSStatus(
    @SerializedName("breakTimeRemaining") val breakTimeRemaining: Int,
    @SerializedName("driveTimeRemaining") val driveTimeRemaining: Int,
    @SerializedName("shiftTimeRemaining") val shiftTimeRemaining: Int,
    @SerializedName("cycleTimeRemaining") val cycleTimeRemaining: Int,
    @SerializedName("breakTimeUsed") val breakTimeUsed: Int,
    @SerializedName("driveTimeUsed") val driveTimeUsed: Int,
    @SerializedName("shiftTimeUsed") val shiftTimeUsed: Int,
    @SerializedName("cycleTimeUsed") val cycleTimeUsed: Int,
    @SerializedName("breakTimeTotal") val breakTimeTotal: Int,
    @SerializedName("driveTimeTotal") val driveTimeTotal: Int,
    @SerializedName("shiftTimeTotal") val shiftTimeTotal: Int,
    @SerializedName("cycleTimeTotal") val cycleTimeTotal: Int
) {
    // Progress represents REMAINING time (1.0 = full time remaining)
    val breakProgress: Float
        get() = if (breakTimeTotal > 0) breakTimeRemaining.toFloat() / breakTimeTotal else 0f

    val driveProgress: Float
        get() = if (driveTimeTotal > 0) driveTimeRemaining.toFloat() / driveTimeTotal else 0f

    val shiftProgress: Float
        get() = if (shiftTimeTotal > 0) shiftTimeRemaining.toFloat() / shiftTimeTotal else 0f

    val cycleProgress: Float
        get() = if (cycleTimeTotal > 0) cycleTimeRemaining.toFloat() / cycleTimeTotal else 0f

    // Formatted time strings
    val breakTimeRemainingFormatted: String get() = formatMinutes(breakTimeRemaining)
    val driveTimeRemainingFormatted: String get() = formatMinutes(driveTimeRemaining)
    val shiftTimeRemainingFormatted: String get() = formatMinutes(shiftTimeRemaining)
    val cycleTimeRemainingFormatted: String get() = formatHours(cycleTimeRemaining)

    val breakTimeTotalFormatted: String get() = formatMinutes(breakTimeTotal)
    val driveTimeTotalFormatted: String get() = formatMinutes(driveTimeTotal)
    val shiftTimeTotalFormatted: String get() = formatMinutes(shiftTimeTotal)
    val cycleTimeTotalFormatted: String get() = formatHours(cycleTimeTotal)

    private fun formatMinutes(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return String.format("%02d:%02d", hours, mins)
    }

    private fun formatHours(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return String.format("%02d:%02d", hours, mins)
    }
}

/**
 * Request model for syncing HOS data including violations
 * Must match backend's MobileHosSyncRequest exactly
 */
data class HOSSyncRequest(
    @SerializedName("breakMinutes") val breakMinutes: Int,
    @SerializedName("driveMinutes") val driveMinutes: Int,
    @SerializedName("shiftMinutes") val shiftMinutes: Int,
    @SerializedName("cycleMinutes") val cycleMinutes: Int,
    @SerializedName("cycleLeftForTomorrow") val cycleLeftForTomorrow: Int? = null,
    /** List of active violations (if any) - uses HosViolationItem, not the batch request */
    @SerializedName("activeViolations") val activeViolations: List<HosViolationItem>? = null
)

/**
 * Response model for HOS sync
 */
data class HOSSyncResponse(
    @SerializedName("syncTime") val syncTime: String,
    @SerializedName("violationsRecorded") val violationsRecorded: Int,
    @SerializedName("message") val message: String
)

/**
 * Response model for creating violations (BATCH)
 * Must match backend's MobileCreateViolationResponse exactly
 */
data class CreateViolationResponse(
    /** List of created violation IDs */
    @SerializedName("violationIds") val violationIds: List<Int> = emptyList(),
    /** Number of violations deleted before creating new ones */
    @SerializedName("deletedCount") val deletedCount: Int = 0,
    /** Number of violations created */
    @SerializedName("createdCount") val createdCount: Int = 0,
    /** Descriptive message about the operation result */
    @SerializedName("message") val message: String = ""
)
