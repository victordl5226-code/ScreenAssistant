package com.screenassistant.service.system.bridge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parseo puro best-effort del JSON de respuesta (D6): extrae el `id` plano (eco
 * del extra `id` del broadcast de vuelta, para filtrar en Tasker sin parsear) y
 * el TEXTO a hablar (canal humano TTS). JSON inválido → null/null → no habla.
 *
 * textoDe: `resultado` si `estado == ok`; en el camino de error el texto humano
 * es `mensaje` ("Error: ...", contrato ADR-009/H4) con el código `error` como
 * alternativa si el mensaje no existe — hablar el código (`json_invalido`, etc.)
 * es inútil para un humano (H7: el TTS es el canal humano; el máquina lleva el
 * JSON íntegro en el extra `respuesta`).
 */
object TaskerRespuestaTexto {

    private val json = Json { ignoreUnknownKeys = true }

    fun idDe(respuestaJson: String): String? {
        val objeto = parsear(respuestaJson) ?: return null
        return (objeto["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    fun textoDe(respuestaJson: String): String? {
        val objeto = parsear(respuestaJson) ?: return null
        val estado = (objeto["estado"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (estado == "ok") {
            return (objeto["resultado"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        }
        val mensaje = (objeto["mensaje"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        return mensaje ?: (objeto["error"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    private fun parsear(respuestaJson: String): JsonObject? {
        if (respuestaJson.isBlank()) return null
        return try {
            json.parseToJsonElement(respuestaJson) as? JsonObject
        } catch (e: Exception) {
            null
        }
    }
}
