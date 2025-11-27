package com.eld.driver.ble.models

/**
 * Unidentified Driving Event Model
 * Represents an unidentified driving event from Geometris device
 */
data class UnidentifiedEvent(
    var reason: Int? = null,
    var timestamp: Long? = null,  // Unix timestamp in seconds
    var engineHours: Double? = null,
    var speed: Double? = null,  // km/h
    var odometer: Double? = null,  // kilometers
    var latitude: Double? = null,
    var longitude: Double? = null,
    var gpsTimestamp: Long? = null  // Unix timestamp in seconds
) {
    /**
     * Get human-readable reason string
     */
    fun getReasonString(): String {
        return when (reason) {
            13 -> "END_STOP (Speed > 3 MPH)"
            12 -> "BEGIN_STOP (Speed < 3 MPH)"
            40 -> "BLE DISCONNECT"
            39 -> "BLE CONNECT"
            41 -> "BUS MALFUNCTION (Ignition on, no engine data)"
            42 -> "BUS MALFUNCTION END (Ignition on, engine data received)"
            else -> "UNKNOWN ($reason)"
        }
    }

    override fun toString(): String {
        return """
            UnidentifiedEvent(
                reason=${getReasonString()},
                time=$timestamp,
                speed=$speed km/h,
                odometer=$odometer km,
                lat=$latitude,
                lon=$longitude
            )
        """.trimIndent()
    }
}
