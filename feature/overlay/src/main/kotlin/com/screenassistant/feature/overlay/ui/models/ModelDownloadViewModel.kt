package com.screenassistant.feature.overlay.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.screenassistant.core.domain.di.IoDispatcher
import com.screenassistant.core.model.domain.ModelAsset
import com.screenassistant.core.model.domain.ModelAssetRepository
import com.screenassistant.core.model.domain.ModelAssetStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Estado de un modelo individual en la UI.
 */
data class ModelUiState(
    val asset: ModelAsset,
    val status: ModelAssetStatus = ModelAssetStatus.NotDownloaded,
    val formattedSize: String = "",
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
) {
    val isReady: Boolean get() = status is ModelAssetStatus.Ready
    val isNotDownloaded: Boolean get() = status is ModelAssetStatus.NotDownloaded
    val isFailed: Boolean get() = status is ModelAssetStatus.Failed
}

/**
 * Estado de la pantalla de descarga de modelos.
 */
data class ModelDownloadUiState(
    val models: List<ModelUiState> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * ViewModel para la pantalla de descarga de modelos.
 *
 * Gestiona el estado de descarga de los 3 modelos del sistema:
 * - Piper TTS (Voz)
 * - Vosk STT (Reconocimiento)
 * - MiniLM (Embeddings)
 *
 * Permite descargar, eliminar y observar el estado de cada modelo.
 */
@HiltViewModel
class ModelDownloadViewModel @Inject constructor(
    private val modelRepository: ModelAssetRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelDownloadUiState())
    val uiState: StateFlow<ModelDownloadUiState> = _uiState.asStateFlow()

    init {
        refreshStatus()
    }

    /**
     * Actualiza el estado de todos los modelos desde el repositorio.
     */
    fun refreshStatus() {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isLoading = true) }

            val models = modelRepository.availableModels().map { asset ->
                val status = modelRepository.observeStatus(asset).value
                ModelUiState(
                    asset = asset,
                    status = status,
                    formattedSize = formatBytes(asset.sizeBytes),
                )
            }

            _uiState.update {
                it.copy(
                    models = models,
                    isLoading = false,
                    errorMessage = null,
                )
            }

            // Observar cambios de estado de cada modelo
            for (asset in modelRepository.availableModels()) {
                launch {
                    modelRepository.observeStatus(asset).collect { status ->
                        _uiState.update { state ->
                            state.copy(
                                models = state.models.map { uiModel ->
                                    if (uiModel.asset == asset) {
                                        uiModel.copy(
                                            status = status,
                                            isDownloading = status is ModelAssetStatus.Downloading,
                                            downloadProgress = (status as? ModelAssetStatus.Downloading)?.progress ?: 0f,
                                        )
                                    } else {
                                        uiModel
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Descarga un modelo específico.
     */
    fun downloadModel(asset: ModelAsset) {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { state ->
                state.copy(
                    models = state.models.map {
                        if (it.asset == asset) it.copy(isDownloading = true)
                        else it
                    }
                )
            }

            try {
                modelRepository.download(asset)
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        errorMessage = "Error descargando ${asset.displayName}: ${e.message}",
                        models = state.models.map {
                            if (it.asset == asset) it.copy(isDownloading = false)
                            else it
                        }
                    )
                }
            }
        }
    }

    /**
     * Elimina un modelo descargado.
     */
    fun deleteModel(asset: ModelAsset) {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isLoading = true) }

            val result = modelRepository.delete(asset)
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "No se pudo eliminar ${asset.displayName}",
                    )
                }
            } else {
                refreshStatus()
            }
        }
    }

    /**
     * Limpia el mensaje de error.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return "${"%.1f".format(mb)} MB"
    }
}
