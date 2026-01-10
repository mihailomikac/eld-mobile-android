package com.eld.driver.ui.screens.login

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.auth0.android.jwt.JWT
import com.eld.driver.ELDDriverApplication
import com.eld.driver.ble.GeometrisWQManager
import com.eld.driver.data.api.ApiErrorParser
import com.eld.driver.data.api.ApiService
import com.eld.driver.data.models.FmcsaEventRecordOrigin
import com.eld.driver.data.models.LoginRequest
import com.eld.driver.data.models.TickEventRequest
import com.eld.driver.data.models.TickEventType
import com.eld.driver.data.models.User
import com.eld.driver.data.repository.ELDRepository
import com.eld.driver.location.LocationService
import com.eld.driver.service.ELDForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * LoginViewModel - Handles authentication logic
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "LoginViewModel"
    }

    private val apiService = ApiService.getInstance()
    private val repository = ELDRepository.getInstance(application)
    private val locationService = LocationService.getInstance(application)
    private val bleManager = GeometrisWQManager.getInstance(application)

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Initial)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _authToken = MutableStateFlow<String?>(null)
    val authToken: StateFlow<String?> = _authToken.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    fun login(username: String, password: String, forceLogout: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading

            try {
                val response = apiService.login(LoginRequest(
                    email = username,
                    password = password,
                    forceLogout = forceLogout
                ))

                if (response.isSuccessful) {
                    val loginResponse = response.body()
                    if (loginResponse?.success == true) {
                        // Check if we need force logout confirmation
                        if (loginResponse.needsForceLogout) {
                            val message = loginResponse.message
                                ?: "Already logged in on another device. Do you want to logout there and continue?"
                            _uiState.value = LoginUiState.NeedsForceLogout(
                                message = message,
                                email = username,
                                password = password,
                                isDriverCurrentlyDriving = loginResponse.isDriverCurrentlyDriving
                            )
                            Log.d(TAG, "⚠️ Already logged in elsewhere - needs force logout confirmation (driving: ${loginResponse.isDriverCurrentlyDriving})")
                            return@launch
                        }

                        val token = loginResponse.token
                        if (token != null) {
                            val bearerToken = "Bearer $token"
                            _authToken.value = bearerToken

                            // Set global auth token (also saves to persistent storage)
                            ELDDriverApplication.setAuthToken(bearerToken)

                            // Decode JWT and extract user info
                            val user = decodeUserFromToken(token)
                            _currentUser.value = user

                            // Save user to persistent storage
                            if (user != null) {
                                ELDDriverApplication.setCurrentUser(user)
                            }

                            // Save email to recent logins
                            ELDDriverApplication.addRecentEmail(username)

                            // Connect to SignalR and WAIT for connection to be established
                            // This ensures backend has our connectionId before login completes
                            // Without this, if another device logs in immediately, we won't receive ForceLogout
                            Log.d(TAG, "📡 Waiting for SignalR connection...")
                            val signalRConnected = ELDDriverApplication.connectSignalRAndWait(timeoutMs = 5000)
                            if (signalRConnected) {
                                Log.d(TAG, "✅ SignalR connected - ready to receive force logout notifications")
                            } else {
                                Log.w(TAG, "⚠️ SignalR connection timeout - force logout notifications may not work")
                            }

                            // NOTE: LOGIN tick event is sent from VehicleViewModel after vehicle selection
                            // This ensures we have vehicle info when sending the tick event

                            _uiState.value = LoginUiState.Success(user)
                            Log.d(TAG, "✅ Login successful - Token saved to persistent storage")
                            Log.d(TAG, "✅ User: ${user?.fullName} (${user?.email})")
                        } else {
                            _uiState.value = LoginUiState.Error("No token received")
                            Log.e(TAG, "❌ Login failed: No token")
                        }
                    } else {
                        val error = loginResponse?.error ?: "Login failed"
                        _uiState.value = LoginUiState.Error(error)
                        Log.e(TAG, "❌ Login failed: $error")
                    }
                } else {
                    // Parse error from response body
                    val error = ApiErrorParser.parse(
                        response.errorBody()?.string(),
                        "Login failed: ${response.code()} ${response.message()}"
                    )
                    _uiState.value = LoginUiState.Error(error)
                    Log.e(TAG, "❌ Login failed: $error")
                }
            } catch (e: Exception) {
                val error = "Network error: ${e.message}"
                _uiState.value = LoginUiState.Error(error)
                Log.e(TAG, "❌ $error", e)
            }
        }
    }

    /**
     * Confirm force logout and retry login.
     * Called when user confirms they want to logout the other device.
     */
    fun confirmForceLogout() {
        val currentState = _uiState.value
        if (currentState is LoginUiState.NeedsForceLogout) {
            Log.d(TAG, "🔄 User confirmed force logout - retrying login with forceLogout=true")
            login(
                username = currentState.email,
                password = currentState.password,
                forceLogout = true
            )
        }
    }

    /**
     * Cancel force logout and go back to initial state.
     */
    fun cancelForceLogout() {
        Log.d(TAG, "❌ User cancelled force logout")
        _uiState.value = LoginUiState.Initial
    }

    fun logout(reason: String? = null) {
        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "🔓 LOGOUT INITIATED${reason?.let { " - Reason: $it" } ?: ""}")
        Log.d(TAG, "════════════════════════════════════════")

        // Save token for sync operations before clearing
        val token = _authToken.value

        // Clear UI state immediately for responsive UX
        _currentUser.value = null
        _uiState.value = LoginUiState.Initial

        // Sync and cleanup in background
        viewModelScope.launch {
            try {
                // STEP 1: Sync all pending local data to server BEFORE clearing
                if (token != null) {
                    Log.d(TAG, "📤 Syncing pending data before logout...")
                    repository.syncBeforeLogout()
                    Log.d(TAG, "✅ Pending data synced")

                    // STEP 2: Send logout tick event with location and telemetry
                    Log.d(TAG, "📤 Sending LOGOUT tick event...")

                    val currentLocation = locationService.getCurrentLocation()
                    val fmcsaLocation = if (currentLocation != null) {
                        locationService.getFMCSALocation(currentLocation.latitude, currentLocation.longitude)
                    } else null
                    val eldData = bleManager.eldData.value
                    val odometerMiles = eldData?.odometer?.let { it * 0.621371 }

                    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                    isoFormat.timeZone = TimeZone.getTimeZone("UTC")
                    val timestamp = isoFormat.format(Date())

                    val request = TickEventRequest(
                        eventType = TickEventType.LOGOUT,
                        vehicleId = ELDDriverApplication.getCurrentVehicleId(),
                        deviceId = ELDDriverApplication.getCurrentDeviceId(),
                        latitude = currentLocation?.latitude,
                        longitude = currentLocation?.longitude,
                        location = fmcsaLocation,
                        odometer = odometerMiles,
                        engineHours = eldData?.engineHours,
                        note = reason ?: "Driver logged out",
                        timestamp = timestamp,
                        eventRecordOrigin = FmcsaEventRecordOrigin.DRIVER
                    )
                    val response = apiService.createTickEvent(token, request)
                    if (response.isSuccessful) {
                        Log.d(TAG, "✅ Logout tick event sent successfully")
                    } else {
                        Log.w(TAG, "⚠️ Logout tick event failed: ${response.code()} ${response.message()}")
                    }

                    // STEP 3: Call backend logout to clear Redis session
                    Log.d(TAG, "📤 Calling backend logout...")
                    try {
                        val logoutResponse = apiService.logout(token)
                        if (logoutResponse.isSuccessful) {
                            Log.d(TAG, "✅ Backend session cleared")
                        } else {
                            Log.w(TAG, "⚠️ Backend logout failed: ${logoutResponse.code()}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Backend logout error: ${e.message}")
                    }
                }

                // STEP 4: Stop foreground service
                ELDForegroundService.stop(getApplication())
                Log.d(TAG, "🛑 Foreground service stopped")

                // STEP 5: NOW clear auth token and local data
                _authToken.value = null
                ELDDriverApplication.clearSession()  // Clears token, user, vehicleId from memory and storage
                Log.d(TAG, "✅ Session cleared")

                // STEP 6: Clear all local database data
                Log.d(TAG, "🗑️ Clearing all local database data...")
                repository.logout()
                Log.d(TAG, "✅ All local data cleared")

            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Failed during logout: ${e.message}", e)
                // Still clear token and data even if sync failed
                _authToken.value = null
                ELDDriverApplication.clearSession()
                try { repository.logout() } catch (_: Exception) {}
            }
            Log.d(TAG, "════════════════════════════════════════")
        }
    }

    /**
     * Try to restore session from persistent storage.
     * Call this on app start to check if user is already logged in.
     * Returns true if session was restored.
     */
    fun tryRestoreSession(): Boolean {
        if (ELDDriverApplication.isLoggedIn()) {
            val token = ELDDriverApplication.getAuthToken()
            val user = ELDDriverApplication.getCurrentUser()

            if (token != null) {
                _authToken.value = token
                _currentUser.value = user
                _uiState.value = LoginUiState.Success(user)
                Log.d(TAG, "✅ Session restored from storage")
                Log.d(TAG, "   User: ${user?.fullName} (${user?.email})")
                return true
            }
        }
        Log.d(TAG, "No valid session to restore")
        return false
    }

    fun clearError() {
        if (_uiState.value is LoginUiState.Error) {
            _uiState.value = LoginUiState.Initial
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState.Initial
    }

    /**
     * Get recent login emails for shortcuts
     */
    fun getRecentEmails(): List<String> {
        return ELDDriverApplication.getRecentEmails()
    }

    private fun decodeUserFromToken(token: String): User? {
        return try {
            val jwt = JWT(token)

            // Debug: Print all claims
            Log.d(TAG, "🔍 JWT Claims:")
            jwt.claims.forEach { (key, claim) ->
                Log.d(TAG, "  $key = ${claim.asString()}")
            }

            // Extract claims using short names (matching iOS implementation)
            // Try short claim names first, then fall back to full URIs
            val userId = jwt.getClaim("sub").asString()
                ?: jwt.getClaim("nameid").asString()
                ?: jwt.getClaim("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier").asString()

            val email = jwt.getClaim("email").asString()
                ?: jwt.getClaim("unique_name").asString()
                ?: jwt.getClaim("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress").asString()

            var firstName = jwt.getClaim("given_name").asString()
            var lastName = jwt.getClaim("family_name").asString()
            val role = jwt.getClaim("role").asString()

            // If no given_name/family_name, try to parse unique_name (e.g., "Mikac Kacmi")
            if (firstName == null && lastName == null) {
                val uniqueName = jwt.getClaim("unique_name").asString()
                if (uniqueName != null && uniqueName.contains(" ")) {
                    val nameParts = uniqueName.split(" ", limit = 2)
                    firstName = nameParts.getOrNull(0)
                    lastName = nameParts.getOrNull(1)
                    Log.d(TAG, "🔍 Parsed unique_name '$uniqueName' -> firstName='$firstName', lastName='$lastName'")
                } else if (uniqueName != null) {
                    firstName = uniqueName
                    Log.d(TAG, "🔍 Using unique_name as firstName: '$firstName'")
                }
            }

            Log.d(TAG, "🔍 Extracted: firstName='$firstName', lastName='$lastName', email='$email', userId='$userId', role='$role'")

            if (email != null && userId != null) {
                // Use firstName/lastName from JWT, or fallback to email
                val finalFirstName = firstName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")
                val finalLastName = lastName ?: ""

                User(
                    userId = userId,
                    username = email, // Use email as username
                    email = email,
                    firstName = finalFirstName,
                    lastName = finalLastName,
                    role = role,
                    phoneNumber = null
                )
            } else {
                Log.w(TAG, "⚠️ Could not extract user info from token (email or userId is null)")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to decode JWT token: ${e.message}", e)
            null
        }
    }
}

/**
 * UI State for Login Screen
 */
sealed class LoginUiState {
    object Initial : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val user: User?) : LoginUiState()
    data class Error(val message: String) : LoginUiState()

    /**
     * User is already logged in on another device.
     * Show confirmation dialog before force logout.
     */
    data class NeedsForceLogout(
        val message: String,
        val email: String,
        val password: String,
        val isDriverCurrentlyDriving: Boolean = false
    ) : LoginUiState()
}
