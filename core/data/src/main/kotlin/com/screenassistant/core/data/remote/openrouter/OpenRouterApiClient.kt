package com.screenassistant.core.data.remote.openrouter

import kotlinx.coroutines.flow.Flow

/**
 * Interfaz testable del cliente HTTP para OpenRouter (formato OpenAI-compatible).
 * C6 de QA: contratos de chatCompletion y chatCompletionStream.
 */
interface OpenRouterApiClient {

    /**
     * Completa un chat (no streaming). Lanza [OpenRouterException] en error HTTP.
     */
    suspend fun chatCompletion(request: OpenRouterRequest): OpenRouterResponse

    /**
     * Completa un chat con streaming (SSE). Emite chunks de [OpenRouterResponse]
     * conforme llegan datos del servidor.
     */
    fun chatCompletionStream(request: OpenRouterRequest): Flow<OpenRouterResponse>
}
