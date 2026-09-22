package com.screenassistant.core.ai.local.llama.model

import android.content.Context
import android.os.StatFs
import android.util.Log
import com.screenassistant.core.ai.local.llama.LlamaCppEngine
import com.screenassistant.core.domain.repository.ai.ModelInfo
import com.screenassistant.core.domain.model.ModelState
import com.screenassistant.core.domain.repository.ai.ModelManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestor del ciclo de vida de modelos GGUF para llama.cpp.
 *
 * Implementa ModelManager de core/domain.
 * Maneja descarga, carga, descarga de memoria y verificación de espacio.
 *
 * Directorio de modelos: {filesDir}/llama_models/
 *
 * @param context Contexto de aplicación
 * @param engine Motor LlamaCpp para carga/descarga
 * @param downloader Descargador de modelos
 */
@Singleton
class LlamaModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: LlamaCppEngine,
    private val downloader: ModelDownloader,
) : ModelManager {

    private val modelsDir: File by lazy {
        File(context.filesDir, MODELS_DIR).also { it.mkdirs() }
    }

    @Volatile
    private var selectedModel: ModelInfo = LlamaModelCatalog.DEFAULT

    @Volatile
    private var modelState: ModelState = ModelState.NOT_DOWNLOADED

    /**
     * Lista de modelos disponibles para descargar.
     */
    override val availableModels: List<ModelInfo>
        get() = LlamaModelCatalog.ALL

    /**
     * Obtiene el tamaño del modelo en bytes.
     */
    override fun getModelSizeBytes(): Long {
        val file = getModelFile(selectedModel.id)
        return if (file.exists()) file.length() else selectedModel.sizeBytes
    }

    /**
     * Obtiene el espacio libre necesario para descargar el modelo.
     */
    override fun getRequiredSpaceBytes(): Long {
        return selectedModel.sizeBytes * 2 // 2x para seguridad
    }

    /**
     * Descarga el modelo con progreso.
     *
     * @param progressCallback Callback con progreso (0.0 a 1.0)
     * @return Resultado de la descarga
     */
    override suspend fun downloadModel(
        progressCallback: (Float) -> Unit
    ): Result<Unit> {
        return try {
            val targetFile = getModelFile(selectedModel.id)
            if (targetFile.exists()) {
                Log.i(TAG, "Modelo ya descargado: ${selectedModel.id}")
                return Result.success(Unit)
            }

            modelState = ModelState.DOWNLOADING
            downloader.download(
                url = selectedModel.downloadUrl,
                targetFile = targetFile,
                progressCallback = progressCallback,
            )

            modelState = ModelState.DOWNLOADED
            Log.i(TAG, "Modelo descargado: ${selectedModel.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            modelState = ModelState.ERROR
            Log.e(TAG, "Error al descargar modelo", e)
            Result.failure(e)
        }
    }

    /**
     * Verifica si el modelo está descargado en disco.
     */
    override fun isModelDownloaded(): Boolean {
        return getModelFile(selectedModel.id).exists()
    }

    /**
     * Carga el modelo en memoria.
     *
     * @return true si el modelo se cargó correctamente
     */
    override suspend fun loadModel(): Boolean {
        return try {
            val modelFile = getModelFile(selectedModel.id)
            if (!modelFile.exists()) {
                Log.w(TAG, "Modelo no encontrado: ${selectedModel.id}")
                modelState = ModelState.NOT_DOWNLOADED
                return false
            }

            modelState = ModelState.LOADING
            val success = engine.initialize(modelFile.absolutePath)

            modelState = if (success) ModelState.READY else ModelState.ERROR
            success
        } catch (e: Exception) {
            modelState = ModelState.ERROR
            Log.e(TAG, "Error al cargar modelo", e)
            false
        }
    }

    /**
     * Descarga el modelo de memoria (libera RAM).
     */
    override suspend fun unloadModel() {
        try {
            engine.release()
            modelState = ModelState.DOWNLOADED
            Log.i(TAG, "Modelo descargado de memoria")
        } catch (e: Exception) {
            Log.e(TAG, "Error al descargar modelo", e)
        }
    }

    /**
     * Obtiene información del modelo disponible.
     */
    override fun getModelInfo(): ModelInfo? {
        return selectedModel
    }

    /**
     * Selecciona un modelo por su ID.
     */
    fun selectModel(modelId: String) {
        val model = LlamaModelCatalog.findById(modelId)
        if (model != null) {
            selectedModel = model
            modelState = if (getModelFile(modelId).exists()) {
                ModelState.DOWNLOADED
            } else {
                ModelState.NOT_DOWNLOADED
            }
        }
    }

    /**
     * Obtiene el estado actual del modelo.
     */
    fun getModelState(): ModelState = modelState

    /**
     * Obtiene el espacio disponible en disco.
     */
    fun getAvailableSpaceBytes(): Long {
        val stat = StatFs(modelsDir.absolutePath)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    /**
     * Elimina el archivo del modelo actual del disco.
     */
    override suspend fun deleteModel(): Boolean {
        return deleteModel(selectedModel.id)
    }

    /**
     * Elimina un modelo del disco.
     */
    fun deleteModel(modelId: String): Boolean {
        val file = getModelFile(modelId)
        return if (file.exists()) {
            val deleted = file.delete()
            if (deleted && selectedModel.id == modelId) {
                modelState = ModelState.NOT_DOWNLOADED
            }
            deleted
        } else {
            false
        }
    }

    /**
     * Obtiene la ruta local del modelo.
     */
    fun getLocalModelPath(modelId: String): String? {
        val file = getModelFile(modelId)
        return if (file.exists()) file.absolutePath else null
    }

    private fun getModelFile(modelId: String): File {
        return File(modelsDir, "$modelId.gguf")
    }

    companion object {
        private const val TAG = "LlamaModelManager"
        private const val MODELS_DIR = "llama_models"
    }
}
