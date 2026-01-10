package com.eld.driver.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Types of operations that can be queued for sync
 */
enum class SyncOperationType {
    STATUS_CHANGE,
    TICK_EVENT,
    INSPECTION_CREATE,
    LOG_CERTIFY,
    VIOLATION_CREATE,
    VIOLATION_END
}

/**
 * Local database entity for sync queue.
 * Stores pending operations to be sent to the backend when online.
 * Implements a FIFO queue for reliable offline-first sync.
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val operationType: String,               // STATUS_CHANGE, TICK_EVENT, etc.
    val payload: String,                     // JSON-serialized request body
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastRetryAt: Long? = null,
    val maxRetries: Int = 5,                 // Max retry attempts
    val priority: Int = 0                    // Higher = more important (for future use)
) {
    /**
     * Check if this item has exceeded max retries
     */
    fun hasExceededRetries(): Boolean = retryCount >= maxRetries

    /**
     * Get backoff delay based on retry count (exponential backoff)
     * 1st retry: 5 sec, 2nd: 10 sec, 3rd: 20 sec, etc.
     */
    fun getBackoffDelayMillis(): Long {
        val baseDelay = 5000L // 5 seconds
        return baseDelay * (1 shl retryCount.coerceAtMost(5)) // Max 160 seconds
    }

    /**
     * Check if enough time has passed since last retry
     */
    fun isReadyForRetry(): Boolean {
        if (lastRetryAt == null) return true
        val timeSinceLastRetry = System.currentTimeMillis() - lastRetryAt
        return timeSinceLastRetry >= getBackoffDelayMillis()
    }
}
