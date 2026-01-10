package com.eld.driver.data.models

import com.google.gson.annotations.SerializedName

/**
 * DVIR Inspection models matching backend DTOs
 */

/**
 * FMCSA ELD Event Record Origin (Table 7 - 49 CFR Part 395, Appendix A)
 * Identifies WHO created or modified the ELD record.
 */
object FmcsaEventRecordOrigin {
    /** Origin 1: Automatically recorded by ELD (e.g., auto-drive detection) */
    const val AUTO_BY_ELD = 1
    /** Origin 2: Edited or entered by the Driver */
    const val DRIVER = 2
    /** Origin 3: Edit requested by another authenticated user (motor carrier) */
    const val MOTOR_CARRIER = 3
    /** Origin 4: Assumed from Unidentified Driver profile */
    const val UNIDENTIFIED_DRIVER = 4
}

/**
 * FMCSA ELD Event Record Status (Table 8 - 49 CFR Part 395, Appendix A)
 * Tracks the lifecycle status of an ELD record.
 */
object FmcsaEventRecordStatus {
    /** Status 1: Active - The current valid record */
    const val ACTIVE = 1
    /** Status 2: Inactive-Changed - Original record that was edited */
    const val INACTIVE_CHANGED = 2
    /** Status 3: Inactive-Change Requested - Pending edit request from carrier */
    const val INACTIVE_CHANGE_REQUESTED = 3
    /** Status 4: Inactive-Change Rejected - Driver rejected the edit request */
    const val INACTIVE_CHANGE_REJECTED = 4
}

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
    val vin: String? = null,
    val inspectionType: InspectionType,
    val passedWithoutDefects: Boolean,
    val hasDefects: Boolean = false,
    val totalPhotosCount: Int = 0,
    val isSigned: Boolean = false,
    val isVerified: Boolean = false,
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
    val odometer: Double? = null,
    val engineHours: Double? = null,
    val note: String? = null,
    val timestamp: String? = null,  // ISO 8601 format - original time when event was created
    /**
     * FMCSA Event Record Origin (Table 7)
     * 1=Auto by ELD, 2=Driver, 3=Motor Carrier, 4=Unidentified Driver
     */
    val eventRecordOrigin: Int = FmcsaEventRecordOrigin.AUTO_BY_ELD,
    /**
     * FMCSA Event Record Status (Table 8)
     * 1=Active, 2=Inactive-Changed, 3=Inactive-Change Requested, 4=Inactive-Change Rejected
     */
    val eventRecordStatus: Int = FmcsaEventRecordStatus.ACTIVE
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
    @SerializedName("ELD_UNPLUGGED")
    ELD_UNPLUGGED,
    @SerializedName("ELD_REPLUGGED")
    ELD_REPLUGGED,

    // ELD Malfunctions (FMCSA 49 CFR 395.34)
    // Code P - Power Compliance
    @SerializedName("MALFUNCTION_POWER")
    MALFUNCTION_POWER,
    @SerializedName("MALFUNCTION_POWER_CLEARED")
    MALFUNCTION_POWER_CLEARED,

    // Code E - Engine Synchronization
    @SerializedName("MALFUNCTION_ENGINE_SYNC")
    MALFUNCTION_ENGINE_SYNC,
    @SerializedName("MALFUNCTION_ENGINE_SYNC_CLEARED")
    MALFUNCTION_ENGINE_SYNC_CLEARED,

    // Code T - Timing Compliance
    @SerializedName("MALFUNCTION_TIMING")
    MALFUNCTION_TIMING,
    @SerializedName("MALFUNCTION_TIMING_CLEARED")
    MALFUNCTION_TIMING_CLEARED,

    // Code L - Positioning Compliance
    @SerializedName("MALFUNCTION_POSITIONING")
    MALFUNCTION_POSITIONING,
    @SerializedName("MALFUNCTION_POSITIONING_CLEARED")
    MALFUNCTION_POSITIONING_CLEARED,

    // Code R - Data Recording
    @SerializedName("MALFUNCTION_DATA_RECORDING")
    MALFUNCTION_DATA_RECORDING,
    @SerializedName("MALFUNCTION_DATA_RECORDING_CLEARED")
    MALFUNCTION_DATA_RECORDING_CLEARED,

    // Code S - Data Transfer
    @SerializedName("MALFUNCTION_DATA_TRANSFER")
    MALFUNCTION_DATA_TRANSFER,
    @SerializedName("MALFUNCTION_DATA_TRANSFER_CLEARED")
    MALFUNCTION_DATA_TRANSFER_CLEARED,

    // Code O - Other
    @SerializedName("MALFUNCTION_OTHER")
    MALFUNCTION_OTHER,
    @SerializedName("MALFUNCTION_OTHER_CLEARED")
    MALFUNCTION_OTHER_CLEARED,

    // ELD Diagnostics (FMCSA 49 CFR 395.34)
    // Code 1 - Power Data Diagnostic
    @SerializedName("DIAGNOSTIC_POWER")
    DIAGNOSTIC_POWER,
    @SerializedName("DIAGNOSTIC_POWER_CLEARED")
    DIAGNOSTIC_POWER_CLEARED,

    // Code 2 - Engine Synchronization Data Diagnostic
    @SerializedName("DIAGNOSTIC_ENGINE_SYNC")
    DIAGNOSTIC_ENGINE_SYNC,
    @SerializedName("DIAGNOSTIC_ENGINE_SYNC_CLEARED")
    DIAGNOSTIC_ENGINE_SYNC_CLEARED,

    // Code 3 - Missing Required Data Elements Diagnostic
    @SerializedName("DIAGNOSTIC_MISSING_DATA")
    DIAGNOSTIC_MISSING_DATA,
    @SerializedName("DIAGNOSTIC_MISSING_DATA_CLEARED")
    DIAGNOSTIC_MISSING_DATA_CLEARED,

    // Code 4 - Data Transfer Data Diagnostic
    @SerializedName("DIAGNOSTIC_DATA_TRANSFER")
    DIAGNOSTIC_DATA_TRANSFER,
    @SerializedName("DIAGNOSTIC_DATA_TRANSFER_CLEARED")
    DIAGNOSTIC_DATA_TRANSFER_CLEARED,

    // Code 5 - Unidentified Driving Records Diagnostic
    @SerializedName("DIAGNOSTIC_UNIDENTIFIED_DRIVING")
    DIAGNOSTIC_UNIDENTIFIED_DRIVING,
    @SerializedName("DIAGNOSTIC_UNIDENTIFIED_DRIVING_CLEARED")
    DIAGNOSTIC_UNIDENTIFIED_DRIVING_CLEARED,

    // Code 6 - Other ELD Diagnostic
    @SerializedName("DIAGNOSTIC_OTHER")
    DIAGNOSTIC_OTHER,
    @SerializedName("DIAGNOSTIC_OTHER_CLEARED")
    DIAGNOSTIC_OTHER_CLEARED
}

