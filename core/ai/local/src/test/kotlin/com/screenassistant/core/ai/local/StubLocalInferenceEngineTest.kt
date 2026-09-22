package com.screenassistant.core.ai.local

import com.screenassistant.core.domain.repository.ai.AiProvider
import com.screenassistant.core.domain.repository.ai.AiResponse
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StubLocalInferenceEngineTest {

    private lateinit var engine: StubLocalInferenceEngine

    @Before
    fun setup() {
        engine = StubLocalInferenceEngine()
    }

    @Test
    fun `initialize returns false`() = runTest {
        val result = engine.initialize("/path/to/model")
        assertFalse(result)
    }

    @Test
    fun `isReady returns false before initialize`() {
        assertFalse(engine.isReady())
    }

    @Test
    fun `isReady returns false after initialize`() = runTest {
        engine.initialize("/path/to/model")
        assertFalse(engine.isReady())
    }

    @Test
    fun `inference returns AiResponse Error with correct provider and reason`() = runTest {
        val response = engine.inference("test prompt", maxTokens = 100, temperature = 0.5f)
        assertTrue(response is AiResponse.Error)
        val error = response as AiResponse.Error
        assertEquals(AiProvider.LOCAL, error.provider)
        assertEquals("Motor local no disponible: MLC LLM no integrado", error.reason)
        assertFalse(error.recoverable)
    }

    @Test
    fun `streamInference emits one error string then completes`() = runTest {
        val emissions = engine.streamInference("test prompt", maxTokens = 100, temperature = 0.5f).toList()
        assertEquals(1, emissions.size)
        assertEquals("Error: Motor local no disponible", emissions[0])
    }

    @Test
    fun `release does not change ready state`() = runTest {
        assertFalse(engine.isReady())
        engine.release()
        assertFalse(engine.isReady())
    }

    @Test
    fun `release after initialize keeps ready false`() = runTest {
        engine.initialize("/path/to/model")
        assertFalse(engine.isReady())
        engine.release()
        assertFalse(engine.isReady())
    }

    @Test
    fun `inference works regardless of initialize history`() = runTest {
        // Without initialize
        val response1 = engine.inference("prompt1")
        assertTrue(response1 is AiResponse.Error)

        // After initialize
        engine.initialize("/path/to/model")
        val response2 = engine.inference("prompt2")
        assertTrue(response2 is AiResponse.Error)
    }

    @Test
    fun `inference error provider is always LOCAL`() = runTest {
        val response = engine.inference("any prompt")
        val error = response as AiResponse.Error
        assertEquals(AiProvider.LOCAL, error.provider)
    }

    @Test
    fun `inference error recoverable is always false`() = runTest {
        val response = engine.inference("any prompt")
        val error = response as AiResponse.Error
        assertFalse(error.recoverable)
    }

    @Test
    fun `streamInference single emission`() = runTest {
        val emissions = engine.streamInference("prompt").toList()
        assertEquals(1, emissions.size)
    }
}
