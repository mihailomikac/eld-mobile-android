package com.eld.driver.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auth0.android.jwt.JWT
import com.eld.driver.data.api.ApiResult
import com.eld.driver.data.api.ApiService
import com.eld.driver.data.models.LoginRequest
import com.eld.driver.data.models.TickEventRequest
import com.eld.driver.data.models.TickEventType
import com.eld.driver.data.models.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * LoginViewModel - Handles authentication logic
 */
class LoginViewModel : ViewModel() {
    private val apiService = ApiService.getInstance()

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Initial)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _authToken = MutableStateFlow<String?>(null)
    val authToken: StateFlow<String?> = _authToken.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading

            try {
                val response = apiService.login(LoginRequest(email = username, password = password))

                if (response.isSuccessful) {
                    val loginResponse = response.body()
                    if (loginResponse?.success == true) {
                        val token = loginResponse.token
                        if (token != null) {
                            _authToken.value = "Bearer $token"

                            // Decode JWT and extract user info
                            val user = decodeUserFromToken(token)
                            _currentUser.value = user

                            // Send login tick event
                            sendLoginTickEvent(token)

                            _uiState.value = LoginUiState.Success(user)
                            println("✅ Login successful - Token received")
                            println("✅ User: ${user?.fullName} (${user?.email})")
                        } else {
                            _uiState.value = LoginUiState.Error("No token received")
                            println("❌ Login failed: No token")
                        }
                    } else {
                        val error = loginResponse?.error ?: "Login failed"
                        _uiState.value = LoginUiState.Error(error)
                        println("❌ Login failed: $error")
                    }
                } else {
                    val error = "Login failed: ${response.code()} ${response.message()}"
                    _uiState.value = LoginUiState.Error(error)
                    println("❌ $error")
                }
            } catch (e: Exception) {
                val error = "Network error: ${e.message}"
                _uiState.value = LoginUiState.Error(error)
                println("❌ $error")
                e.printStackTrace()
            }
        }
    }

    private fun sendLoginTickEvent(token: String) {
        viewModelScope.launch {
            try {
                val request = TickEventRequest(
                    eventType = TickEventType.LOGIN,
                    note = "Driver logged in"
                )
                apiService.createTickEvent("Bearer $token", request)
                println("✅ Login tick event sent")
            } catch (e: Exception) {
                println("⚠️ Failed to send login tick event: ${e.message}")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                authToken.value?.let { token ->
                    val request = TickEventRequest(
                        eventType = TickEventType.LOGOUT,
                        note = "Driver logged out"
                    )
                    apiService.createTickEvent(token, request)
                    println("✅ Logout tick event sent")
                }
            } catch (e: Exception) {
                println("⚠️ Failed to send logout tick event: ${e.message}")
            } finally {
                _authToken.value = null
                _currentUser.value = null
                _uiState.value = LoginUiState.Initial
            }
        }
    }

    fun clearError() {
        if (_uiState.value is LoginUiState.Error) {
            _uiState.value = LoginUiState.Initial
        }
    }

    private fun decodeUserFromToken(token: String): User? {
        return try {
            val jwt = JWT(token)

            // Debug: Print all claims
            println("🔍 JWT Claims:")
            jwt.claims.forEach { (key, claim) ->
                println("  $key = ${claim.asString()}")
            }

            // Extract claims using short names (matching iOS implementation)
            // Try short claim names first, then fall back to full URIs
            val userId = jwt.getClaim("sub").asString()
                ?: jwt.getClaim("nameid").asString()
                ?: jwt.getClaim("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier").asString()

            val email = jwt.getClaim("email").asString()
                ?: jwt.getClaim("unique_name").asString()
                ?: jwt.getClaim("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress").asString()

            val firstName = jwt.getClaim("given_name").asString()
            val lastName = jwt.getClaim("family_name").asString()
            val role = jwt.getClaim("role").asString()

            println("🔍 Extracted: firstName='$firstName', lastName='$lastName', email='$email', userId='$userId', role='$role'")

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
                println("⚠️ Could not extract user info from token (email or userId is null)")
                null
            }
        } catch (e: Exception) {
            println("❌ Failed to decode JWT token: ${e.message}")
            e.printStackTrace()
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
}
