package com.screenassistant.core.ai.memory.semantic

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Implementación brute-force de VectorStore.
 *
 * Para el volumen esperado (<10K vectores de 384 dimensiones):
 * - Memoria: ~15MB (10K × 384 × 4 bytes)
 * - Búsqueda: ~15ms (cosine similarity sobre 10K vectores)
 *
 * Estrategia:
 * 1. Todos los vectores se mantienen en memoria (List<VectorEntry>)
 * 2. La búsqueda calcula cosine similarity contra TODOS los vectores
 * 3. Se ordenan por similitud y se retornan los top-K
 *
 * Ventajas sobre sqlite-vec para este caso:
 * - Sin dependencias nativas
 * - Sin complejidad de integración con Room
 * - Rendimiento suficiente para el volumen esperado
 * - Código Kotlin puro, testeable sin Android
 */
class BruteForceVectorStore : VectorStore {

    private val entries = mutableListOf<VectorEntry>()
    private val mutex = Mutex()

    override suspend fun add(id: String, vector: FloatArray, metadata: VectorMetadata) {
        mutex.withLock {
            // Eliminar entrada existente con el mismo ID
            entries.removeAll { it.id == id }
            entries.add(VectorEntry(id, vector, metadata))
        }
    }

    override suspend fun search(queryVector: FloatArray, k: Int): List<VectorSearchResult> {
        return mutex.withLock {
            entries.map { entry ->
                VectorSearchResult(
                    id = entry.id,
                    similarity = CosineSimilarity.calculate(queryVector, entry.vector),
                    metadata = entry.metadata,
                )
            }
            .sortedByDescending { it.similarity }
            .take(k)
        }
    }

    override suspend fun remove(id: String) {
        mutex.withLock {
            entries.removeAll { it.id == id }
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            entries.clear()
        }
    }

    override suspend fun loadAll(entries: List<VectorEntry>) {
        mutex.withLock {
            this.entries.clear()
            this.entries.addAll(entries)
        }
    }

    override fun size(): Int = runBlocking { mutex.withLock { entries.size } }
}
