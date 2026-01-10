package com.eld.driver.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.eld.driver.ELDDriverApplication
import com.eld.driver.data.api.ApiService
import com.eld.driver.data.local.ELDDatabase
import com.eld.driver.data.local.dao.DutyStatusEventDao
import com.eld.driver.data.local.dao.SyncQueueDao
import com.eld.driver.data.local.dao.TickEventDao
import com.eld.driver.data.local.entity.DutyStatusEventEntity
import com.eld.driver.data.local.entity.SyncOperationType
import com.eld.driver.data.local.entity.SyncQueueEntity
import com.eld.driver.data.local.entity.TickEventEntity
import com.eld.driver.data.models.DutyStatusChangeRequest
import com.eld.driver.data.models.DutyStatusType
import com.eld.driver.data.models.FmcsaEventRecordOrigin
import com.eld.driver.data.models.FmcsaEventRecordStatus
import com.eld.driver.data.models.TickEventRequest
import com.eld.driver.data.models.TickEventType
import com.eld.driver.data.local.TokenManager
import com.eld.driver.hos.ViolationSyncService
import com.eld.driver.location.LocationService
import com.eld.driver.ble.GeometrisWQManager
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.*

/**
 * SyncManager handles all synchronization between local database and backend.
 *
 * Features:
 * - Queue-based offline-first sync
 * - Automatic sync on connectivity changes
 * - Exponential backoff for retries
 * - Initial sync on login (fetch 8 days of events)
 * - Periodic sync every 5 minutes
 */
