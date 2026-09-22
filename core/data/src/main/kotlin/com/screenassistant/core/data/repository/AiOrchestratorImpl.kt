package com.screenassistant.core.data.repository

import com.screenassistant.core.domain.model.ImageData
import com.screenassistant.core.domain.repository.ai.AiOrchestrator
import com.screenassistant.core.domain.repository.ai.AiProvider
import com.screenassistant.core.domain.repository.ai.AiRepository
import com.screenassistant.core.domain.repository.ai.AiResponse
import com.screenassistant.core.domain.repository.ai.ConnectivityMonitor
import com.screenassistant.core.domain.repository.ai.LocalInferenceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [AiOrchestrator] — decide LOCAL vs GEMINI.
 *
 * ## Estrategia (offline-first)
 * 1. Si LOCAL disponible Y preferLocal → LOCAL
 * 2. Si GEMINI disponible (hay internet) → GEMINI
 * 3. Si LOCAL falla → GEMINI (fallback)
 * 4. Si GEMINI falla → error
 * 5. Si ninguno disponible → error claro
 *
 * @param localEngine Motor de inferencia local (puede ser stub)
 * @param geminiRepository Repositorio Gemini existente
 * @param connectivityMonitor Monitor de red
 */
@Singleton
class AiOrchestratorImpl @Inject constructor(
    private val localEngine: LocalInferenceEngine,
    private val geminiRepository: com.screenassistant.core.domain.repository.GeminiRepository,
    private val connectivityMonitor: ConnectivityMonitor,
) : AiOrchestrator {

    override suspend fun processMessage(
        message: String,
        imageData: ImageData?,
        preferLocal: Boolean,
    ): AiResponse {
        val provider = resolveProvider(preferLocal)

        return when (provider) {
            AiProvider.LOCAL -> {
                val result = localEngine.inference(message)
                if (result is AiResponse.Error && result.recoverable) {
                    // Local falló, intentar Gemini como fallback
                    fallbackToGemini(message, imageData, result.reason)
                } else {
                    result
                }
            }
            AiProvider.GEMINI -> {
                try {
                    val response = geminiRepository.sendMessage(message, imageData)
                    AiResponse.Success(
                        text = response ?: "Sin respuesta de Gemini",
                        provider = AiProvider.GEMINI,
                    )
                } catch (e: Exception) {
                    AiResponse.Error(
                        reason = e.message ?: "Error desconocido en Gemini",
                        provider = AiProvider.GEMINI,
                        recoverable = false,
                    )
                }
            }
            AiProvider.AUTO -> {
                // AUTO no debería llegar aquí (resolved a LOCAL o GEMINI)
                AiResponse.Error(
                    reason = "Provider AUTO no resuelto",
                    provider = AiProvider.AUTO,
                    recoverable = false,
                )
            }
        }
    }

    override fun streamMessage(
        message: String,
        preferLocal: Boolean,
    ): Flow<String> = flow {
        val provider = resolveProvider(preferLocal)
        when (provider) {
            AiProvider.LOCAL -> {
                localEngine.streamInference(message).collect { chunk ->
                    emit(chunk)
                }
            }
            AiProvider.GEMINI -> {
                geminiRepository.streamMessage(message).collect { chunk ->
                    emit(chunk)
                }
            }
            AiProvider.AUTO -> {
                emit("Error: Provider AUTO no resuelto")
            }
        }
    }

    override suspend fun resolveProvider(preferLocal: Boolean): AiProvider {
        val localAvailable = localEngine.isReady()
        val geminiAvailable = connectivityMonitor.isConnected()

        return when {
            // J.A.R.V.I.S. v3.7.1: Si hay internet, preferimos la "Gran Nube" (Gemini)
            // para máxima inteligencia, a menos que se pida local explícitamente.
            geminiAvailable && !preferLocal -> AiProvider.GEMINI
            localAvailable -> AiProvider.LOCAL
            geminiAvailable -> AiProvider.GEMINI
            else -> AiProvider.LOCAL
        }
    }

    override suspend fun isLocalAvailable(): Boolean {
        return localEngine.isReady()
    }

    override suspend fun isGeminiAvailable(): Boolean {
        return connectivityMonitor.isConnected()
    }

    private suspend fun fallbackToGemini(
        message: String,
        imageData: ImageData?,
        localError: String,
    ): AiResponse {
        val geminiAvailable = connectivityMonitor.isConnected()
        if (!geminiAvailable) {
            return AiResponse.Error(
                reason = "Sin conexión y motor local no disponible: $localError",
                provider = AiProvider.LOCAL,
                recoverable = true
            )
        }
        
        return try {
            val response = geminiRepository.sendMessage(message, imageData)
            if (response == null) {
                AiResponse.Error("Gemini no respondió", AiProvider.GEMINI, false)
            } else {
                AiResponse.Success(response, AiProvider.GEMINI)
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Error desconocido"
            if (errorMsg.contains("API key", ignoreCase = true)) {
                AiResponse.Error(
                    reason = "Aún no tengo mi clave de IA configurada. Revisa la configuración de la API key e inténtalo de nuevo.",
                    provider = AiProvider.GEMINI,
                    recoverable = false
                )
            } else {
                AiResponse.FallbackFailed(
                    localError = localError,
                    geminiError = errorMsg,
                )
            }
        }
    }
}
