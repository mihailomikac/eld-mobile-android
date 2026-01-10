package com.eld.driver.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.auth0.android.jwt.JWT
import com.eld.driver.data.models.User
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken

/**
 * TokenManager - Secure persistent storage for auth token and user info.
 *
 * Uses EncryptedSharedPreferences for secure storage that persists
 * across app restarts and process death.
 */
class TokenManager private constructor(context: Context) {

    companion object {
        private const val TAG = "TokenManager"
        private const val PREFS_FILE = "eld_secure_prefs"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_FIRST_NAME = "user_first_name"
        private const val KEY_USER_LAST_NAME = "user_last_name"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_VEHICLE_ID = "vehicle_id"
        private const val KEY_ELD_MAC_ADDRESS = "eld_mac_address"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_RECENT_EMAILS = "recent_emails"
        private const val KEY_TIMEZONE_OFFSET = "timezone_offset"
        private const val KEY_TIMEZONE_NAME = "timezone_name"
        private const val MAX_RECENT_EMAILS = 3

        // Driver settings from DriverData claim
        private const val KEY_ALLOW_YARD_MOVE = "allow_yard_move"
        private const val KEY_ALLOW_PERSONAL_CONVEYANCE = "allow_personal_conveyance"
        private const val KEY_ALLOW_MANUAL_DRIVE_TIME = "allow_manual_drive_time"
        private const val KEY_HAS_30_MIN_BREAK_EXCEPTION = "has_30_min_break_exception"
        private const val KEY_EXEMPT_FROM_ELD = "exempt_from_eld"

        @Volatile
        private var instance: TokenManager? = null

        fun getInstance(context: Context): TokenManager {
            return instance ?: synchronized(this) {
                instance ?: TokenManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences

    init {
        prefs = createPreferences(context)
        Log.d(TAG, "TokenManager initialized")
    }

    private fun createPreferences(context: Context): SharedPreferences {
        return try {
            // Try to create encrypted shared preferences
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create encrypted prefs: ${e.message}", e)

            // Try to delete corrupted prefs file and retry once
            try {
                Log.w(TAG, "Attempting to delete corrupted prefs and retry...")
                context.deleteSharedPreferences(PREFS_FILE)

                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e2: Exception) {
                // Final fallback to regular SharedPreferences
                Log.e(TAG, "Retry failed, using regular SharedPreferences: ${e2.message}")
                context.getSharedPreferences(PREFS_FILE + "_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    /**
     * Save auth token and extract user info from JWT
     */
    fun saveToken(bearerToken: String) {
        val token = bearerToken.removePrefix("Bearer ")

        prefs.edit().apply {
            putString(KEY_AUTH_TOKEN, bearerToken)

            // Extract claims from JWT
            try {
                val jwt = JWT(token)

                // DEBUG: Print entire JWT token contents
                Log.d(TAG, "═══════════════════════════════════════════════════════════")
                Log.d(TAG, "🔑 JWT TOKEN PARSED:")
                Log.d(TAG, "═══════════════════════════════════════════════════════════")
                Log.d(TAG, "Subject: ${jwt.subject}")
                Log.d(TAG, "Issuer: ${jwt.issuer}")
                Log.d(TAG, "Audience: ${jwt.audience}")
                Log.d(TAG, "ExpiresAt: ${jwt.expiresAt}")
                Log.d(TAG, "IssuedAt: ${jwt.issuedAt}")
                Log.d(TAG, "───────────────────────────────────────────────────────────")
                Log.d(TAG, "ALL CLAIMS:")
                jwt.claims.forEach { (key, claim) ->
                    val value = claim.asString() ?: claim.asInt()?.toString() ?: claim.asBoolean()?.toString() ?: claim.asList(String::class.java)?.toString() ?: "null"
                    Log.d(TAG, "  $key = $value")
                }
                Log.d(TAG, "═══════════════════════════════════════════════════════════")

                // Extract expiry
                val expiresAt = jwt.expiresAt?.time ?: 0L
                putLong(KEY_TOKEN_EXPIRY, expiresAt)
                Log.d(TAG, "Token saved, expires at: ${java.util.Date(expiresAt)}")

                // Extract timezone from CompanyPermissions claim
                // Backend stores it as JSON: [{"CompanyId":1, "TimeZoneOffset": -6.0, ...}]
                val companyPermissions = jwt.getClaim("CompanyPermissions").asString()
                Log.d(TAG, "📍 CompanyPermissions claim: ${companyPermissions?.take(200) ?: "NULL"}")

                if (companyPermissions != null) {
                    try {
                        val type = object : TypeToken<List<CompanyPermissionClaim>>() {}.type
                        val permissions: List<CompanyPermissionClaim> = Gson().fromJson(companyPermissions, type)
                        Log.d(TAG, "📍 Parsed ${permissions.size} company permissions")

                        if (permissions.isNotEmpty()) {
                            val firstCompany = permissions[0]
                            Log.d(TAG, "📍 First company: id=${firstCompany.companyId}, name=${firstCompany.companyName}, tzOffset=${firstCompany.timeZoneOffset}")

                            // timeZoneOffset is in hours from backend (e.g., -6.0 for CST)
                            // Convert to minutes for storage (e.g., -360 for CST)
                            val offsetHours = firstCompany.timeZoneOffset ?: 0.0
                            val offsetMinutes = (offsetHours * 60).toInt()
                            putInt(KEY_TIMEZONE_OFFSET, offsetMinutes)
                            Log.d(TAG, "✅ Timezone offset saved: $offsetHours hours = $offsetMinutes minutes")
                        } else {
                            Log.w(TAG, "⚠️ CompanyPermissions is empty array")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Could not parse CompanyPermissions: ${e.message}", e)
                    }
                } else {
                    // Fallback 1: Try DriverData claim (newer format)
                    val driverData = jwt.getClaim("DriverData").asString()
                    Log.d(TAG, "📍 DriverData claim: ${driverData?.take(200) ?: "NULL"}")

                    if (driverData != null) {
                        try {
                            val driverDataObj = Gson().fromJson(driverData, DriverDataClaim::class.java)
                            Log.d(TAG, "📍 Parsed DriverData: companyId=${driverDataObj.companyId}, tzOffset=${driverDataObj.companyTimeOffset}")

                            // companyTimeOffset is in hours from backend (e.g., -8.0 for PST)
                            // Convert to minutes for storage (e.g., -480 for PST)
                            val offsetHours = driverDataObj.companyTimeOffset ?: 0.0
                            val offsetMinutes = (offsetHours * 60).toInt()
                            putInt(KEY_TIMEZONE_OFFSET, offsetMinutes)
                            Log.d(TAG, "✅ Timezone offset from DriverData: $offsetHours hours = $offsetMinutes minutes")

                            // Save driver settings
                            putBoolean(KEY_ALLOW_YARD_MOVE, driverDataObj.allowYardMove ?: false)
                            putBoolean(KEY_ALLOW_PERSONAL_CONVEYANCE, driverDataObj.allowPersonalConveyance ?: false)
                            putBoolean(KEY_ALLOW_MANUAL_DRIVE_TIME, driverDataObj.allowManualDriveTime ?: false)
                            putBoolean(KEY_HAS_30_MIN_BREAK_EXCEPTION, driverDataObj.hasThirtyMinuteBreakException ?: false)
                            putBoolean(KEY_EXEMPT_FROM_ELD, driverDataObj.exemptFromELD ?: false)
                            Log.d(TAG, "✅ Driver settings saved: allowYM=${driverDataObj.allowYardMove}, allowPC=${driverDataObj.allowPersonalConveyance}, allowManualDrive=${driverDataObj.allowManualDriveTime}")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Could not parse DriverData: ${e.message}", e)
                        }
                    } else {
                        // Fallback 2: try direct claims (for backward compatibility)
                        val timezoneOffset = jwt.getClaim("timezoneOffset").asInt()
                        if (timezoneOffset != null) {
                            putInt(KEY_TIMEZONE_OFFSET, timezoneOffset)
                            Log.d(TAG, "Timezone offset from direct claim: $timezoneOffset minutes")
                        }

                        val timezoneName = jwt.getClaim("timezone").asString()
                        if (timezoneName != null) {
                            putString(KEY_TIMEZONE_NAME, timezoneName)
                            Log.d(TAG, "Timezone name from JWT: $timezoneName")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not parse JWT claims: ${e.message}")
            }

            apply()
        }
    }

    /**
     * Save user info
     */
    fun saveUser(user: User) {
        prefs.edit().apply {
            putString(KEY_USER_ID, user.userId)
            putString(KEY_USER_EMAIL, user.email)
            putString(KEY_USER_FIRST_NAME, user.firstName)
            putString(KEY_USER_LAST_NAME, user.lastName)
            putString(KEY_USER_ROLE, user.role)
            apply()
        }
        Log.d(TAG, "User saved: ${user.email}")
    }

    /**
     * Save current vehicle ID
     */
    fun saveVehicleId(vehicleId: Int?) {
        Log.d(TAG, "📍 saveVehicleId() called with: $vehicleId")
        prefs.edit().apply {
            if (vehicleId != null) {
                putInt(KEY_VEHICLE_ID, vehicleId)
                Log.d(TAG, "📍 saveVehicleId(): PUT vehicleId=$vehicleId")
            } else {
                remove(KEY_VEHICLE_ID)
                Log.d(TAG, "📍 saveVehicleId(): REMOVED vehicleId key")
            }
            apply()
        }
        // Verify it was saved
        val verify = getVehicleId()
        Log.d(TAG, "📍 saveVehicleId(): verification read = $verify")
    }

    /**
     * Save ELD MAC address for the current vehicle.
     * Used for direct BLE connection without scanning.
     */
    fun saveEldMacAddress(macAddress: String?) {
        prefs.edit().apply {
            if (macAddress != null) {
                putString(KEY_ELD_MAC_ADDRESS, macAddress)
            } else {
                remove(KEY_ELD_MAC_ADDRESS)
            }
            apply()
        }
        Log.d(TAG, "ELD MAC address saved: $macAddress")
    }

    /**
     * Get stored ELD MAC address
     */
    fun getEldMacAddress(): String? {
        return prefs.getString(KEY_ELD_MAC_ADDRESS, null)
    }

    /**
     * Save ELD device ID for the current vehicle.
     * Used in all API requests that require deviceId.
     */
    fun saveDeviceId(deviceId: Int?) {
        prefs.edit().apply {
            if (deviceId != null) {
                putInt(KEY_DEVICE_ID, deviceId)
            } else {
                remove(KEY_DEVICE_ID)
            }
            apply()
        }
        Log.d(TAG, "Device ID saved: $deviceId")
    }

    /**
     * Get stored ELD device ID
     */
    fun getDeviceId(): Int? {
        return if (prefs.contains(KEY_DEVICE_ID)) {
            prefs.getInt(KEY_DEVICE_ID, -1).takeIf { it != -1 }
        } else {
            null
        }
    }

    /**
     * Get stored auth token (with Bearer prefix)
     */
    fun getToken(): String? {
        val token = prefs.getString(KEY_AUTH_TOKEN, null)
        if (token != null) {
            Log.d(TAG, "Token retrieved from storage")

            // If timezone is not set, try to extract it from the token
            // This handles cases where user logged in before timezone code was added
            if (!prefs.contains(KEY_TIMEZONE_OFFSET)) {
                Log.d(TAG, "📍 Timezone not set, re-parsing token to extract it...")
                extractAndSaveTimezoneFromToken(token)
            }
        }
        return token
    }

    /**
     * Extract timezone from JWT token and save it.
     * Called when restoring a session that doesn't have timezone saved.
     */
    private fun extractAndSaveTimezoneFromToken(bearerToken: String) {
        val token = bearerToken.removePrefix("Bearer ")

        try {
            val jwt = JWT(token)

            // Try CompanyPermissions claim first
            val companyPermissions = jwt.getClaim("CompanyPermissions").asString()
            Log.d(TAG, "📍 Re-parsing CompanyPermissions claim: ${companyPermissions?.take(200) ?: "NULL"}")

            if (companyPermissions != null) {
                try {
                    val type = object : TypeToken<List<CompanyPermissionClaim>>() {}.type
                    val permissions: List<CompanyPermissionClaim> = Gson().fromJson(companyPermissions, type)
                    Log.d(TAG, "📍 Parsed ${permissions.size} company permissions")

                    if (permissions.isNotEmpty()) {
                        val firstCompany = permissions[0]
                        Log.d(TAG, "📍 First company: id=${firstCompany.companyId}, name=${firstCompany.companyName}, tzOffset=${firstCompany.timeZoneOffset}")

                        val offsetHours = firstCompany.timeZoneOffset ?: 0.0
                        val offsetMinutes = (offsetHours * 60).toInt()
                        prefs.edit().putInt(KEY_TIMEZONE_OFFSET, offsetMinutes).apply()
                        Log.d(TAG, "✅ Timezone offset extracted and saved: $offsetHours hours = $offsetMinutes minutes")
                        return
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Could not parse CompanyPermissions: ${e.message}", e)
                }
            }

            // Fallback: Try DriverData claim
            val driverData = jwt.getClaim("DriverData").asString()
            Log.d(TAG, "📍 Re-parsing DriverData claim: ${driverData?.take(200) ?: "NULL"}")

            if (driverData != null) {
                try {
                    val driverDataObj = Gson().fromJson(driverData, DriverDataClaim::class.java)
                    Log.d(TAG, "📍 Parsed DriverData: companyId=${driverDataObj.companyId}, tzOffset=${driverDataObj.companyTimeOffset}")

                    val offsetHours = driverDataObj.companyTimeOffset ?: 0.0
                    val offsetMinutes = (offsetHours * 60).toInt()
                    prefs.edit().putInt(KEY_TIMEZONE_OFFSET, offsetMinutes).apply()
                    Log.d(TAG, "✅ Timezone offset from DriverData: $offsetHours hours = $offsetMinutes minutes")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Could not parse DriverData: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract timezone from JWT: ${e.message}")
        }
    }

    /**
     * Get stored user
     */
    fun getUser(): User? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val email = prefs.getString(KEY_USER_EMAIL, null) ?: return null

        return User(
            userId = userId,
            username = email,
            email = email,
            firstName = prefs.getString(KEY_USER_FIRST_NAME, "") ?: "",
            lastName = prefs.getString(KEY_USER_LAST_NAME, "") ?: "",
            role = prefs.getString(KEY_USER_ROLE, null),
            phoneNumber = null
        )
    }

    /**
     * Get stored vehicle ID
     */
    fun getVehicleId(): Int? {
        val hasKey = prefs.contains(KEY_VEHICLE_ID)
        val value = if (hasKey) prefs.getInt(KEY_VEHICLE_ID, -1) else -1
        val result = if (hasKey && value != -1) value else null
        Log.d(TAG, "📍 getVehicleId(): hasKey=$hasKey, rawValue=$value, returning=$result")
        return result
    }

    /**
     * Check if token is expired
     */
    fun isTokenExpired(): Boolean {
        val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0L)
        if (expiry == 0L) return true

        // Add 5 minute buffer
        val isExpired = System.currentTimeMillis() > (expiry - 5 * 60 * 1000)
        if (isExpired) {
            Log.d(TAG, "Token is expired")
        }
        return isExpired
    }

    /**
     * Check if user is logged in (has valid token)
     */
    fun isLoggedIn(): Boolean {
        val token = getToken()
        return token != null && !isTokenExpired()
    }

    /**
     * Clear session data (on logout) - keeps recent emails for convenience
     */
    fun clear() {
        // Save recent emails before clearing
        val recentEmails = prefs.getString(KEY_RECENT_EMAILS, null)
        Log.d(TAG, "clear() - saving recent emails before clear: '$recentEmails'")

        // Clear all
        prefs.edit().clear().apply()

        // Restore recent emails
        if (recentEmails != null) {
            prefs.edit().putString(KEY_RECENT_EMAILS, recentEmails).apply()
            Log.d(TAG, "clear() - restored recent emails: '$recentEmails'")
        }

        // Verify it was saved
        val verify = prefs.getString(KEY_RECENT_EMAILS, null)
        Log.d(TAG, "clear() - verification read: '$verify'")

        Log.d(TAG, "Session data cleared (recent emails preserved)")
    }

    /**
     * Get token expiry time in millis
     */
    fun getTokenExpiry(): Long {
        return prefs.getLong(KEY_TOKEN_EXPIRY, 0L)
    }

    /**
     * Get company timezone offset in minutes from UTC.
     * E.g., -300 for EST (UTC-5), -480 for PST (UTC-8)
     * Returns null if not set.
     */
    fun getTimezoneOffset(): Int? {
        return if (prefs.contains(KEY_TIMEZONE_OFFSET)) {
            prefs.getInt(KEY_TIMEZONE_OFFSET, 0)
        } else {
            null
        }
    }

    /**
     * Get company timezone name (e.g., "America/New_York")
     * Returns null if not set.
     */
    fun getTimezoneName(): String? {
        return prefs.getString(KEY_TIMEZONE_NAME, null)
    }

    /**
     * Get company TimeZone object.
     * Uses timezone name if available, otherwise calculates from offset.
     * Falls back to device timezone if neither is set.
     */
    fun getCompanyTimeZone(): java.util.TimeZone {
        Log.d(TAG, "📍 getCompanyTimeZone() called")

        // First try timezone name
        val tzName = getTimezoneName()
        if (tzName != null) {
            Log.d(TAG, "📍 Using timezone name: $tzName")
            return java.util.TimeZone.getTimeZone(tzName)
        }

        // Then try offset
        val offsetMinutes = getTimezoneOffset()
        Log.d(TAG, "📍 Timezone offset from storage: $offsetMinutes minutes")

        if (offsetMinutes != null) {
            // Convert minutes to GMT offset string (e.g., "GMT-06:00" for CST)
            // offsetMinutes is negative for west of UTC (e.g., -360 for CST)
            val hours = Math.abs(offsetMinutes) / 60
            val mins = Math.abs(offsetMinutes) % 60
            val sign = if (offsetMinutes >= 0) "+" else "-"  // Negative offset = west of UTC = GMT-XX:XX
            val tzId = String.format("GMT%s%02d:%02d", sign, hours, mins)
            Log.d(TAG, "✅ Created timezone from offset: $offsetMinutes minutes -> $tzId")
            return java.util.TimeZone.getTimeZone(tzId)
        }

        // Fallback to device timezone
        val deviceTz = java.util.TimeZone.getDefault()
        Log.d(TAG, "⚠️ Falling back to device timezone: ${deviceTz.id}")
        return deviceTz
    }

    // ==================== Driver Settings ====================

    /**
     * Check if driver is allowed to use Yard Move status.
     */
    fun isYardMoveAllowed(): Boolean {
        return prefs.getBoolean(KEY_ALLOW_YARD_MOVE, false)
    }

    /**
     * Check if driver is allowed to use Personal Conveyance status.
     */
    fun isPersonalConveyanceAllowed(): Boolean {
        return prefs.getBoolean(KEY_ALLOW_PERSONAL_CONVEYANCE, false)
    }

    /**
     * Check if driver is allowed to manually enter drive time.
     * If false, DRIVING status should not be available for manual selection.
     */
    fun isManualDriveTimeAllowed(): Boolean {
        return prefs.getBoolean(KEY_ALLOW_MANUAL_DRIVE_TIME, false)
    }

    /**
     * Check if driver has 30-minute break exception.
     */
    fun has30MinBreakException(): Boolean {
        return prefs.getBoolean(KEY_HAS_30_MIN_BREAK_EXCEPTION, false)
    }

    /**
     * Check if driver is exempt from ELD requirements.
     */
    fun isExemptFromELD(): Boolean {
        return prefs.getBoolean(KEY_EXEMPT_FROM_ELD, false)
    }

    // ==================== Recent Emails ====================

    /**
     * Add email to recent emails list (for login shortcuts)
     * Keeps only the last 3 unique emails
     */
    fun addRecentEmail(email: String) {
        val currentEmails = getRecentEmails().toMutableList()

        // Remove if already exists (to move to top)
        currentEmails.remove(email)

        // Add to beginning
        currentEmails.add(0, email)

        // Keep only MAX_RECENT_EMAILS
        val trimmedList = currentEmails.take(MAX_RECENT_EMAILS)

        // Save as comma-separated string
        prefs.edit().putString(KEY_RECENT_EMAILS, trimmedList.joinToString(",")).apply()
        Log.d(TAG, "Recent emails updated: $trimmedList")
    }

    /**
     * Get list of recent emails for login shortcuts
     */
    fun getRecentEmails(): List<String> {
        val emailsString = prefs.getString(KEY_RECENT_EMAILS, null)
        Log.d(TAG, "getRecentEmails() raw string: '$emailsString'")
        if (emailsString == null) return emptyList()
        val emails = emailsString.split(",").filter { it.isNotBlank() }
        Log.d(TAG, "getRecentEmails() returning: $emails")
        return emails
    }
}

/**
 * Data class for parsing CompanyPermissions JWT claim.
 * Matches backend's UserCompanyClaim structure.
 * Note: Backend uses PascalCase, so we need @SerializedName annotations.
 */
data class CompanyPermissionClaim(
    @SerializedName("CompanyId") val companyId: Int? = null,
    @SerializedName("CompanyName") val companyName: String? = null,
    @SerializedName("TimeZoneOffset") val timeZoneOffset: Double? = null,  // Hours from UTC (e.g., -6.0 for CST)
    @SerializedName("Role") val role: String? = null
)

/**
 * Data class for parsing DriverData JWT claim.
 * This is the newer format used for driver tokens.
 */
data class DriverDataClaim(
    @SerializedName("CompanyId") val companyId: Int? = null,
    @SerializedName("CompanyTimeOffset") val companyTimeOffset: Double? = null,  // Hours from UTC (e.g., -8.0 for PST)
    @SerializedName("HasThirtyMinuteBreakException") val hasThirtyMinuteBreakException: Boolean? = null,
    @SerializedName("HasTwentyFourHourCycleReset") val hasTwentyFourHourCycleReset: Boolean? = null,
    @SerializedName("ExemptFromELD") val exemptFromELD: Boolean? = null,
    @SerializedName("AllowYardMove") val allowYardMove: Boolean? = null,
    @SerializedName("AllowPersonalConveyance") val allowPersonalConveyance: Boolean? = null,
    @SerializedName("AllowManualDriveTime") val allowManualDriveTime: Boolean? = null,
    @SerializedName("ForbidCertifyWithoutTrailerDocs") val forbidCertifyWithoutTrailerDocs: Boolean? = null,
    @SerializedName("ShowPairedSplitSleeperClocks") val showPairedSplitSleeperClocks: Boolean? = null,
    @SerializedName("EnhanceViolationAlertSound") val enhanceViolationAlertSound: Boolean? = null,
    @SerializedName("RequireDriverSignature") val requireDriverSignature: Boolean? = null
)
