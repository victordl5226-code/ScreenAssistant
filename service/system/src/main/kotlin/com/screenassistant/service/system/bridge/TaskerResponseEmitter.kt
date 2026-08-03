package com.screenassistant.service.system.bridge

/**
 * Emite la respuesta al mundo exterior (D4, ADR-014/T4):
 * broadcast de vuelta + TTS.
 *
 * El broadcast `com.screenassistant.TASKER_RESPONSE` se emite SIEMPRE que hubo
 * comando (canal máquina — incluso errores de decode, el guion lo necesita para
 * ver el JSON de error); el TTS (canal humano) solo si `hablar` es true
 * (suprimido por `"contexto":"silencioso"`, D3).
 */
interface TaskerResponseEmitter {
    fun emitir(respuestaJson: String, hablar: Boolean)
}
