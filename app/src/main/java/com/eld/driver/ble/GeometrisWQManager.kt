package com.eld.driver.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import com.eld.driver.ble.models.BleConnectionState
import com.eld.driver.ble.models.BleDataState
import com.eld.driver.ble.models.GeometrisEldData
import com.geometris.wqlib.WQScanner
import com.geometris.wqlib.Wqa
import com.geometris.wqlib.WherequbeService
import com.geometris.wqlib.AbstractWherequbeStateObserver
import com.geometris.wqlib.RequestHandler
import com.geometris.wqlib.GeoData
import com.geometris.wqlib.WQError
import com.geometris.wqlib.RequestType
import com.geometris.wqlib.Whereqube
import com.geometris.wqlib.UnidentifiedDriverMessageStartReq
import com.geometris.wqlib.UnidentifiedDriverMessageStopReq
import com.geometris.wqlib.UnidentifiedDriverMessagePurgeReq
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Vehicle motion state based on speed
 */
enum class VehicleMotionState {
    IN_MOTION,    // Speed > 5 mph
    STATIONARY    // Speed <= 5 mph
}

/**
 * Manager for Geometris WQ devices using official wqlib SDK v1.0.10
 * Singleton to maintain connection across all screens
 */
@SuppressLint("MissingPermission")
class GeometrisWQManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "GeometrisWQManager"
        private const val REQUEST_TIMEOUT = 5000

        @Volatile
        private var INSTANCE: GeometrisWQManager? = null

        fun getInstance(context: Context): GeometrisWQManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GeometrisWQManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _dataState = MutableStateFlow<BleDataState>(BleDataState.Idle)
    val dataState: StateFlow<BleDataState> = _dataState.asStateFlow()

    private val _eldData = MutableStateFlow<GeometrisEldData?>(null)
    val eldData: StateFlow<GeometrisEldData?> = _eldData.asStateFlow()

    private val _debugLogs = mutableListOf<String>()

    private var scanner: WQScanner? = null
    private var connectedDevice: BluetoothDevice? = null

    // Auto status detection state
    private var lastSpeed: Double = 0.0
    private var lastMovementTime: Long = System.currentTimeMillis()  // Initialize to now
    private var consecutiveIdleTime: Long = 0L
    private var isVehicleMoving: Boolean = false
    private var lastSuggestedStatus: String? = null  // Track last suggested status to avoid duplicates
    private var idleCheckStarted: Boolean = false  // Track if idle checking has started

    // Callbacks for auto status changes
    var onAutoStatusChangeNeeded: ((suggestedStatus: String, reason: String) -> Unit)? = null

    // Callback for connection lost while driving
    var onConnectionLostWhileDriving: (() -> Unit)? = null

    // Callback for showing 60-second delay dialog after 5 min stationary
    var onShowStationaryDelayDialog: (() -> Unit)? = null

    // Vehicle motion state
    private val _vehicleMotionState = MutableStateFlow<VehicleMotionState>(VehicleMotionState.STATIONARY)
    val vehicleMotionState: StateFlow<VehicleMotionState> = _vehicleMotionState.asStateFlow()

    // Track if vehicle was moving when connection was lost
    private var wasMovingBeforeDisconnect = false

    /**
     * Reset the dialog state when user makes a choice or starts driving again
     */
    fun resetDialogState() {
        lastSuggestedStatus = null
        idleCheckStarted = false
        addDebugLog("Dialog state reset")
    }

    private val wherequbeObserver = object : AbstractWherequbeStateObserver() {
        override fun onConnected() {
            addDebugLog("Device connected")
            connectedDevice?.let {
                _connectionState.value = BleConnectionState.Connected(it)
            }
        }

        override fun onSynced() {
            addDebugLog("Device synced and ready")
            connectedDevice?.let {
                _connectionState.value = BleConnectionState.Ready(it)
            }
        }

        override fun onDiscovered() {
            addDebugLog("Device discovered")
        }

        override fun onDisconnected() {
            addDebugLog("Device disconnected")

            // Check if vehicle was moving when disconnected
            if (isVehicleMoving || _vehicleMotionState.value == VehicleMotionState.IN_MOTION) {
                addDebugLog("⚠️ CONNECTION LOST WHILE DRIVING!")
                wasMovingBeforeDisconnect = true
                _vehicleMotionState.value = VehicleMotionState.STATIONARY
                onConnectionLostWhileDriving?.invoke()
            } else {
                wasMovingBeforeDisconnect = false
            }

            _connectionState.value = BleConnectionState.Disconnected
        }

        override fun onError(error: WQError?) {
            val errorMsg = error?.mCode?.toString() ?: "Unknown error"
            addDebugLog("Error: $errorMsg")
            _connectionState.value = BleConnectionState.Error(errorMsg)
        }
    }

    private val dataRequestHandler = RequestHandler { _, request ->
        val geoData = request?.`object` as? GeoData
        if (geoData != null) {
            addDebugLog("Received ELD data from SDK")

            // Convert GeoData to our GeometrisEldData model
            // Note: GeoData uses Joda-Time DateTime for timestamps
            val eldData = GeometrisEldData(
                vin = geoData.vin,
                serialNumber = null,  // Not available in GeoData
                rpm = geoData.engineRPM?.toDouble(),
                engineHours = geoData.engTotalHours,
                rpmTimestamp = geoData.engineRpmTimestamp?.toDate(),
                engineHoursTimestamp = geoData.engTotalHoursTimestamp?.toDate(),
                speed = geoData.vehicleSpeed,
                odometer = geoData.odometer,
                speedTimestamp = geoData.vehicleSpeedTimestamp?.toDate(),
                odometerTimestamp = geoData.odometerTimestamp?.toDate(),
                latitude = geoData.latitude,
                longitude = geoData.longitude,
                gpsTime = geoData.gpsTime?.toLong(),
                locationTimestamp = geoData.gpsTime?.let {
                    // gpsTime is age in minutes, calculate actual time
                    java.util.Date(System.currentTimeMillis() - (it * 60 * 1000))
                },
                protocolVersion = geoData.protocol ?: 1
            )

            _eldData.value = eldData
            _dataState.value = BleDataState.DataReceived(eldData)

            addDebugLog("Speed: ${eldData.speed} km/h")
            addDebugLog("RPM: ${eldData.rpm}")
            addDebugLog("Engine Hours: ${eldData.engineHours}")
            addDebugLog("Odometer: ${eldData.odometer} km")
            addDebugLog("Location: ${eldData.latitude}, ${eldData.longitude}")
            addDebugLog("Protocol: v${eldData.protocolVersion}")

            // Check for automatic status change
            addDebugLog("🔍 Checking automatic status change...")
            checkAutomaticStatusChange(eldData)
        }
        null
    }

    /**
     * Checks if automatic duty status change is needed based on vehicle telemetry
     * Rules:
     * - Speed > 5 mph (8 km/h) → Suggest DRIVING
     * - Speed < 5 mph for 5+ minutes → Suggest ON_DUTY
     */
    private fun checkAutomaticStatusChange(eldData: GeometrisEldData) {
        val currentSpeed = eldData.speed ?: 0.0
        val speedMph = currentSpeed * 0.621371 // Convert km/h to mph
        val currentTime = System.currentTimeMillis()

        addDebugLog("Speed: ${String.format("%.1f", speedMph)} mph, isVehicleMoving: $isVehicleMoving")

        // Rule 1: Speed > 5 mph → Vehicle is moving, should be DRIVING
        if (speedMph > 5.0) {
            // Update motion state
            _vehicleMotionState.value = VehicleMotionState.IN_MOTION

            if (!isVehicleMoving) {
                isVehicleMoving = true
                lastMovementTime = currentTime
                consecutiveIdleTime = 0

                // Only call callback if status is different from last suggested
                if (lastSuggestedStatus != "DRIVING") {
                    addDebugLog("🚗 Auto-Detection: Vehicle STARTED moving at ${String.format("%.1f", speedMph)} mph")
                    addDebugLog("🔔 Calling callback: DRIVING (was: $lastSuggestedStatus)")
                    Log.d(TAG, "════════════════════════════════════════")
                    Log.d(TAG, "🔔 INVOKING onAutoStatusChangeNeeded")
                    Log.d(TAG, "   Status: DRIVING")
                    Log.d(TAG, "   Callback registered: ${onAutoStatusChangeNeeded != null}")
                    Log.d(TAG, "════════════════════════════════════════")
                    onAutoStatusChangeNeeded?.invoke("DRIVING", "Vehicle moving at ${String.format("%.1f", speedMph)} mph")
                    lastSuggestedStatus = "DRIVING"
                    addDebugLog("✅ Callback invoked successfully")
                } else {
                    addDebugLog("⏭️ Already in DRIVING status, skipping duplicate API call")
                }
            } else {
                addDebugLog("⏩ Vehicle already moving, skipping callback")
            }
        }
        // Rule 2: Speed < 5 mph → Vehicle might be idle
        else {
            // Update motion state
            _vehicleMotionState.value = VehicleMotionState.STATIONARY

            if (isVehicleMoving) {
                // Vehicle just stopped
                isVehicleMoving = false
                lastMovementTime = currentTime
                idleCheckStarted = true
                consecutiveIdleTime = 0
                addDebugLog("Auto-Detection: Vehicle stopped, starting idle timer")
            } else {
                // Start idle check if not already started (for manual DRIVING status)
                if (!idleCheckStarted) {
                    idleCheckStarted = true
                    lastMovementTime = currentTime
                    addDebugLog("Auto-Detection: Starting idle timer (vehicle was already stationary)")
                }

                // Vehicle still idle - count time
                consecutiveIdleTime = currentTime - lastMovementTime
                val idleMinutes = consecutiveIdleTime / 60000
                val idleSeconds = (consecutiveIdleTime % 60000) / 1000

                // Log every 30 seconds
                if (consecutiveIdleTime % 30000 < 5000) {
                    addDebugLog("Idle time: ${idleMinutes}m ${idleSeconds}s / 5m required")
                }

                // After 5 minutes of idle (300,000 ms), show delay dialog
                if (consecutiveIdleTime >= 300_000) {
                    if (lastSuggestedStatus != "SHOWING_DIALOG") {
                        addDebugLog("⏰ Vehicle idle for 5+ minutes - showing delay dialog")
                        Log.d(TAG, "════════════════════════════════════════")
                        Log.d(TAG, "🔔 SHOWING STATIONARY DELAY DIALOG")
                        Log.d(TAG, "   Idle time: ${consecutiveIdleTime / 1000}s")
                        Log.d(TAG, "   Callback registered: ${onShowStationaryDelayDialog != null}")
                        Log.d(TAG, "════════════════════════════════════════")
                        onShowStationaryDelayDialog?.invoke()
                        lastSuggestedStatus = "SHOWING_DIALOG"
                        addDebugLog("✅ Dialog callback invoked")
                    }
                    // Reset timer - dialog will handle the rest
                    lastMovementTime = currentTime
                    idleCheckStarted = false
                }
            }
        }

        lastSpeed = currentSpeed
    }

    init {
        addDebugLog("Initializing Geometris WQ SDK v1.0.10...")

        // Initialize SDK
        Wqa.getInstance().initialize(context)
        WherequbeService.getInstance().initialize(context)

        // Register observer for connection events
        wherequbeObserver.register(context)

        // Set up data request handler
        WherequbeService.getInstance().setReqHandler(RequestType.OBD_MEASUREMENT, dataRequestHandler)

        addDebugLog("SDK initialized successfully")
    }

    fun startScan() {
        addDebugLog("Starting BLE scan...")
        _connectionState.value = BleConnectionState.Scanning

        val scanResultListener = WQScanner.ScanResultListener { devices ->
            addDebugLog("Scan completed - found ${devices.size} device(s)")
            for (wqube in devices) {
                val device = wqube.mDevice
                val rssi = wqube.mRssi
                addDebugLog("Found device: ${device.name ?: "Unknown"} (${device.address}) RSSI: $rssi")

                // Emit each found device
                _connectionState.value = BleConnectionState.DeviceFound(device, rssi)
            }
        }

        scanner = WQScanner(scanResultListener)
        scanner?.start(10000) // 10 second scan
    }

    fun stopScan() {
        addDebugLog("Stopping BLE scan...")
        scanner?.stop()
        scanner = null
        _connectionState.value = BleConnectionState.Disconnected
    }

    fun connect(device: BluetoothDevice) {
        addDebugLog("Connecting to device: ${device.name ?: "Unknown"} (${device.address})")
        connectedDevice = device
        _connectionState.value = BleConnectionState.Connecting(device)

        // Connect using device address
        WherequbeService.getInstance().connect(device.address)
    }

    fun disconnect() {
        addDebugLog("Disconnecting from device...")
        WherequbeService.getInstance().disconnect()
        connectedDevice = null
        _connectionState.value = BleConnectionState.Disconnected
    }

    fun requestEldData() {
        addDebugLog("Requesting ELD data...")
        _dataState.value = BleDataState.Receiving
        // Note: OBD data is automatically received once connected and synced
        // The dataRequestHandler will be called when data arrives
    }

    fun startUnidentifiedEvents() {
        addDebugLog("Starting unidentified events...")
        val request = UnidentifiedDriverMessageStartReq()
        WherequbeService.getInstance().sendRequest(request, null, REQUEST_TIMEOUT)
    }

    fun stopUnidentifiedEvents() {
        addDebugLog("Stopping unidentified events...")
        val request = UnidentifiedDriverMessageStopReq()
        WherequbeService.getInstance().sendRequest(request, null, REQUEST_TIMEOUT)
    }

    fun purgeUnidentifiedEvents() {
        addDebugLog("Purging unidentified events...")
        val request = UnidentifiedDriverMessagePurgeReq()
        WherequbeService.getInstance().sendRequest(request, null, REQUEST_TIMEOUT)
    }

    fun isBluetoothEnabled(): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bluetoothManager?.adapter?.isEnabled == true
    }

    fun hasRequiredPermissions(): Boolean {
        // Check if required BLE permissions are granted
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    fun getDebugLogs(): List<String> = _debugLogs.toList()

    private fun addDebugLog(message: String) {
        val timestamp = System.currentTimeMillis()
        val logMessage = "[$timestamp] $message"
        _debugLogs.add(logMessage)
        Log.d(TAG, message)

        // Keep only last 100 logs
        if (_debugLogs.size > 100) {
            _debugLogs.removeAt(0)
        }
    }

    fun destroy() {
        addDebugLog("Destroying GeometrisWQManager...")
        scanner?.stop()
        wherequbeObserver.unregister(context)
        disconnect()
    }
}
