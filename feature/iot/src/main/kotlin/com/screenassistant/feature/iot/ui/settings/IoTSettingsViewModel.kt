package com.screenassistant.feature.iot.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.screenassistant.core.iot.domain.usecase.IotSyncState
import com.screenassistant.core.iot.domain.usecase.SyncIotDevicesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Estado de la UI para la pantalla de configuración IoT.
 */
sealed interface IoTSettingsUiState {
    data object Loading : IoTSettingsUiState
    data class Content(
        val syncState: IotSyncState,
        val matterDevicesOnline: Int,
        val matterDevicesTotal: Int,
        val healthSummary: String,
        val carConnected: Boolean,
        val lastSyncTime: String,
    ) : IoTSettingsUiState
    data class Error(val message: String) : IoTSettingsUiState
}

/**
 * ViewModel para el dashboard de configuración IoT.
 *
 * Orquesta la sincronización de dispositivos Matter, métricas de salud
 * y estado del vehículo en un único [StateFlow].
 */
@HiltViewModel
class IoTSettingsViewModel @Inject constructor(
    private val syncIotDevicesUseCase: SyncIotDevicesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<IoTSettingsUiState>(IoTSettingsUiState.Loading)
    val uiState: StateFlow<IoTSettingsUiState> = _uiState.asStateFlow()

    init {
        observeIoTState()
    }

    private fun observeIoTState() {
        viewModelScope.launch {
            try {
                syncIotDevicesUseCase().collect { syncState ->
                    _uiState.value = IoTSettingsUiState.Content(
                        syncState = syncState,
                        matterDevicesOnline = syncState.matterDevicesOnline,
                        matterDevicesTotal = syncState.matterDevices.size,
                        healthSummary = syncState.healthMetrics.summary(),
                        carConnected = syncState.isCarConnected,
                        lastSyncTime = formatTimestamp(syncState.lastSyncTimestamp),
                    )
                }
            } catch (e: Exception) {
                _uiState.value = IoTSettingsUiState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.value = IoTSettingsUiState.Loading
            try {
                val state = syncIotDevicesUseCase.syncNow()
                _uiState.value = IoTSettingsUiState.Content(
                    syncState = state,
                    matterDevicesOnline = state.matterDevicesOnline,
                    matterDevicesTotal = state.matterDevices.size,
                    healthSummary = state.healthMetrics.summary(),
                    carConnected = state.isCarConnected,
                    lastSyncTime = formatTimestamp(state.lastSyncTimestamp),
                )
            } catch (e: Exception) {
                _uiState.value = IoTSettingsUiState.Error(e.message ?: "Error de sincronización")
            }
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60_000 -> "Hace menos de 1 min"
            diff < 3_600_000 -> "Hace ${diff / 60_000} min"
            else -> "Hace ${diff / 3_600_000} h"
        }
    }
}
