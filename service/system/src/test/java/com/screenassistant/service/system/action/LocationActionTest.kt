package com.screenassistant.service.system.action

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LocationActionTest {

    private lateinit var context: Context
    private lateinit var action: LocationAction

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        mockkStatic(ContextCompat::class)
        action = LocationAction(context)
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
        assertEquals("Error: No tengo permiso para acceder a la ubicación.", result)
    }

    // NOTA: Tests de ubicación con permiso concedido requieren Robolectric o
    // instrumented tests porque FusedLocationProviderClient se crea internamente
    // y no es mockeable en unit test JVM puro.
}
