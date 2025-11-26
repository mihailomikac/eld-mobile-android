package com.eld.driver.data.models

import com.google.gson.annotations.SerializedName

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
