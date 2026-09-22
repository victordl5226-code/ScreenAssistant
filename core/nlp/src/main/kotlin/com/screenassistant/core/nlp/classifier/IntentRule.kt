package com.screenassistant.core.nlp.classifier

import com.screenassistant.core.domain.nlp.model.NlpIntent

/**
 * Regla de clasificación de intención.
 *
 * @param patrones Lista de regex que detectan la intención
 * @param intencion Intención asociada si algún patrón coincide
 * @param confianzaBase Confianza base de la regla [0.0, 1.0]
 */
data class IntentRule(
    val patrones: List<Regex>,
    val intencion: NlpIntent,
    val confianzaBase: Double
) {
    /** Verifica si el texto normalizado coincide con algún patrón. */
    fun coincide(textoNormalizado: String): Boolean =
        patrones.any { it.containsMatchIn(textoNormalizado) }
}
