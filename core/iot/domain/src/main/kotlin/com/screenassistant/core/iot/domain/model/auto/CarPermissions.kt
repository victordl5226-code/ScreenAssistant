package com.screenassistant.core.iot.domain.model.auto

import kotlinx.serialization.Serializable

/**
 * Permisos relacionados con el coche.
 */
@Serializable
data class CarPermissions(
    val microphone: Boolean = false,
    val location: Boolean = false,
    val contacts: Boolean = false,
    val sms: Boolean = false,
    val callLogs: Boolean = false,
    val phoneState: Boolean = false,
    val vehicleData: Boolean = false, // CarInfo API
    val isPassenger: Boolean = false, // Detección de pasajero (UWB/cámara)
) {
    /** Verifica si tiene todos los permisos críticos para asistente de voz */
    val hasVoiceAssistantPermissions: Boolean
        get() = microphone && location
}