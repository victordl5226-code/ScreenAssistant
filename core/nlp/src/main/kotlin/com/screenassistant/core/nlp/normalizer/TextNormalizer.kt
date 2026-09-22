package com.screenassistant.core.nlp.normalizer

/**
 * Normalizador de texto para NLP en español.
 *
 * Convierte a minúsculas, elimina tildes, sustituye ñ→n,
 * y elimina puntuación innecesaria.
 */
object TextNormalizer {

    /** Mapa de tildes a caracteres base. */
    private val TILDES = mapOf(
        'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u',
        'Á' to 'A', 'É' to 'E', 'Í' to 'I', 'Ó' to 'O', 'Ú' to 'U',
        'ü' to 'u', 'Ü' to 'U'
    )

    /**
     * Normaliza el texto para clasificación NLP.
     *
     * Pasos:
     * 1. Minúsculas
     * 2. Eliminar tildes (á→a)
     * 3. Sustituir ñ→n
     * 4. Eliminar puntuación de borde (¿ ¡ ? !)
     * 5. Eliminar espacios múltiples
     */
    fun normalizar(texto: String): String {
        return texto
            .lowercase()
            .map { TILDES[it] ?: it }
            .joinToString("")
            .replace('ñ', 'n')
            .replace(Regex("""[¿¡]"""), "")
            .replace(Regex("""[?!.,;:]+$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
