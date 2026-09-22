package com.screenassistant.feature.overlay.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.screenassistant.core.domain.repository.ai.LocalInferenceEngine
import com.screenassistant.core.domain.repository.ai.ModelManager
import com.screenassistant.core.domain.repository.ai.ModelInfo
import com.screenassistant.core.domain.model.ModelState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state para la pantalla de configuración de IA local.
 */
data class LocalLlmUiState(
    val modelInfo: ModelInfo? = null,
    val isNativeAvailable: Boolean = false,
    val isModelDownloaded: Boolean = false,
    val isModelReady: Boolean = false,
    val downloadProgress: Float = 0f,
    val availableSpaceBytes: Long = 0L,
    val formattedModelSize: String = "",
    val formattedAvailableSpace: String = "",
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
)

/**
 * ViewModel para la configuración de IA local (llama.cpp).
 *
 * Gestiona el ciclo de vida del modelo: detección de disponibilidad,
 * descarga, carga, descarga y eliminación.
 */
@HiltViewModel
class LocalLlmSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localEngine: LocalInferenceEngine,
    private val modelManager: ModelManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalLlmUiState())
    val uiState: StateFlow<LocalLlmUiState> = _uiState.asStateFlow()

    init {
        refreshState()
    }

    /**
     * Actualiza el estado completo basado en el motor y el gestor de modelos.
     */
    fun refreshState() {
        val modelInfo = modelManager.getModelInfo()
        val isDownloaded = modelManager.isModelDownloaded()
        
        _uiState.update {
            it.copy(
                modelInfo = modelInfo,
                isNativeAvailable = true,
                isModelDownloaded = isDownloaded,
                isModelReady = localEngine.isReady(),
                availableSpaceBytes = 4_000_000_000L, // Placeholder
                formattedModelSize = formatBytes(modelInfo?.sizeBytes ?: 0L),
                formattedAvailableSpace = "4.0 GB", // Placeholder
                errorMessage = null,
            )
        }
    }

    private fun formatBytes(bytes: Long): String {
        return "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }

    fun downloadModel() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = modelManager.downloadModel { progress ->
                _uiState.update { it.copy(downloadProgress = progress) }
            }
            
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isModelDownloaded = result.isSuccess,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
            refreshState()
        }
    }

    fun loadModel() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val success = modelManager.loadModel()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isModelReady = success,
                    errorMessage = if (!success) "Error al cargar el modelo" else null
                )
            }
            refreshState()
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            modelManager.unloadModel()
            refreshState()
        }
    }

    fun deleteModel() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val success = modelManager.deleteModel()
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    isModelDownloaded = false,
                    isModelReady = false,
                    errorMessage = if (!success) "No se pudo eliminar el archivo del modelo" else null
                )
            }
            refreshState()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
