package com.eld.driver.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.eld.driver.ble.GeometrisWQManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Location data class containing coordinates and address
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val source: LocationSource,
    val timestamp: Long = System.currentTimeMillis()
)

enum class LocationSource {
    BLE_DEVICE,  // From Geometris ELD device
    GPS,         // From phone GPS
    UNKNOWN
}

/**
 * LocationService - Combines BLE device location with phone GPS as fallback
 * Priority: BLE device location > Phone GPS
 *
 * Supports both Google Play Services (FusedLocationProvider) and native Android LocationManager
 * for devices without GMS (e.g., Huawei phones).
 *
 * Also provides FMCSA-compliant location formatting for offline use.
 */
@SuppressLint("MissingPermission")
class LocationService private constructor(private val context: Context) {

    companion object {
        private const val TAG = "LocationService"

        @Volatile
        private var INSTANCE: LocationService? = null

        fun getInstance(context: Context): LocationService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocationService(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val bleManager = GeometrisWQManager.getInstance(context)

    // Check if Google Play Services is available (not available on Huawei devices)
    private val hasGooglePlayServices: Boolean by lazy {
        val availability = GoogleApiAvailability.getInstance()
        val result = availability.isGooglePlayServicesAvailable(context)
        val available = result == ConnectionResult.SUCCESS
        Log.d(TAG, "Google Play Services available: $available (result code: $result)")
        available
    }

    // Google's FusedLocationProvider (requires Google Play Services)
    private val fusedLocationClient: FusedLocationProviderClient? by lazy {
        if (hasGooglePlayServices) {
            LocationServices.getFusedLocationProviderClient(context)
        } else {
            Log.w(TAG, "Google Play Services not available, using native LocationManager")
            null
        }
    }

    // Native Android LocationManager (works on ALL devices including Huawei)
    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private val geocoder = Geocoder(context, Locale.getDefault())

    // FMCSA location formatter for offline city lookup
    private val fmcsaFormatter = FMCSALocationFormatter.getInstance(context)

    private val _currentLocation = MutableStateFlow<LocationData?>(null)
    val currentLocation: StateFlow<LocationData?> = _currentLocation.asStateFlow()

    private val _isGpsEnabled = MutableStateFlow(false)
    val isGpsEnabled: StateFlow<Boolean> = _isGpsEnabled.asStateFlow()

    private var locationCallback: LocationCallback? = null

    /**
     * Get current location - tries BLE first, then GPS
     * Returns cached location if available, or requests new location
     * Address is always in FMCSA format: "{X} mi. {direction} of {city}, {state}"
     */
    fun getCurrentLocation(): LocationData? {
        // First priority: BLE device location
        val eldData = bleManager.eldData.value
        if (eldData?.latitude != null && eldData.longitude != null) {
            val locationAge = eldData.getLocationAgeInSeconds() ?: Long.MAX_VALUE
            // Use BLE location if it's less than 5 minutes old
            if (locationAge < 300) {
                // Use FMCSA format for address (offline, always works)
                // Wrapped in try-catch to prevent crash if database not ready
                val address = try {
                    runBlocking { getFMCSALocation(eldData.latitude!!, eldData.longitude!!) }
                } catch (e: Exception) {
                    Log.w(TAG, "FMCSA formatting failed, using coordinates: ${e.message}")
                    "${String.format("%.4f", eldData.latitude)}, ${String.format("%.4f", eldData.longitude)}"
                }
                val locationData = LocationData(
                    latitude = eldData.latitude!!,
                    longitude = eldData.longitude!!,
                    address = address,
                    source = LocationSource.BLE_DEVICE
                )
                _currentLocation.value = locationData
                Log.d(TAG, "Using BLE location: ${eldData.latitude}, ${eldData.longitude} -> $address")
                return locationData
            }
        }

        // Second priority: Return cached GPS location if fresh enough
        val cached = _currentLocation.value
        if (cached != null && cached.source == LocationSource.GPS) {
            val age = System.currentTimeMillis() - cached.timestamp
            if (age < 60000) { // Less than 1 minute old
                return cached
            }
        }

        // Request fresh GPS location
        requestGpsLocation()
        return _currentLocation.value
    }

    /**
     * Request fresh GPS location.
     * Uses FusedLocationProvider if Google Play Services available,
     * otherwise falls back to native LocationManager (for Huawei, etc.)
     */
    fun requestGpsLocation() {
        if (hasGooglePlayServices && fusedLocationClient != null) {
            requestGpsLocationGoogle()
        } else {
            requestGpsLocationNative()
        }
    }

    /**
     * Request location using Google's FusedLocationProvider
     */
    private fun requestGpsLocationGoogle() {
        try {
            fusedLocationClient?.lastLocation?.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    updateLocationFromGps(location)
                } else {
                    // Request location update if last location is null
                    requestLocationUpdateGoogle()
                }
            }?.addOnFailureListener { e: Exception ->
                Log.e(TAG, "Google location failed, trying native: ${e.message}")
                // Fallback to native if Google fails
                requestGpsLocationNative()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Google location error, trying native: ${e.message}")
            requestGpsLocationNative()
        }
    }

    /**
     * Request location using native Android LocationManager
     * Works on ALL devices including Huawei without Google Play Services
     */
    private fun requestGpsLocationNative() {
        Log.d(TAG, "Using native LocationManager")
        try {
            // Try GPS provider first
            var location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

            // Fallback to network provider if GPS not available
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }

            if (location != null) {
                val age = System.currentTimeMillis() - location.time
                // Use if less than 2 minutes old
                if (age < 120000) {
                    Log.d(TAG, "Native: Using cached location (age: ${age/1000}s)")
                    updateLocationFromGps(location)
                    return
                }
            }

            // Request fresh location
            requestLocationUpdateNative()
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Native location error: ${e.message}")
        }
    }

