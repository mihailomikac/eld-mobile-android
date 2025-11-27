package com.eld.driver.ui.screens.bletest

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eld.driver.ble.GeometrisWQManager
import com.eld.driver.ble.models.BleConnectionState
import com.eld.driver.ble.models.BleDataState
import com.eld.driver.ble.models.GeometrisEldData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for BLE Test Screen
 * Now using official Geometris wqlib SDK
 * Uses singleton BLE manager to share connection across screens
 */
class BleTestViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = GeometrisWQManager.getInstance(application)

    // Expose BLE manager states
    val connectionState: StateFlow<BleConnectionState> = bleManager.connectionState
    val dataState: StateFlow<BleDataState> = bleManager.dataState
    val eldData: StateFlow<GeometrisEldData?> = bleManager.eldData

    // Track discovered devices
    private val _discoveredDevices = MutableStateFlow<List<Pair<BluetoothDevice, Int>>>(emptyList())
    val discoveredDevices: StateFlow<List<Pair<BluetoothDevice, Int>>> = _discoveredDevices.asStateFlow()

    init {
        // Collect connection state and track found devices
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state is BleConnectionState.DeviceFound) {
                    val currentDevices = _discoveredDevices.value.toMutableList()

                    // Update or add device
                    val existingIndex = currentDevices.indexOfFirst {
                        it.first.address == state.device.address
                    }

                    if (existingIndex >= 0) {
                        currentDevices[existingIndex] = Pair(state.device, state.rssi)
                    } else {
                        currentDevices.add(Pair(state.device, state.rssi))
                    }

                    _discoveredDevices.value = currentDevices
                } else if (state is BleConnectionState.Scanning) {
                    // Clear devices when starting new scan
                    _discoveredDevices.value = emptyList()
                }
            }
        }

        // Note: Automatic status change callback is handled by DashboardViewModel
        // since the BLE manager is now a singleton. Only one callback can be registered.
    }

    fun startScan() {
        bleManager.startScan()
    }

    fun stopScan() {
        bleManager.stopScan()
    }

    fun connect(device: BluetoothDevice) {
        bleManager.connect(device)
    }

    fun disconnect() {
        bleManager.disconnect()
    }

    fun requestEldData() {
        bleManager.requestEldData()
    }

    fun startUnidentifiedEvents() {
        bleManager.startUnidentifiedEvents()
    }

    fun stopUnidentifiedEvents() {
        bleManager.stopUnidentifiedEvents()
    }

    fun purgeUnidentifiedEvents() {
        bleManager.purgeUnidentifiedEvents()
    }

    fun isBluetoothEnabled(): Boolean {
        return bleManager.isBluetoothEnabled()
    }

    fun hasRequiredPermissions(): Boolean {
        return bleManager.hasRequiredPermissions()
    }

    fun getDebugLogs(): List<String> {
        return bleManager.getDebugLogs()
    }

    // Note: Don't call bleManager.destroy() here since it's a singleton
    // shared across all screens. Connection should persist.
}
