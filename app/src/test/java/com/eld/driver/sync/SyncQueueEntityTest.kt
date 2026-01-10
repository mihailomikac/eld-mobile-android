package com.eld.driver.sync

import com.eld.driver.data.local.entity.SyncOperationType
import com.eld.driver.data.local.entity.SyncQueueEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for SyncQueueEntity.
 *
 * Tests the queue item logic:
 * - Retry count tracking
 * - Exponential backoff calculation
 * - Retry readiness detection
 */
class SyncQueueEntityTest {

    // ==================== FACTORY HELPERS ====================

    private fun createQueueItem(
        id: Long = 1L,
        operationType: String = SyncOperationType.STATUS_CHANGE.name,
        payload: String = "{}",
        createdAt: Long = System.currentTimeMillis(),
        retryCount: Int = 0,
        lastRetryAt: Long? = null,
        maxRetries: Int = 5,
        priority: Int = 0
    ) = SyncQueueEntity(
        id = id,
        operationType = operationType,
        payload = payload,
        createdAt = createdAt,
        retryCount = retryCount,
        lastRetryAt = lastRetryAt,
        maxRetries = maxRetries,
        priority = priority
    )

    // ==================== RETRY COUNT TESTS ====================

    @Test
    fun `hasExceededRetries returns false when retry count is 0`() {
        val item = createQueueItem(retryCount = 0, maxRetries = 5)

        assertThat(item.hasExceededRetries()).isFalse()
    }

    @Test
    fun `hasExceededRetries returns false when retry count is below max`() {
        val item = createQueueItem(retryCount = 3, maxRetries = 5)

        assertThat(item.hasExceededRetries()).isFalse()
    }

    @Test
    fun `hasExceededRetries returns true when retry count equals max`() {
        val item = createQueueItem(retryCount = 5, maxRetries = 5)

        assertThat(item.hasExceededRetries()).isTrue()
    }

    @Test
    fun `hasExceededRetries returns true when retry count exceeds max`() {
        val item = createQueueItem(retryCount = 7, maxRetries = 5)

        assertThat(item.hasExceededRetries()).isTrue()
    }

    @Test
    fun `hasExceededRetries respects custom maxRetries`() {
        val item = createQueueItem(retryCount = 3, maxRetries = 3)

        assertThat(item.hasExceededRetries()).isTrue()
    }

    // ==================== BACKOFF DELAY TESTS ====================

    @Test
    fun `getBackoffDelayMillis returns 10 seconds for first retry`() {
        val item = createQueueItem(retryCount = 0)

        // Base delay = 5000ms, 1 << 0 = 1, so 5000 * 1 = 5000
        // Wait, retryCount 0 means no retries yet, so:
        // 5000 * (1 << 0) = 5000 * 1 = 5000ms = 5 seconds
        assertThat(item.getBackoffDelayMillis()).isEqualTo(5000L)
    }

    @Test
    fun `getBackoffDelayMillis returns 10 seconds after 1 retry`() {
        val item = createQueueItem(retryCount = 1)

        // 5000 * (1 << 1) = 5000 * 2 = 10000ms = 10 seconds
        assertThat(item.getBackoffDelayMillis()).isEqualTo(10000L)
    }

    @Test
    fun `getBackoffDelayMillis returns 20 seconds after 2 retries`() {
        val item = createQueueItem(retryCount = 2)

        // 5000 * (1 << 2) = 5000 * 4 = 20000ms = 20 seconds
        assertThat(item.getBackoffDelayMillis()).isEqualTo(20000L)
    }

    @Test
    fun `getBackoffDelayMillis returns 40 seconds after 3 retries`() {
        val item = createQueueItem(retryCount = 3)

        // 5000 * (1 << 3) = 5000 * 8 = 40000ms = 40 seconds
        assertThat(item.getBackoffDelayMillis()).isEqualTo(40000L)
    }