class SyncManager private constructor(
    private val context: Context,
    private val apiService: ApiService,
    private val database: ELDDatabase
) {
    companion object {
        private const val TAG = "SyncManager"
        private const val SYNC_DAYS = 8  // Fetch 8 days to match LogsScreen display

        @Volatile
        private var INSTANCE: SyncManager? = null

        fun getInstance(context: Context): SyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncManager(
                    context.applicationContext,
                    ApiService.getInstance(),
                    ELDDatabase.getInstance(context)
                ).also { INSTANCE = it }
            }
        }
    }

    private val dutyStatusEventDao: DutyStatusEventDao = database.dutyStatusEventDao()
    private val tickEventDao: TickEventDao = database.tickEventDao()
    private val syncQueueDao: SyncQueueDao = database.syncQueueDao()
    private val tokenManager = TokenManager.getInstance(context)
    private val locationService = LocationService.getInstance(context)
    private val bleManager = GeometrisWQManager.getInstance(context)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private val syncMutex = Mutex()

    // Network state
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    // Sync state
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _pendingSyncCount = MutableStateFlow(0)
    val pendingSyncCount: StateFlow<Int> = _pendingSyncCount.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        observeConnectivity()
        updatePendingCount()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // NETWORK CONNECTIVITY
    // ═══════════════════════════════════════════════════════════════════════

    private fun observeConnectivity() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Check initial state - more robust check
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val hasValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

        _isOnline.value = hasInternet && hasValidated
        Log.d(TAG, "Initial network state: online=${_isOnline.value} (internet=$hasInternet, validated=$hasValidated)")

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                _isOnline.value = true
                // Trigger sync when coming online
                scope.launch { processQueue() }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                _isOnline.value = false
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasNet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                _isOnline.value = hasNet && validated
                Log.d(TAG, "Network capabilities changed: online=${_isOnline.value}")
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // QUEUE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Enqueue a duty status change for sync.
     * Saves locally first, then adds to sync queue.
     */
    suspend fun enqueueDutyStatusChange(
        request: DutyStatusChangeRequest,
        localEventId: String
    ) {
        val queueItem = SyncQueueEntity(
            operationType = SyncOperationType.STATUS_CHANGE.name,
            payload = gson.toJson(StatusChangePayload(localEventId, request)),
            createdAt = System.currentTimeMillis()
        )
        syncQueueDao.enqueue(queueItem)
        updatePendingCount()

        Log.d(TAG, "Enqueued status change: ${request.dutyStatus}, isOnline=${_isOnline.value}")

        // Try immediate sync if online
        if (_isOnline.value) {
            Log.d(TAG, "Online - triggering immediate sync")
            scope.launch {
                val count = processQueue()
                Log.d(TAG, "Immediate sync processed $count items")
            }
        } else {
            Log.d(TAG, "Offline - will sync later")
        }
    }

    /**
     * Enqueue a tick event for sync.
     */
    suspend fun enqueueTickEvent(
        request: TickEventRequest,
        localEventId: String
    ) {
        val queueItem = SyncQueueEntity(
            operationType = SyncOperationType.TICK_EVENT.name,
            payload = gson.toJson(TickEventPayload(localEventId, request)),
            createdAt = System.currentTimeMillis()
        )
        syncQueueDao.enqueue(queueItem)
        updatePendingCount()

        Log.d(TAG, "Enqueued tick event: ${request.eventType}")

        if (_isOnline.value) {
            scope.launch { processQueue() }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PROCESS QUEUE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Process all items in the sync queue.
     * Uses mutex to prevent concurrent processing.
     */
    suspend fun processQueue(): Int {
        Log.d(TAG, "processQueue called - isOnline: ${_isOnline.value}")

        if (!_isOnline.value) {
            Log.d(TAG, "Offline - skipping queue processing")
            return 0
        }

        val token = getAuthToken()
        if (token == null) {
            Log.e(TAG, "No auth token available - skipping queue processing")
            return 0
        }

        return syncMutex.withLock {
            _isSyncing.value = true
            var processedCount = 0

            try {
                val pendingItems = syncQueueDao.getRetryableItems()
                Log.d(TAG, "Processing ${pendingItems.size} queue items (token present: ${token.isNotEmpty()})")

                for (item in pendingItems) {
                    if (!item.isReadyForRetry()) {
                        Log.d(TAG, "Item ${item.id} not ready for retry (backoff)")
                        continue
                    }

                    val success = try {
                        when (item.operationType) {
                            SyncOperationType.STATUS_CHANGE.name -> syncStatusChange(item)
                            SyncOperationType.TICK_EVENT.name -> syncTickEvent(item)
                            else -> {
                                Log.w(TAG, "Unknown operation type: ${item.operationType}")
                                true // Remove unknown items
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error syncing item ${item.id}", e)
                        false
                    }

                    if (success) {
                        syncQueueDao.remove(item)
                        processedCount++
                        Log.d(TAG, "Synced and removed item ${item.id}")
                    } else {
                        syncQueueDao.incrementRetry(item.id)
                        Log.w(TAG, "Failed to sync item ${item.id}, retry count: ${item.retryCount + 1}")
                    }
                }

                // Cleanup failed items
                val removedFailed = syncQueueDao.removeFailedItems()
                if (removedFailed > 0) {
                    Log.w(TAG, "Removed $removedFailed failed items from queue")
                }

                _lastSyncTime.value = System.currentTimeMillis()
            } finally {
                _isSyncing.value = false
                updatePendingCount()
            }

            processedCount
        }
    }

    // Store last error for debug display
    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    private suspend fun syncStatusChange(item: SyncQueueEntity): Boolean {
        val payload = gson.fromJson(item.payload, StatusChangePayload::class.java)
        val token = getAuthToken()

        if (token == null) {
            Log.e(TAG, "syncStatusChange: No auth token!")
            _lastSyncError.value = "No auth token"
            return false
        }

        Log.d(TAG, "syncStatusChange: Sending ${payload.request.dutyStatus} to server...")
        Log.d(TAG, "syncStatusChange: Request = ${payload.request}")

        return try {
            val response = apiService.changeDutyStatus(token, payload.request)
            val responseBody = response.body()

            Log.d(TAG, "syncStatusChange: Response code=${response.code()}")
            Log.d(TAG, "syncStatusChange: Response body=$responseBody")

            if (response.isSuccessful && responseBody?.success == true) {
                val serverData = responseBody.data
                if (serverData != null) {
                    // Update local event with server ID
                    dutyStatusEventDao.markSynced(
                        localId = payload.localEventId,
                        serverId = serverData.id,
                        serverTime = System.currentTimeMillis()
                    )
                    Log.d(TAG, "syncStatusChange: SUCCESS - synced to server ID ${serverData.id}")
                    _lastSyncError.value = null
                }
                true
            } else {
                val errorMsg = "HTTP ${response.code()}: ${responseBody?.error ?: response.message()}"
                Log.e(TAG, "Status change failed: $errorMsg")
                _lastSyncError.value = errorMsg
                false
            }
        } catch (e: Exception) {
            val errorMsg = "Exception: ${e.message}"
            Log.e(TAG, "Status change exception: ${e.message}", e)
            _lastSyncError.value = errorMsg
            false
        }
    }

    private suspend fun syncTickEvent(item: SyncQueueEntity): Boolean {
        val payload = gson.fromJson(item.payload, TickEventPayload::class.java)
        val token = getAuthToken() ?: return false

        return try {
            val response = apiService.createTickEvent(token, payload.request)
            if (response.isSuccessful && response.body()?.success == true) {
                tickEventDao.markSynced(payload.localEventId)
                true
            } else {
                Log.e(TAG, "Tick event failed: ${response.body()?.error}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tick event exception", e)
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INITIAL SYNC (On Login)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Perform initial sync on login.
     * CLEARS all local data and fetches fresh data from server.
     * This ensures server is the source of truth on login.
     *
     * IMPORTANT: Uses COMPANY TIMEZONE for date calculations to match server expectations.
     */
    suspend fun performInitialSync(): Result<Unit> {
        Log.d(TAG, "═══════════════════════════════════════════════════════")
        Log.d(TAG, "🔄 INITIAL SYNC - Starting fresh sync from server")
        Log.d(TAG, "═══════════════════════════════════════════════════════")

        if (!_isOnline.value) {
            Log.w(TAG, "⚠️ Offline - initial sync skipped, using cached data")
            return Result.failure(Exception("No network connection"))
        }

        val token = getAuthToken() ?: return Result.failure(Exception("No auth token"))

        // Use COMPANY TIMEZONE for date calculations (matches server expectations)
        val companyTz = tokenManager.getCompanyTimeZone()
        Log.d(TAG, "📅 Using company timezone: ${companyTz.id}")

        return try {
            // STEP 1: Clear ALL local duty status events (fresh start)
            Log.d(TAG, "🗑️ Clearing local duty status events...")
            dutyStatusEventDao.deleteAll()

            // STEP 2: Clear sync queue (no pending items on fresh login)
            syncQueueDao.clearQueue()
            Log.d(TAG, "🗑️ Cleared sync queue")

            // STEP 3: Fetch events for last SYNC_DAYS days from server (including TODAY)
            // Use company timezone for "today" calculation
            val calendar = Calendar.getInstance(companyTz)
            val today = formatDate(calendar.time, companyTz)

            Log.d(TAG, "📥 Fetching events for today ($today) and last $SYNC_DAYS days in company timezone (${companyTz.id})")

            var totalEvents = 0

            // Fetch today first, then go back SYNC_DAYS days
            for (i in 0..SYNC_DAYS) {
                val date = formatDate(calendar.time, companyTz)

                try {
                    Log.d(TAG, "   📅 Requesting date: $date (iteration $i)")
                    val response = apiService.getDriverEvents(token, date)
                    if (response.isSuccessful && response.body()?.success == true) {
                        val eventsData = response.body()?.data
                        if (eventsData != null && eventsData.dutyStatusEvents.isNotEmpty()) {
                            // Log each event for debugging
                            eventsData.dutyStatusEvents.forEach { event ->
                                Log.d(TAG, "   📋 Server event: id=${event.id}, status=${event.dutyStatus}, " +
                                    "startTime=${event.startTime}, isActive=${event.isActive}")
                            }
                            // Convert and save duty status events
                            // Pass query date to avoid ID collision with multi-day events
                            val entities = eventsData.dutyStatusEvents.map { it.toEntity(date) }
                            dutyStatusEventDao.insertAll(entities)
                            totalEvents += entities.size
                            Log.d(TAG, "   ✅ $date: ${entities.size} events saved")
                        } else {
                            Log.d(TAG, "   📅 $date: 0 events (empty response)")
                        }
                    } else {
                        Log.w(TAG, "   ⚠️ $date: API returned error - ${response.body()?.error ?: response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "   ❌ Error fetching events for $date: ${e.message}")
                }

                // Go back one day for next iteration
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            }

            // STEP 4: Check if driver needs initial OFF_DUTY event
            // Create if: no events OR most recent event is older than 6 months
            val mostRecentEvent = dutyStatusEventDao.getMostRecentEvent()
            val sixMonthsAgo = System.currentTimeMillis() - (180L * 24 * 60 * 60 * 1000)

            Log.d(TAG, "📊 After fetching from server:")
            if (mostRecentEvent != null) {
                Log.d(TAG, "   Most recent event: status=${mostRecentEvent.dutyStatus}, " +
                    "startTime=${java.util.Date(mostRecentEvent.startTime)}, isActive=${mostRecentEvent.isActive}")
            } else {
                Log.d(TAG, "   ⚠️ No events in database!")
            }

            val needsInitialEvent = mostRecentEvent == null || mostRecentEvent.startTime < sixMonthsAgo

            if (needsInitialEvent) {
                Log.d(TAG, "📝 Driver needs initial OFF_DUTY events (no recent events or > 6 months old)")
                createOffDutyEventsForEmptyHistory()
            }

            // STEP 4.5: Split any multi-day local events into daily segments
            // This handles events that were created locally while app was offline
            // and span multiple days (e.g., DRIVING from Dec 20 to Dec 24)
            splitMultiDayLocalEvents()

            // STEP 5: ALWAYS process sync queue after initial sync
            // This syncs any new/updated events back to server
            // Important because:
            // - New driver: sync the OFF_DUTY events we just created
            // - Existing driver: sync any recalculated data
            Log.d(TAG, "📤 Processing sync queue after initial sync...")
            val syncedCount = processQueue()
            Log.d(TAG, "📤 Synced $syncedCount events to server")

            // STEP 6: Analyze and sync violations
            // KEY: Violations are DERIVED from event history, not tracked in real-time
            // This ensures accurate start/end times even if driver was offline
            Log.d(TAG, "📊 Analyzing and syncing violations...")
            val violationSyncService = ViolationSyncService.getInstance(context)
            val violationsSynced = violationSyncService.analyzeAndSyncViolations()
            Log.d(TAG, "📊 Violations synced: $violationsSynced")

            _lastSyncTime.value = System.currentTimeMillis()
            updatePendingCount()

            // Log total events now in database for debugging
            val totalInDb = dutyStatusEventDao.getEventsSince(
                System.currentTimeMillis() - (SYNC_DAYS.toLong() * 24 * 60 * 60 * 1000)
            ).size

            Log.d(TAG, "═══════════════════════════════════════════════════════")
            Log.d(TAG, "✅ INITIAL SYNC COMPLETE:")
            Log.d(TAG, "   - Events fetched from server: $totalEvents")
            Log.d(TAG, "   - Events synced to server: $syncedCount")
            Log.d(TAG, "   - Violations synced: $violationsSynced")
            Log.d(TAG, "   - Total events in local DB (last $SYNC_DAYS days): $totalInDb")
            Log.d(TAG, "   - Company timezone: ${companyTz.id}")
            Log.d(TAG, "═══════════════════════════════════════════════════════")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Initial sync failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PERIODIC SYNC
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Perform periodic sync.
     * Called every 5 minutes.
     * ONLY SENDS local changes to server - does NOT pull from server.
     * Server data is only fetched on login (initial sync).
     *
     * NOTE: LastSyncTime on backend is updated automatically when HOS is synced.
     */
    suspend fun performPeriodicSync() {
        if (!_isOnline.value) {
            Log.d(TAG, "Offline - periodic sync skipped")
            return
        }

        Log.d(TAG, "Starting periodic sync")

        // STEP 1: Process pending queue items (send local changes to server)
        val eventsSynced = processQueue()
        Log.d(TAG, "📤 Events synced: $eventsSynced")

        // STEP 2: Analyze and sync violations
        // This re-analyzes from event history and syncs any new/ended violations
        val violationSyncService = ViolationSyncService.getInstance(context)
        val violationsSynced = violationSyncService.analyzeAndSyncViolations()
        Log.d(TAG, "📊 Violations synced: $violationsSynced")

        // NOTE: We do NOT pull events from server during periodic sync
        // Local database is the source of truth after initial sync
        // This prevents data mixing/confusion

        // NOTE: LastSyncTime is updated on backend automatically when HOS is synced
        // (in HOSService.syncHOSToBackend via POST api/mobile/drivers/hos)

        _lastSyncTime.value = System.currentTimeMillis()
    }

    // REMOVED: pullLatestEvents - we don't pull during periodic sync anymore
    // Data flow: Login -> Initial Sync (pull) -> Work locally -> Periodic Sync (push only)

    @Deprecated("No longer used - we don't pull events during periodic sync")
    private suspend fun pullLatestEvents_DEPRECATED() {
        val token = getAuthToken() ?: return

        try {
            // Get today's events
            val today = formatDate(Date())
            val response = apiService.getDriverEvents(token, today)

            if (response.isSuccessful && response.body()?.success == true) {
                val eventsData = response.body()?.data
                if (eventsData != null) {
                    // Convert and save, respecting local pending changes
                    val serverEntities = eventsData.dutyStatusEvents.map { it.toEntity(today) }

                    for (entity in serverEntities) {
                        // Only update if we don't have pending changes for this event
                        val existing = entity.serverId?.let { dutyStatusEventDao.getEventByServerId(it) }
                        if (existing == null || !existing.pendingSync) {
                            dutyStatusEventDao.insert(entity)
                        }
                    }

                    Log.d(TAG, "Pulled ${serverEntities.size} events from server")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pulling latest events", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LOCAL EVENT CREATION
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Create a local duty status event and enqueue for sync.
     * Returns the local event ID.
     */
    suspend fun createLocalDutyStatusEvent(
        request: DutyStatusChangeRequest
    ): String {
        val now = System.currentTimeMillis()
        val localId = UUID.randomUUID().toString()

        // Close the current active event
        val currentActive = dutyStatusEventDao.getCurrentActiveEvent()
        if (currentActive != null) {
            val durationMs = now - currentActive.startTime
            val durationMinutes = (durationMs / 60000).toInt()
            dutyStatusEventDao.closeEvent(currentActive.id, now, durationMinutes)
        }

        // Create new event
        val newEvent = DutyStatusEventEntity(
            id = localId,
            dutyStatus = request.dutyStatus.name,
            startTime = now,
            endTime = null,
            location = request.location,
            latitude = request.latitude,
            longitude = request.longitude,
            vehicleId = request.vehicleId,
            deviceId = request.deviceId,
            odometer = request.odometer,
            engineHours = request.engineHours,
            note = request.note,
            shippingDocumentNumber = request.shippingDocumentNumber,
            trailerNumber = request.trailerNumber,
            isActive = true,
            isSynced = false,
            pendingSync = true,
            localCreatedAt = now
        )

        dutyStatusEventDao.insert(newEvent)

        // Enqueue for sync - IMPORTANT: Add startTime to request so server knows
        // the ORIGINAL time when status was created (not sync time)
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        isoFormat.timeZone = TimeZone.getTimeZone("UTC")
        val requestWithTime = request.copy(
            startTime = isoFormat.format(Date(now))
        )
        enqueueDutyStatusChange(requestWithTime, localId)

        Log.d(TAG, "Created local event: $localId (${request.dutyStatus}) with startTime=${requestWithTime.startTime}")

        return localId
    }

    /**
     * Create a local duty status event that's already synced (direct API call succeeded).
     * This is used when online direct sync succeeds to keep local DB in sync.
     */
    suspend fun createSyncedDutyStatusEvent(
        request: DutyStatusChangeRequest,
        serverId: Int
    ): String {
        val now = System.currentTimeMillis()
        val localId = "direct_${UUID.randomUUID()}"

        // Close the current active event
        val currentActive = dutyStatusEventDao.getCurrentActiveEvent()
        if (currentActive != null) {
            val durationMs = now - currentActive.startTime
            val durationMinutes = (durationMs / 60000).toInt()
            dutyStatusEventDao.closeEvent(currentActive.id, now, durationMinutes)
        }

        // Create new event (already synced)
        val newEvent = DutyStatusEventEntity(
            id = localId,
            serverId = serverId,
            dutyStatus = request.dutyStatus.name,
            startTime = now,
            endTime = null,
            location = request.location,
            latitude = request.latitude,
            longitude = request.longitude,
            vehicleId = request.vehicleId,
            deviceId = request.deviceId,
            odometer = request.odometer,
            engineHours = request.engineHours,
            note = request.note,
            shippingDocumentNumber = request.shippingDocumentNumber,
            trailerNumber = request.trailerNumber,
            isActive = true,
            isSynced = true,  // Already synced!
            pendingSync = false,  // Not pending!
            localCreatedAt = now,
            serverCreatedAt = now
        )

        dutyStatusEventDao.insert(newEvent)

        Log.d(TAG, "Created synced local event: $localId (${request.dutyStatus}) -> server ID $serverId")

        return localId
    }

    /**
     * Create a local tick event and enqueue for sync.
     */
    suspend fun createLocalTickEvent(
        request: TickEventRequest
    ): String {
        val now = System.currentTimeMillis()
        val localId = UUID.randomUUID().toString()

        val entity = TickEventEntity(
            id = localId,
            eventType = request.eventType.name,
            timestamp = now,
            latitude = request.latitude,
            longitude = request.longitude,
            location = request.location,
            odometer = request.odometer,
            engineHours = request.engineHours,
            vehicleId = request.vehicleId,
            deviceId = request.deviceId,
            isSynced = false,
            pendingSync = true,
            localCreatedAt = now
        )

        tickEventDao.insert(entity)

        // Enqueue for sync - IMPORTANT: Add timestamp to request so server knows
        // the ORIGINAL time when event was created (not sync time)
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        isoFormat.timeZone = TimeZone.getTimeZone("UTC")
        val requestWithTime = request.copy(
            timestamp = isoFormat.format(Date(now))
        )
        enqueueTickEvent(requestWithTime, localId)

        return localId
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private fun getAuthToken(): String? {
        // Token from ELDDriverApplication already has "Bearer " prefix
        return ELDDriverApplication.getAuthToken()
    }

    /**
     * Format date in yyyy-MM-dd format using specified timezone.
     * Defaults to device timezone for backward compatibility.
     */
    private fun formatDate(date: Date, tz: TimeZone = TimeZone.getDefault()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = tz
        return sdf.format(date)
    }

    /**
     * @deprecated Use createOffDutyEventsForEmptyHistory() instead.
     * This creates a single event spanning 8 days which doesn't work correctly for daily logs.
     */
    @Deprecated("Use createOffDutyEventsForEmptyHistory() instead")
    private suspend fun createInitialOffDutyEvent() {
        val now = System.currentTimeMillis()
        val localId = "initial_offduty_${UUID.randomUUID()}"

        // Get current location (required for FMCSA compliance)
        val currentLocation = locationService.getCurrentLocation()
        val fmcsaLocation = if (currentLocation != null) {
            locationService.getFMCSALocation(currentLocation.latitude, currentLocation.longitude)
        } else null
        val locationString = fmcsaLocation ?: "Location unavailable"

        // Get vehicle and device info from global state
        val vehicleId = ELDDriverApplication.getCurrentVehicleId()
        val deviceId = ELDDriverApplication.getCurrentDeviceId()

        // Get telemetry from ELD if connected
        val eldData = bleManager.eldData.value
        val odometerMiles = eldData?.odometer?.let { it * 0.621371 }  // Convert km to miles
        val engineHours = eldData?.engineHours

        Log.d(TAG, "📍 Initial event: location=$locationString, vehicleId=$vehicleId, deviceId=$deviceId")

        // Start time = 8 days ago (7 days + 24h = 8 days)
        // This covers the entire logs view period
        val eightDaysAgo = now - (8L * 24 * 60 * 60 * 1000)

        val event = DutyStatusEventEntity(
            id = localId,
            serverId = null,
            dutyStatus = DutyStatusType.OFF_DUTY.name,
            startTime = eightDaysAgo,  // Started 8 days ago
            endTime = null, // Active event - no end time (still ongoing)
            durationMinutes = null,
            location = locationString,
            latitude = currentLocation?.latitude,
            longitude = currentLocation?.longitude,
            vehicleId = vehicleId,
            deviceId = deviceId,
            odometer = odometerMiles,
            engineHours = engineHours,
            note = "Auto-created: Driver had no recent duty status history",
            shippingDocumentNumber = null,
            trailerNumber = null,
            isActive = true,
            isSynced = false,
            pendingSync = true,
            localCreatedAt = now,
            serverCreatedAt = null
        )

        dutyStatusEventDao.insert(event)
        Log.d(TAG, "📝 Created initial OFF_DUTY event starting ${formatDate(Date(eightDaysAgo))} (8 days ago)")

        // Enqueue for sync to server
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        isoFormat.timeZone = TimeZone.getTimeZone("UTC")

        val request = DutyStatusChangeRequest(
            dutyStatus = DutyStatusType.OFF_DUTY,
            vehicleId = vehicleId,
            deviceId = deviceId,
            latitude = currentLocation?.latitude,
            longitude = currentLocation?.longitude,
            location = locationString,
            odometer = odometerMiles,
            engineHours = engineHours,
            note = "Auto-created: Driver had no recent duty status history",
            startTime = isoFormat.format(Date(eightDaysAgo)),  // Send actual start time (8 days ago)
            shippingDocumentNumber = null,
            trailerNumber = null,
            // FMCSA: Origin=1 (Auto by ELD) for system-generated events
            eventRecordOrigin = FmcsaEventRecordOrigin.AUTO_BY_ELD
        )
        enqueueDutyStatusChange(request, localId)
        Log.d(TAG, "📤 Enqueued initial OFF_DUTY event for sync (startTime: ${isoFormat.format(Date(eightDaysAgo))})")
    }

    /**
     * Create OFF_DUTY events for each day when driver has no recent history.
     * Creates 8 events total:
     * - 7 full 24-hour OFF_DUTY events (day -7 to day -1)
     * - 1 active OFF_DUTY event for today (00:00 to now, remains active)
     * Each event is enqueued for sync to server with its actual startTime.
     */
    private suspend fun createOffDutyEventsForEmptyHistory() {
        val now = System.currentTimeMillis()
        val events = mutableListOf<DutyStatusEventEntity>()

        // Get current location (required for FMCSA compliance)
        // Use getLocationWithFallback() which WAITS for location - essential for Huawei phones
        Log.d(TAG, "📍 Requesting location for initial OFF_DUTY events (will wait up to 10s for Huawei compatibility)...")
        val currentLocation = locationService.getLocationWithFallback(timeoutMs = 10000)
        val fmcsaLocation = if (currentLocation != null) {
            locationService.getFMCSALocation(currentLocation.latitude, currentLocation.longitude)
        } else null
        val locationString = fmcsaLocation ?: "Location unavailable"

        // Get vehicle and device info from global state
        val vehicleId = ELDDriverApplication.getCurrentVehicleId()
        val deviceId = ELDDriverApplication.getCurrentDeviceId()

        // Get telemetry from ELD if connected
        val eldData = bleManager.eldData.value
        val odometerMiles = eldData?.odometer?.let { it * 0.621371 }  // Convert km to miles
        val engineHours = eldData?.engineHours

        Log.d(TAG, "📍 Initial events: location=$locationString, vehicleId=$vehicleId, deviceId=$deviceId, odometer=$odometerMiles, engineHours=$engineHours")

        // Use company timezone for date calculations
        val companyTz = tokenManager.getCompanyTimeZone()

        // Start from 7 days ago (not 8) and work forward to today
        // This creates 8 events: day -7, -6, -5, -4, -3, -2, -1, 0 (today)
        val calendar = Calendar.getInstance(companyTz)
        calendar.add(Calendar.DAY_OF_YEAR, -7)  // 7 days ago
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        Log.d(TAG, "📅 Creating 8 OFF_DUTY events (7 full days + today) in timezone ${companyTz.id}...")

        // Create 8 events (day -7 to day 0)
        for (dayIndex in 0..7) {
            val dayStart = calendar.timeInMillis
            val isToday = dayIndex == 7  // Last iteration is today

            // Calculate end time
            val endTime: Long?
            val durationMinutes: Int?

            if (isToday) {
                // Today: active event, no endTime
                endTime = null
                durationMinutes = null
            } else {
                // Past days: full 24 hours (00:00:00 to 23:59:59.999)
                endTime = dayStart + (24 * 60 * 60 * 1000) - 1
                durationMinutes = 24 * 60
            }

            val event = DutyStatusEventEntity(
                id = "offline_init_${formatDate(calendar.time, companyTz)}",
                serverId = null,
                dutyStatus = DutyStatusType.OFF_DUTY.name,
                startTime = dayStart,
                endTime = endTime,
                durationMinutes = durationMinutes,
                location = locationString,
                latitude = currentLocation?.latitude,
                longitude = currentLocation?.longitude,
                vehicleId = vehicleId,
                deviceId = deviceId,
                odometer = odometerMiles,
                engineHours = engineHours,
                note = "Auto-created: Driver had no duty status history",
                shippingDocumentNumber = null,
                trailerNumber = null,
                isActive = isToday,
                isSynced = false,
                pendingSync = true,
                localCreatedAt = now,
                serverCreatedAt = null
            )

            events.add(event)
            Log.d(TAG, "   📅 ${formatDate(calendar.time, companyTz)}: OFF_DUTY ${if (isToday) "(active)" else "(24h closed)"}")

            // Move to next day
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Insert all events
        if (events.isNotEmpty()) {
            dutyStatusEventDao.insertAll(events)
            Log.d(TAG, "✅ Created ${events.size} OFF_DUTY events")

            // Enqueue ALL events for sync to server
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            isoFormat.timeZone = TimeZone.getTimeZone("UTC")

            for (event in events) {
                val startTimeIso = isoFormat.format(Date(event.startTime))

                val request = DutyStatusChangeRequest(
                    dutyStatus = DutyStatusType.OFF_DUTY,
                    vehicleId = vehicleId,
                    deviceId = deviceId,
                    latitude = currentLocation?.latitude,
                    longitude = currentLocation?.longitude,
                    location = locationString,
                    odometer = odometerMiles,
                    engineHours = engineHours,
                    note = "Auto-created: Driver had no duty status history",
                    startTime = startTimeIso,
                    shippingDocumentNumber = null,
                    trailerNumber = null,
                    // FMCSA: Origin=1 (Auto by ELD) for system-generated events
                    eventRecordOrigin = FmcsaEventRecordOrigin.AUTO_BY_ELD
                )
                enqueueDutyStatusChange(request, event.id)
                Log.d(TAG, "   📤 Enqueued OFF_DUTY for ${formatDate(Date(event.startTime), companyTz)} with startTime=$startTimeIso")
            }
            Log.d(TAG, "📤 Enqueued ${events.size} OFF_DUTY events for sync")
        }
    }

    private fun updatePendingCount() {
        scope.launch {
            _pendingSyncCount.value = syncQueueDao.getPendingCount()
        }
    }

    /**
     * Split any multi-day LOCAL events into daily segments.
     * Called after initial sync and periodically to handle events that span multiple days.
     *
     * This handles locally created events (not synced from server) that may have
     * been created while the app was offline for multiple days.
     *
     * For each local event that spans multiple days:
     * - Create separate events for each day
     * - Past days: full 24h (00:00-23:59:59)
     * - Current day: 00:00 to now (active if original was active)
     * - Enqueue all new events for sync
     */
    suspend fun splitMultiDayLocalEvents() {
        val companyTz = tokenManager.getCompanyTimeZone()
        val now = System.currentTimeMillis()

        Log.d(TAG, "🔀 Checking for multi-day local events to split...")

        // Get all local (unsynced) events - these have IDs that don't start with "server_"
        val allEvents = dutyStatusEventDao.getEventsSince(
            now - (SYNC_DAYS.toLong() * 24 * 60 * 60 * 1000)
        )

        val localEvents = allEvents.filter { !it.id.startsWith("server_") }

        if (localEvents.isEmpty()) {
            Log.d(TAG, "   No local events to check")
            return
        }

        Log.d(TAG, "   Checking ${localEvents.size} local events...")

        for (event in localEvents) {
            // Calculate which days this event spans
            val startCalendar = Calendar.getInstance(companyTz).apply {
                timeInMillis = event.startTime
            }
            val startDayOfYear = startCalendar.get(Calendar.YEAR) * 1000 + startCalendar.get(Calendar.DAY_OF_YEAR)

            val endMs = event.endTime ?: now
            val endCalendar = Calendar.getInstance(companyTz).apply {
                timeInMillis = endMs
            }
            val endDayOfYear = endCalendar.get(Calendar.YEAR) * 1000 + endCalendar.get(Calendar.DAY_OF_YEAR)

            if (startDayOfYear == endDayOfYear) {
                // Same day, no split needed
                continue
            }

            val daysSpanned = ((endMs - event.startTime) / (24 * 60 * 60 * 1000)).toInt() + 1
            Log.d(TAG, "   📅 Event ${event.id} (${event.dutyStatus}) spans $daysSpanned days - splitting...")

            // Delete the original multi-day event
            dutyStatusEventDao.delete(event.id)

            // Remove from sync queue if pending
            syncQueueDao.removeByLocalId(event.id)

            // Create daily segments
            val calendar = Calendar.getInstance(companyTz).apply {
                timeInMillis = event.startTime
            }

            var dayIndex = 0
            while (true) {
                val currentDayStart = calendar.clone() as Calendar
                currentDayStart.set(Calendar.HOUR_OF_DAY, 0)
                currentDayStart.set(Calendar.MINUTE, 0)
                currentDayStart.set(Calendar.SECOND, 0)
                currentDayStart.set(Calendar.MILLISECOND, 0)

                val currentDayEnd = currentDayStart.clone() as Calendar
                currentDayEnd.add(Calendar.DAY_OF_YEAR, 1)
                currentDayEnd.add(Calendar.MILLISECOND, -1)

                // Calculate actual start for this day's segment
                val segmentStart = if (dayIndex == 0) {
                    event.startTime // First day: use original start time
                } else {
                    currentDayStart.timeInMillis // Other days: start at 00:00
                }

                // Check if this is the last day (contains the end time)
                val isLastDay = calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR) == endDayOfYear

                // Calculate end time for this day's segment
                val segmentEnd: Long?
                val segmentDuration: Int?
                val segmentIsActive: Boolean

                if (isLastDay && event.isActive) {
                    // Last day and original was active: keep it active
                    segmentEnd = null
                    segmentDuration = null
                    segmentIsActive = true
                } else if (isLastDay) {
                    // Last day and original was closed: use original end time
                    segmentEnd = event.endTime
                    segmentDuration = ((segmentEnd!! - segmentStart) / 60000).toInt()
                    segmentIsActive = false
                } else {
                    // Middle day: full 24h until 23:59:59
                    segmentEnd = currentDayEnd.timeInMillis
                    segmentDuration = ((segmentEnd - segmentStart) / 60000).toInt()
                    segmentIsActive = false
                }

                val dateStr = formatDate(calendar.time, companyTz)
                val newId = "${event.id}_split_$dateStr"

                val splitEvent = DutyStatusEventEntity(
                    id = newId,
                    serverId = event.serverId,
                    dutyStatus = event.dutyStatus,
                    startTime = segmentStart,
                    endTime = segmentEnd,
                    durationMinutes = segmentDuration,
                    location = event.location,
                    latitude = event.latitude,
                    longitude = event.longitude,
                    vehicleId = event.vehicleId,
                    deviceId = event.deviceId,
                    odometer = event.odometer,
                    engineHours = event.engineHours,
                    note = event.note,
                    shippingDocumentNumber = event.shippingDocumentNumber,
                    trailerNumber = event.trailerNumber,
                    isActive = segmentIsActive,
                    // Preserve FMCSA fields from original event
                    eventRecordOrigin = event.eventRecordOrigin,
                    eventRecordStatus = event.eventRecordStatus,
                    isSynced = false,
                    pendingSync = true,
                    localCreatedAt = now,
                    serverCreatedAt = null
                )

                dutyStatusEventDao.insert(splitEvent)
                Log.d(TAG, "      ✅ Created $dateStr: ${event.dutyStatus} ${if (segmentIsActive) "(active)" else "($segmentDuration min)"}")

                // Enqueue for sync
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                isoFormat.timeZone = TimeZone.getTimeZone("UTC")

                val request = DutyStatusChangeRequest(
                    dutyStatus = DutyStatusType.valueOf(event.dutyStatus),
                    vehicleId = event.vehicleId,
                    deviceId = event.deviceId,
                    latitude = event.latitude,
                    longitude = event.longitude,
                    location = event.location,
                    odometer = event.odometer,
                    engineHours = event.engineHours,
                    note = event.note,
                    startTime = isoFormat.format(Date(segmentStart)),
                    shippingDocumentNumber = event.shippingDocumentNumber,
                    trailerNumber = event.trailerNumber,
                    // Preserve FMCSA fields from original event
                    eventRecordOrigin = event.eventRecordOrigin,
                    eventRecordStatus = event.eventRecordStatus
                )
                enqueueDutyStatusChange(request, newId)

                if (isLastDay) break

                // Move to next day
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                dayIndex++
            }
        }

        Log.d(TAG, "🔀 Multi-day event splitting complete")
    }

    /**
     * Clear all local data (for logout).
     */
    suspend fun clearAllData() {
        dutyStatusEventDao.deleteAll()
        tickEventDao.deleteAll()
        syncQueueDao.clearQueue()
        database.hosStatusDao().deleteHOSStatus()
        _pendingSyncCount.value = 0
        _lastSyncTime.value = null
        Log.d(TAG, "Cleared all local data")
    }

    /**
     * Cleanup resources.
     */
    fun cleanup() {
        scope.cancel()
        networkCallback?.let {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(it)
        }
    }
}

// Payload classes for JSON serialization
private data class StatusChangePayload(
    val localEventId: String,
    val request: DutyStatusChangeRequest
)

private data class TickEventPayload(
    val localEventId: String,
    val request: TickEventRequest
)

// Extension to convert API DTO to Entity
// IMPORTANT: Uses CLAMPED times for display (what server returns for each day)
// Backend returns events clamped to each day's boundaries for correct daily display
// actualStartTime/actualEndTime are only used for events that span day boundaries
private fun com.eld.driver.data.models.DutyStatusEventDto.toEntity(queryDate: String): DutyStatusEventEntity {
    // Use the CLAMPED startTime from server (already adjusted to day boundaries)
    val startTimeMs = parseDateTime(startTime)

    // Use the CLAMPED endTime from server
    // If event is active, endTime should be null
    val endTimeMs = if (isActive) {
        null
    } else {
        endTime?.let { parseDateTime(it) }
    }

    // Use server-provided durationMinutes (already clamped to this day's portion)
    val calculatedDurationMinutes = durationMinutes

    // Create unique local ID per day to handle multi-day events
    // Same server event appearing on different days gets different local IDs
    // IMPORTANT: Use the QUERY date (the date we requested), not the startTime date!
    // This fixes ID collision when same event (e.g., 23-hour DRIVING) is split across days
    // and returned with different portions for each day's query.
    // Format: server_{id}_{queryDate} e.g., server_123_2024-12-24
    val localId = "server_${id}_$queryDate"

    return DutyStatusEventEntity(
        id = localId,
        serverId = id,
        dutyStatus = dutyStatus.name,
        startTime = startTimeMs,
        endTime = endTimeMs,
        durationMinutes = calculatedDurationMinutes,
        location = location,
        latitude = latitude,
        longitude = longitude,
        vehicleId = vehicleId,
        odometer = odometer,
        engineHours = engineHours,
        note = note,  // Map note from server DTO
        isActive = isActive,
        // FMCSA fields - use server values if available, otherwise defaults
        eventRecordOrigin = eventRecordOrigin ?: FmcsaEventRecordOrigin.DRIVER,
        eventRecordStatus = eventRecordStatus ?: FmcsaEventRecordStatus.ACTIVE,
        isSynced = true,
        pendingSync = false,
        localCreatedAt = System.currentTimeMillis(),
        serverCreatedAt = startTimeMs
    )
}

private fun parseDateTime(dateTimeString: String): Long {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val cleanTime = dateTimeString.substringBefore("Z").substringBefore("+").take(19)
        inputFormat.parse(cleanTime)?.time ?: System.currentTimeMillis()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }
}
