package com.screenassistant.core.domain.repository

import com.screenassistant.core.domain.model.Memory
import kotlinx.coroutines.flow.Flow

interface MemoryRepository {
    fun getAllMemories(): Flow<List<Memory>>
    suspend fun saveMemory(content: String)
    suspend fun clearMemories()
    suspend fun getUserName(memories: List<Memory>): String?
    suspend fun getAssistantName(memories: List<Memory>): String?
}
