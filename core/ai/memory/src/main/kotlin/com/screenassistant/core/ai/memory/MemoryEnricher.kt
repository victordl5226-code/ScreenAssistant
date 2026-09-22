package com.screenassistant.core.ai.memory

import com.screenassistant.core.domain.repository.ai.FactExtractor
import com.screenassistant.core.domain.repository.ai.MemoryEntry
import com.screenassistant.core.domain.repository.ai.MemoryRole
import com.screenassistant.core.domain.repository.ai.MemoryStore
import com.screenassistant.core.domain.repository.ai.MemoryTurn
import com.screenassistant.core.domain.repository.ai.MemoryType
import com.screenassistant.core.domain.repository.ai.SemanticMemoryRepository
import com.screenassistant.core.domain.repository.ai.ShortTermMemory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fachada que encapsula las operaciones de memoria para el ViewModel.
 *
 * ## Búsqueda semántica (RAG)
 * Si [SemanticMemoryRepository] está disponible y listo,
 * usa búsqueda por embeddings vectoriales (significado).
 * Si no, cae a búsqueda LIKE (texto exacto) como fallback.
 *
 * ## Almacenamiento
 * Cuando se guarda un hecho nuevo, también se genera su embedding
 * para búsquedas semánticas futuras.
 */
@Singleton
class MemoryEnricher @Inject constructor(
    private val shortTermMemory: ShortTermMemory,
    private val memoryStore: MemoryStore,
    private val factExtractor: FactExtractor,
    private val semanticMemoryRepository: SemanticMemoryRepository,
    private val logger: MemoryLogger,
) {

    companion object {
        private const val TAG = "MemoryEnricher"
    }

    /**
     * Busca contexto relevante para enriquecer el prompt del LLM.
     *
     * Estrategia:
     * 1. Si SemanticMemoryRepository está listo → búsqueda semántica
     * 2. Si no → fallback a búsqueda LIKE (texto exacto)
     */
    suspend fun getRelevantContext(query: String, maxFacts: Int = 3): String {
        return try {
            val facts = if (semanticMemoryRepository.isReady()) {
                // Búsqueda semántica (RAG) — entiende significado
                val results = semanticMemoryRepository.searchSimilar(query, maxFacts)
                results.map { result ->
                    MemoryEntry(
                        id = result.factId,
                        role = MemoryRole.USER,
                        content = result.text,
                        timestamp = result.timestamp,
                        type = MemoryType.FACT,
                    )
                }
            } else {
                // Fallback: búsqueda LIKE (texto exacto)
                memoryStore.search(query, maxFacts)
            }

            if (facts.isNotEmpty()) {
                val factsText = facts.joinToString("; ") { it.content }
                "\n[CONOCIMIENTO DEL USUARIO]: $factsText"
            } else {
                ""
            }
        } catch (e: Exception) {
            logger.w(TAG, "Semantic search failed, falling back to LIKE", e)
            // Fallback a búsqueda LIKE
            try {
                val fallbackFacts = memoryStore.search(query, maxFacts)
                if (fallbackFacts.isNotEmpty()) {
                    val factsText = fallbackFacts.joinToString("; ") { it.content }
                    "\n[CONOCIMIENTO DEL USUARIO]: $factsText"
                } else {
                    ""
                }
            } catch (e2: Exception) {
                logger.w(TAG, "Fallback search also failed", e2)
                ""
            }
        }
    }

    suspend fun persistTurn(userMessage: String, assistantResponse: String) {
        try {
            shortTermMemory.addTurn(MemoryTurn(userMessage, assistantResponse))
        } catch (e: Exception) {
            logger.w(TAG, "ShortTermMemory save failed", e)
        }

        try {
            memoryStore.save(MemoryEntry(role = MemoryRole.USER, content = userMessage, type = MemoryType.CONVERSATION))
            memoryStore.save(MemoryEntry(role = MemoryRole.ASSISTANT, content = assistantResponse, type = MemoryType.CONVERSATION))
        } catch (e: Exception) {
            logger.w(TAG, "LongTermMemory save failed", e)
        }

        try {
            val facts = factExtractor.extractFromMessage(userMessage)
            for (fact in facts) {
                memoryStore.saveFact(fact)

                // NUEVO: generar embedding para búsqueda semántica
                try {
                    if (semanticMemoryRepository.isReady()) {
                        val textToEmbed = "${fact.subject}: ${fact.predicate} = ${fact.`object`}"
                        semanticMemoryRepository.storeFactEmbedding(
                            factId = fact.id,
                            text = textToEmbed,
                            category = fact.category.name,
                        )
                    }
                } catch (e: Exception) {
                    logger.w(TAG, "Embedding generation failed for fact ${fact.id}", e)
                }
            }
        } catch (e: Exception) {
            logger.w(TAG, "Fact extraction failed", e)
        }
    }

    fun getRecentContext(maxTurns: Int = 5): String {
        return try {
            val turns = shortTermMemory.getContext(maxTurns)
            if (turns.isNotEmpty()) {
                val history = turns.joinToString("\n") { turn ->
                    "Usuario: ${turn.userMessage} -> Asistente: ${turn.assistantResponse}"
                }
                "\nHistorial reciente:\n$history"
            } else {
                ""
            }
        } catch (e: Exception) {
            logger.w(TAG, "ShortTermMemory context failed", e)
            ""
        }
    }

    suspend fun getRecentTurns(limit: Int = 10): List<MemoryTurn> {
        return try {
            val entries = memoryStore.getRecent(limit * 2)
            val turns = mutableListOf<MemoryTurn>()
            
            for (i in entries.indices step 2) {
                if (i + 1 < entries.size) {
                    // El más reciente está en entries[0] (Assistant), entries[1] (User)
                    turns.add(MemoryTurn(entries[i+1].content, entries[i].content, entries[i].timestamp))
                }
            }
            turns
        } catch (e: Exception) {
            emptyList()
        }
    }
}
