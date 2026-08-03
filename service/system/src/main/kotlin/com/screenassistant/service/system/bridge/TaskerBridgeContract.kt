package com.screenassistant.service.system.bridge

/**
 * Constantes del contrato de transporte del puente Tasker (ADR-014, Lote 6 — Fase 2).
 *
 * Vocabulario del TRANSPORTE Android (actions de broadcast y extras de Intent):
 * el protocolo JSON de 6 claves sigue viviendo en core:domain (ADR-013/H4) y
 * core/domain no conoce este contrato (regla de arquitectura de la sección 1).
 */
object TaskerBridgeContract {
    const val ACTION_ENTRADA = "com.screenassistant.TASKER_COMMAND"
    const val ACTION_RESPUESTA = "com.screenassistant.TASKER_RESPONSE"
    const val EXTRA_MESSAGE = "message" // AutoRemote (canónico, D1/D3)
    const val EXTRA_CMD = "cmd" // Tasker Send Intent (fallback, D1/D3)
    const val EXTRA_RESPUESTA = "respuesta" // JSON 6 claves (contrato H4, sin envoltorio)
    const val EXTRA_RESPUESTA_ID = "id" // eco plano para filtrar en Tasker sin parsear
    const val TIMEOUT_MS = 10_000L // ventana goAsync (patrón AlarmReceiver, D2)
    const val CODIGO_TIMEOUT = "error_timeout" // código NUEVO aditivo (H6; no rompe Fase 1)
    const val CONTEXTO_SILENCIOSO = "silencioso" // convención reservada del wire (D3)

    // Referencias documentales (no se usan en v1, D1/D5):
    // net.dinglisch.android.taskerm (Tasker) · com.bighugegiraffe.andromeda (AutoRemote).
}
