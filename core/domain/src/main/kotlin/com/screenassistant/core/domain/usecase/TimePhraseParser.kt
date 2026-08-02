package com.screenassistant.core.domain.usecase

import com.screenassistant.core.domain.model.HourMinute

/** Resultado interno con la posición final del match (para extraer la etiqueta "para X"). */
internal data class HourMinuteMatch(val time: HourMinute, val matchEnd: Int)

/**
 * Parser de horas habladas en español para alarmas.
 *
 * Grammar soportado (entrada normalizada: lowercase, sin tildes, ñ→n):
 *   (las|la)\s+ (palabra_hora | \d{1,2}(:\d{1,2})?)
 *     ( en punto | y <minutos 0-59: fracción, número hablado o "decena y unidad"> |
 *       menos <minutos 1-59: fracción o número hablado> )?
 *     ( de (la )?(manana|tarde|noche) )?
 *
 * - "decena y unidad": la unidad puede ser palabra o DÍGITO 1-9 ("treinta y cinco",
 *   "veinte y 5" — ADR-TMP-7: mismo rango léxico, sin descarte silencioso).
 * - Vocabulario numérico compartido con las duraciones del temporizador (SpokenNumber).
 * - Sin período → 24h literal ("las siete" → 7:00, "las veintiuna" → 21:00).
 * - "menos X" → minute = 60 - X, hour = floorMod(h-1, 24) (¡floorMod, no % que da -1!).
 * - "y/menos <token no resoluble>" (palabra fuera de vocabulario, compuesto inválido)
 *   tras hora simple → REJECT del match completo (null), nunca hora parcial silenciosa (B3).
 * - Lookbehind no-letra antes de (las|la): evita falsos positivos ("sala 4", "clasificar").
 */
object TimePhraseParser {

    private val HOUR_WORDS: Map<String, Int> = mapOf(
        "cero" to 0, "una" to 1, "uno" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4,
        "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10,
        "once" to 11, "doce" to 12, "trece" to 13, "catorce" to 14, "quince" to 15,
        "dieciseis" to 16, "diecisiete" to 17, "dieciocho" to 18, "diecinueve" to 19,
        "veinte" to 20, "veintiuna" to 21, "veintiuno" to 21, "veintidos" to 22,
        "veintitres" to 23
    )

    // O9/TMP-num: resolución completa de minutos hablados (0-59) delegada al objeto
    // compartido SpokenNumber (fuente única: fracciones + simples 0-29 + decenas
    // 20/30/40/50 + compuestos "decena y unidad 1-9").

    /**
     * O9 + ADR-TMP-7: resuelve el token (o par de tokens) de minutos a un valor 0..59.
     *  - token2 == null → fracción, número simple (0-29) o decena (30/40/50).
     *  - token2 != null → solo "decena + unidad 1-9": token2 acepta palabra o DÍGITO
     *    con la misma validación ("treinta y cinco" → 35, "veinte y 5" → 25).
     *  - Cualquier combinación no resoluble (palabra fuera de vocabulario, compuesto
     *    inválido, token2 fuera de 1-9) → null (B3: rechazo completo, nunca hora parcial).
     */
    private fun resolveMinute(token1: String, token2: String?): Int? =
        SpokenNumber.resolve(token1, token2)

    fun parse(text: String): HourMinute? = findMatch(text)?.time

