package com.eld.driver.ui.screens.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eld.driver.data.api.ApiService
import com.eld.driver.data.models.MobileVehicleListData
import com.eld.driver.data.models.Vehicle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * VehicleViewModel - Handles vehicle selection and confirmation
 */
class VehicleViewModel : ViewModel() {
    private val apiService = ApiService.getInstance()

    private val _uiState = MutableStateFlow<VehicleUiState>(VehicleUiState.Loading)
    val uiState: StateFlow<VehicleUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _allVehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    private val _filteredVehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val filteredVehicles: StateFlow<List<Vehicle>> = _filteredVehicles.asStateFlow()

    private val _currentVehicleId = MutableStateFlow<Int?>(null)
    val currentVehicleId: StateFlow<Int?> = _currentVehicleId.asStateFlow()

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
        // Just set the selected vehicle locally (no API call needed - matches iOS behavior)
        _currentVehicleId.value = vehicleId
        println("✅ Vehicle selected: ID $vehicleId")
        onSuccess()
    }

    fun clearError() {
        if (_uiState.value is VehicleUiState.Error) {
            _uiState.value = VehicleUiState.Success
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
