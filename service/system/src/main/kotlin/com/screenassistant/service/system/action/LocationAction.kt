package com.screenassistant.service.system.action

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationAction @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val geocoder: Geocoder by lazy {
        Geocoder(context, Locale.getDefault())
    }

    suspend fun getCurrentLocation(): String {
        if (!hasLocationPermission()) {
            return "Error: No tengo permiso para acceder a la ubicación."
        }

        return try {
            val location = getLastKnownOrFreshLocation()
            if (location == null) {
                return "Error: No se pudo obtener la ubicación actual."
            }
            formatLocation(location)
        } catch (e: SecurityException) {
            "Error: No tengo permiso para acceder a la ubicación."
        } catch (e: Exception) {
            "Error: No se pudo obtener la ubicación."
        }
    }

    private suspend fun getLastKnownOrFreshLocation(): Location? {
        // Intentar ubicación conocida primero
        val lastKnown = getLastKnownLocation()
        if (lastKnown != null && isFreshEnough(lastKnown)) {
            return lastKnown
        }
        // Si no hay conocida o es vieja, pedir una nueva
        return requestFreshLocation()
    }

    private suspend fun getLastKnownLocation(): Location? {
        return try {
            suspendCancellableCoroutine { cont ->
                fusedClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (cont.isActive) cont.resume(location)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun requestFreshLocation(): Location? {
        return suspendCancellableCoroutine { cont ->
            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                5000L // 5 segundos mínimo
            ).apply {
                setMaxUpdates(1) // Solo una ubicación
            }.build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    fusedClient.removeLocationUpdates(this)
                    if (cont.isActive) {
                        cont.resume(result.lastLocation)
                    }
                }
            }

            try {
                fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            } catch (e: SecurityException) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    private fun isFreshEnough(location: Location): Boolean {
        val ageMs = System.currentTimeMillis() - location.time
        return ageMs < MAX_LOCATION_AGE_MS
    }

    // D3: Geocoder.getFromLocation(lat, lon, max) deprecado en API 33+ en
    // favor de la variante async con listener. Se mantiene la llamada sync
    // con @Suppress, SIN migrar a GeocoderCompat de androidx.core:
    // 1) GeocoderCompat vive en el artefacto separado core-location-altitude,
    //    NO declarado en service:system (solo core-ktx) — migrar exigiría
    //    añadir una dependencia nueva (prohibido por la tarea);
    // 2) la variante async cambiaría el comportamiento observable (threading
    //    y timing del geocoding). El sync sigue funcionando y el fail-soft
    //    (try/catch → "") queda intacto.
    @Suppress("DEPRECATION")
    private fun formatLocation(location: Location): String {
        val lat = String.format(Locale.US, "%.6f", location.latitude)
        val lon = String.format(Locale.US, "%.6f", location.longitude)
        val alt = if (location.hasAltitude()) {
            " Altitud: ${String.format(Locale.US, "%.0f", location.altitude)}m."
        } else ""
        val acc = if (location.hasAccuracy()) {
            " Precisión: ${String.format(Locale.US, "%.0f", location.accuracy)}m."
        } else ""

        val address = try {
            val addresses = geocoder.getFromLocation(
                location.latitude, location.longitude, 1
            )
            addresses?.firstOrNull()?.let { addr ->
                val parts = mutableListOf<String>()
                addr.thoroughfare?.let { parts.add(it) }
                addr.locality?.let { parts.add(it) }
                addr.countryName?.let { parts.add(it) }
                if (parts.isNotEmpty()) " (${parts.joinToString(", ")})." else ""
            } ?: ""
        } catch (e: Exception) {
            ""
        }

        return "Ubicación: $lat, $lon.$alt$acc Dirección:$address"
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val MAX_LOCATION_AGE_MS = 30_000L // 30 segundos
    }
}
