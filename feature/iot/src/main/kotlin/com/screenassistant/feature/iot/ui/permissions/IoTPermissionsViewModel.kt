package com.screenassistant.feature.iot.ui.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.screenassistant.core.iot.domain.usecase.GetRequiredPermissionsUseCase
import com.screenassistant.core.iot.domain.usecase.PermissionsReport
import com.screenassistant.core.iot.domain.usecase.RequestPermissionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Estado de la UI para la pantalla de permisos IoT.
 */
sealed interface IoTPermissionsUiState {
    data object Loading : IoTPermissionsUiState
    data class Content(
        val report: PermissionsReport,
        val isRequesting: Boolean = false,
    ) : IoTPermissionsUiState
    data class Error(val message: String) : IoTPermissionsUiState
}

/**
 * ViewModel para la gestión de permisos IoT.
 *
 * Expone el estado de permisos de Health Connect y Car App,
 * y permite solicitar permisos al usuario.
 */
@HiltViewModel
class IoTPermissionsViewModel @Inject constructor(
    private val getRequiredPermissionsUseCase: GetRequiredPermissionsUseCase,
    private val requestPermissionsUseCase: RequestPermissionsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<IoTPermissionsUiState>(IoTPermissionsUiState.Loading)
    val uiState: StateFlow<IoTPermissionsUiState> = _uiState.asStateFlow()

    init {
        loadPermissions()
    }

    private fun loadPermissions() {
        viewModelScope.launch {
            try {
                val report = getRequiredPermissionsUseCase()
                _uiState.value = IoTPermissionsUiState.Content(report = report)
            } catch (e: Exception) {
                _uiState.value = IoTPermissionsUiState.Error(e.message ?: "Error al cargar permisos")
            }
        }
    }

    fun requestAllEssential() {
        viewModelScope.launch {
            _uiState.update { (it as? IoTPermissionsUiState.Content)?.copy(isRequesting = true) ?: it }
            try {
                requestPermissionsUseCase.requestAllEssential()
            } catch (e: Exception) {
                // Permission request may fail if UI is not available
            }
            loadPermissions()
        }
    }
}
