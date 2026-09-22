package com.screenassistant.core.iot.data.repository

import android.util.Log
import com.screenassistant.core.data.local.iot.IotDao
import com.screenassistant.core.data.local.iot.MatterSceneEntity
import com.screenassistant.core.iot.data.util.IotClock
import com.screenassistant.core.iot.domain.model.smartHome.MatterCommand
import com.screenassistant.core.iot.domain.model.smartHome.MatterDevice
import com.screenassistant.core.iot.domain.model.smartHome.MatterDeviceType
import com.screenassistant.core.iot.domain.model.smartHome.MatterScene
import com.screenassistant.core.iot.domain.model.smartHome.SceneActivationResult
import com.screenassistant.core.iot.domain.model.smartHome.SceneDeviceState
import com.screenassistant.core.iot.domain.repository.MatterDeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [MatterDeviceRepository] con state management local + Room persistence.
 *
 * Estrategia:
 * 1. Escucha dispositivos Matter via MatterController (cuando disponible)
 * 2. Mantiene cache local en memoria (StateFlow)
 * 3. Persiste escenas en Room (via IotDao)
 * 4. Comandos se envían al controlador cuando disponible
 *
 * @param clock Reloj inyectable para testing
 * @param iotDao DAO para persistencia Room
 */
@Singleton
class MatterDeviceRepositoryImpl @Inject constructor(
    private val clock: IotClock,
    private val iotDao: IotDao,
) : MatterDeviceRepository {

    companion object {
        private const val TAG = "MatterDeviceRepo"
    }

    private val _devices = MutableStateFlow<List<MatterDevice>>(emptyList())
    private val _scenes = MutableStateFlow<List<MatterScene>>(emptyList())
    private val json = Json { ignoreUnknownKeys = true }

    // ── Dispositivos ─────────────────────────────────────────────────────────

    override fun observeDevices(): Flow<List<MatterDevice>> = _devices.asStateFlow()

    override suspend fun getDevice(deviceId: String): MatterDevice? =
        _devices.value.find { it.deviceId == deviceId }

    override suspend fun sendCommand(command: MatterCommand): Boolean {
        val device = getDevice(command.deviceId)
        if (device == null) {
            Log.w(TAG, "Device not found: ${command.deviceId}")
            return false
        }

        Log.d(TAG, "Sending command to ${command.deviceId}: ${command.capability}=${command.value}")

        // Actualizar atributo localmente (optimistic update)
        val updatedAttributes = device.attributes.toMutableMap()
        updatedAttributes[command.capability.attributeKey] = com.screenassistant.core.iot.domain.model.smartHome.MatterAttribute(
            value = command.value,
            lastUpdated = clock.now(),
        )

        val updatedDevice = device.copy(attributes = updatedAttributes)
        _devices.update { list ->
            list.map { if (it.deviceId == command.deviceId) updatedDevice else it }
        }

        // TODO: Enviar al controlador Matter real cuando SDK esté disponible
        return true
    }

    override suspend fun rediscoverDevices(): List<MatterDevice> {
        Log.d(TAG, "Rediscovering Matter devices...")
        // TODO: Implementar con MatterController.discoverDevices() cuando SDK esté disponible
        return _devices.value
    }

    // ── Escenas ──────────────────────────────────────────────────────────────

    override fun observeScenes(): Flow<List<MatterScene>> = _scenes.asStateFlow()

    override suspend fun getScene(sceneId: String): MatterScene? =
        _scenes.value.find { it.sceneId == sceneId }

    override suspend fun activateScene(sceneId: String): SceneActivationResult {
        val scene = getScene(sceneId)
            ?: return SceneActivationResult(
                sceneId = sceneId,
                success = false,
                deviceResults = emptyList(),
                error = "Scene not found: $sceneId",
            )

        Log.d(TAG, "Activating scene: ${scene.name} (${scene.deviceCount} devices)")

        val deviceResults = scene.devices.map { deviceState ->
            val device = getDevice(deviceState.deviceId)
            if (device == null) {
                com.screenassistant.core.iot.domain.model.smartHome.DeviceActivationResult(
                    deviceId = deviceState.deviceId,
                    success = false,
                    error = "Device not found",
                )
            } else if (deviceState.targetAttributes.isEmpty()) {
                com.screenassistant.core.iot.domain.model.smartHome.DeviceActivationResult(
                    deviceId = deviceState.deviceId,
                    success = false,
                    error = "No target attributes",
                )
            } else {
                // Aplicar TODOS los estados objetivo al dispositivo
                var allSuccess = true
                var firstError: String? = null

                for ((attrKey, attrValue) in deviceState.targetAttributes) {
                    val capability = com.screenassistant.core.iot.domain.model.smartHome.MatterCapability.entries
                        .find { it.attributeKey == attrKey }
                    if (capability != null) {
                        val cmd = MatterCommand(
                            deviceId = deviceState.deviceId,
                            capability = capability,
                            value = attrValue,
                        )
                        val success = sendCommand(cmd)
                        if (!success && firstError == null) {
                            allSuccess = false
                            firstError = "Failed to apply $attrKey"
                        }
                    } else if (firstError == null) {
                        allSuccess = false
                        firstError = "Unknown capability: $attrKey"
                    }
                }

                com.screenassistant.core.iot.domain.model.smartHome.DeviceActivationResult(
                    deviceId = deviceState.deviceId,
                    success = allSuccess,
                    error = firstError,
                    targetAttributes = deviceState.targetAttributes,
                )
            }
        }

        val allSuccess = deviceResults.all { it.success }
        Log.d(TAG, "Scene '$sceneId' activation: ${if (allSuccess) "SUCCESS" else "PARTIAL"}")

        return SceneActivationResult(
            sceneId = sceneId,
            success = allSuccess,
            deviceResults = deviceResults,
        )
    }

    override suspend fun createScene(
        name: String,
        icon: String,
        deviceStates: List<MatterDeviceRepository.SceneDeviceState>,
    ): MatterScene {
        val sceneId = "scene_${System.currentTimeMillis()}"
        val now = clock.now()
        val scene = MatterScene(
            sceneId = sceneId,
            name = name,
            icon = icon,
            devices = deviceStates.map { ds ->
                SceneDeviceState(
                    deviceId = ds.deviceId,
                    targetAttributes = ds.targetAttributes.mapValues { it.value.toString() },
                    transitionTimeMs = ds.transitionTimeMs,
                )
            },
            createdAt = now,
            updatedAt = now,
        )

        _scenes.update { it + scene }

        // Persistir en Room
        try {
            val entity = MatterSceneEntity(
                sceneId = scene.sceneId,
                name = scene.name,
                icon = scene.icon,
                devicesJson = json.encodeToString(scene.devices),
                createdAt = now.toEpochMilliseconds(),
                updatedAt = now.toEpochMilliseconds(),
            )
            iotDao.insertMatterScene(entity)
            Log.d(TAG, "Created scene: $name ($sceneId) — persisted to Room")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist scene to Room", e)
        }

        return scene
    }

    override suspend fun updateScene(scene: MatterScene): Boolean {
        val index = _scenes.value.indexOfFirst { it.sceneId == scene.sceneId }
        if (index == -1) {
            Log.w(TAG, "Scene not found for update: ${scene.sceneId}")
            return false
        }
        val now = clock.now()
        _scenes.update { list ->
            list.toMutableList().apply { set(index, scene.copy(updatedAt = now)) }
        }

        // Persistir en Room
        try {
            val entity = MatterSceneEntity(
                sceneId = scene.sceneId,
                name = scene.name,
                icon = scene.icon,
                devicesJson = json.encodeToString(scene.devices),
                createdAt = scene.createdAt.toEpochMilliseconds(),
                updatedAt = now.toEpochMilliseconds(),
            )
            iotDao.updateMatterScene(entity)
            Log.d(TAG, "Updated scene: ${scene.name} — persisted to Room")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist scene update to Room", e)
        }

        return true
    }

    override suspend fun deleteScene(sceneId: String): Boolean {
        val before = _scenes.value.size
        _scenes.update { list -> list.filter { it.sceneId != sceneId } }
        val deleted = _scenes.value.size < before

        if (deleted) {
            // Eliminar de Room
            try {
                iotDao.deleteMatterScene(sceneId)
                Log.d(TAG, "Deleted scene: $sceneId — removed from Room")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete scene from Room", e)
            }
        }

        return deleted
    }

    /**
     * Carga dispositivos en el cache local.
     * Llamado desde IotSyncWorker o desde el init del repo.
     */
    fun loadDevices(devices: List<MatterDevice>) {
        _devices.value = devices
        Log.d(TAG, "Loaded ${devices.size} Matter devices into cache")
    }

    /**
     * Carga escenas en el cache local.
     * También carga desde Room si el cache está vacío.
     */
    fun loadScenes(scenes: List<MatterScene>) {
        _scenes.value = scenes
        Log.d(TAG, "Loaded ${scenes.size} Matter scenes into cache")
    }

    /**
     * Carga escenas desde Room al cache local.
     * Llamado al iniciar para restaurar escenas persistidas.
     */
    suspend fun loadScenesFromRoom() {
        try {
            val entities = iotDao.getAllMatterScenes()
            val scenes = entities.map { entity ->
                MatterScene(
                    sceneId = entity.sceneId,
                    name = entity.name,
                    icon = entity.icon,
                    devices = try {
                        json.decodeFromString<List<SceneDeviceState>>(entity.devicesJson)
                    } catch (e: Exception) {
                        emptyList()
                    },
                    createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(entity.createdAt),
                    updatedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(entity.updatedAt),
                )
            }
            _scenes.value = scenes
            Log.d(TAG, "Loaded ${scenes.size} Matter scenes from Room")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load scenes from Room", e)
        }
    }
}
