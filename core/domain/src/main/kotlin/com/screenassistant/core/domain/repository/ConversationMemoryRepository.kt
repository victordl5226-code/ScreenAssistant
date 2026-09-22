package com.screenassistant.core.domain.repository

import com.screenassistant.core.domain.model.personality.ConversationTurn
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio de acceso al historial de conversaciones del asistente.
 *
 * Almacena y consulta los [ConversationTurn]s intercambiados entre el usuario
 * y el asistente. Esta memoria conversacional permite al asistente mantener
 * contexto, detectar temas recurrentes y adaptar sus respuestas a lo largo
 * del tiempo.
 */
interface ConversationMemoryRepository {

    /**
     * Flujo reactivo de los turnos de conversación más recientes.
     * Emite una nueva lista cada vez que se guarda un turno.
     *
     * @param limit Número máximo de turnos a incluir (por defecto 20)
     * @return [Flow] que emite la lista de turnos ordenados de más reciente a más antiguo
     */
    fun getRecentTurns(limit: Int = 20): Flow<List<ConversationTurn>>

    /**
     * Guarda un turno de conversación en el historial.
     *
     * @param turn Turno a persistir con el intercambio completo
     */
    suspend fun saveTurn(turn: ConversationTurn)

    /**
     * Busca turnos cuyo contenido contenga la consulta indicada.
     * La búsqueda es case-insensitive y se realiza tanto en el mensaje del
     * usuario como en la respuesta del asistente.
     *
     * @param query Texto a buscar en el contenido de los turnos
     * @return Lista de turnos que coinciden con la búsqueda
     */
    suspend fun searchByContent(query: String): List<ConversationTurn>

    /**
     * Busca turnos filtrados por el tema principal detectado.
     *
     * @param topic Tema a buscar (coincidencia exacta, case-insensitive)
     * @return Lista de turnos asociados al tema indicado
     */
    suspend fun searchByTopic(topic: String): List<ConversationTurn>

    /**
     * Obtiene los turnos de conversación dentro de un rango de tiempo específico.
     *
     * @param startMillis Timestamp de inicio del rango en milisegundos desde epoch
     * @param endMillis Timestamp de fin del rango en milisegundos desde epoch
     * @return Lista de turnos dentro del rango, ordenados cronológicamente
     */
    suspend fun getTurnsBetween(startMillis: Long, endMillis: Long): List<ConversationTurn>

    /**
     * Elimina los turnos de conversación con una antigüedad superior a [olderThanDays] días.
     * Operación de limpieza para gestionar el tamaño del historial.
     *
     * @param olderThanDays Antigüedad máxima en días (por defecto 90)
     */
    suspend fun clearOldTurns(olderThanDays: Int = 90)
}
