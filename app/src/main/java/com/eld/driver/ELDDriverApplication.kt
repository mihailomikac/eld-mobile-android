package com.eld.driver

import android.app.Application
import android.util.Log
import com.eld.driver.ble.GeometrisWQManager
import com.eld.driver.data.api.ApiService
import com.eld.driver.data.local.ELDDatabase
import com.eld.driver.data.local.TokenManager
import com.eld.driver.data.local.USCitiesDataLoader
import com.eld.driver.data.models.DutyStatusChangeRequest
import com.eld.driver.data.models.DutyStatusType
import com.eld.driver.data.models.FmcsaEventRecordOrigin
import com.eld.driver.data.models.TickEventRequest
import com.eld.driver.data.models.TickEventType
import com.eld.driver.data.models.User
import com.eld.driver.hos.MalfunctionDetector
import com.eld.driver.location.LocationService
import com.eld.driver.service.SignalRConnectionState
import com.eld.driver.service.SignalRService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Application class for ELD Driver app
 * Handles global initialization and automatic duty status changes
 */
class ELDDriverApplication : Application() {

    companion object {
        private const val TAG = "ELDDriverApp"

        // In-memory cache (backed by TokenManager for persistence)
        @Volatile
        private var authToken: String? = null

        @Volatile
        private var currentVehicleId: Int? = null

        @Volatile
        private var currentEldMacAddress: String? = null

        @Volatile
        private var currentDeviceId: Int? = null

        @Volatile
        private var currentUser: User? = null

        private var tokenManager: TokenManager? = null

        // Reference to application instance for BLE disconnect
        @Volatile
        private var instance: ELDDriverApplication? = null

        /**
         * Disconnect from ELD device.
         * Should be called on logout.
         */
        fun disconnectELD() {
            instance?.bleManager?.disconnect()
            Log.d(TAG, "ELD disconnected")
        }

        /**
         * Set the auth token for automatic duty status changes
         * Saves to persistent storage
         */
        fun setAuthToken(token: String?) {
            authToken = token
            if (token != null) {
                tokenManager?.saveToken(token)
            } else {
                tokenManager?.clear()
            }
            Log.d(TAG, "Auth token ${if (token != null) "set" else "cleared"}")
        }

        fun getAuthToken(): String? {
            // First check in-memory cache
            if (authToken != null) return authToken

            // Try to restore from persistent storage
            val storedToken = tokenManager?.getToken()
            if (storedToken != null && tokenManager?.isTokenExpired() == false) {
                authToken = storedToken
                Log.d(TAG, "Auth token restored from storage")
                return storedToken
            }

            return null
        }

        /**
         * Set current vehicle ID, ELD MAC address, and device ID
         * Saves to persistent storage
         */
        fun setCurrentVehicle(vehicleId: Int?, eldMacAddress: String? = null, deviceId: Int? = null) {
            currentVehicleId = vehicleId
            currentEldMacAddress = eldMacAddress
            currentDeviceId = deviceId
            tokenManager?.saveVehicleId(vehicleId)
            tokenManager?.saveEldMacAddress(eldMacAddress)
            tokenManager?.saveDeviceId(deviceId)
            Log.d(TAG, "Current vehicle ID ${if (vehicleId != null) "set to $vehicleId" else "cleared"}, ELD MAC: $eldMacAddress, Device ID: $deviceId")
        }

        /**
         * Set current vehicle ID (legacy method for compatibility)
         */
        fun setCurrentVehicleId(vehicleId: Int?) {
            currentVehicleId = vehicleId
            tokenManager?.saveVehicleId(vehicleId)
            Log.d(TAG, "Current vehicle ID ${if (vehicleId != null) "set to $vehicleId" else "cleared"}")
        }

        fun getCurrentVehicleId(): Int? {
            // First check in-memory cache
            if (currentVehicleId != null) return currentVehicleId

            // Try to restore from storage
            val storedId = tokenManager?.getVehicleId()
            if (storedId != null) {
                currentVehicleId = storedId
                Log.d(TAG, "Vehicle ID restored from storage: $storedId")
            }
            return currentVehicleId
        }

        /**
         * Get the ELD MAC address for the current vehicle.
         * Used for direct BLE connection without scanning.
         */
        fun getCurrentEldMacAddress(): String? {
            if (currentEldMacAddress != null) return currentEldMacAddress

            val storedMac = tokenManager?.getEldMacAddress()
            if (storedMac != null) {
                currentEldMacAddress = storedMac
                Log.d(TAG, "ELD MAC address restored from storage: $storedMac")
            }
            return currentEldMacAddress
        }

        /**
         * Get the ELD device ID for the current vehicle.
         * Used in all API requests that require deviceId.
         */
        fun getCurrentDeviceId(): Int? {
            if (currentDeviceId != null) return currentDeviceId

            val storedDeviceId = tokenManager?.getDeviceId()
            if (storedDeviceId != null) {
                currentDeviceId = storedDeviceId
                Log.d(TAG, "Device ID restored from storage: $storedDeviceId")
            }
            return currentDeviceId
        }

        /**
         * Set current user
         * Saves to persistent storage
         */
        fun setCurrentUser(user: User?) {
            currentUser = user
            if (user != null) {
                tokenManager?.saveUser(user)
            }
        }

        fun getCurrentUser(): User? {
            if (currentUser != null) return currentUser

            val storedUser = tokenManager?.getUser()
            if (storedUser != null) {
                currentUser = storedUser
                Log.d(TAG, "User restored from storage: ${storedUser.email}")
            }
            return currentUser
        }

        /**
         * Get BLE manager instance for ELD data access
         */
        fun getBleManager(): GeometrisWQManager? {
            return instance?.bleManager
        }

        /**
         * Check if user is logged in with valid token
         */
        fun isLoggedIn(): Boolean {
            return tokenManager?.isLoggedIn() == true
        }

        /**
         * Clear all session data and disconnect from ELD and SignalR.
         * Should be called on logout.
         */
        fun clearSession() {
            // Disconnect from ELD device first
            disconnectELD()

            // Disconnect from SignalR
            disconnectSignalR()

            authToken = null
            currentVehicleId = null
            currentEldMacAddress = null
            currentDeviceId = null
            currentUser = null
            tokenManager?.clear()
            Log.d(TAG, "Session cleared (ELD and SignalR disconnected)")
        }

        /**
         * Add email to recent logins list
         */
        fun addRecentEmail(email: String) {
            tokenManager?.addRecentEmail(email)
        }

        /**
         * Get recent login emails for shortcuts
         */
        fun getRecentEmails(): List<String> {
            return tokenManager?.getRecentEmails() ?: emptyList()
        }

        // Callback for navigation to Dashboard when speed > 5mph
        var onNavigateToDashboard: (() -> Unit)? = null

        // Callback for force logout (SignalR notification from another device login)
        var onForceLogout: ((reason: String) -> Unit)? = null

        /**
         * Get SignalR service instance
         */
        fun getSignalRService(): SignalRService? {
            return instance?.signalRService
        }

        /**
         * Connect to SignalR hub for real-time notifications.
         * Call this after successful login.
         * NOTE: This is fire-and-forget. Use connectSignalRAndWait() if you need to wait.
         */
        fun connectSignalR() {
            val token = authToken
            if (token != null) {
                instance?.applicationScope?.launch(Dispatchers.IO) {
                    try {
                        instance?.signalRService?.connect(token)
                        Log.d(TAG, "✅ SignalR connected")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to connect SignalR: ${e.message}", e)
                    }
                }
            } else {
                Log.w(TAG, "⚠️ Cannot connect SignalR: No auth token")
            }
        }

        /**
         * Connect to SignalR and wait for connection to be established.
         * This ensures the backend has our connectionId before we proceed.
         * @param timeoutMs Maximum time to wait for connection (default 5 seconds)
         * @return true if connected successfully, false if timeout or error
         */
        suspend fun connectSignalRAndWait(timeoutMs: Long = 5000): Boolean {
            val token = authToken
            if (token == null) {
                Log.w(TAG, "⚠️ Cannot connect SignalR: No auth token")
                return false
            }

            return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    Log.d(TAG, "📡 Connecting to SignalR (waiting for connection)...")
                    instance?.signalRService?.connect(token)

                    // Wait for connection state to become CONNECTED
                    val startTime = System.currentTimeMillis()
                    while (System.currentTimeMillis() - startTime < timeoutMs) {
                        val state = instance?.signalRService?.connectionState?.value
                        if (state == SignalRConnectionState.CONNECTED) {
                            Log.d(TAG, "✅ SignalR connected and ready!")
                            return@withContext true
                        }
                        if (state == SignalRConnectionState.ERROR) {
                            Log.e(TAG, "❌ SignalR connection failed")
                            return@withContext false
                        }
                        kotlinx.coroutines.delay(100) // Check every 100ms
                    }
                    Log.w(TAG, "⚠️ SignalR connection timeout after ${timeoutMs}ms")
                    false
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to connect SignalR: ${e.message}", e)
                    false
                }
            }
        }

        /**
         * Disconnect from SignalR hub.
         * Call this on logout.
         */
        fun disconnectSignalR() {
            instance?.applicationScope?.launch(Dispatchers.IO) {
                try {
                    instance?.signalRService?.disconnect()
                    Log.d(TAG, "✅ SignalR disconnected")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Error disconnecting SignalR: ${e.message}")
                }
            }
        }
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val apiService = ApiService.getInstance()
    private lateinit var locationService: LocationService
    private lateinit var bleManager: GeometrisWQManager
    private lateinit var signalRService: SignalRService

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ELDDriverApplication initialized")

        // Set instance for static access (needed for BLE disconnect on logout)
        instance = this

        // Initialize TokenManager FIRST (before anything else)
        try {
            tokenManager = TokenManager.getInstance(this)

            // Check if user was previously logged in
            if (isLoggedIn()) {
                Log.d(TAG, "✅ User session restored from storage")
                // Restore in-memory cache from storage
                authToken = tokenManager?.getToken()
                currentVehicleId = tokenManager?.getVehicleId()
                currentEldMacAddress = tokenManager?.getEldMacAddress()
                currentDeviceId = tokenManager?.getDeviceId()
                currentUser = tokenManager?.getUser()
                Log.d(TAG, "   Token: ${authToken?.take(30)}...")
                Log.d(TAG, "   Vehicle ID: $currentVehicleId")
                Log.d(TAG, "   ELD MAC: $currentEldMacAddress")
                Log.d(TAG, "   Device ID: $currentDeviceId")
                Log.d(TAG, "   User: ${currentUser?.email}")
            } else {
                Log.d(TAG, "No valid session found - clearing local data")
                // Only clear data if not logged in (fresh start or expired token)
                clearAllLocalDataOnStart()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TokenManager: ${e.message}", e)
            // Continue without session restore - user will need to login
            clearAllLocalDataOnStart()
        }

        // Initialize BLE manager singleton
        bleManager = GeometrisWQManager.getInstance(this)

        // Initialize Location service
        locationService = LocationService.getInstance(this)

        // Initialize SignalR service for real-time notifications
        signalRService = SignalRService.getInstance(this)

        // Set up SignalR force logout handler
        applicationScope.launch {
            signalRService.forceLogoutEvent.collect { event ->
                if (event != null) {
                    Log.w(TAG, "!!! FORCE LOGOUT RECEIVED !!!")
                    Log.w(TAG, "Reason: ${event.reason}")

                    // IMMEDIATELY disconnect SignalR to free up the WebSocket connection
                    // This prevents "maximum concurrent WebSocket requests" errors on Azure
                    Log.w(TAG, "📡 Disconnecting SignalR immediately to free resources...")
                    signalRService.disconnect()

                    // Notify UI layer to handle logout (show dialog)
                    onForceLogout?.invoke(event.reason)

                    // Clear the event so it doesn't trigger again
                    signalRService.clearForceLogoutEvent()
                }
            }
        }

        // If user is logged in, connect to SignalR
        if (isLoggedIn() && authToken != null) {
            Log.d(TAG, "📡 Restoring SignalR connection...")
            connectSignalR()
        }

        // Load US cities database for FMCSA-compliant location formatting
        loadUSCitiesDatabase()

        // Set up global automatic duty status change callback
        bleManager.onAutoStatusChangeNeeded = callback@{ suggestedStatus, reason ->
            Log.d(TAG, "════════════════════════════════════════════════════════")
            Log.d(TAG, "🤖 AUTO STATUS CHANGE TRIGGERED")
            Log.d(TAG, "   Suggested Status: $suggestedStatus")
            Log.d(TAG, "   Reason: $reason")
            Log.d(TAG, "════════════════════════════════════════════════════════")

            // Get current auth token
            val token = authToken
            if (token == null) {
                Log.w(TAG, "⚠️ Cannot auto-change status: No auth token available")
                Log.w(TAG, "   authToken is NULL - user not logged in?")
                return@callback
            }
            Log.d(TAG, "✓ Auth token available: ${token.take(20)}...")

            // Map suggested status to DutyStatusType
            val dutyStatusType = when (suggestedStatus) {
                "DRIVING" -> DutyStatusType.DRIVING
                "ON_DUTY" -> DutyStatusType.ON_DUTY_NOT_DRIVING
                else -> {
                    Log.w(TAG, "❌ Unknown suggested status: $suggestedStatus")
                    Log.w(TAG, "   Valid values are: DRIVING, ON_DUTY")
                    return@callback
                }
            }

            Log.d(TAG, "🔄 Mapped to DutyStatusType: $dutyStatusType")

            // If switching to DRIVING, navigate to Dashboard
            if (dutyStatusType == DutyStatusType.DRIVING) {
                Log.d(TAG, "🚗 Vehicle started moving - navigating to Dashboard")
                onNavigateToDashboard?.invoke()
            }

            // Call API to change status
            Log.d(TAG, "📡 Calling API to change duty status...")
            applicationScope.launch {
                try {
                    // Get current location from BLE device or GPS
                    // Use getLocationWithFallback() which WAITS for location - essential for Huawei phones
                    val currentLocation = locationService.getLocationWithFallback(timeoutMs = 5000)
                    val eldData = bleManager.eldData.value

                    // Use FMCSA-compliant location format if we have coordinates
                    // Format: "{X} miles {direction} of {city}, {state}"
                    val fmcsaLocation = if (currentLocation != null) {
                        locationService.getFMCSALocation(currentLocation.latitude, currentLocation.longitude)
                    } else null

                    // Convert odometer from kilometers to miles (ELD sends km, backend expects miles)
                    val odometerMiles = eldData?.odometer?.let { it * 0.621371 }

                    Log.d(TAG, "📍 Location data:")
                    Log.d(TAG, "   Latitude: ${currentLocation?.latitude}")
                    Log.d(TAG, "   Longitude: ${currentLocation?.longitude}")
                    Log.d(TAG, "   FMCSA Location: $fmcsaLocation")
                    Log.d(TAG, "📊 ELD data:")
                    Log.d(TAG, "   Odometer: ${eldData?.odometer} km = $odometerMiles miles")
                    Log.d(TAG, "   Engine Hours: ${eldData?.engineHours}")

                    // Get current vehicle ID
                    val vehicleId = currentVehicleId
                    Log.d(TAG, "   Vehicle ID: $vehicleId")

                    val request = DutyStatusChangeRequest(
                        dutyStatus = dutyStatusType,
                        vehicleId = vehicleId,
                        deviceId = currentDeviceId,
                        latitude = currentLocation?.latitude,
                        longitude = currentLocation?.longitude,
                        location = fmcsaLocation,
                        odometer = odometerMiles,
                        engineHours = eldData?.engineHours,
                        note = reason,
                        shippingDocumentNumber = null,
                        trailerNumber = null,
                        // FMCSA: Origin=1 (Auto by ELD) for automatic status changes
                        eventRecordOrigin = FmcsaEventRecordOrigin.AUTO_BY_ELD
                    )

                    Log.d(TAG, "📤 Sending request via repository (offline-first): $request")

                    // Use repository for offline-first approach:
                    // 1. Saves to local database immediately (UI updates via Flow)
                    // 2. Queues for sync to server
                    val repository = com.eld.driver.data.repository.ELDRepository.getInstance(this@ELDDriverApplication)
                    val result = repository.changeDutyStatus(request)

                    if (result.isSuccess) {
                        Log.d(TAG, "✅ SUCCESS! Status automatically changed to $dutyStatusType (saved locally, queued for sync)")
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Unknown error"
                        Log.e(TAG, "❌ FAILED to auto-change status: $error")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ EXCEPTION during auto-change status:")
                    Log.e(TAG, "   Message: ${e.message}")
                    Log.e(TAG, "   Stack trace:", e)
                }
                Log.d(TAG, "════════════════════════════════════════════════════════")
            }
        }

        Log.d(TAG, "Global automatic duty status change handler initialized")

        // Set up ELD connection tick event handlers (offline-first)
        bleManager.onEldConnected = {
            Log.d(TAG, "════════════════════════════════════════════════════════")
            Log.d(TAG, "🔌 ELD CONNECTED - Sending tick event")
            Log.d(TAG, "════════════════════════════════════════════════════════")

            applicationScope.launch {
                try {
                    val token = authToken
                    if (token == null) {
                        Log.w(TAG, "⚠️ Cannot send CONNECTED tick: No auth token")
                        return@launch
                    }

                    val currentLocation = locationService.getCurrentLocation()
                    val fmcsaLocation = if (currentLocation != null) {
                        locationService.getFMCSALocation(currentLocation.latitude, currentLocation.longitude)
                    } else null

                    val eldData = bleManager.eldData.value
                    val odometerMiles = eldData?.odometer?.let { it * 0.621371 }

                    // Create ISO 8601 timestamp
                    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                    isoFormat.timeZone = TimeZone.getTimeZone("UTC")
                    val timestamp = isoFormat.format(Date())

                    val request = TickEventRequest(
                        eventType = TickEventType.CONNECTED,
                        vehicleId = currentVehicleId,
                        deviceId = currentDeviceId,
                        latitude = currentLocation?.latitude,
                        longitude = currentLocation?.longitude,
                        location = fmcsaLocation,
                        odometer = odometerMiles,
                        engineHours = eldData?.engineHours,
                        timestamp = timestamp
                    )

                    val repository = com.eld.driver.data.repository.ELDRepository.getInstance(this@ELDDriverApplication)
                    repository.createTickEvent(request)
                    Log.d(TAG, "✅ CONNECTED tick event saved (offline-first)")

                    // Clear power malfunction/diagnostic when ELD reconnects
                    val malfunctionDetector = MalfunctionDetector.getInstance(this@ELDDriverApplication)
                    malfunctionDetector.onPowerRestored()
                    malfunctionDetector.start()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to send CONNECTED tick event: ${e.message}", e)
                }
            }
        }

        bleManager.onEldDisconnected = { isManual ->
            Log.d(TAG, "════════════════════════════════════════════════════════")
            Log.d(TAG, "🔌 ELD DISCONNECTED - Sending tick event (manual: $isManual)")
            Log.d(TAG, "════════════════════════════════════════════════════════")

            applicationScope.launch {
                try {
                    val token = authToken
                    if (token == null) {
                        Log.w(TAG, "⚠️ Cannot send DISCONNECTED tick: No auth token")
                        return@launch
                    }

                    val currentLocation = locationService.getCurrentLocation()
                    val fmcsaLocation = if (currentLocation != null) {
                        locationService.getFMCSALocation(currentLocation.latitude, currentLocation.longitude)
                    } else null

                    val eldData = bleManager.eldData.value
                    val odometerMiles = eldData?.odometer?.let { it * 0.621371 }

                    // Create ISO 8601 timestamp
                    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                    isoFormat.timeZone = TimeZone.getTimeZone("UTC")
                    val timestamp = isoFormat.format(Date())

                    val request = TickEventRequest(
                        eventType = TickEventType.DISCONNECTED,
                        vehicleId = currentVehicleId,
                        deviceId = currentDeviceId,
                        latitude = currentLocation?.latitude,
                        longitude = currentLocation?.longitude,
                        location = fmcsaLocation,
                        odometer = odometerMiles,
                        engineHours = eldData?.engineHours,
                        timestamp = timestamp
                    )

                    val repository = com.eld.driver.data.repository.ELDRepository.getInstance(this@ELDDriverApplication)
                    repository.createTickEvent(request)
                    Log.d(TAG, "✅ DISCONNECTED tick event saved (offline-first)")

                    // Only trigger power diagnostic for unexpected disconnects (signal loss)
                    // Manual disconnects are intentional and should NOT trigger power diagnostic
                    val malfunctionDetector = MalfunctionDetector.getInstance(this@ELDDriverApplication)
                    if (!isManual) {
                        Log.d(TAG, "⚠️ Signal lost - triggering power diagnostic")
                        malfunctionDetector.onPowerLost()
                    } else {
                        Log.d(TAG, "✅ Manual disconnect - no power diagnostic")
                    }
                    malfunctionDetector.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to send DISCONNECTED tick event: ${e.message}", e)
                }
            }
        }

        Log.d(TAG, "ELD connection tick event handlers initialized")
    }

    /**
     * Clear all local database data on app start.
     * This ensures a clean state - user must login to get fresh data from server.
     */
    private fun clearAllLocalDataOnStart() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "🗑️ Clearing all local data on app start...")
                val db = ELDDatabase.getInstance(this@ELDDriverApplication)

                // Clear all tables
                db.dutyStatusEventDao().deleteAll()
                db.tickEventDao().deleteAll()
                db.hosStatusDao().deleteHOSStatus()
                db.syncQueueDao().clearQueue()

                Log.d(TAG, "✅ All local data cleared on app start")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to clear local data on start: ${e.message}", e)
            }
        }
    }

    /**
     * Load US cities database for FMCSA-compliant location formatting.
     * Runs in background - does not block app startup.
     */
    private fun loadUSCitiesDatabase() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "📍 Loading US cities database...")
                val loader = USCitiesDataLoader(this@ELDDriverApplication)
                loader.loadCitiesIfNeeded()
                val stats = loader.getStats()
                Log.d(TAG, "✅ US cities database ready: ${stats.totalCities} cities loaded")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to load US cities database: ${e.message}", e)
            }
        }
    }
}
