package com.eld.driver.data.models

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.*

data class DailyLog(
    @SerializedName("id") val id: String,
    @SerializedName("date") val date: Date,
    @SerializedName("recapHours") val recapHours: Int,
    @SerializedName("recapMinutes") val recapMinutes: Int,
    @SerializedName("inspections") val inspections: Int,
    @SerializedName("distance") val distance: Double,
    @SerializedName("isCertified") val isCertified: Boolean,
    @SerializedName("hasDefects") val hasDefects: Boolean
) {
    val dateFormatted: String
        get() {
            val formatter = SimpleDateFormat("EEE, MMM dd", Locale.getDefault())
            return formatter.format(date)
        }

    val recapFormatted: String
        get() = "Recap: $recapHours hr $recapMinutes min"

    val inspectionsFormatted: String
        get() = when (inspections) {
            0 -> "No Inspections"
            1 -> "1 inspection"
            else -> "$inspections inspections"
        }

    val defectsFormatted: String?
        get() = if (hasDefects) "1 defect" else null

    val distanceFormatted: String
        get() = "Distance: ${distance.toInt()} mi"

    val statusBadge: String?
        get() = if (!isCertified) "Uncertified" else null
}
