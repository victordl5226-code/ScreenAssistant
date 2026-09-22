package com.screenassistant.core.domain.usecase

import com.screenassistant.core.domain.model.AssistantLanguage
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ScreenAnalysisPromptBuilderTest {

    private lateinit var builderSpanish: ScreenAnalysisPromptBuilder
    private lateinit var builderEnglish: ScreenAnalysisPromptBuilder

    @Before
    fun setup() {
        builderSpanish = ScreenAnalysisPromptBuilder(AssistantLanguage.SPANISH)
        builderEnglish = ScreenAnalysisPromptBuilder(AssistantLanguage.ENGLISH)
    }

    @Test
    fun `buildPrompt con texto e imagen genera prompt completo en español`() {
        val result = builderSpanish.buildPrompt(
            screenText = "Hola mundo",
            userQuestion = "¿Qué es esto?"
        )

        assertEquals(
            "Analiza visualmente esta pantalla de Android. Texto visible en pantalla: \"Hola mundo\". Pregunta del usuario: \"¿Qué es esto?\"",
            result
        )
    }

    @Test
    fun `buildPrompt con texto e imagen genera prompt completo en inglés`() {
        val result = builderEnglish.buildPrompt(
            screenText = "Hello world",
            userQuestion = "What is this?"
        )

        assertEquals(
            "Analyze this Android screen visually. Visible screen text: \"Hello world\". User question: \"What is this?\"",
            result
        )
    }

    @Test
    fun `buildPrompt solo con texto genera prompt de análisis textual en español`() {
        val result = builderSpanish.buildPrompt(
            screenText = "Botón de guardar",
            userQuestion = ""
        )

        assertEquals(
            "Analiza visualmente esta pantalla de Android. Texto visible en pantalla: \"Botón de guardar\". Describe qué hay en la pantalla.",
            result
        )
    }

    @Test
    fun `buildPrompt solo con texto genera prompt de análisis textual en inglés`() {
        val result = builderEnglish.buildPrompt(
            screenText = "Save button",
            userQuestion = ""
        )

        assertEquals(
            "Analyze this Android screen visually. Visible screen text: \"Save button\". Describe what is on the screen.",
            result
        )
    }

    @Test
    fun `buildPrompt solo con imagen genera prompt de análisis visual en español`() {
        val result = builderSpanish.buildPrompt(
            screenText = "",
            userQuestion = "¿Qué aplicación está abierta?"
        )

        assertEquals(
            "Analiza visualmente esta pantalla de Android. Pregunta del usuario: \"¿Qué aplicación está abierta?\"",
            result
        )
    }

    @Test
    fun `buildPrompt solo con imagen genera prompt de análisis visual en inglés`() {
        val result = builderEnglish.buildPrompt(
            screenText = "",
            userQuestion = "What app is open?"
        )

        assertEquals(
            "Analyze this Android screen visually. User question: \"What app is open?\"",
            result
        )
    }

    @Test
    fun `buildPrompt con ambos vacios genera prompt generico en español`() {
        val result = builderSpanish.buildPrompt(
            screenText = "",
            userQuestion = ""
        )

        assertEquals(
            "Analiza visualmente esta pantalla de Android. Describe qué hay en la pantalla y qué aplicación está activa.",
            result
        )
    }

    @Test
    fun `buildPrompt con ambos vacios genera prompt generico en inglés`() {
        val result = builderEnglish.buildPrompt(
            screenText = "",
            userQuestion = ""
        )

        assertEquals(
            "Analyze this Android screen visually. Describe what is on the screen and which app is active.",
            result
        )
    }
}
