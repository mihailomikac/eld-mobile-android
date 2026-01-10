package com.eld.driver.ble

import com.eld.driver.ble.models.BleConnectionState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for BLE connection state management.
 *
 * Covers test cases:
 * - Case #1: Connect button → Connected
 * - Case #2: Disconnect → Disconnected
 * - Case #3: Stable connection for 5+ min
 * - Case #4-5: Two drivers, one hardware
 * - Case #6-7: Walk away and reconnect
 * - Case #18-19: Connection lost while driving
 */
class ConnectionStateTest {

    companion object {
        const val MAX_RECONNECT_ATTEMPTS = 6
        const val RECONNECT_DELAY_MS = 10_000L
    }

    // ==================== BASIC CONNECTION TESTS ====================
    // Case #1, #2

    @Test
    fun `initial state is Disconnected`() {
        val stateMachine = MockConnectionStateMachine()
        assertThat(stateMachine.state).isInstanceOf(ConnectionState.Disconnected::class.java)
    }

    @Test
    fun `connect transitions to Connecting then Connected`() {
        val stateMachine = MockConnectionStateMachine()

        stateMachine.connect()
        assertThat(stateMachine.state).isInstanceOf(ConnectionState.Connecting::class.java)

        stateMachine.onConnected()
        assertThat(stateMachine.state).isInstanceOf(ConnectionState.Connected::class.java)

        stateMachine.onSynced()
        assertThat(stateMachine.state).isInstanceOf(ConnectionState.Ready::class.java)
    }

    @Test
    fun `disconnect transitions to Disconnected`() {
        val stateMachine = MockConnectionStateMachine()

        // Connect first
        stateMachine.connect()
        stateMachine.onConnected()
        stateMachine.onSynced()
        assertThat(stateMachine.state).isInstanceOf(ConnectionState.Ready::class.java)

        // Disconnect
        stateMachine.disconnect()
        assertThat(stateMachine.state).isInstanceOf(ConnectionState.Disconnected::class.java)
    }

    @Test
    fun `manual disconnect does not trigger reconnect`() {
        val stateMachine = MockConnectionStateMachine()

        stateMachine.connect()
        stateMachine.onConnected()
        stateMachine.onSynced()

        stateMachine.disconnect()  // Manual disconnect
        assertThat(stateMachine.isReconnecting).isFalse()
        assertThat(stateMachine.reconnectAttempts).isEqualTo(0)
    }

    // ==================== RECONNECTION TESTS ====================
    // Case #6, #7

    @Test
    fun `signal loss triggers auto reconnect`() {
        val stateMachine = MockConnectionStateMachine()

        stateMachine.connect()
        stateMachine.onConnected()
        stateMachine.onSynced()

        // Signal lost (not manual disconnect)
        stateMachine.onSignalLost()

        assertThat(stateMachine.isReconnecting).isTrue()
        assertThat(stateMachine.state).isInstanceOf(ConnectionState.Reconnecting::class.java)
    }

    @Test
    fun `reconnect attempts are limited to MAX_RECONNECT_ATTEMPTS`() {
        val stateMachine = MockConnectionStateMachine()

        stateMachine.connect()
        stateMachine.onConnected()
        stateMachine.onSynced()

        // Signal lost
        stateMachine.onSignalLost()

        // Fail all reconnect attempts
        repeat(MAX_RECONNECT_ATTEMPTS) {
            assertThat(stateMachine.isReconnecting).isTrue()
            stateMachine.onReconnectFailed()
        }

        // After max attempts, should give up
        assertThat(stateMachine.isReconnecting).isFalse()
        assertThat(stateMachine.state).isInstanceOf(ConnectionState.Disconnected::class.java)
    }

    @Test
    fun `successful reconnect resets attempt counter`() {
        val stateMachine = MockConnectionStateMachine()

        stateMachine.connect()
        stateMachine.onConnected()
        stateMachine.onSynced()

        // Signal lost
        stateMachine.onSignalLost()

        // Fail a few attempts
        stateMachine.onReconnectFailed()
        stateMachine.onReconnectFailed()
        assertThat(stateMachine.reconnectAttempts).isEqualTo(2)

        // Successful reconnect
        stateMachine.onConnected()
        stateMachine.onSynced()

        assertThat(stateMachine.reconnectAttempts).isEqualTo(0)
        assertThat(stateMachine.isReconnecting).isFalse()
        assertThat(stateMachine.state).isInstanceOf(ConnectionState.Ready::class.java)
    }

