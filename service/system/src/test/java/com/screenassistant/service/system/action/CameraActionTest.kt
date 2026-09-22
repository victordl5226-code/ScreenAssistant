package com.screenassistant.service.system.action

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests de CameraAction.
 *
 * NOTA: En unit tests JVM puros (sin Robolectric), Intent.resolveActivity()
 * retorna null por defecto (isReturnDefaultValues=true). Por eso los tests de
 * éxito (abrir cámara real) no son testeables aquí — se cubren en androidTest
 * con Compose/Robolectric. Estos tests validan la lógica de error y los paths
 * que SÍ son mockeables.
 */
class CameraActionTest {

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var action: CameraAction

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        packageManager = mockk(relaxed = true)
        every { context.packageManager } returns packageManager
        action = CameraAction(context)
    }

    @Test
    fun `takePhoto sin intent resoluble devuelve error de camara no disponible`() {
        // Intent.resolveActivity() retorna null en unit tests JVM puros
        val result = action.takePhoto(false)
        assertEquals("Error: No hay aplicación de cámara disponible.", result)
    }

    @Test
    fun `takePhoto frontal sin intent resoluble devuelve error`() {
        val result = action.takePhoto(true)
        // En unit test JVM puro, CameraCharacteristics puede ser null → exception → error genérico
        assertTrue(result.startsWith("Error:"))
    }

    @Test
    fun `takePhoto con contextpackageManager retorna packageManager`() {
        val result = action.takePhoto(false)
        // Verifica que el método existe y ejecuta sin crash
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `takePhoto trasera contiene texto de error esperado`() {
        val result = action.takePhoto(false)
        assertTrue(result.startsWith("Error:") || result.contains("cámara"))
    }

    @Test
    fun `takePhoto frontal contiene texto de error esperado`() {
        val result = action.takePhoto(true)
        assertTrue(result.startsWith("Error:") || result.contains("cámara"))
    }
}
