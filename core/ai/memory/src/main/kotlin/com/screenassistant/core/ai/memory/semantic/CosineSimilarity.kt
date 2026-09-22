package com.screenassistant.core.ai.memory.semantic

import kotlin.math.sqrt

/**
 * Calcula la similitud coseno entre dos vectores.
 * Utilidad pura — sin dependencias Android.
 *
 * La similitud coseno mide qué tan similares son dos vectores
 * independientemente de su magnitud. Rango: [-1.0, 1.0]
 *
 * - 1.0 = vectores idénticos
 * - 0.0 = vectores ortogonales (sin relación)
 * - -1.0 = vectores opuestos
 *
 * Para embeddings de texto, valores típicos:
 * - > 0.8: Muy similar (mismo significado)
 * - 0.5-0.8: Moderadamente similar
 * - < 0.3: Poca relación semántica
 */
object CosineSimilarity {

    /**
     * Similitud coseno entre dos vectores de misma dimensión.
     *
     * @param a Primer vector
     * @param b Segundo vector
     * @return Float entre -1.0 y 1.0
     * @throws IllegalArgumentException si los vectores tienen diferente dimensión
     */
    fun calculate(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) {
            "Vectors must have same dimension: ${a.size} vs ${b.size}"
        }

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator == 0f) 0f else dotProduct / denominator
    }

    /**
     * Distancia coseno (1 - similitud).
     * Rango: [0.0, 2.0] donde 0.0 = idénticos.
     */
    fun distance(a: FloatArray, b: FloatArray): Float {
        return 1f - calculate(a, b)
    }
}
