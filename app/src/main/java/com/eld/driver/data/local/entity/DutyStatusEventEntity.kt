package com.eld.driver.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.eld.driver.data.models.DutyStatusType
import com.eld.driver.data.models.FmcsaEventRecordOrigin
import com.eld.driver.data.models.FmcsaEventRecordStatus

/**
 * Local database entity for duty status events.
 * Stores all duty status changes for offline access and HOS calculation.
 */
@Entity(tableName = "duty_status_events")
data class DutyStatusEventEntity(
    @PrimaryKey
    val id: String,                          // UUID for local, or server ID as string
    val serverId: Int? = null,               // Server-assigned ID (null if not synced)
    val dutyStatus: String,                  // DRIVING, OFF_DUTY, etc.
    val startTime: Long,                     // Epoch milliseconds
    val endTime: Long? = null,               // Null if current/active event
    val durationMinutes: Int? = null,        // Duration in minutes
    val location: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val vehicleId: Int? = null,
    val deviceId: Int? = null,
    val odometer: Double? = null,
    val engineHours: Double? = null,
    val note: String? = null,
    val shippingDocumentNumber: String? = null,
    val trailerNumber: String? = null,
    val isActive: Boolean = false,           // Is this the current active status

    // FMCSA compliance fields
    /** FMCSA Event Record Origin (1=Auto, 2=Driver, 3=Carrier, 4=Unidentified) */
    val eventRecordOrigin: Int = FmcsaEventRecordOrigin.DRIVER,
    /** FMCSA Event Record Status (1=Active, 2=Changed, 3=Requested, 4=Rejected) */
    val eventRecordStatus: Int = FmcsaEventRecordStatus.ACTIVE,

    // Sync metadata
    val isSynced: Boolean = false,           // Has been confirmed by server
    val pendingSync: Boolean = true,         // Needs to be sent to server
    val localCreatedAt: Long = System.currentTimeMillis(),
    val serverCreatedAt: Long? = null
) {
    /**
     * Get duration in milliseconds, calculating from startTime if endTime exists
     */
    fun getDurationMillis(now: Long = System.currentTimeMillis()): Long {
        return (endTime ?: now) - startTime
    }

    /**
     * Check if this is a rest status (OFF_DUTY, SLEEPER_BERTH, or PERSONAL_CONVEYANCE)
     * Note: PC counts as off-duty time per FMCSA rules
     */
    fun isRestStatus(): Boolean {
        return dutyStatus == DutyStatusType.OFF_DUTY.name ||
               dutyStatus == DutyStatusType.SLEEPER_BERTH.name ||
               dutyStatus == DutyStatusType.PERSONAL_CONVEYANCE.name
    }

    /**
     * Check if this is a sleeper berth status (only SB, not OFF_DUTY or PC)
     * Used for Split Sleeper Berth rule where 7+ hours must be in SB only
     */
    fun isSleeperBerthStatus(): Boolean {
        return dutyStatus == DutyStatusType.SLEEPER_BERTH.name
    }

    /**
     * Check if this qualifies for the 2+ hour portion of split sleeper
     * Can be SB, OFF_DUTY, or PC (or any combination)
     */
    fun isQualifyingRestForSplitSleeper(): Boolean {
        return dutyStatus == DutyStatusType.OFF_DUTY.name ||
               dutyStatus == DutyStatusType.SLEEPER_BERTH.name ||
               dutyStatus == DutyStatusType.PERSONAL_CONVEYANCE.name
    }

    /**
     * Check if this is a driving status
     */
    fun isDrivingStatus(): Boolean {
        return dutyStatus == DutyStatusType.DRIVING.name
    }

    /**
     * Check if this is on-duty but NOT driving (ON_DUTY_NOT_DRIVING or YARD_MOVE)
     */
    fun isOnDutyNotDriving(): Boolean {
        return dutyStatus == DutyStatusType.ON_DUTY_NOT_DRIVING.name ||
               dutyStatus == DutyStatusType.YARD_MOVE.name
    }

    /**
     * Check if this is an on-duty status (DRIVING, ON_DUTY_NOT_DRIVING, or YARD_MOVE)
     * Note: YARD_MOVE counts as on-duty time per FMCSA rules
     */
    fun isOnDutyStatus(): Boolean {
        return dutyStatus == DutyStatusType.DRIVING.name ||
               dutyStatus == DutyStatusType.ON_DUTY_NOT_DRIVING.name ||
               dutyStatus == DutyStatusType.YARD_MOVE.name
    }

    /**
     * Check if this is Personal Conveyance
     */
    fun isPersonalConveyance(): Boolean {
        return dutyStatus == DutyStatusType.PERSONAL_CONVEYANCE.name
    }

    /**
     * Check if this is Yard Move
     */
    fun isYardMove(): Boolean {
        return dutyStatus == DutyStatusType.YARD_MOVE.name
    }

    /**
     * Check if this qualifies for 30-minute break (OFF_DUTY, SLEEPER, or ON_DUTY per FMCSA)
     */
    fun isQualifyingBreakStatus(): Boolean {
        return dutyStatus == DutyStatusType.OFF_DUTY.name ||
               dutyStatus == DutyStatusType.SLEEPER_BERTH.name ||
               dutyStatus == DutyStatusType.ON_DUTY_NOT_DRIVING.name ||
               dutyStatus == DutyStatusType.PERSONAL_CONVEYANCE.name
    }
}