    /**
     * Start continuous location updates.
     * Uses appropriate provider based on device capabilities.
     */
    fun startLocationUpdates() {
        if (hasGooglePlayServices && fusedLocationClient != null) {
            startLocationUpdatesGoogle()
        } else {
            startLocationUpdatesNative()
        }
    }

    /**
     * Start continuous location updates using Google's FusedLocationProvider
     */
    private fun startLocationUpdatesGoogle() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000 // Update every 10 seconds
        ).setMinUpdateIntervalMillis(5000).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location: Location ->
                    // Only use GPS if BLE location is not available or stale
                    val eldData = bleManager.eldData.value
                    val bleLocationAge = eldData?.getLocationAgeInSeconds() ?: Long.MAX_VALUE

                    if (eldData?.latitude == null || bleLocationAge > 300) {
                        updateLocationFromGps(location)
                    }
                }
            }
        }

        try {
            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            _isGpsEnabled.value = true
            Log.d(TAG, "Started Google location updates")
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted: ${e.message}")
        }
    }

    // Native location listener for continuous updates
    private var nativeLocationListener: LocationListener? = null

    /**
     * Start continuous location updates using native Android LocationManager
     * For devices without Google Play Services (Huawei, etc.)
     */
    private fun startLocationUpdatesNative() {
        Log.d(TAG, "Starting native location updates")

        nativeLocationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                // Only use GPS if BLE location is not available or stale
                val eldData = bleManager.eldData.value
                val bleLocationAge = eldData?.getLocationAgeInSeconds() ?: Long.MAX_VALUE

                if (eldData?.latitude == null || bleLocationAge > 300) {
                    updateLocationFromGps(location)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {
                Log.d(TAG, "Native: Provider enabled: $provider")
            }
            override fun onProviderDisabled(provider: String) {
                Log.w(TAG, "Native: Provider disabled: $provider")
            }
        }

        try {
            // Request from GPS provider
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    10000L,  // Update every 10 seconds
                    10f,     // Or every 10 meters
                    nativeLocationListener!!,
                    Looper.getMainLooper()
                )
                Log.d(TAG, "Started native GPS location updates")
            }

            // Also request from network provider as backup
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    10000L,
                    10f,
                    nativeLocationListener!!,
                    Looper.getMainLooper()
                )
                Log.d(TAG, "Started native Network location updates")
            }

            _isGpsEnabled.value = true
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting native location updates: ${e.message}")
        }
    }

    /**
     * Stop location updates (both Google and native)
     */
    fun stopLocationUpdates() {
        // Stop Google location updates
        locationCallback?.let {
            fusedLocationClient?.removeLocationUpdates(it)
            locationCallback = null
        }

        // Stop native location updates
        nativeLocationListener?.let {
            try {
                locationManager.removeUpdates(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing native location updates: ${e.message}")
            }
            nativeLocationListener = null
        }

        _isGpsEnabled.value = false
        Log.d(TAG, "Stopped location updates")
    }

    /**
     * Request single location update using Google's FusedLocationProvider
     */
    private fun requestLocationUpdateGoogle() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000
        ).setMaxUpdates(1).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location: Location ->
                    updateLocationFromGps(location)
                }
                fusedLocationClient?.removeLocationUpdates(this)
            }
        }

        try {
            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted: ${e.message}")
        }
    }

    /**
     * Request single location update using native Android LocationManager
     * Works on Huawei and other devices without Google Play Services
     */
    private fun requestLocationUpdateNative() {
        Log.d(TAG, "Native: Requesting fresh location")

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                Log.d(TAG, "Native: Got fresh location")
                updateLocationFromGps(location)
                // Remove listener after getting location
                try {
                    locationManager.removeUpdates(this)
                } catch (e: Exception) {
                    Log.w(TAG, "Error removing location updates: ${e.message}")
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            // Try GPS provider first
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,  // min time between updates (ms)
                    0f,     // min distance between updates (meters)
                    locationListener,
                    Looper.getMainLooper()
                )
                Log.d(TAG, "Native: Requested GPS location updates")
            }
            // Also try network provider as backup
            else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    0f,
                    locationListener,
                    Looper.getMainLooper()
                )
                Log.d(TAG, "Native: Requested Network location updates")
            } else {
                Log.w(TAG, "Native: No location provider available!")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting native location: ${e.message}")
        }
    }

    private fun updateLocationFromGps(location: Location) {
        // Use FMCSA format for address (offline, always works)
        // Wrapped in try-catch to prevent crash if database not ready
        val address = try {
            runBlocking { getFMCSALocation(location.latitude, location.longitude) }
        } catch (e: Exception) {
            Log.w(TAG, "FMCSA formatting failed, using coordinates: ${e.message}")
            "${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}"
        }
        val locationData = LocationData(
            latitude = location.latitude,
            longitude = location.longitude,
            address = address,
            source = LocationSource.GPS
        )
        _currentLocation.value = locationData
        Log.d(TAG, "Updated GPS location: ${location.latitude}, ${location.longitude} -> $address")
    }

    /**
     * @deprecated Use getFMCSALocation() instead for FMCSA-compliant offline formatting.
     * This method uses online Android Geocoder which may not work offline.
     */
    @Deprecated("Use getFMCSALocation() for FMCSA-compliant offline formatting",
        ReplaceWith("runBlocking { getFMCSALocation(latitude, longitude) }"))
    @Suppress("DEPRECATION")
    fun getAddressFromCoordinates(latitude: Double, longitude: Double): String? {
        // Fallback to FMCSA format instead of old geocoder
        return try {
            runBlocking { getFMCSALocation(latitude, longitude) }
        } catch (e: Exception) {
            "${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}"
        }
    }

    /**
     * @deprecated Use getFMCSALocation() instead.
     */
    @Deprecated("Use getFMCSALocation() for FMCSA-compliant formatting",
        ReplaceWith("runBlocking { getFMCSALocation(latitude, longitude) }"))
    fun formatLocationWithDistance(latitude: Double, longitude: Double, referenceCity: String? = null): String {
        return try {
            runBlocking { getFMCSALocation(latitude, longitude) }
        } catch (e: Exception) {
            "${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}"
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FMCSA-COMPLIANT LOCATION METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get FMCSA-compliant location string.
     * Format: "{X} miles {direction} of {city}, {state}"
     *
     * Works completely offline using local city database.
     * Per FMCSA 49 CFR 395.8 requirements.
     *
     * @param latitude GPS latitude in decimal degrees
     * @param longitude GPS longitude in decimal degrees
     * @return Formatted location string or coordinates as fallback
     */
    suspend fun getFMCSALocation(latitude: Double, longitude: Double): String {
        return fmcsaFormatter.formatLocation(latitude, longitude)
    }

    /**
     * Get current location with FMCSA-formatted address.
     * Combines GPS/BLE location with offline FMCSA formatting.
     *
     * @return LocationData with FMCSA-formatted address, or null if no location available
     */
    suspend fun getCurrentLocationFMCSA(): LocationData? {
        val location = getCurrentLocation() ?: return null

        val fmcsaAddress = getFMCSALocation(location.latitude, location.longitude)

        return location.copy(address = fmcsaAddress)
    }

    /**
     * Check if FMCSA city database is populated.
     */
    suspend fun isFMCSADatabaseReady(): Boolean {
        return fmcsaFormatter.isDatabasePopulated()
    }

    // ═══════════════════════════════════════════════════════════════
    // HUAWEI-COMPATIBLE LOCATION METHODS (WAIT FOR LOCATION)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get location with fallback - WAITS for location if not immediately available.
     * This is essential for Huawei phones where getLastKnownLocation() returns null.
     *
     * Flow:
     * 1. Try BLE device location (if connected)
     * 2. Try cached GPS location (if recent)
     * 3. Try getLastKnownLocation (GPS then Network)
     * 4. If still null, request fresh location and WAIT up to timeout
     *
     * @param timeoutMs Maximum time to wait for location (default 10 seconds)
     * @return LocationData or null if location unavailable after timeout
     */
    suspend fun getLocationWithFallback(timeoutMs: Long = 10000): LocationData? {
        Log.d(TAG, "🔍 getLocationWithFallback: Starting (timeout=${timeoutMs}ms)")

        // 1. Try BLE device location first
        val eldData = bleManager.eldData.value
        if (eldData?.latitude != null && eldData.longitude != null) {
            val locationAge = eldData.getLocationAgeInSeconds() ?: Long.MAX_VALUE
            if (locationAge < 300) {
                val address = try {
                    getFMCSALocation(eldData.latitude!!, eldData.longitude!!)
                } catch (e: Exception) {
                    "${String.format("%.4f", eldData.latitude)}, ${String.format("%.4f", eldData.longitude)}"
                }
                Log.d(TAG, "🔍 Using BLE location: ${eldData.latitude}, ${eldData.longitude}")
                return LocationData(
                    latitude = eldData.latitude!!,
                    longitude = eldData.longitude!!,
                    address = address,
                    source = LocationSource.BLE_DEVICE
                )
            }
        }

        // 2. Try cached location if fresh
        val cached = _currentLocation.value
        if (cached != null) {
            val age = System.currentTimeMillis() - cached.timestamp
            if (age < 60000) {
                Log.d(TAG, "🔍 Using cached location (age: ${age/1000}s)")
                return cached
            }
        }

        // 3. Try getLastKnownLocation (works better on some devices)
        val lastKnown = getLastKnownLocationDirect()
        if (lastKnown != null) {
            Log.d(TAG, "🔍 Using last known location")
            return lastKnown
        }

        // 4. Request fresh location and WAIT for it (essential for Huawei)
        Log.d(TAG, "🔍 No cached location, requesting fresh (will wait up to ${timeoutMs}ms)")
        return requestLocationAndWait(timeoutMs)
    }

    /**
     * Get last known location directly from LocationManager.
     * Tries GPS provider first, then Network provider.
     */
    private suspend fun getLastKnownLocationDirect(): LocationData? {
        try {
            // Try GPS provider
            var location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

            // Fallback to network provider
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }

            if (location != null) {
                val age = System.currentTimeMillis() - location.time
                // Accept if less than 5 minutes old
                if (age < 300000) {
                    val address = try {
                        getFMCSALocation(location.latitude, location.longitude)
                    } catch (e: Exception) {
                        "${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}"
                    }
                    val locationData = LocationData(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        address = address,
                        source = LocationSource.GPS,
                        timestamp = location.time
                    )
                    _currentLocation.value = locationData
                    return locationData
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Error getting last known location: ${e.message}")
        }
        return null
    }

    /**
     * Request fresh location and wait for result.
     * Uses native LocationManager to work on ALL devices including Huawei.
     */
    private suspend fun requestLocationAndWait(timeoutMs: Long): LocationData? {
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        Log.d(TAG, "🔍 Got fresh location: ${location.latitude}, ${location.longitude}")
                        try {
                            locationManager.removeUpdates(this)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error removing updates: ${e.message}")
                        }

                        val address = try {
                            runBlocking { getFMCSALocation(location.latitude, location.longitude) }
                        } catch (e: Exception) {
                            "${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}"
                        }

                        val locationData = LocationData(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            address = address,
                            source = LocationSource.GPS,
                            timestamp = location.time
                        )
                        _currentLocation.value = locationData

                        if (continuation.isActive) {
                            continuation.resume(locationData)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }

                try {
                    var providerUsed = false

                    // Try GPS provider
                    if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            0L, 0f,
                            listener,
                            Looper.getMainLooper()
                        )
                        providerUsed = true
                        Log.d(TAG, "🔍 Requested GPS provider updates")
                    }

                    // Also try Network provider (often faster)
                    if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            0L, 0f,
                            listener,
                            Looper.getMainLooper()
                        )
                        providerUsed = true
                        Log.d(TAG, "🔍 Requested Network provider updates")
                    }

                    if (!providerUsed) {
                        Log.w(TAG, "🔍 No location provider available!")
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }

                    // Clean up on cancellation
                    continuation.invokeOnCancellation {
                        try {
                            locationManager.removeUpdates(listener)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error removing updates on cancel: ${e.message}")
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Location permission not granted: ${e.message}")
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error requesting location: ${e.message}")
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        }
    }
}
