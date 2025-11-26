package com.eld.driver.ui.screens.inspection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eld.driver.data.api.ApiService
import com.eld.driver.data.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * InspectionViewModel - Handles DVIR inspections
 */
class InspectionViewModel : ViewModel() {
    private val apiService = ApiService.getInstance()

    private val _inspectionsState = MutableStateFlow<InspectionsUiState>(InspectionsUiState.Loading)
    val inspectionsState: StateFlow<InspectionsUiState> = _inspectionsState.asStateFlow()

    private val _inspectionDetailState = MutableStateFlow<InspectionDetailUiState>(InspectionDetailUiState.Loading)
    val inspectionDetailState: StateFlow<InspectionDetailUiState> = _inspectionDetailState.asStateFlow()

    private val _createInspectionState = MutableStateFlow<CreateInspectionUiState>(CreateInspectionUiState.Initial)
    val createInspectionState: StateFlow<CreateInspectionUiState> = _createInspectionState.asStateFlow()

    private val _vehicleDefectsForm = MutableStateFlow<VehicleDefectsFormResponse?>(null)
    val vehicleDefectsForm: StateFlow<VehicleDefectsFormResponse?> = _vehicleDefectsForm.asStateFlow()

    private val _assetDefectsForm = MutableStateFlow<AssetDefectsFormResponse?>(null)
    val assetDefectsForm: StateFlow<AssetDefectsFormResponse?> = _assetDefectsForm.asStateFlow()

    // Convenience properties for defect options as simple lists
    private val _vehicleDefectsState = MutableStateFlow<List<String>>(emptyList())
    val vehicleDefectsState: StateFlow<List<String>> = _vehicleDefectsState.asStateFlow()

    private val _assetDefectsState = MutableStateFlow<List<String>>(emptyList())
    val assetDefectsState: StateFlow<List<String>> = _assetDefectsState.asStateFlow()

    private val _selectedVehicleDefects = MutableStateFlow<List<VehicleDefectItem>>(emptyList())
    val selectedVehicleDefects: StateFlow<List<VehicleDefectItem>> = _selectedVehicleDefects.asStateFlow()

    private val _selectedAssetDefects = MutableStateFlow<List<AssetDefectItem>>(emptyList())
    val selectedAssetDefects: StateFlow<List<AssetDefectItem>> = _selectedAssetDefects.asStateFlow()

