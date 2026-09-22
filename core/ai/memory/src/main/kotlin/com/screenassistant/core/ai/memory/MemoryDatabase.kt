package com.screenassistant.core.ai.memory

import androidx.room.Database
import androidx.room.RoomDatabase
import com.screenassistant.core.ai.memory.semantic.SemanticFactDao
import com.screenassistant.core.ai.memory.semantic.SemanticFactEntity

/**
 * Room Database para el sistema de memoria de IA.
 *
 * Almacena:
 * - Conversaciones completas (long_term_memory)
 * - Hechos y preferencias del usuario (knowledge_facts)
 * - Embeddings vectoriales para búsqueda semántica (semantic_facts)
 *
 * ## Nota
 * La memoria a corto plazo NO usa Room — se maneja in-memory.
 *
 * ## Migración v1 → v2
 * Se agregó la tabla `semantic_facts` para soporte de RAG
 * con embeddings vectoriales (ADR-026).
 */
@Database(
    entities = [
        LongTermMemoryEntity::class,
        KnowledgeFactEntity::class,
        SemanticFactEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun longTermMemoryDao(): LongTermMemoryDao
    abstract fun knowledgeFactDao(): KnowledgeFactDao
    abstract fun semanticFactDao(): SemanticFactDao
}
