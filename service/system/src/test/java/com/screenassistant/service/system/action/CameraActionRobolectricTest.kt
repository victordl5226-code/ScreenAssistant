package com.screenassistant.service.system.action

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Tests de CameraAction con Robolectric.
 *
 * En unit tests JVM puros, Intent.resolveActivity() retorna null porque no hay
 * sistema real. Robolectric provee un PackageManager real que SÍ resuelve intents,
 * permitiendo testear los paths de éxito (cámara disponible) y el path de error
 * (sin cámara) con comportamiento auténtico.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CameraActionRobolectricTest {

    private lateinit var context: Context
    private lateinit var action: CameraAction

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        action = CameraAction(context)
    }

    @Test
    fun `takePhoto trasera con camara disponible devuelve mensaje de apertura`() {
        registerFakeCameraActivity()

        val result = action.takePhoto(false)

        assertEquals("Abriendo cámara trasera para tomar una foto.", result)
    }

    @Test
    fun `takePhoto frontal con camara disponible devuelve mensaje de apertura`() {
        registerFakeCameraActivity()

        val result = action.takePhoto(true)

        assertEquals("Abriendo cámara frontal para tomar una foto.", result)
    }

    @Test
    fun `takePhoto sin camara disponible devuelve error`() {
        // No register any activity — resolveActivity() returns null
        val result = action.takePhoto(false)
        assertEquals("Error: No hay aplicación de cámara disponible.", result)
    }

    @Test
    fun `takePhoto sin camara con frontal tambien devuelve error`() {
        val result = action.takePhoto(true)
        assertEquals("Error: No hay aplicación de cámara disponible.", result)
    }

    @Test
    fun `takePhoto trasera con camara disponible no lanza excepcion`() {
        registerFakeCameraActivity()

        // Should complete without throwing
        val result = action.takePhoto(false)
        assertTrue(result.contains("cámara"))
    }

    private fun registerFakeCameraActivity() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = context.packageName
                name = FakeCameraActivity::class.java.name
                applicationInfo = context.applicationInfo
            }
        }
        val pm = context.packageManager
        val shadowPm = Shadows.shadowOf(pm)
        // API moderna (Robolectric 4.10+): el overload con un solo ResolveInfo
        // está deprecado → se pasa la lista.
        shadowPm.addResolveInfoForIntent(intent, listOf(resolveInfo))
    }

    private class FakeCameraActivity : android.app.Activity()
}
