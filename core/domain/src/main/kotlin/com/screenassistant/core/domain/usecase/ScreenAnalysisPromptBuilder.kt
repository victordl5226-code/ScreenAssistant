package com.screenassistant.core.domain.usecase

import com.screenassistant.core.domain.model.AssistantLanguage
import javax.inject.Inject

/**
 * Construye el prompt para análisis visual de pantalla.
 * Función pura: dado texto e imagen, genera el prompt optimizado para Gemini.
 * Respeta el idioma configurado del asistente (AssistantLanguage).
 */
class ScreenAnalysisPromptBuilder @Inject constructor(
    private val assistantLanguage: AssistantLanguage
) {

    /**
     * Construye el prompt de análisis visual.
     * @param screenText Texto extraído de la pantalla (puede estar vacío)
     * @param userQuestion Pregunta del usuario (puede estar vacío para análisis general)
     * @return Prompt formateado para Gemini en el idioma configurado
     */
    fun buildPrompt(screenText: String, userQuestion: String = ""): String {
        val base = when (assistantLanguage) {
            AssistantLanguage.ENGLISH -> "Analyze this Android screen visually."
            else -> "Analiza visualmente esta pantalla de Android."
        }
        return when {
            screenText.isNotEmpty() && userQuestion.isNotEmpty() -> when (assistantLanguage) {
                AssistantLanguage.ENGLISH ->
                    "$base Visible screen text: \"$screenText\". User question: \"$userQuestion\""
                else ->
                    "$base Texto visible en pantalla: \"$screenText\". Pregunta del usuario: \"$userQuestion\""
            }
            screenText.isNotEmpty() -> when (assistantLanguage) {
                AssistantLanguage.ENGLISH ->
                    "$base Visible screen text: \"$screenText\". Describe what is on the screen."
                else ->
                    "$base Texto visible en pantalla: \"$screenText\". Describe qué hay en la pantalla."
            }
            userQuestion.isNotEmpty() -> when (assistantLanguage) {
                AssistantLanguage.ENGLISH ->
                    "$base User question: \"$userQuestion\""
                else ->
                    "$base Pregunta del usuario: \"$userQuestion\""
            }
            else -> when (assistantLanguage) {
                AssistantLanguage.ENGLISH ->
                    "$base Describe what is on the screen and which app is active."
                else ->
                    "$base Describe qué hay en la pantalla y qué aplicación está activa."
            }
        }
    }
}
