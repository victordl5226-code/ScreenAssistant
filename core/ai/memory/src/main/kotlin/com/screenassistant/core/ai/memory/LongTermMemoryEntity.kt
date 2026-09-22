package com.screenassistant.core.ai.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity para almacenamiento persistente de memoria a largo plazo.
 *
 * Almacena conversaciones completas y contexto de pantalla.
 * La memoria a corto plazo se maneja in-memory (ArrayDeque).
 */
@Entity(tableName = "long_term_memory")
data class LongTermMemoryEntity(
    @PrimaryKey
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val type: String,
    val metadataJson: String = "{}",
    val sessionId: String = "",
)