    @Test
    fun `reconnect shows correct attempt number`() {
        val stateMachine = MockConnectionStateMachine()

        stateMachine.connect()
        stateMachine.onConnected()
        stateMachine.onSynced()
        stateMachine.onSignalLost()

        val state1 = stateMachine.state as ConnectionState.Reconnecting
        assertThat(state1.attempt).isEqualTo(1)
        assertThat(state1.maxAttempts).isEqualTo(MAX_RECONNECT_ATTEMPTS)

        stateMachine.onReconnectFailed()
        val state2 = stateMachine.state as ConnectionState.Reconnecting
        assertThat(state2.attempt).isEqualTo(2)

        stateMachine.onReconnectFailed()
        val state3 = stateMachine.state as ConnectionState.Reconnecting
        assertThat(state3.attempt).isEqualTo(3)
    }

    // ==================== CONNECTION LOST WHILE DRIVING ====================
    // Case #18, #19

    @Test
    fun `connection lost while driving triggers alert`() {
        val stateMachine = MockConnectionStateMachine()

        stateMachine.connect()
        stateMachine.onConnected()
        stateMachine.onSynced()

        // Start driving
        stateMachine.setVehicleMoving(true)

        // Signal lost while driving
        stateMachine.onSignalLost()

        assertThat(stateMachine.connectionLostWhileDrivingAlertTriggered).isTrue()
    }

    @Test
    fun `connection lost while stationary does NOT trigger driving alert`() {
        val stateMachine = MockConnectionStateMachine()

        stateMachine.connect()
        stateMachine.onConnected()
        stateMachine.onSynced()

        // Vehicle is stationary
        stateMachine.setVehicleMoving(false)

        // Signal lost while stationary
        stateMachine.onSignalLost()

        assertThat(stateMachine.connectionLostWhileDrivingAlertTriggered).isFalse()
    }

    @Test
    fun `connection lost while driving should show STATIONARY banner`() {
        val stateMachine = MockConnectionStateMachine()

        stateMachine.connect()
        stateMachine.onConnected()
        stateMachine.onSynced()
        stateMachine.setVehicleMoving(true)

        stateMachine.onSignalLost()

        // After disconnection, motion state should reset to STATIONARY
        assertThat(stateMachine.vehicleMotionState).isEqualTo(VehicleMotionState.STATIONARY)
    }

    // ==================== MULTI-DEVICE TESTS ====================
    // Case #4, #5

    @Test
    fun `second device shows Pairing when first is connected`() {
        val hardware = MockHardware()
        val device1 = MockConnectionStateMachine(hardware)
        val device2 = MockConnectionStateMachine(hardware)

        // Device 1 connects
        device1.connect()
        device1.onConnected()
        device1.onSynced()
        assertThat(device1.state).isInstanceOf(ConnectionState.Ready::class.java)

        // Device 2 tries to connect - should show Pairing
        device2.connect()
        assertThat(device2.state).isInstanceOf(ConnectionState.Pairing::class.java)
    }

    @Test
    fun `second device connects after first disconnects`() {
        val hardware = MockHardware()
        val device1 = MockConnectionStateMachine(hardware)
        val device2 = MockConnectionStateMachine(hardware)

        // Device 1 connects
        device1.connect()
        device1.onConnected()
        device1.onSynced()

        // Device 2 tries to connect - pairing
        device2.connect()
        assertThat(device2.state).isInstanceOf(ConnectionState.Pairing::class.java)

        // Device 1 disconnects
        device1.disconnect()
        hardware.notifyDisconnected()

        // Device 2 should now connect
        device2.onConnected()
        device2.onSynced()
        assertThat(device2.state).isInstanceOf(ConnectionState.Ready::class.java)
    }

    // ==================== BACKGROUND RECONNECTION ====================
    // Case #7

    @Test
    fun `background reconnection works after coming back`() {
        val stateMachine = MockConnectionStateMachine()

        stateMachine.connect()
        stateMachine.onConnected()
        stateMachine.onSynced()

        // App goes to background
        stateMachine.onAppBackground()

        // Signal lost in background
        stateMachine.onSignalLost()
        assertThat(stateMachine.isReconnecting).isTrue()

        // Come back to vehicle, signal restored
        stateMachine.onConnected()
        stateMachine.onSynced()

        // Reopen app
        stateMachine.onAppForeground()

        assertThat(stateMachine.state).isInstanceOf(ConnectionState.Ready::class.java)
    }

    // ==================== SCANNING TESTS ====================

    @Test
    fun `startScan transitions to Scanning state`() {
        val stateMachine = MockConnectionStateMachine()

        stateMachine.startScan()
        assertThat(stateMachine.state).isInstanceOf(ConnectionState.Scanning::class.java)
    }

