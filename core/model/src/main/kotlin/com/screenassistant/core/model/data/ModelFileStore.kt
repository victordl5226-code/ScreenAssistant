package com.screenassistant.core.model.data

import android.content.Context
import com.screenassistant.core.model.domain.ModelAsset
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestiona el almacenamiento en disco de los modelos descargados.
 *
 * Los modelos se guardan en `<filesDir>/downloaded_models/<modelId>/`.
 */
@Singleton
class ModelFileStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val modelsDir: File by lazy {
        File(context.filesDir, "downloaded_models").also { it.mkdirs() }
    }

    fun getModelDir(model: ModelAsset): File = File(modelsDir, model.id)

    fun getModelFile(model: ModelAsset): File = File(getModelDir(model), model.fileName)

    fun isModelReady(model: ModelAsset): Boolean {
        val file = getModelFile(model)
        return file.exists() && file.length() > 0
    }

    fun getModelSizeBytes(model: ModelAsset): Long = getModelFile(model).length()

    fun deleteModel(model: ModelAsset): Boolean = getModelDir(model).deleteRecursively()

    fun getTempFile(): File = File.createTempFile("model_", ".tmp", modelsDir)
}
