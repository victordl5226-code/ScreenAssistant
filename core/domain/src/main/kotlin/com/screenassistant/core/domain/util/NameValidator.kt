package com.screenassistant.core.domain.util

/**
 * Validador de nombres para el asistente.
 *
 * Reglas:
 * - No puede estar vacío
 * - Longitud máxima: [MAX_LENGTH] caracteres
 * - Solo letras, números, puntos, guiones y espacios
 */
object NameValidator {
    const val MAX_LENGTH = 20
    const val MIN_LENGTH = 1
    private val NAME_PATTERN = Regex("^[\\p{L}\\d.\\-\\s]+$")

    /**
     * Valida el nombre ingresado por el usuario.
     *
     * @param name Nombre a validar (se hace trim automáticamente)
     * @return [ValidationResult] con el resultado de la validación
     */
    fun validate(name: String): ValidationResult {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> ValidationResult(false, "El nombre no puede estar vacío")
            trimmed.length > MAX_LENGTH -> ValidationResult(false, "Máximo $MAX_LENGTH caracteres")
            !NAME_PATTERN.containsMatchIn(trimmed) -> ValidationResult(false, "Solo letras, números, puntos y guiones")
            else -> ValidationResult(true)
        }
    }

    /**
     * Resultado de la validación de un nombre.
     *
     * @property isValid `true` si el nombre es válido
     * @property error Mensaje de error si la validación falla, `null` si es válida
     */
    data class ValidationResult(val isValid: Boolean, val error: String? = null)
}
