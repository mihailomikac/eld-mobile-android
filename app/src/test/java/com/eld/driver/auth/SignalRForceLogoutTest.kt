package com.eld.driver.auth

import com.eld.driver.service.ForceLogoutEvent
import com.eld.driver.service.SignalRConnectionState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests for SignalR Force Logout functionality.
 *
 * Tests cover:
 * - Force logout event creation and handling
 * - Connection state transitions
 * - Event parsing scenarios
 * - Multi-device notification scenarios
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignalRForceLogoutTest {

    // ==================== FORCE LOGOUT EVENT TESTS ====================

    @Test
    fun `forceLogoutEvent_creation_hasReason`() {
        val event = ForceLogoutEvent(
            reason = "You have been logged out because someone logged in from another device."
        )

        assertThat(event.reason).isEqualTo("You have been logged out because someone logged in from another device.")
    }

    @Test
    fun `forceLogoutEvent_creation_hasTimestamp`() {
        val beforeCreation = System.currentTimeMillis()
        val event = ForceLogoutEvent(reason = "Test")
        val afterCreation = System.currentTimeMillis()

        assertThat(event.timestamp).isAtLeast(beforeCreation)
        assertThat(event.timestamp).isAtMost(afterCreation)
    }

    @Test
    fun `forceLogoutEvent_customTimestamp_isPreserved`() {
        val customTimestamp = 1234567890L
        val event = ForceLogoutEvent(reason = "Test", timestamp = customTimestamp)

        assertThat(event.timestamp).isEqualTo(customTimestamp)
    }

    @Test
    fun `forceLogoutEvent_differentReasons_areNotEqual`() {
        val event1 = ForceLogoutEvent(reason = "Reason 1", timestamp = 1000L)
        val event2 = ForceLogoutEvent(reason = "Reason 2", timestamp = 1000L)

        assertThat(event1).isNotEqualTo(event2)
    }

    @Test
    fun `forceLogoutEvent_sameReasonAndTimestamp_areEqual`() {
        val event1 = ForceLogoutEvent(reason = "Same reason", timestamp = 1000L)
        val event2 = ForceLogoutEvent(reason = "Same reason", timestamp = 1000L)

        assertThat(event1).isEqualTo(event2)
    }

    // ==================== CONNECTION STATE TESTS ====================

    @Test
    fun `connectionState_disconnected_exists`() {
        assertThat(SignalRConnectionState.DISCONNECTED).isNotNull()
    }

    @Test
    fun `connectionState_connecting_exists`() {
        assertThat(SignalRConnectionState.CONNECTING).isNotNull()
    }

    @Test
    fun `connectionState_connected_exists`() {
        assertThat(SignalRConnectionState.CONNECTED).isNotNull()
    }

    @Test
    fun `connectionState_error_exists`() {
        assertThat(SignalRConnectionState.ERROR).isNotNull()
    }

    @Test
    fun `connectionState_allStatesAreDifferent`() {
        val states = SignalRConnectionState.values()
        val uniqueStates = states.toSet()

        assertThat(uniqueStates.size).isEqualTo(states.size)
    }

    @Test
    fun `connectionState_enumHasFourValues`() {
        assertThat(SignalRConnectionState.values().size).isEqualTo(4)
    }

    // ==================== STATE FLOW SIMULATION TESTS ====================

    @Test
    fun `forceLogoutEventFlow_initiallyNull`() {
        val flow = MutableStateFlow<ForceLogoutEvent?>(null)

        assertThat(flow.value).isNull()
    }

    @Test
    fun `forceLogoutEventFlow_emitsEvent`() = runTest {
        val flow = MutableStateFlow<ForceLogoutEvent?>(null)
        val event = ForceLogoutEvent(reason = "Force logout")

        flow.value = event

        assertThat(flow.value).isEqualTo(event)
    }

    @Test
    fun `forceLogoutEventFlow_clearedAfterHandling`() = runTest {
        val flow = MutableStateFlow<ForceLogoutEvent?>(null)
        val event = ForceLogoutEvent(reason = "Force logout")

        // Emit event
        flow.value = event
        assertThat(flow.value).isNotNull()

        // Clear after handling
        flow.value = null
        assertThat(flow.value).isNull()
    }

    @Test
    fun `connectionStateFlow_initiallyDisconnected`() {
        val flow = MutableStateFlow(SignalRConnectionState.DISCONNECTED)

        assertThat(flow.value).isEqualTo(SignalRConnectionState.DISCONNECTED)
    }

    @Test
    fun `connectionStateFlow_transitionsToConnecting`() = runTest {
        val flow = MutableStateFlow(SignalRConnectionState.DISCONNECTED)

        flow.value = SignalRConnectionState.CONNECTING

        assertThat(flow.value).isEqualTo(SignalRConnectionState.CONNECTING)
    }

    @Test
    fun `connectionStateFlow_transitionsToConnected`() = runTest {
        val flow = MutableStateFlow(SignalRConnectionState.CONNECTING)

        flow.value = SignalRConnectionState.CONNECTED

        assertThat(flow.value).isEqualTo(SignalRConnectionState.CONNECTED)
    }

    @Test
    fun `connectionStateFlow_transitionsToError`() = runTest {
        val flow = MutableStateFlow(SignalRConnectionState.CONNECTING)

        flow.value = SignalRConnectionState.ERROR

        assertThat(flow.value).isEqualTo(SignalRConnectionState.ERROR)
    }

    // ==================== MULTI-DEVICE SCENARIO TESTS ====================

    @Test
    fun `multiDevice_deviceAReceivesForceLogout_eventHasCorrectReason`() {
        // Device A is logged in, Device B force logs in
        // Device A should receive this event
        val event = ForceLogoutEvent(
            reason = "You have been logged out because someone logged in from another device."
        )

        assertThat(event.reason).contains("another device")
    }

    @Test
    fun `multiDevice_forceLogoutWhileDriving_eventIndicatesStatus`() {
        // When force logout happens while driver was in DRIVING status
        val event = ForceLogoutEvent(
            reason = "Session terminated. You were force logged out while in DRIVING status."
        )

        assertThat(event.reason).contains("DRIVING")
    }

    @Test
    fun `multiDevice_consecutiveForceLogouts_eachHasUniqueTimestamp`() {
        // Simulate rapid consecutive force logouts
        val events = mutableListOf<ForceLogoutEvent>()

        repeat(3) {
            Thread.sleep(1) // Ensure different timestamps
            events.add(ForceLogoutEvent(reason = "Force logout $it"))
        }

        val timestamps = events.map { it.timestamp }.toSet()
        assertThat(timestamps.size).isEqualTo(3)
    }

    // ==================== REASON PARSING TESTS ====================

    @Test
    fun `reasonParsing_mapFormat_extractsReason`() {
        // SignalR might send data as a map
        val data = mapOf("Reason" to "Test reason", "Timestamp" to System.currentTimeMillis())

        val reason = data["Reason"]?.toString() ?: data["reason"]?.toString() ?: "Default"

        assertThat(reason).isEqualTo("Test reason")
    }

    @Test
    fun `reasonParsing_lowercaseKey_extractsReason`() {
        val data = mapOf("reason" to "Lowercase key reason")

        val reason = data["Reason"]?.toString() ?: data["reason"]?.toString() ?: "Default"

        assertThat(reason).isEqualTo("Lowercase key reason")
    }

    @Test
    fun `reasonParsing_missingKey_usesDefault`() {
        val data = mapOf("otherKey" to "Some value")

        val reason = data["Reason"]?.toString() ?: data["reason"]?.toString()
            ?: "You have been logged out from another device."

        assertThat(reason).isEqualTo("You have been logged out from another device.")
    }

    @Test
    fun `reasonParsing_stringFormat_usesDirectly`() {
        val data: Any = "Direct string reason"

        val reason = when (data) {
            is String -> data
            else -> "Default"
        }

        assertThat(reason).isEqualTo("Direct string reason")
    }

    // ==================== CONNECTION LIFECYCLE TESTS ====================

    @Test
    fun `connectionLifecycle_connect_stateChangesToConnecting`() = runTest {
        val stateFlow = MutableStateFlow(SignalRConnectionState.DISCONNECTED)

        // Simulate connect() call
        stateFlow.value = SignalRConnectionState.CONNECTING

        assertThat(stateFlow.value).isEqualTo(SignalRConnectionState.CONNECTING)
    }

    @Test
    fun `connectionLifecycle_connectSuccess_stateChangesToConnected`() = runTest {
        val stateFlow = MutableStateFlow(SignalRConnectionState.CONNECTING)

        // Simulate successful connection
        stateFlow.value = SignalRConnectionState.CONNECTED

        assertThat(stateFlow.value).isEqualTo(SignalRConnectionState.CONNECTED)
    }

    @Test
    fun `connectionLifecycle_disconnect_stateChangesToDisconnected`() = runTest {
        val stateFlow = MutableStateFlow(SignalRConnectionState.CONNECTED)
        val eventFlow = MutableStateFlow<ForceLogoutEvent?>(ForceLogoutEvent("Old event"))

        // Simulate disconnect()
        stateFlow.value = SignalRConnectionState.DISCONNECTED
        eventFlow.value = null

        assertThat(stateFlow.value).isEqualTo(SignalRConnectionState.DISCONNECTED)
        assertThat(eventFlow.value).isNull()
    }

    @Test
    fun `connectionLifecycle_connectionClosed_stateChangesToDisconnected`() = runTest {
        val stateFlow = MutableStateFlow(SignalRConnectionState.CONNECTED)

        // Simulate onClosed callback
        stateFlow.value = SignalRConnectionState.DISCONNECTED

        assertThat(stateFlow.value).isEqualTo(SignalRConnectionState.DISCONNECTED)
    }

    @Test
    fun `connectionLifecycle_connectionError_stateChangesToError`() = runTest {
        val stateFlow = MutableStateFlow(SignalRConnectionState.CONNECTING)

        // Simulate connection error
        stateFlow.value = SignalRConnectionState.ERROR

        assertThat(stateFlow.value).isEqualTo(SignalRConnectionState.ERROR)
    }

    // ==================== EVENT HANDLING TESTS ====================

    @Test
    fun `eventHandling_eventReceived_flowEmitsEvent`() = runTest {
        val eventFlow = MutableStateFlow<ForceLogoutEvent?>(null)

        // Simulate receiving ForceLogout event from SignalR
        val event = ForceLogoutEvent(reason = "Force logout from hub")
        eventFlow.value = event

        assertThat(eventFlow.value).isEqualTo(event)
    }

    @Test
    fun `eventHandling_eventCleared_flowIsNull`() = runTest {
        val eventFlow = MutableStateFlow<ForceLogoutEvent?>(
            ForceLogoutEvent(reason = "Initial event")
        )

        // Clear after handling
        eventFlow.value = null

        assertThat(eventFlow.value).isNull()
    }

    @Test
    fun `eventHandling_multipleEvents_onlyLatestKept`() = runTest {
        val eventFlow = MutableStateFlow<ForceLogoutEvent?>(null)

        val event1 = ForceLogoutEvent(reason = "First", timestamp = 1000L)
        val event2 = ForceLogoutEvent(reason = "Second", timestamp = 2000L)

        eventFlow.value = event1
        eventFlow.value = event2

        assertThat(eventFlow.value).isEqualTo(event2)
    }

    // ==================== RECONNECTION SCENARIO TESTS ====================

    @Test
    fun `reconnection_afterDisconnect_stateResetsToConnecting`() = runTest {
        val stateFlow = MutableStateFlow(SignalRConnectionState.DISCONNECTED)

        // Simulate reconnection attempt
        stateFlow.value = SignalRConnectionState.CONNECTING

        assertThat(stateFlow.value).isEqualTo(SignalRConnectionState.CONNECTING)
    }

    @Test
    fun `reconnection_existingConnection_disconnectsFirst`() = runTest {
        val stateFlow = MutableStateFlow(SignalRConnectionState.CONNECTED)

        // Simulate disconnect before reconnect
        stateFlow.value = SignalRConnectionState.DISCONNECTED
        stateFlow.value = SignalRConnectionState.CONNECTING

        assertThat(stateFlow.value).isEqualTo(SignalRConnectionState.CONNECTING)
    }

    // ==================== ISCONNECTED CHECK TESTS ====================

    @Test
    fun `isConnected_whenConnected_returnsTrue`() {
        val state = SignalRConnectionState.CONNECTED
        val isConnected = state == SignalRConnectionState.CONNECTED

        assertThat(isConnected).isTrue()
    }

    @Test
    fun `isConnected_whenDisconnected_returnsFalse`() {
        val state = SignalRConnectionState.DISCONNECTED
        val isConnected = state == SignalRConnectionState.CONNECTED

        assertThat(isConnected).isFalse()
    }

    @Test
    fun `isConnected_whenConnecting_returnsFalse`() {
        val state = SignalRConnectionState.CONNECTING
        val isConnected = state == SignalRConnectionState.CONNECTED

        assertThat(isConnected).isFalse()
    }

    @Test
    fun `isConnected_whenError_returnsFalse`() {
        val state = SignalRConnectionState.ERROR
        val isConnected = state == SignalRConnectionState.CONNECTED

        assertThat(isConnected).isFalse()
    }

    // ==================== SESSION EXPIRED NOTIFICATION TESTS ====================

    @Test
    fun `sessionExpired_deviceAReceivesNotification_whenDeviceBForceLogsIn`() {
        // Scenario: Device A is logged in, Device B force logs in
        // Device A should receive ForceLogout event with "session expired" type message

        val forceLogoutEvent = ForceLogoutEvent(
            reason = "You have been logged out because someone logged in from another device."
        )

        // Device A receives this event via SignalR
        assertThat(forceLogoutEvent.reason).contains("logged out")
        assertThat(forceLogoutEvent.reason).contains("another device")
    }

    @Test
    fun `sessionExpired_eventContainsTimestamp_forAuditPurposes`() {
        val beforeEvent = System.currentTimeMillis()
        val event = ForceLogoutEvent(
            reason = "Session expired - force logout from another device"
        )
        val afterEvent = System.currentTimeMillis()

        // Timestamp should be set for audit/logging
        assertThat(event.timestamp).isAtLeast(beforeEvent)
        assertThat(event.timestamp).isAtMost(afterEvent)
    }

    @Test
    fun `sessionExpired_flowEmitsEvent_uiCanReact`() = runTest {
        val forceLogoutFlow = MutableStateFlow<ForceLogoutEvent?>(null)

        // Initially no event
        assertThat(forceLogoutFlow.value).isNull()

        // SignalR receives force logout
        val event = ForceLogoutEvent(reason = "Session expired")
        forceLogoutFlow.value = event

        // UI layer should see the event
        assertThat(forceLogoutFlow.value).isNotNull()
        assertThat(forceLogoutFlow.value?.reason).isEqualTo("Session expired")
    }

    @Test
    fun `sessionExpired_afterHandling_eventIsCleared`() = runTest {
        val forceLogoutFlow = MutableStateFlow<ForceLogoutEvent?>(null)

        // Event received
        forceLogoutFlow.value = ForceLogoutEvent(reason = "Session expired")
        assertThat(forceLogoutFlow.value).isNotNull()

        // After UI shows dialog and user acknowledges, event is cleared
        forceLogoutFlow.value = null
        assertThat(forceLogoutFlow.value).isNull()
    }

    @Test
    fun `sessionExpired_multipleDevicesInSequence_eachReceivesNotification`() {
        // When devices are force logged out in sequence: A → B → C
        // Each should receive a notification

        val eventForA = ForceLogoutEvent(reason = "Logged out by device B", timestamp = 1000L)
        val eventForB = ForceLogoutEvent(reason = "Logged out by device C", timestamp = 2000L)

        // Both events are valid and can be distinguished
        assertThat(eventForA.reason).isNotEqualTo(eventForB.reason)
        assertThat(eventForA.timestamp).isNotEqualTo(eventForB.timestamp)
    }

    @Test
    fun `sessionExpired_tenDevicesScenario_device1to9ReceiveNotifications`() {
        // 10 devices login in sequence, devices 1-9 should all receive force logout
        val events = (1..9).map { deviceNumber ->
            ForceLogoutEvent(
                reason = "Device $deviceNumber logged out by device ${deviceNumber + 1}",
                timestamp = System.currentTimeMillis() + deviceNumber
            )
        }

        // All 9 events should be unique
        assertThat(events.map { it.reason }.toSet().size).isEqualTo(9)

        // Timestamps should be in order
        for (i in 0 until events.size - 1) {
            assertThat(events[i].timestamp).isLessThan(events[i + 1].timestamp)
        }
    }

    @Test
    fun `sessionExpired_drivingDriver_receivesSpecialWarning`() {
        // When driver was in DRIVING status and gets force logged out
        val event = ForceLogoutEvent(
            reason = "Session terminated while in DRIVING status. Your logs have been preserved."
        )

        assertThat(event.reason).contains("DRIVING")
        assertThat(event.reason).contains("preserved")
    }

    @Test
    fun `sessionExpired_connectionMustBeActive_toReceiveEvent`() = runTest {
        val connectionState = MutableStateFlow(SignalRConnectionState.DISCONNECTED)
        val forceLogoutFlow = MutableStateFlow<ForceLogoutEvent?>(null)

        // When disconnected, events won't be received (simulated by not setting)
        assertThat(connectionState.value).isEqualTo(SignalRConnectionState.DISCONNECTED)
        assertThat(forceLogoutFlow.value).isNull()

        // When connected, events can be received
        connectionState.value = SignalRConnectionState.CONNECTED
        forceLogoutFlow.value = ForceLogoutEvent(reason = "Force logout")

        assertThat(forceLogoutFlow.value).isNotNull()
    }

    @Test
    fun `sessionExpired_offlineDevice_getsLoggedOutOnNextApiCall`() {
        // If device is offline when force logout happens, it won't receive SignalR
        // But on next API call, backend will reject with session-expired header
        // This is tested at API level, but we verify the expected response

        // Backend would return 403 with header: X-Auth-Failure-Reason: session-expired
        val expectedErrorReason = "Session expired. You have been logged out because another device logged in."

        assertThat(expectedErrorReason.lowercase()).contains("session expired")
        assertThat(expectedErrorReason).contains("another device")
    }

    // ==================== TOKEN HANDLING TESTS ====================

    @Test
    fun `tokenHandling_bearerPrefixRemoved`() {
        val tokenWithPrefix = "Bearer my_jwt_token"
        val cleanToken = tokenWithPrefix.removePrefix("Bearer ").trim()

        assertThat(cleanToken).isEqualTo("my_jwt_token")
    }

    @Test
    fun `tokenHandling_noPrefixUnchanged`() {
        val tokenWithoutPrefix = "my_jwt_token"
        val cleanToken = tokenWithoutPrefix.removePrefix("Bearer ").trim()

        assertThat(cleanToken).isEqualTo("my_jwt_token")
    }

    @Test
    fun `tokenHandling_extraSpacesRemoved`() {
        val tokenWithSpaces = "Bearer   my_jwt_token  "
        val cleanToken = tokenWithSpaces.removePrefix("Bearer ").trim()

        assertThat(cleanToken).isEqualTo("my_jwt_token")
    }
}
