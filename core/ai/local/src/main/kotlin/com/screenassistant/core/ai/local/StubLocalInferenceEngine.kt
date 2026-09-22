package com.screenassistant.core.ai.local

import com.screenassistant.core.domain.repository.ai.AiResponse
import com.screenassistant.core.domain.repository.ai.LocalInferenceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Stub de [LocalInferenceEngine] para testing sin MLC LLM.
 *
 * Retorna error indicando que el motor local no está disponible.
 * Se reemplazará por la implementación real de MLC LLM cuando esté disponible.
 *
 * ## Uso
 * - Testing de AiOrchestratorImpl (verifica fallback a GEMINI)
 * - Desarrollo sin modelo local
 * - CI/CD sin dependencia de modelo de 2.3GB
 */
class StubLocalInferenceEngine : LocalInferenceEngine {

    @Volatile
    private var ready = false

    override suspend fun initialize(modelPath: String): Boolean {
        // Stub: nunca se inicializa correctamente
        ready = false
        return false
    }

    override suspend fun inference(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
    ): AiResponse {
        return AiResponse.Error(
            reason = "Motor local no disponible: MLC LLM no integrado",
            provider = com.screenassistant.core.domain.repository.ai.AiProvider.LOCAL,
            recoverable = false,
        )
    }

    override fun streamInference(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
    ): Flow<String> = flow {
        emit("Error: Motor local no disponible")
    }

    override fun isReady(): Boolean = ready

    override suspend fun release() {
        ready = false
    }
}
