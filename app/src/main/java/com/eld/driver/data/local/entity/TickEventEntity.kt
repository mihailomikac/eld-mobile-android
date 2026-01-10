package com.eld.driver.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.eld.driver.data.models.FmcsaEventRecordOrigin
import com.eld.driver.data.models.FmcsaEventRecordStatus

/**
 * Tick event types matching backend TickEventTypeEnum
 */
enum class TickEventType {
    LOGIN,
    LOGOUT,
    CONNECTED,
    DISCONNECTED,
    POWER_UP,
    POWER_DOWN,
    SHUT_DOWN,
    INTERMEDIATE
}

/**
 * Local database entity for tick events.
 * Tick events are telemetry snapshots sent periodically or on specific events.
 */
@Entity(tableName = "tick_events")
data class TickEventEntity(
    @PrimaryKey
    val id: String,                          // UUID
    val eventType: String,                   // LOGIN, INTERMEDIATE, etc.
    val timestamp: Long,                     // Epoch milliseconds
    val latitude: Double? = null,
    val longitude: Double? = null,
    val location: String? = null,
    val odometer: Double? = null,
    val engineHours: Double? = null,
    val speed: Double? = null,
    val rpm: Int? = null,
    val vehicleId: Int? = null,
    val deviceId: Int? = null,
    val note: String? = null,

    // FMCSA compliance fields
    /** FMCSA Event Record Origin (1=Auto, 2=Driver, 3=Carrier, 4=Unidentified) */
    val eventRecordOrigin: Int = FmcsaEventRecordOrigin.AUTO_BY_ELD,
    /** FMCSA Event Record Status (1=Active, 2=Changed, 3=Requested, 4=Rejected) */
    val eventRecordStatus: Int = FmcsaEventRecordStatus.ACTIVE,

    // Sync metadata
    val isSynced: Boolean = false,
    val pendingSync: Boolean = true,
    val localCreatedAt: Long = System.currentTimeMillis()
)
