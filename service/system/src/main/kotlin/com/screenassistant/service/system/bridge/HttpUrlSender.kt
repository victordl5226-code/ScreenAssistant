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
    override suspend fun enviar(url: String): Boolean {
        var conexion: HttpURLConnection? = null
        return try {
            conexion = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
            }
            conexion.responseCode in 200..299
        } catch (e: Exception) {
            // M7: NUNCA se loguea e.message — MalformedURLException (y otros) incluye
            // la URL COMPLETA, que puede contener la key de AutoRemote (secreto F3).
            // Solo la clase del error (o mensaje fijo), jamás datos del callback.
            Log.w(TAG, "Error enviando URL callback de AutoRemote: ${e.javaClass.simpleName}")
            false
        } finally {
            // M7: disconnect() SIEMPRE, incluso si responseCode lanza (antes: fuga
            // de socket en el camino de error).
            conexion?.disconnect()
        }
    }

    private companion object {
        const val TAG = "HttpUrlSender"
    }
}
