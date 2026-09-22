package com.screenassistant.feature.overlay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.screenassistant.core.domain.model.personality.PersonalityConfig
import com.screenassistant.core.domain.repository.PersonalityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Estado de la UI para la pantalla de configuración de personalidad.
 *
 * @property config Configuración de personalidad actual del asistente
 * @property isLoading `true` mientras se carga la configuración inicial
 */
data class PersonalityUiState(
    val config: PersonalityConfig = PersonalityConfig.DEFAULT_JARVIS,
    val isLoading: Boolean = true
)

/**
 * ViewModel para la configuración de la personalidad del asistente.
 *
 * Expone la configuración de personalidad ([PersonalityConfig]) como un
 * [StateFlow] reactivo que la UI puede observar directamente. Permite
 * al usuario ajustar los rasgos de personalidad (formalidad, humor,
 * verbosidad, calidez, sarcasmo) y el nombre del asistente.
 *
 * La persistencia se realiza a través de [PersonalityRepository], que
 * a su vez delega en [PersonalityPreferences] (DataStore).
 *
 * @property personalityRepository Repositorio de acceso a la configuración de personalidad
 */
@HiltViewModel
class PersonalityViewModel @Inject constructor(
    private val personalityRepository: PersonalityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonalityUiState())
    val uiState: StateFlow<PersonalityUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            personalityRepository.getPersonalityConfig().collect { config ->
                _uiState.value = PersonalityUiState(config = config, isLoading = false)
            }
        }
    }

    /**
     * Actualiza el valor de un rasgo de personalidad específico.
     *
     * El nombre del rasgo se mapea al campo correspondiente de [PersonalityConfig.traits].
     * Si el nombre no coincide con ningún rasgo conocido, la operación es un no-op.
     *
     * @param trait Nombre del rasgo a modificar ("formality", "humor", "warmth",
     *              "verbosity", "sarcasm")
     * @param value Nuevo valor del rasgo (0.0 - 1.0)
     */
    fun updateTrait(trait: String, value: Float) {
        viewModelScope.launch {
            val current = _uiState.value.config
            val newTraits = when (trait) {
                "formality" -> current.traits.copy(formality = value)
                "humor" -> current.traits.copy(humor = value)
                "warmth" -> current.traits.copy(warmth = value)
                "verbosity" -> current.traits.copy(verbosity = value)
                "sarcasm" -> current.traits.copy(sarcasm = value)
                else -> current.traits
            }
            personalityRepository.updatePersonalityConfig(
                current.copy(traits = newTraits)
            )
        }
    }

    /**
     * Actualiza el nombre del asistente.
     *
     * @param name Nuevo nombre del asistente (no puede estar en blanco)
     */
    fun updateName(name: String) {
        viewModelScope.launch {
            val current = _uiState.value.config
            personalityRepository.updatePersonalityConfig(current.copy(name = name))
        }
    }
}
