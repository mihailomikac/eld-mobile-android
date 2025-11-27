package com.eld.driver.ble.models

import java.util.Date

/**
 * Geometris ELD Data Model
 * Contains all data received from Geometris BLE device
 */
data class GeometrisEldData(
    // Vehicle identification
    var vin: String? = null,
    var serialNumber: String? = null,

    // Engine data
    var rpm: Double? = null,
    var engineHours: Double? = null,  // In hours (device sends * 10)
    var rpmTimestamp: Date? = null,
    var engineHoursTimestamp: Date? = null,

    // Vehicle motion data
    var speed: Double? = null,  // km/h
    var odometer: Double? = null,  // kilometers
    var speedTimestamp: Date? = null,
    var odometerTimestamp: Date? = null,

    // GPS location data
    var latitude: Double? = null,
    var longitude: Double? = null,
    var gpsTime: Long? = null,  // Unix timestamp in milliseconds
    var locationTimestamp: Date? = null,

    // Unidentified driving events
    var unidentifiedEvents: MutableList<UnidentifiedEvent> = mutableListOf(),
    var totalUnidentifiedEvents: Int = 0,

    // Protocol info
    var protocolVersion: Int = 1,
    var timestamp: Date = Date()
) {
    /**
     * Check if engine is running based on RPM
     */
    fun isEngineRunning(): Boolean {
        return rpm != null && rpm!! > 200.0
    }

    /**
     * Check if vehicle is moving based on speed
     */
    fun isVehicleMoving(): Boolean {
        return speed != null && speed!! > 3.0  // > 3 km/h
    }

    /**
     * Get age of location data in seconds
     */
    fun getLocationAgeInSeconds(): Long? {
        return gpsTime?.let {
            (System.currentTimeMillis() - it) / 1000
        }
    }

    override fun toString(): String {
        return """
            GeometrisEldData(
                vin=$vin,
                rpm=$rpm,
                speed=$speed km/h,
                odometer=$odometer km,
                engineHours=$engineHours hrs,
                lat=$latitude,
                lon=$longitude,
                unidentifiedEvents=${unidentifiedEvents.size}
            )
        """.trimIndent()
    }
}
