package com.eld.driver.data.repository

import android.content.Context
import com.eld.driver.data.api.ApiService
import com.eld.driver.data.local.ELDDatabase
import com.eld.driver.data.local.entity.DailyLogEntity
import com.eld.driver.data.local.entity.DutyStatusEventEntity
import com.eld.driver.data.local.entity.HOSStatusEntity
import com.eld.driver.data.local.entity.ViolationData
import com.eld.driver.data.models.*
import com.eld.driver.data.models.HosViolationTypeApi
import com.google.gson.Gson
import com.eld.driver.hos.HOSCalculationResult
import com.eld.driver.hos.HOSService
import com.eld.driver.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * ELDRepository - Single source of truth for all ELD data.
 *
 * This repository abstracts data access and provides:
 * - Offline-first data access (local DB prioritized)
 * - Automatic sync management
 * - HOS calculation integration
 * - Clean API for ViewModels
 */
class ELDRepository private constructor(
    private val context: Context
) {
    companion object {
        @Volatile
        private var INSTANCE: ELDRepository? = null

        fun getInstance(context: Context): ELDRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ELDRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val apiService = ApiService.getInstance()
    private val database = ELDDatabase.getInstance(context)
    private val syncManager = SyncManager.getInstance(context)
    private val hosService = HOSService.getInstance(context)

    // DAOs
    private val dutyStatusEventDao = database.dutyStatusEventDao()
    private val hosStatusDao = database.hosStatusDao()
    private val tickEventDao = database.tickEventDao()
    private val dailyLogDao = database.dailyLogDao()

    private val gson = Gson()

    // ═══════════════════════════════════════════════════════════════════════
    // HOS STATUS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get HOS status as Flow for reactive UI.
     * Returns locally calculated HOS.
     */
    fun getHOSStatusFlow(): Flow<HOSCalculationResult?> = hosService.hosStatus

    /**
     * Get current HOS status.
     */
    fun getCurrentHOSStatus(): HOSCalculationResult? = hosService.hosStatus.value

    /**
     * Get HOS status as HOSStatus model (for backward compatibility).
     */
    fun getHOSStatusModel(): HOSStatus? = hosService.getHOSStatusModel()

    /**
     * Force HOS recalculation.
     */
    suspend fun recalculateHOS() {
        hosService.recalculateNow()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DUTY STATUS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get current duty status from local database.
     * Uses most recent event by time to handle server-synced events.
     */
    suspend fun getCurrentDutyStatus(): DutyStatusEventEntity? {
        return dutyStatusEventDao.getMostRecentEvent()
    }

    /**
     * Get current duty status as Flow.
     * Uses most recent event by time (not just isActive=1) to handle server-synced events.
     */
    fun getCurrentDutyStatusFlow(): Flow<DutyStatusEventEntity?> {
        return dutyStatusEventDao.getMostRecentEventFlow()
    }

    /**
     * Get current duty status type.
     */
    suspend fun getCurrentDutyStatusType(): DutyStatusType? {
        val event = dutyStatusEventDao.getCurrentActiveEvent()
        return event?.let {
            try { DutyStatusType.valueOf(it.dutyStatus) } catch (e: Exception) { null }
        }
    }

    /**
     * Change duty status (offline-first).
     * Saves locally first, then syncs with backend.
     */
    suspend fun changeDutyStatus(request: DutyStatusChangeRequest): Result<String> {
        return try {
            val localEventId = hosService.onDutyStatusChanged(request)
            Result.success(localEventId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Save duty status that was already synced via direct API call.
     * This keeps local DB in sync without creating duplicate sync queue entries.
     */
    suspend fun saveSyncedDutyStatus(request: DutyStatusChangeRequest, serverId: Int) {
        syncManager.createSyncedDutyStatusEvent(request, serverId)
        // Recalculate HOS with new event
        hosService.recalculateNow()
    }

    /**
     * Get duty status events for a date range.
     */
    suspend fun getDutyStatusEvents(startTime: Long, endTime: Long): List<DutyStatusEventEntity> {
        return dutyStatusEventDao.getEventsForDate(startTime, endTime)
    }

    /**
     * Get duty status events that OVERLAP with the specified day.
     * Includes events that started before the day but extend into it.
     * This matches backend's GetDriverEventsOverlappingDateAsync logic.
     */
    suspend fun getDutyStatusEventsOverlapping(dayStart: Long, dayEnd: Long): List<DutyStatusEventEntity> {
        return dutyStatusEventDao.getEventsOverlappingDate(dayStart, dayEnd)
    }

    /**
     * Get duty status events for last N days.
     */
    suspend fun getDutyStatusEventsForDays(days: Int): List<DutyStatusEventEntity> {
        val since = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000)
        return dutyStatusEventDao.getEventsSince(since)
    }

    /**
     * Get duty status events as Flow.
     */
    fun getDutyStatusEventsFlow(since: Long): Flow<List<DutyStatusEventEntity>> {
        return dutyStatusEventDao.getEventsSinceFlow(since)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TICK EVENTS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Create a tick event.
     */
    suspend fun createTickEvent(request: TickEventRequest): Result<String> {
        return try {
            val localId = hosService.onTickEvent(request)
            Result.success(localId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SYNC OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Perform initial sync on login.
     */
    suspend fun performInitialSync(): Result<Unit> {
        return hosService.performInitialSync()
    }

    /**
     * Get sync status.
     */
    fun isOnline(): Boolean = syncManager.isOnline.value

    /**
     * Get pending sync count.
     */
    fun getPendingSyncCount(): Int = syncManager.pendingSyncCount.value

    /**
     * Get pending sync count as Flow.
     */
    fun getPendingSyncCountFlow(): Flow<Int> = syncManager.pendingSyncCount

    /**
     * Force process sync queue.
     */
    suspend fun forceSyncQueue(): Int {
        android.util.Log.d("ELDRepository", "Force sync queue triggered")
        return syncManager.processQueue()
    }

    /**
     * Manual sync trigger for testing.
     */
    suspend fun triggerManualSync() {
        android.util.Log.d("ELDRepository", "Manual sync triggered - online: ${syncManager.isOnline.value}")
        val count = syncManager.processQueue()
        android.util.Log.d("ELDRepository", "Manual sync completed - processed $count items")
    }

    /**
     * Get last sync error for debug display.
     */
    fun getLastSyncError(): String? = syncManager.lastSyncError.value

    /**
     * Clear sync queue (for testing/debug).
     */
    suspend fun clearSyncQueue() {
        val db = ELDDatabase.getInstance(context)
        db.syncQueueDao().clearQueue()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SERVICE LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Start HOS service.
     * Should be called after successful login.
     */
    fun startServices() {
        hosService.start()
    }

    /**
     * Stop HOS service.
     */
    fun stopServices() {
        hosService.stop()
    }

    /**
     * Set violation callback.
     */
    fun setViolationCallback(callback: ((List<com.eld.driver.data.local.entity.HOSViolationType>) -> Unit)?) {
        hosService.onViolationDetected = callback
    }

    /**
     * Sync all pending data to server before logout.
     * Called before clearing local data.
     */
    suspend fun syncBeforeLogout() {
        android.util.Log.d("ELDRepository", "Syncing before logout...")

        // Force sync all pending events
        val syncedCount = syncManager.processQueue()
        android.util.Log.d("ELDRepository", "Synced $syncedCount pending events")

        // Analyze and sync violations BEFORE syncing HOS
        val violationSyncService = com.eld.driver.hos.ViolationSyncService.getInstance(context)
        val violationsSynced = violationSyncService.analyzeAndSyncViolations()
        android.util.Log.d("ELDRepository", "Synced $violationsSynced violations")

        // Sync current HOS
        hosService.syncHOSNow()
        android.util.Log.d("ELDRepository", "HOS synced")
    }

    /**
     * Logout - clear all local data and stop services.
     */
    suspend fun logout() {
        hosService.logout()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // NETWORK API CALLS (For data not cached locally)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Login user.
     */
    suspend fun login(email: String, password: String): Result<LoginResponse> {
        return try {
            val response = apiService.login(LoginRequest(email, password))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Login failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get vehicles from backend.
     */
    suspend fun getVehicles(token: String, searchTerm: String? = null): Result<List<Vehicle>> {
        return try {
            val response = apiService.getVehicles(token, searchTerm)
            if (response.isSuccessful && response.body()?.success == true) {
                val vehicles = response.body()?.data?.vehicles ?: emptyList()
                Result.success(vehicles)
            } else {
                Result.failure(Exception(response.body()?.error ?: "Failed to get vehicles"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get driver logs from backend (for logs screen).
     */
    suspend fun getDriverLogs(token: String, days: Int = 7): Result<DriverLogsData> {
        return try {
            val response = apiService.getDriverLogs(token, days)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.data!!)
            } else {
                Result.failure(Exception(response.body()?.error ?: "Failed to get logs"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DAILY LOGS (Local Storage)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Sync daily logs from server and store locally.
     * Backend returns: last 7 days + uncertified logs (up to 6 months old).
     * This method replaces local logs with what backend returns.
     */
    suspend fun syncDailyLogs(token: String, days: Int = 8): Result<Int> {
        return try {
            val response = apiService.getDriverLogs(token, days)
            if (response.isSuccessful && response.body()?.success == true) {
                val logs = response.body()?.data?.logs ?: emptyList()

                // Clear all existing logs and replace with server data
                // This ensures certified old logs disappear when backend stops returning them
                dailyLogDao.deleteAll()

                // Convert DTOs to entities and store
                val entities = logs.map { dto -> dto.toEntity() }
                dailyLogDao.insertOrUpdateAll(entities)

                android.util.Log.d("ELDRepository", "Synced ${entities.size} daily logs (replaced all)")
                Result.success(entities.size)
            } else {
                Result.failure(Exception(response.body()?.error ?: "Failed to sync logs"))
            }
        } catch (e: Exception) {
            android.util.Log.e("ELDRepository", "Error syncing daily logs", e)
            Result.failure(e)
        }
    }

    /**
     * Get daily logs from local database.
     * Returns ALL synced logs - backend already filters correctly:
     * - Last 7 days + uncertified logs (up to 6 months old)
     * The 'days' parameter is kept for API compatibility but is ignored.
     */
    suspend fun getLocalDailyLogs(days: Int = 7): List<DailyLogDto> {
        // Use getAllSyncedLogs() instead of getLogsForDays(days)
        // Backend already returns correct logs: 7 days + uncertified older ones
        val entities = dailyLogDao.getAllSyncedLogs()
        return entities.map { it.toDto() }
    }

    /**
     * Get all daily logs as Flow.
     */
    fun getDailyLogsFlow(): Flow<List<DailyLogDto>> {
        return dailyLogDao.getAllLogsFlow().map { entities ->
            entities.map { it.toDto() }
        }
    }

    /**
     * Get a specific daily log by date.
     */
    suspend fun getDailyLogByDate(date: String): DailyLogDto? {
        return dailyLogDao.getLogByDate(date)?.toDto()
    }

    /**
     * Update certification status locally.
     */
    suspend fun updateLogCertification(date: String, isCertified: Boolean) {
        dailyLogDao.updateCertificationStatus(date, isCertified)
    }

    /**
     * Extension function to convert DailyLogDto to Entity.
     */
    private fun DailyLogDto.toEntity(): DailyLogEntity {
        val violationDataList = violations.map { violation ->
            ViolationData(
                id = violation.id,
                violationType = violation.violationType.name,  // Convert enum to String
                startTime = violation.startTime,
                endTime = violation.endTime,
                violationError = violation.violationError,
                overLimitMinutes = violation.overLimitMinutes,
                vehicleId = violation.vehicleId,
                vehicleNumber = violation.vehicleNumber,
                isAcknowledged = violation.isAcknowledged
            )
        }

        return DailyLogEntity(
            date = date.substringBefore("T"),  // Store as yyyy-MM-dd
            dayOfWeek = dayOfWeek,
            month = month,
            day = day,
            recapHours = recapHours,
            recapMinutes = recapMinutes,
            defectsCount = defectsCount,
            distanceMiles = distanceMiles,
            isCertified = isCertified,
            hasInspections = hasInspections,
            violationCount = violationCount,
            violationsJson = gson.toJson(violationDataList),
            formMannerErrorCount = formMannerErrorCount,
            formMannerErrorsJson = gson.toJson(formMannerErrors),
            inspectionCount = inspectionCount
        )
    }

    /**
     * Extension function to convert Entity to DailyLogDto.
     */
    private fun DailyLogEntity.toDto(): DailyLogDto {
        val violationDataList: List<ViolationData> = try {
            gson.fromJson(violationsJson, Array<ViolationData>::class.java)?.toList() ?: emptyList()
        } catch (e: Exception) { emptyList() }

        val formMannerErrorsList: List<String> = try {
            gson.fromJson(formMannerErrorsJson, Array<String>::class.java)?.toList() ?: emptyList()
        } catch (e: Exception) { emptyList() }

        return DailyLogDto(
            date = "${date}T00:00:00.000Z",
            dayOfWeek = dayOfWeek,
            month = month,
            day = day,
            recapHours = recapHours,
            recapMinutes = recapMinutes,
            defectsCount = defectsCount,
            distanceMiles = distanceMiles,
            isCertified = isCertified,
            hasInspections = hasInspections,
            violationCount = violationCount,
            violations = violationDataList.map { data ->
                HosViolationDto(
                    id = data.id,
                    violationType = try {
                        HosViolationTypeApi.valueOf(data.violationType)
                    } catch (e: Exception) {
                        HosViolationTypeApi.DRIVING_11_HOUR  // Default fallback
                    },
                    startTime = data.startTime,
                    endTime = data.endTime,
                    violationError = data.violationError,
                    overLimitMinutes = data.overLimitMinutes,
                    vehicleId = data.vehicleId,
                    vehicleNumber = data.vehicleNumber,
                    isAcknowledged = data.isAcknowledged,
                    acknowledgedAt = null,
                    isActive = data.endTime == null
                )
            },
            formMannerErrorCount = formMannerErrorCount,
            formMannerErrors = formMannerErrorsList,
            inspectionCount = inspectionCount
        )
    }

    /**
     * Get driver events for specific date.
     */
    suspend fun getDriverEvents(token: String, date: String): Result<DriverEventsData> {
        return try {
            val response = apiService.getDriverEvents(token, date)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.data!!)
            } else {
                Result.failure(Exception(response.body()?.error ?: "Failed to get events"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Certify log for specific date.
     */
    suspend fun certifyLog(token: String, date: String): Result<Unit> {
        return try {
            val response = apiService.certifyLog(token, date)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.body()?.error ?: "Failed to certify log"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
