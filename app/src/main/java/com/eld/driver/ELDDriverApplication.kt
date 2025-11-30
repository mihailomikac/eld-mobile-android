package com.eld.driver

import android.app.Application
import android.util.Log
import com.eld.driver.ble.GeometrisWQManager
import com.eld.driver.data.api.ApiService
import com.eld.driver.data.models.DutyStatusChangeRequest
import com.eld.driver.data.models.DutyStatusType
import com.eld.driver.location.LocationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class for ELD Driver app
 * Handles global initialization and automatic duty status changes
 */
class ELDDriverApplication : Application() {

    companion object {
        private const val TAG = "ELDDriverApp"

        @Volatile
        private var authToken: String? = null

        /**
         * Set the auth token for automatic duty status changes
         * Call this after successful login
         */
        fun setAuthToken(token: String?) {
            authToken = token
            Log.d(TAG, "Auth token ${if (token != null) "set" else "cleared"}")
        }

        fun getAuthToken(): String? = authToken

        // Current vehicle ID for automatic status changes
        @Volatile
        private var currentVehicleId: Int? = null

        fun setCurrentVehicleId(vehicleId: Int?) {
            currentVehicleId = vehicleId
            Log.d(TAG, "Current vehicle ID ${if (vehicleId != null) "set to $vehicleId" else "cleared"}")
        }

        fun getCurrentVehicleId(): Int? = currentVehicleId

        // Callback for navigation to Dashboard when speed > 5mph
        var onNavigateToDashboard: (() -> Unit)? = null
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val apiService = ApiService.getInstance()
    private lateinit var locationService: LocationService
    private lateinit var bleManager: GeometrisWQManager

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ELDDriverApplication initialized")

        // Initialize BLE manager singleton
        bleManager = GeometrisWQManager.getInstance(this)

        // Initialize Location service
        locationService = LocationService.getInstance(this)

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
                    val currentLocation = locationService.getCurrentLocation()
                    val eldData = bleManager.eldData.value

                    Log.d(TAG, "📍 Location data:")
                    Log.d(TAG, "   Latitude: ${currentLocation?.latitude}")
                    Log.d(TAG, "   Longitude: ${currentLocation?.longitude}")
                    Log.d(TAG, "   Address: ${currentLocation?.address}")
                    Log.d(TAG, "📊 ELD data:")
                    Log.d(TAG, "   Odometer: ${eldData?.odometer}")
                    Log.d(TAG, "   Engine Hours: ${eldData?.engineHours}")

                    // Get current vehicle ID
                    val vehicleId = currentVehicleId
                    Log.d(TAG, "   Vehicle ID: $vehicleId")

                    val request = DutyStatusChangeRequest(
                        dutyStatus = dutyStatusType,
                        vehicleId = vehicleId,
                        deviceId = null,
                        latitude = currentLocation?.latitude,
                        longitude = currentLocation?.longitude,
                        location = currentLocation?.address,
                        odometer = eldData?.odometer,
                        engineHours = eldData?.engineHours,
                        note = reason,
                        shippingDocumentNumber = null,
                        trailerNumber = null
                    )

                    Log.d(TAG, "📤 Sending request: $request")

                    val response = apiService.changeDutyStatus(token, request)

                    Log.d(TAG, "📥 Response received:")
                    Log.d(TAG, "   HTTP Code: ${response.code()}")
                    Log.d(TAG, "   Success: ${response.isSuccessful}")
                    Log.d(TAG, "   Body: ${response.body()}")

                    if (response.isSuccessful && response.body()?.success == true) {
                        Log.d(TAG, "✅ SUCCESS! Status automatically changed to $dutyStatusType")
                    } else {
                        val error = response.body()?.error ?: "Failed with code ${response.code()}"
                        Log.e(TAG, "❌ FAILED to auto-change status: $error")
                        Log.e(TAG, "   Response body: ${response.body()}")
                        Log.e(TAG, "   Error body: ${response.errorBody()?.string()}")
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
    }
}
