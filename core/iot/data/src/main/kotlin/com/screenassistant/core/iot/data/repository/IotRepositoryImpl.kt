package com.screenassistant.core.iot.data.repository

import android.util.Log
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
import com.screenassistant.core.iot.domain.repository.CarAppRepository
import com.screenassistant.core.iot.domain.repository.CarPermission
import com.screenassistant.core.iot.domain.repository.HealthConnectRepository
import com.screenassistant.core.iot.domain.repository.HomeAssistantRepository
import com.screenassistant.core.iot.domain.repository.IotRepository
import com.screenassistant.core.iot.domain.repository.MatterDeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [IotRepository] como fachada.
 *
 * Delega las operaciones a los repositorios especializados:
 * - [MatterDeviceRepository] para dispositivos y escenas Matter
 * - [HealthConnectRepository] para métricas de salud
 * - [CarAppRepository] para estado del vehículo
 * - [HomeAssistantRepository] para entidades HA
 *
 * Esta clase es el punto único de acceso para los UseCases que necesitan
 * datos de múltiples dominios IoT.
 */
@Singleton
class IotRepositoryImpl @Inject constructor(
    private val matterRepository: MatterDeviceRepository,
    private val healthRepository: HealthConnectRepository,
    private val carRepository: CarAppRepository,
    private val homeAssistantRepository: HomeAssistantRepository,
) : IotRepository {

    companion object {
        private const val TAG = "IotRepositoryImpl"
    }

    // ===== Smart Home =====

    override fun getMatterDevices(): Flow<List<MatterDevice>> = matterRepository.observeDevices()

    override suspend fun getMatterDevice(deviceId: String): MatterDevice? =
        matterRepository.getDevice(deviceId)

    override suspend fun sendMatterCommand(command: MatterCommand): Boolean =
        matterRepository.sendCommand(command)

    override fun getMatterScenes(): Flow<List<MatterScene>> = matterRepository.observeScenes()

    override suspend fun activateMatterScene(sceneId: String): SceneActivationResult =
        matterRepository.activateScene(sceneId)

    override fun getHomeAssistantEntities(): Flow<List<HomeAssistantEntity>> =
        homeAssistantRepository.observeEntities()

    override suspend fun getHomeAssistantEntity(entityId: String): HomeAssistantEntity? =
        homeAssistantRepository.getEntity(entityId)

    override suspend fun callHomeAssistantService(call: HAServiceCall): Boolean =
        homeAssistantRepository.callService(call)

    override fun getAutomationRules(): Flow<List<AutomationRule>> =
        homeAssistantRepository.observeAutomations()

    override suspend fun saveAutomationRule(rule: AutomationRule): Boolean =
        homeAssistantRepository.createAutomation(rule)

    override suspend fun deleteAutomationRule(ruleId: String): Boolean =
        homeAssistantRepository.deleteAutomation(ruleId)

    override suspend fun setAutomationRuleEnabled(ruleId: String, enabled: Boolean): Boolean =
        homeAssistantRepository.setAutomationEnabled(ruleId, enabled)

    // ===== Wearables =====

    override fun getHealthMetrics(): Flow<HealthMetrics> = healthRepository.observeHealthMetrics()

    override suspend fun syncHealthMetrics(): HealthMetrics = healthRepository.forceSync()

    override fun getWearTileState(tileId: String): Flow<WearTileState> = emptyFlow()

    override suspend fun updateWearTileState(state: WearTileState): Boolean {
        Log.d(TAG, "WearTile not implemented yet")
        return false
    }

    override fun getWearComplicationData(complicationId: String): Flow<WearComplicationData> = emptyFlow()

    override suspend fun updateWearComplicationData(data: WearComplicationData): Boolean {
        Log.d(TAG, "WearComplication not implemented yet")
        return false
    }

    // ===== Auto =====

    override fun getCarAppState(): Flow<CarAppState> = carRepository.observeCarState()

    override suspend fun processVoiceCommand(command: VoiceCommand): VoiceResponse =
        carRepository.processVoiceCommand(command)

    override suspend fun getCarPermissions(): CarPermissions = carRepository.getCarPermissions()

    override suspend fun requestCarPermissions(permissions: List<CarPermission>): CarPermissions =
        carRepository.requestCarPermissions(permissions)

    override suspend fun getCurrentHealthMetrics(): HealthMetrics = healthRepository.getCurrentHealthMetrics()

    override suspend fun getCurrentCarAppState(): CarAppState = carRepository.getCurrentCarState()
}
