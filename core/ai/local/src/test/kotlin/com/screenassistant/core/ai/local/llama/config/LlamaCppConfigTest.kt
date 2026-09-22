package com.screenassistant.core.ai.local.llama.config

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests unitarios para LlamaCppConfig.
 */
class LlamaCppConfigTest {

    @Test
    fun `valores por defecto son correctos`() {
        val config = LlamaCppConfig()
        assertEquals(2048, config.contextSize)
        assertEquals(512, config.batchSize)
        assertTrue(config.numThreads in 2..8)
        assertEquals(1024, config.maxTokens)
        assertEquals(0.7f, config.temperature, 0.01f)
        assertEquals(0.9f, config.topP, 0.01f)
        assertEquals(40, config.topK)
        assertEquals(1.1f, config.repeatPenalty, 0.01f)
        assertTrue(config.systemPrompt.isNotEmpty())
    }

    @Test
    fun `toInitParams convierte correctamente`() {
        val config = LlamaCppConfig()
        config.contextSize = 4096
        config.batchSize = 1024
        config.numThreads = 6

        val params = config.toInitParams()
        assertEquals(4096, params.nCtx)
        assertEquals(1024, params.nBatch)
        assertEquals(6, params.nThreads)
        assertTrue(params.useMmap)
        assertFalse(params.useMlock)
        assertFalse(params.verbose)
    }

    @Test
    fun `getOptimalThreads retorna valor razonable`() {
        val config = LlamaCppConfig()
        val threads = config.getOptimalThreads()
        assertTrue(threads in 2..8)
    }

    @Test
    fun `systemPrompt contiene asistente`() {
        val config = LlamaCppConfig()
        assertTrue(config.systemPrompt.contains("J.A.R.V.I.S") ||
                   config.systemPrompt.contains("asistente"))
    }

    @Test
    fun `se pueden modificar valores`() {
        val config = LlamaCppConfig()
        config.contextSize = 8192
        config.temperature = 0.5f
        config.topP = 0.8f

        assertEquals(8192, config.contextSize)
        assertEquals(0.5f, config.temperature, 0.01f)
        assertEquals(0.8f, config.topP, 0.01f)
    }
}
