package com.screenassistant.service.system.action

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WifiToggleActionTest {

    private lateinit var context: Context
    private lateinit var wifiManager: WifiManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var action: WifiToggleAction

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        wifiManager = mockk(relaxed = true)
        connectivityManager = mockk(relaxed = true)
        every { context.applicationContext } returns context
        every { context.getSystemService(Context.WIFI_SERVICE) } returns wifiManager
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        action = WifiToggleAction(context)
    }

    private fun mockWifiState(isWifiConnected: Boolean) {
        val network = mockk<Network>()
        val caps = mockk<NetworkCapabilities>()
        if (isWifiConnected) {
            every { connectivityManager.activeNetwork } returns network
            every { connectivityManager.getNetworkCapabilities(network) } returns caps
            every { caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        } else {
            every { connectivityManager.activeNetwork } returns null
        }
    }

    // Deuda técnica: WifiManager.setWifiEnabled() deprecado en API 29+ (en Q+ la
    // fuente ya abre Settings; el path legacy solo corre en API < 29). Cambiar
    // la fuente/test al panel moderno cambiaría comportamiento → suppress puntual.
    @Suppress("DEPRECATION")
    @Test
    fun `setWifi true cuando no esta conectado llama setWifiEnabled`() {
        mockWifiState(isWifiConnected = false)
        every { wifiManager.setWifiEnabled(true) } returns true

        val result = action.setWifi(true)

        assertEquals("WiFi encendido.", result)
        verify { wifiManager.setWifiEnabled(true) }
    }

    // Deuda técnica: ver comentario en `setWifi true cuando no esta conectado...`.
    @Suppress("DEPRECATION")
    @Test
    fun `setWifi false cuando esta conectado llama setWifiEnabled false`() {
        mockWifiState(isWifiConnected = true)
        every { wifiManager.setWifiEnabled(false) } returns true

        val result = action.setWifi(false)

        assertEquals("WiFi apagado.", result)
        verify { wifiManager.setWifiEnabled(false) }
    }

    @Test
    fun `setWifi true cuando ya esta encendido`() {
        mockWifiState(isWifiConnected = true)

        val result = action.setWifi(true)

        assertEquals("El WiFi ya está encendido.", result)
    }

    @Test
    fun `setWifi false cuando ya esta apagado`() {
        mockWifiState(isWifiConnected = false)

        val result = action.setWifi(false)

        assertEquals("El WiFi ya está apagado.", result)
    }

    // Deuda técnica: WifiManager.getConnectionInfo() deprecado en API 31+
    // (ver WifiInfoActionTest) → suppress puntual, sin cambio de comportamiento.
    @Suppress("DEPRECATION")
    @Test
    fun `getWifiState cuando conectado muestra info`() {
        mockWifiState(isWifiConnected = true)
        every { wifiManager.connectionInfo } returns mockk {
            every { ssid } returns "\"MiWifi\""
            every { rssi } returns -50
            every { linkSpeed } returns 72
        }

        val result = action.getWifiState()

        assertTrue(result.contains("WiFi encendido"))
    }

    @Test
    fun `getWifiState cuando desconectado`() {
        mockWifiState(isWifiConnected = false)

        val result = action.getWifiState()

        assertEquals("WiFi apagado.", result)
    }
}
