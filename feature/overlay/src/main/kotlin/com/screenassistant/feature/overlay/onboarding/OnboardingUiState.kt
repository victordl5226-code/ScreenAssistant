package com.screenassistant.feature.overlay.onboarding

/**
 * Estado de la UI para la pantalla de onboarding.
 *
 * @property nameInput Texto actual del campo de nombre
 * @property isNameValid `true` si el nombre actual es válido
 * @property isLoading `true` si se está guardando la configuración
 * @property errorMessage Mensaje de error de validación, o `null` si no hay error
 * @property isOnboardingCompleted `true` si el onboarding fue completado (navegar fuera)
 */
data class OnboardingUiState(
    val nameInput: String = "",
    val isNameValid: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isOnboardingCompleted: Boolean = false
)
