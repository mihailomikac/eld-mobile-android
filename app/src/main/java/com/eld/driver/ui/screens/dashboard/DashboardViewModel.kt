package com.eld.driver.ui.screens.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eld.driver.ble.GeometrisWQManager
import com.eld.driver.ble.models.BleConnectionState
import com.eld.driver.data.api.ApiService
import com.eld.driver.data.models.*
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
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val request = DutyStatusChangeRequest(
                    dutyStatus = newStatus,
                    vehicleId = null, // TODO: Get from selected vehicle
                    deviceId = null,
                    latitude = null,
                    longitude = null,
                    location = location,
                    odometer = null,
                    engineHours = null,
                    note = notes,
                    shippingDocumentNumber = null,
                    trailerNumber = null
                )

                val response = apiService.changeDutyStatus(token, request)

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.success == true && apiResponse.data != null) {
                        _currentDutyStatus.value = DutyStatusUiState.Success(apiResponse.data)
                        println("✅ Changed duty status to: ${apiResponse.data.dutyStatus}")
                        onSuccess()
                    } else {
                        val error = apiResponse?.error ?: "Failed to change duty status"
                        println("❌ Change duty status failed: $error")
                    }
                } else {
                    val error = "Failed to change duty status: ${response.code()} ${response.message()}"
                    println("❌ $error")
                }
            } catch (e: Exception) {
                println("❌ Network error: ${e.message}")
                e.printStackTrace()
            }
        }
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