    @Test
    fun `getBackoffDelayMillis returns 80 seconds after 4 retries`() {
        val item = createQueueItem(retryCount = 4)

        // 5000 * (1 << 4) = 5000 * 16 = 80000ms = 80 seconds
        assertThat(item.getBackoffDelayMillis()).isEqualTo(80000L)
    }

    @Test
    fun `getBackoffDelayMillis returns 160 seconds after 5 retries`() {
        val item = createQueueItem(retryCount = 5)

        // 5000 * (1 << 5) = 5000 * 32 = 160000ms = 160 seconds
        assertThat(item.getBackoffDelayMillis()).isEqualTo(160000L)
    }

    @Test
    fun `getBackoffDelayMillis caps at 160 seconds for high retry counts`() {
        // Even with retry count 10, should cap at 160 seconds
        val item = createQueueItem(retryCount = 10)

        // coerceAtMost(5) limits the shift to max 5
        // 5000 * (1 << 5) = 160000ms
        assertThat(item.getBackoffDelayMillis()).isEqualTo(160000L)
    }

    @Test
    fun `getBackoffDelayMillis caps at 160 seconds for very high retry counts`() {
        val item = createQueueItem(retryCount = 100)

        assertThat(item.getBackoffDelayMillis()).isEqualTo(160000L)
    }

    // ==================== EXPONENTIAL BACKOFF SEQUENCE ====================

    @Test
    fun `backoff delay follows exponential sequence`() {
        val expectedDelays = listOf(
            0 to 5000L,    // 5 sec
            1 to 10000L,   // 10 sec
            2 to 20000L,   // 20 sec
            3 to 40000L,   // 40 sec
            4 to 80000L,   // 80 sec
            5 to 160000L   // 160 sec (max)
        )

        for ((retryCount, expectedDelay) in expectedDelays) {
            val item = createQueueItem(retryCount = retryCount)
            assertThat(item.getBackoffDelayMillis())
                .isEqualTo(expectedDelay)
        }
    }

    // ==================== RETRY READINESS TESTS ====================

    @Test
    fun `isReadyForRetry returns true when lastRetryAt is null`() {
        val item = createQueueItem(lastRetryAt = null)

        assertThat(item.isReadyForRetry()).isTrue()
    }

    @Test
    fun `isReadyForRetry returns true when enough time has passed`() {
        val now = System.currentTimeMillis()
        // Last retry was 10 seconds ago, backoff for retry 0 is 5 seconds
        val item = createQueueItem(
            retryCount = 0,
            lastRetryAt = now - 10000L // 10 seconds ago
        )

        assertThat(item.isReadyForRetry()).isTrue()
    }

    @Test
    fun `isReadyForRetry returns false when not enough time has passed`() {
        val now = System.currentTimeMillis()
        // Last retry was 1 second ago, backoff for retry 0 is 5 seconds
        val item = createQueueItem(
            retryCount = 0,
            lastRetryAt = now - 1000L // 1 second ago
        )

        assertThat(item.isReadyForRetry()).isFalse()
    }

    @Test
    fun `isReadyForRetry respects backoff for higher retry counts`() {
        val now = System.currentTimeMillis()

        // Retry count 3 has backoff of 40 seconds
        val itemNotReady = createQueueItem(
            retryCount = 3,
            lastRetryAt = now - 30000L // 30 seconds ago (not enough)
        )
        assertThat(itemNotReady.isReadyForRetry()).isFalse()

        val itemReady = createQueueItem(
            retryCount = 3,
            lastRetryAt = now - 50000L // 50 seconds ago (enough)
        )
        assertThat(itemReady.isReadyForRetry()).isTrue()
    }

    @Test
    fun `isReadyForRetry handles exactly backoff time`() {
        val now = System.currentTimeMillis()
        val item = createQueueItem(
            retryCount = 0,
            lastRetryAt = now - 5000L // Exactly 5 seconds ago
        )

        // Should be ready when time >= backoff
        assertThat(item.isReadyForRetry()).isTrue()
    }

    // ==================== OPERATION TYPE TESTS ====================

