package com.eld.driver.hos

import android.content.Context
import android.util.Log
import com.eld.driver.ble.GeometrisWQManager
import com.eld.driver.data.models.TickEventRequest
import com.eld.driver.data.models.TickEventType
import com.eld.driver.data.repository.ELDRepository
import com.eld.driver.location.LocationService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * MalfunctionDetector monitors ELD device for FMCSA-required malfunctions and diagnostics.
 *
 * FMCSA 49 CFR 395.34 requires monitoring for:
 *
 * MALFUNCTIONS (Critical - driver must use paper logs):
 * - P: Power compliance malfunction (>30 min without power in 24h)
 * - E: Engine synchronization malfunction (>30 min without ECM in 24h)
 * - T: Timing compliance malfunction (>10 min clock drift from UTC)
 * - L: Positioning compliance malfunction (>60 min without GPS during driving)
 * - R: Data recording compliance malfunction (cannot store data)
 * - S: Data transfer compliance malfunction (cannot transfer data)
 * - O: Other ELD malfunction
 *
 * DIAGNOSTICS (Warning - can continue, must report within 24h):
 * - 1: Power data diagnostic (brief power loss)
 * - 2: Engine synchronization diagnostic (brief ECM data loss)
 * - 3: Missing required data elements diagnostic (GPS signal weak)
 * - 4: Data transfer diagnostic (upload failed)
 * - 5: Unidentified driving records diagnostic (>30 min driving without logged driver)
 * - 6: Other ELD diagnostic
 */
