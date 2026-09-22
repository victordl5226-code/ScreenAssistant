package com.screenassistant.core.domain.model.personality

import com.screenassistant.core.domain.model.proactive.PatternContext
import java.time.ZoneId
import java.util.UUID

/**
 * Estilo de comunicación preferido por el usuario.
 * Determina el nivel de formalidad y estructura de las respuestas.
 */
enum class CommunicationStyle {
    /** Comunicación formal: estructurada, educada, sin jerga */
    FORMAL,
    /** Comunicación casual: relajada, coloquial, directa */
    CASUAL,
    /** Comunicación mixta: adapta el estilo según el contexto */
    MIXED
}

/**
 * Rutina detectada o configurada del usuario.
 * Representa una actividad recurrente en un patrón de tiempo y ubicación.
 *
 * @property name Nombre descriptivo de la rutina
 * @property pattern Patrón temporal de la rutina
 * @property action Acción o comportamiento asociado a la rutina
 * @property confidence Nivel de confianza en la detección de la rutina (0.0 a 1.0)
 */
data class UserRoutine(
    val name: String,
    val pattern: PatternContext,
    val action: String,
    val confidence: Float = 0.5f
) {
    init {
        require(name.isNotBlank()) { "El nombre de la rutina no puede estar vacío" }
        require(action.isNotBlank()) { "La acción de la rutina no puede estar vacía" }
        require(confidence in 0f..1f) { "La confianza debe estar entre 0.0 y 1.0, recibido: $confidence" }
    }
}

/**
 * Perfil completo del usuario que el asistente usa para personalizar su comportamiento.
 * Incluye información demográfica, preferencias de comunicación, intereses y rutinas.
 *
 * @property name Nombre real del usuario
 * @property preferredName Nombre preferido para ser llamado (puede ser un apodo)
 * @property communicationStyle Estilo de comunicación preferido
 * @property interests Lista de intereses o temas de interés del usuario
 * @property timezone Zona horaria del usuario
 * @property routines Rutinas detectadas o configuradas del usuario
 */
data class UserProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val preferredName: String? = null,
    val communicationStyle: CommunicationStyle = CommunicationStyle.MIXED,
    val interests: List<String> = emptyList(),
    val timezone: ZoneId = ZoneId.systemDefault(),
    val routines: List<UserRoutine> = emptyList()
) {
    init {
        require(name.isNotBlank()) { "El nombre del usuario no puede estar vacío" }
    }

    /**
     * Devuelve el nombre para saludar al usuario.
     * Usa [preferredName] si está definido, sino usa [name].
     */
    val greetingName: String
        get() = preferredName ?: name

    companion object {
        /**
         * Crea un perfil de usuario básico con solo nombre.
         *
         * @param name Nombre del usuario
         * @return UserProfile con valores por defecto para el resto de campos
         */
        fun basic(name: String): UserProfile = UserProfile(name = name)
    }
}
