package com.eld.driver.data.models

import com.google.gson.annotations.SerializedName

/**
 * DVIR Inspection models matching backend DTOs
 */

enum class InspectionType {
    @SerializedName("PRE_TRIP")
    PRE_TRIP,

    @SerializedName("POST_TRIP")
    POST_TRIP
}

data class VehicleDefectItem(
    val defect: String,
    val comment: String? = null,
    val photoUrls: List<String>? = null
)

data class AssetDefectItem(
    val defect: String,
    val comment: String? = null,
    val photoUrls: List<String>? = null
)

data class InspectionCreateRequest(
    val vehicleId: Int,
    val inspectionType: InspectionType,
    val inspectionTime: String? = null,
    val vehicleDefects: List<VehicleDefectItem>? = null,
    val assetDefects: List<AssetDefectItem>? = null,
    val notes: String? = null,
    val odometerReading: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationDescription: String? = null
)

data class Inspection(
    val id: Int,
    val inspectionTime: String,
    val vehicleId: Int,
    val vehicleIdString: String? = null,
    val vin: String? = null,
    val driverId: Int,
    val inspectionType: InspectionType,
    val vehicleDefects: List<VehicleDefectItem>? = null,
    val assetDefects: List<AssetDefectItem>? = null,
    val passedWithoutDefects: Boolean,
    val signatureBlobUrl: String? = null,
    val signedOn: String? = null,
    val verifiedOn: String? = null,
    val notes: String? = null,
    val odometerReading: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationDescription: String? = null
)

data class InspectionListItem(
    val id: Int,
    val inspectionTime: String,
    val vehicleId: Int,
    val vehicleIdString: String? = null,
    val inspectionType: InspectionType,
    val passedWithoutDefects: Boolean,
    val signedOn: String? = null,
    val verifiedOn: String? = null
)

data class VehicleDefectsFormResponse(
    val dvirFormId: Int,
    val formName: String,
    val allowedVehicleDefects: List<String>
)

data class AssetDefectsFormResponse(
    val dvirFormId: Int,
    val formName: String,
    val allowedAssetDefects: List<String>
)

data class TickEventRequest(
    val eventType: TickEventType,
    val vehicleId: Int? = null,
    val deviceId: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val location: String? = null,
    val note: String? = null
)

enum class TickEventType {
    LOGIN,
    LOGOUT,
    CONNECTED,
    DISCONNECTED,
    POWER_UP,
    SHUT_DOWN
}

data class DutyStatusChangeRequest(
    val dutyStatus: DutyStatusType,
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
