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
    val dvirFormId: Int? = null,
    val formName: String? = null,
    val allowedVehicleDefects: List<String>? = null,
    val defectCategories: List<DefectCategoryResponse>? = null
)

data class AssetDefectsFormResponse(
    val dvirFormId: Int? = null,
    val formName: String? = null,
    val allowedAssetDefects: List<String>? = null,
    val defectCategories: List<DefectCategoryResponse>? = null
)

data class DefectCategoryResponse(
    val id: Int? = null,
    val name: String? = null,
    val defects: List<DefectItemResponse>? = null
)

data class DefectItemResponse(
    val id: Int? = null,
    val name: String? = null
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
    @SerializedName("LOGIN")
    LOGIN,
    @SerializedName("LOGOUT")
    LOGOUT,
    @SerializedName("CONNECTED")
    CONNECTED,
    @SerializedName("DISCONNECTED")
    DISCONNECTED,
    @SerializedName("POWER_UP")
    POWER_UP,
    @SerializedName("POWER_DOWN")
    POWER_DOWN,
    @SerializedName("SHUT_DOWN")
    SHUT_DOWN,
    @SerializedName("INTERMEDIATE")
    INTERMEDIATE
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
