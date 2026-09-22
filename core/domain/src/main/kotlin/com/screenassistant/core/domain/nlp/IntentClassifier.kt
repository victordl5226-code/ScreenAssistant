package com.screenassistant.core.domain.nlp

import com.screenassistant.core.domain.nlp.model.NlpIntent

/**
 * Clasificador de intenciones basado en texto.
 *
 * CONTRATO:
 * - 100% local (sin dependencia de red)
 * - < 100ms por clasificación
 * - Texto de entrada ya normalizado (minúsculas, sin tildes)
 * - Retorna intención + confianza; UNKNOWN si no clasifica
 */
interface IntentClassifier {

    /**
     * Clasifica el texto normalizado en una intención.
     *
     * @param textoNormalizado Texto normalizado (minúsculas, sin tildes, trim)
     * @return Par de (intención, confianza)
     */
    fun clasificar(textoNormalizado: String): Pair<NlpIntent, Double>
}
