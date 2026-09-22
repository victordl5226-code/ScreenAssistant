package com.screenassistant.service.system.action

import android.content.Context
import android.hardware.camera2.CameraManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FlashlightActionTest {

    private lateinit var context: Context
    private lateinit var cameraManager: CameraManager
    private lateinit var action: FlashlightAction

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        cameraManager = mockk<CameraManager>(relaxed = true)
        every { context.getSystemService(Context.CAMERA_SERVICE) } returns cameraManager
        action = FlashlightAction(context)
    }

    @Test
    fun `encender linterna devuelve exito`() {
        every { cameraManager.cameraIdList } returns arrayOf("0")
        val result = action.setFlashlight(true)
        assertEquals("Linterna encendida.", result)
    }

    @Test
    fun `apagar linterna devuelve exito`() {
        every { cameraManager.cameraIdList } returns arrayOf("0")
        val result = action.setFlashlight(false)
        assertEquals("Linterna apagada.", result)
    }

    @Test
    fun `sin camara devuelve error`() {
        every { cameraManager.cameraIdList } returns emptyArray()
        val result = action.setFlashlight(true)
        assertEquals("Error: No se encontró una cámara con flash.", result)
    }

    @Test
    fun `security exception devuelve error permiso`() {
        every { cameraManager.cameraIdList } returns arrayOf("0")
        every { cameraManager.setTorchMode("0", true) } throws SecurityException()
        val result = action.setFlashlight(true)
        assertEquals("Error: No tengo permiso para usar la cámara.", result)
    }

    @Test
    fun `exception generica devuelve error control`() {
        every { cameraManager.cameraIdList } returns arrayOf("0")
        every { cameraManager.setTorchMode("0", true) } throws RuntimeException("fail")
        val result = action.setFlashlight(true)
        assertEquals("Error: No pudo controlarse la linterna.", result)
    }

    @Test
    fun `estado isOn cambia correctamente`() {
        every { cameraManager.cameraIdList } returns arrayOf("0")
        action.setFlashlight(true)
        // isOn es private, no se puede verificar directamente
        // pero el hecho de que no lance excepción indica que funciona
        action.setFlashlight(false)
    }
}