package com.screenassistant.core.data.repository

import com.screenassistant.core.domain.model.proactive.ConnectivityInfo
import com.screenassistant.core.domain.repository.ai.AiProvider
import com.screenassistant.core.domain.repository.ai.AiResponse
import com.screenassistant.core.domain.repository.ai.ConnectivityMonitor
import com.screenassistant.core.domain.repository.ai.LocalInferenceEngine
import io.mockk.coEvery
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiOrchestratorImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var localEngine: LocalInferenceEngine
    private lateinit var geminiRepository: com.screenassistant.core.domain.repository.GeminiRepository
    private lateinit var connectivityMonitor: ConnectivityMonitor
    private lateinit var orchestrator: AiOrchestratorImpl

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        localEngine = mockk()
        geminiRepository = mockk()
        connectivityMonitor = mockk()
        orchestrator = AiOrchestratorImpl(localEngine, geminiRepository, connectivityMonitor)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun setConnected(connected: Boolean) {
        coEvery { connectivityMonitor.isConnected() } returns connected
        every { connectivityMonitor.connectivityInfo } returns kotlinx.coroutines.flow.MutableStateFlow(
            ConnectivityInfo.from(wifi = connected, mobile = false)
        )
    }

    private fun setLocalReady(ready: Boolean) {
        coEvery { localEngine.isReady() } returns ready
    }

    @Test
    fun `resolveProvider returns GEMINI when local not ready even if preferLocal is true`() = runTest {
        // P4-fix (Grupo B): el test anterior esperaba LOCAL incondicional con
        // preferLocal=true, pero el contrato de la fuente (AiOrchestratorImpl,
        // paso 1: "Si LOCAL disponible Y preferLocal → LOCAL") exige que el
        // motor esté listo. Enrutar a un motor no listo pudiendo usar la nube
        // garantizaría un error evitable → lo correcto es GEMINI.
        setConnected(true)
        setLocalReady(false)
        val provider = orchestrator.resolveProvider(preferLocal = true)
        assertEquals(AiProvider.GEMINI, provider)
    }

    @Test
    fun `resolveProvider returns GEMINI when connected`() = runTest {
        setConnected(true)
        setLocalReady(false)
        val provider = orchestrator.resolveProvider(preferLocal = false)
        assertEquals(AiProvider.GEMINI, provider)
    }

    @Test
    fun `isLocalAvailable delegates to engine`() = runTest {
        coEvery { localEngine.isReady() } returns false
        assertEquals(false, orchestrator.isLocalAvailable())
        coEvery { localEngine.isReady() } returns true
        assertEquals(true, orchestrator.isLocalAvailable())
    }

    @Test
    fun `isGeminiAvailable delegates to connectivityMonitor`() = runTest {
        setConnected(true)
        assertEquals(true, orchestrator.isGeminiAvailable())
        setConnected(false)
        assertEquals(false, orchestrator.isGeminiAvailable())
    }

    @Test
    fun `processMessage uses GEMINI when connected and preferLocal false`() = runTest {
        setConnected(true)
        setLocalReady(false)
        coEvery { geminiRepository.sendMessage(any(), any()) } returns "respuesta gemini"
        val response = orchestrator.processMessage("hola", preferLocal = false)
        assertTrue(response is AiResponse.Success)
        assertEquals("respuesta gemini", (response as AiResponse.Success).text)
        assertEquals(AiProvider.GEMINI, response.provider)
    }

    @Test
    fun `processMessage returns error when not connected and local not ready`() = runTest {
        setConnected(false)
        setLocalReady(false)
        // P4-fix (Grupo B): sin internet y sin motor local, resolveProvider
        // retorna LOCAL (rama else de la fuente) → hay que stubear inference
        // (antes sin stub → MockKException). El test anterior asumía GEMINI
        // como "último recurso" sin internet, supuesto irreal: intentar la
        // nube sin conectividad solo produciría un timeout confuso en vez del
        // error preciso del motor local.
        coEvery { localEngine.inference(any(), any(), any()) } returns AiResponse.Error(
            reason = "Motor local no disponible",
            provider = AiProvider.LOCAL,
            recoverable = false,
        )
        val response = orchestrator.processMessage("hola", preferLocal = false)
        assertTrue(response is AiResponse.Error)
        assertEquals(AiProvider.LOCAL, (response as AiResponse.Error).provider)
    }

    @Test
    fun `processMessage falls back to GEMINI when local fails with recoverable error`() = runTest {
        // Simular local listo pero con error recuperable
        coEvery { localEngine.isReady() } returns true
        coEvery { localEngine.inference(any(), any(), any()) } returns AiResponse.Error(
            reason = "local timeout",
            provider = AiProvider.LOCAL,
            recoverable = true,
        )
        setConnected(true)
        coEvery { geminiRepository.sendMessage(any(), any()) } returns "rescatado por gemini"

        val response = orchestrator.processMessage("hola", preferLocal = true)

        assertTrue(response is AiResponse.Success)
        assertEquals("rescatado por gemini", (response as AiResponse.Success).text)
        assertEquals(AiProvider.GEMINI, response.provider)
    }

    @Test
    fun `processMessage uses LOCAL when available and offline`() = runTest {
        // Local listo, sin internet
        coEvery { localEngine.isReady() } returns true
        coEvery { localEngine.inference(any(), any(), any()) } returns AiResponse.Success(
            text = "respuesta local",
            provider = AiProvider.LOCAL,
        )
        setConnected(false)

        val response = orchestrator.processMessage("hola", preferLocal = true)

        assertTrue(response is AiResponse.Success)
        assertEquals("respuesta local", (response as AiResponse.Success).text)
        assertEquals(AiProvider.LOCAL, response.provider)
    }
}
