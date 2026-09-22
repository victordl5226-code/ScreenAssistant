package com.screenassistant.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO para acceder a los turnos de conversación almacenados en Room.
 *
 * Proporciona operaciones para:
 * - Consultar turnos recientes (reactivo via [Flow])
 * - Buscar por contenido libre o por tema
 * - Obtener turnos en un rango de tiempo específico
 * - Elimizar turnos antiguos para liberar espacio
 *
 * Los resultados de [getRecentTurns] se emiten como [Flow] para que la UI
 * se actualice automáticamente cuando cambien los datos.
 */
@Dao
interface ConversationTurnDao {
    /**
     * Obtiene los turnos más recientes ordenados por timestamp descendente.
     *
     * @param limit Número máximo de turnos a retornar
     * @return Flow con la lista de turnos recientes
     */
    @Query("SELECT * FROM conversation_turns ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTurns(limit: Int): Flow<List<ConversationTurnEntity>>

    /**
     * Inserta o reemplaza un turno de conversación.
     *
     * @param turn Turno a insertar (se reemplaza si existe un conflicto por PK)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTurn(turn: ConversationTurnEntity)

    /**
     * Busca turnos cuyo mensaje del usuario o respuesta del asistente contengan el texto dado.
     * La búsqueda es case-insensitive.
     *
     * @param query Texto a buscar
     * @return Lista de turnos que coinciden, ordenados por timestamp descendente
     */
    @Query(
        "SELECT * FROM conversation_turns " +
            "WHERE userMessage LIKE '%' || :query || '%' " +
            "OR assistantResponse LIKE '%' || :query || '%' " +
            "ORDER BY timestamp DESC"
    )
    suspend fun searchByContent(query: String): List<ConversationTurnEntity>

    /**
     * Busca turnos por tema principal.
     *
     * @param topic Tema a buscar
     * @return Lista de turnos del tema dado, ordenados por timestamp descendente
     */
    @Query("SELECT * FROM conversation_turns WHERE topic = :topic ORDER BY timestamp DESC")
    suspend fun searchByTopic(topic: String): List<ConversationTurnEntity>

    /**
     * Obtiene todos los turnos en un rango de tiempo específico.
     *
     * @param startMillis Timestamp de inicio del rango (inclusivo)
     * @param endMillis Timestamp de fin del rango (inclusivo)
     * @return Lista de turnos en el rango, ordenados por timestamp ascendente
     */
    @Query(
        "SELECT * FROM conversation_turns " +
            "WHERE timestamp BETWEEN :startMillis AND :endMillis " +
            "ORDER BY timestamp ASC"
    )
    suspend fun getTurnsBetween(startMillis: Long, endMillis: Long): List<ConversationTurnEntity>

    /**
     * Elimina todos los turnos anteriores al timestamp dado.
     * Se usa para limpiar conversaciones antiguas y liberar espacio.
     *
     * @param beforeMillis Timestamp límite (los turnos anteriores se eliminan)
     */
    @Query("DELETE FROM conversation_turns WHERE timestamp < :beforeMillis")
    suspend fun deleteOldTurns(beforeMillis: Long)
}
