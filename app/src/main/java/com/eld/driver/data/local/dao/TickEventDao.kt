package com.eld.driver.data.local.dao

import androidx.room.*
import com.eld.driver.data.local.entity.TickEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for tick events.
 * Provides all database operations for tick event storage.
 */
@Dao
interface TickEventDao {

    // ═══════════════════════════════════════════════════════════════
    // QUERY OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get all tick events since a specific time.
     */
    @Query("SELECT * FROM tick_events WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun getEventsSince(since: Long): List<TickEventEntity>

    /**
     * Get tick events as Flow.
     */
    @Query("SELECT * FROM tick_events WHERE timestamp >= :since ORDER BY timestamp ASC")
    fun getEventsSinceFlow(since: Long): Flow<List<TickEventEntity>>

    /**
     * Get the most recent tick event.
     */
    @Query("SELECT * FROM tick_events ORDER BY timestamp DESC LIMIT 1")
    suspend fun getMostRecentEvent(): TickEventEntity?

    /**
     * Get tick events for a specific date.
     */
    @Query("SELECT * FROM tick_events WHERE timestamp >= :startOfDay AND timestamp < :endOfDay ORDER BY timestamp ASC")
    suspend fun getEventsForDate(startOfDay: Long, endOfDay: Long): List<TickEventEntity>

    /**
     * Get all pending sync events.
     */
    @Query("SELECT * FROM tick_events WHERE pendingSync = 1 ORDER BY localCreatedAt ASC")
    suspend fun getPendingSyncEvents(): List<TickEventEntity>

    /**
     * Get count of pending sync events.
     */
    @Query("SELECT COUNT(*) FROM tick_events WHERE pendingSync = 1")
    suspend fun getPendingSyncCount(): Int

    // ═══════════════════════════════════════════════════════════════
    // INSERT OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Insert a single event.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: TickEventEntity)

    /**
     * Insert multiple events.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<TickEventEntity>)

    // ═══════════════════════════════════════════════════════════════
    // UPDATE OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Mark event as synced.
     */
    @Query("UPDATE tick_events SET isSynced = 1, pendingSync = 0 WHERE id = :id")
    suspend fun markSynced(id: String)

    /**
     * Mark all events before a time as synced.
     */
    @Query("UPDATE tick_events SET isSynced = 1, pendingSync = 0 WHERE timestamp <= :beforeTime")
    suspend fun markAllSyncedBefore(beforeTime: Long)

    // ═══════════════════════════════════════════════════════════════
    // DELETE OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Delete events older than a specific time.
     */
    @Query("DELETE FROM tick_events WHERE timestamp < :before")
    suspend fun deleteEventsBefore(before: Long)

    /**
     * Delete all events.
     */
    @Query("DELETE FROM tick_events")
    suspend fun deleteAll()

    /**
     * Delete a specific event.
     */
    @Delete
    suspend fun delete(event: TickEventEntity)
}
