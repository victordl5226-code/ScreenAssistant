package com.screenassistant.feature.overlay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.screenassistant.core.data.util.ProactivePreferences
import com.screenassistant.core.domain.model.proactive.ProactiveRule
import com.screenassistant.core.domain.repository.ProactiveRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Estado de la UI para la pantalla de configuración de proactividad.
 *
 * @property isEnabled `true` si el sistema proactivo está habilitado
 * @property checkIntervalMinutes Intervalo de comprobación de reglas en minutos
 * @property rules Lista de reglas proactivas almacenadas
 */
data class ProactiveUiState(
    val isEnabled: Boolean = true,
    val checkIntervalMinutes: Long = 15L,
    val rules: List<ProactiveRule> = emptyList()
)

/**
 * ViewModel para la configuración del sistema proactivo.
 *
 * Expone las preferencias del motor proactivo ([ProactivePreferences]) y
 * la lista de reglas almacenadas ([ProactiveRuleRepository]) como un
 * [StateFlow] reactivo que la UI puede observar directamente.
 *
 * Permite al usuario habilitar/deshabilitar el sistema proactivo y
 * ajustar el intervalo de comprobación.
 *
 * @property preferences Preferencias de persistencia del sistema proactivo
 * @property ruleRepository Repositorio de acceso a las reglas proactivas
 */
@HiltViewModel
class ProactiveViewModel @Inject constructor(
    private val preferences: ProactivePreferences,
    private val ruleRepository: ProactiveRuleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProactiveUiState())
    val uiState: StateFlow<ProactiveUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                preferences.isEnabled,
                preferences.checkIntervalMinutes,
                ruleRepository.getAllRules()
            ) { enabled, interval, rules ->
                ProactiveUiState(
                    isEnabled = enabled,
                    checkIntervalMinutes = interval,
                    rules = rules
                )
            }.collect { _uiState.value = it }
        }
    }

    /**
     * Habilita o deshabilita el sistema proactivo.
     *
     * @param enabled `true` para activar, `false` para desactivar
     */
    fun toggleProactive(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setEnabled(enabled)
        }
    }

    /**
     * Establece el intervalo de comprobación de reglas proactivas.
     *
     * @param minutes Intervalo en minutos (mínimo 1)
     */
    fun setCheckInterval(minutes: Long) {
        viewModelScope.launch {
            preferences.setCheckInterval(minutes)
        }
    }
}