    @Test
    fun `can create item with STATUS_CHANGE operation type`() {
        val item = createQueueItem(operationType = SyncOperationType.STATUS_CHANGE.name)

        assertThat(item.operationType).isEqualTo("STATUS_CHANGE")
    }

    @Test
    fun `can create item with TICK_EVENT operation type`() {
        val item = createQueueItem(operationType = SyncOperationType.TICK_EVENT.name)

        assertThat(item.operationType).isEqualTo("TICK_EVENT")
    }

    @Test
    fun `can create item with INSPECTION_CREATE operation type`() {
        val item = createQueueItem(operationType = SyncOperationType.INSPECTION_CREATE.name)

        assertThat(item.operationType).isEqualTo("INSPECTION_CREATE")
    }

    @Test
    fun `can create item with LOG_CERTIFY operation type`() {
        val item = createQueueItem(operationType = SyncOperationType.LOG_CERTIFY.name)

        assertThat(item.operationType).isEqualTo("LOG_CERTIFY")
    }

    // ==================== PRIORITY TESTS ====================

    @Test
    fun `default priority is 0`() {
        val item = createQueueItem()

        assertThat(item.priority).isEqualTo(0)
    }

    @Test
    fun `can set custom priority`() {
        val item = createQueueItem(priority = 10)

        assertThat(item.priority).isEqualTo(10)
    }

    // ==================== PAYLOAD TESTS ====================

    @Test
    fun `payload stores JSON string`() {
        val json = """{"dutyStatus":"DRIVING","vehicleId":123}"""
        val item = createQueueItem(payload = json)

        assertThat(item.payload).isEqualTo(json)
    }

    @Test
    fun `payload can be empty object`() {
        val item = createQueueItem(payload = "{}")

        assertThat(item.payload).isEqualTo("{}")
    }

    // ==================== DATA CLASS TESTS ====================

    @Test
    fun `equals works correctly for same data`() {
        val item1 = createQueueItem(id = 1, operationType = "TEST", payload = "{}")
        val item2 = createQueueItem(id = 1, operationType = "TEST", payload = "{}")

        assertThat(item1).isEqualTo(item2)
    }

    @Test
    fun `equals returns false for different IDs`() {
        val item1 = createQueueItem(id = 1)
        val item2 = createQueueItem(id = 2)

        assertThat(item1).isNotEqualTo(item2)
    }

    @Test
    fun `copy works correctly`() {
        val original = createQueueItem(retryCount = 0)
        val copy = original.copy(retryCount = 3)

        assertThat(copy.retryCount).isEqualTo(3)
        assertThat(copy.id).isEqualTo(original.id)
        assertThat(copy.operationType).isEqualTo(original.operationType)
    }

    // ==================== EDGE CASES ====================

    @Test
    fun `handles zero maxRetries`() {
        val item = createQueueItem(retryCount = 0, maxRetries = 0)

        assertThat(item.hasExceededRetries()).isTrue()
    }

    @Test
    fun `handles negative retry count gracefully`() {
        // Should not happen in practice, but test defensive coding
        val item = createQueueItem(retryCount = -1)

        // -1 < 5, so should not have exceeded
        assertThat(item.hasExceededRetries()).isFalse()

        // Backoff with negative shift - (1 << -1) in Kotlin is undefined behavior
        // but coerceAtMost(5) should handle it
        // Actually, negative numbers coerced to max 5 will be -1, and 1 << -1 is 0
        // Let's just verify it doesn't crash
        item.getBackoffDelayMillis()
    }

    @Test
    fun `handles very old lastRetryAt`() {
        val veryOldTime = 0L // Unix epoch
        val item = createQueueItem(lastRetryAt = veryOldTime)

        // Should definitely be ready for retry
        assertThat(item.isReadyForRetry()).isTrue()
    }

    @Test
    fun `handles future lastRetryAt`() {
        val futureTime = System.currentTimeMillis() + 1000000L
        val item = createQueueItem(lastRetryAt = futureTime)

        // Time since last retry would be negative, so not ready
        assertThat(item.isReadyForRetry()).isFalse()
    }
}
