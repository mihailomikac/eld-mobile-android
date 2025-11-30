package com.eld.driver.ui.screens.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eld.driver.ble.GeometrisWQManager
import com.eld.driver.ble.VehicleMotionState
import com.eld.driver.ble.models.BleConnectionState
import com.eld.driver.data.api.ApiService
import com.eld.driver.data.models.*
import com.eld.driver.location.LocationService
import com.eld.driver.location.LocationData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * DashboardViewModel - Handles dashboard data and state + BLE integration
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val apiService = ApiService.getInstance()

    // BLE Manager for ELD device (singleton - shared across all screens)
    private val bleManager = GeometrisWQManager.getInstance(application)

    // Location Service for GPS + BLE location
    private val locationService = LocationService.getInstance(application)

    // TODO: Change hardcoded ELD serial to real device serial number
    private val hardcodedEldSerial = "87A4141310908"

    private val _currentDutyStatus = MutableStateFlow<DutyStatusUiState>(DutyStatusUiState.Loading)
    val currentDutyStatus: StateFlow<DutyStatusUiState> = _currentDutyStatus.asStateFlow()

    private val _hosStatus = MutableStateFlow<HOSStatus?>(null)
    val hosStatus: StateFlow<HOSStatus?> = _hosStatus.asStateFlow()

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

    // Status change error/success message for UI display
    private val _statusChangeMessage = MutableStateFlow<String?>(null)
    val statusChangeMessage: StateFlow<String?> = _statusChangeMessage.asStateFlow()

    // Stationary delay dialog (60 sec countdown after 5 min idle)
    private val _showStationaryDelayDialog = MutableStateFlow(false)
    val showStationaryDelayDialog: StateFlow<Boolean> = _showStationaryDelayDialog.asStateFlow()

    private val _stationaryDelayCountdown = MutableStateFlow<Int?>(null)
    val stationaryDelayCountdown: StateFlow<Int?> = _stationaryDelayCountdown.asStateFlow()

    init {
        // Periodically sync BLE manager logs to UI
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000) // Update every second
                val bleManagerLogs = bleManager.getDebugLogs()
                if (bleManagerLogs.isNotEmpty()) {
                    _debugLogs.value = bleManagerLogs
                }
            }
        }

        // Periodically refresh current duty status to catch automatic changes
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5000) // Refresh every 5 seconds
                val token = com.eld.driver.ELDDriverApplication.getAuthToken()
                if (token != null && _eldConnectionStatus.value == ELDConnectionStatus.CONNECTED) {
                    // Silently refresh status without showing loading state
                    try {
                        val response = apiService.getCurrentDutyStatus(token)
                        if (response.isSuccessful && response.body()?.success == true && response.body()?.data != null) {
                            _currentDutyStatus.value = DutyStatusUiState.Success(response.body()!!.data!!)
                        }
                    } catch (e: Exception) {
                        // Silently fail - don't disrupt UI
                    }
                }
            }
        }

        // Monitor BLE connection state
        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                _eldConnectionStatus.value = when (state) {
                    is BleConnectionState.Disconnected -> ELDConnectionStatus.DISCONNECTED
                    is BleConnectionState.Scanning -> ELDConnectionStatus.PAIRING
                    is BleConnectionState.Connecting -> ELDConnectionStatus.PAIRING
                    is BleConnectionState.Connected -> ELDConnectionStatus.CONNECTED
                    is BleConnectionState.Ready -> ELDConnectionStatus.CONNECTED
                    else -> ELDConnectionStatus.DISCONNECTED
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
        if (!bleManager.isBluetoothEnabled()) {
            android.util.Log.e("DashboardViewModel", "Bluetooth not enabled")
            return
        }
        if (!bleManager.hasRequiredPermissions()) {
            android.util.Log.e("DashboardViewModel", "Missing BLE permissions")
            return
        }

        android.util.Log.d("DashboardViewModel", "Starting scan for ELD")
        bleManager.startScan()
    }

    fun disconnectFromELD() {
        android.util.Log.d("DashboardViewModel", "Disconnecting from ELD")
        bleManager.disconnect()
    }

    fun loadCurrentDutyStatus(token: String) {
        viewModelScope.launch {
            _currentDutyStatus.value = DutyStatusUiState.Loading

            try {
                val response = apiService.getCurrentDutyStatus(token)

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.success == true && apiResponse.data != null) {
                        _currentDutyStatus.value = DutyStatusUiState.Success(apiResponse.data)
                        println("✅ Loaded current duty status: ${apiResponse.data.dutyStatus}")
                    } else {
                        val error = apiResponse?.error ?: "Failed to load duty status"
                        _currentDutyStatus.value = DutyStatusUiState.Error(error)
                        println("❌ Load duty status failed: $error")
                    }
                } else {
                    val error = "Failed to load duty status: ${response.code()} ${response.message()}"
                    _currentDutyStatus.value = DutyStatusUiState.Error(error)
                    println("❌ $error")
                }
            } catch (e: Exception) {
                val error = "Network error: ${e.message}"
                _currentDutyStatus.value = DutyStatusUiState.Error(error)
                println("❌ $error")
                e.printStackTrace()
            }
        }
    }

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
            android.util.Log.d("DashboardViewModel", "🔄 MANUAL STATUS CHANGE REQUEST")
            android.util.Log.d("DashboardViewModel", "   New Status: $newStatus")
            android.util.Log.d("DashboardViewModel", "   Location: $location")
            android.util.Log.d("DashboardViewModel", "   Notes: $notes")
            android.util.Log.d("DashboardViewModel", "   Vehicle ID: $vehicleId")
            android.util.Log.d("DashboardViewModel", "   Token: ${token.take(20)}...")
            android.util.Log.d("DashboardViewModel", "════════════════════════════════════════════════════════")

            _statusChangeMessage.value = "Changing to $newStatus..."

            try {
                // Get current location from BLE device or GPS
                val currentLocation = locationService.getCurrentLocation()
                val eldData = bleManager.eldData.value

                android.util.Log.d("DashboardViewModel", "📍 Location data:")
                android.util.Log.d("DashboardViewModel", "   GPS Lat: ${currentLocation?.latitude}")
                android.util.Log.d("DashboardViewModel", "   GPS Lon: ${currentLocation?.longitude}")
                android.util.Log.d("DashboardViewModel", "   Address: ${currentLocation?.address}")
                android.util.Log.d("DashboardViewModel", "📊 ELD data:")
                android.util.Log.d("DashboardViewModel", "   Odometer: ${eldData?.odometer}")
                android.util.Log.d("DashboardViewModel", "   Engine Hours: ${eldData?.engineHours}")

                // Use provided location text or get from coordinates
                val locationText = location?.ifBlank { null }
                    ?: currentLocation?.address

                val request = DutyStatusChangeRequest(
                    dutyStatus = newStatus,
                    vehicleId = vehicleId,
                    deviceId = null,
                    latitude = currentLocation?.latitude,
                    longitude = currentLocation?.longitude,
                    location = locationText,
                    odometer = eldData?.odometer,
                    engineHours = eldData?.engineHours,
                    note = notes,
                    shippingDocumentNumber = null,
                    trailerNumber = null
                )

                android.util.Log.d("DashboardViewModel", "📤 Sending request:")
                android.util.Log.d("DashboardViewModel", "   $request")

                val response = apiService.changeDutyStatus(token, request)

                android.util.Log.d("DashboardViewModel", "📥 Response received:")
                android.util.Log.d("DashboardViewModel", "   HTTP Code: ${response.code()}")
                android.util.Log.d("DashboardViewModel", "   Is Successful: ${response.isSuccessful}")

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    android.util.Log.d("DashboardViewModel", "   Body: $apiResponse")

                    if (apiResponse?.success == true && apiResponse.data != null) {
                        _currentDutyStatus.value = DutyStatusUiState.Success(apiResponse.data)
                        android.util.Log.d("DashboardViewModel", "✅ SUCCESS! Changed to: ${apiResponse.data.dutyStatus}")
                        _statusChangeMessage.value = "✅ Changed to ${apiResponse.data.dutyStatus}"
                        onSuccess()
                    } else {
                        val error = apiResponse?.error ?: "Unknown error - success=false"
                        android.util.Log.e("DashboardViewModel", "❌ API returned error: $error")
                        _statusChangeMessage.value = "❌ Error: $error"
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    android.util.Log.e("DashboardViewModel", "❌ HTTP Error: ${response.code()} ${response.message()}")
                    android.util.Log.e("DashboardViewModel", "   Error body: $errorBody")
                    _statusChangeMessage.value = "❌ HTTP ${response.code()}: $errorBody"
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
        // TODO: When HOS status endpoint is available, implement this
        // For now, use mock data
        _hosStatus.value = HOSStatus(
            breakTimeRemaining = 480,      // 08:00 remaining
            driveTimeRemaining = 660,      // 11:00 remaining
            shiftTimeRemaining = 840,      // 14:00 remaining
            cycleTimeRemaining = 4200,     // 70:00 remaining
            breakTimeUsed = 0,
            driveTimeUsed = 0,
            shiftTimeUsed = 0,
            cycleTimeUsed = 0,
            breakTimeTotal = 480,          // 08:00 total
            driveTimeTotal = 660,          // 11:00 total
            shiftTimeTotal = 840,          // 14:00 total
            cycleTimeTotal = 4200          // 70:00 total
        )
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