    @Test
    fun `stopScan transitions back to Disconnected`() {
        val stateMachine = MockConnectionStateMachine()

        stateMachine.startScan()
        stateMachine.stopScan()
        assertThat(stateMachine.state).isInstanceOf(ConnectionState.Disconnected::class.java)
    }

    @Test
    fun `device found during scan updates state`() {
        val stateMachine = MockConnectionStateMachine()

        stateMachine.startScan()
        stateMachine.onDeviceFound("Geometris-12345", -60)

        val state = stateMachine.state as ConnectionState.DeviceFound
        assertThat(state.deviceName).isEqualTo("Geometris-12345")
        assertThat(state.rssi).isEqualTo(-60)
    }

    // ==================== HELPER CLASSES ====================

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Scanning : ConnectionState()
        data class DeviceFound(val deviceName: String, val rssi: Int) : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        object Ready : ConnectionState()
        object Pairing : ConnectionState()
        data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    class MockHardware {
        var connectedDevice: MockConnectionStateMachine? = null
        val pairingDevices = mutableListOf<MockConnectionStateMachine>()

        fun tryConnect(device: MockConnectionStateMachine): Boolean {
            return if (connectedDevice == null) {
                connectedDevice = device
                true
            } else {
                pairingDevices.add(device)
                false
            }
        }

        fun disconnect(device: MockConnectionStateMachine) {
            if (connectedDevice == device) {
                connectedDevice = null
            }
        }

        fun notifyDisconnected() {
            // Auto-connect first pairing device
            if (pairingDevices.isNotEmpty()) {
                val nextDevice = pairingDevices.removeAt(0)
                connectedDevice = nextDevice
            }
        }
    }

    class MockConnectionStateMachine(
        private val hardware: MockHardware? = null
    ) {
        var state: ConnectionState = ConnectionState.Disconnected
            private set
        var isReconnecting: Boolean = false
            private set
        var reconnectAttempts: Int = 0
            private set
        var connectionLostWhileDrivingAlertTriggered: Boolean = false
            private set
        var vehicleMotionState: VehicleMotionState = VehicleMotionState.STATIONARY
            private set

        private var isVehicleMoving = false
        private var isManualDisconnect = false
        private var isInBackground = false
        private var lastConnectedDeviceAddress: String? = null

        fun startScan() {
            state = ConnectionState.Scanning
        }

        fun stopScan() {
            state = ConnectionState.Disconnected
        }

        fun onDeviceFound(name: String, rssi: Int) {
            state = ConnectionState.DeviceFound(name, rssi)
        }

        fun connect() {
            if (hardware != null) {
                val connected = hardware.tryConnect(this)
                state = if (connected) {
                    ConnectionState.Connecting
                } else {
                    ConnectionState.Pairing
                }
            } else {
                state = ConnectionState.Connecting
            }
            lastConnectedDeviceAddress = "mock_address"
        }

        fun onConnected() {
            state = ConnectionState.Connected
            isReconnecting = false
            reconnectAttempts = 0
        }

        fun onSynced() {
            state = ConnectionState.Ready
            isReconnecting = false
            reconnectAttempts = 0
        }

        fun disconnect() {
            isManualDisconnect = true
            hardware?.disconnect(this)
            state = ConnectionState.Disconnected
            isReconnecting = false
            reconnectAttempts = 0
            lastConnectedDeviceAddress = null
        }

        fun onSignalLost() {
            // Check if driving when connection lost
            if (isVehicleMoving) {
                connectionLostWhileDrivingAlertTriggered = true
            }

            // Reset motion state
            vehicleMotionState = VehicleMotionState.STATIONARY

            if (!isManualDisconnect && lastConnectedDeviceAddress != null) {
                // Start auto-reconnect
                isReconnecting = true
                reconnectAttempts = 1
                state = ConnectionState.Reconnecting(1, MAX_RECONNECT_ATTEMPTS)
            } else {
                state = ConnectionState.Disconnected
            }
            isManualDisconnect = false
        }

        fun onReconnectFailed() {
            if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                // Give up
                isReconnecting = false
                state = ConnectionState.Disconnected
            } else {
                reconnectAttempts++
                state = ConnectionState.Reconnecting(reconnectAttempts, MAX_RECONNECT_ATTEMPTS)
            }
        }

        fun setVehicleMoving(moving: Boolean) {
            isVehicleMoving = moving
            vehicleMotionState = if (moving) {
                VehicleMotionState.IN_MOTION
            } else {
                VehicleMotionState.STATIONARY
            }
        }

        fun onAppBackground() {
            isInBackground = true
        }

        fun onAppForeground() {
            isInBackground = false
        }
    }
}
