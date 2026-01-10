package com.eld.driver.data.api

import android.util.Log
import org.json.JSONObject

/**
 * Utility object for parsing API error responses.
 * Handles both standard error format and ASP.NET validation errors.
 */
object ApiErrorParser {
    private const val TAG = "ApiErrorParser"

    /**
     * Parse API error response to get human-readable error message.
     *
     * @param errorBody The raw error body string from the response
     * @param fallbackError Optional fallback error message
     * @return Human-readable error message
     */
    fun parse(errorBody: String?, fallbackError: String? = null): String {
        if (errorBody.isNullOrBlank()) {
            return fallbackError ?: "An error occurred"
        }

        return try {
            val json = JSONObject(errorBody)

            // Check for ASP.NET validation errors format
            // Format: {"errors": {"FieldName": ["Error message 1", "Error message 2"]}}
            if (json.has("errors")) {
                val errors = json.getJSONObject("errors")
                val errorMessages = mutableListOf<String>()

                errors.keys().forEach { key ->
                    val fieldErrors = errors.getJSONArray(key)
                    for (i in 0 until fieldErrors.length()) {
                        errorMessages.add(fieldErrors.getString(i))
                    }
                }

                if (errorMessages.isNotEmpty()) {
                    Log.d(TAG, "Parsed validation errors: $errorMessages")
                    return errorMessages.joinToString("\n")
                }
            }

            // Check for title field (ASP.NET problem details)
            if (json.has("title")) {
                val title = json.getString("title")
                Log.d(TAG, "Parsed title error: $title")
                return title
            }

            // Check for standard error field
            if (json.has("error")) {
                val error = json.getString("error")
                Log.d(TAG, "Parsed error field: $error")
                return error
            }

            // Check for message field
            if (json.has("message")) {
                val message = json.getString("message")
                Log.d(TAG, "Parsed message field: $message")
                return message
            }

            Log.w(TAG, "Could not find error in response: $errorBody")
            fallbackError ?: "An error occurred"
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse error body: $errorBody", e)
            fallbackError ?: "An error occurred"
        }
    }
}
