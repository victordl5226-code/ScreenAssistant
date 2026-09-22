package com.screenassistant.core.ai.local

import android.content.Context
import android.util.Log
import com.screenassistant.core.domain.model.MlcModelInfo
import com.screenassistant.core.domain.repository.ai.AiProvider
import com.screenassistant.core.domain.repository.ai.AiResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MlcLlmEngineTest {

    private lateinit var context: Context
    private lateinit var engine: MlcLlmEngine

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        context = mockk(relaxed = true)
        engine = MlcLlmEngine(context)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `initialize returns false when MLC not in classpath`() = runTest {
        val result = engine.initialize("/nonexistent/path")
        assertFalse(result)
    }

    @Test
    fun `isReady returns false when MLC not available`() {
        assertFalse(engine.isReady())
    }

    @Test
    fun `isMlcAvailable returns false in test environment`() {
        assertFalse(engine.isMlcAvailable())
    }

    @Test
    fun `inference returns error when MLC not available`() = runTest {
        val response = engine.inference("test prompt", maxTokens = 100, temperature = 0.5f)
        assertTrue(response is AiResponse.Error)
        val error = response as AiResponse.Error
        assertEquals(AiProvider.LOCAL, error.provider)
        assertFalse(error.recoverable)
    }

    @Test
    fun `inference error message mentions Gemini fallback`() = runTest {
        val response = engine.inference("test prompt")
        val error = response as AiResponse.Error
        assertTrue(error.reason.contains("Gemini"))
    }

    @Test
    fun `streamInference emits error when MLC not available`() = runTest {
        val emissions = engine.streamInference("test prompt").toList()
        assertTrue(emissions.isNotEmpty())
        assertTrue(emissions[0].contains("Error"))
    }

    @Test
    fun `release does not throw`() = runTest {
        engine.release()
        assertFalse(engine.isReady())
    }

    @Test
    fun `getCurrentModelInfo returns null initially`() {
        assertNull(engine.getCurrentModelInfo())
    }

    @Test
    fun `setCurrentModelInfo updates model info`() {
        val info = MlcModelInfo.DEFAULT
        engine.setCurrentModelInfo(info)
        assertEquals(info, engine.getCurrentModelInfo())
    }

    @Test
    fun `release clears model info`() = runTest {
        engine.setCurrentModelInfo(MlcModelInfo.DEFAULT)
        engine.release()
        assertNull(engine.getCurrentModelInfo())
    }

    @Test
    fun `inference with different temperatures does not crash`() = runTest {
        val response1 = engine.inference("test", temperature = 0.0f)
        val response2 = engine.inference("test", temperature = 1.0f)
        assertTrue(response1 is AiResponse.Error)
        assertTrue(response2 is AiResponse.Error)
    }

    @Test
    fun `inference with large prompt does not crash`() = runTest {
        val largePrompt = "x".repeat(10_000)
        val response = engine.inference(largePrompt)
        assertTrue(response is AiResponse.Error)
    }
}
