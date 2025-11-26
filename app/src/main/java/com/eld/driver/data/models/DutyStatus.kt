package com.eld.driver.data.models

import com.google.gson.annotations.SerializedName

/**
 * Duty Status Type - matches backend DutyStatusEnum
 */
enum class DutyStatusType(val displayName: String, val shortName: String) {
    @SerializedName("OFF_DUTY")
    OFF_DUTY("OFF DUTY", "OFF"),

    @SerializedName("ON_DUTY_NOT_DRIVING")
    ON_DUTY_NOT_DRIVING("ON DUTY", "ON"),

    @SerializedName("SLEEPER_BERTH")
    SLEEPER_BERTH("SLEEPER BERTH", "SB"),

    @SerializedName("DRIVING")
    DRIVING("DRIVING", "D"),

    @SerializedName("PERSONAL_CONVEYANCE")
    PERSONAL_CONVEYANCE("PERSONAL CONVEYANCE", "PC"),

    @SerializedName("YARD_MOVE")
    YARD_MOVE("YARD MOVE", "YM")
}

/**
 * Duty Status data class - current duty status response from backend
 */
data class DutyStatus(
    val id: Int,
    val dutyStatus: DutyStatusType,
    val startTime: String,
    val endTime: String? = null,
    val vehicleId: Int? = null,
    val deviceId: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val location: String? = null,
    val odometer: Double? = null,
    val engineHours: Double? = null,
    val note: String? = null,
    val shippingDocumentNumber: String? = null,
    val trailerNumber: String? = null
)
