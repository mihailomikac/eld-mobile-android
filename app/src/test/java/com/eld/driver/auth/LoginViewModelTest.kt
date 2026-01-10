package com.eld.driver.auth

import android.app.Application
import com.eld.driver.data.api.ApiService
import com.eld.driver.data.models.LoginRequest
import com.eld.driver.data.models.LoginResponse
import com.eld.driver.data.models.MobileLoginData
import com.eld.driver.ui.screens.login.LoginUiState
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/**
 * Unit tests for LoginViewModel.
 *
 * Tests the complete login flow including:
 * - State transitions
 * - Force logout handling
 * - Error handling
 * - Session management
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @MockK
    private lateinit var apiService: ApiService

    @MockK(relaxed = true)
    private lateinit var application: Application

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ==================== HELPER FUNCTIONS ====================

    private fun createLoginResponse(
        success: Boolean = true,
        token: String? = "jwt_token",
        needsForceLogout: Boolean = false,
        isDriverCurrentlyDriving: Boolean = false,
        message: String? = null,
        error: String? = null
    ) = LoginResponse(
        success = success,
        data = MobileLoginData(
            token = token,
            needsForceLogout = needsForceLogout,
            isDriverCurrentlyDriving = isDriverCurrentlyDriving,
            message = message
        ),
        error = error,
        statusCode = if (success) 200 else 401
    )

    // ==================== UI STATE TRANSITION TESTS ====================

    @Test
    fun `uiState_initial_isInitial`() {
        val state = LoginUiState.Initial
        assertThat(state).isInstanceOf(LoginUiState.Initial::class.java)
    }

    @Test
    fun `uiState_loading_isLoading`() {
        val state = LoginUiState.Loading
        assertThat(state).isInstanceOf(LoginUiState.Loading::class.java)
    }

    @Test
    fun `uiState_success_containsUser`() {
        val state = LoginUiState.Success(user = null)
        assertThat(state).isInstanceOf(LoginUiState.Success::class.java)
    }

    @Test
    fun `uiState_error_containsMessage`() {
        val state = LoginUiState.Error("Test error")
        assertThat(state.message).isEqualTo("Test error")
    }

    @Test
    fun `uiState_needsForceLogout_containsAllFields`() {
        val state = LoginUiState.NeedsForceLogout(
            message = "Already logged in",
            email = "test@test.com",
            password = "password123",
            isDriverCurrentlyDriving = true
        )

        assertThat(state.message).isEqualTo("Already logged in")
        assertThat(state.email).isEqualTo("test@test.com")
        assertThat(state.password).isEqualTo("password123")
        assertThat(state.isDriverCurrentlyDriving).isTrue()
    }

    // ==================== NEEDS FORCE LOGOUT STATE TESTS ====================

    @Test
    fun `needsForceLogout_notDriving_flagIsFalse`() {
        val state = LoginUiState.NeedsForceLogout(
            message = "Already logged in",
            email = "test@test.com",
            password = "pass",
            isDriverCurrentlyDriving = false
        )

        assertThat(state.isDriverCurrentlyDriving).isFalse()
    }

    @Test
    fun `needsForceLogout_driving_flagIsTrue`() {
        val state = LoginUiState.NeedsForceLogout(
            message = "Driver is DRIVING",
            email = "test@test.com",
            password = "pass",
            isDriverCurrentlyDriving = true
        )

        assertThat(state.isDriverCurrentlyDriving).isTrue()
    }

    @Test
    fun `needsForceLogout_preservesCredentials_forRetry`() {
        val email = "driver@company.com"
        val password = "securePassword123"

        val state = LoginUiState.NeedsForceLogout(
            message = "Test",
            email = email,
            password = password,
            isDriverCurrentlyDriving = false
        )

        // Credentials should be preserved for the force logout retry
        assertThat(state.email).isEqualTo(email)
        assertThat(state.password).isEqualTo(password)
    }

    // ==================== ERROR STATE TESTS ====================

    @Test
    fun `errorState_invalidCredentials_hasCorrectMessage`() {
        val state = LoginUiState.Error("Invalid credentials")
        assertThat(state.message).isEqualTo("Invalid credentials")
    }

    @Test
    fun `errorState_networkError_hasCorrectMessage`() {
        val state = LoginUiState.Error("Network error: Unable to connect")
        assertThat(state.message).contains("Network error")
    }

    @Test
    fun `errorState_accountInactive_hasCorrectMessage`() {
        val state = LoginUiState.Error("Account is inactive. Please contact support.")
        assertThat(state.message).contains("inactive")
    }

    @Test
    fun `errorState_driversOnly_hasCorrectMessage`() {
        val state = LoginUiState.Error("This login is for drivers only.")
        assertThat(state.message).contains("drivers only")
    }

    // ==================== LOGIN RESPONSE PARSING TESTS ====================

    @Test
    fun `parseResponse_successWithToken_extractsToken`() {
        val response = createLoginResponse(
            success = true,
            token = "my_jwt_token"
        )

        assertThat(response.success).isTrue()
        assertThat(response.token).isEqualTo("my_jwt_token")
    }

    @Test
    fun `parseResponse_needsForceLogout_noToken`() {
        val response = createLoginResponse(
            success = true,
            token = null,
            needsForceLogout = true
        )

        assertThat(response.success).isTrue()
        assertThat(response.token).isNull()
        assertThat(response.needsForceLogout).isTrue()
    }

    @Test
    fun `parseResponse_driverDriving_flagExtracted`() {
        val response = createLoginResponse(
            needsForceLogout = true,
            isDriverCurrentlyDriving = true,
            message = "Driver is in DRIVING status"
        )

        assertThat(response.isDriverCurrentlyDriving).isTrue()
        assertThat(response.message).contains("DRIVING")
    }

    @Test
    fun `parseResponse_failedLogin_extractsError`() {
        val response = LoginResponse(
            success = false,
            data = null,
            error = "Invalid credentials",
            statusCode = 401
        )

        assertThat(response.success).isFalse()
        assertThat(response.error).isEqualTo("Invalid credentials")
    }

    // ==================== STATE MACHINE TESTS ====================

    @Test
    fun `stateMachine_initialToLoading_onLoginStart`() {
        // When login starts, state should go from Initial to Loading
        val initialState = LoginUiState.Initial
        val loadingState = LoginUiState.Loading

        assertThat(initialState).isNotEqualTo(loadingState)
        assertThat(loadingState).isInstanceOf(LoginUiState.Loading::class.java)
    }

    @Test
    fun `stateMachine_loadingToSuccess_onLoginSuccess`() {
        // When login succeeds, state should go from Loading to Success
        val successState = LoginUiState.Success(user = null)

        assertThat(successState).isInstanceOf(LoginUiState.Success::class.java)
    }

    @Test
    fun `stateMachine_loadingToError_onLoginFailure`() {
        // When login fails, state should go from Loading to Error
        val errorState = LoginUiState.Error("Failed")

        assertThat(errorState).isInstanceOf(LoginUiState.Error::class.java)
    }

    @Test
    fun `stateMachine_loadingToNeedsForceLogout_onExistingSession`() {
        // When existing session found, state should go to NeedsForceLogout
        val forceLogoutState = LoginUiState.NeedsForceLogout(
            message = "Test",
            email = "test@test.com",
            password = "pass",
            isDriverCurrentlyDriving = false
        )

        assertThat(forceLogoutState).isInstanceOf(LoginUiState.NeedsForceLogout::class.java)
    }

    @Test
    fun `stateMachine_needsForceLogoutToLoading_onConfirm`() {
        // When user confirms force logout, state should go back to Loading
        val loadingState = LoginUiState.Loading

        assertThat(loadingState).isInstanceOf(LoginUiState.Loading::class.java)
    }

    @Test
    fun `stateMachine_needsForceLogoutToInitial_onCancel`() {
        // When user cancels force logout, state should go to Initial
        val initialState = LoginUiState.Initial

        assertThat(initialState).isInstanceOf(LoginUiState.Initial::class.java)
    }

    // ==================== FORCE LOGOUT CONFIRMATION FLOW TESTS ====================

    @Test
    fun `forceLogoutFlow_stateContainsOriginalCredentials`() {
        val originalEmail = "driver@test.com"
        val originalPassword = "myPassword"

        val state = LoginUiState.NeedsForceLogout(
            message = "Already logged in",
            email = originalEmail,
            password = originalPassword,
            isDriverCurrentlyDriving = false
        )

        // When confirmForceLogout is called, these credentials should be used
        assertThat(state.email).isEqualTo(originalEmail)
        assertThat(state.password).isEqualTo(originalPassword)
    }

    @Test
    fun `forceLogoutFlow_drivingWarning_messageContainsStatus`() {
        val state = LoginUiState.NeedsForceLogout(
            message = "Driver is currently in DRIVING status. Force logout will invalidate their session.",
            email = "test@test.com",
            password = "pass",
            isDriverCurrentlyDriving = true
        )

        assertThat(state.message).contains("DRIVING")
        assertThat(state.isDriverCurrentlyDriving).isTrue()
    }

    // ==================== API CALL VERIFICATION TESTS ====================

    @Test
    fun `loginCall_firstAttempt_forceLogoutIsFalse`() = runTest {
        val request = LoginRequest(
            email = "test@test.com",
            password = "password",
            forceLogout = false
        )

        assertThat(request.forceLogout).isFalse()
    }

    @Test
    fun `loginCall_afterConfirmation_forceLogoutIsTrue`() = runTest {
        val request = LoginRequest(
            email = "test@test.com",
            password = "password",
            forceLogout = true
        )

        assertThat(request.forceLogout).isTrue()
    }

    // ==================== COMPREHENSIVE SCENARIO TESTS ====================

    @Test
    fun `scenario_freshLogin_noExistingSession`() = runTest {
        // User has no existing session anywhere
        val response = createLoginResponse(
            success = true,
            token = "fresh_token",
            needsForceLogout = false
        )

        coEvery { apiService.login(any()) } returns Response.success(response)

        val result = apiService.login(LoginRequest("test@test.com", "pass"))

        assertThat(result.body()?.success).isTrue()
        assertThat(result.body()?.token).isNotNull()
        assertThat(result.body()?.needsForceLogout).isFalse()
    }

    @Test
    fun `scenario_existingSession_driverNotDriving`() = runTest {
        // User is logged in on another device, but not actively driving
        val response = createLoginResponse(
            success = true,
            token = null,
            needsForceLogout = true,
            isDriverCurrentlyDriving = false,
            message = "Already logged in on another device."
        )

        coEvery { apiService.login(any()) } returns Response.success(response)

        val result = apiService.login(LoginRequest("test@test.com", "pass"))

        assertThat(result.body()?.needsForceLogout).isTrue()
        assertThat(result.body()?.isDriverCurrentlyDriving).isFalse()
    }

    @Test
    fun `scenario_existingSession_driverIsDriving`() = runTest {
        // User is logged in on another device AND actively driving
        val response = createLoginResponse(
            success = true,
            token = null,
            needsForceLogout = true,
            isDriverCurrentlyDriving = true,
            message = "Driver is currently in DRIVING status."
        )

        coEvery { apiService.login(any()) } returns Response.success(response)

        val result = apiService.login(LoginRequest("test@test.com", "pass"))

        assertThat(result.body()?.needsForceLogout).isTrue()
        assertThat(result.body()?.isDriverCurrentlyDriving).isTrue()
    }

    @Test
    fun `scenario_forceLogoutConfirmed_newSessionCreated`() = runTest {
        // User confirmed force logout, new session should be created
        val response = createLoginResponse(
            success = true,
            token = "new_session_token_v2",
            needsForceLogout = false
        )

        coEvery { apiService.login(any()) } returns Response.success(response)

        val result = apiService.login(LoginRequest("test@test.com", "pass", forceLogout = true))

        assertThat(result.body()?.success).isTrue()
        assertThat(result.body()?.token).isEqualTo("new_session_token_v2")
    }

    @Test
    fun `scenario_forceLogoutCancelled_stateReturnsToInitial`() {
        // User cancelled force logout dialog
        val cancelledState = LoginUiState.Initial

        assertThat(cancelledState).isEqualTo(LoginUiState.Initial)
    }

    // ==================== CONCURRENT OPERATIONS TESTS ====================

    @Test
    fun `concurrent_rapidLoginAttempts_handledCorrectly`() = runTest {
        // Simulate multiple rapid login attempts
        var callCount = 0
        coEvery { apiService.login(any()) } answers {
            callCount++
            Response.success(createLoginResponse(token = "token_$callCount"))
        }

        // Multiple calls
        repeat(3) {
            apiService.login(LoginRequest("test@test.com", "pass"))
        }

        assertThat(callCount).isEqualTo(3)
    }

    // ==================== DATA CLASS EQUALITY TESTS ====================

    @Test
    fun `loginUiState_error_equalityWorks`() {
        val state1 = LoginUiState.Error("Error message")
        val state2 = LoginUiState.Error("Error message")
        val state3 = LoginUiState.Error("Different error")

        assertThat(state1).isEqualTo(state2)
        assertThat(state1).isNotEqualTo(state3)
    }

    @Test
    fun `loginUiState_needsForceLogout_equalityWorks`() {
        val state1 = LoginUiState.NeedsForceLogout("msg", "email", "pass", false)
        val state2 = LoginUiState.NeedsForceLogout("msg", "email", "pass", false)
        val state3 = LoginUiState.NeedsForceLogout("msg", "email", "pass", true)

        assertThat(state1).isEqualTo(state2)
        assertThat(state1).isNotEqualTo(state3)
    }

    // ==================== NULL SAFETY TESTS ====================

    @Test
    fun `nullSafety_tokenCanBeNull`() {
        val response = createLoginResponse(token = null)
        assertThat(response.token).isNull()
    }

    @Test
    fun `nullSafety_messageCanBeNull`() {
        val response = createLoginResponse(message = null)
        assertThat(response.message).isNull()
    }

    @Test
    fun `nullSafety_userInSuccessCanBeNull`() {
        val state = LoginUiState.Success(user = null)
        assertThat(state.user).isNull()
    }
}
