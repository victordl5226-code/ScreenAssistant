package com.screenassistant.core.ai.memory.semantic

import android.util.Log
import com.screenassistant.core.domain.repository.ai.SemanticFactResult
import com.screenassistant.core.domain.repository.ai.SemanticMemoryRepository
import com.screenassistant.core.ai.memory.KnowledgeFactDao
import com.screenassistant.core.ai.memory.semantic.SemanticFactEntity.Companion.toFloatArray
import com.screenassistant.core.ai.memory.semantic.SemanticFactEntity.Companion.toEmbeddingBytes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [SemanticMemoryRepository].
 *
 * Orquesta:
 * 1. EmbeddingGenerator → genera vectores de 384 dimensiones
 * 2. VectorStore → búsqueda brute-force por cosine similarity
 * 3. SemanticFactDao → persistencia Room (solo load/save, NO búsqueda)
 *
 * ## Flujo de búsqueda
 * 1. Recibe query del usuario
 * 2. Genera embedding de la query (~50-100ms)
 * 3. Busca en VectorStore (brute-force, ~15ms)
 * 4. Retorna top-K con scores de similitud
 *
 * ## Flujo de almacenamiento
 * 1. Recibe hecho nuevo
 * 2. Genera embedding del texto
 * 3. Guarda en Room (SemanticFactDao)
 * 4. Agrega a VectorStore (en memoria)
 */
@Singleton
class SemanticMemoryRepositoryImpl @Inject constructor(
    private val embeddingGenerator: EmbeddingGenerator,
    private val vectorStore: VectorStore,
    private val semanticFactDao: SemanticFactDao,
    private val knowledgeFactDao: KnowledgeFactDao,
) : SemanticMemoryRepository {

    companion object {
        private const val TAG = "SemanticMemoryRepo"
    }

    /**
     * Flag de inicialización. Se pone en true después de cargar los vectores.
     */
    @Volatile
    private var initialized = false
    private val initMutex = Mutex()

    /**
     * Inicializa el VectorStore cargando todos los embeddings desde Room.
     * Se llama una vez al inicio (lazy). Thread-safe con Mutex.
     */
    private suspend fun ensureInitialized() {
        if (initialized) return

        initMutex.withLock {
            // Doble verificación después de adquirir el mutex
            if (initialized) return

            try {
                val entities = semanticFactDao.getAll()
                if (entities.isNotEmpty()) {
                    val entries = entities.map { entity ->
                        VectorEntry(
                            id = entity.id,
                            vector = entity.embedding.toFloatArray(),
                            metadata = VectorMetadata(
                                factId = entity.factId,
                                text = entity.text,
                                category = entity.category,
                                confidence = entity.confidence,
                                timestamp = entity.timestamp,
                            ),
                        )
                    }
                    vectorStore.loadAll(entries)
                    Log.d(TAG, "Loaded ${entries.size} embeddings into VectorStore")
                }
                initialized = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize VectorStore", e)
            }
        }
    }

    override suspend fun searchSimilar(query: String, limit: Int): List<SemanticFactResult> {
        if (!isReady()) {
            Log.w(TAG, "EmbeddingGenerator not ready, cannot search")
            return emptyList()
        }

        ensureInitialized()

        return try {
            // 1. Generar embedding de la query
            val queryEmbedding = embeddingGenerator.embed(query)

            // 2. Buscar en VectorStore (brute-force cosine)
            val results = vectorStore.search(queryEmbedding, k = limit)

            // 3. Mapear a resultados
            results.map { result ->
                SemanticFactResult(
                    factId = result.metadata.factId,
                    text = result.metadata.text,
                    category = result.metadata.category,
                    confidence = result.metadata.confidence,
                    similarity = result.similarity,
                    timestamp = result.metadata.timestamp,
                )
            }.filter { it.similarity > 0.3f }  // Umbral mínimo de relevancia
        } catch (e: Exception) {
            Log.e(TAG, "Semantic search failed", e)
            emptyList()
        }
    }

    override suspend fun storeFactEmbedding(
        factId: String,
        text: String,
        category: String,
    ) {
        if (!isReady()) {
            Log.w(TAG, "EmbeddingGenerator not ready, skipping store")
            return
        }

        try {
            // 1. Generar embedding
            val embedding = embeddingGenerator.embed(text)

            // 2. Crear entity
            val entity = SemanticFactEntity(
                id = "sem_$factId",
                factId = factId,
                text = text,
                embedding = embedding.toEmbeddingBytes(),
                category = category,
                confidence = 1.0f,
                timestamp = System.currentTimeMillis(),
            )

            // 3. Guardar en Room
            semanticFactDao.insert(entity)

            // 4. Agregar a VectorStore
            vectorStore.add(
                id = entity.id,
                vector = embedding,
                metadata = VectorMetadata(
                    factId = factId,
                    text = text,
                    category = category,
                    confidence = entity.confidence,
                    timestamp = entity.timestamp,
                ),
            )

            Log.d(TAG, "Stored embedding for fact: $factId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store embedding for fact: $factId", e)
        }
    }

    override fun isReady(): Boolean {
        return embeddingGenerator.isReady()
    }

    override suspend fun rebuildAllEmbeddings() {
        try {
            // Limpiar store actual
            vectorStore.clear()
            semanticFactDao.clear()
            initialized = false

            if (!embeddingGenerator.isReady()) {
                Log.w(TAG, "EmbeddingGenerator not ready, cannot rebuild")
                return
            }

            // Leer todos los hechos de knowledge_facts
            val knowledgeFacts = knowledgeFactDao.getRecent(limit = Int.MAX_VALUE)
            Log.i(TAG, "Rebuilding embeddings for ${knowledgeFacts.size} facts...")

            // Regenerar embeddings para cada hecho
            for (fact in knowledgeFacts) {
                try {
                    val text = "${fact.subject} ${fact.predicate} ${fact.`object`}"
                    val embedding = embeddingGenerator.embed(text)

                    val entity = SemanticFactEntity(
                        id = "sem_${fact.id}",
                        factId = fact.id,
                        text = text,
                        embedding = embedding.toEmbeddingBytes(),
                        category = fact.category,
                        confidence = fact.confidence,
                        timestamp = fact.timestamp,
                    )

                    semanticFactDao.insert(entity)
                    vectorStore.add(
                        id = entity.id,
                        vector = embedding,
                        metadata = VectorMetadata(
                            factId = fact.id,
                            text = text,
                            category = fact.category,
                            confidence = fact.confidence,
                            timestamp = fact.timestamp,
                        ),
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to rebuild embedding for fact ${fact.id}", e)
                }
            }

            initialized = true
            Log.i(TAG, "Rebuilt ${knowledgeFacts.size} embeddings successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rebuild embeddings", e)
        }
    }
}
