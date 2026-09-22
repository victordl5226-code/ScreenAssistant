package com.screenassistant.core.ai.local.llama.prompt

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests unitarios para PromptTemplate.
 */
class PromptTemplateTest {

    @Test
    fun `tinyLlamaChat genera formato correcto`() {
        val result = PromptTemplate.tinyLlamaChat(
            systemPrompt = "Eres un asistente",
            userMessage = "Hola",
        )

        assertTrue(result.contains("<|system|>"))
        assertTrue(result.contains("Eres un asistente"))
        assertTrue(result.contains("<|user|>"))
        assertTrue(result.contains("Hola"))
        assertTrue(result.contains("<|assistant|>"))
    }

    @Test
    fun `tinyLlamaChat incluye historial`() {
        val history = listOf(
            "Hola" to "Hola, soy JARVIS",
            "Como estas" to "Bien, gracias",
        )
        val result = PromptTemplate.tinyLlamaChat(
            systemPrompt = "Asistente",
            userMessage = "Adios",
            conversationHistory = history,
        )

        assertTrue(result.contains("Hola"))
        assertTrue(result.contains("Hola, soy JARVIS"))
        assertTrue(result.contains("Como estas"))
        assertTrue(result.contains("Bien, gracias"))
        assertTrue(result.contains("Adios"))
    }

    @Test
    fun `chatML genera formato correcto`() {
        val result = PromptTemplate.chatML(
            systemPrompt = "Eres un asistente",
            userMessage = "Hola",
        )

        assertTrue(result.contains("im_start"))
        assertTrue(result.contains("system"))
        assertTrue(result.contains("user"))
        assertTrue(result.contains("assistant"))
        assertTrue(result.contains("im_end"))
    }

    @Test
    fun `detectTemplate retorna template para tinyllama`() {
        val template = PromptTemplate.detectTemplate("tinyllama-1.1b-chat")
        val result = template("sys", "msg", emptyList())
        assertTrue(result.contains("<|system|>"))
    }

    @Test
    fun `detectTemplate retorna template chatml`() {
        val template = PromptTemplate.detectTemplate("chatml-model")
        val result = template("sys", "msg", emptyList())
        assertTrue(result.contains("im_start"))
    }

    @Test
    fun `detectTemplate usa tinyllama por defecto`() {
        val template = PromptTemplate.detectTemplate("modelo-desconocido")
        val result = template("sys", "msg", emptyList())
        assertTrue(result.contains("<|system|>"))
    }

    @Test
    fun `tinyLlamaChat sin historial es corto`() {
        val result = PromptTemplate.tinyLlamaChat("sys", "msg")
        val lines = result.lines().filter { it.isNotBlank() }
        assertTrue(lines.size <= 5)
    }
}
