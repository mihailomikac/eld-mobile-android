package com.eld.driver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.eld.driver.ELDDriverApplication
import com.eld.driver.MainActivity
import com.eld.driver.ble.GeometrisWQManager
import com.eld.driver.ble.models.BleConnectionState
import com.eld.driver.data.models.DutyStatusType
import com.eld.driver.data.models.ELDConnectionStatus
import com.eld.driver.location.LocationService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * ELD Foreground Service - Keeps the app running in the background
 *
 * This service is required for FMCSA compliance because:
 * - Must track location continuously while driver is on duty
 * - Must maintain BLE connection to ELD device
 * - Must detect vehicle motion and auto-change duty status
 * - Must record all driving time accurately
 *
 * The service runs with a persistent notification showing:
 * - Current duty status
 * - ELD connection status
 * - Vehicle info (if connected)
 */
class ELDForegroundService : Service() {

    companion object {
        private const val TAG = "ELDForegroundService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "eld_foreground_channel"
        const val CHANNEL_NAME = "ELD Active"

        // Actions
        const val ACTION_START = "com.eld.driver.service.START"
        const val ACTION_STOP = "com.eld.driver.service.STOP"
        const val ACTION_UPDATE_STATUS = "com.eld.driver.service.UPDATE_STATUS"

        // Extras
        const val EXTRA_DUTY_STATUS = "duty_status"
        const val EXTRA_ELD_CONNECTED = "eld_connected"
        const val EXTRA_VEHICLE_NAME = "vehicle_name"

        /**
         * Start the foreground service
         */
        fun start(context: Context) {
            val intent = Intent(context, ELDForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "📤 Start service requested")
        }

        /**
         * Stop the foreground service
         */
        fun stop(context: Context) {
            val intent = Intent(context, ELDForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
            Log.d(TAG, "📤 Stop service requested")
        }

        /**
         * Update notification with new status
         */
        fun updateStatus(
            context: Context,
            dutyStatus: DutyStatusType? = null,
            eldConnected: Boolean? = null,
            vehicleName: String? = null
        ) {
            val intent = Intent(context, ELDForegroundService::class.java).apply {
                action = ACTION_UPDATE_STATUS
                dutyStatus?.let { putExtra(EXTRA_DUTY_STATUS, it.name) }
                eldConnected?.let { putExtra(EXTRA_ELD_CONNECTED, it) }
                vehicleName?.let { putExtra(EXTRA_VEHICLE_NAME, it) }
            }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var currentDutyStatus: DutyStatusType = DutyStatusType.OFF_DUTY
    private var isEldConnected: Boolean = false
    private var vehicleName: String? = null

    private lateinit var locationService: LocationService
    private lateinit var bleManager: GeometrisWQManager

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "🚀 ELD Foreground Service CREATED")
        Log.d(TAG, "════════════════════════════════════════")

        createNotificationChannel()
        locationService = LocationService.getInstance(this)
        bleManager = GeometrisWQManager.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "▶️ ACTION_START received")
                startForegroundService()
                startObservers()
            }
            ACTION_STOP -> {
                Log.d(TAG, "⏹️ ACTION_STOP received")
                stopForegroundService()
            }
            ACTION_UPDATE_STATUS -> {
                intent.getStringExtra(EXTRA_DUTY_STATUS)?.let {
                    currentDutyStatus = try {
                        DutyStatusType.valueOf(it)
                    } catch (e: Exception) {
                        DutyStatusType.OFF_DUTY
                    }
                }
                intent.getBooleanExtra(EXTRA_ELD_CONNECTED, isEldConnected).let {
                    isEldConnected = it
                }
                intent.getStringExtra(EXTRA_VEHICLE_NAME)?.let {
                    vehicleName = it
                }
                updateNotification()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "💀 ELD Foreground Service DESTROYED")
        Log.d(TAG, "════════════════════════════════════════")
        releaseWakeLock()
        serviceScope.cancel()
    }

    private fun startForegroundService() {
        Log.d(TAG, "🔔 Starting foreground with notification...")

        // Acquire wake lock to keep CPU running
        acquireWakeLock()

        // Start location updates
        locationService.startLocationUpdates()

        // Show foreground notification
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        Log.d(TAG, "✅ Foreground service started successfully")
    }

    private fun stopForegroundService() {
        Log.d(TAG, "🛑 Stopping foreground service...")

        // Stop location updates
        locationService.stopLocationUpdates()

        // Release wake lock
        releaseWakeLock()

        // Stop foreground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()

        Log.d(TAG, "✅ Foreground service stopped")
    }

    private fun startObservers() {
        // Observe BLE connection status
        serviceScope.launch {
            bleManager.connectionState.collectLatest { state ->
                isEldConnected = state is BleConnectionState.Connected || state is BleConnectionState.Ready
                updateNotification()
            }
        }

        // Observe ELD data for vehicle info
        serviceScope.launch {
            bleManager.eldData.collectLatest { data ->
                // Update notification periodically
                updateNotification()
            }
        }

        // Periodic location logging (every 5 minutes for FMCSA compliance)
        serviceScope.launch {
            while (isActive) {
                delay(5 * 60 * 1000) // 5 minutes
                val location = locationService.getCurrentLocation()
                if (location != null) {
                    Log.d(TAG, "📍 Periodic location: ${location.latitude}, ${location.longitude}")
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Low importance = no sound
            ).apply {
                description = "ELD Driver app is actively tracking your duty status"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "📢 Notification channel created")
        }
    }

    private fun createNotification(): Notification {
        // Intent to open app when notification is tapped
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build status text
        val statusText = buildStatusText()
        val contentTitle = "ELD Active - ${getDutyStatusDisplayName(currentDutyStatus)}"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_compass) // Use system icon (replace with app icon later)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setColor(getDutyStatusColor())
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun buildStatusText(): String {
        val parts = mutableListOf<String>()

        // ELD connection status
        if (isEldConnected) {
            parts.add("ELD Connected")
        } else {
            parts.add("ELD Disconnected")
        }

        // Vehicle name
        vehicleName?.let { parts.add(it) }

        // Driver name
        ELDDriverApplication.getCurrentUser()?.fullName?.let {
            parts.add(it)
        }

        return parts.joinToString(" • ")
    }

    private fun getDutyStatusDisplayName(status: DutyStatusType): String {
        return when (status) {
            DutyStatusType.OFF_DUTY -> "Off Duty"
            DutyStatusType.SLEEPER_BERTH -> "Sleeper"
            DutyStatusType.DRIVING -> "Driving"
            DutyStatusType.ON_DUTY_NOT_DRIVING -> "On Duty"
            DutyStatusType.YARD_MOVE -> "Yard Move"
            DutyStatusType.PERSONAL_CONVEYANCE -> "Personal Use"
        }
    }

    private fun getDutyStatusColor(): Int {
        return when (currentDutyStatus) {
            DutyStatusType.OFF_DUTY -> 0xFF9E9E9E.toInt() // Gray
            DutyStatusType.SLEEPER_BERTH -> 0xFF7E57C2.toInt() // Purple
            DutyStatusType.DRIVING -> 0xFF4CAF50.toInt() // Green
            DutyStatusType.ON_DUTY_NOT_DRIVING -> 0xFF2196F3.toInt() // Blue
            DutyStatusType.YARD_MOVE -> 0xFFFF9800.toInt() // Orange
            DutyStatusType.PERSONAL_CONVEYANCE -> 0xFF00BCD4.toInt() // Cyan
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ELDDriver::ForegroundServiceWakeLock"
            ).apply {
                acquire(10 * 60 * 60 * 1000L) // 10 hours max
            }
            Log.d(TAG, "🔋 Wake lock acquired")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "🔋 Wake lock released")
            }
        }
        wakeLock = null
    }
}
