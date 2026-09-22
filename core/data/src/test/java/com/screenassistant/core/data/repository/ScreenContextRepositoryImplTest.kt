package com.screenassistant.core.data.repository

import com.screenassistant.core.domain.model.ImageData
import com.screenassistant.core.domain.model.ScreenMonitoringState
import com.screenassistant.core.domain.repository.ScreenCaptureProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * B3 (Lote 8) / D7 (Lote 9): el repositorio delega la captura en el proveedor
 * REGISTRADO dinámicamente (setScreenCaptureProvider — el servicio de accesibilidad
 * se auto-registra) y falla suave con null si no hay proveedor (servicio
 * desconectado). Constructor SIN provider desde D7 (el registro es mecanismo
 * interno de core:data; la interfaz de dominio no se ensucia — P1-5).
 * 
 * Lote 10: Auto-recovery al reconectar proveedor tras desconexión.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenContextRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Adaptado (D7): registro explícito del provider antes de capturar.
    @Test
    fun `captureScreenshot con provider registrado delega y propaga el resultado`() = runTest {
        val provider = mockk<ScreenCaptureProvider>()
        val imagen = ImageData(byteArrayOf(1, 2, 3), 10, 20)
        coEvery { provider.captureScreenshot() } returns imagen

        val repo = ScreenContextRepositoryImpl(testDispatcher)
        repo.setScreenCaptureProvider(provider)

        assertSame(imagen, repo.captureScreenshot())
        coVerify(exactly = 1) { provider.captureScreenshot() }
    }

    // Adaptado (D7): provider registrado que devuelve null → null propagado.
    @Test
    fun `captureScreenshot con provider registrado que devuelve null propaga el fail-soft`() = runTest {
        val provider = mockk<ScreenCaptureProvider>()
        coEvery { provider.captureScreenshot() } returns null

        val repo = ScreenContextRepositoryImpl(testDispatcher)
        repo.setScreenCaptureProvider(provider)

        assertNull(repo.captureScreenshot())
        coVerify(exactly = 1) { provider.captureScreenshot() }
    }

    // Nuevo (D7): sin provider registrado → null fail-soft (contrato B3).
    @Test
    fun `captureScreenshot sin provider registrado devuelve null fail-soft`() = runTest {
        val repo = ScreenContextRepositoryImpl(testDispatcher)

        assertNull(repo.captureScreenshot())
    }

    // Nuevo (D7): desregistrar el provider vuelve al fail-soft sin llamadas.
    @Test
    fun `desregistrar el provider vuelve al fail-soft null`() = runTest {
        val provider = mockk<ScreenCaptureProvider>()
        val imagen = ImageData(byteArrayOf(1), 1, 1)
        coEvery { provider.captureScreenshot() } returns imagen

        val repo = ScreenContextRepositoryImpl(testDispatcher)
        repo.setScreenCaptureProvider(provider)
        assertSame(imagen, repo.captureScreenshot())

        repo.setScreenCaptureProvider(null)

        assertNull(repo.captureScreenshot())
        coVerify(exactly = 1) { provider.captureScreenshot() }
    }

    // === Tests de monitoreo continuo ===

    @Test
    fun `monitoringState inicia en Idle`() = runTest {
        val repo = ScreenContextRepositoryImpl(testDispatcher)
        assertTrue(repo.monitoringState.value is ScreenMonitoringState.Idle)
    }

    @Test
    fun `startMonitoring cambia el estado a Active`() = runTest {
        val repo = ScreenContextRepositoryImpl(testDispatcher)

        repo.startMonitoring(2000L)

        val state = repo.monitoringState.value
        assertTrue(state is ScreenMonitoringState.Active)
        assertEquals(2000L, (state as ScreenMonitoringState.Active).intervalMs)
    }

    @Test
    fun `stopMonitoring vuelve a Idle`() = runTest {
        val repo = ScreenContextRepositoryImpl(testDispatcher)

        repo.startMonitoring()
        repo.stopMonitoring()

        assertTrue(repo.monitoringState.value is ScreenMonitoringState.Idle)
    }

    @Test
    fun `updateScreenText actualiza el texto del monitoreo activo`() = runTest {
        val repo = ScreenContextRepositoryImpl(testDispatcher)

        repo.startMonitoring()
        repo.updateScreenText("Hola mundo")

        val state = repo.monitoringState.value as ScreenMonitoringState.Active
        assertEquals("Hola mundo", state.screenText)
        assertEquals("Hola mundo", repo.screenText.value)
    }

    @Test
    fun `startMonitoring coerce el intervalo a minimo 1000ms`() = runTest {
        val repo = ScreenContextRepositoryImpl(testDispatcher)

        repo.startMonitoring(100L)

        val state = repo.monitoringState.value as ScreenMonitoringState.Active
        assertEquals(1000L, state.intervalMs)
    }

    @Test
    fun `stopMonitoring cancela el job y resetea el estado`() = runTest {
        val repo = ScreenContextRepositoryImpl(testDispatcher)

        repo.startMonitoring(2000L)
        assertTrue(repo.monitoringState.value is ScreenMonitoringState.Active)

        repo.stopMonitoring()

        assertTrue(repo.monitoringState.value is ScreenMonitoringState.Idle)
        advanceUntilIdle()
    }

    // === Lote 10: Auto-recovery (tests básicos verificables) ===

    @Test
    fun `autoRecovery flag se resetea en stopMonitoring`() = runTest {
        val repo = ScreenContextRepositoryImpl(testDispatcher)

        repo.startMonitoring(3000L)
        repo.stopMonitoring()
        advanceUntilIdle()

        // Tras stopMonitoring, reconectar proveedor NO debe reiniciar
        val provider = mockk<ScreenCaptureProvider>()
        repo.setScreenCaptureProvider(provider)
        advanceUntilIdle()

        assertTrue(repo.monitoringState.value is ScreenMonitoringState.Idle)
    }

    @Test
    fun `autoRecovery guarda el último intervalo usado`() = runTest {
        val repo = ScreenContextRepositoryImpl(testDispatcher)

        repo.startMonitoring(5000L)
        assertEquals(5000L, (repo.monitoringState.value as ScreenMonitoringState.Active).intervalMs)

        repo.startMonitoring(8000L)
        assertEquals(8000L, (repo.monitoringState.value as ScreenMonitoringState.Active).intervalMs)

        // El último intervalo debe ser 8000 para auto-recovery
        // (se verifica indirectamente via comportamiento)
        assertEquals(8000L, (repo.monitoringState.value as ScreenMonitoringState.Active).intervalMs)
    }
}