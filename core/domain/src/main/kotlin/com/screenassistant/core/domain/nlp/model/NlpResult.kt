package com.screenassistant.core.domain.nlp.model

/**
 * Resultado estructurado del motor NLP.
 *
 * @param intent Intención clasificada
 * @param entidades Entidades extraídas del texto
 * @param confianza Nivel de confianza [0.0, 1.0]
 * @param textoOriginal Texto original del usuario
 * @param necesitaParserLegacy true si el NLP no pudo resolver completamente
 *                             (ej: duración compuesta "1 hora y 30 minutos")
 */
data class NlpResult(
    val intent: NlpIntent,
    val entidades: List<NlpEntity>,
    val confianza: Double,
    val textoOriginal: String,
    val necesitaParserLegacy: Boolean = false
) {
    /** true si el resultado es reconocido y supera el umbral mínimo de confianza. */
    val esReconocido: Boolean
        get() = intent != NlpIntent.UNKNOWN && confianza >= UMBRAL_CONFIANZA

    companion object {
        /** Umbral mínimo de confianza para aceptar un resultado NLP. */
        const val UMBRAL_CONFIANZA = 0.6

        /** Resultado de fallback: no se reconoció la intención. */
        fun desconocido(input: String) = NlpResult(
            intent = NlpIntent.UNKNOWN,
            entidades = emptyList(),
            confianza = 0.0,
            textoOriginal = input
        )
    }
}
