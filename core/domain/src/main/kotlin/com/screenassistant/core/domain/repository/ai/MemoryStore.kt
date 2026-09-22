package com.screenassistant.core.domain.repository.ai

/**
 * Almacenamiento persistente de memoria a largo plazo.
 *
 * Implementado con Room en `core:ai:memory`.
 * Almacena conversaciones completas y hechos extraídos.
 */
interface MemoryStore {

    /**
     * Guarda una entrada de memoria.
     */
    suspend fun save(entry: MemoryEntry)

    /**
     * Obtiene las entradas más recientes.
     *
     * @param limit Número máximo de entradas
     * @return Lista de entradas ordenadas por timestamp descendente
     */
    suspend fun getRecent(limit: Int = 20): List<MemoryEntry>

    /**
     * Busca entradas por texto (búsqueda simple, NO semántica).
     *
     * @param query Texto a buscar
     * @param limit Máximo de resultados
     * @return Entradas que contienen el texto
     */
    suspend fun search(query: String, limit: Int = 10): List<MemoryEntry>

    /**
     * Cuenta el total de entradas almacenadas.
     */
    suspend fun count(): Int

    /**
     * Elimina entradas anteriores a una fecha.
     *
     * @param timestampMs Timestamp en milisegundos
     * @return Número de entradas eliminadas
     */
    suspend fun deleteOlderThan(timestampMs: Long): Int

    /**
     * Guarda un hecho de conocimiento del usuario.
     *
     * @param fact Hecho extraído a persistir
     */
    suspend fun saveFact(fact: KnowledgeFact)

    /**
     * Obtiene hechos recientes del usuario.
     *
     * @param limit Número máximo de hechos
     * @return Lista de hechos ordenados por confianza descendente
     */
    suspend fun getRecentFacts(limit: Int = 20): List<KnowledgeFact>

    /**
     * Limpia toda la memoria.
     */
    suspend fun clear()
}

/**
 * Entrada unificada de memoria.
 */
data class MemoryEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: MemoryRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap(),
    val type: MemoryType = MemoryType.CONVERSATION,
)

/**
 * Rol del mensaje en la conversación.
 */
enum class MemoryRole {
    USER,
    ASSISTANT,
    SYSTEM,
}

/**
 * Tipo de memoria.
 */
enum class MemoryType {
    /** Conversación normal */
    CONVERSATION,
    /** Hecho extraído del usuario */
    FACT,
    /** Preferencia del usuario */
    PREFERENCE,
    /** Contexto de pantalla */
    SCREEN_CONTEXT,
}
