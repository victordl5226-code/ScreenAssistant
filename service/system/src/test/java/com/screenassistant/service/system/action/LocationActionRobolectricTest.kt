package com.screenassistant.service.system.action

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocationActionRobolectricTest {

    private lateinit var context: Context
    private lateinit var action: LocationAction

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockkStatic(ContextCompat::class)
        mockkStatic(LocationServices::class)
        action = LocationAction(context)
    }

    @After
    fun tearDown() {
        unmockkStatic(ContextCompat::class)
        unmockkStatic(LocationServices::class)
    }

    @Test
    fun `getCurrentLocation sin permiso FINE ni COARSE devuelve error`() = runTest {
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_DENIED
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        } returns PackageManager.PERMISSION_DENIED

        val result = action.getCurrentLocation()
        assertTrue(result.contains("No tengo permiso para acceder a la ubicación"))
    }

    @Test
    fun `getCurrentLocation con permiso FINE retorna ubicacion mockeada`() = runTest {
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED

        val fusedClient = mockk<FusedLocationProviderClient>(relaxed = true)
        every { LocationServices.getFusedLocationProviderClient(context) } returns fusedClient

        val testLocation = Location("test").apply {
            latitude = 40.4168
            longitude = -3.7038
            time = System.currentTimeMillis()
        }

        val lastLocationTask = mockk<Task<Location?>>()
        every { fusedClient.lastLocation } returns lastLocationTask
        every { lastLocationTask.addOnSuccessListener(any()) } answers {
            @Suppress("UNCHECKED_CAST")
            val listener = firstArg<OnSuccessListener<Location?>>()
            listener.onSuccess(testLocation)
            lastLocationTask
        }
        every { lastLocationTask.addOnFailureListener(any()) } returns lastLocationTask

        val result = action.getCurrentLocation()

        assertTrue(result.startsWith("Ubicación:"))
        assertTrue(result.contains("40.416800"))
        assertTrue(result.contains("-3.703800"))
    }

    @Test
    fun `getCurrentLocation con permiso COARSE retorna ubicacion mockeada`() = runTest {
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_DENIED
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED

        val fusedClient = mockk<FusedLocationProviderClient>(relaxed = true)
        every { LocationServices.getFusedLocationProviderClient(context) } returns fusedClient

        val testLocation = Location("test").apply {
            latitude = -33.8688
            longitude = 151.2093
            time = System.currentTimeMillis()
        }

        val lastLocationTask = mockk<Task<Location?>>()
        every { fusedClient.lastLocation } returns lastLocationTask
        every { lastLocationTask.addOnSuccessListener(any()) } answers {
            @Suppress("UNCHECKED_CAST")
            val listener = firstArg<OnSuccessListener<Location?>>()
            listener.onSuccess(testLocation)
            lastLocationTask
        }
        every { lastLocationTask.addOnFailureListener(any()) } returns lastLocationTask

        val result = action.getCurrentLocation()

        assertTrue(result.startsWith("Ubicación:"))
        assertTrue(result.contains("-33.868800"))
        assertTrue(result.contains("151.209300"))
    }
}
