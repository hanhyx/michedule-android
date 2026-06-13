package com.ljh.michedule.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

private const val TAG = "LocationHelper"

data class LocationResult(
    val placeName: String,
    val latitude: Double,
    val longitude: Double
)

class LocationHelper(private val context: Context) {

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getCurrentLocation(): LocationResult? {
        if (!hasLocationPermission()) return null

        return try {
            val location = getLastOrCurrentLocation() ?: return null
            val placeName = reverseGeocode(location.first, location.second)
            LocationResult(placeName, location.first, location.second)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get location", e)
            null
        }
    }

    @SuppressWarnings("MissingPermission")
    private suspend fun getLastOrCurrentLocation(): Pair<Double, Double>? {
        return suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }

            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        cont.resume(Pair(loc.latitude, loc.longitude))
                    } else {
                        fusedClient.lastLocation.addOnSuccessListener { last ->
                            if (last != null) cont.resume(Pair(last.latitude, last.longitude))
                            else cont.resume(null)
                        }.addOnFailureListener { cont.resume(null) }
                    }
                }
                .addOnFailureListener {
                    Log.e(TAG, "getCurrentLocation failed", it)
                    cont.resume(null)
                }
        }
    }

    private fun reverseGeocode(lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.KOREAN)
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (addresses.isNullOrEmpty()) return formatCoords(lat, lng)

            val addr = addresses[0]
            buildString {
                val poi = addr.featureName
                val thoroughfare = addr.thoroughfare
                val subLocality = addr.subLocality
                val locality = addr.locality

                when {
                    poi != null && !poi.matches(Regex("^[\\d-]+$")) -> append(poi)
                    thoroughfare != null -> {
                        if (subLocality != null && subLocality != thoroughfare) append("$subLocality ")
                        append(thoroughfare)
                    }
                    subLocality != null -> append(subLocality)
                    locality != null -> append(locality)
                    else -> append(addr.getAddressLine(0)?.take(30) ?: formatCoords(lat, lng))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding failed", e)
            formatCoords(lat, lng)
        }
    }

    private fun formatCoords(lat: Double, lng: Double) =
        "%.4f, %.4f".format(lat, lng)
}
