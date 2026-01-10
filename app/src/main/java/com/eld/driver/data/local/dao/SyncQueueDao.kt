package com.eld.driver.data.local.dao

import androidx.room.*
import com.eld.driver.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for sync queue.
 * Manages pending sync operations for offline-first architecture.
 */
@Dao
interface SyncQueueDao {

    // ═══════════════════════════════════════════════════════════════
    // QUERY OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get all pending items in FIFO order.
     */
    @Query("SELECT * FROM sync_queue ORDER BY priority DESC, createdAt ASC")
    suspend fun getAllPending(): List<SyncQueueEntity>

    /**
     * Get all pending items as Flow.
     */
    @Query("SELECT * FROM sync_queue ORDER BY priority DESC, createdAt ASC")
    fun getAllPendingFlow(): Flow<List<SyncQueueEntity>>

    /**
     * Get pending items by operation type.
     */
    @Query("SELECT * FROM sync_queue WHERE operationType = :type ORDER BY createdAt ASC")
    suspend fun getPendingByType(type: String): List<SyncQueueEntity>

    /**
     * Get the next item to process.
     */
    @Query("SELECT * FROM sync_queue ORDER BY priority DESC, createdAt ASC LIMIT 1")
    suspend fun getNextItem(): SyncQueueEntity?

    /**
     * Get count of pending items.
     */
    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun getPendingCount(): Int

    /**
     * Get count as Flow for UI updates.
     */
    @Query("SELECT COUNT(*) FROM sync_queue")
    fun getPendingCountFlow(): Flow<Int>

    /**
     * Get items that are ready for retry (based on backoff).
     */
    @Query("""
        SELECT * FROM sync_queue
        WHERE retryCount < maxRetries
        ORDER BY priority DESC, createdAt ASC
    """)
    suspend fun getRetryableItems(): List<SyncQueueEntity>

    // ═══════════════════════════════════════════════════════════════
    // INSERT OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Add item to queue.
     */
    @Insert
    suspend fun enqueue(item: SyncQueueEntity): Long

    /**
     * Add multiple items to queue.
     */
    @Insert
    suspend fun enqueueAll(items: List<SyncQueueEntity>)

    // ═══════════════════════════════════════════════════════════════
    // UPDATE OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Increment retry count for an item.
     */
    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, lastRetryAt = :now WHERE id = :id")
    suspend fun incrementRetry(id: Long, now: Long = System.currentTimeMillis())

    /**
     * Update item.
     */
    @Update
    suspend fun update(item: SyncQueueEntity)

    // ═══════════════════════════════════════════════════════════════
    // DELETE OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Remove item from queue (after successful sync).
     */
    @Delete
    suspend fun remove(item: SyncQueueEntity)

    /**
     * Remove item by ID.
     */
    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun removeById(id: Long)

    /**
     * Remove all items that have exceeded max retries.
     */
    @Query("DELETE FROM sync_queue WHERE retryCount >= maxRetries")
    suspend fun removeFailedItems(): Int

    /**
     * Clear entire queue.
     */
    @Query("DELETE FROM sync_queue")
    suspend fun clearQueue()

    /**
     * Remove items older than a specific time (cleanup).
     */
    @Query("DELETE FROM sync_queue WHERE createdAt < :before")
    suspend fun removeOldItems(before: Long)

    /**
     * Remove items by local event ID (searches in payload JSON).
     * Used when splitting multi-day events to remove old queue entries.
     */
    @Query("DELETE FROM sync_queue WHERE payload LIKE '%\"localEventId\":\"' || :localId || '\"%'")
    suspend fun removeByLocalId(localId: String)
}
