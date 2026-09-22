package com.screenassistant.core.model.data

import com.screenassistant.core.model.domain.ModelAsset
import com.screenassistant.core.model.domain.ModelAssetRepository
import com.screenassistant.core.model.domain.ModelAssetStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [ModelAssetRepository].
 *
 * Coordina [ModelFileStore] (almacenamiento) y [GitHubModelDownloader] (red)
 * para gestionar el ciclo de vida de los modelos descargables.
 */
@Singleton
class ModelAssetRepositoryImpl @Inject constructor(
    private val fileStore: ModelFileStore,
    private val downloader: GitHubModelDownloader
) : ModelAssetRepository {

    private val statuses = mutableMapOf<ModelAsset, MutableStateFlow<ModelAssetStatus>>()

    /** Estado mutable interno para lectura/escritura dentro del repository. */
    private fun mutableStatus(model: ModelAsset): MutableStateFlow<ModelAssetStatus> {
        return statuses.getOrPut(model) {
            MutableStateFlow(
                if (fileStore.isModelReady(model)) {
                    ModelAssetStatus.Ready(fileStore.getModelFile(model).absolutePath)
                } else {
                    ModelAssetStatus.NotDownloaded
                }
            )
        }
    }

    override fun observeStatus(model: ModelAsset): StateFlow<ModelAssetStatus> = mutableStatus(model)

    override suspend fun isReady(model: ModelAsset): Boolean = fileStore.isModelReady(model)

    override suspend fun download(model: ModelAsset) {
        val state = mutableStatus(model)
        if (state.value is ModelAssetStatus.Ready) return

        state.value = ModelAssetStatus.Downloading(0f)
        val targetFile = fileStore.getModelFile(model)
        targetFile.parentFile?.mkdirs()

        val result = downloader.download(
            url = model.downloadUrl,
            targetFile = targetFile,
            expectedSha256 = model.sha256,
            onProgress = { percent -> state.value = ModelAssetStatus.Downloading(percent) }
        )

        state.value = if (result.isSuccess) {
            ModelAssetStatus.Ready(targetFile.absolutePath)
        } else {
            ModelAssetStatus.Failed(result.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }

    override suspend fun getLocalPath(model: ModelAsset): String? {
        return if (fileStore.isModelReady(model)) fileStore.getModelFile(model).absolutePath else null
    }

    override suspend fun delete(model: ModelAsset): Result<Unit> {
        return if (fileStore.deleteModel(model)) {
            mutableStatus(model).value = ModelAssetStatus.NotDownloaded
            Result.success(Unit)
        } else {
            Result.failure(Exception("Failed to delete model"))
        }
    }

    override fun availableModels(): List<ModelAsset> = ModelAsset.entries

    override suspend fun getModelSizeBytes(model: ModelAsset): Long = model.sizeBytes

    override suspend fun getRequiredSpaceBytes(model: ModelAsset): Long = (model.sizeBytes * 1.2).toLong()
}
