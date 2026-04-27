package com.betpass.mc01pilot.airport.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class DeviceLocation(val latitude: Double, val longitude: Double)

class LocationClient(private val context: Context) {
    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): DeviceLocation? {
        if (!hasLocationPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        return suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { loc ->
                    cont.resume(loc?.let { DeviceLocation(it.latitude, it.longitude) })
                }
                .addOnFailureListener { cont.resume(null) }
        }
    }
}

fun distanceKm(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
    val results = FloatArray(1)
    android.location.Location.distanceBetween(fromLat, fromLon, toLat, toLon, results)
    return results.first() / 1000.0
}
