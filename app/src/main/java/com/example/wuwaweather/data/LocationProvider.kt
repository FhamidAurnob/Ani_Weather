package com.example.wuwaweather.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Gets the device's approximate location (city-level is plenty for weather). */
class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Returns lat/lon, or null if permission is missing or location is turned off. */
    @SuppressLint("MissingPermission")
    suspend fun current(): Place.Coords? {
        if (!hasPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        return suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        cont.resume(Place.Coords(loc.latitude, loc.longitude))
                    } else {
                        // Fall back to the last known location
                        client.lastLocation
                            .addOnSuccessListener { last -> cont.resume(last?.let { Place.Coords(it.latitude, it.longitude) }) }
                            .addOnFailureListener { cont.resume(null) }
                    }
                }
                .addOnFailureListener { cont.resume(null) }
            cont.invokeOnCancellation { cts.cancel() }
        }
    }
}
