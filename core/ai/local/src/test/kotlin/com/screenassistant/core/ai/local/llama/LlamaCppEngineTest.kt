package com.screenassistant.core.ai.local.llama

import android.util.Log
import com.screenassistant.core.ai.local.llama.bridge.LlamaCppBridgeInterface
import com.screenassistant.core.ai.local.llama.config.LlamaCppConfig
import com.screenassistant.core.domain.repository.ai.AiProvider
import com.screenassistant.core.domain.repository.ai.AiResponse
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para LlamaCppEngine.
 *
 * Usa MockK para mockear la interfaz LlamaCppBridgeInterface
 * y probar la lógica del motor sin dependencia del .so nativo.
 */
class LlamaCppEngineTest {

    private lateinit var bridge: LlamaCppBridgeInterface
    private lateinit var config: LlamaCppConfig
    private lateinit var engine: LlamaCppEngine

    @Before
    fun setUp() {
        // Mockear android.util.Log para que no crashee en JVM tests
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0

        bridge = mockk(relaxed = true)
        config = LlamaCppConfig()
        engine = LlamaCppEngine(bridge, config)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `initialize retorna true cuando el bridge funciona`() = runTest {
        every { bridge.nativeInit(any()) } returns 1L
        every { bridge.nativeLoadModel(any(), any(), any()) } returns 1L
        every { bridge.nativeIsReady(any()) } returns true

        val result = engine.initialize("/path/to/model.gguf")

        assertTrue(result)
        assertTrue(engine.isReady())
        verify { bridge.nativeInit(any()) }
        verify { bridge.nativeLoadModel(1L, "/path/to/model.gguf", any()) }
    }

    @Test
    fun `initialize retorna false cuando contextHandle es 0`() = runTest {
        every { bridge.nativeInit(any()) } returns 0L

        val result = engine.initialize("/path/to/model.gguf")

        assertFalse(result)
        assertFalse(engine.isReady())
    }

    @Test
    fun `initialize retorna false cuando modelHandle es 0`() = runTest {
        every { bridge.nativeInit(any()) } returns 1L
        every { bridge.nativeLoadModel(any(), any(), any()) } returns 0L

        val result = engine.initialize("/path/to/model.gguf")

        assertFalse(result)
        verify { bridge.nativeDestroy(1L) }
    }

    @Test
    fun `inference retorna error cuando no esta listo`() = runTest {
        val result = engine.inference("test prompt")

        assertTrue(result is AiResponse.Error)
        assertEquals(AiProvider.LOCAL, (result as AiResponse.Error).provider)
        assertTrue(result.recoverable)
    }

    @Test
    fun `inference retorna exito cuando esta listo`() = runTest {
        // Inicializar el motor
        every { bridge.nativeInit(any()) } returns 1L
        every { bridge.nativeLoadModel(any(), any(), any()) } returns 1L
        every { bridge.nativeIsReady(any()) } returns true
        engine.initialize("/path/to/model.gguf")

        // Mock inference
        every { bridge.nativeGenerate(any(), any(), any(), any(), any(), any(), any()) } returns "Respuesta"

        val result = engine.inference("test prompt")

        assertTrue(result is AiResponse.Success)
        assertEquals("Respuesta", (result as AiResponse.Success).text)
        assertEquals(AiProvider.LOCAL, result.provider)
        assertTrue(result.latencyMs >= 0)
    }

    @Test
    fun `inference retorna error recoverable en OOM`() = runTest {
        every { bridge.nativeInit(any()) } returns 1L
        every { bridge.nativeLoadModel(any(), any(), any()) } returns 1L
        every { bridge.nativeIsReady(any()) } returns true
        engine.initialize("/path/to/model.gguf")

        every { bridge.nativeGenerate(any(), any(), any(), any(), any(), any(), any()) } throws OutOfMemoryError("test OOM")

        val result = engine.inference("test prompt")

        assertTrue(result is AiResponse.Error)
        assertTrue((result as AiResponse.Error).recoverable)
        assertFalse(engine.isReady())
    }

    @Test
    fun `release libera recursos`() = runTest {
        every { bridge.nativeInit(any()) } returns 1L
        every { bridge.nativeLoadModel(any(), any(), any()) } returns 1L
        every { bridge.nativeIsReady(any()) } returns true
        engine.initialize("/path/to/model.gguf")

        engine.release()

        assertFalse(engine.isReady())
        verify { bridge.nativeFreeModel(1L, 1L) }
        verify { bridge.nativeDestroy(1L) }
    }

    @Test
    fun `streamInference retorna flow`() = runTest {
        every { bridge.nativeInit(any()) } returns 1L
        every { bridge.nativeLoadModel(any(), any(), any()) } returns 1L
        every { bridge.nativeIsReady(any()) } returns true
        engine.initialize("/path/to/model.gguf")

        val flow = engine.streamInference("test prompt")
        assertNotNull(flow)
    }
}
