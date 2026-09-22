package com.screenassistant.core.iot.domain.usecase

import com.screenassistant.core.iot.domain.model.auto.CarAppState
import com.screenassistant.core.iot.domain.model.smartHome.AutomationRule
import com.screenassistant.core.iot.domain.model.smartHome.HomeAssistantEntity
import com.screenassistant.core.iot.domain.model.smartHome.MatterDevice
import com.screenassistant.core.iot.domain.model.smartHome.MatterScene
import com.screenassistant.core.iot.domain.model.wearables.HealthMetrics
import com.screenassistant.core.iot.domain.repository.IotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * UseCase: Sincroniza dispositivos IoT de todas las fuentes.
 *
 * Orquesta la sincronización entre:
 * - Dispositivos Matter (red local Thread/WiFi)
 * - Entidades Home Assistant (REST + WebSocket)
 * - Métricas de salud (Health Connect)
 * - Estado del vehículo (Android Auto)
 *
 * Emite un flujo combinado con el estado agregado de todos los dominios.
 */
class SyncIotDevicesUseCase @Inject constructor(
    private val repository: IotRepository,
) {
    /**
     * Ejecuta sincronización completa y emite estado agregado.
     *
     * @return Flow que emite cuando hay actualizaciones en cualquier dominio
     */
    operator fun invoke(): Flow<IotSyncState> {
        val matterDevicesFlow = repository.getMatterDevices()
        val haEntitiesFlow = repository.getHomeAssistantEntities()
        val healthMetricsFlow = repository.getHealthMetrics()
        val carStateFlow = repository.getCarAppState()
        val scenesFlow = repository.getMatterScenes()
        val automationsFlow = repository.getAutomationRules()

        return combine(
            matterDevicesFlow, haEntitiesFlow, healthMetricsFlow,
            carStateFlow, scenesFlow, automationsFlow
        ) { results: Array<Any> ->
            @Suppress("UNCHECKED_CAST")
            val matterDevices = results[0] as List<MatterDevice>
            @Suppress("UNCHECKED_CAST")
            val haEntities = results[1] as List<HomeAssistantEntity>
            @Suppress("UNCHECKED_CAST")
            val healthMetrics = results[2] as HealthMetrics
            @Suppress("UNCHECKED_CAST")
            val carState = results[3] as CarAppState
            @Suppress("UNCHECKED_CAST")
            val scenes = results[4] as List<MatterScene>
            @Suppress("UNCHECKED_CAST")
            val automations = results[5] as List<AutomationRule>

            IotSyncState(
                matterDevices = matterDevices,
                homeAssistantEntities = haEntities,
                healthMetrics = healthMetrics,
                carAppState = carState,
                scenes = scenes,
                automations = automations,
                lastSyncTimestamp = System.currentTimeMillis(),
            )
        }.distinctUntilChanged()
    }

    /**
     * Fuerza una sincronización inmediata (one-shot).
     */
    suspend fun syncNow(): IotSyncState {
        val matterDevices = repository.getMatterDevices().first()
        val haEntities = repository.getHomeAssistantEntities().first()
        val healthMetrics = repository.getCurrentHealthMetrics()
        val carState = repository.getCurrentCarAppState()
        val scenes = repository.getMatterScenes().first()
        val automations = repository.getAutomationRules().first()

        return IotSyncState(
            matterDevices = matterDevices,
            homeAssistantEntities = haEntities,
            healthMetrics = healthMetrics,
            carAppState = carState,
            scenes = scenes,
            automations = automations,
            lastSyncTimestamp = System.currentTimeMillis(),
        )
    }
}

/**
 * Estado agregado de sincronización IoT.
 */
data class IotSyncState(
    val matterDevices: List<MatterDevice>,
    val homeAssistantEntities: List<HomeAssistantEntity>,
    val healthMetrics: HealthMetrics,
    val carAppState: CarAppState,
    val scenes: List<MatterScene>,
    val automations: List<AutomationRule>,
    val lastSyncTimestamp: Long,
) {
    /** Total de dispositivos Matter online */
    val matterDevicesOnline: Int
        get() = matterDevices.count { it.isOnline }

    /** Total de entidades HA disponibles */
    val haEntitiesAvailable: Int
        get() = homeAssistantEntities.count { it.isAvailable }

    /** Verifica si hay datos de salud recientes */
    val hasRecentHealthData: Boolean
        get() = healthMetrics.hasRecentData()

    /** Verifica si está conectado al coche */
    val isCarConnected: Boolean
        get() = carAppState.isConnected

    /** Resumen textual para logging/UI */
    fun summary(): String {
        return "Matter: $matterDevicesOnline/${matterDevices.size} online | " +
               "HA: $haEntitiesAvailable/${homeAssistantEntities.size} available | " +
               "Health: ${if (hasRecentHealthData) "recent" else "stale"} | " +
               "Car: ${if (isCarConnected) "connected" else "disconnected"}"
    }
}