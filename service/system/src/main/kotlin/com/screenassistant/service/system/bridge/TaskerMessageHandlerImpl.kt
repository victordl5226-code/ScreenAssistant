package com.screenassistant.service.system.bridge

import android.util.Log
import com.screenassistant.core.data.util.PuenteConfigStore
import com.screenassistant.core.domain.bridge.CommandBridge
import com.screenassistant.core.domain.bridge.SystemCommandJsonCodec
import com.screenassistant.core.domain.bridge.SystemCommandJsonCodec.Companion.CODIGO_FALLO_EJECUCION
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handler puro (D6, ADR-014/T6; F1, ADR-015 v1.2): SIN Dispatchers dentro — el
 * receiver lanza en Dispatchers.IO y los tests usan tiempo virtual (runTest +
 * delay > TIMEOUT_MS).
 *
 * - **F1 v1.2 (token compartido del canal)**: ANTES del puente, si
 *   `config.tokenCompartido` es no-blank exige igualdad EXACTA con el extra
 *   recibido (`token?.trim() != tokenConfig.trim()`, case-sensitive). DENY →
 *   respuesta fail-closed `token_invalido` (6 claves, id eco best-effort) SIN
 *   ejecutar el bridge. Token config blank → canal abierto (default, cero
 *   regresión). El token NUNCA se loguea ni se expone en el JSON.
 * - `withTimeoutOrNull(TIMEOUT_MS)` alrededor del puente: si se agota →
 *   `error_timeout` (código NUEVO aditivo, H6) con id eco best-effort del wire.
 * - try/catch defensivo → `fallo_ejecucion`: el invariante "nunca excepciones
 *   hacia Tasker" es contrato de Fase 1 (ADR-013) y este handler lo mantiene.
 * - Devuelve SIEMPRE el JSON de 6 claves del codec (emisor único de respuestas).
 */
class TaskerMessageHandlerImpl @Inject constructor(
    private val bridge: CommandBridge,
    private val codec: SystemCommandJsonCodec = SystemCommandJsonCodec(),
    private val configStore: PuenteConfigStore,
) : TaskerMessageHandler {

    override suspend fun handle(extra: String, token: String?): String {
        val config = configStore.cargar()
        val tokenConfig = config.tokenCompartido
        if (tokenConfig.isNotBlank() && token?.trim() != tokenConfig.trim()) {
            // F1 v1.2 fail-closed (ADR-015 §2.3): el bridge NO se ejecuta; el
            // emisor debe VER el error (invariante H3 intacto). El token NUNCA se
            // loguea ni se expone en el JSON (ADR-015 §2.5).
            return codec.encodeResult(
                "error",
                TaskerPayloadExtractor.idDe(extra),
                null,
                TaskerBridgeContract.CODIGO_TOKEN_INVALIDO,
                TaskerBridgeContract.MSG_TOKEN_INVALIDO,
            )
        }
        return try {
            withTimeoutOrNull(TaskerBridgeContract.TIMEOUT_MS) { bridge.handle(extra) }
                ?: codec.encodeResult(
                    "error",
                    TaskerPayloadExtractor.idDe(extra),
                    null,
                    TaskerBridgeContract.CODIGO_TIMEOUT,
                    "Error: Tiempo de espera agotado.",
                )
        } catch (e: CancellationException) {
            // La cancelación del scope NO es un fallo del puente: se propaga (la
            // convierte el receiver en su propio rethrow; cierre QA/Supervisor, punto 4).
            throw e
        } catch (e: Exception) {
            // Lote 11: el mensaje del wire se HABLA por TTS (H7) → nunca e.message
            // crudo (ADR-009). Detalle a logcat local; `fallo_ejecucion` con el
            // mensaje saneado de fuente única (los guiones ramifican por %error).
            Log.w("TaskerMessageHandler", "Fallo inesperado del puente", e)
            codec.encodeResult(
                "error",
                TaskerPayloadExtractor.idDe(extra),
                null,
                CODIGO_FALLO_EJECUCION,
                TaskerBridgeContract.MSG_FALLO_EJECUCION,
            )
        }
    }
}
