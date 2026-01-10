package com.eld.driver.service

import android.content.Context
import android.util.Log
import com.eld.driver.data.api.ApiConfig
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubConnectionState
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

/**
 * SignalR Service for real-time notifications from backend.
 * Handles ForceLogout events when driver logs in from another device.
 */
class SignalRService private constructor(context: Context) {
    companion object {
        private const val TAG = "SignalRService"
        private const val HUB_PATH = "/hubs/mobile"

        @Volatile
        private var instance: SignalRService? = null

        fun getInstance(context: Context): SignalRService {
            return instance ?: synchronized(this) {
                instance ?: SignalRService(context.applicationContext).also { instance = it }
            }
        }
    }

    // Lock to prevent race conditions between connect and disconnect
    private val connectionLock = Object()
    private var hubConnection: HubConnection? = null

    // Session counter to prevent old disconnect from killing new connection
    @Volatile
    private var sessionCounter = 0L

    // Force logout event - when another device logs in
    private val _forceLogoutEvent = MutableStateFlow<ForceLogoutEvent?>(null)
    val forceLogoutEvent: StateFlow<ForceLogoutEvent?> = _forceLogoutEvent.asStateFlow()

    // Connection state
    private val _connectionState = MutableStateFlow(SignalRConnectionState.DISCONNECTED)
    val connectionState: StateFlow<SignalRConnectionState> = _connectionState.asStateFlow()