class MalfunctionDetector private constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "MalfunctionDetector"

        // FMCSA timing thresholds for DIAGNOSTICS (shorter - warnings)
        private const val DIAGNOSTIC_POWER_THRESHOLD_MS = 5_000L        // 5 seconds power loss
        private const val DIAGNOSTIC_ENGINE_SYNC_THRESHOLD_MS = 5_000L  // 5 seconds no ECM
        private const val DIAGNOSTIC_GPS_THRESHOLD_MS = 60_000L         // 1 minute no GPS
        private const val DIAGNOSTIC_UNIDENTIFIED_DRIVING_MS = 30 * 60_000L  // 30 min unidentified

        // FMCSA timing thresholds for MALFUNCTIONS (longer - critical)
        private const val MALFUNCTION_POWER_THRESHOLD_MS = 30 * 60_000L      // 30 min in 24h
        private const val MALFUNCTION_ENGINE_SYNC_THRESHOLD_MS = 30 * 60_000L // 30 min in 24h
        private const val MALFUNCTION_TIMING_THRESHOLD_MS = 10 * 60_000L      // 10 min clock drift
        private const val MALFUNCTION_GPS_THRESHOLD_MS = 60 * 60_000L         // 60 min during driving

        // Check intervals
        private const val CHECK_INTERVAL_MS = 60_000L  // Check every minute

        @Volatile
        private var INSTANCE: MalfunctionDetector? = null

        fun getInstance(context: Context): MalfunctionDetector {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MalfunctionDetector(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val repository = ELDRepository.getInstance(context)
    private val locationService = LocationService.getInstance(context)
    private val bleManager = GeometrisWQManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Active malfunctions and diagnostics
    private val _activeMalfunctions = MutableStateFlow<Set<TickEventType>>(emptySet())
    val activeMalfunctions: StateFlow<Set<TickEventType>> = _activeMalfunctions.asStateFlow()

    private val _activeDiagnostics = MutableStateFlow<Set<TickEventType>>(emptySet())
    val activeDiagnostics: StateFlow<Set<TickEventType>> = _activeDiagnostics.asStateFlow()

    // Tracking timestamps
    private var lastValidLatitude: Double? = null
    private var lastValidLongitude: Double? = null
    private var lastGpsAcquisitionTime: Long = System.currentTimeMillis()
    private var gpsLostTime: Long? = null

    private var engineStartTime: Long? = null
    private var hasReceivedEcmData: Boolean = false
    private var ecmLostTime: Long? = null

    private var powerLostTime: Long? = null
    private var totalPowerLossIn24h: Long = 0L
    private var totalEcmLossIn24h: Long = 0L

    // Unidentified driving tracking
    private var unidentifiedDrivingStartTime: Long? = null

    // Running state
    private var checkJob: Job? = null
    private var isRunning = false
    private var isDriving = false

    /**
     * Start malfunction detection.
     */
    fun start() {
        if (isRunning) return
        isRunning = true

        Log.d(TAG, "Starting malfunction/diagnostic detection")

        checkJob = scope.launch {
            while (isActive) {
                checkForIssues()
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    /**
     * Stop malfunction detection.
     */
    fun stop() {
        isRunning = false
        checkJob?.cancel()
        checkJob = null
        Log.d(TAG, "Stopped malfunction/diagnostic detection")
    }

    /**
     * Set driving state (affects GPS malfunction thresholds).
     */
    fun setDrivingState(driving: Boolean) {
        isDriving = driving
    }

    /**
     * Called when engine starts (ELD power up).
     */
    fun onEngineStart() {
        engineStartTime = System.currentTimeMillis()
        hasReceivedEcmData = false
        Log.d(TAG, "Engine start detected, waiting for ECM data")
    }

    /**
     * Called when ECM data is received from ELD.
     */
    fun onEcmDataReceived() {
        val wasLost = !hasReceivedEcmData
        hasReceivedEcmData = true

        // Clear ECM loss tracking
        ecmLostTime?.let { lostTime ->
            val lostDuration = System.currentTimeMillis() - lostTime
            totalEcmLossIn24h += lostDuration
        }
        ecmLostTime = null

        if (wasLost) {
            Log.d(TAG, "ECM data received - engine sync OK")
            scope.launch {
                clearDiagnostic(TickEventType.DIAGNOSTIC_ENGINE_SYNC)
                clearMalfunction(TickEventType.MALFUNCTION_ENGINE_SYNC)
            }
        }
    }

    /**
     * Called when ECM data is lost.
     */
    fun onEcmDataLost() {
        if (ecmLostTime == null) {
            ecmLostTime = System.currentTimeMillis()
            hasReceivedEcmData = false
            Log.w(TAG, "ECM data lost")
        }
    }

    /**
     * Called when GPS position is acquired.
     */
    fun onGpsPositionAcquired(latitude: Double, longitude: Double) {
        lastGpsAcquisitionTime = System.currentTimeMillis()
        gpsLostTime = null

        lastValidLatitude = latitude
        lastValidLongitude = longitude

        // Clear GPS-related issues
        scope.launch {
            clearDiagnostic(TickEventType.DIAGNOSTIC_MISSING_DATA)
            clearMalfunction(TickEventType.MALFUNCTION_POSITIONING)
        }
    }

    /**
     * Called when GPS signal is lost.
     */
    fun onGpsLost() {
        if (gpsLostTime == null) {
            gpsLostTime = System.currentTimeMillis()
            Log.w(TAG, "GPS signal lost")
        }
    }

    /**
     * Called when power is restored after loss.
     */
    fun onPowerRestored() {
        powerLostTime?.let { lostTime ->
            val lostDuration = System.currentTimeMillis() - lostTime
            totalPowerLossIn24h += lostDuration
        }
        powerLostTime = null

        scope.launch {
            clearDiagnostic(TickEventType.DIAGNOSTIC_POWER)
            clearMalfunction(TickEventType.MALFUNCTION_POWER)
        }
    }

    /**
     * Called when power is lost.
     */
    fun onPowerLost() {
        if (powerLostTime == null) {
            powerLostTime = System.currentTimeMillis()
            Log.w(TAG, "ELD power lost")
        }
    }

    /**
     * Called when unidentified driving is detected.
     */
    fun onUnidentifiedDrivingStart() {
        if (unidentifiedDrivingStartTime == null) {
            unidentifiedDrivingStartTime = System.currentTimeMillis()
            Log.w(TAG, "Unidentified driving started")
        }
    }

    /**
     * Called when driver logs in during unidentified driving.
     */
    fun onUnidentifiedDrivingEnd() {
        unidentifiedDrivingStartTime = null
        scope.launch {
            clearDiagnostic(TickEventType.DIAGNOSTIC_UNIDENTIFIED_DRIVING)
        }
    }

    /**
     * Called when data transfer succeeds.
     */
    fun onDataTransferSuccess() {
        scope.launch {
            clearDiagnostic(TickEventType.DIAGNOSTIC_DATA_TRANSFER)
            clearMalfunction(TickEventType.MALFUNCTION_DATA_TRANSFER)
        }
    }

    /**
     * Called when data transfer fails.
     */
    fun onDataTransferFailed(consecutiveFailures: Int) {
        scope.launch {
            // First failure = diagnostic, multiple = malfunction
            if (consecutiveFailures >= 3) {
                triggerMalfunction(TickEventType.MALFUNCTION_DATA_TRANSFER, "Data transfer failed $consecutiveFailures times")
            } else {
                triggerDiagnostic(TickEventType.DIAGNOSTIC_DATA_TRANSFER, "Data transfer failed")
            }
        }
    }

    /**
     * Check for all types of issues.
     */
    private suspend fun checkForIssues() {
        checkPowerIssues()
        checkEngineSyncIssues()
        checkPositioningIssues()
        checkUnidentifiedDriving()
    }

    /**
     * Check for power-related diagnostics/malfunctions.
     */
    private suspend fun checkPowerIssues() {
        val lostTime = powerLostTime ?: return
        val elapsed = System.currentTimeMillis() - lostTime

        when {
            elapsed >= MALFUNCTION_POWER_THRESHOLD_MS -> {
                triggerMalfunction(TickEventType.MALFUNCTION_POWER, "Power lost for ${elapsed / 60000} minutes")
            }
            elapsed >= DIAGNOSTIC_POWER_THRESHOLD_MS -> {
                triggerDiagnostic(TickEventType.DIAGNOSTIC_POWER, "Power lost for ${elapsed / 1000} seconds")
            }
        }
    }

    /**
     * Check for engine sync diagnostics/malfunctions.
     */
    private suspend fun checkEngineSyncIssues() {
        val lostTime = ecmLostTime ?: return
        val elapsed = System.currentTimeMillis() - lostTime

        when {
            totalEcmLossIn24h + elapsed >= MALFUNCTION_ENGINE_SYNC_THRESHOLD_MS -> {
                triggerMalfunction(TickEventType.MALFUNCTION_ENGINE_SYNC, "ECM data lost for 30+ minutes in 24h")
            }
            elapsed >= DIAGNOSTIC_ENGINE_SYNC_THRESHOLD_MS -> {
                triggerDiagnostic(TickEventType.DIAGNOSTIC_ENGINE_SYNC, "ECM data lost")
            }
        }
    }

    /**
     * Check for positioning diagnostics/malfunctions.
     */
    private suspend fun checkPositioningIssues() {
        val currentLocation = locationService.getCurrentLocation()

        if (currentLocation != null) {
            onGpsPositionAcquired(currentLocation.latitude, currentLocation.longitude)
            return
        }

        val lostTime = gpsLostTime ?: run {
            gpsLostTime = System.currentTimeMillis()
            return
        }
        val elapsed = System.currentTimeMillis() - lostTime

        when {
            // Malfunction only triggers during driving
            isDriving && elapsed >= MALFUNCTION_GPS_THRESHOLD_MS -> {
                triggerMalfunction(TickEventType.MALFUNCTION_POSITIONING, "GPS unavailable for 60+ minutes during driving")
            }
            elapsed >= DIAGNOSTIC_GPS_THRESHOLD_MS -> {
                triggerDiagnostic(TickEventType.DIAGNOSTIC_MISSING_DATA, "GPS signal weak/unavailable")
            }
        }
    }

    /**
     * Check for unidentified driving diagnostic.
     */
    private suspend fun checkUnidentifiedDriving() {
        val startTime = unidentifiedDrivingStartTime ?: return
        val elapsed = System.currentTimeMillis() - startTime

        if (elapsed >= DIAGNOSTIC_UNIDENTIFIED_DRIVING_MS) {
            triggerDiagnostic(
                TickEventType.DIAGNOSTIC_UNIDENTIFIED_DRIVING,
                "Unidentified driving for ${elapsed / 60000} minutes"
            )
        }
    }

    /**
     * Trigger a malfunction event.
     */
    private suspend fun triggerMalfunction(type: TickEventType, reason: String) {
        val currentMalfunctions = _activeMalfunctions.value
        if (type in currentMalfunctions) return

        Log.w(TAG, "⚠️ MALFUNCTION DETECTED: $type - $reason")
        _activeMalfunctions.value = currentMalfunctions + type

        sendEvent(type, "Malfunction: $reason")
    }

    /**
     * Trigger a diagnostic event.
     */
    private suspend fun triggerDiagnostic(type: TickEventType, reason: String) {
        val currentDiagnostics = _activeDiagnostics.value
        if (type in currentDiagnostics) return

        Log.w(TAG, "📋 DIAGNOSTIC DETECTED: $type - $reason")
        _activeDiagnostics.value = currentDiagnostics + type

        sendEvent(type, "Diagnostic: $reason")
    }

    /**
     * Clear a malfunction.
     */
    private suspend fun clearMalfunction(type: TickEventType) {
        if (type !in _activeMalfunctions.value) return

        Log.d(TAG, "✅ MALFUNCTION CLEARED: $type")
        _activeMalfunctions.value = _activeMalfunctions.value - type

        val clearedType = getClearedType(type)
        if (clearedType != null) {
            sendEvent(clearedType, "Malfunction cleared")
        }
    }

    /**
     * Clear a diagnostic.
     */
    private suspend fun clearDiagnostic(type: TickEventType) {
        if (type !in _activeDiagnostics.value) return

        Log.d(TAG, "✅ DIAGNOSTIC CLEARED: $type")
        _activeDiagnostics.value = _activeDiagnostics.value - type

        val clearedType = getClearedType(type)
        if (clearedType != null) {
            sendEvent(clearedType, "Diagnostic cleared")
        }
    }

    /**
     * Get the corresponding CLEARED event type.
     */
    private fun getClearedType(type: TickEventType): TickEventType? {
        return when (type) {
            // Malfunctions
            TickEventType.MALFUNCTION_POWER -> TickEventType.MALFUNCTION_POWER_CLEARED
            TickEventType.MALFUNCTION_ENGINE_SYNC -> TickEventType.MALFUNCTION_ENGINE_SYNC_CLEARED
            TickEventType.MALFUNCTION_TIMING -> TickEventType.MALFUNCTION_TIMING_CLEARED
            TickEventType.MALFUNCTION_POSITIONING -> TickEventType.MALFUNCTION_POSITIONING_CLEARED
            TickEventType.MALFUNCTION_DATA_RECORDING -> TickEventType.MALFUNCTION_DATA_RECORDING_CLEARED
            TickEventType.MALFUNCTION_DATA_TRANSFER -> TickEventType.MALFUNCTION_DATA_TRANSFER_CLEARED
            TickEventType.MALFUNCTION_OTHER -> TickEventType.MALFUNCTION_OTHER_CLEARED
            // Diagnostics
            TickEventType.DIAGNOSTIC_POWER -> TickEventType.DIAGNOSTIC_POWER_CLEARED
            TickEventType.DIAGNOSTIC_ENGINE_SYNC -> TickEventType.DIAGNOSTIC_ENGINE_SYNC_CLEARED
            TickEventType.DIAGNOSTIC_MISSING_DATA -> TickEventType.DIAGNOSTIC_MISSING_DATA_CLEARED
            TickEventType.DIAGNOSTIC_DATA_TRANSFER -> TickEventType.DIAGNOSTIC_DATA_TRANSFER_CLEARED
            TickEventType.DIAGNOSTIC_UNIDENTIFIED_DRIVING -> TickEventType.DIAGNOSTIC_UNIDENTIFIED_DRIVING_CLEARED
            TickEventType.DIAGNOSTIC_OTHER -> TickEventType.DIAGNOSTIC_OTHER_CLEARED
            else -> null
        }
    }

    /**
     * Send an event to the repository.
     */
    private suspend fun sendEvent(type: TickEventType, note: String) {
        val currentLocation = locationService.getCurrentLocation()
        val fmcsaLocation = if (currentLocation != null) {
            locationService.getFMCSALocation(currentLocation.latitude, currentLocation.longitude)
        } else null

        // Get ELD telemetry data
        val eldData = bleManager.eldData.value
        val odometerMiles = eldData?.odometer?.let { it * 0.621371 }

        // Create ISO 8601 timestamp
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        isoFormat.timeZone = TimeZone.getTimeZone("UTC")
        val timestamp = isoFormat.format(Date())

        val request = TickEventRequest(
            eventType = type,
            vehicleId = com.eld.driver.ELDDriverApplication.getCurrentVehicleId(),
            deviceId = com.eld.driver.ELDDriverApplication.getCurrentDeviceId(),
            latitude = currentLocation?.latitude,
            longitude = currentLocation?.longitude,
            location = fmcsaLocation,
            odometer = odometerMiles,
            engineHours = eldData?.engineHours,
            note = note,
            timestamp = timestamp
        )

        repository.createTickEvent(request)
    }

    /**
     * Check if there are any active malfunctions.
     */
    fun hasMalfunctions(): Boolean = _activeMalfunctions.value.isNotEmpty()

    /**
     * Check if there are any active diagnostics.
     */
    fun hasDiagnostics(): Boolean = _activeDiagnostics.value.isNotEmpty()

    /**
     * Reset 24-hour counters (call at midnight).
     */
    fun reset24HourCounters() {
        totalPowerLossIn24h = 0L
        totalEcmLossIn24h = 0L
        Log.d(TAG, "Reset 24-hour malfunction counters")
    }

    /**
     * Calculate distance between two coordinates in miles.
     */
    private fun calculateDistanceMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusMiles = 3958.8
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadiusMiles * c
    }

    /**
     * Cleanup resources.
     */
    fun cleanup() {
        stop()
        scope.cancel()
    }
}
