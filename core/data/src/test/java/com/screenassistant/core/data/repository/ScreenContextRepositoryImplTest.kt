package com.screenassistant.core.data.repository

import com.screenassistant.core.domain.model.ImageData
import com.screenassistant.core.domain.repository.ScreenCaptureProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * B3 (Lote 8) / D7 (Lote 9): el repositorio delega la captura en el proveedor
 * REGISTRADO dinámicamente (setScreenCaptureProvider — el servicio de accesibilidad
 * se auto-registra) y falla suave con null si no hay proveedor (servicio
 * desconectado). Constructor SIN provider desde D7 (el registro es mecanismo
 * interno de core:data; la interfaz de dominio no se ensucia — P1-5).
 */
class ScreenContextRepositoryImplTest {

    // Adaptado (D7): registro explícito del provider antes de capturar.
    @Test
    fun `captureScreenshot con provider registrado delega y propaga el resultado`() = runTest {
        val provider = mockk<ScreenCaptureProvider>()
        val imagen = ImageData(byteArrayOf(1, 2, 3), 10, 20)
        coEvery { provider.captureScreenshot() } returns imagen

        val repo = ScreenContextRepositoryImpl()
        repo.setScreenCaptureProvider(provider)

        assertSame(imagen, repo.captureScreenshot())
        coVerify(exactly = 1) { provider.captureScreenshot() }
    }

    // Adaptado (D7): provider registrado que devuelve null → null propagado.
    @Test
    fun `captureScreenshot con provider registrado que devuelve null propaga el fail-soft`() = runTest {
        val provider = mockk<ScreenCaptureProvider>()
        coEvery { provider.captureScreenshot() } returns null

        val repo = ScreenContextRepositoryImpl()
        repo.setScreenCaptureProvider(provider)

        assertNull(repo.captureScreenshot())
        coVerify(exactly = 1) { provider.captureScreenshot() }
    }

    // Nuevo (D7): sin provider registrado → null fail-soft (contrato B3).
    @Test
    fun `captureScreenshot sin provider registrado devuelve null fail-soft`() = runTest {
        val repo = ScreenContextRepositoryImpl()

        assertNull(repo.captureScreenshot())
    }

    // Nuevo (D7): desregistrar el provider vuelve al fail-soft sin llamadas.
    @Test
    fun `desregistrar el provider vuelve al fail-soft null`() = runTest {
        val provider = mockk<ScreenCaptureProvider>()
        val imagen = ImageData(byteArrayOf(1), 1, 1)
        coEvery { provider.captureScreenshot() } returns imagen

        val repo = ScreenContextRepositoryImpl()
        repo.setScreenCaptureProvider(provider)
        assertSame(imagen, repo.captureScreenshot())

        repo.setScreenCaptureProvider(null)

        assertNull(repo.captureScreenshot())
        coVerify(exactly = 1) { provider.captureScreenshot() }
    }
}
