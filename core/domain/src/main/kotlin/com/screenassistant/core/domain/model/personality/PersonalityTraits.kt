package com.screenassistant.core.domain.model.personality

/**
 * Rasgos de personalidad del asistente.
 * Cada rasgo se representa como un valor continuo entre 0.0 y 1.0.
 *
 * El sistema usa estos rasgos para adaptar el tono, la verbosidad y el estilo
 * de las respuestas del asistente al perfil del usuario.
 *
 * @property formality Nivel de formalidad (0.0 = muy informal, 1.0 = muy formal)
 * @property humor Nivel de humor (0.0 = serio, 1.0 = muy humorístico)
 * @property verbosity Nivel de verbosidad (0.0 = conciso, 1.0 = muy detallado)
 * @property warmth Nivel de calidez (0.0 = distante/profesional, 1.0 = cálido/amigable)
 * @property sarcasm Nivel de sarcasmo (0.0 = nada sarcástico, 1.0 = muy sarcástico)
 */
data class PersonalityTraits(
    val formality: Float,
    val humor: Float,
    val verbosity: Float,
    val warmth: Float,
    val sarcasm: Float
) {
    init {
        require(formality in 0f..1f) { "formality debe estar entre 0.0 y 1.0, recibido: $formality" }
        require(humor in 0f..1f) { "humor debe estar entre 0.0 y 1.0, recibido: $humor" }
        require(verbosity in 0f..1f) { "verbosity debe estar entre 0.0 y 1.0, recibido: $verbosity" }
        require(warmth in 0f..1f) { "warmth debe estar entre 0.0 y 1.0, recibido: $warmth" }
        require(sarcasm in 0f..1f) { "sarcasm debe estar entre 0.0 y 1.0, recibido: $sarcasm" }
    }

    companion object {
        /**
         * Perfil de personalidad por defecto de J.A.R.V.I.S.
         * Formal pero accesible, conciso, con un toque de calidez y humor sutil.
         */
        val DEFAULT_JARVIS = PersonalityTraits(
            formality = 0.7f,
            humor = 0.3f,
            verbosity = 0.4f,
            warmth = 0.6f,
            sarcasm = 0.2f
        )

        /**
         * Perfil completamente neutral (todos los rasgos en 0.5).
         * Útil como punto de partida para personalización.
         */
        val NEUTRAL = PersonalityTraits(
            formality = 0.5f,
            humor = 0.5f,
            verbosity = 0.5f,
            warmth = 0.5f,
            sarcasm = 0.5f
        )
    }

    /**
     * Mezcla este perfil con otro, ponderado por [ratio].
     *
     * @param other Otro perfil de personalidad
     * @param ratio Proporción de este perfil (0.0 = solo el otro, 1.0 = solo este)
     * @return Nuevo PersonalityTraits mezclado
     */
    fun blendWith(other: PersonalityTraits, ratio: Float): PersonalityTraits {
        require(ratio in 0f..1f) { "El ratio debe estar entre 0.0 y 1.0, recibido: $ratio" }
        val inverse = 1f - ratio
        return PersonalityTraits(
            formality = this.formality * inverse + other.formality * ratio,
            humor = this.humor * inverse + other.humor * ratio,
            verbosity = this.verbosity * inverse + other.verbosity * ratio,
            warmth = this.warmth * inverse + other.warmth * ratio,
            sarcasm = this.sarcasm * inverse + other.sarcasm * ratio
        )
    }
}
