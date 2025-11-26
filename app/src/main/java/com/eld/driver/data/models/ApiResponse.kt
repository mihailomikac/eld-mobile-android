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
 * Login request
 */
data class LoginRequest(
    val email: String,
    val password: String
)

/**
 * Login response - data field contains JWT token as string
 */
data class LoginResponse(
    val success: Boolean,
    val data: String?,      // JWT token
    val error: String?,
    val statusCode: Int?
) {
    val token: String? get() = data
}
