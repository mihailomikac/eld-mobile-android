package com.eld.driver.ble.models

import android.bluetooth.BluetoothDevice

/**
 * BLE Connection State
 * Represents the current state of BLE connection
 */
sealed class BleConnectionState {
    object Disconnected : BleConnectionState()
    object Scanning : BleConnectionState()
    data class DeviceFound(val device: BluetoothDevice, val rssi: Int) : BleConnectionState()
    data class Connecting(val device: BluetoothDevice) : BleConnectionState()
    data class Connected(val device: BluetoothDevice) : BleConnectionState()
    data class ServicesDiscovered(val device: BluetoothDevice) : BleConnectionState()
    data class Ready(val device: BluetoothDevice) : BleConnectionState()  // Notifications enabled, ready to receive data
    data class Error(val message: String, val throwable: Throwable? = null) : BleConnectionState()
}

/**
 * BLE Data State
 * Represents the state of data reception from BLE device
 */
sealed class BleDataState {
    object Idle : BleDataState()
    object Receiving : BleDataState()
    data class DataReceived(val eldData: GeometrisEldData) : BleDataState()
    data class Error(val message: String) : BleDataState()
}
