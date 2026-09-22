package com.screenassistant.core.ai.memory

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity para almacenar hechos y preferencias extraídas del usuario.
 *
 * Permite búsqueda por categoría y sujeto para enriquecer prompts.
 */
@Entity(
    tableName = "knowledge_facts",
    indices = [
        Index(value = ["category"]),
        Index(value = ["subject"]),
    ],
)
data class KnowledgeFactEntity(
    @PrimaryKey
    val id: String,
    val category: String,
    val subject: String,
    val predicate: String,
    val `object`: String,
    val confidence: Float,
    val timestamp: Long,
    val source: String = "",
)
