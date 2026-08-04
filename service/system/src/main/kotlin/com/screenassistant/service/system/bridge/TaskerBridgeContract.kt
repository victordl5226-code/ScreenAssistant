package com.screenassistant.service.system.bridge

/**
 * Constantes del contrato de transporte del puente Tasker (ADR-014, Lote 6 — Fase 2;
 * Lote 7 / ADR-015 v1.2 — Fase 3A: token compartido F1 + URL callback F3).
 *
 * Vocabulario del TRANSPORTE Android (actions de broadcast y extras de Intent):
 * el protocolo JSON de 6 claves sigue viviendo en core:domain (ADR-013/H4) y
 * core/domain no conoce este contrato (regla de arquitectura de la sección 1).
 *
 * v1.2 (rediseño tras veto B1/H1): el token es un extra del TRANSPORTE
 * (`intent.getStringExtra("token")`), NO un campo del contrato JSON — el codec de
 * core:domain decodifica con ignoreUnknownKeys=false y tocar la base sealed
 * implicaría 22 overrides + 95 tests de Fase 1 (ADR-015 §2.1). Eliminados con la
 * identidad del emisor (inobtenible, H1): CODIGO_ORIGEN_NO_AUTORIZADO,
 * MSG_ORIGEN_NO_AUTORIZADO y PACKAGE_AUTOREMOTE_PREFIX (ADR-015 §2.6).
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

    // === Fase 3A (Lote 7 / ADR-015 v1.2) ===

    /** v1.2 (F1): autenticación de CANAL (extra del transporte, NO campo del
     *  contrato JSON — ADR-015 §2.1). Lo pone el emisor en Send Intent /
     *  AutoRemote / adb (`--es token '...'`). Se lee ANTES de goAsync (B1). */
    const val EXTRA_TOKEN = "token"

    /** v1.2: código NUEVO aditivo de F1 (precedente error_timeout). El código
     *  `error` del JSON es String libre del TRANSPORTE, no del protocolo → no
     *  toca core/domain. Respuesta fail-closed: el bridge NO se ejecuta. */
    const val CODIGO_TOKEN_INVALIDO = "token_invalido"

    /** ADR-B7 (Lote 8): fallo FUNCIONAL — la acción se ejecutó pero devolvió
     *  "Error: ..." (patrón ADR-009, p.ej. "Error: No encontré la app"). Distinto
     *  del estructural `fallo_ejecucion` (excepción). Lo emite SOLO la rama
     *  Success de SystemCommandBridgeImpl vía ResultadoWire.estadoDe. */
    const val CODIGO_FALLO_ACCION = "fallo_accion"

    /** Patrón ADR-009: "Error: <razón>." — llega al emisor en `mensaje`. */
    const val MSG_TOKEN_INVALIDO = "Error: Token inválido."

    // Referencias documentales (no se usan en v1, D1/D5):
    // net.dinglisch.android.taskerm (Tasker) · com.bighugegiraffe.andromeda (AutoRemote).
}