/**
 * Request to create an intermediate event during DRIVING
 * Intermediate events are recorded every 60 minutes during driving (FMCSA requirement)
 */
data class IntermediateEventRequest(
    val time: String,  // ISO 8601 format
    val latitude: Double? = null,
    val longitude: Double? = null,
    val location: String? = null,
    val odometer: Double,
    val engineHours: Double,
    val vehicleId: Int? = null,
    val deviceId: Int? = null,  // Backend determines from vehicle assignment
    val driverEventId: Int? = null,  // Parent DRIVING event ID (optional, backend finds active)
    val dataDiagnostic: Boolean? = null,
    val malfunction: Boolean? = null,
    /** FMCSA Event Record Origin - always AUTO_BY_ELD for intermediate events */
    val eventRecordOrigin: Int = FmcsaEventRecordOrigin.AUTO_BY_ELD,
    /** FMCSA Event Record Status */
    val eventRecordStatus: Int = FmcsaEventRecordStatus.ACTIVE
)

/**
 * Response from intermediate event creation
 */
data class IntermediateEventResponse(
    val id: Int,
    val sequenceId: Int,
    val time: String,
    val driverEventId: Int?,
    val message: String?
)

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
    val startTime: String? = null,  // ISO 8601 format, e.g. "2024-12-02T00:00:00Z"
    val shippingDocumentNumber: String? = null,
    val trailerNumber: String? = null,
    /**
     * FMCSA Event Record Origin (Table 7)
     * 1=Auto by ELD, 2=Driver, 3=Motor Carrier, 4=Unidentified Driver
     */
    val eventRecordOrigin: Int = FmcsaEventRecordOrigin.DRIVER,
    /**
     * FMCSA Event Record Status (Table 8)
     * 1=Active, 2=Inactive-Changed, 3=Inactive-Change Requested, 4=Inactive-Change Rejected
     */
    val eventRecordStatus: Int = FmcsaEventRecordStatus.ACTIVE
)

