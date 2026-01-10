package com.eld.driver.ui.screens.dvir

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.eld.driver.ble.GeometrisWQManager
import com.eld.driver.ble.models.BleConnectionState
import com.eld.driver.data.api.ApiErrorParser
import com.eld.driver.data.api.ApiService
import com.eld.driver.data.local.TokenManager
import com.eld.driver.data.models.*
import com.eld.driver.location.LocationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for DVIR screens
 */
class DVIRViewModel(application: Application) : AndroidViewModel(application) {
    private val apiService = ApiService.getInstance()
    private val locationService = LocationService.getInstance(application)
    private val tokenManager = TokenManager.getInstance(application)
    private val bleManager = GeometrisWQManager.getInstance(application)

    // Current vehicle info
    private val _currentVehicle = MutableStateFlow<Vehicle?>(null)
    val currentVehicle: StateFlow<Vehicle?> = _currentVehicle.asStateFlow()

    // Vehicle defects from API
    private val _vehicleDefectsForm = MutableStateFlow<VehicleDefectsFormState>(VehicleDefectsFormState.Idle)
    val vehicleDefectsForm: StateFlow<VehicleDefectsFormState> = _vehicleDefectsForm.asStateFlow()

    // Asset defects from API
    private val _assetDefectsForm = MutableStateFlow<AssetDefectsFormState>(AssetDefectsFormState.Idle)
    val assetDefectsForm: StateFlow<AssetDefectsFormState> = _assetDefectsForm.asStateFlow()

    // Selected defects
    private val _selectedVehicleDefects = MutableStateFlow<List<VehicleDefectItem>>(emptyList())
    val selectedVehicleDefects: StateFlow<List<VehicleDefectItem>> = _selectedVehicleDefects.asStateFlow()

    private val _selectedAssetDefects = MutableStateFlow<List<AssetDefectItem>>(emptyList())
    val selectedAssetDefects: StateFlow<List<AssetDefectItem>> = _selectedAssetDefects.asStateFlow()

    // Create inspection state
    private val _createInspectionState = MutableStateFlow<CreateInspectionState>(CreateInspectionState.Idle)
    val createInspectionState: StateFlow<CreateInspectionState> = _createInspectionState.asStateFlow()

    // Current location
    private val _currentLocation = MutableStateFlow<String?>(null)
    val currentLocation: StateFlow<String?> = _currentLocation.asStateFlow()

    // Current odometer from ELD (in miles)
    private val _currentOdometer = MutableStateFlow<Double?>(null)
    val currentOdometer: StateFlow<Double?> = _currentOdometer.asStateFlow()

    // ELD connection state
    val eldConnectionState: StateFlow<BleConnectionState> = bleManager.connectionState

    // ELD data (for observing odometer changes)
    val eldData = bleManager.eldData

    // Company timezone for display
    fun getCompanyTimeZone(): java.util.TimeZone = tokenManager.getCompanyTimeZone()

    // Today's inspections
    private val _todayInspections = MutableStateFlow<List<InspectionListItem>>(emptyList())
    val todayInspections: StateFlow<List<InspectionListItem>> = _todayInspections.asStateFlow()

    private val _inspectionsLoading = MutableStateFlow(false)
    val inspectionsLoading: StateFlow<Boolean> = _inspectionsLoading.asStateFlow()

    fun setCurrentVehicle(vehicle: Vehicle?) {
        _currentVehicle.value = vehicle
    }

