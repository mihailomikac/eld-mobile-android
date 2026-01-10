package com.eld.driver.ui.screens.vehicle

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eld.driver.ELDDriverApplication
import com.eld.driver.ble.GeometrisWQManager
import com.eld.driver.data.api.ApiService
import com.eld.driver.data.models.FmcsaEventRecordOrigin
import com.eld.driver.data.models.MobileVehicleListData
import com.eld.driver.data.models.TickEventRequest
import com.eld.driver.data.models.TickEventType
import com.eld.driver.data.models.Vehicle
import com.eld.driver.location.LocationService
import com.eld.driver.service.ELDForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * VehicleViewModel - Handles vehicle selection and confirmation
 */
class VehicleViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "VehicleViewModel"
    }

    private val apiService = ApiService.getInstance()
    private val locationService = LocationService.getInstance(application)
    private val bleManager = GeometrisWQManager.getInstance(application)

    private val _uiState = MutableStateFlow<VehicleUiState>(VehicleUiState.Loading)
    val uiState: StateFlow<VehicleUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _allVehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    private val _filteredVehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val filteredVehicles: StateFlow<List<Vehicle>> = _filteredVehicles.asStateFlow()

    private val _currentVehicleId = MutableStateFlow<Int?>(null)
    val currentVehicleId: StateFlow<Int?> = _currentVehicleId.asStateFlow()

    // Currently selected vehicle object
    val selectedVehicle: StateFlow<Vehicle?> = combine(
        _currentVehicleId,
        _allVehicles
    ) { vehicleId, vehicles ->
        vehicleId?.let { id -> vehicles.find { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun loadVehicles(token: String) {
        viewModelScope.launch {
            _uiState.value = VehicleUiState.Loading

            try {
                val response = apiService.getVehicles(token)

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.success == true && apiResponse.data != null) {
                        val mobileVehicleData = apiResponse.data
                        _allVehicles.value = mobileVehicleData.vehicles
                        _filteredVehicles.value = mobileVehicleData.vehicles
                        _currentVehicleId.value = mobileVehicleData.currentVehicleId
                        _uiState.value = VehicleUiState.Success
                        println("✅ Loaded ${mobileVehicleData.vehicles.size} vehicles")
                    } else {
                        val error = apiResponse?.error ?: "Failed to load vehicles"
                        _uiState.value = VehicleUiState.Error(error)
                        println("❌ Load vehicles failed: $error")
                    }
                } else {
                    val error = "Failed to load vehicles: ${response.code()} ${response.message()}"
                    _uiState.value = VehicleUiState.Error(error)
                    println("❌ $error")
                }
            } catch (e: Exception) {
                val error = "Network error: ${e.message}"
                _uiState.value = VehicleUiState.Error(error)
                println("❌ $error")
                e.printStackTrace()
            }
        }
    }

    fun searchVehicles(query: String) {
        _searchQuery.value = query

        if (query.isBlank()) {
            _filteredVehicles.value = _allVehicles.value
        } else {
            _filteredVehicles.value = _allVehicles.value.filter { vehicle ->
                vehicle.vehicleNumber.contains(query, ignoreCase = true) ||
                vehicle.displayName.contains(query, ignoreCase = true) ||
                vehicle.vin?.contains(query, ignoreCase = true) == true
            }
        }
    }

    fun selectVehicle(vehicleId: Int, onSuccess: () -> Unit) {
        // Find vehicle to get its ELD MAC address and device ID
        val vehicle = _allVehicles.value.find { it.id == vehicleId }
        val eldMacAddress = vehicle?.eldMacAddress
        val deviceId = vehicle?.deviceId

        // Set vehicle ID, ELD MAC address, and device ID in global state
        _currentVehicleId.value = vehicleId
        com.eld.driver.ELDDriverApplication.setCurrentVehicle(vehicleId, eldMacAddress, deviceId)

        Log.d(TAG, "✅ Vehicle selected: ID $vehicleId, ELD MAC: $eldMacAddress, Device ID: $deviceId")
        onSuccess()
    }

    /**
     * Confirm vehicle selection and send LOGIN tick event.
     * Called when user clicks "Accept" on VehicleConfirmationScreen.
     * This completes the login flow and navigates to dashboard.
     */
    fun confirmVehicleSelection(authToken: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "📤 Sending LOGIN tick event after vehicle confirmation...")

                // Get current location and telemetry
                val currentLocation = locationService.getCurrentLocation()
                val fmcsaLocation = if (currentLocation != null) {
                    locationService.getFMCSALocation(currentLocation.latitude, currentLocation.longitude)
                } else null
                val eldData = bleManager.eldData.value
                val odometerMiles = eldData?.odometer?.let { it * 0.621371 }

                // Create ISO 8601 timestamp
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                isoFormat.timeZone = TimeZone.getTimeZone("UTC")
                val timestamp = isoFormat.format(Date())

                val request = TickEventRequest(
                    eventType = TickEventType.LOGIN,
                    vehicleId = ELDDriverApplication.getCurrentVehicleId(),
                    deviceId = ELDDriverApplication.getCurrentDeviceId(),
                    latitude = currentLocation?.latitude,
                    longitude = currentLocation?.longitude,
                    location = fmcsaLocation,
                    odometer = odometerMiles,
                    engineHours = eldData?.engineHours,
                    note = "Driver logged in",
                    timestamp = timestamp,
                    eventRecordOrigin = FmcsaEventRecordOrigin.DRIVER
                )
                val response = apiService.createTickEvent(authToken, request)
                if (response.isSuccessful) {
                    Log.d(TAG, "✅ LOGIN tick event sent successfully with vehicleId=${_currentVehicleId.value}")
                } else {
                    Log.w(TAG, "⚠️ LOGIN tick event failed: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Failed to send LOGIN tick event: ${e.message}", e)
            }

            // Start foreground service to keep app running in background
            val vehicle = _allVehicles.value.find { it.id == _currentVehicleId.value }
            ELDForegroundService.start(getApplication())
            ELDForegroundService.updateStatus(
                context = getApplication(),
                dutyStatus = null, // Will be updated by DashboardViewModel
                eldConnected = false,
                vehicleName = vehicle?.displayName
            )
            Log.d(TAG, "🚀 Foreground service started after vehicle confirmation")

            // Always navigate to dashboard after attempting to send tick event
            onComplete()
        }
    }

    fun clearError() {
        if (_uiState.value is VehicleUiState.Error) {
            _uiState.value = VehicleUiState.Success
        }
    }

    /**
     * Reset ViewModel state for new user (call on logout).
     */
    fun resetForNewUser() {
        _uiState.value = VehicleUiState.Loading
        _searchQuery.value = ""
        _allVehicles.value = emptyList()
        _filteredVehicles.value = emptyList()
        _currentVehicleId.value = null
    }

    /**
     * Restore vehicle state on session restore.
     * Loads vehicles from API and sets the current vehicle ID from storage.
     */
    fun restoreVehicleSession(token: String) {
        val storedVehicleId = ELDDriverApplication.getCurrentVehicleId()
        Log.d(TAG, "🔄 Restoring vehicle session, storedVehicleId=$storedVehicleId")

        if (storedVehicleId != null) {
            _currentVehicleId.value = storedVehicleId
        }

        // Load vehicles from API so selectedVehicle can find the vehicle object
        viewModelScope.launch {
            try {
                val response = apiService.getVehicles(token)
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.success == true && apiResponse.data != null) {
                        _allVehicles.value = apiResponse.data.vehicles
                        _filteredVehicles.value = apiResponse.data.vehicles
                        _uiState.value = VehicleUiState.Success
                        Log.d(TAG, "✅ Vehicle session restored: ${apiResponse.data.vehicles.size} vehicles loaded, current=$storedVehicleId")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Failed to load vehicles during session restore: ${e.message}")
            }
        }
    }
}

/**
 * UI State for Vehicle Screen
 */
sealed class VehicleUiState {
    object Loading : VehicleUiState()
    object Success : VehicleUiState()
    data class Error(val message: String) : VehicleUiState()
}
