package com.eld.driver.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.eld.driver.ble.models.BleConnectionState
import com.eld.driver.ble.models.BleDataState
import com.eld.driver.ble.models.GeometrisEldData
import java.util.*

/**
 * Geometris BLE Manager
 * Manages BLE connection and data reception from Geometris device
 */
@SuppressLint("MissingPermission")
class GeometrisBleManager(private val context: Context) {
    private val TAG = "GeometrisBleManager"

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private val dataParser = GeometrisDataParser()

    // State flows
    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _dataState = MutableStateFlow<BleDataState>(BleDataState.Idle)
    val dataState: StateFlow<BleDataState> = _dataState.asStateFlow()

    private val _eldData = MutableStateFlow<GeometrisEldData?>(null)
    val eldData: StateFlow<GeometrisEldData?> = _eldData.asStateFlow()

    // Expose parser debug logs
    fun getDebugLogs(): List<String> = dataParser.debugLogs.toList()

    /**
     * Check if BLE is supported and enabled
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Check if required permissions are granted
     */
    fun hasRequiredPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        }
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start scanning for Geometris devices
     */
    fun startScan() {
        if (!isBluetoothEnabled()) {
            _connectionState.value = BleConnectionState.Error("Bluetooth is not enabled")
            return
        }

        if (!hasRequiredPermissions()) {
            _connectionState.value = BleConnectionState.Error("Missing required permissions")
            return
        }

        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(GeometrisBleService.SERVICE_UUID))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bluetoothLeScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
            _connectionState.value = BleConnectionState.Scanning
            Log.d(TAG, "Started scanning for Geometris devices")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scan", e)
            _connectionState.value = BleConnectionState.Error("Failed to start scan: ${e.message}", e)
        }
    }

    /**
     * Stop scanning
     */
    fun stopScan() {
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
            Log.d(TAG, "Stopped scanning")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop scan", e)
        }
    }

    /**
     * Connect to a Geometris device
     */
    fun connect(device: BluetoothDevice) {
        stopScan()

        _connectionState.value = BleConnectionState.Connecting(device)

        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            Log.d(TAG, "Connecting to device: ${device.name} (${device.address})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect", e)
            _connectionState.value = BleConnectionState.Error("Failed to connect: ${e.message}", e)
        }
    }

    /**
     * Disconnect from device
     */
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        dataParser.reset()
        _connectionState.value = BleConnectionState.Disconnected
        _dataState.value = BleDataState.Idle
        Log.d(TAG, "Disconnected from device")
    }

    /**
     * Request latest ELD data from device
     */
    fun requestEldData() {
        val controlCharacteristic = bluetoothGatt?.getService(GeometrisBleService.SERVICE_UUID)
            ?.getCharacteristic(GeometrisBleService.OBD_CONTROL_UUID)

        if (controlCharacteristic != null) {
            controlCharacteristic.value = GeometrisBleService.CMD_GET_LATEST_ELD_DATA
            bluetoothGatt?.writeCharacteristic(controlCharacteristic)
            Log.d(TAG, "Requested latest ELD data")
        } else {
            Log.e(TAG, "OBD_CONTROL characteristic not found")
        }
    }

    /**
     * Start receiving unidentified driving events
     */
    fun startUnidentifiedEvents() {
        val controlCharacteristic = bluetoothGatt?.getService(GeometrisBleService.SERVICE_UUID)
            ?.getCharacteristic(GeometrisBleService.OBD_CONTROL_UUID)

        if (controlCharacteristic != null) {
            controlCharacteristic.value = GeometrisBleService.CMD_START_UNIDENTIFIED_EVENTS
            bluetoothGatt?.writeCharacteristic(controlCharacteristic)
            Log.d(TAG, "Started unidentified events")
        }
    }

    /**
     * Stop receiving unidentified driving events
     */
    fun stopUnidentifiedEvents() {
        val controlCharacteristic = bluetoothGatt?.getService(GeometrisBleService.SERVICE_UUID)
            ?.getCharacteristic(GeometrisBleService.OBD_CONTROL_UUID)

        if (controlCharacteristic != null) {
            controlCharacteristic.value = GeometrisBleService.CMD_STOP_UNIDENTIFIED_EVENTS
            bluetoothGatt?.writeCharacteristic(controlCharacteristic)
            Log.d(TAG, "Stopped unidentified events")
        }
    }

    /**
     * Purge all unidentified driving events from device
     */
    fun purgeUnidentifiedEvents() {
        val controlCharacteristic = bluetoothGatt?.getService(GeometrisBleService.SERVICE_UUID)
            ?.getCharacteristic(GeometrisBleService.OBD_CONTROL_UUID)

        if (controlCharacteristic != null) {
            controlCharacteristic.value = GeometrisBleService.CMD_PURGE_UNIDENTIFIED_EVENTS
            bluetoothGatt?.writeCharacteristic(controlCharacteristic)
            Log.d(TAG, "Purged unidentified events")
        }
    }

    // Scan callback
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: ""

            // Only report devices with WQ- prefix
            if (deviceName.startsWith(GeometrisBleService.DEVICE_NAME_PREFIX)) {
                _connectionState.value = BleConnectionState.DeviceFound(device, result.rssi)
                Log.d(TAG, "Found Geometris device: $deviceName (${device.address}), RSSI: ${result.rssi}")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = BleConnectionState.Error("Scan failed with error: $errorCode")
            Log.e(TAG, "Scan failed with error code: $errorCode")
        }
    }

    // GATT callback
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = BleConnectionState.Connected(gatt.device)
                    Log.d(TAG, "Connected to GATT server")
                    // Discover services
                    gatt.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = BleConnectionState.Disconnected
                    Log.d(TAG, "Disconnected from GATT server")
                    disconnect()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = BleConnectionState.ServicesDiscovered(gatt.device)
                Log.d(TAG, "Services discovered")

                // Enable notifications on OBD_DATA characteristic
                val dataCharacteristic = gatt.getService(GeometrisBleService.SERVICE_UUID)
                    ?.getCharacteristic(GeometrisBleService.OBD_DATA_UUID)

                if (dataCharacteristic != null) {
                    val enabled = gatt.setCharacteristicNotification(dataCharacteristic, true)

                    // Write to descriptor to enable notifications
                    val descriptor = dataCharacteristic.getDescriptor(
                        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")  // Client Characteristic Configuration
                    )

                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                        Log.d(TAG, "Enabled notifications on OBD_DATA")
                    }
                } else {
                    Log.e(TAG, "OBD_DATA characteristic not found")
                    _connectionState.value = BleConnectionState.Error("OBD_DATA characteristic not found")
                }
            } else {
                Log.e(TAG, "Service discovery failed with status: $status")
                _connectionState.value = BleConnectionState.Error("Service discovery failed")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = BleConnectionState.Ready(gatt.device)
                Log.d(TAG, "Device is ready to receive data")

                // Automatically request ELD data
                requestEldData()
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == GeometrisBleService.OBD_DATA_UUID) {
                val data = characteristic.value
                Log.d(TAG, "Received BLE packet: ${data?.size} bytes")

                if (data != null) {
                    _dataState.value = BleDataState.Receiving

                    // Add packet to parser
                    val allPacketsReceived = dataParser.addPacket(data)

                    if (allPacketsReceived) {
                        // Parse complete ELD data
                        val eldData = dataParser.parseEldData()
                        if (eldData != null) {
                            _eldData.value = eldData
                            _dataState.value = BleDataState.DataReceived(eldData)
                            Log.d(TAG, "ELD data parsed successfully: $eldData")
                        } else {
                            _dataState.value = BleDataState.Error("Failed to parse ELD data")
                            Log.e(TAG, "Failed to parse ELD data")
                        }
                    }
                }
            }
        }
    }
}