    /**
     * Load vehicle defects form from API
     */
    fun loadVehicleDefects(token: String, vehicleId: Int) {
        viewModelScope.launch {
            _vehicleDefectsForm.value = VehicleDefectsFormState.Loading
            try {
                val response = apiService.getVehicleDefects(token, vehicleId)
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    if (data != null) {
                        _vehicleDefectsForm.value = VehicleDefectsFormState.Success(data)
                    } else {
                        _vehicleDefectsForm.value = VehicleDefectsFormState.Error("No defects form found")
                    }
                } else {
                    _vehicleDefectsForm.value = VehicleDefectsFormState.Error(
                        response.body()?.error ?: "Failed to load vehicle defects"
                    )
                }
            } catch (e: Exception) {
                _vehicleDefectsForm.value = VehicleDefectsFormState.Error(e.message ?: "Network error")
            }
        }
    }

    /**
     * Load asset defects form from API
     */
    fun loadAssetDefects(token: String, assetId: Int) {
        viewModelScope.launch {
            _assetDefectsForm.value = AssetDefectsFormState.Loading
            try {
                val response = apiService.getAssetDefects(token, assetId)
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    if (data != null) {
                        _assetDefectsForm.value = AssetDefectsFormState.Success(data)
                    } else {
                        _assetDefectsForm.value = AssetDefectsFormState.Error("No defects form found")
                    }
                } else {
                    _assetDefectsForm.value = AssetDefectsFormState.Error(
                        response.body()?.error ?: "Failed to load asset defects"
                    )
                }
            } catch (e: Exception) {
                _assetDefectsForm.value = AssetDefectsFormState.Error(e.message ?: "Network error")
            }
        }
    }

    /**
     * Update selected vehicle defects
     */
    fun updateSelectedVehicleDefects(defects: List<VehicleDefectItem>) {
        _selectedVehicleDefects.value = defects
    }

    /**
     * Update selected asset defects
     */
    fun updateSelectedAssetDefects(defects: List<AssetDefectItem>) {
        _selectedAssetDefects.value = defects
    }

    /**
     * Get current location.
     * Uses getLocationWithFallback() which WAITS for location - essential for Huawei phones.
     */
    fun refreshLocation() {
        viewModelScope.launch {
            val location = locationService.getLocationWithFallback(timeoutMs = 5000)
            _currentLocation.value = location?.address
        }
    }

    /**
     * Get current odometer from ELD device.
     * Only works if ELD is connected and has valid data.
     * Returns odometer in miles (converted from km).
     */
    fun refreshOdometer() {
        val connectionState = bleManager.connectionState.value
        if (connectionState is BleConnectionState.Ready) {
            val eldData = bleManager.eldData.value
            val odometerKm = eldData?.odometer
            if (odometerKm != null && odometerKm > 0) {
                // Convert km to miles (1 km = 0.621371 miles)
                val odometerMiles = odometerKm * 0.621371
                _currentOdometer.value = odometerMiles
                Log.d("DVIRViewModel", "Odometer from ELD: $odometerKm km = $odometerMiles mi")
            } else {
                Log.d("DVIRViewModel", "No valid odometer from ELD (value: $odometerKm)")
                _currentOdometer.value = null
            }
        } else {
            Log.d("DVIRViewModel", "ELD not connected, cannot get odometer (state: $connectionState)")
            _currentOdometer.value = null
        }
    }

    /**
     * Check if ELD is connected and ready
     */
    fun isEldConnected(): Boolean {
        return bleManager.connectionState.value is BleConnectionState.Ready
    }

    /**
     * Get company timezone ZoneId for displaying times
     */
    fun getCompanyZoneId(): java.time.ZoneId {
        return tokenManager.getCompanyTimeZone().toZoneId()
    }

    /**
     * Load today's inspections from API
     */
    fun loadTodayInspections(token: String, vehicleId: Int?) {
        viewModelScope.launch {
            _inspectionsLoading.value = true
            try {
                val response = apiService.getInspections(
                    token = token,
                    vehicleId = vehicleId,
                    inspectionType = null,
                    pageNumber = 1,
                    pageSize = 10
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val paginatedData = response.body()?.data
                    // Filter for today's inspections using company timezone
                    val companyZoneId = tokenManager.getCompanyTimeZone().toZoneId()
                    val today = java.time.LocalDate.now(companyZoneId)
                    Log.d("DVIRViewModel", "Using company timezone: $companyZoneId, today=$today")

                    val todayItems = paginatedData?.data?.filter { item ->
                        try {
                            // Handle both formats: with Z and without Z
                            val timeStr = item.inspectionTime
                            val inspectionDate = if (timeStr.endsWith("Z")) {
                                java.time.Instant.parse(timeStr)
                                    .atZone(companyZoneId)
                                    .toLocalDate()
                            } else {
                                // Parse as LocalDateTime (server sends in UTC)
                                java.time.LocalDateTime.parse(timeStr)
                                    .atZone(java.time.ZoneId.of("UTC"))
                                    .withZoneSameInstant(companyZoneId)
                                    .toLocalDate()
                            }
                            inspectionDate == today
                        } catch (e: Exception) {
                            Log.w("DVIRViewModel", "Failed to parse inspection time: ${item.inspectionTime}", e)
                            false
                        }
                    } ?: emptyList()
                    _todayInspections.value = todayItems
                    Log.d("DVIRViewModel", "Loaded ${todayItems.size} inspections for today")
                } else {
                    Log.w("DVIRViewModel", "Failed to load inspections: ${response.body()?.error}")
                    _todayInspections.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e("DVIRViewModel", "Error loading inspections", e)
                _todayInspections.value = emptyList()
            } finally {
                _inspectionsLoading.value = false
            }
        }
    }

    /**
     * Create inspection
     * Location is always in FMCSA format: "{X} mi. {direction} of {city}, {state}"
     */
    fun createInspection(
        token: String,
        vehicleId: Int,
        inspectionType: InspectionType,
        odometer: Double?,
        location: String?,
        notes: String?,
        onSuccess: (Inspection) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _createInspectionState.value = CreateInspectionState.Loading
            try {
                val currentLoc = locationService.getCurrentLocation()

                // Use FMCSA formatted location if available
                val fmcsaLocation = if (currentLoc != null) {
                    locationService.getFMCSALocation(currentLoc.latitude, currentLoc.longitude)
                } else null

                val request = InspectionCreateRequest(
                    vehicleId = vehicleId,
                    inspectionType = inspectionType,
                    inspectionTime = null, // Server will use current time
                    vehicleDefects = _selectedVehicleDefects.value.ifEmpty { null },
                    assetDefects = _selectedAssetDefects.value.ifEmpty { null },
                    notes = notes?.ifBlank { null },
                    odometerReading = odometer,
                    latitude = currentLoc?.latitude,
                    longitude = currentLoc?.longitude,
                    locationDescription = location ?: fmcsaLocation
                )

                val response = apiService.createInspection(token, request)
                if (response.isSuccessful && response.body()?.success == true) {
                    val inspection = response.body()?.data
                    if (inspection != null) {
                        _createInspectionState.value = CreateInspectionState.Success(inspection)
                        // Clear selected defects
                        _selectedVehicleDefects.value = emptyList()
                        _selectedAssetDefects.value = emptyList()
                        onSuccess(inspection)
                    } else {
                        val error = "Failed to create inspection"
                        _createInspectionState.value = CreateInspectionState.Error(error)
                        onError(error)
                    }
                } else {
                    // Try to parse validation errors from error body
                    val error = ApiErrorParser.parse(response.errorBody()?.string(), response.body()?.error)
                    Log.e("DVIRViewModel", "Create inspection failed: $error")
                    _createInspectionState.value = CreateInspectionState.Error(error)
                    onError(error)
                }
            } catch (e: Exception) {
                val error = e.message ?: "Network error"
                Log.e("DVIRViewModel", "Create inspection exception: $error", e)
                _createInspectionState.value = CreateInspectionState.Error(error)
                onError(error)
            }
        }
    }

    /**
     * Reset state
     */
    fun reset() {
        _selectedVehicleDefects.value = emptyList()
        _selectedAssetDefects.value = emptyList()
        _createInspectionState.value = CreateInspectionState.Idle
    }
}

sealed class VehicleDefectsFormState {
    object Idle : VehicleDefectsFormState()
    object Loading : VehicleDefectsFormState()
    data class Success(val data: VehicleDefectsFormResponse) : VehicleDefectsFormState()
    data class Error(val message: String) : VehicleDefectsFormState()
}

sealed class AssetDefectsFormState {
    object Idle : AssetDefectsFormState()
    object Loading : AssetDefectsFormState()
    data class Success(val data: AssetDefectsFormResponse) : AssetDefectsFormState()
    data class Error(val message: String) : AssetDefectsFormState()
}

sealed class CreateInspectionState {
    object Idle : CreateInspectionState()
    object Loading : CreateInspectionState()
    data class Success(val inspection: Inspection) : CreateInspectionState()
    data class Error(val message: String) : CreateInspectionState()
}
