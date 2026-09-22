package com.screenassistant.core.data.repository

import com.screenassistant.core.ai.local.StubLocalInferenceEngine
import com.screenassistant.core.domain.model.proactive.ConnectivityInfo
import com.screenassistant.core.domain.repository.GeminiRepository
import com.screenassistant.core.domain.repository.ai.AiProvider
import com.screenassistant.core.domain.repository.ai.AiResponse
import com.screenassistant.core.domain.repository.ai.ConnectivityMonitor
import com.screenassistant.core.domain.repository.ai.LocalInferenceEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests de integración de AiOrchestratorImpl con ConnectivityMonitor real + Gemini mock.
 *
 * Verifica:
 * - Estrategia offline-first: LOCAL cuando no hay internet
 * - Fallback: LOCAL → GEMINI cuando local falla
 * - Gemini directo: cuando hay internet y local no está listo
 * - Ambos caen: error FallbackFailed
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiOrchestratorIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var localEngine: StubLocalInferenceEngine
    private lateinit var geminiRepository: GeminiRepository
    private lateinit var connectivityMonitor: ConnectivityMonitor
    private lateinit var orchestrator: AiOrchestratorImpl
    private val connectivityFlow = MutableStateFlow(ConnectivityInfo.from(wifi = false, mobile = false))

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        localEngine = StubLocalInferenceEngine()
        geminiRepository = mockk()
        connectivityMonitor = mockk()
        every { connectivityMonitor.connectivityInfo } returns connectivityFlow

        orchestrator = AiOrchestratorImpl(localEngine, geminiRepository, connectivityMonitor)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Estrategia: GEMINI cuando hay internet ───────────────────

    @Test
    fun `con internet usa GEMINI directamente`() = runTest {
        coEvery { connectivityMonitor.isConnected() } returns true
        coEvery { geminiRepository.sendMessage(any(), any()) } returns "respuesta gemini"

        val response = orchestrator.processMessage("hola", preferLocal = false)

        assertTrue(response is AiResponse.Success)
        assertEquals("respuesta gemini", (response as AiResponse.Success).text)
        assertEquals(AiProvider.GEMINI, response.provider)
    }

    @Test
    fun `con internet y preferLocal false usa GEMINI`() = runTest {
        coEvery { connectivityMonitor.isConnected() } returns true
        coEvery { geminiRepository.sendMessage(any(), any()) } returns "ok"

        val response = orchestrator.processMessage("hola", preferLocal = false)

        assertTrue(response is AiResponse.Success)
        coVerify(exactly = 1) { geminiRepository.sendMessage(any(), any()) }
    }

    // ── Estrategia: LOCAL cuando no hay internet ─────────────────

    @Test
    fun `sin internet y local no listo retorna error local sin intentar GEMINI`() = runTest {
        // P4-fix (Grupo B): el test anterior esperaba Success("rescatado") vía
        // GEMINI sin internet — físicamente imposible en producción (solo
        // pasaba con el mock). La fuente retorna LOCAL (rama else) y el stub
        // real informa "no disponible" con recoverable=false → error preciso
        // y sin intento de red inútil. Se verifica que GEMINI ni se llama.
        coEvery { connectivityMonitor.isConnected() } returns false

        val response = orchestrator.processMessage("hola", preferLocal = false)

        assertTrue(response is AiResponse.Error)
        assertEquals(AiProvider.LOCAL, (response as AiResponse.Error).provider)
        coVerify(exactly = 0) { geminiRepository.sendMessage(any(), any()) }
    }

    // ── Fallback: LOCAL falla → GEMINI ───────────────────────────

    @Test
    fun `fallback a GEMINI cuando local falla`() = runTest {
        coEvery { connectivityMonitor.isConnected() } returns true
        coEvery { geminiRepository.sendMessage(any(), any()) } returns "rescatado por gemini"

        // Crear un mock local engine que falle con error recuperable
        val mockLocalEngine = mockk<LocalInferenceEngine>()
        coEvery { mockLocalEngine.isReady() } returns true
        coEvery { mockLocalEngine.inference(any(), any(), any()) } returns AiResponse.Error(
            reason = "local timeout",
            provider = AiProvider.LOCAL,
            recoverable = true
        )
        val testOrchestrator = AiOrchestratorImpl(mockLocalEngine, geminiRepository, connectivityMonitor)

        // P4-fix (Grupo B): preferLocal=true para ejercer el fallback REAL
        // (con preferLocal=false la fuente va directo a GEMINI y el engine
        // local ni se consulta → el test pasaba por coincidencia sin cubrir
        // la rama LOCAL → GEMINI).
        val response = testOrchestrator.processMessage("hola", preferLocal = true)

        assertTrue(response is AiResponse.Success)
        assertEquals("rescatado por gemini", (response as AiResponse.Success).text)
    }

    @Test
    fun `sin internet y gemini cae retorna error`() = runTest {
        // Sin internet → resolveProvider retorna GEMINI como último recurso
        coEvery { connectivityMonitor.isConnected() } returns false
        coEvery { geminiRepository.sendMessage(any(), any()) } throws RuntimeException("caído")

        val response = orchestrator.processMessage("hola", preferLocal = false)

        // P4-fix (Grupo B): sin internet y stub local no listo → provider
        // LOCAL → el stub retorna Error no recuperable (el stub de Gemini con
        // `throws` queda como red de seguridad si la estrategia cambiara).
        assertTrue(response is AiResponse.Error)
    }

    // ── Streaming ────────────────────────────────────────────────

    @Test
    fun `streaming con GEMINI`() = runTest {
        coEvery { connectivityMonitor.isConnected() } returns true
        every { geminiRepository.streamMessage(any()) } returns kotlinx.coroutines.flow.flow {
            emit("chunk1")
            emit("chunk2")
        }

        val chunks = mutableListOf<String>()
        orchestrator.streamMessage("hola", preferLocal = false).collect {
            chunks.add(it)
        }

        assertEquals(2, chunks.size)
        assertEquals("chunk1", chunks[0])
        assertEquals("chunk2", chunks[1])
    }

    // ── Resolución de provider ───────────────────────────────────

    @Test
    fun `resolveProvider retorna GEMINI cuando hay internet`() = runTest {
        coEvery { connectivityMonitor.isConnected() } returns true
        assertEquals(AiProvider.GEMINI, orchestrator.resolveProvider(preferLocal = false))
    }

    @Test
    fun `isLocalAvailable retorna false para stub`() = runTest {
        assertEquals(false, orchestrator.isLocalAvailable())
    }

    @Test
    fun `isGeminiAvailable delega a monitor`() = runTest {
        coEvery { connectivityMonitor.isConnected() } returns true
        assertEquals(true, orchestrator.isGeminiAvailable())

        coEvery { connectivityMonitor.isConnected() } returns false
        assertEquals(false, orchestrator.isGeminiAvailable())
    }
}
