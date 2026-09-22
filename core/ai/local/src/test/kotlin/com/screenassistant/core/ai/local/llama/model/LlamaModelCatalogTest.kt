package com.screenassistant.core.ai.local.llama.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests unitarios para LlamaModelCatalog.
 */
class LlamaModelCatalogTest {

    @Test
    fun `TINY_LLAMA_Q4 tiene datos correctos`() {
        val model = LlamaModelCatalog.TINY_LLAMA_Q4
        assertEquals("tinyllama-1.1b-chat-v1.0-q4_k_m", model.id)
        assertEquals("TinyLlama 1.1B Chat", model.name)
        assertEquals("1.0", model.version)
        assertEquals("Q4_K_M", model.quantization)
        assertTrue(model.sizeBytes > 0)
        assertTrue(model.downloadUrl.contains("huggingface.co"))
        assertTrue(model.downloadUrl.endsWith(".gguf"))
    }

    @Test
    fun `TINY_LLAMA_Q8 tiene datos correctos`() {
        val model = LlamaModelCatalog.TINY_LLAMA_Q8
        assertEquals("tinyllama-1.1b-chat-v1.0-q8_0", model.id)
        assertEquals("Q8_0", model.quantization)
        assertTrue(model.sizeBytes > LlamaModelCatalog.TINY_LLAMA_Q4.sizeBytes)
    }

    @Test
    fun `ALL contiene ambos modelos`() {
        assertEquals(2, LlamaModelCatalog.ALL.size)
        assertTrue(LlamaModelCatalog.ALL.contains(LlamaModelCatalog.TINY_LLAMA_Q4))
        assertTrue(LlamaModelCatalog.ALL.contains(LlamaModelCatalog.TINY_LLAMA_Q8))
    }

    @Test
    fun `DEFAULT es Q4`() {
        assertEquals(LlamaModelCatalog.TINY_LLAMA_Q4, LlamaModelCatalog.DEFAULT)
    }

    @Test
    fun `findById retorna modelo existente`() {
        val model = LlamaModelCatalog.findById("tinyllama-1.1b-chat-v1.0-q4_k_m")
        assertNotNull(model)
        assertEquals("tinyllama-1.1b-chat-v1.0-q4_k_m", model?.id)
    }

    @Test
    fun `findById retorna null para ID inexistente`() {
        val model = LlamaModelCatalog.findById("modelo-fantasma")
        assertNull(model)
    }

    @Test
    fun `todos los modelos tienen URL valida`() {
        for (model in LlamaModelCatalog.ALL) {
            assertTrue("Modelo ${model.id} no tiene URL valida",
                       model.downloadUrl.startsWith("https://"))
        }
    }
}