    /** Busca "las/la HH" en cualquier posición del texto. null si no parsea. */
    internal fun findMatch(text: String): HourMinuteMatch? {
        val normalized = normalize(text)

        // (?<![a-z]) evita "sala 4" / "clasificar": "la" debe ser palabra independiente
        val anchor = Regex("""(?<![a-z])(las|la)\s+""").find(normalized) ?: return null
        val afterAnchor = anchor.range.last + 1
        val rest = normalized.substring(afterAnchor)

        // 1. Hora: palabra o numérica (con :MM opcional)
        val hourNum = Regex("""^(\d{1,2})(?::(\d{1,2}))?""").find(rest)
        val hourWord = if (hourNum == null) {
            Regex("""^[a-z]+""").find(rest)?.value
        } else null

        var hour: Int?
        var minute: Int
        var consumed: Int
        if (hourWord != null) {
            hour = HOUR_WORDS[hourWord]
            if (hour == null) return null
            minute = 0
            consumed = hourWord.length
        } else if (hourNum != null) {
            hour = hourNum.groupValues[1].toInt()
            minute = hourNum.groupValues[2].ifBlank { "0" }.toInt()
            consumed = hourNum.value.length
        } else {
            return null
        }

        val afterHour = rest.substring(consumed)

        // 2. Modificador opcional
        var modMatch = Regex("""^\s+en\s+punto""").find(afterHour)
        if (modMatch != null) {
            minute = 0
        } else {
            // Rama "y": token único o compuesto "decena y unidad" (token2 palabra o dígito
            // 1-9, ADR-TMP-7: "veinte y 5" ya no se descarta silenciosamente).
            modMatch = Regex("""^\s+y\s+([a-z]+|\d{1,2})(?:\s+y\s+([a-z]+|\d{1,2}))?""").find(afterHour)
            if (modMatch != null) {
                val minutes = resolveMinute(
                    modMatch.groupValues[1],
                    modMatch.groupValues[2].takeIf { it.isNotEmpty() }
                )
                if (minutes == null) return null // B3: token no resoluble → rechazo completo
                minute = minutes
                // matchEnd = modMatch.range.last: derivado automáticamente; la etiqueta
                // "para X" y el período "de la tarde" siguen funcionando.
            } else {
                // Rama "menos": misma estructura ampliada (B2 intacto: floorMod; token2
                // palabra o dígito 1-9, ADR-TMP-7).
                modMatch = Regex("""^\s+menos\s+([a-z]+|\d{1,2})(?:\s+y\s+([a-z]+|\d{1,2}))?""").find(afterHour)
                if (modMatch != null) {
                    val x = resolveMinute(
                        modMatch.groupValues[1],
                        modMatch.groupValues[2].takeIf { it.isNotEmpty() }
                    )
                    if (x == null) return null // B3: token no resoluble → rechazo completo
                    minute = 60 - x
                    hour = Math.floorMod(hour - 1, 24) // B2: nunca -1
                }
            }
        }
        val consumedMod = modMatch?.range?.last?.plus(1) ?: 0

        // 3. Período opcional (am/pm)
        val afterMod = afterHour.substring(consumedMod)
        val period = Regex("""^\s+de\s+(la\s+)?(manana|tarde|noche)""").find(afterMod)
        if (period != null) {
            // O3: "las doce de la manana" = 12:00 (mediodía), "las doce de la noche" = 0:00
            // (medianoche). Regla de producto: las 12 son el punto de cruce del reloj de
            // 12h — el período NO puede desplazarlas fuera de su valor natural.
            hour = when (period.groupValues[2]) {
                "manana" -> if (hour == 12) 12 else hour % 12
                "noche" -> if (hour == 12) 0 else (hour % 12) + 12
                else -> (hour % 12) + 12   // "tarde" inalterada
            }
        }

        // 4. Validación estricta: hora 0..23, minuto 0..59
        if (hour !in 0..23 || minute !in 0..59) return null

        // matchEnd en índices del texto (normalización 1:1 → mismo índice que el original)
        val end = anchor.range.last + 1 + consumed + consumedMod + (period?.range?.last?.plus(1) ?: 0)
        return HourMinuteMatch(HourMinute(hour, minute), end)
    }

    /** lowercase + sin tildes + ñ→n. Longitud invariante: seguro para substring sobre el original. */
    private fun normalize(text: String): String =
        text.lowercase()
            .replace('á', 'a').replace('é', 'e').replace('í', 'i')
            .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')
            .replace('ñ', 'n')
}
