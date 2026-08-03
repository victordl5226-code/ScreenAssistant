package com.screenassistant.service.system.bridge

import android.content.Context
import android.content.Intent
import com.screenassistant.core.domain.service.TextToSpeech
import javax.inject.Inject

/**
 * Emisor Android fino (D6, ADR-014/T4): broadcast `com.screenassistant.TASKER_RESPONSE`
 * con el MISMO JSON de 6 claves (contrato H4 intacto, sin envoltorio) + extra `id`
 * plano (filtro por variable en Tasker), y TTS humano si `hablar`.
 *
 * El parseo del JSON de salida es `TaskerRespuestaTexto` (puro, best-effort):
 * JSON inválido → id null (extra ausente) y texto null → no habla.
 *
 * RIESGO DE PRIVACIDAD (cierre QA/Supervisor, ADR-014/T4): el broadcast es GLOBAL
 * (sin permiso ni setPackage) → cualquier app del dispositivo puede recibir el extra
 * `respuesta` con el JSON íntegro; `leer_nota`/`leer_notas` exponen el texto de la
 * nota en `resultado`. El control v1 es este documento (riesgo declarado); opciones
 * Fase 3: setPackage configurable (rompería AutoRemote, que re-emite con su propio
 * targetPackage) o permiso signature (inviable: el emisor no comparte nuestra firma).
 *
 * Sin @Inject constructor (mismo precedente que AlarmAction): se provee en
 * AppModule con @ApplicationContext (el Context sin calificador no es inyectable
 * por Hilt en constructores).
 */
class TaskerResponseEmitterImpl @Inject constructor(
    private val context: Context,
    private val tts: TextToSpeech,
) : TaskerResponseEmitter {

    override fun emitir(respuestaJson: String, hablar: Boolean) {
        val intent = Intent(TaskerBridgeContract.ACTION_RESPUESTA).apply {
            putExtra(TaskerBridgeContract.EXTRA_RESPUESTA, respuestaJson)
            // Eco plano del id: solo si existe (putExtra(String, null) removería la
            // clave en Android real — el if es la misma semántica, sin null).
            TaskerRespuestaTexto.idDe(respuestaJson)?.let {
                putExtra(TaskerBridgeContract.EXTRA_RESPUESTA_ID, it)
            }
        }
        context.sendBroadcast(intent)
        if (hablar) {
            TaskerRespuestaTexto.textoDe(respuestaJson)?.let { tts.speak(it) }
        }
    }
}
