package com.screenassistant.core.domain.nlp

import com.screenassistant.core.domain.nlp.model.NlpEntity

/**
 * Extractor de entidades del texto del usuario.
 *
 * CONTRATO:
 * - 100% local
 * - Maneja idioma español
 * - Texto normalizado para clasificación, texto original para extracción
 */
interface EntityExtractor {

    /**
     * Extrae todas las entidades reconocibles del texto.
     *
     * @param textoNormalizado Texto normalizado (para detección de patrones)
     * @param textoOriginal Texto original (para extraer valores con mayúsculas)
     * @return Lista de entidades extraídas (puede estar vacía)
     */
    fun extraer(textoNormalizado: String, textoOriginal: String): List<NlpEntity>
}
