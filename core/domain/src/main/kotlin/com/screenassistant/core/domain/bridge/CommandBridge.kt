package com.screenassistant.core.domain.bridge

/**
 * Puente Tasker (ADR-013, H1): entrada única del protocolo JSON.
 *
 * Contrato: recibe el comando crudo del wire y devuelve SIEMPRE el JSON de
 * respuesta del esquema fijo de 6 claves (ver SystemCommandJsonCodec.encodeResult).
 * El puente NUNCA decide por el wire: todo error de decode se traduce en una
 * respuesta estructurada, nunca en una excepción hacia Tasker.
 */
interface CommandBridge {

    /** Procesa un comando JSON del wire y devuelve la respuesta JSON estructurada. */
    suspend fun handle(input: String): String
}
