package com.screenassistant.core.iot.data.repository

import com.screenassistant.core.iot.domain.model.auto.CarAppState
import com.screenassistant.core.iot.domain.model.smartHome.HomeAssistantEntity
import com.screenassistant.core.iot.domain.model.smartHome.MatterDevice
import com.screenassistant.core.iot.domain.model.wearables.HealthMetrics
import kotlinx.datetime.Instant

/**
 * Resultado de sincronización completa de IoT.
 *
 * Separado en su propio archivo para asegurar visibilidad durante
 * el procesamiento KSP (Hilt/Dagger/Room).
 */
data class IotSyncResult(
    val healthMetrics: HealthMetrics,
    val carState: CarAppState,
    val matterDevices: List<MatterDevice>,
    val homeAssistantEntities: List<HomeAssistantEntity>,
    val syncedAt: Instant,
)