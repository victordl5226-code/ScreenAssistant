package com.screenassistant.core.domain.repository

import com.screenassistant.core.domain.model.proactive.ProactiveRule
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio de acceso a las reglas proactivas del sistema.
 *
 * Gestiona el ciclo de vida completo de las [ProactiveRule]: consulta,
 * persistencia, activación/desactivación y eliminación.
 *
 * Las reglas definen qué acciones proactivas se ejecutan cuando se
 * cumplen ciertas condiciones de contexto (batería, tiempo, ubicación, etc.).
 */
interface ProactiveRuleRepository {

    /**
     * Flujo reactivo de todas las reglas almacenadas.
     * Emite una nueva lista cada vez que se modifica cualquier regla.
     *
     * @return [Flow] que emite la lista completa de reglas
     */
    fun getAllRules(): Flow<List<ProactiveRule>>

    /**
     * Obtiene únicamente las reglas habilitadas.
     * Operación de una sola lectura, no reactiva.
     *
     * @return Lista de reglas con [ProactiveRule.enabled] igual a `true`
     */
    suspend fun getEnabledRules(): List<ProactiveRule>

    /**
     * Busca una regla por su identificador único.
     *
     * @param id Identificador de la regla a buscar
     * @return La [ProactiveRule] si existe, o `null` si no se encontró
     */
    suspend fun getRule(id: String): ProactiveRule?

    /**
     * Inserta o actualiza una regla en el repositorio.
     * Si ya existe una regla con el mismo [ProactiveRule.id], se sobrescribe.
     *
     * @param rule Regla a persistir
     */
    suspend fun saveRule(rule: ProactiveRule)

    /**
     * Elimina una regla por su identificador.
     * Si no existe ninguna regla con ese identificador, la operación es un noop.
     *
     * @param id Identificador de la regla a eliminar
     */
    suspend fun deleteRule(id: String)

    /**
     * Activa o desactiva una regla existente.
     *
     * @param id Identificador de la regla a modificar
     * @param enabled `true` para activar, `false` para desactivar
     * @throws IllegalArgumentException si no existe una regla con el id dado
     */
    suspend fun toggleRule(id: String, enabled: Boolean)
}