/**
 * HOS Violation type enum - matches backend HosViolationTypeEnum
 */
enum class HosViolationTypeApi {
    @SerializedName("DRIVING_11_HOUR")
    DRIVING_11_HOUR,
    @SerializedName("SHIFT_14_HOUR")
    SHIFT_14_HOUR,
    @SerializedName("BREAK_30_MIN")
    BREAK_30_MIN,
    @SerializedName("CYCLE_60_HOUR_7_DAY")
    CYCLE_60_HOUR_7_DAY,
    @SerializedName("CYCLE_70_HOUR_8_DAY")
    CYCLE_70_HOUR_8_DAY,
    @SerializedName("FORM_AND_MANNER")
    FORM_AND_MANNER,
    @SerializedName("FALSE_LOG")
    FALSE_LOG,
    @SerializedName("ADVERSE_WEATHER_MISUSE")
    ADVERSE_WEATHER_MISUSE,
    @SerializedName("SHORT_HAUL_EXCEPTION")
    SHORT_HAUL_EXCEPTION
}

/**
 * Single violation item within a batch request
 * Must match backend's MobileHosViolationItem exactly
 */
data class HosViolationItem(
    val violationType: HosViolationTypeApi,
    val startTime: String,  // ISO 8601 format
    val endTime: String? = null,
    val violationError: String? = null,
    val overLimitMinutes: Int? = null,
    val vehicleId: Int? = null
)

/**
 * Request to create/update HOS violations (BATCH)
 * Must match backend's MobileHosViolationRequest exactly
 *
 * Backend will:
 * 1. Delete all violations where StartTime >= deleteFromTime (and < deleteToTime if provided)
 * 2. Create new violations from the violations list
 */
data class HosViolationRequest(
    /**
     * Delete all existing violations for this driver where StartTime >= this value.
     * Used when mobile recalculates/resets HOS and needs to replace violations from a certain point.
     * REQUIRED by backend.
     */
    val deleteFromTime: String,  // ISO 8601 format - REQUIRED by backend
    /**
     * Delete violations up to this time (exclusive).
     * If null, deletes all violations from deleteFromTime onwards.
     */
    val deleteToTime: String? = null,  // ISO 8601 format
    /**
     * List of violations to create. Can be empty (only deletes violations in range).
     */
    val violations: List<HosViolationItem> = emptyList()
)

/**
 * Request to end an active violation
 */
data class EndViolationRequest(
    val violationType: HosViolationTypeApi,
    val endTime: String  // ISO 8601 format
)

/**
 * HOS Violation response from backend
 */
data class HosViolationDto(
    val id: Int,
    val violationType: HosViolationTypeApi,
    val startTime: String,
    val endTime: String?,
    val violationError: String?,
    val overLimitMinutes: Int?,
    val vehicleId: Int?,
    val vehicleNumber: String?,
    val isAcknowledged: Boolean,
    val acknowledgedAt: String?,
    val isActive: Boolean
)
