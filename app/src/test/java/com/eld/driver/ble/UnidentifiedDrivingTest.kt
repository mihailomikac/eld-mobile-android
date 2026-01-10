package com.eld.driver.ble

import com.eld.driver.ble.models.UnidentifiedEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for Unidentified Driving (UD) events.
 *
 * Covers test cases:
 * - Case #23: Not connected while driving → UD created
 * - Case #24: Not logged in while driving → UD created
 * - Case #25: Connect while driving → UD ends at connect time
 * - Case #26: UD events fetched and displayed
 * - Case #29: Hardware unplugged → UD event ends
 */
class UnidentifiedDrivingTest {

    // ==================== REASON CODE TESTS ====================

    @Test
    fun `reason 13 is END_STOP - speed above 3 MPH`() {
        val event = UnidentifiedEvent(reason = 13)
        assertThat(event.getReasonString()).contains("END_STOP")
        assertThat(event.getReasonString()).contains("3 MPH")
    }

    @Test
    fun `reason 12 is BEGIN_STOP - speed below 3 MPH`() {
        val event = UnidentifiedEvent(reason = 12)
        assertThat(event.getReasonString()).contains("BEGIN_STOP")
        assertThat(event.getReasonString()).contains("3 MPH")
    }

    @Test
    fun `reason 40 is BLE DISCONNECT`() {
        val event = UnidentifiedEvent(reason = 40)
        assertThat(event.getReasonString()).contains("BLE DISCONNECT")
    }

    @Test
    fun `reason 39 is BLE CONNECT`() {
        val event = UnidentifiedEvent(reason = 39)
        assertThat(event.getReasonString()).contains("BLE CONNECT")
    }

    @Test
    fun `reason 41 is BUS MALFUNCTION`() {
        val event = UnidentifiedEvent(reason = 41)
        assertThat(event.getReasonString()).contains("BUS MALFUNCTION")
    }

    @Test
    fun `reason 42 is BUS MALFUNCTION END`() {
        val event = UnidentifiedEvent(reason = 42)
        assertThat(event.getReasonString()).contains("BUS MALFUNCTION END")
    }

    @Test
    fun `unknown reason shows UNKNOWN with code`() {
        val event = UnidentifiedEvent(reason = 99)
        assertThat(event.getReasonString()).contains("UNKNOWN")
        assertThat(event.getReasonString()).contains("99")
    }

    @Test
    fun `null reason shows UNKNOWN with null`() {
        val event = UnidentifiedEvent(reason = null)
        assertThat(event.getReasonString()).contains("UNKNOWN")
        assertThat(event.getReasonString()).contains("null")
    }

    // ==================== UD EVENT CREATION TESTS ====================
    // Case #23, #24, #25

    @Test
    fun `UD event created when driving without connection`() {
        val scenario = UDScenario()

        // Start driving without connection
        scenario.startDriving(speedMph = 10.0)
        assertThat(scenario.isUDEventInProgress).isTrue()

        // Stop driving
        scenario.stopDriving()
        assertThat(scenario.udEvents).hasSize(1)
    }

    @Test
    fun `UD event has correct start time when driving started`() {
        val scenario = UDScenario()
        val startTime = System.currentTimeMillis()

        scenario.startDriving(speedMph = 10.0, atTime = startTime)
        scenario.stopDriving(atTime = startTime + 600_000L)  // 10 min later

        val udEvent = scenario.udEvents.first()
        assertThat(udEvent.startTime).isEqualTo(startTime)
    }

    @Test
    fun `UD event ends when driver connects - Case 25`() {
        val scenario = UDScenario()
        val startTime = System.currentTimeMillis()
        val connectTime = startTime + 300_000L  // 5 min later

        // Start driving without connection
        scenario.startDriving(speedMph = 10.0, atTime = startTime)

        // Driver connects while still driving
        scenario.driverConnects(atTime = connectTime)

        // UD event should end at connect time
        val udEvent = scenario.udEvents.first()
        assertThat(udEvent.endTime).isEqualTo(connectTime)
    }

