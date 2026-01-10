package com.eld.driver.hos

import android.content.Context
import android.util.Log
import com.eld.driver.data.local.ELDDatabase
import com.eld.driver.data.local.dao.DutyStatusEventDao
import com.eld.driver.data.local.dao.HOSStatusDao
import com.eld.driver.data.local.entity.HOSViolationType
import com.eld.driver.data.api.ApiService
import com.eld.driver.data.models.DutyStatusChangeRequest
import com.eld.driver.data.models.DutyStatusType
import com.eld.driver.data.models.HOSSyncRequest
import com.eld.driver.data.models.IntermediateEventRequest
import com.eld.driver.data.models.TickEventRequest
import com.eld.driver.data.models.TickEventType
import java.text.SimpleDateFormat
import java.util.*
import com.eld.driver.sync.SyncManager
import com.eld.driver.location.LocationService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * HOSService manages Hours of Service calculations and sync operations.
 *
 * Responsibilities:
 * - Calculate HOS every minute
 * - Perform sync every 5 minutes
 * - Provide HOS state to UI
 * - Handle violations
 */
class HOSService private constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "HOSService"
        private const val HOS_CALCULATION_INTERVAL_MS = 60_000L    // 1 minute
        private const val PERIODIC_SYNC_INTERVAL_MS = 5 * 60_000L  // 5 minutes
        private const val INTERMEDIATE_EVENT_INTERVAL_MS = 60 * 60_000L // 60 minutes (FMCSA requirement)
        private const val CYCLE_DAYS = 8

        @Volatile
        private var INSTANCE: HOSService? = null

        fun getInstance(context: Context): HOSService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HOSService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val database = ELDDatabase.getInstance(context)
    private val dutyStatusEventDao: DutyStatusEventDao = database.dutyStatusEventDao()
    private val hosStatusDao: HOSStatusDao = database.hosStatusDao()
    private val hosCalculator = HOSCalculator()
    private val syncManager = SyncManager.getInstance(context)
    private val apiService = ApiService.getInstance()
    private val locationService = LocationService.getInstance(context)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // HOS State
    private val _hosStatus = MutableStateFlow<HOSCalculationResult?>(null)
    val hosStatus: StateFlow<HOSCalculationResult?> = _hosStatus.asStateFlow()

    // Current duty status (for context)
    private val _currentDutyStatus = MutableStateFlow<String?>(null)
    val currentDutyStatus: StateFlow<String?> = _currentDutyStatus.asStateFlow()

    // Service state
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // Violations callback (for UI warnings)
    // Note: Violations are now analyzed by ViolationSyncService during sync
    // This callback is just for showing warnings to the user in real-time
    var onViolationDetected: ((List<HOSViolationType>) -> Unit)? = null

    // Last HOS sync timestamp
    private var lastHOSSyncTime: Long = 0L
    private val _lastHOSSyncError = MutableStateFlow<String?>(null)
    val lastHOSSyncError: StateFlow<String?> = _lastHOSSyncError.asStateFlow()

    private var calculationJob: Job? = null
    private var syncJob: Job? = null
    private var tickJob: Job? = null

    // ═══════════════════════════════════════════════════════════════════════
    // SERVICE LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Start the HOS service.
     * Begins periodic HOS calculation and sync.
     */
    fun start() {
        if (_isRunning.value) {
            Log.d(TAG, "Service already running")
            return
        }

        Log.d(TAG, "Starting HOS Service")
        _isRunning.value = true

        // Load cached HOS immediately
        scope.launch {
            loadCachedHOS()
        }

        // Start periodic HOS calculation (every 1 minute)
        startHOSCalculation()

        // Start periodic sync (every 5 minutes)
        startPeriodicSync()

        // Start intermediate events during DRIVING (every 60 minutes - FMCSA requirement)
        startIntermediateEvents()
    }

    /**
     * Stop the HOS service.
     */
    fun stop() {
        Log.d(TAG, "Stopping HOS Service")
        _isRunning.value = false

        calculationJob?.cancel()
        syncJob?.cancel()
        tickJob?.cancel()

        calculationJob = null
        syncJob = null
        tickJob = null
    }

    /**
     * Cleanup all resources.
     */
    fun cleanup() {
        stop()
        scope.cancel()
        syncManager.cleanup()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HOS CALCULATION
    // ═══════════════════════════════════════════════════════════════════════

    private fun startHOSCalculation() {
        calculationJob = scope.launch {
            while (isActive) {
                calculateAndSaveHOS()
                delay(HOS_CALCULATION_INTERVAL_MS)
            }
        }
    }

    /**
     * Calculate HOS from local events and save to database.
     * Note: Violations are now analyzed by ViolationSyncService during sync,
     * not tracked in real-time here.
     */
    suspend fun calculateAndSaveHOS() {
        try {
            val eightDaysAgo = System.currentTimeMillis() - (CYCLE_DAYS * 24L * 60 * 60 * 1000)
            val events = dutyStatusEventDao.getEventsSince(eightDaysAgo)

            // Get current active event for status
            val activeEvent = dutyStatusEventDao.getCurrentActiveEvent()
            _currentDutyStatus.value = activeEvent?.dutyStatus

            if (events.isNotEmpty()) {
                val result = hosCalculator.calculate(events, activeEvent?.dutyStatus)
                _hosStatus.value = result

                // Save to database for offline access
                hosStatusDao.saveHOSStatus(result.toEntity())

                // Notify UI about current violations (for warnings only)
                if (result.hasViolations()) {
                    Log.w(TAG, "HOS Violations detected: ${result.violations}")
                    onViolationDetected?.invoke(result.violations)
                }

                Log.d(TAG, "HOS calculated - Drive: ${result.driveTimeRemainingFormatted}, " +
                        "Shift: ${result.shiftTimeRemainingFormatted}, " +
                        "Break: ${result.breakTimeRemainingFormatted}, " +
                        "Cycle: ${result.cycleTimeRemainingFormatted}")
            } else {
                // No events, create default HOS
                val defaultResult = hosCalculator.calculate(emptyList(), activeEvent?.dutyStatus)
                _hosStatus.value = defaultResult
                hosStatusDao.saveHOSStatus(defaultResult.toEntity())
                Log.d(TAG, "No events found, using default HOS")
            }
        } catch (e: Exception) {
            Log.e(TAG, "HOS calculation failed", e)
        }
    }

    /**
     * Force immediate HOS recalculation.
     * Called after status changes.
     */
    suspend fun recalculateNow() {
        calculateAndSaveHOS()
    }

    /**
     * Load cached HOS from database.
     */
    private suspend fun loadCachedHOS() {
        try {
            val cached = hosStatusDao.getHOSStatus()
            if (cached != null) {
                val cachedViolations = cached.getViolationsList()

                // Convert entity back to result (approximately)
                _hosStatus.value = HOSCalculationResult(
                    driveTimeRemainingMs = cached.driveTimeRemainingSeconds * 1000,
                    shiftTimeRemainingMs = cached.shiftTimeRemainingSeconds * 1000,
                    breakTimeRemainingMs = cached.breakTimeRemainingSeconds * 1000,
                    cycleTimeRemainingMs = cached.cycleTimeRemainingSeconds * 1000,
                    currentDriveTimeMs = cached.driveTimeUsedSeconds * 1000,
                    currentShiftDurationMs = cached.shiftTimeUsedSeconds * 1000,
                    drivingTimeSinceBreakMs = cached.breakTimeDrivingSeconds * 1000,
                    currentCycleHoursMs = cached.cycleTimeUsedSeconds * 1000,
                    shiftStartTime = cached.shiftStartTime ?: System.currentTimeMillis(),
                    lastBreakEndTime = cached.lastBreakEndTime,
                    calculatedAt = cached.lastCalculatedAt,
                    violations = cachedViolations,
                    currentDutyStatus = cached.currentDutyStatus
                )
                _currentDutyStatus.value = cached.currentDutyStatus

                Log.d(TAG, "Loaded cached HOS from database")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cached HOS", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PERIODIC SYNC
    // ═══════════════════════════════════════════════════════════════════════

    private fun startPeriodicSync() {
        syncJob = scope.launch {
            while (isActive) {
                delay(PERIODIC_SYNC_INTERVAL_MS)
                performPeriodicSync()
            }
        }
    }

    private suspend fun performPeriodicSync() {
        try {
            // First, sync the queue (duty status changes, tick events)
            syncManager.performPeriodicSync()

            // Then, sync HOS to backend
            syncHOSToBackend()

            Log.d(TAG, "Periodic sync completed")
        } catch (e: Exception) {
            Log.e(TAG, "Periodic sync failed", e)
        }
    }

    /**
     * Public method to force HOS sync to backend.
     * Called before logout to ensure data is sent.
     */
    suspend fun syncHOSNow() {
        Log.d(TAG, "Force syncing HOS to backend...")
        syncHOSToBackend()
    }

    /**
     * Sync current HOS calculation to backend.
     * Note: Violations are synced separately by ViolationSyncService.
     * Called during periodic sync and after status changes.
     */
    private suspend fun syncHOSToBackend() {
        val hos = _hosStatus.value ?: run {
            Log.d(TAG, "No HOS to sync")
            return
        }

        val token = getAuthToken() ?: run {
            Log.w(TAG, "No auth token, skipping HOS sync")
            return
        }

        if (!syncManager.isOnline.value) {
            Log.d(TAG, "Offline, skipping HOS sync to backend")
            return
        }

        try {
            val request = HOSSyncRequest(
                breakMinutes = (hos.breakTimeRemainingMs / 60000).toInt(),
                driveMinutes = (hos.driveTimeRemainingMs / 60000).toInt(),
                shiftMinutes = (hos.shiftTimeRemainingMs / 60000).toInt(),
                cycleMinutes = (hos.cycleTimeRemainingMs / 60000).toInt(),
                cycleLeftForTomorrow = (hos.cycleLeftForTomorrowMs / 60000).toInt(),
                activeViolations = null  // Violations handled by ViolationSyncService
            )

            Log.d(TAG, "Syncing HOS to backend: break=${request.breakMinutes}, drive=${request.driveMinutes}, shift=${request.shiftMinutes}, cycle=${request.cycleMinutes}")

            val response = apiService.syncHOS(token, request)

            if (response.isSuccessful && response.body()?.success == true) {
                val result = response.body()?.data
                lastHOSSyncTime = System.currentTimeMillis()
                _lastHOSSyncError.value = null
                Log.d(TAG, "✅ HOS synced to backend: ${result?.message}")
            } else {
                val error = response.body()?.error ?: "HTTP ${response.code()}"
                _lastHOSSyncError.value = error
                Log.w(TAG, "HOS sync failed: $error")
            }
        } catch (e: Exception) {
            _lastHOSSyncError.value = e.message
            Log.e(TAG, "HOS sync exception", e)
        }
    }

    private fun getAuthToken(): String? {
        return com.eld.driver.ELDDriverApplication.getAuthToken()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INTERMEDIATE EVENTS (FMCSA Requirement during DRIVING)
    // ═══════════════════════════════════════════════════════════════════════

    private fun startIntermediateEvents() {
        tickJob = scope.launch {
            while (isActive) {
                delay(INTERMEDIATE_EVENT_INTERVAL_MS)
                sendIntermediateEventIfDriving()
            }
        }
    }

    /**
     * Send intermediate event only if currently in DRIVING status.
     * FMCSA requires one intermediate event every 60 minutes during driving
     * to track GPS coordinates and vehicle metrics.
     */
    private suspend fun sendIntermediateEventIfDriving() {
        try {
            // Only send if in DRIVING status
            val currentStatus = _currentDutyStatus.value
            if (currentStatus != DutyStatusType.DRIVING.name) {
                Log.d(TAG, "Not in DRIVING status ($currentStatus), skipping intermediate event")
                return
            }

            val token = getAuthToken() ?: run {
                Log.w(TAG, "No auth token, skipping intermediate event")
                return
            }

            // Get current location from LocationService
            val currentLocation = locationService.getCurrentLocation()

            // Use FMCSA-compliant location format if we have coordinates
            val fmcsaLocation = if (currentLocation != null) {
                locationService.getFMCSALocation(currentLocation.latitude, currentLocation.longitude)
            } else null

            // Get ELD data for odometer and engine hours
            val bleManager = com.eld.driver.ELDDriverApplication.getBleManager()
            val eldData = bleManager?.eldData?.value
            val odometerMiles = eldData?.odometer?.let { it * 0.621371 } ?: 0.0
            val engineHours = eldData?.engineHours ?: 0.0

            // Format timestamp in ISO 8601
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            isoFormat.timeZone = TimeZone.getTimeZone("UTC")
            val timestamp = isoFormat.format(Date())

            val request = IntermediateEventRequest(
                time = timestamp,
                latitude = currentLocation?.latitude,
                longitude = currentLocation?.longitude,
                location = fmcsaLocation,
                odometer = odometerMiles,
                engineHours = engineHours,
                vehicleId = getCurrentVehicleId(),
                deviceId = com.eld.driver.ELDDriverApplication.getCurrentDeviceId()
            )

            // Send directly to API (intermediate events don't go through sync queue)
            if (syncManager.isOnline.value) {
                val response = apiService.createIntermediateEvent(token, request)
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    Log.d(TAG, "✅ Intermediate event sent: id=${data?.id}, location=$fmcsaLocation, odometer=$odometerMiles")
                } else {
                    val error = response.body()?.error ?: "HTTP ${response.code()}"
                    Log.w(TAG, "Intermediate event failed: $error")
                }
            } else {
                Log.d(TAG, "Offline, skipping intermediate event")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send intermediate event", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EVENT HANDLERS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Handle duty status change.
     * Creates local event, enqueues sync, recalculates HOS, and syncs HOS to backend.
     */
    suspend fun onDutyStatusChanged(request: DutyStatusChangeRequest): String {
        // Create local event and enqueue for sync
        val localEventId = syncManager.createLocalDutyStatusEvent(request)

        // Immediate HOS recalculation
        recalculateNow()

        // Analyze and sync violations after status change
        // This detects new violations or ends existing ones based on the new status
        val violationSyncService = ViolationSyncService.getInstance(context)
        val violationsSynced = violationSyncService.analyzeAndSyncViolations()
        Log.d(TAG, "Violations synced after status change: $violationsSynced")

        // Sync HOS to backend after status change
        syncHOSToBackend()

        Log.d(TAG, "Duty status changed to ${request.dutyStatus}")

        return localEventId
    }

    /**
     * Handle tick event (LOGIN, LOGOUT, CONNECTED, etc.)
     */
    suspend fun onTickEvent(request: TickEventRequest): String {
        return syncManager.createLocalTickEvent(request)
    }

    /**
     * Perform initial sync on login.
     * Note: HOS is calculated locally from events, not fetched from backend.
     */
    suspend fun performInitialSync(): Result<Unit> {
        val result = syncManager.performInitialSync()
        if (result.isSuccess) {
            // Calculate HOS from synced events
            calculateAndSaveHOS()

            // Sync HOS to backend (important for new drivers with no history)
            Log.d(TAG, "📤 Syncing HOS to backend after initial sync...")
            syncHOSToBackend()
        }
        return result
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private fun getCurrentVehicleId(): Int? {
        return com.eld.driver.ELDDriverApplication.getCurrentVehicleId()
    }

    /**
     * Get current HOS status as HOSStatus model (for API compatibility).
     */
    fun getHOSStatusModel(): com.eld.driver.data.models.HOSStatus? {
        val hos = _hosStatus.value ?: return null

        return com.eld.driver.data.models.HOSStatus(
            breakTimeRemaining = (hos.breakTimeRemainingMs / 60000).toInt(),
            driveTimeRemaining = (hos.driveTimeRemainingMs / 60000).toInt(),
            shiftTimeRemaining = (hos.shiftTimeRemainingMs / 60000).toInt(),
            cycleTimeRemaining = (hos.cycleTimeRemainingMs / 60000).toInt(),
            breakTimeUsed = (hos.drivingTimeSinceBreakMs / 60000).toInt(),
            driveTimeUsed = (hos.currentDriveTimeMs / 60000).toInt(),
            shiftTimeUsed = (hos.currentShiftDurationMs / 60000).toInt(),
            cycleTimeUsed = (hos.currentCycleHoursMs / 60000).toInt(),
            breakTimeTotal = 8 * 60,  // 8 hours in minutes
            driveTimeTotal = 11 * 60, // 11 hours in minutes
            shiftTimeTotal = 14 * 60, // 14 hours in minutes
            cycleTimeTotal = hos.cycleRule.limitHours * 60  // Dynamic based on cycle rule (70 or 60 hours)
        )
    }

    /**
     * Clear all local data and stop service.
     */
    suspend fun logout() {
        stop()
        syncManager.clearAllData()
        // Also clear violations
        ViolationSyncService.getInstance(context).clearAll()
        _hosStatus.value = null
        _currentDutyStatus.value = null
        Log.d(TAG, "Logged out - cleared all data")
    }
}
