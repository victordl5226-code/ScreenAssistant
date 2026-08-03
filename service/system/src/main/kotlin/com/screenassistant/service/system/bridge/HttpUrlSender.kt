package com.screenassistant.service.system.bridge

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cáscara JDK de F3 (ADR-015): HttpURLConnection del JDK (el stack NO tiene
 * OkHttp/Retrofit — cero dependencias nuevas, consistente con ADR-014/T8).
 * ~15 líneas, sin unit tests (política "Notas de proceso": validación manual P5).
 *
 * Fail-soft: cualquier fallo → false + log; el broadcast/TTS locales nunca se
 * condicionan a la red. Sin reintento (WorkManager = YAGNI en ecosistema 1
 * dispositivo, documentado). El timeout 5 s connect + 5 s read limita el coste
 * de la corrutina hermana del goAsync.
 */
class HttpUrlSender : UrlSender {
    override suspend fun enviar(url: String): Boolean = try {
        val conexion = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 5_000
        }
        val ok = conexion.responseCode in 200..299
        conexion.disconnect()
        ok
    } catch (e: Exception) {
        Log.w(TAG, "Error enviando URL callback de AutoRemote: ${e.message}")
        false
    }

    private companion object {
        const val TAG = "HttpUrlSender"
    }
}
