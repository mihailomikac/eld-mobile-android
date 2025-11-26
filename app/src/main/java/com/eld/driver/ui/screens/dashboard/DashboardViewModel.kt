package com.eld.driver.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * DashboardViewModel - Handles dashboard data and state
 */
class DashboardViewModel : ViewModel() {
    private val apiService = ApiService.getInstance()

    private val _currentDutyStatus = MutableStateFlow<DutyStatusUiState>(DutyStatusUiState.Loading)
    val currentDutyStatus: StateFlow<DutyStatusUiState> = _currentDutyStatus.asStateFlow()

    private val _hosStatus = MutableStateFlow<HOSStatus?>(null)
    val hosStatus: StateFlow<HOSStatus?> = _hosStatus.asStateFlow()

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
