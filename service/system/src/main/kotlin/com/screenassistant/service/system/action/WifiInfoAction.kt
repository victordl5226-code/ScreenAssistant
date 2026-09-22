package com.screenassistant.service.system.action

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiInfoAction @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Suppress("DEPRECATION")
    fun getWifiInfo(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager

        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (!isWifi) {
            return "No estás conectado a una red WiFi."
        }

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
            as WifiManager
        val wifiInfo = wifiManager.connectionInfo

        val ssid = wifiInfo.ssid?.removeSurrounding("\"") ?: "Desconocido"
        val rssi = wifiInfo.rssi
        val linkSpeed = wifiInfo.linkSpeed
        val signalLevel = WifiManager.calculateSignalLevel(rssi, 5)

        val signalDescription = when (signalLevel) {
            4 -> "excelente"
            3 -> "buena"
            2 -> "regular"
            1 -> "débil"
            else -> "muy débil"
        }

        return "Red WiFi: $ssid. Señal $signalDescription ($signalLevel de 4). Velocidad de enlace: $linkSpeed Mbps."
    }
}
