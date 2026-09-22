package com.screenassistant.core.domain.repository.ai

import com.screenassistant.core.domain.model.ImageData
import kotlinx.coroutines.flow.Flow

/**
 * Orquestador de IA — decide qué proveedor usar según conectividad.
 *
 * Implementado en `core:data` (no en core:domain).
 * Strategy pattern: LOCAL → GEMINI (fallback) → Error.
 *
 * ## Flujo
 * 1. Si hay internet Y GEMINI configurado → GEMINI
 * 2. Si LOCAL disponible → LOCAL
 * 3. Si ambos disponibles → LOCAL (offline-first)
 * 4. Si ninguno → error claro
 */
interface AiOrchestrator {

    /**
     * Procesa un mensaje usando la estrategia óptima.
     *
     * @param message Mensaje del usuario
     * @param imageData Datos de imagen (opcional)
     * @param preferLocal Si true, prefiere LOCAL aunque haya internet
     * @return Respuesta tipada con provider usado
     */
    suspend fun processMessage(
        message: String,
        imageData: ImageData? = null,
        preferLocal: Boolean = true,
    ): AiResponse

    /**
     * Streaming con estrategia óptima.
     *
     * @param message Mensaje del usuario
     * @param preferLocal Si true, prefiere LOCAL
     * @return Flow de chunks de texto
     */
    fun streamMessage(
        message: String,
        preferLocal: Boolean = true,
    ): Flow<String>

    /**
     * Obtiene el provider que se usaría actualmente.
     */
    suspend fun resolveProvider(preferLocal: Boolean = true): AiProvider

    /**
     * Verifica si LOCAL está disponible y funcional.
     */
    suspend fun isLocalAvailable(): Boolean

    /**
     * Verifica si GEMINI está disponible y funcional.
     */
    suspend fun isGeminiAvailable(): Boolean
}
