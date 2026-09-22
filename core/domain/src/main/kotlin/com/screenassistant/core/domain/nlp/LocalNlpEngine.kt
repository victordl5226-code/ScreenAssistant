package com.screenassistant.core.domain.nlp

import com.screenassistant.core.domain.nlp.model.NlpResult

/**
 * Motor NLP local que orquesta IntentClassifier + EntityExtractor.
 *
 * PROPÓSITO: Procesar texto del usuario y devolver un resultado
 * estructurado (intención + entidades + confianza) SIN dependencia de red.
 *
 * FLUJO DE USO:
 *   texto → LocalNlpEngine.procesar(texto) → NlpResult
 *     ├─ esReconocido → NlpCommandParser → ejecutar SystemCommand
 *     └─ !esReconocido → SystemCommandParser legacy → AI
 */
interface LocalNlpEngine {

    /**
     * Procesa el texto del usuario y retorna un resultado NLP estructurado.
     *
     * @param texto Texto original del usuario (puede tener tildes, mayúsculas)
     * @return NlpResult con intención, entidades y confianza
     */
    fun procesar(texto: String): NlpResult
}
