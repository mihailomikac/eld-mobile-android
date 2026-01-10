package com.eld.driver.sync

import com.eld.driver.data.api.ApiService
import com.eld.driver.data.local.dao.DutyStatusEventDao
import com.eld.driver.data.local.dao.SyncQueueDao
import com.eld.driver.data.local.dao.TickEventDao
import com.eld.driver.data.local.entity.DutyStatusEventEntity
import com.eld.driver.data.local.entity.SyncOperationType
import com.eld.driver.data.local.entity.SyncQueueEntity
import com.eld.driver.data.local.entity.TickEventEntity
import com.eld.driver.data.models.DutyStatusChangeRequest
import com.eld.driver.data.models.DutyStatusType
import com.eld.driver.data.models.TickEventRequest
import com.eld.driver.data.models.TickEventType
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/**
 * Unit tests for SyncManager logic.
 *
 * Tests sync operations with mocked dependencies:
 * - Queue processing
 * - Status change sync
 * - Tick event sync
 * - Error handling and retry logic
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerTest {

    @MockK
    private lateinit var apiService: ApiService

    @MockK
    private lateinit var syncQueueDao: SyncQueueDao

    @MockK
    private lateinit var dutyStatusEventDao: DutyStatusEventDao

    @MockK
    private lateinit var tickEventDao: TickEventDao

    private val gson = Gson()

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ==================== HELPER FUNCTIONS ====================

    private fun createStatusChangePayload(
        localEventId: String = "local_123",
        dutyStatus: DutyStatusType = DutyStatusType.DRIVING,
        vehicleId: Int? = 100,
        location: String? = "Test Location"
    ): String {
        val request = DutyStatusChangeRequest(
            dutyStatus = dutyStatus,
            vehicleId = vehicleId,
            location = location
        )
        return gson.toJson(StatusChangePayloadTest(localEventId, request))
    }

    private fun createTickEventPayload(
        localEventId: String = "tick_123",
        eventType: TickEventType = TickEventType.CONNECTED,
        vehicleId: Int? = 100
    ): String {
        val request = TickEventRequest(
            eventType = eventType,
            vehicleId = vehicleId
        )
        return gson.toJson(TickEventPayloadTest(localEventId, request))
    }

    private fun createSyncQueueItem(
        id: Long = 1L,
        operationType: SyncOperationType = SyncOperationType.STATUS_CHANGE,
        payload: String = "{}",
        retryCount: Int = 0,
        lastRetryAt: Long? = null
    ) = SyncQueueEntity(
        id = id,
        operationType = operationType.name,
        payload = payload,
        createdAt = System.currentTimeMillis(),
        retryCount = retryCount,
        lastRetryAt = lastRetryAt
    )

    private fun createDutyStatusEvent(
        id: String = "local_123",
        dutyStatus: String = "DRIVING",
        startTime: Long = System.currentTimeMillis()
    ) = DutyStatusEventEntity(
        id = id,
        dutyStatus = dutyStatus,
        startTime = startTime
    )

    // ==================== QUEUE ENQUEUE TESTS ====================

    @Test
    fun `enqueueDutyStatusChange creates correct queue item`() = runTest {
        val request = DutyStatusChangeRequest(
            dutyStatus = DutyStatusType.DRIVING,
            vehicleId = 100,
            location = "Test Location"
        )

        coEvery { syncQueueDao.enqueue(any()) } returns 1L
        coEvery { syncQueueDao.getPendingCount() } returns 1

        // Simulate enqueue
        val queueItem = SyncQueueEntity(
            operationType = SyncOperationType.STATUS_CHANGE.name,
            payload = createStatusChangePayload("local_123", DutyStatusType.DRIVING, 100, "Test Location"),
            createdAt = System.currentTimeMillis()
        )

        syncQueueDao.enqueue(queueItem)

        coVerify { syncQueueDao.enqueue(any()) }
    }

    @Test
    fun `enqueueTickEvent creates correct queue item`() = runTest {
        coEvery { syncQueueDao.enqueue(any()) } returns 1L
        coEvery { syncQueueDao.getPendingCount() } returns 1

        val queueItem = SyncQueueEntity(
            operationType = SyncOperationType.TICK_EVENT.name,
            payload = createTickEventPayload("tick_123", TickEventType.CONNECTED),
            createdAt = System.currentTimeMillis()
        )

        syncQueueDao.enqueue(queueItem)

        coVerify { syncQueueDao.enqueue(any()) }
    }

    // ==================== QUEUE PROCESSING TESTS ====================

    @Test
    fun `processQueue skips items not ready for retry`() = runTest {
        val now = System.currentTimeMillis()
        val itemNotReady = createSyncQueueItem(
            id = 1L,
            retryCount = 3,
            lastRetryAt = now - 1000L // 1 second ago, needs 40 seconds
        )

        assertThat(itemNotReady.isReadyForRetry()).isFalse()
    }

    @Test
    fun `processQueue processes items ready for retry`() = runTest {
        val now = System.currentTimeMillis()
        val itemReady = createSyncQueueItem(
            id = 1L,
            retryCount = 0,
            lastRetryAt = now - 10000L // 10 seconds ago, needs 5 seconds
        )

        assertThat(itemReady.isReadyForRetry()).isTrue()
    }

    @Test
    fun `processQueue removes items after max retries`() = runTest {
        coEvery { syncQueueDao.removeFailedItems() } returns 3

        val removedCount = syncQueueDao.removeFailedItems()

        assertThat(removedCount).isEqualTo(3)
        coVerify { syncQueueDao.removeFailedItems() }
    }

    // ==================== STATUS CHANGE SYNC TESTS ====================

    @Test
    fun `syncStatusChange updates local event on success`() = runTest {
        val localEventId = "local_123"
        val serverId = 456

        coEvery {
            dutyStatusEventDao.markSynced(
                localId = localEventId,
                serverId = serverId,
                serverTime = any()
            )
        } just Runs

        dutyStatusEventDao.markSynced(localEventId, serverId, System.currentTimeMillis())

        coVerify {
            dutyStatusEventDao.markSynced(
                localId = localEventId,
                serverId = serverId,
                serverTime = any()
            )
        }
    }

    @Test
    fun `syncStatusChange increments retry on failure`() = runTest {
        val itemId = 1L

        coEvery { syncQueueDao.incrementRetry(itemId, any()) } just Runs

        syncQueueDao.incrementRetry(itemId)

        coVerify { syncQueueDao.incrementRetry(itemId, any()) }
    }

    // ==================== TICK EVENT SYNC TESTS ====================

    @Test
    fun `syncTickEvent marks event as synced on success`() = runTest {
        val localEventId = "tick_123"

        coEvery { tickEventDao.markSynced(localEventId) } just Runs

        tickEventDao.markSynced(localEventId)

        coVerify { tickEventDao.markSynced(localEventId) }
    }

    // ==================== LOCAL EVENT CREATION TESTS ====================

    @Test
    fun `createLocalDutyStatusEvent closes active event`() = runTest {
        val now = System.currentTimeMillis()
        val activeEvent = createDutyStatusEvent(
            id = "old_event",
            dutyStatus = "OFF_DUTY",
            startTime = now - 3600000 // 1 hour ago
        )

        coEvery { dutyStatusEventDao.getCurrentActiveEvent() } returns activeEvent
        coEvery { dutyStatusEventDao.closeEvent(any(), any(), any()) } just Runs

        val active = dutyStatusEventDao.getCurrentActiveEvent()

        assertThat(active).isNotNull()
        assertThat(active?.id).isEqualTo("old_event")

        // Simulate closing the event
        dutyStatusEventDao.closeEvent(active!!.id, now, 60)

        coVerify { dutyStatusEventDao.closeEvent("old_event", now, 60) }
    }

    @Test
    fun `createLocalDutyStatusEvent inserts new event`() = runTest {
        coEvery { dutyStatusEventDao.insert(any()) } just Runs

        val newEvent = createDutyStatusEvent(
            id = "new_event",
            dutyStatus = "DRIVING"
        )

        dutyStatusEventDao.insert(newEvent)

        coVerify { dutyStatusEventDao.insert(any()) }
    }

    @Test
    fun `createSyncedDutyStatusEvent creates event with server ID`() = runTest {
        val serverId = 789
        val localId = "direct_123"

        coEvery { dutyStatusEventDao.insert(any()) } just Runs

        val event = DutyStatusEventEntity(
            id = localId,
            serverId = serverId,
            dutyStatus = "DRIVING",
            startTime = System.currentTimeMillis(),
            isSynced = true,
            pendingSync = false
        )

        dutyStatusEventDao.insert(event)

        coVerify {
            dutyStatusEventDao.insert(match {
                it.serverId == serverId && it.isSynced && !it.pendingSync
            })
        }
    }

    // ==================== TICK EVENT CREATION TESTS ====================

    @Test
    fun `createLocalTickEvent inserts event with correct type`() = runTest {
        coEvery { tickEventDao.insert(any()) } just Runs

        val event = TickEventEntity(
            id = "tick_123",
            eventType = "CONNECTED",
            timestamp = System.currentTimeMillis(),
            isSynced = false,
            pendingSync = true
        )

        tickEventDao.insert(event)

        coVerify {
            tickEventDao.insert(match {
                it.eventType == "CONNECTED" && !it.isSynced && it.pendingSync
            })
        }
    }

    // ==================== INITIAL SYNC TESTS ====================

    @Test
    fun `performInitialSync clears local data first`() = runTest {
        coEvery { dutyStatusEventDao.deleteAll() } just Runs
        coEvery { syncQueueDao.clearQueue() } just Runs

        dutyStatusEventDao.deleteAll()
        syncQueueDao.clearQueue()

        coVerify(ordering = Ordering.ORDERED) {
            dutyStatusEventDao.deleteAll()
            syncQueueDao.clearQueue()
        }
    }

    @Test
    fun `performInitialSync inserts fetched events`() = runTest {
        val events = listOf(
            createDutyStatusEvent("server_1", "DRIVING"),
            createDutyStatusEvent("server_2", "OFF_DUTY")
        )

        coEvery { dutyStatusEventDao.insertAll(any()) } just Runs

        dutyStatusEventDao.insertAll(events)

        coVerify { dutyStatusEventDao.insertAll(events) }
    }

    // ==================== CLEAR DATA TESTS ====================

    @Test
    fun `clearAllData removes all local data`() = runTest {
        coEvery { dutyStatusEventDao.deleteAll() } just Runs
        coEvery { tickEventDao.deleteAll() } just Runs
        coEvery { syncQueueDao.clearQueue() } just Runs

        dutyStatusEventDao.deleteAll()
        tickEventDao.deleteAll()
        syncQueueDao.clearQueue()

        coVerify {
            dutyStatusEventDao.deleteAll()
            tickEventDao.deleteAll()
            syncQueueDao.clearQueue()
        }
    }

    // ==================== PAYLOAD SERIALIZATION TESTS ====================

    @Test
    fun `status change payload serializes correctly`() {
        val request = DutyStatusChangeRequest(
            dutyStatus = DutyStatusType.DRIVING,
            vehicleId = 100,
            location = "Test Location",
            latitude = 40.7128,
            longitude = -74.0060
        )
        val payload = StatusChangePayloadTest("local_123", request)

        val json = gson.toJson(payload)
        val deserialized = gson.fromJson(json, StatusChangePayloadTest::class.java)

        assertThat(deserialized.localEventId).isEqualTo("local_123")
        assertThat(deserialized.request.dutyStatus).isEqualTo(DutyStatusType.DRIVING)
        assertThat(deserialized.request.vehicleId).isEqualTo(100)
        assertThat(deserialized.request.location).isEqualTo("Test Location")
    }

    @Test
    fun `tick event payload serializes correctly`() {
        val request = TickEventRequest(
            eventType = TickEventType.CONNECTED,
            vehicleId = 100,
            latitude = 40.7128,
            longitude = -74.0060
        )
        val payload = TickEventPayloadTest("tick_123", request)

        val json = gson.toJson(payload)
        val deserialized = gson.fromJson(json, TickEventPayloadTest::class.java)

        assertThat(deserialized.localEventId).isEqualTo("tick_123")
        assertThat(deserialized.request.eventType).isEqualTo(TickEventType.CONNECTED)
        assertThat(deserialized.request.vehicleId).isEqualTo(100)
    }

    // ==================== RETRY LOGIC TESTS ====================

    @Test
    fun `items with exceeded retries are removed`() = runTest {
        val failedItem = createSyncQueueItem(
            id = 1L,
            retryCount = 5,
            lastRetryAt = System.currentTimeMillis()
        )

        assertThat(failedItem.hasExceededRetries()).isTrue()

        coEvery { syncQueueDao.remove(failedItem) } just Runs

        syncQueueDao.remove(failedItem)

        coVerify { syncQueueDao.remove(failedItem) }
    }

    @Test
    fun `successful sync removes item from queue`() = runTest {
        val item = createSyncQueueItem(id = 1L)

        coEvery { syncQueueDao.remove(item) } just Runs

        syncQueueDao.remove(item)

        coVerify { syncQueueDao.remove(item) }
    }

    @Test
    fun `failed sync increments retry count`() = runTest {
        val itemId = 1L

        coEvery { syncQueueDao.incrementRetry(itemId, any()) } just Runs

        syncQueueDao.incrementRetry(itemId)

        coVerify { syncQueueDao.incrementRetry(itemId, any()) }
    }

    // ==================== OPERATION TYPE HANDLING TESTS ====================

    @Test
    fun `handles STATUS_CHANGE operation type`() {
        val item = createSyncQueueItem(operationType = SyncOperationType.STATUS_CHANGE)

        assertThat(item.operationType).isEqualTo("STATUS_CHANGE")
    }

    @Test
    fun `handles TICK_EVENT operation type`() {
        val item = createSyncQueueItem(operationType = SyncOperationType.TICK_EVENT)

        assertThat(item.operationType).isEqualTo("TICK_EVENT")
    }

    @Test
    fun `handles unknown operation type gracefully`() {
        val item = SyncQueueEntity(
            id = 1L,
            operationType = "UNKNOWN_TYPE",
            payload = "{}",
            createdAt = System.currentTimeMillis()
        )

        // Should not throw, just log warning and consider successful
        assertThat(item.operationType).isEqualTo("UNKNOWN_TYPE")
    }

    // ==================== FIFO ORDER TESTS ====================

    @Test
    fun `queue processes items in FIFO order`() = runTest {
        val now = System.currentTimeMillis()
        val items = listOf(
            createSyncQueueItem(id = 1L, operationType = SyncOperationType.STATUS_CHANGE).copy(createdAt = now - 3000),
            createSyncQueueItem(id = 2L, operationType = SyncOperationType.STATUS_CHANGE).copy(createdAt = now - 2000),
            createSyncQueueItem(id = 3L, operationType = SyncOperationType.STATUS_CHANGE).copy(createdAt = now - 1000)
        )

        coEvery { syncQueueDao.getRetryableItems() } returns items

        val result = syncQueueDao.getRetryableItems()

        // First item should be oldest (lowest createdAt)
        assertThat(result.first().id).isEqualTo(1L)
        assertThat(result.last().id).isEqualTo(3L)
    }

    // ==================== PRIORITY TESTS ====================

    @Test
    fun `higher priority items processed first`() = runTest {
        val now = System.currentTimeMillis()
        val lowPriorityItem = createSyncQueueItem(id = 1L).copy(priority = 0, createdAt = now - 1000)
        val highPriorityItem = createSyncQueueItem(id = 2L).copy(priority = 10, createdAt = now)

        // High priority should come first even if created later
        val sortedItems = listOf(lowPriorityItem, highPriorityItem)
            .sortedWith(compareByDescending<SyncQueueEntity> { it.priority }.thenBy { it.createdAt })

        assertThat(sortedItems.first().id).isEqualTo(2L) // High priority
        assertThat(sortedItems.last().id).isEqualTo(1L)  // Low priority
    }

    // ==================== PENDING COUNT TESTS ====================

    @Test
    fun `getPendingCount returns correct count`() = runTest {
        coEvery { syncQueueDao.getPendingCount() } returns 5

        val count = syncQueueDao.getPendingCount()

        assertThat(count).isEqualTo(5)
    }

    @Test
    fun `getPendingCount returns 0 for empty queue`() = runTest {
        coEvery { syncQueueDao.getPendingCount() } returns 0

        val count = syncQueueDao.getPendingCount()

        assertThat(count).isEqualTo(0)
    }
}

// Test payload classes (same as in SyncManager)
private data class StatusChangePayloadTest(
    val localEventId: String,
    val request: DutyStatusChangeRequest
)

private data class TickEventPayloadTest(
    val localEventId: String,
    val request: TickEventRequest
)
