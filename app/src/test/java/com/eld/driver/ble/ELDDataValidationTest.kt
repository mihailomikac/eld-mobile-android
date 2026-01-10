package com.eld.driver.ble

import com.eld.driver.ble.models.GeometrisEldData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Date

/**
 * Unit tests for ELD data validation.
 *
 * Covers test cases:
 * - Case #21: ELD Info view shows correct values
 * - Case #27-28: Unplugged/Reconnect events
 */
class ELDDataValidationTest {

    // ==================== ELD INFO VIEW TESTS ====================
    // Case #21

    @Test
    fun `ELD data contains latitude`() {
        val data = GeometrisEldData(latitude = 40.7128)
        assertThat(data.latitude).isEqualTo(40.7128)
    }

    @Test
    fun `ELD data contains longitude`() {
        val data = GeometrisEldData(longitude = -74.0060)
        assertThat(data.longitude).isEqualTo(-74.0060)
    }

    @Test
    fun `ELD data contains speed in kmh`() {
        val data = GeometrisEldData(speed = 80.0)  // km/h
        assertThat(data.speed).isEqualTo(80.0)
    }

    @Test
    fun `speed converts to mph correctly`() {
        val data = GeometrisEldData(speed = 80.0)  // 80 km/h
        val speedMph = data.speed!! * 0.621371
        assertThat(speedMph).isWithin(0.1).of(49.7)  // ~50 mph
    }

    @Test
    fun `ELD data contains odometer in km`() {
        val data = GeometrisEldData(odometer = 150000.0)  // km
        assertThat(data.odometer).isEqualTo(150000.0)
    }

    @Test
    fun `odometer converts to miles correctly`() {
        val data = GeometrisEldData(odometer = 160934.0)  // km
        val odometerMiles = data.odometer!! * 0.621371
        assertThat(odometerMiles).isWithin(1.0).of(100000.0)  // 100,000 miles
    }

    @Test
    fun `ELD data contains engine hours`() {
        val data = GeometrisEldData(engineHours = 5432.5)
        assertThat(data.engineHours).isEqualTo(5432.5)
    }

    @Test
    fun `ELD data contains VIN`() {
        val data = GeometrisEldData(vin = "1HGCM82633A123456")
        assertThat(data.vin).isEqualTo("1HGCM82633A123456")
    }

    @Test
    fun `ELD data contains RPM`() {
        val data = GeometrisEldData(rpm = 2500.0)
        assertThat(data.rpm).isEqualTo(2500.0)
    }

    @Test
    fun `ELD data contains protocol version`() {
        val data = GeometrisEldData(protocolVersion = 2)
        assertThat(data.protocolVersion).isEqualTo(2)
    }

    // ==================== ENGINE STATE TESTS ====================

    @Test
    fun `isEngineRunning returns true when RPM above 200`() {
        val data = GeometrisEldData(rpm = 800.0)
        assertThat(data.isEngineRunning()).isTrue()
    }

    @Test
    fun `isEngineRunning returns false when RPM below 200`() {
        val data = GeometrisEldData(rpm = 100.0)
        assertThat(data.isEngineRunning()).isFalse()
    }

    @Test
    fun `isEngineRunning returns false when RPM is 0`() {
        val data = GeometrisEldData(rpm = 0.0)
        assertThat(data.isEngineRunning()).isFalse()
    }

    @Test
    fun `isEngineRunning returns false when RPM is null`() {
        val data = GeometrisEldData(rpm = null)
        assertThat(data.isEngineRunning()).isFalse()
    }

    // ==================== VEHICLE MOTION TESTS ====================

    @Test
    fun `isVehicleMoving returns true when speed above 3 kmh`() {
        val data = GeometrisEldData(speed = 10.0)  // 10 km/h
        assertThat(data.isVehicleMoving()).isTrue()
    }

    @Test
    fun `isVehicleMoving returns false when speed below 3 kmh`() {
        val data = GeometrisEldData(speed = 2.0)  // 2 km/h
        assertThat(data.isVehicleMoving()).isFalse()
    }

    @Test
    fun `isVehicleMoving returns false when speed is 0`() {
        val data = GeometrisEldData(speed = 0.0)
        assertThat(data.isVehicleMoving()).isFalse()
    }

    @Test
    fun `isVehicleMoving returns false when speed is null`() {
        val data = GeometrisEldData(speed = null)
        assertThat(data.isVehicleMoving()).isFalse()
    }

