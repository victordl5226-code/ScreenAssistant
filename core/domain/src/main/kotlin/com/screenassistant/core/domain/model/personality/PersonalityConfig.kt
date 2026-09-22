package com.screenassistant.core.domain.model.personality

/**
 * Configuración completa de personalidad del asistente.
 * Incluye los rasgos, configuración de idioma, frases características
 * y si el asistente se ha adaptado al usuario.
 *
 * @property name Nombre del asistente (e.g. "J.A.R.V.I.S.")
 * @property traits Rasgos de personalidad que definen el tono del asistente
 * @property language Código de idioma ISO 639-1 (e.g. "es", "en")
 * @property catchphrases Frases características o lemas del asistente
 * @property adaptedToUser true si el asistente ya se ha adaptado al perfil del usuario
 */
data class PersonalityConfig(
    val name: String,
    val traits: PersonalityTraits,
    val language: String = "es",
    val catchphrases: List<String> = emptyList(),
    val adaptedToUser: Boolean = false
) {
    init {
        require(name.isNotBlank()) { "El nombre del asistente no puede estar vacío" }
        require(language.isNotBlank()) { "El código de idioma no puede estar vacío" }
    }

    /**
     * Devuelve una frase característica aleatoria, o null si no hay catchphrases definidas.
     */
    fun randomCatchphrase(): String? =
        catchphrases.randomOrNull()

    companion object {
        /**
         * Configuración por defecto de J.A.R.V.I.S.
         * Personalidad formal pero accesible en español.
         */
        val DEFAULT_JARVIS = PersonalityConfig(
            name = "J.A.R.V.I.S.",
            traits = PersonalityTraits.DEFAULT_JARVIS,
            language = "es",
            catchphrases = listOf(
                "A su servicio.",
                "Como desee.",
                "Procesando su solicitud.",
                "Enseguida lo resuelvo."
            )
        )
    }
}
