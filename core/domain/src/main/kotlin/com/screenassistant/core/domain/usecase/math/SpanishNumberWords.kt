package com.screenassistant.core.domain.usecase.math

import com.screenassistant.core.domain.usecase.SpokenNumber

/**
 * Palabras numéricas en español para el Nivel 1 matemático (ADR-MATH P2).
 *
 * Reutiliza [SpokenNumber.SIMPLE] (0-29, incl. `un/una`) y [SpokenNumber.TENS]
 * (20-50) y AÑADE `sesenta/setenta/ochenta/noventa` (=60/70/80/90),
 * compuestos `X y Y` hasta 99 (`noventa y nueve`) y los cientos ATÓMICOS
 * 100-900 en token único (`cien/ciento/doscientos/...`).
 *
 * Límites (ADR §6/§14 + test §9 #4):
 * - Vocabulario compuesto 0-100; fuera de rango vía palabras → null.
 * - `doscientos` (y cientos 100-900) resuelven SOLO como token único: el test
 *   `el quince por ciento de doscientos → 30` lo exige, pero la composición
 *   con cientos (`ciento uno` = 101, `doscientos cincuenta`) es MATH-2 →
 *   null (nunca parcial). Los dígitos literales (`200`, `101+2`) no son
 *   palabras y se evalúan con normalidad.
 */
object SpanishNumberWords {

    private val DECENAS_EXTRA: Map<String, Int> = mapOf(
        "sesenta" to 60, "setenta" to 70, "ochenta" to 80, "noventa" to 90
    )

    private val CIENTOS: Map<String, Int> = mapOf(
        "cien" to 100, "ciento" to 100,
        "doscientos" to 200, "doscientas" to 200,
        "trescientos" to 300, "trescientas" to 300,
        "cuatrocientos" to 400, "cuatrocientas" to 400,
        "quinientos" to 500, "quinientas" to 500,
        "seiscientos" to 600, "seiscientas" to 600,
        "setecientos" to 700, "setecientas" to 700,
        "ochocientos" to 800, "ochocientas" to 800,
        "novecientos" to 900, "novecientas" to 900
    )

    /** Decenas que admiten compuesto `X y Y` (20 arcaico + 30-90). */
    val decenasCompuesto: Map<String, Int> = SpokenNumber.TENS + DECENAS_EXTRA

    /** ¿Es un cientos atómico (`cien`, `doscientos`, ...)? */
    fun esCientos(palabra: String): Boolean = palabra in CIENTOS

    /**
     * Tokens de número ordenados por longitud DESCENDENTE para alternation de
     * regex (el token más largo primero → sin solapes parciales).
     */
    fun tokensPorLongitud(): List<String> =
        (SpokenNumber.SIMPLE.keys + SpokenNumber.TENS.keys +
            DECENAS_EXTRA.keys + CIENTOS.keys)
            .distinct().sortedByDescending { it.length }

    /**
     * Resuelve una frase numérica (ya en minúsculas, con o sin tildes) a 0..900.
     * Acepta: dígitos, simples 0-29, decenas, compuesto `decena y unidad 1-9`
     * (unidad en palabra O dígito, igual que [SpokenNumber.resolve]) y cientos
     * atómicos. Cualquier otra cosa (incl. `ciento uno`) → null.
     */
    fun resolve(frase: String): Int? {
        val p = frase.trim().lowercase()
            .replace('á', 'a').replace('é', 'e').replace('í', 'i')
            .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')
            .replace(Regex("""\s+"""), " ")
        if (p.isEmpty()) return null
        p.toIntOrNull()?.let { return it.takeIf { d -> d in 0..999999 } }
        CIENTOS[p]?.let { return it }
        SpokenNumber.SIMPLE[p]?.let { return it }
        SpokenNumber.TENS[p]?.let { return it }
        DECENAS_EXTRA[p]?.let { return it }
        val partes = p.split(" y ")
        if (partes.size == 2) {
            val decena = decenasCompuesto[partes[0]] ?: return null
            val unidad = partes[1].toIntOrNull() ?: SpokenNumber.SIMPLE[partes[1]] ?: return null
            if (unidad !in 1..9) return null
            return decena + unidad
        }
        return null
    }
}
