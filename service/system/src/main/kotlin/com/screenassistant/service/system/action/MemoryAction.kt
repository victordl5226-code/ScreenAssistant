package com.screenassistant.service.system.action

import com.screenassistant.core.domain.repository.MemoryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryAction @Inject constructor(
    private val memoryRepository: MemoryRepository
) {
    suspend fun saveMemory(fact: String): String {
        memoryRepository.saveMemory(fact)
        return "Éxito: Entendido, lo recordaré."
    }
}
