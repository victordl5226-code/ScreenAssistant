package com.screenassistant.core.ai.local.llama.config

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configuración del motor LlamaCpp.
 *
 * Configuración centralizada para el motor de inferencia.
 * Los valores se pueden cambiar en runtime desde la UI de configuración.
 */
@Singleton
class LlamaCppConfig @Inject constructor() {

    /** Tamaño del contexto en tokens */
    var contextSize: Int = 2048

    /** Tamaño del batch para procesamiento */
    var batchSize: Int = 512

    /** Número de hilos CPU (auto-detectado) */
    var numThreads: Int = getOptimalThreads()

    /** Máximo de tokens a generar */
    var maxTokens: Int = 1024

    /** Temperatura de muestreo */
    var temperature: Float = 0.7f

    /** Top-p sampling */
    var topP: Float = 0.9f

    /** Top-k sampling */
    var topK: Int = 40

    /** Penalización de repetición */
    var repeatPenalty: Float = 1.1f

    /** Prompt del sistema para el chat */
    var systemPrompt: String = DEFAULT_SYSTEM_PROMPT

    /**
     * Convierte la configuración a parámetros de inicialización.
     */
    fun toInitParams(): LlamaInitParams {
        return LlamaInitParams(
            nCtx = contextSize,
            nBatch = batchSize,
            nThreads = numThreads,
            useMmap = true,
            useMlock = false,
            verbose = false,
        )
    }

    /**
     * Obtiene el número óptimo de hilos para el dispositivo actual.
     * En JVM tests retorna 4 por defecto.
     */
    fun getOptimalThreads(): Int {
        return try {
            val cpuCores = Runtime.getRuntime().availableProcessors()
            cpuCores.coerceIn(2, 8)
        } catch (e: Exception) {
            4 // Fallback para tests JVM
        }
    }

    companion object {
        /** Prompt del sistema por defecto */
        const val DEFAULT_SYSTEM_PROMPT = "Eres J.A.R.V.I.S., un asistente virtual inteligente y amigable. Responde en español de forma clara y concisa."
    }
}
