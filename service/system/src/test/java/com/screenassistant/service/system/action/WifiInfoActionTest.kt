package com.screenassistant.service.system.action

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WifiInfoActionTest {

    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var wifiManager: WifiManager
    private lateinit var networkCapabilities: NetworkCapabilities
    private lateinit var wifiInfo: WifiInfo
    private lateinit var action: WifiInfoAction

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        wifiManager = mockk<WifiManager>(relaxed = true)
        networkCapabilities = mockk<NetworkCapabilities>(relaxed = true)
        wifiInfo = mockk<WifiInfo>(relaxed = true)

        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { context.applicationContext.getSystemService(Context.WIFI_SERVICE) } returns wifiManager
        action = WifiInfoAction(context)
    }

    @Test
    fun `sin red wifi devuelve mensaje`() {
        every { connectivityManager.activeNetwork } returns null
        val result = action.getWifiInfo()
        assertEquals("No estás conectado a una red WiFi.", result)
    }

    @Test
    fun `red no es wifi devuelve mensaje`() {
        val network = mockk<android.net.Network>(relaxed = true)
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        val result = action.getWifiInfo()
        assertEquals("No estás conectado a una red WiFi.", result)
    }

    // Deuda técnica: WifiManager.getConnectionInfo() deprecado en API 31+ (usar
    // NetworkCapabilities.transportInfo). Migrarlo exige cambiar WifiInfoAction
    // con riesgo de runtime → se mantiene el mock y se suprime puntual.
    @Suppress("DEPRECATION")
    @Test
    fun `wifi conectado devuelve string no nulo`() {
        val network = mockk<android.net.Network>(relaxed = true)
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { wifiManager.connectionInfo } returns wifiInfo
        every { wifiInfo.ssid } returns "\"MiRedWiFi\""
        every { wifiInfo.rssi } returns -45
        every { wifiInfo.linkSpeed } returns 54

        val result = action.getWifiInfo()
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
    }

    // Deuda técnica: ver comentario en `wifi conectado devuelve string no nulo`.
    @Suppress("DEPRECATION")
    @Test
    fun `senal debil devuelve string no nulo`() {
        val network = mockk<android.net.Network>(relaxed = true)
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { wifiManager.connectionInfo } returns wifiInfo
        every { wifiInfo.ssid } returns "\"WeakWiFi\""
        every { wifiInfo.rssi } returns -90
        every { wifiInfo.linkSpeed } returns 1

        val result = action.getWifiInfo()
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
    }

    // Deuda técnica: ver comentario en `wifi conectado devuelve string no nulo`.
    @Suppress("DEPRECATION")
    @Test
    fun `ssid sin comillas devuelve string no nulo`() {
        val network = mockk<android.net.Network>(relaxed = true)
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { wifiManager.connectionInfo } returns wifiInfo
        every { wifiInfo.ssid } returns "SinComillas"
        every { wifiInfo.rssi } returns -50
        every { wifiInfo.linkSpeed } returns 72

        val result = action.getWifiInfo()
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
    }
}