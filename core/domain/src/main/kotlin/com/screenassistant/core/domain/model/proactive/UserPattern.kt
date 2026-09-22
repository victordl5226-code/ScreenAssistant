package com.screenassistant.core.domain.model.proactive

import java.time.DayOfWeek
import java.time.Instant
import java.util.UUID

/**
 * Contexto en el que se observó un patrón de uso del usuario.
 * Se usa para correlacionar acciones recurrentes con condiciones del entorno.
 *
 * @property dayOfWeek Día de la semana del patrón
 * @property hourStart Hora de inicio del rango horario del patrón
 * @property hourEnd Hora de fin del rango horario del patrón
 * @property location Tipo de ubicación donde se observó el patrón
 * @property screenApp Paquete de la app que estaba en primer plano (null si no aplica)
 */
data class PatternContext(
    val dayOfWeek: DayOfWeek,
    val hourStart: Int,
    val hourEnd: Int,
    val location: LocationType,
    val screenApp: String? = null
) {
    init {
        require(hourStart in 0..23) { "hourStart debe estar entre 0 y 23, recibido: $hourStart" }
        require(hourEnd in 0..23) { "hourEnd debe estar entre 0 y 23, recibido: $hourEnd" }
    }
}

/**
 * Patrón de comportamiento del usuario detectado por el sistema de aprendizaje.
 * Representa una acción recurrente observada en un contexto específico.
 *
 * Los patrones se usan para mejorar las sugerencias proactivas: cuanto mayor
 * sea la [confidence], más probable es que el usuario desee la acción sugerida.
 *
 * @property id Identificador único del patrón
 * @property action Identificador de la acción asociada al patrón
 * @property context Contexto en el que se observó el patrón
 * @property frequency Número de veces que se ha observado este patrón
 * @property lastSeen Timestamp del último avistamiento en milisegundos desde epoch
 * @property confidence Nivel de confianza en el patrón (0.0 a 1.0)
 */
data class UserPattern(
    val id: String = UUID.randomUUID().toString(),
    val action: String,
    val context: PatternContext,
    val frequency: Int,
    val lastSeen: Instant,
    val confidence: Float
) {
    init {
        require(frequency >= 0) { "La frecuencia no puede ser negativa, recibido: $frequency" }
        require(confidence in 0f..1f) { "La confianza debe estar entre 0.0 y 1.0, recibido: $confidence" }
    }

    companion object {
        /**
         * Crea un patrón nuevo con un avistamiento inicial.
         *
         * @param action Identificador de la acción
         * @param context Contexto del patrón
         * @param timestamp Timestamp del avistamiento
         * @return UserPattern con frequency=1 y confidence=0.1
         */
        fun initial(
            action: String,
            context: PatternContext,
            timestamp: Instant = Instant.now()
        ): UserPattern = UserPattern(
            action = action,
            context = context,
            frequency = 1,
            lastSeen = timestamp,
            confidence = 0.1f
        )

        /**
         * Umbral mínimo de confianza para que un patrón sea considerado "establecido".
         * Patrones por debajo de este umbral se consideran exploratorios.
         */
        const val ESTABLISHED_THRESHOLD = 0.6f
    }

    /**
     * Indica si el patrón tiene suficiente confianza para ser considerado establecido.
     * Los patrones establecidos se usan para sugerencias proactivas de alta confianza.
     */
    val isEstablished: Boolean
        get() = confidence >= ESTABLISHED_THRESHOLD
}
