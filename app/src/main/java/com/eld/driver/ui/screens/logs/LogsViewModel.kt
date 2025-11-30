package com.eld.driver.ui.screens.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eld.driver.data.api.ApiService
import com.eld.driver.data.models.DailyLogDto
import com.eld.driver.data.models.DriverEventsData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Logs Screen
 */
sealed class LogsUiState {
    object Loading : LogsUiState()
    data class Success(val logs: List<DailyLogDto>) : LogsUiState()
    data class Error(val message: String) : LogsUiState()
}

/**
 * UI State for Log Detail (Events for a specific date)
 */
sealed class LogDetailUiState {
    object Idle : LogDetailUiState()
    object Loading : LogDetailUiState()
    data class Success(val events: DriverEventsData) : LogDetailUiState()
    data class Error(val message: String) : LogDetailUiState()
}

/**
 * LogsViewModel - Handles logs data and state
 */
class LogsViewModel : ViewModel() {
    private val apiService = ApiService.getInstance()

    private val _logsState = MutableStateFlow<LogsUiState>(LogsUiState.Loading)
    val logsState: StateFlow<LogsUiState> = _logsState.asStateFlow()

    private val _logDetailState = MutableStateFlow<LogDetailUiState>(LogDetailUiState.Idle)
    val logDetailState: StateFlow<LogDetailUiState> = _logDetailState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _certifyingDate = MutableStateFlow<String?>(null)
    val certifyingDate: StateFlow<String?> = _certifyingDate.asStateFlow()

    /**
     * Load driver logs for the last N days
     */
    fun loadLogs(token: String, days: Int = 7) {
        viewModelScope.launch {
            _logsState.value = LogsUiState.Loading
            try {
                val response = apiService.getDriverLogs(token, days)
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    if (data != null) {
                        _logsState.value = LogsUiState.Success(data.logs)
                    } else {
                        _logsState.value = LogsUiState.Error("No data received")
                    }
                } else {
                    val error = response.body()?.error ?: "Failed to load logs"
                    _logsState.value = LogsUiState.Error(error)
                }
            } catch (e: Exception) {
                _logsState.value = LogsUiState.Error(e.message ?: "Network error")
            }
        }
    }

    /**
     * Refresh logs (pull to refresh)
     */
    fun refreshLogs(token: String, days: Int = 7) {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val response = apiService.getDriverLogs(token, days)
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    if (data != null) {
                        _logsState.value = LogsUiState.Success(data.logs)
                    }
                }
            } catch (e: Exception) {
                // Silent fail on refresh
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Load events for a specific date
     */
    fun loadLogDetail(token: String, date: String) {
        viewModelScope.launch {
            _logDetailState.value = LogDetailUiState.Loading
            try {
                val response = apiService.getDriverEvents(token, date)
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    if (data != null) {
                        _logDetailState.value = LogDetailUiState.Success(data)
                    } else {
                        _logDetailState.value = LogDetailUiState.Error("No data received")
                    }
                } else {
                    val error = response.body()?.error ?: "Failed to load log details"
                    _logDetailState.value = LogDetailUiState.Error(error)
                }
            } catch (e: Exception) {
                _logDetailState.value = LogDetailUiState.Error(e.message ?: "Network error")
            }
        }
    }

    /**
     * Certify a specific log date
     */
    fun certifyLog(token: String, date: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _certifyingDate.value = date
            try {
                val response = apiService.certifyLog(token, date)
                if (response.isSuccessful && response.body()?.success == true) {
                    // Refresh logs to show updated certification status
                    refreshLogs(token)
                    onSuccess()
                } else {
                    val error = response.body()?.error ?: "Failed to certify log"
                    onError(error)
                }
            } catch (e: Exception) {
                onError(e.message ?: "Network error")
            } finally {
                _certifyingDate.value = null
            }
        }
    }

    /**
     * Certify all uncertified logs
     */
    fun certifyAllLogs(token: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val currentState = _logsState.value
            if (currentState is LogsUiState.Success) {
                val uncertifiedLogs = currentState.logs.filter { !it.isCertified }
                var successCount = 0
                var errorMessage: String? = null

                for (log in uncertifiedLogs) {
                    try {
                        // Extract date in yyyy-MM-dd format
                        val dateStr = log.date.substringBefore("T")
                        _certifyingDate.value = dateStr
                        val response = apiService.certifyLog(token, dateStr)
                        if (response.isSuccessful && response.body()?.success == true) {
                            successCount++
                        } else {
                            errorMessage = response.body()?.error ?: "Failed to certify some logs"
                        }
                    } catch (e: Exception) {
                        errorMessage = e.message
                    }
                }

                _certifyingDate.value = null

                if (errorMessage != null) {
                    onError(errorMessage)
                } else {
                    // Refresh logs to show updated certification status
                    refreshLogs(token)
                    onSuccess()
                }
            }
        }
    }

    /**
     * Reset log detail state
     */
    fun resetLogDetail() {
        _logDetailState.value = LogDetailUiState.Idle
    }
}
