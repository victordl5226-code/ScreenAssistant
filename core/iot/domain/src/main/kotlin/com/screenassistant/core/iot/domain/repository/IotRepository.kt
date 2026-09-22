package com.screenassistant.core.iot.domain.repository

import com.screenassistant.core.iot.domain.model.auto.CarAppState
import com.screenassistant.core.iot.domain.model.auto.CarPermissions
import com.screenassistant.core.iot.domain.model.auto.VoiceCommand
import com.screenassistant.core.iot.domain.model.auto.VoiceResponse
import com.screenassistant.core.iot.domain.model.smartHome.AutomationRule
import com.screenassistant.core.iot.domain.model.smartHome.HAServiceCall
import com.screenassistant.core.iot.domain.model.smartHome.HomeAssistantEntity
import com.screenassistant.core.iot.domain.model.smartHome.MatterCommand
import com.screenassistant.core.iot.domain.model.smartHome.MatterDevice
import com.screenassistant.core.iot.domain.model.smartHome.MatterScene
import com.screenassistant.core.iot.domain.model.smartHome.SceneActivationResult
import com.screenassistant.core.iot.domain.model.wearables.HealthMetrics
import com.screenassistant.core.iot.domain.model.wearables.WearComplicationData
import com.screenassistant.core.iot.domain.model.wearables.WearTileState
import com.screenassistant.core.iot.domain.repository.CarPermission
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio unificado de alto nivel para funcionalidades IoT.
 *
 * Esta interfaz agrupa las operaciones comunes entre todos los dominios IoT
 * (Smart Home, Wearables, Auto) y sirve como fachada principal para los UseCases.
 * Las implementaciones concretas delegan a los repositorios especializados.
 */
interface IotRepository {
    // ===== Smart Home =====

    /**
     * Obtiene todos los dispositivos Matter conocidos.
     */
    fun getMatterDevices(): Flow<List<MatterDevice>>

    /**
     * Obtiene un dispositivo Matter por ID.
     */
    suspend fun getMatterDevice(deviceId: String): MatterDevice?

    /**
     * Envía un comando a un dispositivo Matter.
     */
    suspend fun sendMatterCommand(command: MatterCommand): Boolean

    /**
     * Obtiene todas las escenas Matter.
     */
    fun getMatterScenes(): Flow<List<MatterScene>>

    /**
     * Activa una escena Matter.
     */
    suspend fun activateMatterScene(sceneId: String): SceneActivationResult

    /**
     * Obtiene entidades de Home Assistant.
     */
    fun getHomeAssistantEntities(): Flow<List<HomeAssistantEntity>>

    /**
     * Obtiene una entidad de Home Assistant por ID.
     */
    suspend fun getHomeAssistantEntity(entityId: String): HomeAssistantEntity?

    /**
     * Llama a un servicio de Home Assistant.
     */
    suspend fun callHomeAssistantService(call: HAServiceCall): Boolean

    /**
     * Obtiene reglas de automatización.
     */
    fun getAutomationRules(): Flow<List<AutomationRule>>

    /**
     * Guarda/actualiza una regla de automatización.
     */
    suspend fun saveAutomationRule(rule: AutomationRule): Boolean

    /**
     * Elimina una regla de automatización.
     */
    suspend fun deleteAutomationRule(ruleId: String): Boolean

    /**
     * Habilita/deshabilita una regla de automatización.
     */
    suspend fun setAutomationRuleEnabled(ruleId: String, enabled: Boolean): Boolean

    // ===== Wearables =====

    /**
     * Obtiene métricas de salud actuales.
     */
    fun getHealthMetrics(): Flow<HealthMetrics>

    /**
     * Fuerza sincronización de datos de salud.
     */
    suspend fun syncHealthMetrics(): HealthMetrics

    /**
     * Obtiene estado del Tile de Wear OS.
     */
    fun getWearTileState(tileId: String): Flow<WearTileState>

    /**
     * Actualiza estado del Tile.
     */
    suspend fun updateWearTileState(state: WearTileState): Boolean

    /**
     * Obtiene datos para complicaciones.
     */
    fun getWearComplicationData(complicationId: String): Flow<WearComplicationData>

    /**
     * Actualiza datos de complicación.
     */
    suspend fun updateWearComplicationData(data: WearComplicationData): Boolean

    // ===== Auto =====

    /**
     * Obtiene estado actual de la app en el coche.
     */
    fun getCarAppState(): Flow<CarAppState>

    /**
     * Procesa un comando de voz en contexto automotriz.
     */
    suspend fun processVoiceCommand(command: VoiceCommand): VoiceResponse

    /**
     * Obtiene permisos del coche.
     */
    suspend fun getCarPermissions(): CarPermissions

    /**
     * Solicita permisos del coche.
     */
    suspend fun requestCarPermissions(permissions: List<CarPermission>): CarPermissions

    /**
     * Obtiene métricas de salud actuales (snapshot para syncNow).
     */
    suspend fun getCurrentHealthMetrics(): HealthMetrics

    /**
     * Obtiene estado del coche actual (snapshot para syncNow).
     */
    suspend fun getCurrentCarAppState(): CarAppState
}