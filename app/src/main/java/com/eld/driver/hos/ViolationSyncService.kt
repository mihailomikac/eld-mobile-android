package com.eld.driver.hos

import android.content.Context
import android.util.Log
import com.eld.driver.data.api.ApiService
import com.eld.driver.data.local.ELDDatabase
import com.eld.driver.data.local.TokenManager
import com.eld.driver.data.local.dao.DutyStatusEventDao
import com.eld.driver.data.local.dao.ViolationRecordDao
import com.eld.driver.data.local.entity.HOSViolationType
import com.eld.driver.data.local.entity.ViolationRecordEntity
import com.eld.driver.data.models.EndViolationRequest
import com.eld.driver.data.models.HosViolationItem
import com.eld.driver.data.models.HosViolationRequest
import com.eld.driver.data.models.HosViolationTypeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViolationSyncService - Handles violation analysis and synchronization.
 *
 * KEY PRINCIPLE: Violations are DERIVED from event history, not tracked in real-time.
 *
 * Flow:
 * 1. After events are synced, this service analyzes the complete event history
 * 2. Detects all violations with correct start/end times
 * 3. Compares with locally stored violations
 * 4. Syncs new/ended violations to backend
 *
 * This ensures violations are accurate even if driver was offline when they occurred.
 */
class ViolationSyncService private constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "ViolationSyncService"
        private const val DAYS_TO_ANALYZE = 8

        @Volatile
        private var INSTANCE: ViolationSyncService? = null

        fun getInstance(context: Context): ViolationSyncService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ViolationSyncService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val database = ELDDatabase.getInstance(context)
    private val dutyStatusEventDao: DutyStatusEventDao = database.dutyStatusEventDao()
    private val violationRecordDao: ViolationRecordDao = database.violationRecordDao()
    private val tokenManager = TokenManager.getInstance(context)
    private val apiService = ApiService.getInstance()
    private val violationAnalyzer = ViolationAnalyzer()

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Analyze violations from event history and sync to backend.
     * Call this AFTER events have been synced.
     *
     * SIMPLIFIED APPROACH:
     * 1. Clear all local violations
     * 2. Analyze events fresh
     * 3. Insert new violations
     * 4. Sync to backend (backend dedupes with isNew flag)
     *
     * This ensures local DB always matches current event state.
     *
     * @return Number of violations synced
     */
    suspend fun analyzeAndSyncViolations(): Int = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting violation analysis and sync...")

        try {
            // Step 1: Get all events for analysis
            val since = System.currentTimeMillis() - (DAYS_TO_ANALYZE * 24L * 60 * 60 * 1000)
            val events = dutyStatusEventDao.getEventsSince(since)

            if (events.isEmpty()) {
                Log.d(TAG, "No events to analyze")
                // Clear local violations since there are no events
                violationRecordDao.deleteAll()
                return@withContext 0
            }

            Log.d(TAG, "Analyzing ${events.size} events for violations")

            // Step 2: Analyze violations from event history
            val analyzedViolations = violationAnalyzer.analyzeViolations(events)
            Log.d(TAG, "Analysis found ${analyzedViolations.size} violations")

            // Step 3: Clear all local violations and rewrite with fresh data
            // This ensures local state always matches current event analysis
            violationRecordDao.deleteAll()
            Log.d(TAG, "Cleared local violations, inserting ${analyzedViolations.size} fresh")

            // Step 4: Insert all violations into local DB
            for (analyzed in analyzedViolations) {
                val entity = ViolationRecordEntity.fromViolationRecord(analyzed)
                violationRecordDao.insert(entity)
                Log.d(TAG, "Violation: ${analyzed.type.name}, start=${analyzed.toIsoStartTime()}, end=${analyzed.toIsoEndTime() ?: "active"}")
            }

            // Step 5: Sync ALL violations to backend as a BATCH
            val syncCount = syncViolationsBatchToBackend(analyzedViolations)

            // Step 6: Mark all as synced if successful
            if (syncCount > 0) {
                for (analyzed in analyzedViolations) {
                    val entity = ViolationRecordEntity.fromViolationRecord(analyzed)
                    violationRecordDao.markSynced(entity.id, 0)
                }
            }

            Log.d(TAG, "Violation sync complete: $syncCount synced to backend")
            return@withContext syncCount

        } catch (e: Exception) {
            Log.e(TAG, "Error in violation analysis/sync", e)
            return@withContext 0
        }
    }

    /**
     * Send ALL violations to backend as a BATCH.
     * Backend will:
     * 1. Delete all existing violations from deleteFromTime onwards
     * 2. Create new violations from the list
     *
     * @return Number of violations created on backend
     */
    private suspend fun syncViolationsBatchToBackend(violations: List<ViolationRecord>): Int {
        val token = tokenManager.getToken() ?: run {
            Log.w(TAG, "No auth token for violation sync")
            return 0
        }

        return try {
            // deleteFromTime: Use the start of the analysis period (8 days ago)
            // This tells backend to delete/replace any violations from this point forward
            val deleteFromTime = System.currentTimeMillis() - (DAYS_TO_ANALYZE * 24L * 60 * 60 * 1000)
            val deleteFromTimeStr = isoFormat.format(Date(deleteFromTime))

            // Convert violations to batch items
            val violationItems = violations.map { violation ->
                HosViolationItem(
                    violationType = violation.type.toApiType(),
                    startTime = violation.toIsoStartTime(),
                    endTime = violation.toIsoEndTime(),
                    violationError = "${violation.type.displayName} limit exceeded",
                    overLimitMinutes = null,
                    vehicleId = violation.vehicleId
                )
            }

            val request = HosViolationRequest(
                deleteFromTime = deleteFromTimeStr,
                deleteToTime = null,  // Delete all from deleteFromTime onwards
                violations = violationItems
            )

            Log.d(TAG, "📤 Sending ${violations.size} violations as batch, deleteFromTime=$deleteFromTimeStr")

            val response = apiService.createViolation(token, request)

            if (response.isSuccessful && response.body()?.success == true) {
                val result = response.body()?.data
                Log.d(TAG, "✅ Batch sync complete: created=${result?.createdCount}, deleted=${result?.deletedCount}, message=${result?.message}")
                result?.createdCount ?: violations.size
            } else {
                val error = response.body()?.error ?: "HTTP ${response.code()}"
                Log.e(TAG, "❌ Batch violation sync failed: $error")
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception syncing violations batch", e)
            0
        }
    }

    /**
     * End a violation on backend.
     */
    private suspend fun endViolationOnBackend(violation: ViolationRecord): Boolean {
        if (violation.endTime == null) {
            Log.w(TAG, "Cannot end violation without endTime")
            return false
        }

        val token = tokenManager.getToken() ?: run {
            Log.w(TAG, "No auth token for ending violation")
            return false
        }

        return try {
            val request = EndViolationRequest(
                violationType = violation.type.toApiType(),
                endTime = violation.toIsoEndTime()!!
            )

            Log.d(TAG, "📤 Ending violation: ${violation.type.name}, endTime=${request.endTime}")

            val response = apiService.endViolation(token, request)

            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "✅ Violation ended on backend: ${violation.type.name}")
                true
            } else {
                val error = response.body()?.error ?: "HTTP ${response.code()}"
                Log.e(TAG, "❌ Failed to end violation: $error")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception ending violation", e)
            false
        }
    }

    /**
     * Get current active violations (for UI display).
     */
    suspend fun getActiveViolations(): List<ViolationRecord> = withContext(Dispatchers.IO) {
        try {
            val entities = violationRecordDao.getActiveViolations()
            entities.map { it.toViolationRecord() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active violations", e)
            emptyList()
        }
    }

    /**
     * Re-analyze violations without syncing (for local display).
     */
    suspend fun analyzeViolationsLocally(): List<ViolationRecord> = withContext(Dispatchers.IO) {
        try {
            val since = System.currentTimeMillis() - (DAYS_TO_ANALYZE * 24L * 60 * 60 * 1000)
            val events = dutyStatusEventDao.getEventsSince(since)
            violationAnalyzer.analyzeViolations(events)
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing violations locally", e)
            emptyList()
        }
    }

    /**
     * Clear all violations (for logout).
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        violationRecordDao.deleteAll()
    }
}

/**
 * Extension to convert local HOSViolationType to API type.
 */
fun HOSViolationType.toApiType(): HosViolationTypeApi {
    return when (this) {
        HOSViolationType.DRIVE_TIME_EXCEEDED -> HosViolationTypeApi.DRIVING_11_HOUR
        HOSViolationType.SHIFT_TIME_EXCEEDED -> HosViolationTypeApi.SHIFT_14_HOUR
        HOSViolationType.BREAK_REQUIRED -> HosViolationTypeApi.BREAK_30_MIN
        HOSViolationType.CYCLE_TIME_EXCEEDED -> HosViolationTypeApi.CYCLE_70_HOUR_8_DAY
    }
}
