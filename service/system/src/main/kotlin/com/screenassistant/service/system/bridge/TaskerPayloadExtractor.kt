package com.screenassistant.service.system.bridge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Payload extraído de los extras del Intent de entrada (D3, ADR-014/T3; F1 v1.2).
 *
 * `texto == null` → SILENCIO deliberado: el receiver hace return sin respuesta
 * (un broadcast sin extras no es un comando; H3: el invariante "el emisor nunca
 * espera sin respuesta" aplica desde el handler hacia arriba).
 */
data class PayloadTasker(
    val texto: String?,
    val silencioso: Boolean,
    /** v1.2 (F1): extra de autenticación de canal (`intent.getStringExtra("token")`);
     *  null si el emisor no lo mandó. NO es campo del contrato JSON (ADR-015 §2.1). */
    val token: String? = null,
)

/**
 * Extracción de DECISIÓN pura (JVM, SIN android.* — H8: verificación de code review,
 * no test meta): el vocabulario `message`/`cmd` es del TRANSPORTE Android, no del
 * protocolo → core/domain no se filtra (precedente ADR-003).
 *
 * - Precedencia: `message` (AutoRemote) gana a `cmd` (Tasker). Si `message` está
 *   presente aunque sea vacío, gana: un message vacío es un COMANDO vacío → el
 *   puente responde `json_invalido` estructurado (F0), mejor que un silencio.
 * - Sin límites aquí: el transporte NO trunca ni pre-valida; el 8192 lo decide el
 *   puente (ADR-013 S6/S7, fuente única de límites).
 * - `silencioso`: convención nueva del transporte — si el wire contiene
 *   `"contexto":"silencioso"` (parseo best-effort con try/catch, sin falsos
 *   positivos por subcadena) se suprime el TTS pero NO el broadcast de respuesta.
 */
object TaskerPayloadExtractor {

    private val json = Json { ignoreUnknownKeys = true }

    /** v1.2: tercer parámetro con default — los call sites de Fase 2 y los 19
     *  tests existentes siguen compilando sin cambios (ADR-015 §2.2). */
    fun extraer(message: String?, cmd: String?, token: String? = null): PayloadTasker {
        val texto = message ?: cmd ?: return PayloadTasker(null, false)
        return PayloadTasker(texto, esContextoSilencioso(texto), token)
    }

    /** id best-effort del wire crudo (H6): string válido si existe, si no null. */
    fun idDe(extra: String): String? {
        if (extra.isBlank()) return null
        return try {
            val objeto = json.parseToJsonElement(extra) as? JsonObject ?: return null
            (objeto["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        } catch (e: Exception) {
            null
        }
    }

    private fun esContextoSilencioso(extra: String): Boolean {
        if (extra.isBlank()) return false
        return try {
            val objeto = json.parseToJsonElement(extra) as? JsonObject ?: return false
            val contexto = objeto["contexto"] as? JsonPrimitive ?: return false
            contexto.isString &&
                contexto.content == TaskerBridgeContract.CONTEXTO_SILENCIOSO
        } catch (e: Exception) {
            false
        }
    }
}
