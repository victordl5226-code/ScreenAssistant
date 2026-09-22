package com.screenassistant.core.ai.memory.semantic

/**
 * Almacenamiento y búsqueda de vectores en memoria.
 * Para el volumen esperado (<10K vectores), brute-force es suficiente.
 *
 * Diseño:
 * - Los vectores se mantienen en memoria (List<VectorEntry>)
 * - La persistencia la maneja SemanticFactDao (Room)
 * - La búsqueda es O(n) pero n < 10K → ~15ms
 */
interface VectorStore {

    /**
     * Agrega un vector con metadata asociada.
     */
    suspend fun add(id: String, vector: FloatArray, metadata: VectorMetadata)

    /**
     * Busca los K vectores más similares al query.
     *
     * @param queryVector Vector de consulta
     * @param k Número de vecinos cercanos
     * @return Lista ordenada por similitud descendente
     */
    suspend fun search(queryVector: FloatArray, k: Int = 5): List<VectorSearchResult>

    /**
     * Elimina un vector por ID.
     */
    suspend fun remove(id: String)

    /**
     * Elimina todos los vectores.
     */
    suspend fun clear()

    /**
     * Carga todos los vectores desde la fuente persistente.
     */
    suspend fun loadAll(entries: List<VectorEntry>)

    /**
     * Número de vectores almacenados.
     */
    fun size(): Int
}

/**
 * Metadata asociada a un vector.
 */
data class VectorMetadata(
    val factId: String,
    val text: String,
    val category: String,
    val confidence: Float,
    val timestamp: Long,
)

/**
 * Entrada completa: vector + metadata.
 */
data class VectorEntry(
    val id: String,
    val vector: FloatArray,
    val metadata: VectorMetadata,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VectorEntry) return false
        return id == other.id
    }
    override fun hashCode(): Int = id.hashCode()
}

/**
 * Resultado de búsqueda vectorial.
 */
data class VectorSearchResult(
    val id: String,
    val similarity: Float,
    val metadata: VectorMetadata,
)
