package com.screenassistant.core.domain.repository.ai

/**
 * Extractor de conocimiento — extrae hechos y preferencias del usuario.
 *
 * Implementado en `core:ai:memory`.
 * Puede usar LLM local para extracción más precisa.
 */
interface FactExtractor {

    /**
     * Extrae hechos de una lista de entradas de memoria.
     *
     * @param entries Entradas de memoria a analizar
     * @return Lista de hechos extraídos
     */
    suspend fun extractFacts(entries: List<MemoryEntry>): List<KnowledgeFact>

    /**
     * Extrae hechos de un solo mensaje del usuario.
     *
     * @param message Mensaje del usuario
     * @return Hechos extraídos (puede estar vacío si no hay información)
     */
    suspend fun extractFromMessage(message: String): List<KnowledgeFact>
}

/**
 * Hecho o preferencia extraído del usuario.
 */
data class KnowledgeFact(
    val id: String = java.util.UUID.randomUUID().toString(),
    val category: FactCategory,
    val subject: String,
    val predicate: String,
    val `object`: String,
    val confidence: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "",
)

/**
 * Categorías de hechos.
 */
enum class FactCategory {
    /** Preferencia personal (color favorito, comida, etc.) */
    PREFERENCE,
    /** Información de contacto (nombre, teléfono, etc.) */
    CONTACT,
    /** Rutina o hábito (hora de dormir, ejercicio, etc.) */
    ROUTINE,
    /** Información de trabajo (proyecto, reuniones, etc.) */
    WORK,
    /** Relación (familia, amigos, etc.) */
    RELATIONSHIP,
    /** Ubicación frecuente (casa, oficina, gimnasio) */
    LOCATION,
    /** Otro */
    OTHER,
}
