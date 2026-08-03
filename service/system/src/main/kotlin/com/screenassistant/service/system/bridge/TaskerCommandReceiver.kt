package com.screenassistant.service.system.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.screenassistant.core.domain.bridge.SystemCommandJsonCodec
import com.screenassistant.core.domain.bridge.SystemCommandJsonCodec.Companion.CODIGO_FALLO_EJECUCION
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Cáscara Android del puente Tasker (D2, ADR-014/T2/T6): patrón EXACTO de
 * AlarmReceiver — @AndroidEntryPoint + goAsync + coroutine en Dispatchers.IO +
 * finally { finish() }. SIN START_FOREGROUND_SERVICE en esta fase (D2): el
 * handler interno garantiza su propio timeout (error_timeout estructurado).
 *
 * - Silencio decidido ANTES de goAsync (D3/H3): sin extras → return sin
 *   respuesta ni TTS (anti-spam, precedente requestCode < 0 de AlarmReceiver).
 * - exported=true + intent-filter (D5): el emisor es otra app; riesgo máximo
 *   documentado (H2: CALL_PHONE/SEND_SMS con permisos concedidos) y `origen`
 *   best-effort viaja al handler (seam allowlist de Fase 3).
 * - H9: try/catch defensivo — si el handler VIOLA su contrato (lanza, cosa que
 *   no debe ocurrir), el emisor recibe `fallo_ejecucion` estructurado vía codec
 *   inyectado; el emisor nunca recibe silencio ante un comando extraído.
 * - CONTRATO DE TIMEOUT (cierre QA/Supervisor): el withTimeoutOrNull(10s) vive en el
 *   handler inyectado (TaskerMessageHandlerImpl), no en la cáscara — una futura
 *   implementación del handler que viole su contrato (colgarse sin devolver ni
 *   lanzar) colgaría la ventana goAsync; el catch H9 cubre excepciones, NO
 *   colgamientos. Riesgo aceptado por diseño y declarado (ADR-014/T2: migración a
 *   FGS specialUse con el MISMO handler si una acción supera ~8 s medidos).
 *   No testeable sin Robolectric → cobertura en la capa pura (Notas de proceso).
 */
@AndroidEntryPoint
class TaskerCommandReceiver : BroadcastReceiver() {

    @Inject lateinit var handler: TaskerMessageHandler
    @Inject lateinit var emitter: TaskerResponseEmitter
    @Inject lateinit var codec: SystemCommandJsonCodec

    override fun onReceive(context: Context, intent: Intent) {
        val payload = TaskerPayloadExtractor.extraer(
            intent.getStringExtra(TaskerBridgeContract.EXTRA_MESSAGE),
            intent.getStringExtra(TaskerBridgeContract.EXTRA_CMD),
        )
        val extra = payload.texto ?: return // silencio deliberado ANTES de goAsync (H3)
        val origen = intent.getPackage()
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val respuesta = handler.handle(extra, origen)
                emitter.emitir(respuesta, hablar = !payload.silencioso)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // H9: violación del contrato del handler → fallo_ejecucion estructurado.
                emitter.emitir(
                    codec.encodeResult(
                        "error",
                        TaskerPayloadExtractor.idDe(extra),
                        null,
                        CODIGO_FALLO_EJECUCION,
                        "Error: ${t.message ?: "Error desconocido"}",
                    ),
                    hablar = !payload.silencioso,
                )
            } finally {
                result.finish()
            }
        }
    }
}
