package com.screenassistant.core.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectivityMonitorImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var monitor: ConnectivityMonitorImpl

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mockk()
        connectivityManager = mockk()
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun setupNetwork(hasWifi: Boolean, hasMobile: Boolean) {
        val activeNetwork = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        every { connectivityManager.activeNetwork } returns activeNetwork
        every { connectivityManager.getNetworkCapabilities(activeNetwork) } returns capabilities
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns hasWifi
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns hasMobile
    }

    @Test
    fun `isConnected returns true when wifi available`() = runTest {
        setupNetwork(hasWifi = true, hasMobile = false)
        monitor = ConnectivityMonitorImpl(context)
        assertTrue(monitor.isConnected())
    }

    @Test
    fun `isConnected returns true when mobile available`() = runTest {
        setupNetwork(hasWifi = false, hasMobile = true)
        monitor = ConnectivityMonitorImpl(context)
        assertTrue(monitor.isConnected())
    }

    @Test
    fun `isConnected returns false when no network`() = runTest {
        every { connectivityManager.activeNetwork } returns null
        monitor = ConnectivityMonitorImpl(context)
        assertFalse(monitor.isConnected())
    }

    @Test
    fun `isConnected returns false when capabilities null`() = runTest {
        val activeNetwork = mockk<Network>()
        every { connectivityManager.activeNetwork } returns activeNetwork
        every { connectivityManager.getNetworkCapabilities(activeNetwork) } returns null
        monitor = ConnectivityMonitorImpl(context)
        assertFalse(monitor.isConnected())
    }

    @Test
    fun `isConnected returns false when ConnectivityManager null`() = runTest {
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns null
        monitor = ConnectivityMonitorImpl(context)
        assertFalse(monitor.isConnected())
    }
}
