package com.screenassistant.core.data.repository

import com.screenassistant.core.domain.model.ImageData
import com.screenassistant.core.domain.repository.ScreenCaptureProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * B3 (Lote 8): el repositorio delega la captura en el provider inyectado
 * (core:data no conoce el servicio de accesibilidad) y falla suave con null.
 */
class ScreenContextRepositoryImplTest {

    @Test
    fun `captureScreenshot delega en el provider y propaga el resultado`() = runTest {
        val provider = mockk<ScreenCaptureProvider>()
        val imagen = ImageData(byteArrayOf(1, 2, 3), 10, 20)
        coEvery { provider.captureScreenshot() } returns imagen

        val repo = ScreenContextRepositoryImpl(provider)

        assertSame(imagen, repo.captureScreenshot())
        coVerify(exactly = 1) { provider.captureScreenshot() }
    }

    @Test
    fun `captureScreenshot con provider null devuelve null fail-soft`() = runTest {
        val provider = mockk<ScreenCaptureProvider>()
        coEvery { provider.captureScreenshot() } returns null

        val repo = ScreenContextRepositoryImpl(provider)

        assertNull(repo.captureScreenshot())
        coVerify(exactly = 1) { provider.captureScreenshot() }
    }
}
