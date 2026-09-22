package com.screenassistant.feature.overlay.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.screenassistant.core.domain.model.personality.PersonalityConfig
import com.screenassistant.core.domain.repository.PersonalityRepository
import com.screenassistant.core.domain.di.IoDispatcher
import com.screenassistant.core.domain.util.NameValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel para la pantalla de onboarding (personalización del nombre del asistente).
 *
 * Gestiona la validación del nombre, el guardado de la configuración
 * y el estado de navegación hacia la pantalla principal.
 *
 * @property personalityRepository Repositorio de configuración de personalidad
 * @property ioDispatcher Dispatcher para operaciones de IO
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val personalityRepository: PersonalityRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            val completed = personalityRepository.isOnboardingCompleted()
            _uiState.update { it.copy(isOnboardingCompleted = completed) }
        }
    }

    /**
     * Se llama cada vez que el usuario cambia el texto del campo de nombre.
     * Actualiza el estado de validación en tiempo real.
     *
     * @param name Nuevo texto ingresado
     */
    fun onNameChanged(name: String) {
        val validation = NameValidator.validate(name)
        _uiState.update {
            it.copy(
                nameInput = name,
                isNameValid = validation.isValid,
                errorMessage = validation.error
            )
        }
    }

    /**
     * Completa el onboarding guardando el nombre elegido.
     * Si el nombre está en blanco, usa [DEFAULT_NAME].
     */
    fun completeOnboarding() {
        val name = _uiState.value.nameInput.ifBlank { DEFAULT_NAME }
        val validation = NameValidator.validate(name)
        if (!validation.isValid) return

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(ioDispatcher) {
            val config = PersonalityConfig.DEFAULT_JARVIS.copy(name = name)
            personalityRepository.completeOnboarding(config)
            _uiState.update { it.copy(isOnboardingCompleted = true, isLoading = false) }
        }
    }

    /**
     * Salta el onboarding usando el nombre por defecto (J.A.R.V.I.S.).
     */
    fun skipOnboarding() {
        viewModelScope.launch(ioDispatcher) {
            personalityRepository.completeOnboarding(PersonalityConfig.DEFAULT_JARVIS)
            _uiState.update { it.copy(isOnboardingCompleted = true) }
        }
    }

    companion object {
        const val DEFAULT_NAME = "J.A.R.V.I.S."
    }
}