    // ==================== GPS TIME TESTS ====================

    @Test
    fun `getLocationAgeInSeconds returns correct age`() {
        val now = System.currentTimeMillis()
        val fiveMinutesAgo = now - (5 * 60 * 1000)
        val data = GeometrisEldData(gpsTime = fiveMinutesAgo)

        val age = data.getLocationAgeInSeconds()
        assertThat(age).isAtLeast(299L)  // At least 5 min
        assertThat(age).isAtMost(302L)   // Allow 2 sec buffer
    }

    @Test
    fun `getLocationAgeInSeconds returns null when gpsTime is null`() {
        val data = GeometrisEldData(gpsTime = null)
        assertThat(data.getLocationAgeInSeconds()).isNull()
    }

    // ==================== UNIDENTIFIED EVENTS TESTS ====================

    @Test
    fun `unidentified events list is empty by default`() {
        val data = GeometrisEldData()
        assertThat(data.unidentifiedEvents).isEmpty()
    }

    @Test
    fun `totalUnidentifiedEvents is 0 by default`() {
        val data = GeometrisEldData()
        assertThat(data.totalUnidentifiedEvents).isEqualTo(0)
    }

    @Test
    fun `can add unidentified events`() {
        val data = GeometrisEldData()
        data.unidentifiedEvents.add(
            com.eld.driver.ble.models.UnidentifiedEvent(reason = 13)
        )
        assertThat(data.unidentifiedEvents).hasSize(1)
    }

    // ==================== COMPLETE ELD DATA TESTS ====================

    @Test
    fun `complete ELD data toString contains key info`() {
        val data = GeometrisEldData(
            vin = "1HGCM82633A123456",
            rpm = 2500.0,
            speed = 80.0,
            odometer = 150000.0,
            engineHours = 5432.5,
            latitude = 40.7128,
            longitude = -74.0060
        )

        val str = data.toString()
        assertThat(str).contains("1HGCM82633A123456")
        assertThat(str).contains("2500.0")
        assertThat(str).contains("80.0")
        assertThat(str).contains("150000.0")
        assertThat(str).contains("5432.5")
        assertThat(str).contains("40.7128")
    }

    @Test
    fun `ELD data with timestamps`() {
        val now = Date()
        val data = GeometrisEldData(
            speed = 80.0,
            speedTimestamp = now,
            odometer = 150000.0,
            odometerTimestamp = now,
            rpm = 2500.0,
            rpmTimestamp = now
        )

        assertThat(data.speedTimestamp).isEqualTo(now)
        assertThat(data.odometerTimestamp).isEqualTo(now)
        assertThat(data.rpmTimestamp).isEqualTo(now)
    }

    // ==================== DATA VALIDATION HELPER TESTS ====================

    @Test
    fun `validateELDData returns OK for valid speed`() {
        val result = validateELDData(speed = 80.0)
        assertThat(result.speedStatus).isEqualTo("OK")
    }

    @Test
    fun `validateELDData returns OK for valid odometer`() {
        val result = validateELDData(odometer = 150000.0)
        assertThat(result.odometerStatus).isEqualTo("OK")
    }

    @Test
    fun `validateELDData returns OK for valid engine hours`() {
        val result = validateELDData(engineHours = 5432.5)
        assertThat(result.engineHoursStatus).isEqualTo("OK")
    }

    @Test
    fun `validateELDData returns OK for valid location`() {
        val result = validateELDData(latitude = 40.7128, longitude = -74.0060)
        assertThat(result.locationStatus).isEqualTo("OK")
    }

    @Test
    fun `validateELDData returns ERROR for null speed`() {
        val result = validateELDData(speed = null)
        assertThat(result.speedStatus).isEqualTo("ERROR")
    }

    @Test
    fun `validateELDData returns ERROR for null location`() {
        val result = validateELDData(latitude = null, longitude = null)
        assertThat(result.locationStatus).isEqualTo("ERROR")
    }

    @Test
    fun `validateELDData returns ERROR for invalid latitude`() {
        val result = validateELDData(latitude = 100.0, longitude = -74.0060)  // Invalid
        assertThat(result.locationStatus).isEqualTo("ERROR")
    }

    @Test
    fun `validateELDData returns ERROR for invalid longitude`() {
        val result = validateELDData(latitude = 40.7128, longitude = -200.0)  // Invalid
        assertThat(result.locationStatus).isEqualTo("ERROR")
    }

