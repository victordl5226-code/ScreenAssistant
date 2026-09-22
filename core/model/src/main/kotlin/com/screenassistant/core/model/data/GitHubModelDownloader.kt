package com.screenassistant.core.model.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Descarga modelos desde GitHub Releases con verificación SHA-256.
 *
 * Usa OkHttp síncrono encapsulado en [Dispatchers.IO].
 */
@Singleton
class GitHubModelDownloader @Inject constructor(
    private val client: OkHttpClient
) {
    /**
     * Descarga [url] en [targetFile], verificando SHA-256 contra [expectedSha256].
     *
     * @param onProgress callback con progreso de 0f a 1f.
     * @return [Result.success] si todo OK, [Result.failure] con la excepción.
     */
    suspend fun download(
        url: String,
        targetFile: File,
        expectedSha256: String,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            targetFile.parentFile?.mkdirs()
            val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val body = response.body
                ?: return@withContext Result.failure(Exception("Empty body"))

            val totalBytes = body.contentLength().toFloat()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) onProgress(downloadedBytes / totalBytes)
                    }
                }
            }

            // Verificar SHA-256
            if (!verifySha256(tempFile, expectedSha256)) {
                tempFile.delete()
                return@withContext Result.failure(Exception("SHA-256 mismatch"))
            }

            // Renombrar atómico
            tempFile.renameTo(targetFile)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun verifySha256(file: File, expected: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == expected.lowercase()
    }
}
