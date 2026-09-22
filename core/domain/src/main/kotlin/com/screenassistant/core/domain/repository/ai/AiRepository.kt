package com.screenassistant.core.domain.repository.ai

/**
 * Proveedor unificado de IA — reemplaza gradualmente a GeminiRepository.
 *
 * Soporta múltiples backends (local MLC LLM, Gemini cloud, híbrido).
 * El [AiOrchestrator] decide qué proveedor usar según conectividad.
 *
 * ## Migración desde GeminiRepository
 * - [GeminiRepository] se mantiene por ahora (deprecated gradual).
 * - [AiRepository] agrega capacidades: streaming, fallback, provider info.
 * - Los nuevos componentes DEBEN usar [AiRepository].
 * - Los componentes existentes se migran incrementalmente.
 */
interface AiRepository {

    /**
     * Envía mensaje con imagen y retorna respuesta completa.
     *
     * @param message Texto del usuario
     * @param imageData Datos de imagen (puede ser null si es solo texto)
     * @param provider Proveedor a usar (AUTO decide según conectividad)
     * @return Respuesta del modelo
     */
    suspend fun sendMessage(
        message: String,
        imageData: ByteArray? = null,
        provider: AiProvider = AiProvider.AUTO,
    ): AiResponse

    /**
     * Envía mensaje y retorna respuesta como Flow de chunks.
     *
     * @param message Texto del usuario
     * @param provider Proveedor a usar
     * @return Flow de chunks de texto
     */
    fun streamMessage(
        message: String,
        provider: AiProvider = AiProvider.AUTO,
    ): kotlinx.coroutines.flow.Flow<String>

    /**
     * Verifica si un proveedor está disponible.
     */
    suspend fun isProviderAvailable(provider: AiProvider): Boolean

    /**
     * Obtiene el proveedor actualmente activo.
     */
    fun getCurrentProvider(): AiProvider
}

/**
 * Proveedores de IA disponibles.
 */
enum class AiProvider {
    /** LLM local via MLC LLM (sin internet) */
    LOCAL,
    /** Gemini cloud (requiere internet) */
    GEMINI,
    /** Auto: LOCAL primero, GEMINI como fallback */
    AUTO,
}

/**
 * Respuesta tipada del sistema de IA.
 */
sealed class AiResponse {
    /** Respuesta exitosa */
    data class Success(
        val text: String,
        val provider: AiProvider,
        val latencyMs: Long = 0,
    ) : AiResponse()

    /** Error en la inferencia */
    data class Error(
        val reason: String,
        val provider: AiProvider,
        val recoverable: Boolean = true,
    ) : AiResponse()

    /** Fallback falló: ambos providers fallaron */
    data class FallbackFailed(
        val localError: String,
        val geminiError: String,
    ) : AiResponse()
}
