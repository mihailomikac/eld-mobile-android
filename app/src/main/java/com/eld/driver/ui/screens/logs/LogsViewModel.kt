package com.eld.driver.ui.screens.logs

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eld.driver.data.api.ApiService
import com.eld.driver.data.local.TokenManager
import com.eld.driver.data.models.DailyLogDto
import com.eld.driver.data.models.DriverEventsData
import com.eld.driver.data.models.DutyStatusEventDto
import com.eld.driver.data.models.DutyStatusSummary
import com.eld.driver.data.models.DutyStatusType
import com.eld.driver.data.repository.ELDRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
 *
 * ALWAYS uses LOCAL data only. Server data is fetched during initial sync on login.
 * This ensures consistent display regardless of network state.
 *
 * All times are displayed in COMPANY TIMEZONE (extracted from JWT token).
 */
class LogsViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "LogsViewModel"
    }

    private val apiService = ApiService.getInstance()
    private val repository = ELDRepository.getInstance(application)
    private val tokenManager = TokenManager.getInstance(application)

    // Company timezone for display - extracted from JWT token
    private val companyTimeZone: TimeZone
        get() = tokenManager.getCompanyTimeZone()

    private val _logsState = MutableStateFlow<LogsUiState>(LogsUiState.Loading)
    val logsState: StateFlow<LogsUiState> = _logsState.asStateFlow()

    private val _logDetailState = MutableStateFlow<LogDetailUiState>(LogDetailUiState.Idle)
    val logDetailState: StateFlow<LogDetailUiState> = _logDetailState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _certifyingDate = MutableStateFlow<String?>(null)
    val certifyingDate: StateFlow<String?> = _certifyingDate.asStateFlow()

    /**
     * Load driver logs.
     * Backend returns: last 8 days (today + 7 previous) + uncertified logs (up to 6 months old).
     * First tries to load from local database, then syncs from server in background.
     *
     * @param days - passed to backend API (default 8 = today + 7 previous days).
     *               Backend will also include any uncertified logs older than this, up to 6 months.
     */
    fun loadLogs(token: String, days: Int = 8) {
        viewModelScope.launch {
            _logsState.value = LogsUiState.Loading

            // First try to load from local Room database (server-synced logs)
            val serverLogs = repository.getLocalDailyLogs(days)
            if (serverLogs.isNotEmpty()) {
                Log.d(TAG, "Loaded ${serverLogs.size} logs from local database (server-synced)")
                _logsState.value = LogsUiState.Success(serverLogs)

                // Sync from server in background to get updates
                syncLogsFromServer(token, days)
            } else {
                // No server-synced logs - fall back to generating from local duty events
                val localLogs = loadLogsFromLocal(days)
                if (localLogs.isNotEmpty()) {
                    _logsState.value = LogsUiState.Success(localLogs)
                } else {
                    _logsState.value = LogsUiState.Error("No logs available")
                }

                // Try to sync from server
                syncLogsFromServer(token, days)
            }
        }
    }

    /**
     * Sync logs from server in background.
     */
    private fun syncLogsFromServer(token: String, days: Int) {
        viewModelScope.launch {
            try {
                val result = repository.syncDailyLogs(token, days)
                if (result.isSuccess) {
                    Log.d(TAG, "Synced ${result.getOrNull()} logs from server")
                    // Reload from local database
                    val updatedLogs = repository.getLocalDailyLogs(days)
                    if (updatedLogs.isNotEmpty()) {
                        _logsState.value = LogsUiState.Success(updatedLogs)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing logs from server", e)
                // Don't update state - keep showing local data
            }
        }
    }

    /**
     * Generate logs from local duty status events.
     * Groups events by date in COMPANY TIMEZONE.
     * ALWAYS generates a log entry for EVERY day in the range (today + previous N-1 days).
     * For example: days=8 generates today + 7 previous days.
     */
    private suspend fun loadLogsFromLocal(days: Int): List<DailyLogDto> {
        val events = repository.getDutyStatusEventsForDays(days)
        Log.d(TAG, "loadLogsFromLocal: Requested $days days, got ${events.size} events from repository")

        // Use company timezone for grouping events by date
        val tz = companyTimeZone
        Log.d(TAG, "Loading logs using company timezone: ${tz.id}")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = tz
        }
        val now = System.currentTimeMillis()

        // Group events by date in COMPANY TIMEZONE
        val eventsByDate = events.groupBy { dateFormat.format(Date(it.startTime)) }

        Log.d(TAG, "loadLogsFromLocal: Events grouped into ${eventsByDate.size} days: ${eventsByDate.keys.sorted()}")

        // Generate ALL dates in the range (today and last N-1 days)
        val allDates = mutableListOf<String>()
        val calendar = Calendar.getInstance(tz)
        for (i in 0 until days) {
            allDates.add(dateFormat.format(calendar.time))
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        Log.d(TAG, "loadLogsFromLocal: Generating logs for $days days: ${allDates.sorted()}")

        return allDates.map { date ->
            val dayEvents = eventsByDate[date] ?: emptyList()
            // Calculate end of this day (midnight) in COMPANY TIMEZONE
            val parsedDate = dateFormat.parse(date)
            val calendar = Calendar.getInstance(tz).apply {
                time = parsedDate ?: Date()
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val endOfDay = calendar.timeInMillis

            // Check if this is today in COMPANY TIMEZONE
            val todayStr = dateFormat.format(Date(now))
            val isToday = date == todayStr

            // Calculate totals for each status type
            var offDutyMinutes = 0
            var sleeperMinutes = 0
            var drivingMinutes = 0
            var onDutyMinutes = 0

            // Sort events by start time to find the last one
            val sortedEvents = dayEvents.sortedBy { it.startTime }

            sortedEvents.forEachIndexed { index, event ->
                val isLastEventOfDay = index == sortedEvents.size - 1

                // Calculate duration
                val duration = if (event.durationMinutes != null && !isLastEventOfDay) {
                    // Use stored duration if not the last event
                    event.durationMinutes
                } else if (isLastEventOfDay) {
                    // Last event of the day:
                    // - If today and active: calculate to now
                    // - If past day: calculate to end of that day
                    val endTime = if (isToday && event.isActive) {
                        now
                    } else {
                        event.endTime ?: endOfDay
                    }
                    ((endTime - event.startTime) / 60000).toInt()
                } else if (event.endTime != null) {
                    // Has end time but no duration stored
                    ((event.endTime - event.startTime) / 60000).toInt()
                } else {
                    0
                }

                when (event.dutyStatus) {
                    "OFF_DUTY" -> offDutyMinutes += duration
                    "SLEEPER_BERTH" -> sleeperMinutes += duration
                    "DRIVING" -> drivingMinutes += duration
                    "ON_DUTY_NOT_DRIVING" -> onDutyMinutes += duration
                }
            }

            // Reset calendar for display formatting in COMPANY TIMEZONE
            calendar.time = parsedDate ?: Date()
            val dayOfWeekFormat = SimpleDateFormat("EEE", Locale.US).apply { timeZone = tz }
            val monthFormat = SimpleDateFormat("MMM", Locale.US).apply { timeZone = tz }

            // Recap = ON_DUTY + DRIVING (work time)
            val recapTotalMinutes = onDutyMinutes + drivingMinutes

            DailyLogDto(
                date = "${date}T00:00:00.000Z",
                dayOfWeek = dayOfWeekFormat.format(parsedDate ?: Date()),
                month = monthFormat.format(parsedDate ?: Date()),
                day = calendar.get(Calendar.DAY_OF_MONTH),
                recapHours = recapTotalMinutes / 60,
                recapMinutes = recapTotalMinutes % 60,
                defectsCount = 0,
                distanceMiles = 0.0,
                isCertified = false,  // TODO: Store certification status locally
                hasInspections = false
            )
        }.sortedByDescending { it.date }
    }

    /**
     * Refresh logs (pull to refresh).
     * Force syncs from server and updates local database.
     * Backend returns: last 8 days (today + 7 previous) + uncertified logs (up to 6 months old).
     */
    fun refreshLogs(token: String, days: Int = 8) {
        viewModelScope.launch {
            _isRefreshing.value = true

            // Force sync from server
            try {
                val result = repository.syncDailyLogs(token, days)
                if (result.isSuccess) {
                    Log.d(TAG, "Refreshed ${result.getOrNull()} logs from server")
                    val updatedLogs = repository.getLocalDailyLogs(days)
                    if (updatedLogs.isNotEmpty()) {
                        _logsState.value = LogsUiState.Success(updatedLogs)
                    }
                } else {
                    // If sync fails, reload from local
                    loadLogs(token, days)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing logs", e)
                loadLogs(token, days)
            }

            _isRefreshing.value = false
        }
    }

    /**
     * Load events for a specific date.
     *
     * OFFLINE-FIRST STRATEGY:
     * - TODAY: Always use local database (may have unsynced events from current session)
     * - PAST DAYS: Try server first (properly clamped), fallback to local
     *
     * This ensures users always see their recent changes even when offline,
     * while historical data is properly formatted from the server.
     */
    fun loadLogDetail(token: String, date: String) {
        viewModelScope.launch {
            _logDetailState.value = LogDetailUiState.Loading

            val dateStr = date.substringBefore("T")
            val tz = companyTimeZone
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }
            val todayStr = dateFormat.format(Date())
            val isToday = dateStr == todayStr

            if (isToday) {
                // TODAY: Always use local database (offline-first for current session)
                Log.d(TAG, "Loading TODAY's events from local database (offline-first)")
                val localEvents = loadEventsFromLocal(date)
                if (localEvents != null) {
                    Log.d(TAG, "Loaded ${localEvents.dutyStatusEvents.size} events from local for today")
                    _logDetailState.value = LogDetailUiState.Success(localEvents)
                } else {
                    _logDetailState.value = LogDetailUiState.Error("No events for today")
                }
            } else {
                // PAST DAYS: Try server first (properly clamped), fallback to local
                try {
                    val response = apiService.getDriverEvents(token, dateStr)
                    if (response.isSuccessful && response.body()?.success == true) {
                        val serverData = response.body()!!.data!!
                        Log.d(TAG, "Loaded ${serverData.dutyStatusEvents.size} events from server for $dateStr")
                        _logDetailState.value = LogDetailUiState.Success(serverData)
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Server unavailable for $dateStr, falling back to local: ${e.message}")
                }

                // Fallback to local database for past days
                val localEvents = loadEventsFromLocal(date)
                if (localEvents != null) {
                    Log.d(TAG, "Loaded ${localEvents.dutyStatusEvents.size} events from local for $dateStr")
                    _logDetailState.value = LogDetailUiState.Success(localEvents)
                } else {
                    _logDetailState.value = LogDetailUiState.Error("No events for this date")
                }
            }
        }
    }

    /**
     * Load events for a specific date from local database.
     * Uses COMPANY TIMEZONE for date calculations.
     * Implements same CLAMP logic as backend to ensure graph alignment.
     */
    private suspend fun loadEventsFromLocal(date: String): DriverEventsData? {
        val tz = companyTimeZone
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = tz
        }
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val now = System.currentTimeMillis()

        // Parse the date to get start/end of day in COMPANY TIMEZONE
        val dateStr = date.substringBefore("T")
        val calendar = Calendar.getInstance(tz)
        try {
            val parsed = dateFormat.parse(dateStr) ?: return null
            calendar.time = parsed
        } catch (e: Exception) {
            return null
        }

        // Start of day in company timezone (00:00:00.000)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val dayStart = calendar.timeInMillis

        // End of day in company timezone (next day 00:00:00.000)
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val dayEnd = calendar.timeInMillis

        // Check if this is today in COMPANY TIMEZONE
        val todayStr = dateFormat.format(Date(now))
        val isToday = dateStr == todayStr

        Log.d(TAG, "Loading events for $dateStr in timezone ${tz.id}, dayStart=$dayStart, dayEnd=$dayEnd")

        // Use OVERLAPPING query - includes events that started before this day but extend into it
        var events = repository.getDutyStatusEventsOverlapping(dayStart, dayEnd)

        // Filter out events that don't actually overlap (e.g., active events where current time is before dayStart)
        var relevantEvents = events.filter { entity ->
            val actualEnd = entity.endTime ?: now
            actualEnd > dayStart
        }

        // FALLBACK: If no overlapping events, get the most recent event and carry it forward
        // This handles the case where the last event was from a previous day but is still "current"
        if (relevantEvents.isEmpty()) {
            Log.d(TAG, "No overlapping events for $dateStr, checking for carry-forward event")
            val lastEvent = repository.getCurrentDutyStatus()
            if (lastEvent != null && lastEvent.startTime < dayStart) {
                Log.d(TAG, "Found carry-forward event: ${lastEvent.dutyStatus} from ${Date(lastEvent.startTime)}")
                relevantEvents = listOf(lastEvent)
            } else {
                Log.d(TAG, "No carry-forward event found")
                return null
            }
        }

        // Sort events by start time
        val sortedEvents = relevantEvents.sortedBy { it.startTime }

        Log.d(TAG, "Found ${sortedEvents.size} overlapping events for $dateStr")

        // Map entities to DTOs with CLAMP logic (same as backend)
        val dutyStatusEvents = sortedEvents.mapIndexed { index, entity ->
            val isFirstEvent = index == 0
            val isLastEvent = index == sortedEvents.size - 1

            // CLAMP startTime: max(event.startTime, dayStart)
            val clampedStartTime = maxOf(entity.startTime, dayStart)

            // Determine actual end time:
            // 1. If event is still active (endTime == null) → use 'now' for today, dayEnd for past days
            // 2. If event ended BEFORE dayStart (carry-forward scenario) → use 'now' for today, dayEnd for past days
            // 3. Otherwise use the actual endTime
            val effectiveEndTime = when {
                entity.endTime == null -> if (isToday) now else dayEnd
                entity.endTime < dayStart -> if (isToday) now else dayEnd  // Carry-forward: extend to current time
                else -> entity.endTime
            }

            // CLAMP endTime: min(effectiveEndTime, dayEnd)
            val clampedEndTime = minOf(effectiveEndTime, dayEnd)

            // Calculate duration based on clamped times (ensure non-negative)
            val calculatedDuration = maxOf(0, ((clampedEndTime - clampedStartTime) / 60000).toInt())

            // actualStartTime: only for FIRST event if it started BEFORE the day
            val actualStartTimeMs: Long? = if (isFirstEvent && entity.startTime < dayStart) {
                entity.startTime
            } else null

            // actualEndTime: only for LAST event if it ends at/after day boundary OR is still active
            val actualEndTimeMs: Long? = if (isLastEvent && (effectiveEndTime >= dayEnd || entity.endTime == null)) {
                effectiveEndTime
            } else null

            Log.d(TAG, "Event ${entity.dutyStatus}: start=${entity.startTime}, clamped=$clampedStartTime, effectiveEnd=$effectiveEndTime, duration=$calculatedDuration")

            DutyStatusEventDto(
                id = entity.serverId ?: 0,
                dutyStatus = try {
                    DutyStatusType.valueOf(entity.dutyStatus)
                } catch (e: Exception) {
                    DutyStatusType.OFF_DUTY
                },
                // Use CLAMPED times for display/graph
                startTime = isoFormat.format(Date(clampedStartTime)),
                endTime = isoFormat.format(Date(clampedEndTime)),
                durationMinutes = calculatedDuration,
                location = entity.location,
                latitude = entity.latitude,
                longitude = entity.longitude,
                vehicleId = entity.vehicleId,
                vehicleNumber = null,  // Not stored locally
                odometer = entity.odometer,
                engineHours = entity.engineHours,
                note = entity.note,  // Map note from local entity
                isActive = entity.isActive,
                // Actual times for reference
                actualStartTime = actualStartTimeMs,
                actualEndTime = actualEndTimeMs
            )
        }

        // Calculate summary from clamped durations
        val summary = DutyStatusSummary(
            offDutyMinutes = dutyStatusEvents
                .filter { it.dutyStatus == DutyStatusType.OFF_DUTY || it.dutyStatus == DutyStatusType.PERSONAL_CONVEYANCE }
                .sumOf { it.durationMinutes ?: 0 },
            sleeperBerthMinutes = dutyStatusEvents
                .filter { it.dutyStatus == DutyStatusType.SLEEPER_BERTH }
                .sumOf { it.durationMinutes ?: 0 },
            drivingMinutes = dutyStatusEvents
                .filter { it.dutyStatus == DutyStatusType.DRIVING }
                .sumOf { it.durationMinutes ?: 0 },
            onDutyNotDrivingMinutes = dutyStatusEvents
                .filter { it.dutyStatus == DutyStatusType.ON_DUTY_NOT_DRIVING || it.dutyStatus == DutyStatusType.YARD_MOVE }
                .sumOf { it.durationMinutes ?: 0 },
            totalOnDutyMinutes = dutyStatusEvents
                .filter { it.dutyStatus in listOf(DutyStatusType.DRIVING, DutyStatusType.ON_DUTY_NOT_DRIVING, DutyStatusType.YARD_MOVE) }
                .sumOf { it.durationMinutes ?: 0 }
        )

        return DriverEventsData(
            date = "${dateStr}T00:00:00.000Z",
            dutyStatusEvents = dutyStatusEvents,
            tickEvents = emptyList(),  // Not loaded locally
            summary = summary,
            coDriversInfo = null,
            coDriversDutyEvents = null,
            vehicle = null,
            isCertified = false
        )
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
                    // Update local database
                    val dateStr = date.substringBefore("T")
                    repository.updateLogCertification(dateStr, true)

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
     * Certify all certifiable logs (not already certified and not today)
     */
    fun certifyAllLogs(token: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val currentState = _logsState.value
            if (currentState is LogsUiState.Success) {
                // Use canCertify to skip already certified AND today's log
                val certifiableLogs = currentState.logs.filter { it.canCertify }
                var successCount = 0
                var errorMessage: String? = null

                for (log in certifiableLogs) {
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
