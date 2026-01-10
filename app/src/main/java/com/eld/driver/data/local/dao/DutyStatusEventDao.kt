package com.eld.driver.data.local.dao

import androidx.room.*
import com.eld.driver.data.local.entity.DutyStatusEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for duty status events.
 * Provides all database operations for duty status event storage.
 */
@Dao
interface DutyStatusEventDao {

    // ═══════════════════════════════════════════════════════════════
    // QUERY OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get all events since a specific time, ordered by start time ascending.
     * Used for HOS calculation - needs events in chronological order.
     */
    @Query("SELECT * FROM duty_status_events WHERE startTime >= :since ORDER BY startTime ASC")
    suspend fun getEventsSince(since: Long): List<DutyStatusEventEntity>

    /**
     * Get all events as Flow for reactive UI updates.
     */
    @Query("SELECT * FROM duty_status_events WHERE startTime >= :since ORDER BY startTime ASC")
    fun getEventsSinceFlow(since: Long): Flow<List<DutyStatusEventEntity>>

    /**
     * Get the current active duty status event.
     */
    @Query("SELECT * FROM duty_status_events WHERE isActive = 1 LIMIT 1")
    suspend fun getCurrentActiveEvent(): DutyStatusEventEntity?

    /**
     * Get the current active duty status event as Flow.
     */
    @Query("SELECT * FROM duty_status_events WHERE isActive = 1 LIMIT 1")
    fun getCurrentActiveEventFlow(): Flow<DutyStatusEventEntity?>

    /**
     * Get the most recent event by start time.
     */
    @Query("SELECT * FROM duty_status_events ORDER BY startTime DESC LIMIT 1")
    suspend fun getMostRecentEvent(): DutyStatusEventEntity?

    /**
     * Get the most recent event as Flow for reactive UI.
     */
    @Query("SELECT * FROM duty_status_events ORDER BY startTime DESC LIMIT 1")
    fun getMostRecentEventFlow(): Flow<DutyStatusEventEntity?>

    /**
     * Get event by ID.
     */
    @Query("SELECT * FROM duty_status_events WHERE id = :id")
    suspend fun getEventById(id: String): DutyStatusEventEntity?

    /**
     * Get event by server ID.
     */
    @Query("SELECT * FROM duty_status_events WHERE serverId = :serverId")
    suspend fun getEventByServerId(serverId: Int): DutyStatusEventEntity?

    /**
     * Get events for a specific date range (for logs screen).
     * Only returns events that STARTED on this day.
     */
    @Query("SELECT * FROM duty_status_events WHERE startTime >= :startOfDay AND startTime < :endOfDay ORDER BY startTime ASC")
    suspend fun getEventsForDate(startOfDay: Long, endOfDay: Long): List<DutyStatusEventEntity>

    /**
     * Get events that OVERLAP with the specified day.
     * Includes:
     * 1. Events that started on this day (startTime >= dayStart && startTime < dayEnd)
     * 2. Events that started before but extend into this day (startTime < dayStart && (endTime > dayStart OR endTime IS NULL))
     *
     * This matches backend's GetDriverEventsOverlappingDateAsync logic.
     */
    @Query("""
        SELECT * FROM duty_status_events WHERE
            (startTime >= :dayStart AND startTime < :dayEnd)
            OR
            (startTime < :dayStart AND (endTime > :dayStart OR endTime IS NULL))
        ORDER BY startTime ASC
    """)
    suspend fun getEventsOverlappingDate(dayStart: Long, dayEnd: Long): List<DutyStatusEventEntity>

    /**
     * Get events for a specific date as Flow.
     */
    @Query("SELECT * FROM duty_status_events WHERE startTime >= :startOfDay AND startTime < :endOfDay ORDER BY startTime ASC")
    fun getEventsForDateFlow(startOfDay: Long, endOfDay: Long): Flow<List<DutyStatusEventEntity>>

