package com.eld.driver.data.models

import com.google.gson.annotations.SerializedName

data class Vehicle(
    @SerializedName("id") val id: Int,
    @SerializedName("vehicleId") val vehicleId: String?,
    @SerializedName("vin") val vin: String?,
    @SerializedName("make") val make: String?,
    @SerializedName("model") val model: String?,
    @SerializedName("year") val year: Int?,
    @SerializedName("active") val active: Boolean
) {
    val vehicleNumber: String get() = vehicleId ?: "Unknown"

    val displayName: String
        get() {
            return when {
                make != null && model != null && year != null -> "$year $make $model"
                make != null && model != null -> "$make $model"
                else -> vehicleNumber
            }
        }
}

data class MobileVehicleListData(
    @SerializedName("vehicles") val vehicles: List<Vehicle>,
    @SerializedName("totalCount") val totalCount: Int,
    @SerializedName("currentVehicleId") val currentVehicleId: Int?
)

data class VehicleSearchResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: MobileVehicleListData?,
    @SerializedName("error") val error: String?,
    @SerializedName("statusCode") val statusCode: Int?
) {
    val vehicles: List<Vehicle>? get() = data?.vehicles
    val currentVehicleId: Int? get() = data?.currentVehicleId
}

enum class ELDConnectionStatus(val displayText: String) {
    DISCONNECTED("Connect"),
    PAIRING("PAIRING"),
    CONNECTED("Connected")
}
