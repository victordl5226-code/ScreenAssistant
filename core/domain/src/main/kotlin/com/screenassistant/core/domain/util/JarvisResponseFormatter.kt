package com.screenassistant.core.domain.util

import com.screenassistant.core.domain.model.AssistantMode

/**
 * Formateador central de respuestas J.A.R.V.I.S. (ADR-028).
 * 
 * Añade el tono y la personalidad de Industrias Stark a las respuestas 
 * de los comandos locales, asegurando que J.A.R.V.I.S. siempre suene 
 * como ella misma, incluso sin internet.
 */
object JarvisResponseFormatter {

    /**
     * Formatea un mensaje de éxito con el tono de J.A.R.V.I.S.
     */
    fun formatSuccess(message: String, userName: String, mode: AssistantMode): String {
        if (mode == AssistantMode.SILENCIOSO) return message
        
        val prefix = when (mode) {
            AssistantMode.CENTINELA -> "De inmediato, $userName. "
            AssistantMode.TACTICO -> "Hecho. "
            else -> ""
        }
        
        return "$prefix$message"
    }

    /**
     * Formatea un mensaje de error con el tono de J.A.R.V.I.S.
     */
    fun formatError(reason: String, userName: String, mode: AssistantMode): String {
        if (mode == AssistantMode.SILENCIOSO) return "Error: $reason"
        
        return "Atención $userName, he detectado un inconveniente: $reason"
    }

    /**
     * Formatea un saludo inicial.
     */
    fun formatGreeting(assistantName: String, userName: String): String {
        return "A su servicio, $userName. Soy $assistantName. Sistemas en línea."
    }

    /**
     * Limpia y formatea mensajes del sistema (hardware).
     */
    fun formatSystemAlert(alert: String, userName: String): String {
        return "$alert, $userName."
    }
}
