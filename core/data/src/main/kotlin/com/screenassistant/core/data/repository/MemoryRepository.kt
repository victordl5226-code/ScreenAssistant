package com.screenassistant.core.data.repository

import com.screenassistant.core.data.local.MemoryDao
import com.screenassistant.core.data.local.MemoryEntity
import com.screenassistant.core.domain.model.Memory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    private val memoryDao: MemoryDao
) : com.screenassistant.core.domain.repository.MemoryRepository {

    override fun getAllMemories(): Flow<List<Memory>> {
        return memoryDao.getAllMemories().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveMemory(content: String) {
        memoryDao.insertMemory(MemoryEntity(content = content))
    }

    override suspend fun clearMemories() {
        memoryDao.clearAll()
    }

    override suspend fun getUserName(memories: List<Memory>): String? {
        return memories.find { it.content.startsWith("Nombre del usuario:", ignoreCase = true) }
            ?.content?.removePrefix("Nombre del usuario:")?.trim()
    }

    override suspend fun getAssistantName(memories: List<Memory>): String? {
        return memories.find { it.content.startsWith("Nombre de la asistente:", ignoreCase = true) }
            ?.content?.removePrefix("Nombre de la asistente:")?.trim()
    }
}
