package com.screenassistant.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO para acceder a las reglas proactivas almacenadas en Room.
 *
 * Proporciona operaciones para:
 * - Consultar todas las reglas (reactivo via [Flow])
 * - Obtener solo las reglas habilitadas para evaluación
 * - Insertar, eliminar y toggle de reglas
 *
 * Las reglas se evalúan periódicamente contra el contexto agregado del dispositivo.
 * Solo las reglas habilitadas ([enabled] = true) se consideran en la evaluación.
 */
@Dao
interface ProactiveRuleDao {
    /**
     * Obtiene todas las reglas ordenadas por prioridad descendente.
     *
     * @return Flow con la lista completa de reglas
     */
    @Query("SELECT * FROM proactive_rules ORDER BY priority DESC")
    fun getAllRules(): Flow<List<ProactiveRuleEntity>>

    /**
     * Obtiene solo las reglas habilitadas, ordenadas por prioridad descendente.
     * Se usa para la evaluación periódica de reglas proactivas.
     *
     * @return Lista de reglas habilitadas
     */
    @Query("SELECT * FROM proactive_rules WHERE enabled = 1 ORDER BY priority DESC")
    suspend fun getEnabledRules(): List<ProactiveRuleEntity>

    /**
     * Obtiene una regla específica por su ID.
     *
     * @param id Identificador de la regla
     * @return La regla si existe, null de lo contrario
     */
    @Query("SELECT * FROM proactive_rules WHERE id = :id")
    suspend fun getRule(id: String): ProactiveRuleEntity?

    /**
     * Inserta o reemplaza una regla proactiva.
     *
     * @param rule Regla a insertar (se reemplaza si existe un conflicto por PK)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: ProactiveRuleEntity)

    /**
     * Elimina una regla específica por su ID.
     *
     * @param id Identificador de la regla a eliminar
     */
    @Query("DELETE FROM proactive_rules WHERE id = :id")
    suspend fun deleteRule(id: String)

    /**
     * Habilita o deshabilita una regla específica.
     * Se usa para activar/desactivar reglas sin eliminarlas.
     *
     * @param id Identificador de la regla
     * @param enabled Nuevo estado de la regla
     */
    @Query("UPDATE proactive_rules SET enabled = :enabled WHERE id = :id")
    suspend fun toggleRule(id: String, enabled: Boolean)
}