    fun loadInspections(
        token: String,
        vehicleId: Int? = null,
        inspectionType: InspectionType? = null,
        pageNumber: Int = 1,
        pageSize: Int = 20
    ) {
        viewModelScope.launch {
            _inspectionsState.value = InspectionsUiState.Loading

            try {
                val response = apiService.getInspections(
                    token = token,
                    vehicleId = vehicleId,
                    inspectionType = inspectionType,
                    pageNumber = pageNumber,
                    pageSize = pageSize
                )

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.success == true && apiResponse.data != null) {
                        _inspectionsState.value = InspectionsUiState.Success(apiResponse.data)
                        println("✅ Loaded ${apiResponse.data.data.size} inspections")
                    } else {
                        val error = apiResponse?.error ?: "Failed to load inspections"
                        _inspectionsState.value = InspectionsUiState.Error(error)
                        println("❌ Load inspections failed: $error")
                    }
                } else {
                    val error = "Failed to load inspections: ${response.code()} ${response.message()}"
                    _inspectionsState.value = InspectionsUiState.Error(error)
                    println("❌ $error")
                }
            } catch (e: Exception) {
                val error = "Network error: ${e.message}"
                _inspectionsState.value = InspectionsUiState.Error(error)
                println("❌ $error")
                e.printStackTrace()
            }
        }
    }

    fun loadInspectionDetail(token: String, inspectionId: Int) {
        viewModelScope.launch {
            _inspectionDetailState.value = InspectionDetailUiState.Loading

            try {
                val response = apiService.getInspectionById(token, inspectionId)

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.success == true && apiResponse.data != null) {
                        _inspectionDetailState.value = InspectionDetailUiState.Success(apiResponse.data)
                        println("✅ Loaded inspection detail: ${apiResponse.data.id}")
                    } else {
                        val error = apiResponse?.error ?: "Failed to load inspection"
                        _inspectionDetailState.value = InspectionDetailUiState.Error(error)
                        println("❌ Load inspection failed: $error")
                    }
                } else {
                    val error = "Failed to load inspection: ${response.code()} ${response.message()}"
                    _inspectionDetailState.value = InspectionDetailUiState.Error(error)
                    println("❌ $error")
                }
            } catch (e: Exception) {
                val error = "Network error: ${e.message}"
                _inspectionDetailState.value = InspectionDetailUiState.Error(error)
                println("❌ $error")
                e.printStackTrace()
            }
        }
    }

    /**
     * Convenience method to load vehicle defects as simple list
     */
    fun loadVehicleDefects(token: String, vehicleId: Int) {
        viewModelScope.launch {
            try {
                val vehicleResponse = apiService.getVehicleDefects(token, vehicleId)
                if (vehicleResponse.isSuccessful) {
                    val apiResponse = vehicleResponse.body()
                    if (apiResponse?.success == true && apiResponse.data != null) {
                        _vehicleDefectsForm.value = apiResponse.data
                        // Extract defect options as simple list
                        _vehicleDefectsState.value = apiResponse.data.allowedVehicleDefects
                        println("✅ Loaded ${apiResponse.data.allowedVehicleDefects.size} vehicle defect options")
                    }
                }
            } catch (e: Exception) {
                println("⚠️ Failed to load vehicle defects: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Convenience method to load asset defects as simple list
     */
    fun loadAssetDefects(token: String, assetId: Int) {
        viewModelScope.launch {
            try {
                val assetResponse = apiService.getAssetDefects(token, assetId)
                if (assetResponse.isSuccessful) {
                    val apiResponse = assetResponse.body()
                    if (apiResponse?.success == true && apiResponse.data != null) {
                        _assetDefectsForm.value = apiResponse.data
                        // Extract defect options as simple list
                        _assetDefectsState.value = apiResponse.data.allowedAssetDefects
                        println("✅ Loaded ${apiResponse.data.allowedAssetDefects.size} asset defect options")
                    }
                }
            } catch (e: Exception) {
                println("⚠️ Failed to load asset defects: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun loadDefectForms(token: String, vehicleId: Int, assetId: Int? = null) {
        viewModelScope.launch {
            try {
                // Load vehicle defects form
                val vehicleResponse = apiService.getVehicleDefects(token, vehicleId)
                if (vehicleResponse.isSuccessful) {
                    val apiResponse = vehicleResponse.body()
                    if (apiResponse?.success == true && apiResponse.data != null) {
                        _vehicleDefectsForm.value = apiResponse.data
                        _vehicleDefectsState.value = apiResponse.data.allowedVehicleDefects
                        println("✅ Loaded vehicle defects form: ${apiResponse.data.formName}")
                    }
                }

                // Load asset defects form (optional)
                if (assetId != null) {
                    val assetResponse = apiService.getAssetDefects(token, assetId)
                    if (assetResponse.isSuccessful) {
                        val apiResponse = assetResponse.body()
                        if (apiResponse?.success == true && apiResponse.data != null) {
                            _assetDefectsForm.value = apiResponse.data
                            _assetDefectsState.value = apiResponse.data.allowedAssetDefects
                            println("✅ Loaded asset defects form: ${apiResponse.data.formName}")
                        }
                    }
                }
            } catch (e: Exception) {
                println("⚠️ Failed to load defect forms: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun addVehicleDefect(defect: VehicleDefectItem) {
        _selectedVehicleDefects.value = _selectedVehicleDefects.value + defect
    }

    fun removeVehicleDefect(defect: VehicleDefectItem) {
        _selectedVehicleDefects.value = _selectedVehicleDefects.value.filter { it.defect != defect.defect }
    }

    fun addAssetDefect(defect: AssetDefectItem) {
        _selectedAssetDefects.value = _selectedAssetDefects.value + defect
    }

    fun removeAssetDefect(defect: AssetDefectItem) {
        _selectedAssetDefects.value = _selectedAssetDefects.value.filter { it.defect != defect.defect }
    }

    fun createInspection(
        token: String,
        request: InspectionCreateRequest,
        onSuccess: (Inspection) -> Unit
    ) {
        viewModelScope.launch {
            _createInspectionState.value = CreateInspectionUiState.Loading

            try {
                val response = apiService.createInspection(token, request)

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.success == true && apiResponse.data != null) {
                        _createInspectionState.value = CreateInspectionUiState.Success(apiResponse.data)
                        println("✅ Inspection created: ${apiResponse.data.id}")
                        onSuccess(apiResponse.data)
                        resetCreateForm()
                    } else {
                        val error = apiResponse?.error ?: "Failed to create inspection"
                        _createInspectionState.value = CreateInspectionUiState.Error(error)
                        println("❌ Create inspection failed: $error")
                    }
                } else {
                    val error = "Failed to create inspection: ${response.code()} ${response.message()}"
                    _createInspectionState.value = CreateInspectionUiState.Error(error)
                    println("❌ $error")
                }
            } catch (e: Exception) {
                val error = "Network error: ${e.message}"
                _createInspectionState.value = CreateInspectionUiState.Error(error)
                println("❌ $error")
                e.printStackTrace()
            }
        }
    }

    fun verifyInspection(token: String, inspectionId: Int, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val response = apiService.verifyInspection(token, inspectionId)

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.success == true && apiResponse.data != null) {
                        println("✅ Inspection verified: ${apiResponse.data}")
                        // Reload inspection detail to get updated data
                        loadInspectionDetail(token, inspectionId)
                        onSuccess()
                    } else {
                        val error = apiResponse?.error ?: "Failed to verify inspection"
                        println("❌ Verify inspection failed: $error")
                    }
                } else {
                    val error = "Failed to verify inspection: ${response.code()} ${response.message()}"
                    println("❌ $error")
                }
            } catch (e: Exception) {
                println("❌ Network error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun resetCreateForm() {
        _selectedVehicleDefects.value = emptyList()
        _selectedAssetDefects.value = emptyList()
        _createInspectionState.value = CreateInspectionUiState.Initial
    }

    fun clearError() {
        if (_inspectionsState.value is InspectionsUiState.Error) {
            _inspectionsState.value = InspectionsUiState.Loading
        }
        if (_inspectionDetailState.value is InspectionDetailUiState.Error) {
            _inspectionDetailState.value = InspectionDetailUiState.Loading
        }
        if (_createInspectionState.value is CreateInspectionUiState.Error) {
            _createInspectionState.value = CreateInspectionUiState.Initial
        }
    }
}

/**
 * UI States for Inspections List
 */
sealed class InspectionsUiState {
    object Loading : InspectionsUiState()
    data class Success(val data: PaginatedData<InspectionListItem>) : InspectionsUiState()
    data class Error(val message: String) : InspectionsUiState()
}

/**
 * UI States for Inspection Detail
 */
sealed class InspectionDetailUiState {
    object Loading : InspectionDetailUiState()
    data class Success(val inspection: Inspection) : InspectionDetailUiState()
    data class Error(val message: String) : InspectionDetailUiState()
}

/**
 * UI States for Create Inspection
 */
sealed class CreateInspectionUiState {
    object Initial : CreateInspectionUiState()
    object Loading : CreateInspectionUiState()
    data class Success(val inspection: Inspection) : CreateInspectionUiState()
    data class Error(val message: String) : CreateInspectionUiState()
}
