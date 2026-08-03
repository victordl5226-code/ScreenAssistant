package com.screenassistant.service.system.bridge

import com.screenassistant.core.data.util.PuenteConfigStore
import com.screenassistant.core.domain.di.IoDispatcher
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Orquestador PURA y testeable de F3 (ADR-015 v1.2): decide + lanza en IO. NO
 * bloquea el finish del receiver (fire-and-forget best-effort, corrutina HERMANA
 * del bloque goAsync: finish() no la espera ni la cancela; pérdida por muerte de
 * proceso = riesgo aceptado, mismo que el TTS actual).
 *
 * Decisión de canal (sección 4.3 v1.2): la URL se envía SOLO si
 * `config.enviarRespuestaURL == true` (flag explícito, default OFF — cero
 * llamadas URL sorpresa) Y `autoRemoteKey` no-blank — ADEMÁS del broadcast/TTS,
 * nunca en lugar. SIN origen: la identidad del emisor es inobtenible sin opt-in
 * (veto H1, ADR-015 §0.2) — la antigua condición
 * `origen.paquete.startsWith("com.bighugegiraffe")` era una decisión sobre un
 * input que la producción nunca produce (ADR-015 §4.1). El flag y la key son
 * independientes del emisor real.
 */
class AutoRemoteUrlCallback @Inject constructor(
    private val configStore: PuenteConfigStore,
    private val urlSender: UrlSender,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    fun responderSiAplica(respuestaJson: String) {
        val config = configStore.cargar()
        if (!config.enviarRespuestaURL) return
        val key = config.autoRemoteKey
        if (key.isBlank()) return
        val url = AutoRemoteUrlBuilder.construir(key, respuestaJson) ?: return
        CoroutineScope(io).launch {
            try {
                urlSender.enviar(url)
            } catch (e: CancellationException) {
                throw e // Convención del repo (handler/receiver): la cancelación se propaga, nunca se engulle
            } catch (e: Exception) {
                // Fail-soft defensivo: el contrato del sender es no lanzar (false
                // interno); si una implementación lo viola, la corrutina hermana
                // muere silenciosa — la URL nunca rompe el flujo local.
            }
        }
    }
}
