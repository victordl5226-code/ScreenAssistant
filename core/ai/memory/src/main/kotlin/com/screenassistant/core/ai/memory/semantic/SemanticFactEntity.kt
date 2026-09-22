package com.screenassistant.core.ai.memory.semantic

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity para almacenar embeddings vectoriales de hechos.
 *
 * Cada hecho en knowledge_facts tiene un embedding asociado
 * que permite búsqueda semántica (no por texto exacto).
 *
 * El embedding se serializa como ByteArray (BLOB):
 * - 384 floats × 4 bytes = 1536 bytes por embedding
 * - 10K hechos = ~15MB en disco
 *
 * ## Migración
 * Esta tabla se crea en la migración v1 → v2 de MemoryDatabase.
 */
@Entity(
    tableName = "semantic_facts",
    indices = [
        Index(value = ["factId"], unique = true),
    ],
)
data class SemanticFactEntity(
    @PrimaryKey
    val id: String,

    /** ID del hecho en knowledge_facts (FK lógico) */
    val factId: String,

    /** Texto embebido (subject + predicate + object) */
    val text: String,

    /** Vector de embedding serializado como BLOB (384 floats = 1536 bytes) */
    val embedding: ByteArray,

    /** Categoría del hecho (PREFERENCE, CONTACT, ROUTINE, etc.) */
    val category: String,

    /** Confianza del hecho (0.0 - 1.0) */
    val confidence: Float,

    /** Timestamp de creación */
    val timestamp: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SemanticFactEntity) return false
        return id == other.id && factId == other.factId
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + factId.hashCode()
        return result
    }

    companion object {
        /**
         * Serializa un FloatArray a ByteArray para almacenamiento en Room.
         */
        fun FloatArray.toEmbeddingBytes(): ByteArray {
            val buffer = java.nio.ByteBuffer.allocate(this.size * 4)
            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (value in this) {
                buffer.putFloat(value)
            }
            return buffer.array()
        }

        /**
         * Deserializa un ByteArray a FloatArray desde Room.
         */
        fun ByteArray.toFloatArray(): FloatArray {
            val buffer = java.nio.ByteBuffer.wrap(this)
            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val floats = FloatArray(this.size / 4)
            for (i in floats.indices) {
                floats[i] = buffer.float
            }
            return floats
        }
    }
}
