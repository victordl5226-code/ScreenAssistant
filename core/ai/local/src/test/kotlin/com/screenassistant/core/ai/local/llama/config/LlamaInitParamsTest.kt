package com.screenassistant.core.ai.local.llama.config

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests unitarios para LlamaInitParams.
 */
class LlamaInitParamsTest {

    @Test
    fun `valores por defecto son correctos`() {
        val params = LlamaInitParams()
        assertEquals(2048, params.nCtx)
        assertEquals(512, params.nBatch)
        assertEquals(4, params.nThreads)
        assertTrue(params.useMmap)
        assertFalse(params.useMlock)
        assertFalse(params.verbose)
    }

    @Test
    fun `TINY_LLAMA_MOBILE tiene configuracion optimizada`() {
        val params = LlamaInitParams.TINY_LLAMA_MOBILE
        assertEquals(2048, params.nCtx)
        assertEquals(512, params.nBatch)
        assertEquals(4, params.nThreads)
        assertTrue(params.useMmap)
        assertFalse(params.useMlock)
        assertFalse(params.verbose)
    }

    @Test
    fun `TINY_LLAMA_TABLET tiene mas contexto`() {
        val params = LlamaInitParams.TINY_LLAMA_TABLET
        assertEquals(4096, params.nCtx)
        assertEquals(1024, params.nBatch)
        assertEquals(6, params.nThreads)
    }

    @Test
    fun `data class equals funciona`() {
        val p1 = LlamaInitParams(nCtx = 1024)
        val p2 = LlamaInitParams(nCtx = 1024)
        val p3 = LlamaInitParams(nCtx = 2048)
        assertEquals(p1, p2)
        assertNotEquals(p1, p3)
    }

    @Test
    fun `copy funciona correctamente`() {
        val original = LlamaInitParams()
        val modified = original.copy(nCtx = 4096, nThreads = 8)
        assertEquals(4096, modified.nCtx)
        assertEquals(8, modified.nThreads)
        assertEquals(original.nBatch, modified.nBatch)
    }
}
