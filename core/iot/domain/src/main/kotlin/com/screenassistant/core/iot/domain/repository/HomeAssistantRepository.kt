package com.screenassistant.core.iot.domain.repository

import com.screenassistant.core.iot.domain.model.smartHome.AutomationRule
import com.screenassistant.core.iot.domain.model.smartHome.HAServiceCall
import com.screenassistant.core.iot.domain.model.smartHome.HomeAssistantEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio para integración con Home Assistant.
 *
 * Se comunica con la API REST y WebSocket de Home Assistant
 * para obtener estados de entidades, llamar servicios y gestionar automatizaciones.
 */
interface HomeAssistantRepository {
    /**
     * Emite todas las entidades de Home Assistant.
     * Se actualiza via WebSocket en tiempo real.
     */
    fun observeEntities(): Flow<List<HomeAssistantEntity>>

    /**
     * Obtiene una entidad por entity_id (snapshot).
     */
    suspend fun getEntity(entityId: String): HomeAssistantEntity?

    /**
     * Obtiene entidades filtradas por dominio.
     */
    fun observeEntitiesByDomain(domain: String): Flow<List<HomeAssistantEntity>>

    /**
     * Llama a un servicio de Home Assistant.
     *
     * @param call Llamada al servicio (dominio, servicio, target, data)
     * @return true si la llamada fue exitosa
     */
    suspend fun callService(call: HAServiceCall): Boolean

    /**
     * Llama a múltiples servicios en lote.
     */
    suspend fun callServices(calls: List<HAServiceCall>): List<Boolean>

    /**
     * Verifica conexión con Home Assistant.
     */
    suspend fun checkConnection(): Boolean

    /**
     * Obtiene la configuración de Home Assistant (versión, zona horaria, etc.).
     */
    suspend fun getConfig(): Map<String, Any>

    // ===== Automatizaciones =====

    /**
     * Emite todas las automatizaciones.
     */
    fun observeAutomations(): Flow<List<AutomationRule>>

    /**
     * Obtiene una automatización por ID.
     */
    suspend fun getAutomation(automationId: String): AutomationRule?

    /**
     * Crea una nueva automatización.
     */
    suspend fun createAutomation(rule: AutomationRule): Boolean

    /**
     * Actualiza una automatización existente.
     */
    suspend fun updateAutomation(rule: AutomationRule): Boolean

    /**
     * Elimina una automatización.
     */
    suspend fun deleteAutomation(automationId: String): Boolean

    /**
     * Habilita/deshabilita una automatización.
     */
    suspend fun setAutomationEnabled(automationId: String, enabled: Boolean): Boolean

    /**
     * Dispara manualmente una automatización (trigger).
     */
    suspend fun triggerAutomation(automationId: String): Boolean

    /**
     * Obtiene el trace de ejecución de una automatización.
     */
    suspend fun getAutomationTrace(automationId: String): String?

    // ===== Estados y Disparadores =====

    /**
     * Obtiene historial de estados de una entidad.
     */
    suspend fun getEntityHistory(
        entityId: String,
        startTime: Long,
        endTime: Long? = null,
    ): List<HomeAssistantEntity>

    /**
     * Obtiene el estado actual de múltiples entidades.
     */
    suspend fun getStates(entityIds: List<String>): Map<String, HomeAssistantEntity>

    /**
     * Suscribe a eventos del bus de Home Assistant.
     */
    fun subscribeToEvents(eventType: String): Flow<Map<String, Any>>

    /**
     * Dispara un evento en el bus.
     */
    suspend fun fireEvent(eventType: String, eventData: Map<String, Any>? = null): Boolean
}