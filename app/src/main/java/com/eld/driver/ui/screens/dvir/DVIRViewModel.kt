package com.eld.driver.ui.screens.dvir

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eld.driver.data.api.ApiService
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
     * Get current location
     */
    fun refreshLocation() {
        val location = locationService.getCurrentLocation()
        _currentLocation.value = location?.address
    }

    /**
     * Create inspection
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
                    locationDescription = location ?: currentLoc?.address
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
                    val error = response.body()?.error ?: "Failed to create inspection"
                    _createInspectionState.value = CreateInspectionState.Error(error)
                    onError(error)
                }
            } catch (e: Exception) {
                val error = e.message ?: "Network error"
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
