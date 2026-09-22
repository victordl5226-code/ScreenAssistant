package com.screenassistant.core.domain.model

/**
 * Paso individual de una secuencia multi-paso.
 * Puede ser un comando de sistema atómico o un marcador de UI.
 */
sealed interface CommandStep {
    /**
     * Paso que ejecuta un SystemCommand (acción del sistema: alarma, llamada, etc.)
     */
    data class Command(val command: SystemCommand) : CommandStep

    /**
     * Paso que es un marcador de UI (HELP, REPEAT, START_MONITORING, etc.)
     * Se ejecuta directamente en el ViewModel sin pasar por SystemAction
     */
    data class Marker(val marker: String) : CommandStep
}