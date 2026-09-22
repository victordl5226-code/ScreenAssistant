package com.screenassistant.core.ai.local

import android.content.Context
import android.os.StatFs
import android.util.Log
import com.screenassistant.core.domain.model.MlcModelInfo
import com.screenassistant.core.domain.model.ModelState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestor simplificado de descarga y archivos de modelos MLC.
 *
 * Opera directamente con archivos en disco sin WorkManager.
 * Para descargas reales se usa la conexión directa del motor MLC.
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private const val TAG = "ModelDownloadManager"
        private const val MODELS_DIR = "mlc_models"
    }

    private val modelsDir: File by lazy {
        File(context.filesDir, MODELS_DIR).also { it.mkdirs() }
    }

    /**
     * Retorna la ruta local del modelo si existe, o null.
     */
    fun getLocalModelPath(modelId: String): String? {
        val dir = File(modelsDir, modelId)
        return if (dir.exists() && dir.listFiles()?.isNotEmpty() == true) {
            dir.absolutePath
        } else {
            null
        }
    }

    /**
     * Verifica si el modelo está descargado en disco.
     */
    fun isModelDownloaded(modelId: String): Boolean {
        return getLocalModelPath(modelId) != null
    }

    /**
     * Retorna el espacio disponible en bytes en el directorio de modelos.
     */
    fun getAvailableSpaceBytes(): Long {
        val stat = StatFs(modelsDir.absolutePath)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    /**
     * Elimina los archivos de un modelo del disco.
     */
    fun deleteModel(modelId: String): Boolean {
        val dir = File(modelsDir, modelId)
        return if (dir.exists()) {
            dir.deleteRecursively().also { success ->
                if (success) {
                    Log.d(TAG, "Modelo $modelId eliminado")
                } else {
                    Log.w(TAG, "Error eliminando modelo $modelId")
                }
            }
        } else {
            true
        }
    }

    /**
     * Obtiene el estado del modelo basado en la existencia de archivos.
     */
    fun getModelState(modelInfo: MlcModelInfo): ModelState {
        return when {
            modelInfo.state == ModelState.DOWNLOADING -> ModelState.DOWNLOADING
            modelInfo.state == ModelState.LOADING -> ModelState.LOADING
            modelInfo.state == ModelState.READY -> ModelState.READY
            isModelDownloaded(modelInfo.id) -> ModelState.DOWNLOADED
            else -> ModelState.NOT_DOWNLOADED
        }
    }

    /**
     * Formatea bytes a una representación legible (KB, MB, GB).
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }
}