    /**
     * Connect to SignalR hub with authentication token.
     * Call this after successful login.
     * Uses synchronization to prevent race conditions with disconnect.
     */
    fun connect(token: String) {
        synchronized(connectionLock) {
            // Increment session counter - any pending disconnect from old session will be ignored
            sessionCounter++
            val currentSession = sessionCounter

            Log.d(TAG, "════════════════════════════════════════")
            Log.d(TAG, "📡 SIGNALR CONNECT CALLED (session #$currentSession)")
            Log.d(TAG, "════════════════════════════════════════")

            if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
                Log.d(TAG, "Already connected to SignalR hub")
                return
            }

            // Disconnect existing connection if any - wait for it to complete
            if (hubConnection != null) {
                Log.d(TAG, "Disconnecting existing connection before reconnecting...")
                try {
                    // Use blockingAwait to ensure disconnect completes before proceeding
                    hubConnection?.stop()?.blockingAwait(5, TimeUnit.SECONDS)
                    Log.d(TAG, "✅ Existing connection stopped")
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping existing connection: ${e.message}")
                }
                hubConnection = null
            }

            try {
                // Build the hub URL (without token in query - use withAccessTokenProvider instead)
                // Remove "Bearer " prefix if present
                val cleanToken = token.removePrefix("Bearer ").trim()
                val hubUrl = "${ApiConfig.BASE_URL}$HUB_PATH"

                Log.d(TAG, "Hub URL: $hubUrl")
                Log.d(TAG, "Token (first 50 chars): ${cleanToken.take(50)}...")

                // Use withAccessTokenProvider for proper authorization
                // This sends the token in the Authorization header for negotiate
                // and as query param for WebSocket connection
                hubConnection = HubConnectionBuilder.create(hubUrl)
                    .withAccessTokenProvider(Single.just(cleanToken))
                    .build()

                // Register event handlers BEFORE starting connection
                setupEventHandlers()

                // Handle connection state changes
                hubConnection?.onClosed { error ->
                    Log.w(TAG, "════════════════════════════════════════")
                    Log.w(TAG, "⚠️ SIGNALR CONNECTION CLOSED")
                    Log.w(TAG, "Error: ${error?.message ?: "No error"}")
                    Log.w(TAG, "════════════════════════════════════════")
                    _connectionState.value = SignalRConnectionState.DISCONNECTED
                }

                // Start the connection
                _connectionState.value = SignalRConnectionState.CONNECTING
                Log.d(TAG, "Starting SignalR connection...")

                // Use subscribe instead of blockingAwait for better async handling
                hubConnection?.start()?.subscribe(
                    {
                        // onComplete
                        Log.d(TAG, "════════════════════════════════════════")
                        Log.d(TAG, "✅ SIGNALR CONNECTED SUCCESSFULLY")
                        Log.d(TAG, "Connection state: ${hubConnection?.connectionState}")
                        Log.d(TAG, "════════════════════════════════════════")
                        _connectionState.value = SignalRConnectionState.CONNECTED
                    },
                    { error ->
                        // onError
                        Log.e(TAG, "════════════════════════════════════════")
                        Log.e(TAG, "❌ SIGNALR CONNECTION FAILED")
                        Log.e(TAG, "Error: ${error.message}")
                        Log.e(TAG, "════════════════════════════════════════", error)
                        _connectionState.value = SignalRConnectionState.ERROR
                    }
                )

            } catch (e: Exception) {
                Log.e(TAG, "════════════════════════════════════════")
                Log.e(TAG, "❌ SIGNALR CONNECT EXCEPTION")
                Log.e(TAG, "Error: ${e.message}")
                Log.e(TAG, "════════════════════════════════════════", e)
                _connectionState.value = SignalRConnectionState.ERROR
            }
        }
    }

    /**
     * Disconnect from SignalR hub.
     * Call this on logout.
     * Uses synchronization to prevent race conditions with connect.
     * Will NOT disconnect if a new session has started (prevents old logout from killing new connection).
     */
    fun disconnect() {
        // Capture current session BEFORE acquiring lock
        val sessionAtDisconnectCall = sessionCounter

        synchronized(connectionLock) {
            Log.d(TAG, "════════════════════════════════════════")
            Log.d(TAG, "📡 SIGNALR DISCONNECT CALLED")
            Log.d(TAG, "   Session at call: #$sessionAtDisconnectCall")
            Log.d(TAG, "   Current session: #$sessionCounter")
            Log.d(TAG, "════════════════════════════════════════")

            // If a new session has started since disconnect was called, skip the disconnect
            // This prevents: old logout → new login → old logout kills new connection
            if (sessionAtDisconnectCall != sessionCounter) {
                Log.w(TAG, "⚠️ SKIPPING DISCONNECT - new session started!")
                Log.w(TAG, "   Disconnect was for session #$sessionAtDisconnectCall")
                Log.w(TAG, "   But current session is #$sessionCounter")
                Log.w(TAG, "   This prevents old logout from killing new connection")
                return
            }

            try {
                if (hubConnection != null) {
                    // Use blockingAwait to ensure disconnect completes
                    hubConnection?.stop()?.blockingAwait(5, TimeUnit.SECONDS)
                    Log.d(TAG, "✅ SignalR connection stopped")
                }
                hubConnection = null
                _connectionState.value = SignalRConnectionState.DISCONNECTED
                _forceLogoutEvent.value = null
                Log.d(TAG, "✅ SignalR disconnected and state cleared")
            } catch (e: Exception) {
                Log.w(TAG, "Error disconnecting from SignalR: ${e.message}")
                // Still clear the state even if stop() fails
                hubConnection = null
                _connectionState.value = SignalRConnectionState.DISCONNECTED
                _forceLogoutEvent.value = null
            }
        }
    }

    /**
     * Clear the force logout event after it's been handled.
     */
    fun clearForceLogoutEvent() {
        _forceLogoutEvent.value = null
    }

    /**
     * Check if connected
     */
    fun isConnected(): Boolean {
        return hubConnection?.connectionState == HubConnectionState.CONNECTED
    }

    private fun setupEventHandlers() {
        Log.d(TAG, "Setting up SignalR event handlers...")

        // Handle ForceLogout event
        hubConnection?.on("ForceLogout", { data ->
            Log.w(TAG, "════════════════════════════════════════")
            Log.w(TAG, "!!! FORCE LOGOUT EVENT RECEIVED !!!")
            Log.w(TAG, "Raw data: $data")
            Log.w(TAG, "Data type: ${data?.javaClass?.name}")
            Log.w(TAG, "════════════════════════════════════════")

            // Parse the data - it comes as a map or object
            val reason = try {
                when (data) {
                    is Map<*, *> -> {
                        data["Reason"]?.toString()
                            ?: data["reason"]?.toString()
                            ?: "You have been logged out from another device."
                    }
                    is String -> data
                    else -> {
                        Log.w(TAG, "Unknown data format, using default message")
                        "You have been logged out from another device."
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing ForceLogout data: ${e.message}")
                "You have been logged out from another device."
            }

            Log.w(TAG, "Parsed reason: $reason")
            _forceLogoutEvent.value = ForceLogoutEvent(reason = reason)

        }, Any::class.java)

        Log.d(TAG, "✅ Event handlers registered")
    }
}

/**
 * Force logout event data
 */
data class ForceLogoutEvent(
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * SignalR connection state
 */
enum class SignalRConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