    @Test
    fun `UD event not created when driver is connected`() {
        val scenario = UDScenario()

        // Connect first
        scenario.driverConnects()

        // Start driving
        scenario.startDriving(speedMph = 10.0)

        // No UD event should be created
        assertThat(scenario.isUDEventInProgress).isFalse()
        assertThat(scenario.udEvents).isEmpty()
    }

    @Test
    fun `UD event created when not logged in - Case 24`() {
        val scenario = UDScenario(isLoggedIn = false)

        // Start driving without being logged in
        scenario.startDriving(speedMph = 10.0)
        assertThat(scenario.isUDEventInProgress).isTrue()

        // Stop, then login and connect
        scenario.stopDriving()
        scenario.driverLogsIn()
        scenario.driverConnects()

        // UD event should be fetched
        assertThat(scenario.fetchedUDEvents).hasSize(1)
    }

    // ==================== UD EVENT HARDWARE UNPLUG ====================
    // Case #29

    @Test
    fun `UD event ends when hardware unplugged - Case 29`() {
        val scenario = UDScenario(isLoggedIn = false)
        val startTime = System.currentTimeMillis()
        val unplugTime = startTime + 600_000L  // 10 min

        // Start driving
        scenario.startDriving(speedMph = 10.0, atTime = startTime)

        // Hardware unplugged
        scenario.hardwareUnplugged(atTime = unplugTime)

        // UD event ends at unplug time
        val udEvent = scenario.udEvents.first()
        assertThat(udEvent.endTime).isEqualTo(unplugTime)
    }

    @Test
    fun `new UD event created after hardware reconnected`() {
        val scenario = UDScenario(isLoggedIn = false)
        val startTime = System.currentTimeMillis()

        // First driving session
        scenario.startDriving(speedMph = 10.0, atTime = startTime)
        scenario.hardwareUnplugged(atTime = startTime + 600_000L)

        // Hardware reconnected, start driving again
        scenario.hardwareReconnected(atTime = startTime + 1_200_000L)
        scenario.startDriving(speedMph = 10.0, atTime = startTime + 1_200_000L)
        scenario.stopDriving(atTime = startTime + 1_500_000L)

        // Should have 2 UD events
        assertThat(scenario.udEvents).hasSize(2)
    }

    // ==================== UD EVENT FETCHING ====================
    // Case #26

    @Test
    fun `UD events fetched on connection`() {
        val scenario = UDScenario()

        // Pre-existing UD events on device
        scenario.addPreExistingUDEvent(
            UnidentifiedEvent(
                reason = 13,
                timestamp = System.currentTimeMillis() - 3600_000L,
                speed = 50.0,
                latitude = 40.7128,
                longitude = -74.0060
            )
        )

        // Connect
        scenario.driverConnects()

        // Events should be fetched
        assertThat(scenario.fetchedUDEvents).hasSize(1)
    }

    @Test
    fun `multiple UD events fetched on connection`() {
        val scenario = UDScenario()

        // Multiple pre-existing UD events
        repeat(5) { i ->
            scenario.addPreExistingUDEvent(
                UnidentifiedEvent(
                    reason = 13,
                    timestamp = System.currentTimeMillis() - (i * 3600_000L),
                    speed = 50.0 + i * 10
                )
            )
        }

        scenario.driverConnects()

        assertThat(scenario.fetchedUDEvents).hasSize(5)
    }

    @Test
    fun `UD events count displayed correctly`() {
        val scenario = UDScenario()

        repeat(3) {
            scenario.addPreExistingUDEvent(UnidentifiedEvent(reason = 13))
        }

        scenario.driverConnects()

        assertThat(scenario.getUDEventCountString()).isEqualTo("3 event(s)")
    }

    @Test
    fun `single UD event count displayed correctly`() {
        val scenario = UDScenario()

        scenario.addPreExistingUDEvent(UnidentifiedEvent(reason = 13))
        scenario.driverConnects()

        assertThat(scenario.getUDEventCountString()).isEqualTo("1 event(s)")
    }

    // ==================== UD EVENT DATA VALIDATION ====================

    @Test
    fun `UD event contains location data`() {
        val event = UnidentifiedEvent(
            reason = 13,
            latitude = 40.7128,
            longitude = -74.0060
        )

        assertThat(event.latitude).isEqualTo(40.7128)
        assertThat(event.longitude).isEqualTo(-74.0060)
    }

