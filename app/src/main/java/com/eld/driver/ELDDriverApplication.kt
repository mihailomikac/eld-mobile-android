package com.eld.driver

import android.app.Application
import android.util.Log
import com.eld.driver.ble.GeometrisWQManager
import com.eld.driver.data.api.ApiService
import com.eld.driver.data.models.DutyStatusChangeRequest
import com.eld.driver.data.models.DutyStatusType
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
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val apiService = ApiService.getInstance()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ELDDriverApplication initialized")

        // Initialize BLE manager singleton
        val bleManager = GeometrisWQManager.getInstance(this)

        // Set up global automatic duty status change callback
        bleManager.onAutoStatusChangeNeeded = callback@{ suggestedStatus, reason ->
            Log.d(TAG, "🤖 Auto-change suggested: $suggestedStatus - $reason")

            // Get current auth token
            val token = authToken
            if (token == null) {
                Log.w(TAG, "⚠️ Cannot auto-change status: No auth token available")
                return@callback
            }

            // Map suggested status to DutyStatusType
            val dutyStatusType = when (suggestedStatus) {
                "DRIVING" -> DutyStatusType.DRIVING
                "ON_DUTY" -> DutyStatusType.ON_DUTY_NOT_DRIVING
                else -> {
                    Log.w(TAG, "Unknown suggested status: $suggestedStatus")
                    return@callback
                }
            }

            Log.d(TAG, "🔄 Automatically changing duty status to: $dutyStatusType")

            // Call API to change status
            applicationScope.launch {
                try {
                    val request = DutyStatusChangeRequest(
                        dutyStatus = dutyStatusType,
                        vehicleId = null,
                        deviceId = null,
                        latitude = null,
                        longitude = null,
                        location = null,
                        odometer = null,
                        engineHours = null,
                        note = reason,
                        shippingDocumentNumber = null,
                        trailerNumber = null
                    )

                    val response = apiService.changeDutyStatus(token, request)

                    if (response.isSuccessful && response.body()?.success == true) {
                        Log.d(TAG, "✅ Status automatically changed to $dutyStatusType")
                    } else {
                        val error = response.body()?.error ?: "Failed with code ${response.code()}"
                        Log.e(TAG, "❌ Failed to auto-change status: $error")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error auto-changing status: ${e.message}", e)
                }
            }
        }

        Log.d(TAG, "Global automatic duty status change handler initialized")
    }
}
