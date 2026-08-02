package com.screenassistant.core.domain.usecase

/**
 * Números hablados en español compartidos por los parsers puros de domain
 * (duraciones del temporizador y minutos hablados de las horas).
 *
 * Extraído de TimePhraseParser (O9): los mapas de duración del temporizador
 * (TMP-num) y los de minutos de hora usan ahora el mismo vocabulario, con una
 * sola fuente de verdad. ADR-TMP-6 / ADR-TMP-7.
 */
internal object SpokenNumber {

    /** Números 0-29 en una sola palabra. "un" (coloquial de "uno" en duraciones:
     *  "un minuto") se incluye SOLO aquí; HOUR_WORDS de TimePhraseParser sigue intacto. */
    val SIMPLE: Map<String, Int> = mapOf(
        "cero" to 0, "uno" to 1, "un" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4,
        "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9,
        "diez" to 10, "once" to 11, "doce" to 12, "trece" to 13, "catorce" to 14,
        "quince" to 15, "dieciseis" to 16, "diecisiete" to 17, "dieciocho" to 18,
        "diecinueve" to 19, "veinte" to 20, "veintiuno" to 21, "veintidos" to 22,
        "veintitres" to 23, "veinticuatro" to 24, "veinticinco" to 25, "veintiseis" to 26,
        "veintisiete" to 27, "veintiocho" to 28, "veintinueve" to 29
    )

    /** Decenas que admiten compuesto "X y Y": 20 (arcaico "veinte y uno") y 30-59.
     *  "veinte" está también en SIMPLE (doble entrada intencional: como token único
     *  resuelve 20; como decena habilita el compuesto). */
    val TENS: Map<String, Int> = mapOf(
        "veinte" to 20, "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50
    )

    /** Fracciones de hora (solo ligadas a horas, ver ADR-TMP-6). */
    val FRACTION: Map<String, Int> = mapOf("media" to 30, "cuarto" to 15)

    /** Tokens de cantidad para duraciones (sin fracciones), ordenados por longitud
     *  DESCENDENTE: el regex de alternation prueba el token más largo primero
     *  ("veintinueve" antes que "dos") → sin solapes parciales. */
    fun quantityTokens(): List<String> =
        (SIMPLE.keys + TENS.keys).distinct().sortedByDescending { it.length }

    /** Unidades 1-9 (token2 válido del compuesto "decena y unidad"), ordenadas por
     *  longitud descendente para el alternation del regex. */
    fun unitTokens(): List<String> =
        SIMPLE.filter { it.value in 1..9 }.keys.sortedByDescending { it.length }

    /**
     * Resuelve el token (o par de tokens) a un valor 0..59.
     *  - token2 == null → fracción, número simple (0-29) o decena (30/40/50).
     *  - token2 != null → solo "decena + unidad 1-9" ("treinta y cinco" → 35,
     *    "veinte y 5" → 25): token2 acepta palabra O dígito con la MISMA validación
     *    1..9 (ADR-TMP-7; "veinte y 10" y "veinte y veinte" → null).
     *  - Cualquier combinación no resoluble → null (B3: rechazo completo, nunca parcial).
     */
    fun resolve(token1: String, token2: String?): Int? =
        if (token2 != null) {
            if (token1 in TENS) {
                val unit = token2.toIntOrNull() ?: SIMPLE[token2] ?: return null
                if (unit !in 1..9) null else TENS.getValue(token1) + unit
            } else null
        } else if (token1.firstOrNull()?.isDigit() == true) {
            token1.toIntOrNull()?.takeIf { it in 0..59 }
        } else {
            FRACTION[token1] ?: SIMPLE[token1] ?: TENS[token1]
        }
}
