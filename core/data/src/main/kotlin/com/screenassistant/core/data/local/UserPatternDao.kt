package com.screenassistant.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO para acceder a los patrones de comportamiento del usuario almacenados en Room.
 *
 * Proporciona operaciones para:
 * - Consultar todos los patrones ordenados por frecuencia (reactivo via [Flow])
 * - Obtener un patrón específico por ID
 * - Buscar patrones frecuentes para sugerencias proactivas
 * - Eliminar patrones antiguos o específicos
 *
 * Los patrones se usan para:
 * - Generar sugerencias proactivas personalizadas
 * - Identificar hábitos recurrentes del usuario
 * - Calcular la confianza de las sugerencias
 */
@Dao
interface UserPatternDao {
    /**
     * Obtiene todos los patrones ordenados por frecuencia descendente.
     *
     * @return Flow con la lista completa de patrones
     */
    @Query("SELECT * FROM user_patterns ORDER BY frequency DESC")
    fun getAllPatterns(): Flow<List<UserPatternEntity>>

    /**
     * Obtiene un patrón específico por su ID.
     *
     * @param id Identificador del patrón
     * @return El patrón si existe, null de lo contrario
     */
    @Query("SELECT * FROM user_patterns WHERE id = :id")
    suspend fun getPattern(id: String): UserPatternEntity?

    /**
     * Inserta o reemplaza un patrón de comportamiento.
     *
     * @param pattern Patrón a insertar (se reemplaza si existe un conflicto por PK)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPattern(pattern: UserPatternEntity)

    /**
     * Obtiene los patrones con una frecuencia mínima especificada.
     * Se usa para generar sugerencias basadas en hábitos establecidos.
     *
     * @param minFrequency Frecuencia mínima para incluir el patrón
     * @return Lista de patrones frecuentes, ordenados por frecuencia descendente
     */
    @Query("SELECT * FROM user_patterns WHERE frequency >= :minFrequency ORDER BY frequency DESC")
    suspend fun getFrequentPatterns(minFrequency: Int): List<UserPatternEntity>

    /**
     * Elimina todos los patrones cuyo último avistamiento sea anterior al timestamp dado.
     * Se usa para limpiar patrones obsoletos que ya no representan el comportamiento actual.
     *
     * @param beforeMillis Timestamp límite (los patrones anteriores se eliminan)
     */
    @Query("DELETE FROM user_patterns WHERE lastSeen < :beforeMillis")
    suspend fun deleteOldPatterns(beforeMillis: Long)

    /**
     * Busca un patrón por acción y contexto completo (día, rango horario, ubicación).
     *
     * Se usa en [findExistingPattern] para detectar si ya existe un patrón que
     * coincida con la acción y el contexto actual, evitando cargar todos los
     * patrones de la tabla.
     *
     * @param action Identificador de la acción
     * @param dayOfWeek Nombre del enum DayOfWeek (ej. "MONDAY")
     * @param hourStart Hora de inicio del rango horario (0-23)
     * @param hourEnd Hora de fin del rango horario (0-23)
     * @param location Nombre del enum LocationType (ej. "HOME")
     * @return El patrón si existe, null de lo contrario
     */
    @Query("""
        SELECT * FROM user_patterns
        WHERE action = :action
        AND dayOfWeek = :dayOfWeek
        AND hourStart = :hourStart
        AND hourEnd = :hourEnd
        AND locationType = :location
        LIMIT 1
    """)
    suspend fun findByActionAndContext(
        action: String,
        dayOfWeek: String,
        hourStart: Int,
        hourEnd: Int,
        location: String
    ): UserPatternEntity?

    /**
     * Elimina un patrón específico por su ID.
     *
     * @param id Identificador del patrón a eliminar
     */
    @Query("DELETE FROM user_patterns WHERE id = :id")
    suspend fun deletePattern(id: String)
}