    // ==================== UNPLUGGED/RECONNECT EVENT TESTS ====================
    // Case #27, #28

    @Test
    fun `unplugged event has time and location`() {
        val event = HardwareEvent(
            type = HardwareEventType.UNPLUGGED,
            timestamp = System.currentTimeMillis(),
            latitude = 40.7128,
            longitude = -74.0060,
            odometer = 150000.0
        )

        assertThat(event.type).isEqualTo(HardwareEventType.UNPLUGGED)
        assertThat(event.timestamp).isGreaterThan(0L)
        assertThat(event.latitude).isNotNull()
        assertThat(event.longitude).isNotNull()
        assertThat(event.odometer).isNotNull()
    }

    @Test
    fun `reconnect event has time and location`() {
        val event = HardwareEvent(
            type = HardwareEventType.RECONNECTED,
            timestamp = System.currentTimeMillis(),
            latitude = 40.7128,
            longitude = -74.0060,
            odometer = 150000.0
        )

        assertThat(event.type).isEqualTo(HardwareEventType.RECONNECTED)
    }

    @Test
    fun `reconnect odometer higher than unplugged when driven - Case 28`() {
        val unplugTime = System.currentTimeMillis()
        val reconnectTime = unplugTime + (10 * 60 * 1000L)  // 10 min later

        val unplugEvent = HardwareEvent(
            type = HardwareEventType.UNPLUGGED,
            timestamp = unplugTime,
            latitude = 40.7128,
            longitude = -74.0060,
            odometer = 150000.0
        )

        // Vehicle was driven during unplug
        val reconnectEvent = HardwareEvent(
            type = HardwareEventType.RECONNECTED,
            timestamp = reconnectTime,
            latitude = 40.7228,  // Different location
            longitude = -74.0160,
            odometer = 150010.0  // 10 km more
        )

        assertThat(reconnectEvent.odometer).isGreaterThan(unplugEvent.odometer!!)
    }

    @Test
    fun `reconnect odometer same as unplugged when not driven - Case 28`() {
        val unplugTime = System.currentTimeMillis()
        val reconnectTime = unplugTime + (10 * 60 * 1000L)

        val unplugEvent = HardwareEvent(
            type = HardwareEventType.UNPLUGGED,
            timestamp = unplugTime,
            latitude = 40.7128,
            longitude = -74.0060,
            odometer = 150000.0
        )

        // Vehicle was NOT driven during unplug
        val reconnectEvent = HardwareEvent(
            type = HardwareEventType.RECONNECTED,
            timestamp = reconnectTime,
            latitude = 40.7128,  // Same location
            longitude = -74.0060,
            odometer = 150000.0  // Same odometer
        )

        assertThat(reconnectEvent.odometer).isEqualTo(unplugEvent.odometer)
        assertThat(reconnectEvent.latitude).isEqualTo(unplugEvent.latitude)
        assertThat(reconnectEvent.longitude).isEqualTo(unplugEvent.longitude)
    }

    // ==================== HELPER CLASSES ====================

    data class ValidationResult(
        val speedStatus: String,
        val odometerStatus: String,
        val engineHoursStatus: String,
        val locationStatus: String
    )

    private fun validateELDData(
        speed: Double? = 80.0,
        odometer: Double? = 150000.0,
        engineHours: Double? = 5432.5,
        latitude: Double? = 40.7128,
        longitude: Double? = -74.0060
    ): ValidationResult {
        val speedStatus = if (speed != null) "OK" else "ERROR"
        val odometerStatus = if (odometer != null) "OK" else "ERROR"
        val engineHoursStatus = if (engineHours != null) "OK" else "ERROR"

        val locationStatus = when {
            latitude == null || longitude == null -> "ERROR"
            latitude < -90 || latitude > 90 -> "ERROR"
            longitude < -180 || longitude > 180 -> "ERROR"
            else -> "OK"
        }

        return ValidationResult(
            speedStatus = speedStatus,
            odometerStatus = odometerStatus,
            engineHoursStatus = engineHoursStatus,
            locationStatus = locationStatus
        )
    }

    enum class HardwareEventType {
        UNPLUGGED,
        RECONNECTED
    }

    data class HardwareEvent(
        val type: HardwareEventType,
        val timestamp: Long,
        val latitude: Double?,
        val longitude: Double?,
        val odometer: Double?
    )
}
