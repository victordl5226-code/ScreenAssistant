package com.screenassistant.core.domain.usecase

import com.screenassistant.core.domain.di.IoDispatcher
import com.screenassistant.core.domain.repository.GeminiRepository
import com.screenassistant.core.domain.repository.ScreenContextRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Caso de uso: analiza visualmente la pantalla actual.
 * Captura screenshot + texto, construye prompt, envía a Gemini.
 * Retorna Result<String> con la respuesta o el error.
 */
class AnalyzeScreenUseCase @Inject constructor(
    private val screenContextRepository: ScreenContextRepository,
    private val geminiRepository: GeminiRepository,
    private val promptBuilder: ScreenAnalysisPromptBuilder,
    @IoDispatcher private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
) {
    /**
     * Analiza la pantalla actual y retorna la respuesta de Gemini.
     * @param userQuestion Pregunta opcional del usuario
     * @return Result con la respuesta de texto, o failure con la excepción
     */
    suspend operator fun invoke(userQuestion: String = ""): Result<String> {
        return try {
            // 1. Capturar screenshot en IO dispatcher (operación pesada de bitmap/compresión)
            val screenshot = withContext(ioDispatcher) {
                screenContextRepository.captureScreenshot()
            }

            // 2. Capturar texto de pantalla (siempre disponible vía AccessibilityService, barato)
            val screenText = screenContextRepository.screenText.value

            // 3. Construir prompt
            val prompt = promptBuilder.buildPrompt(screenText, userQuestion)

            // 4. Enviar a Gemini (con o sin imagen) - llamada de red, ya en IO por el caller
            val response = geminiRepository.sendMessage(prompt, screenshot)

            if (response != null) {
                Result.success(response)
            } else {
                Result.failure(Exception("No se pudo obtener respuesta del asistente"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
