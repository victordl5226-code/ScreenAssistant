package com.screenassistant.service.system.bridge

import com.screenassistant.core.domain.bridge.CommandBridge
import com.screenassistant.core.domain.bridge.SystemCommandJsonCodec
import com.screenassistant.core.domain.bridge.SystemCommandJsonCodec.Companion.CODIGO_FALLO_EJECUCION
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handler puro (D6, ADR-014/T6): SIN Dispatchers dentro — el receiver lanza en
 * Dispatchers.IO y los tests usan tiempo virtual (runTest + delay > TIMEOUT_MS).
 *
 * - `withTimeoutOrNull(TIMEOUT_MS)` alrededor del puente: si se agota →
 *   `error_timeout` (código NUEVO aditivo, H6) con id eco best-effort del wire.
 * - try/catch defensivo → `fallo_ejecucion`: el invariante "nunca excepciones
 *   hacia Tasker" es contrato de Fase 1 (ADR-013) y este handler lo mantiene.
 * - Devuelve SIEMPRE el JSON de 6 claves del codec (emisor único de respuestas).
 */
class TaskerMessageHandlerImpl @Inject constructor(
    private val bridge: CommandBridge,
    private val codec: SystemCommandJsonCodec = SystemCommandJsonCodec(),
) : TaskerMessageHandler {

    override suspend fun handle(extra: String, origen: String?): String {
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
            codec.encodeResult(
                "error",
                TaskerPayloadExtractor.idDe(extra),
                null,
                CODIGO_FALLO_EJECUCION,
                "Error: ${e.message ?: "Error desconocido"}",
            )
        }
    }
}
