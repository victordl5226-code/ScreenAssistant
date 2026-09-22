package com.screenassistant.core.iot.domain.repository

import com.screenassistant.core.iot.domain.model.smartHome.MatterCommand
import com.screenassistant.core.iot.domain.model.smartHome.MatterDevice
import com.screenassistant.core.iot.domain.model.smartHome.MatterScene
import com.screenassistant.core.iot.domain.model.smartHome.SceneActivationResult
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio especializado para dispositivos y escenas Matter.
 *
 * Abstrae la comunicación con el controlador Matter (Thread border router,
 * Matter controller, bridge de Home Assistant, etc.).
 * La implementación concreta maneja el protocolo Matter/Thread.
 */
interface MatterDeviceRepository {
    /**
     * Emite la lista de dispositivos Matter descubiertos.
     * Se actualiza automáticamente cuando hay cambios en la red Matter.
     */
    fun observeDevices(): Flow<List<MatterDevice>>

    /**
     * Obtiene un dispositivo por ID (snapshot actual).
     */
    suspend fun getDevice(deviceId: String): MatterDevice?

    /**
     * Envía un comando a un dispositivo Matter.
     *
     * @return true si el comando fue enviado exitosamente al controlador
     */
    suspend fun sendCommand(command: MatterCommand): Boolean

    /**
     * Fuerza redescubrimiento de dispositivos en la red Matter.
     */
    suspend fun rediscoverDevices(): List<MatterDevice>

    /**
     * Emite las escenas Matter disponibles.
     */
    fun observeScenes(): Flow<List<MatterScene>>

    /**
     * Obtiene una escena por ID.
     */
    suspend fun getScene(sceneId: String): MatterScene?

    /**
     * Activa una escena Matter.
     */
    suspend fun activateScene(sceneId: String): SceneActivationResult

    /**
     * Crea una nueva escena a partir de estados actuales de dispositivos.
     */
    suspend fun createScene(name: String, icon: String, deviceStates: List<SceneDeviceState>): MatterScene

    /**
     * Actualiza una escena existente.
     */
    suspend fun updateScene(scene: MatterScene): Boolean

    /**
     * Elimina una escena.
     */
    suspend fun deleteScene(sceneId: String): Boolean

    /**
     * Estado de dispositivo para creación de escenas.
     */
    data class SceneDeviceState(
        val deviceId: String,
        val targetAttributes: Map<String, Any>,
        val transitionTimeMs: Long? = null,
    )
}