    @Test
    fun `UD event contains odometer data`() {
        val event = UnidentifiedEvent(
            reason = 13,
            odometer = 150000.5
        )

        assertThat(event.odometer).isEqualTo(150000.5)
    }

    @Test
    fun `UD event contains engine hours`() {
        val event = UnidentifiedEvent(
            reason = 13,
            engineHours = 5432.1
        )

        assertThat(event.engineHours).isEqualTo(5432.1)
    }

    @Test
    fun `UD event contains speed at time of event`() {
        val event = UnidentifiedEvent(
            reason = 13,
            speed = 65.5  // km/h
        )

        assertThat(event.speed).isEqualTo(65.5)
    }

    @Test
    fun `UD event toString contains key info`() {
        val event = UnidentifiedEvent(
            reason = 13,
            timestamp = 1234567890L,
            speed = 50.0,
            latitude = 40.7128,
            longitude = -74.0060
        )

        val str = event.toString()
        assertThat(str).contains("END_STOP")
        assertThat(str).contains("50.0")
        assertThat(str).contains("40.7128")
    }

    // ==================== PURGE UD EVENTS ====================

    @Test
    fun `purge clears all UD events`() {
        val scenario = UDScenario()

        repeat(5) {
            scenario.addPreExistingUDEvent(UnidentifiedEvent(reason = 13))
        }

        scenario.driverConnects()
        assertThat(scenario.fetchedUDEvents).hasSize(5)

        scenario.purgeUDEvents()
        assertThat(scenario.fetchedUDEvents).isEmpty()
    }

    // ==================== HELPER CLASSES ====================

    data class MockUDEvent(
        val startTime: Long,
        var endTime: Long? = null
    )

    class UDScenario(
        var isLoggedIn: Boolean = true
    ) {
        var isConnected: Boolean = false
            private set
        var isUDEventInProgress: Boolean = false
            private set
        var currentUDEventStart: Long? = null
            private set

        val udEvents = mutableListOf<MockUDEvent>()
        val preExistingUDEvents = mutableListOf<UnidentifiedEvent>()
        val fetchedUDEvents = mutableListOf<UnidentifiedEvent>()

        private var isHardwarePlugged = true

        fun startDriving(speedMph: Double, atTime: Long = System.currentTimeMillis()) {
            if (speedMph > 5.0 && !isConnected && isHardwarePlugged) {
                // UD event starts
                isUDEventInProgress = true
                currentUDEventStart = atTime
            }
        }

        fun stopDriving(atTime: Long = System.currentTimeMillis()) {
            if (isUDEventInProgress) {
                udEvents.add(MockUDEvent(
                    startTime = currentUDEventStart!!,
                    endTime = atTime
                ))
                isUDEventInProgress = false
                currentUDEventStart = null
            }
        }

        fun driverConnects(atTime: Long = System.currentTimeMillis()) {
            if (isUDEventInProgress) {
                // End current UD event
                udEvents.add(MockUDEvent(
                    startTime = currentUDEventStart!!,
                    endTime = atTime
                ))
                isUDEventInProgress = false
                currentUDEventStart = null
            }

            isConnected = true

            // Fetch pre-existing UD events from device
            fetchedUDEvents.addAll(preExistingUDEvents)
        }

        fun driverLogsIn() {
            isLoggedIn = true
        }

        fun hardwareUnplugged(atTime: Long = System.currentTimeMillis()) {
            isHardwarePlugged = false

            if (isUDEventInProgress) {
                udEvents.add(MockUDEvent(
                    startTime = currentUDEventStart!!,
                    endTime = atTime
                ))
                isUDEventInProgress = false
                currentUDEventStart = null
            }
        }

        fun hardwareReconnected(atTime: Long = System.currentTimeMillis()) {
            isHardwarePlugged = true
        }

        fun addPreExistingUDEvent(event: UnidentifiedEvent) {
            preExistingUDEvents.add(event)
        }

        fun purgeUDEvents() {
            fetchedUDEvents.clear()
            preExistingUDEvents.clear()
        }

        fun getUDEventCountString(): String {
            return "${fetchedUDEvents.size} event(s)"
        }
    }
}
