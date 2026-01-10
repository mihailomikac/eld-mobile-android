package com.eld.driver.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.eld.driver.hos.ViolationRecord

/**
 * Database entity for storing violation records.
 *
 * Violations are CALCULATED from event history, not tracked in real-time.
 * This table stores the results of violation analysis for:
 * 1. Offline persistence
 * 2. Sync tracking with backend
 * 3. Quick lookup without re-analyzing
 */
@Entity(tableName = "violation_records")
data class ViolationRecordEntity(
    @PrimaryKey
    val id: String,                          // Local UUID

    // Violation details
    val violationType: String,               // HOSViolationType.name
    val startTime: Long,                     // When violation started (UTC millis)
    val endTime: Long?,                      // When violation ended (null if active)

    // Context
    val vehicleId: Int?,
    val overLimitMinutes: Int?,              // How many minutes over the limit

    // Sync tracking
    val serverId: Int?,                      // Backend ID after sync
    val syncStatus: String = "PENDING",      // PENDING, SYNCED, FAILED
    val lastSyncAttempt: Long? = null,
    val syncError: String? = null,

    // Metadata
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromViolationRecord(record: ViolationRecord): ViolationRecordEntity {
            return ViolationRecordEntity(
                id = java.util.UUID.randomUUID().toString(),
                violationType = record.type.name,
                startTime = record.startTime,
                endTime = record.endTime,
                vehicleId = record.vehicleId,
                overLimitMinutes = null,
                serverId = record.serverId,
                syncStatus = if (record.isSynced) "SYNCED" else "PENDING"
            )
        }
    }

    fun toViolationRecord(): ViolationRecord {
        return ViolationRecord(
            type = HOSViolationType.valueOf(violationType),
            startTime = startTime,
            endTime = endTime,
            vehicleId = vehicleId,
            serverId = serverId,
            isSynced = syncStatus == "SYNCED"
        )
    }

    val isActive: Boolean get() = endTime == null
}
