package com.screenassistant.service.system.action

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiToggleAction @Inject constructor(
    @ApplicationContext private val context: Context
) {

    @Suppress("DEPRECATION")
    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    fun setWifi(enabled: Boolean): String {
        // En Android 10+ (API 29+) no se puede toggle WiFi directamente
        // Abrimos la configuración de WiFi para que el usuario lo haga
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openWifiSettings(enabled)
        } else {
            toggleWifiLegacy(enabled)
        }
    }

    @Suppress("DEPRECATION")
    private fun toggleWifiLegacy(enabled: Boolean): String {
        return try {
            val currentState = isWifiEnabled()
            if (currentState == enabled) {
                return if (enabled) "El WiFi ya está encendido." else "El WiFi ya está apagado."
            }

            @Suppress("DEPRECATION")
            val success = wifiManager.setWifiEnabled(enabled)
            if (success) {
                if (enabled) "WiFi encendido." else "WiFi apagado."
            } else {
                "Error: No se pudo ${if (enabled) "encender" else "apagar"} el WiFi."
            }
        } catch (e: SecurityException) {
            "Error: No tengo permiso para modificar el WiFi."
        } catch (e: Exception) {
            "Error: No se pudo controlar el WiFi."
        }
    }

    private fun openWifiSettings(enableIntent: Boolean): String {
        return try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val action = if (enableIntent) "encender" else "apagar"
            "Abriendo configuración de WiFi para que puedas ${action}lo manualmente."
        } catch (e: Exception) {
            "Error: No se pudo abrir la configuración de WiFi."
        }
    }

    fun getWifiState(): String {
        return if (isWifiEnabled()) {
            val info = getWifiNetworkInfo()
            "WiFi encendido.$info"
        } else {
            "WiFi apagado."
        }
    }

    private fun isWifiEnabled(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun getWifiNetworkInfo(): String {
        return try {
            @Suppress("DEPRECATION")
            val info = wifiManager.connectionInfo
            val ssid = info.ssid?.removeSurrounding("\"") ?: "Desconocido"
            val rssi = info.rssi
            val signalLevel = signalLevelFor(rssi)
            val signalText = when (signalLevel) {
                4 -> "excelente"
                3 -> "buena"
                2 -> "regular"
                1 -> "débil"
                else -> "muy débil"
            }
            " Conectado a: $ssid. Señal $signalText."
        } catch (e: Exception) {
            ""
        }
    }

    // D3: WifiManager.calculateSignalLevel(rssi, numLevels) estático deprecado
    // → método de instancia en API 30+. En API 30+ se usa la instancia (misma
    // escala 0..4 del dispositivo que el estático con numLevels=5; el `when`
    // de getWifiNetworkInfo ya trata cualquier fuera de rango como
    // "muy débil"). En API < 30 se conserva el estático con @Suppress: el
    // método de instancia NO EXISTE ahí y llamarlo rompería en runtime con
    // NoSuchMethodError (minSdk 26). Sin cambio de comportamiento observable.
    private fun signalLevelFor(rssi: Int): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wifiManager.calculateSignalLevel(rssi)
        } else {
            staticSignalLevel(rssi)
        }
    }

    @Suppress("DEPRECATION")
    private fun staticSignalLevel(rssi: Int): Int =
        WifiManager.calculateSignalLevel(rssi, 5)
}
