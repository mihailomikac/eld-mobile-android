package com.eld.driver.ui.screens.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eld.driver.ble.GeometrisWQManager
import com.eld.driver.ble.VehicleMotionState
import com.eld.driver.ble.models.BleConnectionState
import com.eld.driver.ble.models.UnidentifiedEvent
import com.eld.driver.data.models.*
import com.eld.driver.data.repository.ELDRepository
import com.eld.driver.hos.HOSCalculationResult
import com.eld.driver.location.LocationService
import com.eld.driver.location.LocationData
import com.eld.driver.service.ELDForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * DashboardViewModel - Handles dashboard data and state + BLE integration
 *
 * Data flow:
 * - On login: Initial sync pulls data from server -> saves to local DB
 * - During use: UI observes local DB via Flow (single source of truth)
 * - Changes: Save locally first -> queue for sync -> periodic sync sends to server
 * - Never pulls from server after initial sync (to prevent data mixing)
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    // Repository for offline-first data access (single source of truth)
    private val repository = ELDRepository.getInstance(application)

    // BLE Manager for ELD device (singleton - shared across all screens)
    private val bleManager = GeometrisWQManager.getInstance(application)

    // Location Service for GPS + BLE location
    private val locationService = LocationService.getInstance(application)

    // TODO: Change hardcoded ELD serial to real device serial number
    private val hardcodedEldSerial = "87A4141310908"

    private val _currentDutyStatus = MutableStateFlow<DutyStatusUiState>(DutyStatusUiState.Loading)
    val currentDutyStatus: StateFlow<DutyStatusUiState> = _currentDutyStatus.asStateFlow()

    // HOS Status from local calculation
    private val _hosStatus = MutableStateFlow<HOSStatus?>(null)
    val hosStatus: StateFlow<HOSStatus?> = _hosStatus.asStateFlow()

    // Flag to prevent re-initialization on ViewModel recreation
    private var isInitialized = false

    // HOS Calculation Result (detailed, for advanced UI)
    private val _hosCalculationResult = MutableStateFlow<HOSCalculationResult?>(null)
    val hosCalculationResult: StateFlow<HOSCalculationResult?> = _hosCalculationResult.asStateFlow()

    // Flag to track if initial sync has been done (only once per session)
    private var initialSyncCompleted = false

    // Loading state for Dashboard - true until initial data is loaded
    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    // Sync status
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _pendingSyncCount = MutableStateFlow(0)
    val pendingSyncCount: StateFlow<Int> = _pendingSyncCount.asStateFlow()

    // Last sync result for debug display
    private val _lastSyncResult = MutableStateFlow<String?>(null)

    // Refresh tick - increments every minute to trigger UI recomposition
    private val _refreshTick = MutableStateFlow(0L)
    val refreshTick: StateFlow<Long> = _refreshTick.asStateFlow()

    // ELD Connection State
    private val _eldConnectionStatus = MutableStateFlow(ELDConnectionStatus.DISCONNECTED)
    val eldConnectionStatus: StateFlow<ELDConnectionStatus> = _eldConnectionStatus.asStateFlow()

    // Debug logs for UI display
    private val _debugLogs = MutableStateFlow<List<String>>(emptyList())
    val debugLogs: StateFlow<List<String>> = _debugLogs.asStateFlow()

    // Vehicle motion state (In Motion / Stationary)
    val vehicleMotionState: StateFlow<VehicleMotionState> = bleManager.vehicleMotionState

    // Connection lost while driving alert
    private val _showConnectionLostAlert = MutableStateFlow(false)
    val showConnectionLostAlert: StateFlow<Boolean> = _showConnectionLostAlert.asStateFlow()

    // Auto-restart scan countdown
    private val _autoRestartCountdown = MutableStateFlow<Int?>(null)
    val autoRestartCountdown: StateFlow<Int?> = _autoRestartCountdown.asStateFlow()

    // Reconnect attempt info (attempt/maxAttempts)
    private val _reconnectAttemptInfo = MutableStateFlow<Pair<Int, Int>?>(null)
    val reconnectAttemptInfo: StateFlow<Pair<Int, Int>?> = _reconnectAttemptInfo.asStateFlow()

    // Status change error/success message for UI display
    private val _statusChangeMessage = MutableStateFlow<String?>(null)
    val statusChangeMessage: StateFlow<String?> = _statusChangeMessage.asStateFlow()

    // Stationary delay dialog (60 sec countdown after 5 min idle)
    private val _showStationaryDelayDialog = MutableStateFlow(false)
    val showStationaryDelayDialog: StateFlow<Boolean> = _showStationaryDelayDialog.asStateFlow()

    private val _stationaryDelayCountdown = MutableStateFlow<Int?>(null)
    val stationaryDelayCountdown: StateFlow<Int?> = _stationaryDelayCountdown.asStateFlow()

    // Bluetooth enable request
    private val _showBluetoothEnableRequest = MutableStateFlow(false)
    val showBluetoothEnableRequest: StateFlow<Boolean> = _showBluetoothEnableRequest.asStateFlow()

    // Unidentified Driving events count and list
    private val _unidentifiedEventsCount = MutableStateFlow(0)
    val unidentifiedEventsCount: StateFlow<Int> = _unidentifiedEventsCount.asStateFlow()

    private val _unidentifiedEvents = MutableStateFlow<List<UnidentifiedEvent>>(emptyList())
    val unidentifiedEvents: StateFlow<List<UnidentifiedEvent>> = _unidentifiedEvents.asStateFlow()

    init {
        // NOTE: isInitialLoading stays TRUE until performInitialSync() completes
        // This ensures we show loading screen until all data (events + HOS) is ready
        android.util.Log.d("DashboardViewModel", "🔄 ViewModel created - loading screen active until sync completes")

        // Observe HOS status from repository (locally calculated)
        viewModelScope.launch {
            repository.getHOSStatusFlow().collect { result ->
                _hosCalculationResult.value = result
                // Convert to HOSStatus model for backward compatibility
                _hosStatus.value = repository.getHOSStatusModel()
            }
        }

        // Observe sync status
        viewModelScope.launch {
            repository.getPendingSyncCountFlow().collect { count ->
                _pendingSyncCount.value = count
            }
        }

        // Observe unidentified driving events from BLE Manager
        viewModelScope.launch {
            bleManager.eldData.collect { data ->
                _unidentifiedEventsCount.value = data?.totalUnidentifiedEvents ?: 0
                _unidentifiedEvents.value = data?.unidentifiedEvents ?: emptyList()

                if ((data?.totalUnidentifiedEvents ?: 0) > 0) {
                    android.util.Log.d("DashboardViewModel", "📋 UD Events received: ${data?.totalUnidentifiedEvents} total, ${data?.unidentifiedEvents?.size} in list")
                }
            }
        }

        // Also observe the dedicated UD events StateFlow
        viewModelScope.launch {
            bleManager.unidentifiedEvents.collect { events ->
                if (events.isNotEmpty()) {
                    _unidentifiedEvents.value = events
                    _unidentifiedEventsCount.value = events.size
                    android.util.Log.d("DashboardViewModel", "📋 UD Events from StateFlow: ${events.size} events")
                }
            }
        }

        // Refresh tick every minute to update status duration display
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000) // Every 1 minute
                _refreshTick.value = System.currentTimeMillis()
            }
        }

        // Periodically sync BLE manager logs to UI + add sync status
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000) // Update every second
                val bleManagerLogs = bleManager.getDebugLogs().toMutableList()

                // Add UD events status to debug logs
                val udStatus = buildString {
                    appendLine("═══ UNIDENTIFIED DRIVING ═══")
                    val udCount = _unidentifiedEventsCount.value
                    val udList = _unidentifiedEvents.value
                    appendLine("📋 Total UD Events: $udCount")
                    appendLine("📋 Events in list: ${udList.size}")

                    if (udList.isNotEmpty()) {
                        appendLine("───────────────────")
                        udList.forEachIndexed { index, event ->
                            appendLine("Event #${index + 1}:")
                            appendLine("  Reason: ${event.getReasonString()}")
                            appendLine("  Time: ${event.timestamp?.let {
                                java.text.SimpleDateFormat("MM/dd HH:mm:ss", java.util.Locale.US)
                                    .format(java.util.Date(it * 1000))
                            } ?: "N/A"}")
                            appendLine("  Speed: ${String.format("%.1f", (event.speed ?: 0.0) * 0.621371)} mph")
                            appendLine("  Odometer: ${String.format("%.1f", (event.odometer ?: 0.0) * 0.621371)} mi")
                            appendLine("  Location: ${event.latitude?.let { lat ->
                                event.longitude?.let { lon ->
                                    "${String.format("%.4f", lat)}, ${String.format("%.4f", lon)}"
                                }
                            } ?: "N/A"}")
                            if (index < udList.size - 1) appendLine("───────────────────")
                        }
                    } else {
                        appendLine("No UD events detected")
                    }
                }
                bleManagerLogs.add(0, udStatus)

                // Add sync status to debug logs
                val syncStatus = buildString {
                    appendLine("═══ SYNC STATUS ═══")
                    appendLine("Online: ${repository.isOnline()}")
                    appendLine("Pending sync: ${repository.getPendingSyncCount()}")
                    appendLine("Auth token: ${if (com.eld.driver.ELDDriverApplication.getAuthToken() != null) "SET" else "NULL"}")
                    appendLine("Vehicle ID: ${com.eld.driver.ELDDriverApplication.getCurrentVehicleId() ?: "NULL"}")

                    val lastError = repository.getLastSyncError()
                    if (lastError != null) {
                        appendLine("═══ LAST ERROR ═══")
                        appendLine("❌ $lastError")
                    }

                    if (_lastSyncResult.value != null) {
                        appendLine("═══ LAST SYNC ═══")
                        appendLine(_lastSyncResult.value)
                    }
                }
                bleManagerLogs.add(0, syncStatus)

                // Add ELD connection status
                val eldStatus = buildString {
                    appendLine("═══ ELD CONNECTION ═══")
                    appendLine("Status: ${_eldConnectionStatus.value}")
                    appendLine("Motion: ${bleManager.vehicleMotionState.value}")
                    val eldData = bleManager.eldData.value
                    if (eldData != null) {
                        appendLine("Speed: ${String.format("%.1f", (eldData.speed ?: 0.0) * 0.621371)} mph")
                        appendLine("Protocol: v${eldData.protocolVersion}")
                        appendLine("VIN: ${eldData.vin ?: "N/A"}")
                        appendLine("Total UD on device: ${eldData.totalUnidentifiedEvents}")
                    } else {
                        appendLine("No ELD data available")
                    }
                }
                bleManagerLogs.add(0, eldStatus)

                _debugLogs.value = bleManagerLogs

                // Update online status
                _isOnline.value = repository.isOnline()
            }
        }

        // SINGLE SOURCE OF TRUTH: Observe local duty status from Room database
        // UI ALWAYS shows local data. Initial sync populates local DB from server.
        viewModelScope.launch {
            repository.getCurrentDutyStatusFlow().collect { localEvent ->
                if (localEvent != null) {
                    android.util.Log.d("DashboardViewModel", "📊 Current duty status from DB: " +
                        "status=${localEvent.dutyStatus}, startTime=${java.util.Date(localEvent.startTime)}, " +
                        "isActive=${localEvent.isActive}, id=${localEvent.id}")
                    // Convert local entity to UI model
                    val dutyStatus = com.eld.driver.data.models.DutyStatus(
                        id = localEvent.serverId ?: 0,
                        dutyStatus = try {
                            DutyStatusType.valueOf(localEvent.dutyStatus)
                        } catch (e: Exception) {
                            DutyStatusType.OFF_DUTY
                        },
                        startTime = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                            timeZone = java.util.TimeZone.getTimeZone("UTC")
                        }.format(java.util.Date(localEvent.startTime)),
                        location = localEvent.location,
                        vehicleId = localEvent.vehicleId
                    )
                    _currentDutyStatus.value = DutyStatusUiState.Success(dutyStatus)

                    // Update foreground notification with current duty status
                    updateForegroundNotification(dutyStatus.dutyStatus)
                } else {
                    android.util.Log.d("DashboardViewModel", "⚠️ No duty status event in database!")
                    if (_currentDutyStatus.value is DutyStatusUiState.Loading) {
                        // Only keep loading if we haven't loaded anything yet
                        // Don't overwrite existing data with loading state
                    }
                }
            }
        }

        // NOTE: No periodic API polling here!
        // Data flow: Server → Initial Sync → Local DB → Flow → UI
        // Changes: UI → Local DB → Sync Queue → Server

        // Monitor BLE connection state
        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                _eldConnectionStatus.value = when (state) {
                    is BleConnectionState.Disconnected -> ELDConnectionStatus.DISCONNECTED
                    is BleConnectionState.Scanning -> ELDConnectionStatus.PAIRING
                    is BleConnectionState.Connecting -> ELDConnectionStatus.PAIRING
                    is BleConnectionState.Reconnecting -> ELDConnectionStatus.RECONNECTING
                    is BleConnectionState.Connected -> ELDConnectionStatus.CONNECTED
                    is BleConnectionState.Ready -> ELDConnectionStatus.CONNECTED
                    else -> ELDConnectionStatus.DISCONNECTED
                }

                // Update reconnect attempt info
                _reconnectAttemptInfo.value = if (state is BleConnectionState.Reconnecting) {
                    Pair(state.attempt, state.maxAttempts)
                } else {
                    null
                }

                // Update foreground notification when ELD connection changes
                val currentStatus = (_currentDutyStatus.value as? DutyStatusUiState.Success)?.dutyStatus?.dutyStatus
                if (currentStatus != null) {
                    updateForegroundNotification(currentStatus)
                }

                // Auto-connect to device with matching serial during scan
                if (state is BleConnectionState.DeviceFound) {
                    val deviceName = state.device.name
                    val deviceAddress = state.device.address

                    // Check if device name starts with "wq-" or "WQ-" (Geometris Whereqube devices)
                    // OR if it contains our hardcoded serial
                    val isWherequbeDevice = deviceName?.startsWith("wq-", ignoreCase = true) == true ||
                                           deviceName?.startsWith("WQ-", ignoreCase = true) == true
                    val matchesSerial = deviceName?.contains(hardcodedEldSerial, ignoreCase = true) == true ||
                                       deviceAddress?.contains(hardcodedEldSerial, ignoreCase = true) == true

                    if (isWherequbeDevice || matchesSerial) {
                        bleManager.stopScan()
                        bleManager.connect(state.device)
                    }
                }
            }
        }

        // Note: Automatic duty status change callback is now handled globally
        // in ELDDriverApplication class, so it works across all screens

        // Handle connection lost while driving
        bleManager.onConnectionLostWhileDriving = {
            android.util.Log.d("DashboardViewModel", "🚨 Connection lost while driving!")
            _showConnectionLostAlert.value = true

            // Play sound alert
            playConnectionLostSound()

            // Start auto-restart countdown
            startAutoRestartCountdown()
        }

        // Handle 5-minute stationary delay dialog
        bleManager.onShowStationaryDelayDialog = {
            android.util.Log.d("DashboardViewModel", "⏰ Showing stationary delay dialog")
            showStationaryDialog()
        }
    }

    /**
     * Show the 60-second delay dialog after 5 minutes stationary
     */
    private fun showStationaryDialog() {
        _showStationaryDelayDialog.value = true
        startStationaryCountdown()
    }

    /**
     * Start 60 second countdown for stationary delay
     */
    private var stationaryCountdownJob: kotlinx.coroutines.Job? = null

    private fun startStationaryCountdown() {
        stationaryCountdownJob?.cancel()
        stationaryCountdownJob = viewModelScope.launch {
            for (i in 60 downTo 1) {
                _stationaryDelayCountdown.value = i
                kotlinx.coroutines.delay(1000)

                // If vehicle starts moving, cancel dialog
                if (bleManager.vehicleMotionState.value == VehicleMotionState.IN_MOTION) {
                    android.util.Log.d("DashboardViewModel", "🚗 Vehicle started moving - canceling dialog")
                    dismissStationaryDialog()
                    return@launch
                }

                // If dialog was dismissed by user, stop countdown
                if (!_showStationaryDelayDialog.value) {
                    return@launch
                }
            }

            // Countdown finished - auto change to ON_DUTY
            android.util.Log.d("DashboardViewModel", "⏰ Countdown finished - changing to ON_DUTY")
            _stationaryDelayCountdown.value = null
            _showStationaryDelayDialog.value = false

            // Change status to ON_DUTY
            val token = com.eld.driver.ELDDriverApplication.getAuthToken()
            val vehicleId = com.eld.driver.ELDDriverApplication.getCurrentVehicleId()
            if (token != null) {
                changeDutyStatus(
                    token = token,
                    newStatus = DutyStatusType.ON_DUTY_NOT_DRIVING,
                    location = null,
                    notes = "Auto-changed after 5 minutes stationary + 60 sec timeout",
                    vehicleId = vehicleId,
                    onSuccess = { }
                )
            }
            bleManager.resetDialogState()
        }
    }

    /**
     * User tapped "Stay Driving" - dismiss dialog and stay in current status
     */
    fun stayDriving() {
        android.util.Log.d("DashboardViewModel", "👆 User chose to Stay Driving")
        dismissStationaryDialog()
        bleManager.resetDialogState()
    }

    /**
     * User tapped "Go On Duty" - change status to ON_DUTY
     */
    fun goOnDuty() {
        android.util.Log.d("DashboardViewModel", "👆 User chose to Go On Duty")
        dismissStationaryDialog()

        val token = com.eld.driver.ELDDriverApplication.getAuthToken()
        val vehicleId = com.eld.driver.ELDDriverApplication.getCurrentVehicleId()
        if (token != null) {
            changeDutyStatus(
                token = token,
                newStatus = DutyStatusType.ON_DUTY_NOT_DRIVING,
                location = null,
                notes = "Changed to On Duty after stationary",
                vehicleId = vehicleId,
                onSuccess = { }
            )
        }
        bleManager.resetDialogState()
    }

    /**
     * Dismiss the stationary delay dialog
     */
    private fun dismissStationaryDialog() {
        stationaryCountdownJob?.cancel()
        _showStationaryDelayDialog.value = false
        _stationaryDelayCountdown.value = null
    }

    /**
     * Play sound alert for connection lost while driving
     */
    private fun playConnectionLostSound() {
        try {
            val context = getApplication<Application>()
            val mediaPlayer = android.media.MediaPlayer.create(context, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI)
            mediaPlayer?.setOnCompletionListener { it.release() }
            mediaPlayer?.start()
            android.util.Log.d("DashboardViewModel", "🔊 Playing connection lost alert sound")
        } catch (e: Exception) {
            android.util.Log.e("DashboardViewModel", "Failed to play alert sound: ${e.message}")
        }
    }

    /**
     * Start 60 second countdown for auto-restart scan
     */
    private fun startAutoRestartCountdown() {
        viewModelScope.launch {
            for (i in 60 downTo 1) {
                _autoRestartCountdown.value = i
                kotlinx.coroutines.delay(1000)

                // If reconnected during countdown, cancel
                if (_eldConnectionStatus.value == ELDConnectionStatus.CONNECTED) {
                    _autoRestartCountdown.value = null
                    _showConnectionLostAlert.value = false
                    return@launch
                }
            }

            // Countdown finished - auto restart scan
            _autoRestartCountdown.value = null
            android.util.Log.d("DashboardViewModel", "🔄 Auto-restarting scan after 60 seconds")
            connectToELD()
        }
    }

    /**
     * Dismiss connection lost alert
     */
    fun dismissConnectionLostAlert() {
        _showConnectionLostAlert.value = false
    }

    fun connectToELD() {
        // CRITICAL: Check if a vehicle is selected before attempting connection
        val vehicleId = com.eld.driver.ELDDriverApplication.getCurrentVehicleId()
        if (vehicleId == null) {
            android.util.Log.e("DashboardViewModel", "❌ Cannot connect to ELD - no vehicle selected!")
            _statusChangeMessage.value = "❌ Please select a vehicle first"
            viewModelScope.launch {
                kotlinx.coroutines.delay(3000)
                _statusChangeMessage.value = null
            }
            return
        }

        if (!bleManager.isBluetoothEnabled()) {
            android.util.Log.e("DashboardViewModel", "Bluetooth not enabled - requesting enable")
            _showBluetoothEnableRequest.value = true
            return
        }
        if (!bleManager.hasRequiredPermissions()) {
            android.util.Log.e("DashboardViewModel", "Missing BLE permissions")
            return
        }

        // Check if we have an ELD MAC address for the selected vehicle
        val eldMacAddress = com.eld.driver.ELDDriverApplication.getCurrentEldMacAddress()

        if (eldMacAddress != null && eldMacAddress.isNotBlank()) {
            // Direct connect using MAC address - no scanning needed
            android.util.Log.d("DashboardViewModel", "🔌 Direct connecting to ELD MAC: $eldMacAddress (vehicleId=$vehicleId)")
            bleManager.connectToMacAddress(eldMacAddress)
        } else {
            // No MAC address configured - fall back to scanning
            android.util.Log.d("DashboardViewModel", "Starting scan for ELD (no MAC address configured, vehicleId=$vehicleId)")
            bleManager.startScan()
        }
    }

    fun disconnectFromELD() {
        android.util.Log.d("DashboardViewModel", "Disconnecting from ELD")
        bleManager.disconnect()
    }

    /**
     * Cancel ongoing reconnect attempts and go to disconnected state
     */
    fun cancelReconnect() {
        android.util.Log.d("DashboardViewModel", "Canceling reconnect attempts")
        bleManager.cancelReconnect()
    }

    /**
     * Check if Bluetooth is enabled
     */
    fun isBluetoothEnabled(): Boolean {
        return bleManager.isBluetoothEnabled()
    }

    /**
     * Check Bluetooth status and request enable if disabled.
     * Should be called when Dashboard loads.
     */
    fun checkBluetoothAndPrompt() {
        if (!bleManager.isBluetoothEnabled()) {
            android.util.Log.d("DashboardViewModel", "Bluetooth is OFF - requesting enable")
            _showBluetoothEnableRequest.value = true
        } else {
            android.util.Log.d("DashboardViewModel", "Bluetooth is ON")
            _showBluetoothEnableRequest.value = false
        }
    }

    /**
     * Dismiss the Bluetooth enable request
     */
    fun dismissBluetoothRequest() {
        _showBluetoothEnableRequest.value = false
    }

    /**
     * Purge (delete) all unidentified driving events from the ELD device.
     * Call this after the driver has claimed/reviewed the UD events.
     */
    fun purgeUnidentifiedEvents() {
        android.util.Log.d("DashboardViewModel", "📋 Purging UD events from device")
        bleManager.purgeUnidentifiedEvents()
        _unidentifiedEvents.value = emptyList()
        _unidentifiedEventsCount.value = 0
    }

    /**
     * Called when user enabled Bluetooth.
     * Automatically starts scanning for ELD device.
     */
    fun onBluetoothEnabled() {
        _showBluetoothEnableRequest.value = false
        android.util.Log.d("DashboardViewModel", "Bluetooth enabled by user - auto-starting scan")

        // Automatically start scanning for ELD after Bluetooth is enabled
        if (bleManager.hasRequiredPermissions()) {
            bleManager.startScan()
        }
    }

    /**
     * Load current duty status.
     * NOTE: This is now just a trigger - actual data comes from Flow observing local DB.
     * The initial sync populates the local DB, and Flow automatically updates UI.
     */
    fun loadCurrentDutyStatus(token: String) {
        // Flow already observes local DB and updates UI automatically.
        // This function is kept for backward compatibility but does nothing special.
        android.util.Log.d("DashboardViewModel", "loadCurrentDutyStatus called - Flow handles UI updates")
    }

    /**
     * Change duty status.
     * ALWAYS saves locally first, then queues for sync to server.
     * UI updates automatically via Flow observing local database.
     */
    fun changeDutyStatus(
        token: String,
        newStatus: DutyStatusType,
        location: String?,
        notes: String?,
        vehicleId: Int? = null,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            android.util.Log.d("DashboardViewModel", "════════════════════════════════════════════════════════")
            android.util.Log.d("DashboardViewModel", "🔄 STATUS CHANGE REQUEST (Local-First)")
            android.util.Log.d("DashboardViewModel", "   New Status: $newStatus")
            android.util.Log.d("DashboardViewModel", "════════════════════════════════════════════════════════")

            _statusChangeMessage.value = "Changing to $newStatus..."

            try {
                // Get current location from BLE device or GPS
                // Use getLocationWithFallback() which WAITS for location - essential for Huawei phones
                val currentLocation = locationService.getLocationWithFallback(timeoutMs = 5000)
                val eldData = bleManager.eldData.value

                // ALWAYS use FMCSA-compliant location format if we have coordinates
                // Format: "{X} mi. {direction} of {city}, {state}"
                // IMPORTANT: Ignore passed location parameter - it might have old format
                val locationText = if (currentLocation != null) {
                    try {
                        locationService.getFMCSALocation(currentLocation.latitude, currentLocation.longitude)
                    } catch (e: Exception) {
                        android.util.Log.w("DashboardViewModel", "FMCSA format failed: ${e.message}")
                        "${String.format("%.4f", currentLocation.latitude)}, ${String.format("%.4f", currentLocation.longitude)}"
                    }
                } else {
                    // No coordinates - use passed location only as last resort
                    location?.ifBlank { null }
                }

                // Convert odometer from kilometers to miles (ELD sends km, backend expects miles)
                val odometerMiles = eldData?.odometer?.let { it * 0.621371 }

                val request = DutyStatusChangeRequest(
                    dutyStatus = newStatus,
                    vehicleId = vehicleId,
                    deviceId = com.eld.driver.ELDDriverApplication.getCurrentDeviceId(),
                    latitude = currentLocation?.latitude,
                    longitude = currentLocation?.longitude,
                    location = locationText,
                    odometer = odometerMiles,
                    engineHours = eldData?.engineHours,
                    note = notes,
                    shippingDocumentNumber = null,
                    trailerNumber = null
                )

                // ALWAYS save locally first - this is the source of truth
                // UI updates automatically via Flow observing local DB
                val result = repository.changeDutyStatus(request)

                if (result.isSuccess) {
                    android.util.Log.d("DashboardViewModel", "✅ Saved locally, queued for sync")
                    _statusChangeMessage.value = "✅ Changed to $newStatus"
                    onSuccess()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    android.util.Log.e("DashboardViewModel", "❌ Local save failed: $error")
                    _statusChangeMessage.value = "❌ Error: $error"
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "❌ EXCEPTION:", e)
                _statusChangeMessage.value = "❌ Exception: ${e.message}"
            }

            android.util.Log.d("DashboardViewModel", "════════════════════════════════════════════════════════")

            // Clear message after 5 seconds
            kotlinx.coroutines.delay(5000)
            _statusChangeMessage.value = null
        }
    }

    fun clearStatusChangeMessage() {
        _statusChangeMessage.value = null
    }

    /**
     * Get current location for UI display
     */
    fun getCurrentLocation(): LocationData? {
        return locationService.getCurrentLocation()
    }

    /**
     * Request fresh location update
     */
    fun refreshLocation() {
        locationService.requestGpsLocation()
    }

    fun loadHOSStatus(token: String) {
        viewModelScope.launch {
            // HOS is now calculated locally from duty status events
            // The calculation happens automatically via the HOSService
            // Just trigger a recalculation to ensure fresh data
            repository.recalculateHOS()

            // The result will be emitted through the Flow we're observing in init {}
            android.util.Log.d("DashboardViewModel", "📊 HOS recalculation triggered")
        }
    }

    /**
     * Start HOS and sync services after login.
     */
    fun startServices() {
        repository.startServices()
        android.util.Log.d("DashboardViewModel", "🚀 HOS/Sync services started")
    }

    /**
     * Reset ViewModel state for new user (call on logout).
     * This ensures the next user gets fresh data.
     */
    fun resetForNewUser() {
        android.util.Log.d("DashboardViewModel", "🔄 Resetting ViewModel for new user")
        initialSyncCompleted = false
        isInitialized = false
        _isInitialLoading.value = true
        _currentDutyStatus.value = DutyStatusUiState.Loading
        _hosStatus.value = null
        _hosCalculationResult.value = null
        _pendingSyncCount.value = 0
        _eldConnectionStatus.value = ELDConnectionStatus.DISCONNECTED
        _debugLogs.value = emptyList()
    }

    /**
     * Update foreground notification with current duty status and ELD connection.
     */
    private fun updateForegroundNotification(dutyStatus: DutyStatusType) {
        val isEldConnected = _eldConnectionStatus.value == ELDConnectionStatus.CONNECTED
        ELDForegroundService.updateStatus(
            context = getApplication(),
            dutyStatus = dutyStatus,
            eldConnected = isEldConnected
        )
    }

    /**
     * Perform initial sync on login.
     * Only runs ONCE per session - subsequent calls are ignored.
     * BLOCKS loading screen until sync + HOS calculation completes.
     */
    fun performInitialSync() {
        // Only run once per session
        if (initialSyncCompleted) {
            android.util.Log.d("DashboardViewModel", "📥 Initial sync already completed, skipping")
            return
        }

        viewModelScope.launch {
            android.util.Log.d("DashboardViewModel", "📥 Starting initial sync (loading screen active)...")

            // Keep loading screen visible until sync completes
            _isInitialLoading.value = true

            val result = repository.performInitialSync()
            if (result.isSuccess) {
                android.util.Log.d("DashboardViewModel", "✅ Initial sync completed")
                // Recalculate HOS after sync completes
                repository.recalculateHOS()
                android.util.Log.d("DashboardViewModel", "📊 HOS recalculated after initial sync")

                // Load fresh HOS into state
                _hosStatus.value = repository.getHOSStatusModel()
                android.util.Log.d("DashboardViewModel", "📊 HOS loaded: ${_hosStatus.value}")
            } else {
                android.util.Log.e("DashboardViewModel", "❌ Initial sync failed: ${result.exceptionOrNull()?.message}")
            }

            initialSyncCompleted = true

            // NOW we can dismiss the loading screen - data is ready
            _isInitialLoading.value = false
            android.util.Log.d("DashboardViewModel", "✅ Initial sync + HOS complete - dismissing loading screen")
        }
    }

    /**
     * Force sync now - manual trigger for testing
     */
    suspend fun forceSyncNow() {
        android.util.Log.d("DashboardViewModel", "🔄 Force sync triggered by user")
        _statusChangeMessage.value = "Syncing..."
        _lastSyncResult.value = "Syncing at ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}..."

        try {
            val pendingBefore = repository.getPendingSyncCount()
            val count = repository.forceSyncQueue()
            val pendingAfter = repository.getPendingSyncCount()

            val resultMsg = buildString {
                appendLine("Time: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
                appendLine("Processed: $count items")
                appendLine("Before: $pendingBefore pending")
                appendLine("After: $pendingAfter pending")
                if (count == 0 && pendingBefore > 0) {
                    appendLine("⚠️ Items not synced!")
                }
            }
            _lastSyncResult.value = resultMsg

            android.util.Log.d("DashboardViewModel", "✅ Force sync completed: $count items processed")
            _statusChangeMessage.value = if (count > 0) {
                "✅ Synced $count items"
            } else if (pendingBefore > 0) {
                "⚠️ Sync failed - check debug"
            } else {
                "No pending items"
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardViewModel", "❌ Force sync failed: ${e.message}")
            _lastSyncResult.value = "ERROR: ${e.message}"
            _statusChangeMessage.value = "❌ Sync failed: ${e.message}"
        }

        kotlinx.coroutines.delay(3000)
        _statusChangeMessage.value = null
    }

    /**
     * Clear sync queue (for testing/debug)
     */
    suspend fun clearSyncQueue() {
        repository.clearSyncQueue()
        _statusChangeMessage.value = "Queue cleared"
        kotlinx.coroutines.delay(2000)
        _statusChangeMessage.value = null
    }

    fun getDurationText(startTime: String): String {
        return try {
            // Try ISO8601 format first (what backend typically sends)
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            var startDate = try {
                format.parse(startTime)
            } catch (e: Exception) {
                // Fallback to format without milliseconds
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(startTime)
            }

            if (startDate != null) {
                val now = Date()
                val diffMs = abs(now.time - startDate.time)
                val days = (diffMs / 86400000).toInt()
                val hours = ((diffMs % 86400000) / 3600000).toInt()
                val minutes = ((diffMs % 3600000) / 60000).toInt()

                println("🕐 Duration calculation: startTime=$startTime, days=$days, hours=$hours, mins=$minutes")

                when {
                    days > 0 -> "$days day${if (days > 1) "s" else ""}"
                    hours > 0 -> "$hours hour${if (hours > 1) "s" else ""}"
                    else -> "$minutes min${if (minutes > 1) "s" else ""}"
                }
            } else {
                "Unknown"
            }
        } catch (e: Exception) {
            println("❌ Failed to parse startTime: $startTime - ${e.message}")
            "Unknown"
        }
    }
}

/**
 * UI State for Current Duty Status
 */
sealed class DutyStatusUiState {
    object Loading : DutyStatusUiState()
    data class Success(val dutyStatus: DutyStatus) : DutyStatusUiState()
    data class Error(val message: String) : DutyStatusUiState()
}