    /**
     * Get all pending sync events.
     */
    @Query("SELECT * FROM duty_status_events WHERE pendingSync = 1 ORDER BY localCreatedAt ASC")
    suspend fun getPendingSyncEvents(): List<DutyStatusEventEntity>

    /**
     * Get count of pending sync events.
     */
    @Query("SELECT COUNT(*) FROM duty_status_events WHERE pendingSync = 1")
    suspend fun getPendingSyncCount(): Int

    /**
     * Get count of all events.
     */
    @Query("SELECT COUNT(*) FROM duty_status_events")
    suspend fun getTotalEventCount(): Int

    // ═══════════════════════════════════════════════════════════════
    // INSERT OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Insert a single event. Replace on conflict.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: DutyStatusEventEntity)

    /**
     * Insert multiple events. Replace on conflict.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<DutyStatusEventEntity>)

    // ═══════════════════════════════════════════════════════════════
    // UPDATE OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Update an event.
     */
    @Update
    suspend fun update(event: DutyStatusEventEntity)

    /**
     * Mark event as synced with server.
     */
    @Query("UPDATE duty_status_events SET isSynced = 1, pendingSync = 0, serverId = :serverId, serverCreatedAt = :serverTime WHERE id = :localId")
    suspend fun markSynced(localId: String, serverId: Int, serverTime: Long)

    /**
     * Mark all events before a time as synced (bulk operation after initial sync).
     */
    @Query("UPDATE duty_status_events SET isSynced = 1, pendingSync = 0 WHERE startTime <= :beforeTime")
    suspend fun markAllSyncedBefore(beforeTime: Long)

    /**
     * Deactivate all events (set isActive = false).
     * Called before setting a new active event.
     */
    @Query("UPDATE duty_status_events SET isActive = 0 WHERE isActive = 1")
    suspend fun deactivateAllEvents()

    /**
     * Update the end time of an event.
     */
    @Query("UPDATE duty_status_events SET endTime = :endTime, durationMinutes = :durationMinutes, isActive = 0 WHERE id = :id")
    suspend fun closeEvent(id: String, endTime: Long, durationMinutes: Int)

    // ═══════════════════════════════════════════════════════════════
    // DELETE OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Delete events older than a specific time.
     * Used for cleanup - keep only last 8 days.
     */
    @Query("DELETE FROM duty_status_events WHERE startTime < :before")
    suspend fun deleteEventsBefore(before: Long)

    /**
     * Delete all events (for logout/clear).
     */
    @Query("DELETE FROM duty_status_events")
    suspend fun deleteAll()

    /**
     * Delete a specific event.
     */
    @Delete
    suspend fun delete(event: DutyStatusEventEntity)

    /**
     * Delete a specific event by ID.
     */
    @Query("DELETE FROM duty_status_events WHERE id = :id")
    suspend fun delete(id: String)

    // ═══════════════════════════════════════════════════════════════
    // AGGREGATE OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get total driving time in milliseconds since a specific time.
     */
    @Query("""
        SELECT COALESCE(SUM(
            CASE
                WHEN endTime IS NOT NULL THEN endTime - startTime
                ELSE :now - startTime
            END
        ), 0)
        FROM duty_status_events
        WHERE dutyStatus = 'DRIVING' AND startTime >= :since
    """)
    suspend fun getTotalDriveTimeSince(since: Long, now: Long = System.currentTimeMillis()): Long

    /**
     * Get total on-duty time (DRIVING + ON_DUTY_NOT_DRIVING) since a specific time.
     */
    @Query("""
        SELECT COALESCE(SUM(
            CASE
                WHEN endTime IS NOT NULL THEN endTime - startTime
                ELSE :now - startTime
            END
        ), 0)
        FROM duty_status_events
        WHERE dutyStatus IN ('DRIVING', 'ON_DUTY_NOT_DRIVING') AND startTime >= :since
    """)
    suspend fun getTotalOnDutyTimeSince(since: Long, now: Long = System.currentTimeMillis()): Long
}
