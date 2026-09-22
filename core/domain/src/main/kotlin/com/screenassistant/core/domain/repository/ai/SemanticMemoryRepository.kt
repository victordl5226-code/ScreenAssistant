package com.screenassistant.core.domain.repository.ai

/**
 * Repositorio de memoria semántica con embeddings vectoriales.
 * Permite búsqueda por significado, no por texto exacto.
 *
 * Implementado en `core/ai:memory` con ONNX Runtime + all-MiniLM-L6-v2.
 * Domain solo conoce el contrato.
 *
 * ## Caso de uso
 * - Usuario: "¿Qué hora tengo que ir al trabajo?"
 * - Búsqueda semántica → "Mi empleo empieza a las 9am" (similitud alta)
 * - Búsqueda LIKE → no match (similitud baja)
 */
interface SemanticMemoryRepository {

    /**
     * Busca hechos semánticamente similares a la query.
     *
     * @param query Texto del usuario (ej: "¿Qué hora tengo que ir al trabajo?")
     * @param limit Máximo de resultados
     * @return Lista de hechos ordenados por similitud descendente
     */
    suspend fun searchSimilar(query: String, limit: Int = 5): List<SemanticFactResult>

    /**
     * Almacena un hecho con su embedding vectorial.
     *
     * @param factId ID del hecho existente en knowledge_facts
     * @param text Texto para generar embedding (subject + predicate + object)
     * @param category Categoría del hecho
     */
    suspend fun storeFactEmbedding(factId: String, text: String, category: String)

    /**
     * Indica si el generador de embeddings está listo.
     * Puede ser false durante la inicialización del modelo.
     */
    fun isReady(): Boolean

    /**
     * Reconstruye todos los embeddings desde knowledge_facts.
     * Útil para migraciones o si el modelo cambia.
     */
    suspend fun rebuildAllEmbeddings()
}

/**
 * Resultado de búsqueda semántica.
 */
data class SemanticFactResult(
    val factId: String,
    val text: String,
    val category: String,
    val confidence: Float,
    val similarity: Float,  // 0.0 a 1.0 (cosine similarity)
    val timestamp: Long,
)
