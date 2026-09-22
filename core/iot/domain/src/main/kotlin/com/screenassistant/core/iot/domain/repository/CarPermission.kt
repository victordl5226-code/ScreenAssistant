package com.screenassistant.core.iot.domain.repository

/**
 * Permisos específicos del coche (Car Permission API).
 */
enum class CarPermission {
    MICROPHONE,
    LOCATION,
    CONTACTS,
    SMS,
    CALL_LOGS,
    PHONE_STATE,
    VEHICLE_DATA, // CarInfo API (velocidad, combustible, puertas, etc.)
    PASSENGER_DETECTION, // Detección de pasajero para UX completa
}