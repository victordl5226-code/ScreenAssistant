package com.screenassistant.core.ai.local.llama.model

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Descargador de modelos GGUF desde URLs remotas.
 *
 * Usa HttpURLConnection (stdlib Android) para máxima compatibilidad.
 * Soporta reintentos con backoff exponencial.
 *
 * @property maxRetries Número máximo de reintentos
 * @property initialBackoffMs Backoff inicial en milisegundos
 */
class ModelDownloader(
    private val maxRetries: Int = 2,
    private val initialBackoffMs: Long = 1000L,
) {

    /**
     * Descarga un archivo desde una URL.
     *
     * @param url URL de descarga
     * @param targetFile Archivo destino
     * @param progressCallback Callback con progreso (0.0 a 1.0)
     * @throws Exception si la descarga falla después de todos los reintentos
     */
    suspend fun download(
        url: String,
        targetFile: File,
        progressCallback: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        for (attempt in 0..maxRetries) {
            try {
                if (attempt > 0) {
                    val backoff = initialBackoffMs * (1 shl (attempt - 1))
                    Log.i(TAG, "Reintento $attempt/$maxRetries tras ${backoff}ms")
                    Thread.sleep(backoff)
                }

                downloadInternal(url, targetFile, progressCallback)
                return@withContext
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Error en descarga (intento ${attempt + 1}): ${e.message}")
            }
        }

        throw lastException ?: Exception("Descarga fallida después de $maxRetries reintentos")
    }

    /**
     * Descarga interna con manejo de progreso.
     */
    private fun downloadInternal(
        url: String,
        targetFile: File,
        progressCallback: (Float) -> Unit,
    ) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("User-Agent", "ScreenAssistant/1.0")

        try {
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}")
            }

            val contentLength = connection.contentLength.toLong()
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(8192)
            var totalRead = 0L
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead

                if (contentLength > 0) {
                    val progress = (totalRead.toFloat() / contentLength).coerceIn(0f, 1f)
                    progressCallback(progress)
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            Log.i(TAG, "Descarga completada: ${targetFile.absolutePath} (${totalRead} bytes)")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "ModelDownloader"
    }
}
