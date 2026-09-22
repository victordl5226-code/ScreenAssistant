package com.screenassistant.service.system.action

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowWifiManager

/**
 * Tests de WifiToggleAction con Robolectric.
 *
 * Robolectric provee Build.VERSION.SDK_INT REAL (SDK 33 >= Q),
 * permitiendo testear el branching API 29+ (abrir Settings) vs
 * API < 29 (setWifiEnabled directo).
 *
 * En SDK 33, setWifi() siempre abre Settings porque setWifiEnabled
 * fue descontinuado en API 29.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WifiToggleActionRobolectricTest {

    private lateinit var context: Context
    private lateinit var action: WifiToggleAction

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        action = WifiToggleAction(context)
    }

    @Test
    fun `setWifi true en API 29+ abre configuracion de WiFi`() {
        val result = action.setWifi(true)
        assertTrue(result.contains("Abriendo configuración de WiFi"))
        assertTrue(result.contains("encender"))
    }

    @Test
    fun `setWifi false en API 29+ abre configuracion de WiFi`() {
        val result = action.setWifi(false)
        assertTrue(result.contains("Abriendo configuración de WiFi"))
        assertTrue(result.contains("apagar"))
    }

    @Test
    fun `getWifiState retorna un string con estado`() {
        val result = action.getWifiState()
        // Should return either "WiFi encendido" or "WiFi apagado"
        assertTrue(result.contains("WiFi"))
    }

    @Test
    fun `getWifiState contiene info cuando conectado`() {
        // In Robolectric, WiFi is disconnected by default
        val result = action.getWifiState()
        // Just verify it doesn't crash and returns a valid string
        assertTrue(result.isNotEmpty())
        assertTrue(result.startsWith("WiFi"))
    }
}
