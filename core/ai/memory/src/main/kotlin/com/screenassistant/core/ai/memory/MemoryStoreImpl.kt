package com.screenassistant.core.ai.memory

import android.util.Log
import com.screenassistant.core.domain.repository.ai.FactCategory
import com.screenassistant.core.domain.repository.ai.KnowledgeFact
import com.screenassistant.core.domain.repository.ai.MemoryEntry
import com.screenassistant.core.domain.repository.ai.MemoryRole
import com.screenassistant.core.domain.repository.ai.MemoryStore
import com.screenassistant.core.domain.repository.ai.MemoryType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [MemoryStore] usando Room DAOs.
 *
 * - Conversaciones → [LongTermMemoryDao] (tabla long_term_memory)
 * - Hechos → [KnowledgeFactDao] (tabla knowledge_facts)
 *
 * Búsqueda unifica ambas fuentes y mergea resultados.
 */
@Singleton
class MemoryStoreImpl @Inject constructor(
    private val longTermMemoryDao: LongTermMemoryDao,
    private val knowledgeFactDao: KnowledgeFactDao,
) : MemoryStore {

    override suspend fun save(entry: MemoryEntry) {
        Log.d("MemoryStore", "Guardando memoria: ${entry.content.take(30)}...")
        val entity = LongTermMemoryEntity(
            id = entry.id,
            role = entry.role.name,
            content = entry.content,
            timestamp = entry.timestamp,
            type = entry.type.name,
        )
        longTermMemoryDao.insert(entity)
    }

    override suspend fun getRecent(limit: Int): List<MemoryEntry> {
        return longTermMemoryDao.getRecent(limit).map { entity ->
            MemoryEntry(
                id = entity.id,
                role = runCatching { MemoryRole.valueOf(entity.role) }.getOrDefault(MemoryRole.USER),
                content = entity.content,
                timestamp = entity.timestamp,
                type = runCatching { MemoryType.valueOf(entity.type) }.getOrDefault(MemoryType.CONVERSATION),
            )
        }
    }

    override suspend fun search(query: String, limit: Int): List<MemoryEntry> {
        val memoryResults = longTermMemoryDao.search(query, limit)
        val factResults = knowledgeFactDao.searchByQuery(query, limit)
        
        Log.d("MemoryStore", "Búsqueda para '$query': ${memoryResults.size} memorias, ${factResults.size} hechos")

        val memoryEntries = memoryResults.map { entity ->
            MemoryEntry(
                id = entity.id,
                role = runCatching { MemoryRole.valueOf(entity.role) }.getOrDefault(MemoryRole.USER),
                content = entity.content,
                timestamp = entity.timestamp,
                type = runCatching { MemoryType.valueOf(entity.type) }.getOrDefault(MemoryType.CONVERSATION),
            )
        }

        val factEntries = factResults.map { entity ->
            MemoryEntry(
                id = entity.id,
                role = MemoryRole.USER,
                content = "${entity.subject}: ${entity.predicate} = ${entity.`object`}",
                timestamp = entity.timestamp,
                type = MemoryType.FACT,
            )
        }

        return (memoryEntries + factEntries)
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    override suspend fun count(): Int {
        return longTermMemoryDao.count() + knowledgeFactDao.count()
    }

    override suspend fun deleteOlderThan(timestampMs: Long): Int {
        return longTermMemoryDao.deleteOlderThan(timestampMs)
    }

    override suspend fun saveFact(fact: KnowledgeFact) {
        val entity = KnowledgeFactEntity(
            id = fact.id,
            subject = fact.subject,
            predicate = fact.predicate,
            `object` = fact.`object`,
            category = fact.category.name,
            confidence = fact.confidence,
            timestamp = fact.timestamp,
            source = fact.source,
        )
        knowledgeFactDao.insert(entity)
    }

    override suspend fun getRecentFacts(limit: Int): List<KnowledgeFact> {
        return knowledgeFactDao.getRecent(limit).map { entity ->
            KnowledgeFact(
                id = entity.id,
                category = runCatching { FactCategory.valueOf(entity.category) }.getOrDefault(FactCategory.ROUTINE),
                subject = entity.subject,
                predicate = entity.predicate,
                `object` = entity.`object`,
                confidence = entity.confidence,
                timestamp = entity.timestamp,
                source = entity.source,
            )
        }
    }

    override suspend fun clear() {
        longTermMemoryDao.clear()
        knowledgeFactDao.clear()
    }
}
