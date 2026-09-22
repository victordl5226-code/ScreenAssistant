package com.screenassistant.core.domain.repository.ai

/**
 * Motor de inferencia local abstracto.
 *
 * Implementado por MLC LLM en `core:ai:local`.
 * Permite testing con mocks sin depender del motor real.
 */
interface LocalInferenceEngine {

    /**
     * Inicializa el motor con un modelo específico.
     *
     * @param modelPath Ruta al modelo en assets o disco
     * @return true si la inicialización fue exitosa
     */
    suspend fun initialize(modelPath: String): Boolean

    /**
     * Ejecuta inferencia con el modelo cargado.
     *
     * @param prompt Prompt de entrada
     * @param maxTokens Máximo de tokens a generar
     * @param temperature Temperatura de muestreo (0.0 = determinista)
     * @return Texto generado o error
     */
    suspend fun inference(
        prompt: String,
        maxTokens: Int = 1024,
        temperature: Float = 0.7f,
    ): AiResponse

    /**
     * Streaming de inferencia — genera tokens uno a uno.
     *
     * @param prompt Prompt de entrada
     * @param maxTokens Máximo de tokens a generar
     * @param temperature Temperatura de muestreo
     * @return Flow de tokens generados
     */
    fun streamInference(
        prompt: String,
        maxTokens: Int = 1024,
        temperature: Float = 0.7f,
    ): kotlinx.coroutines.flow.Flow<String>

    /**
     * Verifica si el motor está listo para inferencia.
     */
    fun isReady(): Boolean

    /**
     * Libera recursos del motor.
     */
    suspend fun release()
}

/**
 * Configuración de inferencia.
 */
data class InferenceConfig(
    val maxTokens: Int = 1024,
    val temperature: Float = 0.7f,
    val systemPrompt: String = "",
    val topP: Float = 0.9f,
    val topK: Int = 40,
)
