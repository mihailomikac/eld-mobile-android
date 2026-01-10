package com.eld.driver.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import com.eld.driver.ble.models.BleConnectionState
import com.eld.driver.ble.models.BleDataState
import com.eld.driver.ble.models.GeometrisEldData
import com.eld.driver.ble.models.UnidentifiedEvent
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
import com.geometris.wqlib.ResponseHandler
import com.geometris.wqlib.BaseResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
        private const val UDRV_EVENT_TIMEOUT = 30000  // 30 seconds for UD events

        // Reconnect settings
        private const val MAX_RECONNECT_ATTEMPTS = 6  // Try 6 times
        private const val RECONNECT_DELAY_MS = 10_000L  // 10 seconds between attempts (total ~60 sec)

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

    private val _unidentifiedEvents = MutableStateFlow<List<UnidentifiedEvent>>(emptyList())
    val unidentifiedEvents: StateFlow<List<UnidentifiedEvent>> = _unidentifiedEvents.asStateFlow()

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
    private var hasDriverEverMoved: Boolean = false  // Track if driver has EVER moved (speed > 5mph) in this session

    // Callbacks for auto status changes
    var onAutoStatusChangeNeeded: ((suggestedStatus: String, reason: String) -> Unit)? = null

    // Callback for connection lost while driving
    var onConnectionLostWhileDriving: (() -> Unit)? = null

    // Callbacks for ELD connection state tick events
    var onEldConnected: (() -> Unit)? = null
    /** Called when ELD disconnects. Parameter: true = manual disconnect, false = signal lost */
    var onEldDisconnected: ((isManual: Boolean) -> Unit)? = null

    // Callback for showing 60-second delay dialog after 5 min stationary
    var onShowStationaryDelayDialog: (() -> Unit)? = null

    // Callback for when unidentified driving events are received
    var onUnidentifiedEventsReceived: ((List<UnidentifiedEvent>) -> Unit)? = null

    // Vehicle motion state
    private val _vehicleMotionState = MutableStateFlow<VehicleMotionState>(VehicleMotionState.STATIONARY)
    val vehicleMotionState: StateFlow<VehicleMotionState> = _vehicleMotionState.asStateFlow()

    // Track if vehicle was moving when connection was lost
    private var wasMovingBeforeDisconnect = false

    // Reconnect logic
    private val reconnectScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var reconnectJob: Job? = null
    private var isManualDisconnect = false  // True when user clicks disconnect, false when signal lost
    private var reconnectAttempt = 0
    private var lastConnectedDevice: BluetoothDevice? = null  // Remember device for reconnect

    /**
     * Reset the dialog state when user makes a choice or starts driving again
     */
    fun resetDialogState() {
        lastSuggestedStatus = null
        idleCheckStarted = false
        hasDriverEverMoved = false
        consecutiveIdleTime = 0
        addDebugLog("Dialog state reset - idle timer cleared")
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

            // Cancel any ongoing reconnect attempts - we're connected!
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectAttempt = 0

            connectedDevice?.let {
                _connectionState.value = BleConnectionState.Ready(it)
            }

            // Start receiving unidentified driving events using sendRequest with ResponseHandler
            addDebugLog("📋 ═══════════════════════════════════════")
            addDebugLog("📋 STARTING UNIDENTIFIED DRIVING EVENTS...")
            addDebugLog("📋 ═══════════════════════════════════════")
            try {
                val wqService = WherequbeService.getInstance()
                addDebugLog("📋 Sending UnidentifiedDriverMessageStartReq...")

                // Response handler for UD events
                val udrvEventResponseHandler = object : ResponseHandler {
                    override fun onRecv(ctx: Context, response: BaseResponse) {
                        addDebugLog("📋 ═══ UD RESPONSE HANDLER CALLED! ═══")
                        addDebugLog("📋 Response: $response")
                        addDebugLog("📋 Response class: ${response.javaClass.simpleName}")
                        addDebugLog("📋 Response object: ${response.`object`}")

                        val responseObj = response.`object`
                        if (responseObj is GeoData) {
                            addDebugLog("📋 GeoData received in UD handler!")
                            addDebugLog("📋 Protocol: ${responseObj.protocol}")
                            addDebugLog("📋 TotalUdrvEvents: ${responseObj.totalUdrvEvents}")
                            addDebugLog("📋 UnidentifiedEventArrayList size: ${responseObj.unidentifiedEventArrayList?.size ?: "NULL"}")

                            // Parse UD events
                            val udEvents = responseObj.unidentifiedEventArrayList?.map { sdkEvent: com.geometris.wqlib.UnidentifiedEvent ->
                                UnidentifiedEvent(
                                    reason = sdkEvent.reason,
                                    timestamp = sdkEvent.timestamp,
                                    engineHours = sdkEvent.engTotalHours,
                                    speed = sdkEvent.vehicleSpeed,
                                    odometer = sdkEvent.odometer,
                                    latitude = sdkEvent.latitude,
                                    longitude = sdkEvent.longitude,
                                    gpsTimestamp = sdkEvent.getGPSTimestamp()
                                )
                            } ?: emptyList()

                            if (udEvents.isNotEmpty()) {
                                addDebugLog("📋 ═══ FOUND ${udEvents.size} UD EVENTS! ═══")
                                udEvents.forEachIndexed { index: Int, event: UnidentifiedEvent ->
                                    addDebugLog("📋 Event #$index: ${event.getReasonString()} at ${event.timestamp}")
                                }
                                _unidentifiedEvents.value = udEvents
                                onUnidentifiedEventsReceived?.invoke(udEvents)
                            } else {
                                addDebugLog("📋 No UD events in response")
                            }
                        } else {
                            addDebugLog("📋 Response object is not GeoData: ${responseObj?.javaClass?.name}")
                        }
                    }

                    override fun onError(ctx: Context) {
                        addDebugLog("❌ UD request error!")
                    }
                }

                wqService.sendRequest(UnidentifiedDriverMessageStartReq(), udrvEventResponseHandler, UDRV_EVENT_TIMEOUT)
                addDebugLog("📋 UD request sent, waiting for response...")
            } catch (e: Exception) {
                addDebugLog("❌ UD transmission start FAILED: ${e.message}")
                addDebugLog("❌ Stack trace: ${e.stackTraceToString().take(500)}")
            }

            // Send CONNECTED tick event
            addDebugLog("🔌 ELD CONNECTED - triggering tick event callback")
            Log.d(TAG, "════════════════════════════════════════")
            Log.d(TAG, "🔔 INVOKING onEldConnected callback")
            Log.d(TAG, "   Callback registered: ${onEldConnected != null}")
            Log.d(TAG, "════════════════════════════════════════")
            onEldConnected?.invoke()
        }

        override fun onDiscovered() {
            addDebugLog("Device discovered")
        }

        override fun onDisconnected() {
            addDebugLog("Device disconnected (manual: $isManualDisconnect)")
            Log.d(TAG, "════════════════════════════════════════")
            Log.d(TAG, "🔌 DEVICE DISCONNECTED")
            Log.d(TAG, "   Manual disconnect: $isManualDisconnect")
            Log.d(TAG, "   Last connected device: ${lastConnectedDevice?.address}")
            Log.d(TAG, "════════════════════════════════════════")

            // Check if vehicle was moving when disconnected
            if (isVehicleMoving || _vehicleMotionState.value == VehicleMotionState.IN_MOTION) {
                addDebugLog("⚠️ CONNECTION LOST WHILE DRIVING!")
                wasMovingBeforeDisconnect = true
                onConnectionLostWhileDriving?.invoke()
            } else {
                wasMovingBeforeDisconnect = false
            }

            // Reset motion state so auto-detection works on reconnect
            isVehicleMoving = false
            _vehicleMotionState.value = VehicleMotionState.STATIONARY
            idleCheckStarted = false
            consecutiveIdleTime = 0

            if (isManualDisconnect) {
                // User clicked disconnect - go directly to Disconnected
                addDebugLog("Manual disconnect - going to Disconnected state")

                // Send DISCONNECTED tick event for manual disconnect
                Log.d(TAG, "🔔 INVOKING onEldDisconnected callback (manual=true)")
                onEldDisconnected?.invoke(true)  // true = manual disconnect

                _connectionState.value = BleConnectionState.Disconnected
                connectedDevice = null
                lastConnectedDevice = null
                isManualDisconnect = false  // Reset flag
            } else {
                // Signal lost - try to reconnect
                addDebugLog("Signal lost - starting auto-reconnect")
                startAutoReconnect()
            }
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
            addDebugLog("📋 GeoData.protocol: ${geoData.protocol}")
            addDebugLog("📋 GeoData.totalUdrvEvents: ${geoData.totalUdrvEvents}")
            addDebugLog("📋 GeoData.unidentifiedEventArrayList: ${geoData.unidentifiedEventArrayList?.size ?: "NULL"}")

            // Parse unidentified driving events from GeoData
            val udEvents = mutableListOf<UnidentifiedEvent>()
            geoData.unidentifiedEventArrayList?.forEach { sdkEvent ->
                udEvents.add(UnidentifiedEvent(
                    reason = sdkEvent.reason,
                    timestamp = sdkEvent.timestamp,
                    engineHours = sdkEvent.engTotalHours,
                    speed = sdkEvent.vehicleSpeed,
                    odometer = sdkEvent.odometer,
                    latitude = sdkEvent.latitude,
                    longitude = sdkEvent.longitude,
                    gpsTimestamp = sdkEvent.getGPSTimestamp()  // Direct method call
                ))
            }
            val totalUdEvents = geoData.totalUdrvEvents ?: 0

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
                unidentifiedEvents = udEvents,
                totalUnidentifiedEvents = totalUdEvents,
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

            // Log and update StateFlow for unidentified driving events
            if (udEvents.isNotEmpty() || totalUdEvents > 0) {
                addDebugLog("📋 Unidentified Driving Events: ${udEvents.size} (total on device: $totalUdEvents)")
                udEvents.forEach { event ->
                    addDebugLog("  └─ ${event.getReasonString()} at ${event.timestamp}")
                }
                // Update StateFlow
                _unidentifiedEvents.value = udEvents
                // Notify callback about UD events
                onUnidentifiedEventsReceived?.invoke(udEvents)
            }

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

            // Mark that driver has moved at least once
            hasDriverEverMoved = true

            // Reset idle timer when driver starts moving again
            if (idleCheckStarted) {
                idleCheckStarted = false
                consecutiveIdleTime = 0
                addDebugLog("🔄 Driver started moving again - idle timer reset")
            }

            if (!isVehicleMoving) {
                isVehicleMoving = true
                lastMovementTime = currentTime
                consecutiveIdleTime = 0
                lastSuggestedStatus = null  // Reset so dialog can show again after next stop

                // Only call callback if status is different from last suggested
                addDebugLog("🚗 Auto-Detection: Vehicle STARTED moving at ${String.format("%.1f", speedMph)} mph")
                addDebugLog("🔔 Calling callback: DRIVING")
                Log.d(TAG, "════════════════════════════════════════")
                Log.d(TAG, "🔔 INVOKING onAutoStatusChangeNeeded")
                Log.d(TAG, "   Status: DRIVING")
                Log.d(TAG, "   Callback registered: ${onAutoStatusChangeNeeded != null}")
                Log.d(TAG, "════════════════════════════════════════")
                onAutoStatusChangeNeeded?.invoke("DRIVING", "Vehicle moving at ${String.format("%.1f", speedMph)} mph")
                addDebugLog("✅ Callback invoked successfully")
            } else {
                addDebugLog("⏩ Vehicle already moving, skipping callback")
            }
        }
        // Rule 2: Speed < 5 mph → Vehicle might be idle
        else {
            // Update motion state
            _vehicleMotionState.value = VehicleMotionState.STATIONARY

            if (isVehicleMoving) {
                // Vehicle just stopped AFTER driving
                isVehicleMoving = false
                lastMovementTime = currentTime
                idleCheckStarted = true
                consecutiveIdleTime = 0
                addDebugLog("Auto-Detection: Vehicle stopped after driving, starting idle timer")
            } else if (hasDriverEverMoved && idleCheckStarted) {
                // Only count idle time if driver has driven before AND stopped
                consecutiveIdleTime = currentTime - lastMovementTime
                val idleMinutes = consecutiveIdleTime / 60000
                val idleSeconds = (consecutiveIdleTime % 60000) / 1000

                // Log every 30 seconds
                if (consecutiveIdleTime % 30000 < 5000) {
                    addDebugLog("Idle time: ${idleMinutes}m ${idleSeconds}s / 5m required (driver stopped after driving)")
                }

                // After 5 minutes of idle (300,000 ms), show delay dialog
                if (consecutiveIdleTime >= 300_000) {
                    if (lastSuggestedStatus != "SHOWING_DIALOG") {
                        addDebugLog("⏰ Vehicle idle for 5+ minutes after driving - showing delay dialog")
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
            } else {
                // Driver hasn't moved yet or idle check not started - don't start timer
                addDebugLog("⏸️ Vehicle stationary but driver hasn't driven yet - no idle timer")
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

        // Set up data request handler for OBD data (includes UD events in GeoData)
        WherequbeService.getInstance().setReqHandler(RequestType.OBD_MEASUREMENT, dataRequestHandler)

        // Set up handler for UD events request type
        WherequbeService.getInstance().setReqHandler(RequestType.REQUEST_START_UDEVENTS, RequestHandler { _, request ->
            addDebugLog("📋 UD EVENTS REQUEST HANDLER CALLED!")
            addDebugLog("📋 Request object: ${request?.`object`}")
            addDebugLog("📋 Request class: ${request?.`object`?.javaClass?.simpleName}")

            // Try to parse UD events from this request
            val obj = request?.`object`
            if (obj != null) {
                addDebugLog("📋 UD Request fields: ${obj.javaClass.declaredFields.map { it.name }}")
            }
            null
        })

        // Set up handler for UD events stop
        WherequbeService.getInstance().setReqHandler(RequestType.REQUEST_STOP_UDEVENTS, RequestHandler { _, request ->
            addDebugLog("📋 UD STOP EVENTS HANDLER CALLED!")
            addDebugLog("📋 Request: $request")
            null
        })

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
        lastConnectedDevice = device  // Remember for reconnect
        reconnectAttempt = 0  // Reset reconnect attempts
        _connectionState.value = BleConnectionState.Connecting(device)

        // Connect using device address
        WherequbeService.getInstance().connect(device.address)
    }

    /**
     * Connect directly to an ELD device using its MAC address.
     * Used when the vehicle has a pre-configured eldMacAddress.
     * No scanning required - connects directly to the specified address.
     */
    @SuppressLint("MissingPermission")
    fun connectToMacAddress(macAddress: String) {
        addDebugLog("Connecting directly to MAC: $macAddress")

        // Get BluetoothDevice from MAC address
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter

        if (adapter == null) {
            addDebugLog("❌ Bluetooth adapter not available")
            _connectionState.value = BleConnectionState.Error("Bluetooth not available")
            return
        }

        try {
            val device = adapter.getRemoteDevice(macAddress)
            connectedDevice = device
            lastConnectedDevice = device
            reconnectAttempt = 0
            _connectionState.value = BleConnectionState.Connecting(device)

            // Connect using MAC address
            WherequbeService.getInstance().connect(macAddress)
            addDebugLog("✅ Connection initiated to $macAddress")
        } catch (e: IllegalArgumentException) {
            addDebugLog("❌ Invalid MAC address format: $macAddress")
            _connectionState.value = BleConnectionState.Error("Invalid MAC address: $macAddress")
        } catch (e: Exception) {
            addDebugLog("❌ Failed to connect: ${e.message}")
            _connectionState.value = BleConnectionState.Error("Connection failed: ${e.message}")
        }
    }

    fun disconnect() {
        addDebugLog("Disconnecting from device... (manual)")
        isManualDisconnect = true  // Mark as manual disconnect
        reconnectJob?.cancel()  // Cancel any ongoing reconnect attempts
        reconnectJob = null
        reconnectAttempt = 0

        // Stop scanning if in progress (for canceling PAIRING state)
        stopScan()

        WherequbeService.getInstance().disconnect()

        // Set state to Disconnected immediately if we were scanning/pairing/connecting
        val currentState = _connectionState.value
        if (currentState is BleConnectionState.Scanning ||
            currentState is BleConnectionState.Connecting ||
            currentState is BleConnectionState.DeviceFound) {
            addDebugLog("Canceling pairing - setting state to Disconnected")
            _connectionState.value = BleConnectionState.Disconnected
        }
        // Note: For connected devices, state will be set to Disconnected in onDisconnected() callback
    }

    /**
     * Start auto-reconnect after signal loss.
     * Tries to reconnect MAX_RECONNECT_ATTEMPTS times with RECONNECT_DELAY_MS delay between attempts.
     * If all attempts fail, goes to Disconnected state and sends DISCONNECTED tick event.
     */
    private fun startAutoReconnect() {
        val device = lastConnectedDevice
        if (device == null) {
            addDebugLog("Cannot auto-reconnect: no last connected device")
            _connectionState.value = BleConnectionState.Disconnected
            onEldDisconnected?.invoke(false)  // false = signal lost (not manual)
            return
        }

        reconnectJob?.cancel()
        reconnectJob = reconnectScope.launch {
            reconnectAttempt = 0

            while (reconnectAttempt < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempt++
                addDebugLog("🔄 Auto-reconnect attempt $reconnectAttempt/$MAX_RECONNECT_ATTEMPTS")
                Log.d(TAG, "════════════════════════════════════════")
                Log.d(TAG, "🔄 AUTO-RECONNECT ATTEMPT $reconnectAttempt/$MAX_RECONNECT_ATTEMPTS")
                Log.d(TAG, "   Device: ${device.address}")
                Log.d(TAG, "════════════════════════════════════════")

                // Update state to show reconnecting
                _connectionState.value = BleConnectionState.Reconnecting(device, reconnectAttempt, MAX_RECONNECT_ATTEMPTS)

                // Try to connect
                try {
                    WherequbeService.getInstance().connect(device.address)
                } catch (e: Exception) {
                    addDebugLog("Reconnect attempt failed: ${e.message}")
                }

                // Wait for connection or timeout
                delay(RECONNECT_DELAY_MS)

                // Check if we're connected now
                val currentState = _connectionState.value
                if (currentState is BleConnectionState.Ready || currentState is BleConnectionState.Connected) {
                    addDebugLog("✅ Auto-reconnect successful!")
                    Log.d(TAG, "✅ AUTO-RECONNECT SUCCESSFUL")
                    reconnectAttempt = 0
                    return@launch
                }
            }

            // All attempts failed
            addDebugLog("❌ Auto-reconnect failed after $MAX_RECONNECT_ATTEMPTS attempts")
            Log.d(TAG, "════════════════════════════════════════")
            Log.d(TAG, "❌ AUTO-RECONNECT FAILED")
            Log.d(TAG, "   All $MAX_RECONNECT_ATTEMPTS attempts exhausted")
            Log.d(TAG, "════════════════════════════════════════")

            // Now send DISCONNECTED tick event (after all retries failed)
            Log.d(TAG, "🔔 INVOKING onEldDisconnected callback (signal lost, reconnect failed)")
            onEldDisconnected?.invoke(false)  // false = signal lost (not manual)

            _connectionState.value = BleConnectionState.Disconnected
            reconnectAttempt = 0
        }
    }

    /**
     * Cancel any ongoing auto-reconnect attempts
     */
    fun cancelReconnect() {
        addDebugLog("Canceling auto-reconnect")
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
        _connectionState.value = BleConnectionState.Disconnected
    }

    fun requestEldData() {
        addDebugLog("Requesting ELD data...")
        _dataState.value = BleDataState.Receiving
        // Note: OBD data is automatically received once connected and synced
        // The dataRequestHandler will be called when data arrives
    }

    /**
     * Request to start receiving unidentified driving events from the ELD device.
     * Events will be available in:
     * - eldData.value?.unidentifiedEvents
     * - unidentifiedEvents StateFlow
     * - onUnidentifiedEventsReceived callback
     */
    fun startUnidentifiedEvents() {
        addDebugLog("📋 Starting unidentified events transmission...")
        try {
            val responseHandler = object : ResponseHandler {
                override fun onRecv(ctx: Context, response: BaseResponse) {
                    addDebugLog("📋 Start UD response: $response")
                    val responseObj = response.`object`
                    if (responseObj is GeoData) {
                        processUdEventsFromGeoData(responseObj)
                    }
                }
                override fun onError(ctx: Context) {
                    addDebugLog("❌ Start UD error!")
                }
            }
            WherequbeService.getInstance().sendRequest(
                UnidentifiedDriverMessageStartReq(),
                responseHandler,
                UDRV_EVENT_TIMEOUT
            )
            addDebugLog("📋 Start UD request sent")
        } catch (e: Exception) {
            addDebugLog("❌ Start UD failed: ${e.message}")
        }
    }

    /**
     * Request to stop receiving unidentified driving events.
     */
    fun stopUnidentifiedEvents() {
        addDebugLog("📋 Stopping unidentified events transmission...")
        try {
            val responseHandler = object : ResponseHandler {
                override fun onRecv(ctx: Context, response: BaseResponse) {
                    addDebugLog("📋 Stop UD response: $response")
                }
                override fun onError(ctx: Context) {
                    addDebugLog("❌ Stop UD error!")
                }
            }
            WherequbeService.getInstance().sendRequest(
                UnidentifiedDriverMessageStopReq(),
                responseHandler,
                UDRV_EVENT_TIMEOUT
            )
            addDebugLog("📋 Stop UD request sent")
        } catch (e: Exception) {
            addDebugLog("❌ Stop UD failed: ${e.message}")
        }
    }

    /**
     * Purge (delete) all unidentified driving events stored on the ELD device.
     * Use this after a driver has claimed/assigned the UD events.
     */
    fun purgeUnidentifiedEvents() {
        addDebugLog("📋 Purging unidentified events from device...")
        try {
            val responseHandler = object : ResponseHandler {
                override fun onRecv(ctx: Context, response: BaseResponse) {
                    addDebugLog("📋 Purge UD response: $response")
                }
                override fun onError(ctx: Context) {
                    addDebugLog("❌ Purge UD error!")
                }
            }
            WherequbeService.getInstance().sendRequest(
                UnidentifiedDriverMessagePurgeReq(),
                responseHandler,
                UDRV_EVENT_TIMEOUT
            )
            addDebugLog("📋 Purge UD request sent")
            // Clear local state
            _unidentifiedEvents.value = emptyList()
        } catch (e: Exception) {
            addDebugLog("❌ Purge UD failed: ${e.message}")
        }
    }

    /**
     * Helper method to process UD events from GeoData response
     */
    private fun processUdEventsFromGeoData(geoData: GeoData) {
        addDebugLog("📋 Processing UD events from GeoData...")
        addDebugLog("📋 Protocol: ${geoData.protocol}")
        addDebugLog("📋 TotalUdrvEvents: ${geoData.totalUdrvEvents}")
        addDebugLog("📋 UnidentifiedEventArrayList size: ${geoData.unidentifiedEventArrayList?.size ?: "NULL"}")

        val udEvents = geoData.unidentifiedEventArrayList?.map { sdkEvent ->
            UnidentifiedEvent(
                reason = sdkEvent.reason,
                timestamp = sdkEvent.timestamp,
                engineHours = sdkEvent.engTotalHours,
                speed = sdkEvent.vehicleSpeed,
                odometer = sdkEvent.odometer,
                latitude = sdkEvent.latitude,
                longitude = sdkEvent.longitude,
                gpsTimestamp = sdkEvent.getGPSTimestamp()
            )
        } ?: emptyList()

        if (udEvents.isNotEmpty()) {
            addDebugLog("📋 ═══ FOUND ${udEvents.size} UD EVENTS! ═══")
            udEvents.forEachIndexed { index, event ->
                addDebugLog("📋 Event #$index: ${event.getReasonString()} at ${event.timestamp}")
            }
            _unidentifiedEvents.value = udEvents
            onUnidentifiedEventsReceived?.invoke(udEvents)
        } else {
            addDebugLog("📋 No UD events found")
        }
    }

    /**
     * Check if there are any unidentified driving events available.
     */
    fun hasUnidentifiedEvents(): Boolean {
        return _unidentifiedEvents.value.isNotEmpty()
    }

    /**
     * Get the current count of unidentified driving events.
     */
    fun getUnidentifiedEventsCount(): Int {
        return _eldData.value?.totalUnidentifiedEvents ?: _unidentifiedEvents.value.size
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
