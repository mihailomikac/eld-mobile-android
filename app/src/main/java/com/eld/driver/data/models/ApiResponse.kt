package com.eld.driver.data.models

/**
 * Generic API Response wrapper
 * Matches backend ApiResponse<T> format
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null
)

/**
 * Paginated response for list endpoints
 */
data class PaginatedData<T>(
    val data: List<T>,
    val totalCount: Int,
    val pageNumber: Int,
    val pageSize: Int,
    val totalPages: Int
)

/**
 * Login request for mobile driver app
 * Uses new /api/mobile/auth/login endpoint with single-session enforcement
 */
data class LoginRequest(
    val email: String,
    val password: String,
    val forceLogout: Boolean = false  // If true, force logout existing session
)

/**
 * Login response from mobile auth endpoint
 * Supports single-session enforcement flow
 */
data class LoginResponse(
    val success: Boolean,
    val data: MobileLoginData?,
    val error: String?,
    val statusCode: Int?
) {
    // Convenience accessor for token
    val token: String? get() = data?.token

    // True if already logged in elsewhere and needs confirmation
    val needsForceLogout: Boolean get() = data?.needsForceLogout == true

    // True if the existing session is actively driving
    val isDriverCurrentlyDriving: Boolean get() = data?.isDriverCurrentlyDriving == true

    // Message from server
    val message: String? get() = data?.message
}

/**
 * Mobile login response data
 */
data class MobileLoginData(
    val token: String?,
    val needsForceLogout: Boolean = false,
    val isDriverCurrentlyDriving: Boolean = false,
    val message: String? = null
)
