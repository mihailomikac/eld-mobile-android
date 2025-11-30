package com.eld.driver.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import android.util.Log
import com.eld.driver.ble.GeometrisWQManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

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
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val geocoder = Geocoder(context, Locale.getDefault())

    private val _currentLocation = MutableStateFlow<LocationData?>(null)
    val currentLocation: StateFlow<LocationData?> = _currentLocation.asStateFlow()

    private val _isGpsEnabled = MutableStateFlow(false)
    val isGpsEnabled: StateFlow<Boolean> = _isGpsEnabled.asStateFlow()

    private var locationCallback: LocationCallback? = null

    /**
     * Get current location - tries BLE first, then GPS
     * Returns cached location if available, or requests new location
     */
    fun getCurrentLocation(): LocationData? {
        // First priority: BLE device location
        val eldData = bleManager.eldData.value
        if (eldData?.latitude != null && eldData.longitude != null) {
            val locationAge = eldData.getLocationAgeInSeconds() ?: Long.MAX_VALUE
            // Use BLE location if it's less than 5 minutes old
            if (locationAge < 300) {
                val address = getAddressFromCoordinates(eldData.latitude!!, eldData.longitude!!)
                val locationData = LocationData(
                    latitude = eldData.latitude!!,
                    longitude = eldData.longitude!!,
                    address = address,
                    source = LocationSource.BLE_DEVICE
                )
                _currentLocation.value = locationData
                Log.d(TAG, "Using BLE location: ${eldData.latitude}, ${eldData.longitude}")
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
     * Request fresh GPS location
     */
    fun requestGpsLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    updateLocationFromGps(location)
                } else {
                    // Request location update if last location is null
                    requestLocationUpdate()
                }
            }.addOnFailureListener { e: Exception ->
                Log.e(TAG, "Failed to get last location: ${e.message}")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted: ${e.message}")
        }
    }

    /**
     * Start continuous location updates
     */
    fun startLocationUpdates() {
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
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            _isGpsEnabled.value = true
            Log.d(TAG, "Started location updates")
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted: ${e.message}")
        }
    }

    /**
     * Stop location updates
     */
    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
        _isGpsEnabled.value = false
        Log.d(TAG, "Stopped location updates")
    }

    private fun requestLocationUpdate() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000
        ).setMaxUpdates(1).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location: Location ->
                    updateLocationFromGps(location)
                }
                fusedLocationClient.removeLocationUpdates(this)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted: ${e.message}")
        }
    }

    private fun updateLocationFromGps(location: Location) {
        val address = getAddressFromCoordinates(location.latitude, location.longitude)
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
     * Reverse geocoding - get address from coordinates
     */
    @Suppress("DEPRECATION")
    fun getAddressFromCoordinates(latitude: Double, longitude: Double): String? {
        return try {
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                // Format: "City, State" or "Distance + Direction from City, State"
                val city = address.locality ?: address.subAdminArea
                val state = address.adminArea

                when {
                    city != null && state != null -> "$city, $state"
                    city != null -> city
                    state != null -> state
                    address.getAddressLine(0) != null -> address.getAddressLine(0)
                    else -> "${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}"
                }
            } else {
                "${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding failed: ${e.message}")
            "${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}"
        }
    }

    /**
     * Format location as "Xmi Direction of City, State" style
     */
    fun formatLocationWithDistance(latitude: Double, longitude: Double, referenceCity: String? = null): String {
        val address = getAddressFromCoordinates(latitude, longitude)
        return address ?: "${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}"
    }
}
