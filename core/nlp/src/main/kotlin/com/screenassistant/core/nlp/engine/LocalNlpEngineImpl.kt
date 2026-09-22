package com.screenassistant.core.nlp.engine

import com.screenassistant.core.domain.nlp.EntityExtractor
import com.screenassistant.core.domain.nlp.IntentClassifier
import com.screenassistant.core.domain.nlp.LocalNlpEngine
import com.screenassistant.core.domain.nlp.model.NlpResult
import com.screenassistant.core.nlp.normalizer.TextNormalizer

/**
 * Implementación del motor NLP local.
 *
 * Orquesta IntentClassifier + EntityExtractor para procesar
 * el texto del usuario y devolver un resultado estructurado.
 */
class LocalNlpEngineImpl(
    private val clasificador: IntentClassifier,
    private val extractor: EntityExtractor
) : LocalNlpEngine {

    override fun procesar(texto: String): NlpResult {
        if (texto.isBlank()) {
            return NlpResult.desconocido(texto)
        }

        val textoNormalizado = TextNormalizer.normalizar(texto)
        val (intencion, confianza) = clasificador.clasificar(textoNormalizado)
        val entidades = extractor.extraer(textoNormalizado, texto)

        return NlpResult(
            intent = intencion,
            entidades = entidades,
            confianza = confianza,
            textoOriginal = texto
        )
    }
}
