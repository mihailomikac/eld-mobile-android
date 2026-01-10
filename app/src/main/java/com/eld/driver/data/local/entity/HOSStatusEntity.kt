package com.eld.driver.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * HOS Violation types - matches backend HosViolationTypeEnum
 */
enum class HOSViolationType(val backendName: String, val displayName: String) {
    DRIVE_TIME_EXCEEDED("DRIVING_11_HOUR", "11-Hour Driving"),
    SHIFT_TIME_EXCEEDED("SHIFT_14_HOUR", "14-Hour Shift"),
    BREAK_REQUIRED("BREAK_30_MIN", "30-Minute Break"),
    CYCLE_TIME_EXCEEDED("CYCLE_60_70_HOUR", "60/70-Hour Cycle");

    companion object {
        fun fromBackendName(name: String): HOSViolationType? {
            return values().find { it.backendName == name }
        }
    }
}

/**
 * Local database entity for cached HOS status.
 * This is a singleton - only one row with id=1.
 * Stores the last calculated HOS values for offline display.
 */
@Entity(tableName = "hos_status")
data class HOSStatusEntity(
    @PrimaryKey
    val id: Int = 1,                         // Singleton - always 1

    // Remaining times in seconds
    val driveTimeRemainingSeconds: Long,     // Remaining from 11 hours
    val shiftTimeRemainingSeconds: Long,     // Remaining from 14 hours
    val breakTimeRemainingSeconds: Long,     // Time until 30-min break required (from 8 hours driving)
    val cycleTimeRemainingSeconds: Long,     // Remaining from 70 hours

    // Used/elapsed times in seconds
    val driveTimeUsedSeconds: Long,          // Total driving in current shift
    val shiftTimeUsedSeconds: Long,          // Time since shift started
    val breakTimeDrivingSeconds: Long,       // Driving time since last qualified break
    val cycleTimeUsedSeconds: Long,          // Total on-duty in last 8 days

    // Total limits in seconds (for progress calculation)
    val driveTimeTotalSeconds: Long = 11 * 60 * 60,   // 11 hours
    val shiftTimeTotalSeconds: Long = 14 * 60 * 60,   // 14 hours
    val breakTimeTotalSeconds: Long = 8 * 60 * 60,    // 8 hours until break
    val cycleTimeTotalSeconds: Long = 70 * 60 * 60,   // 70 hours

    // Shift tracking
    val shiftStartTime: Long? = null,        // When current shift started
    val lastBreakEndTime: Long? = null,      // When last qualified break ended

    // Violations (stored as comma-separated string)
    val violations: String = "",             // e.g., "DRIVE_TIME_EXCEEDED,BREAK_REQUIRED"

    // Violation start times (stored as JSON: {"DRIVE_TIME_EXCEEDED":1702123456789,...})
    val violationStartTimesJson: String = "",

    // Metadata
    val lastCalculatedAt: Long = System.currentTimeMillis(),
    val currentDutyStatus: String? = null    // Current status for reference
) {
    // Progress values (0.0 to 1.0, where 1.0 = full time remaining)
    val driveProgress: Float
        get() = if (driveTimeTotalSeconds > 0)
            driveTimeRemainingSeconds.toFloat() / driveTimeTotalSeconds else 0f

    val shiftProgress: Float
        get() = if (shiftTimeTotalSeconds > 0)
            shiftTimeRemainingSeconds.toFloat() / shiftTimeTotalSeconds else 0f

    val breakProgress: Float
        get() = if (breakTimeTotalSeconds > 0)
            breakTimeRemainingSeconds.toFloat() / breakTimeTotalSeconds else 0f

    val cycleProgress: Float
        get() = if (cycleTimeTotalSeconds > 0)
            cycleTimeRemainingSeconds.toFloat() / cycleTimeTotalSeconds else 0f

    // Formatted time strings
    val driveTimeRemainingFormatted: String get() = formatSeconds(driveTimeRemainingSeconds)
    val shiftTimeRemainingFormatted: String get() = formatSeconds(shiftTimeRemainingSeconds)
    val breakTimeRemainingFormatted: String get() = formatSeconds(breakTimeRemainingSeconds)
    val cycleTimeRemainingFormatted: String get() = formatSeconds(cycleTimeRemainingSeconds)

    val driveTimeTotalFormatted: String get() = formatSeconds(driveTimeTotalSeconds)
    val shiftTimeTotalFormatted: String get() = formatSeconds(shiftTimeTotalSeconds)
    val breakTimeTotalFormatted: String get() = formatSeconds(breakTimeTotalSeconds)
    val cycleTimeTotalFormatted: String get() = formatSeconds(cycleTimeTotalSeconds)

    // Parse violations string to list
    fun getViolationsList(): List<HOSViolationType> {
        if (violations.isBlank()) return emptyList()
        return violations.split(",").mapNotNull { name ->
            try { HOSViolationType.valueOf(name.trim()) } catch (e: Exception) { null }
        }
    }

    // Parse violation start times JSON to map
    fun getViolationStartTimesMap(): Map<HOSViolationType, Long> {
        if (violationStartTimesJson.isBlank()) return emptyMap()
        return try {
            val result = mutableMapOf<HOSViolationType, Long>()
            // Simple JSON parsing: {"DRIVE_TIME_EXCEEDED":123456,...}
            val cleaned = violationStartTimesJson.trim().removeSurrounding("{", "}")
            if (cleaned.isNotBlank()) {
                cleaned.split(",").forEach { pair ->
                    val parts = pair.split(":")
                    if (parts.size == 2) {
                        val key = parts[0].trim().removeSurrounding("\"")
                        val value = parts[1].trim().toLongOrNull()
                        if (value != null) {
                            try {
                                val violationType = HOSViolationType.valueOf(key)
                                result[violationType] = value
                            } catch (e: Exception) { /* ignore invalid keys */ }
                        }
                    }
                }
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun hasViolations(): Boolean = violations.isNotBlank()

    private fun formatSeconds(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return String.format("%02d:%02d", hours, minutes)
    }
}
