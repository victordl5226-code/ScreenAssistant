package com.screenassistant.core.ai.memory.semantic

/**
 * Genera embeddings vectoriales a partir de texto.
 * Implementado con ONNX Runtime + all-MiniLM-L6-v2.
 *
 * El modelo genera vectores de 384 dimensiones que capturan
 * el significado semántico del texto.
 */
interface EmbeddingGenerator {

    /**
     * Genera un embedding de 384 dimensiones para el texto dado.
     *
     * @param text Texto a embeber (máximo 256 tokens)
     * @return FloatArray de 384 dimensiones
     * @throws IllegalStateException si el modelo no está inicializado
     */
    suspend fun embed(text: String): FloatArray

    /**
     * Genera embeddings para múltiples textos (batch).
     * Más eficiente que llamar embed() repetidamente.
     *
     * @param texts Lista de textos a embeber
     * @return Lista de FloatArrays en el mismo orden
     */
    suspend fun embedBatch(texts: List<String>): List<FloatArray>

    /**
     * Indica si el modelo está listo para inferencia.
     * Puede ser false durante la carga inicial del modelo.
     */
    fun isReady(): Boolean
}
