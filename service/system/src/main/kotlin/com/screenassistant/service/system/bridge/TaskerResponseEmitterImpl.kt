package com.screenassistant.service.system.bridge

import android.content.Context
import android.content.Intent
import com.screenassistant.core.data.util.PuenteConfigStore
import com.screenassistant.core.domain.service.TextToSpeech
import javax.inject.Inject

/**
 * Emisor Android fino (D6, ADR-014/T4; F2, ADR-015): broadcast `com.screenassistant.TASKER_RESPONSE`
 * con el MISMO JSON de 6 claves (contrato H4 intacto, sin envoltorio) + extra `id`
 * plano (filtro por variable en Tasker), y TTS humano si `hablar`.
 *
 * F2 (privacidad, Lote 7): el target del broadcast viene de la config compartida
 * (PuenteConfigStore) — `intent.setPackage(packageRespuesta)` si no-blank. El
 * default `net.dinglisch.android.taskerm` está materializado en
 * `PuenteConfigStore.cargar()` (H1: el consumidor NUNCA ve null/blank en
 * producción); el null-check defensivo cubre inputs directos (tests), no es el
 * punto de materialización. Corrección ADR-014/T4: AutoRemote NO consume
 * TASKER_RESPONSE (solo re-emite la entrada; su salida es la URL callback de F3)
 * → setPackage no rompe AutoRemote; el riesgo real es limitar otros consumidores
 * legítimos (Tasker Beta, MacroDroid), que el usuario lista como paquete concreto.
 *
 * El parseo del JSON de salida es `TaskerRespuestaTexto` (puro, best-effort):
 * JSON inválido → id null (extra ausente) y texto null → no habla.
 */
class TaskerResponseEmitterImpl @Inject constructor(
    private val context: Context,
    private val tts: TextToSpeech,
    private val configStore: PuenteConfigStore,
) : TaskerResponseEmitter {

    override fun emitir(respuestaJson: String, hablar: Boolean) {
        val intent = Intent(TaskerBridgeContract.ACTION_RESPUESTA).apply {
            putExtra(TaskerBridgeContract.EXTRA_RESPUESTA, respuestaJson)
            // Eco plano del id: solo si existe (putExtra(String, null) removería la
            // clave en Android real — el if es la misma semántica, sin null).
            TaskerRespuestaTexto.idDe(respuestaJson)?.let {
                putExtra(TaskerBridgeContract.EXTRA_RESPUESTA_ID, it)
            }
            // F2: restringe consumidores del broadcast (H1: cargar() YA materializa
            // el default taskerm si ausente/blank; este null-check es defensivo).
            configStore.cargar().packageRespuesta
                ?.takeIf { it.isNotBlank() }
                ?.let { setPackage(it) }
        }
        context.sendBroadcast(intent)
        if (hablar) {
            TaskerRespuestaTexto.textoDe(respuestaJson)?.let { tts.speak(it) }
        }
    }
}
