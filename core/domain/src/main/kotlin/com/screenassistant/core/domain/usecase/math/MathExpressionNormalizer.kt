package com.screenassistant.core.domain.usecase.math

/**
 * Normalizador de español libre → expresión canónica (ADR-MATH Nivel 1, P2).
 *
 * Puro Kotlin, sin Android. Recibe el SEGMENTO matemático post-trigger
 * (lo que sigue a `cuanto es / suma / ...`):
 * - `trimmedYaNormalizado`: segmento en formato `normalize` (lowercase, sin
 *   tildes, `.`/`,`→espacio). Solo informativo/defensivo.
 * - `original`: segmento ORIGINAL (preserva `.`, `,`, símbolos y case).
 *   Es la fuente primaria: los decimales `1,5`/`1.000,5` no sobreviven al
 *   `normalize` general, así que este normalizador construye su propia forma
 *   de trabajo desde `original` (KDoc del riesgo locale, ADR §11).
 *
 * `null` = no es matemática / inválida / incompleta → fall-through a Gemini
 * (Nivel 2). Solo emite canónicas COMPLETAS (con dígito, paréntesis
 * balanceados y sin operador colgante); lo completo-pero-indefinido
 * (`10/0`, `sqrt(-1)`) SÍ se emite para que la acción hable el Error.
 *
 * Orden interno (las colisiones `y/por` exigen este orden):
 * cortesía → paréntesis verbales → artículos → guardia cientos →
 * compuestos `X y Y` → simples → raíz → potencias → porcentajes →
 * operadores (`dividido por` antes que `por`; `por ciento` ya consumido) →
 * comas/miles → validación allowlist.
 */
object MathExpressionNormalizer {

    private val CORTESIA = Regex("""(?i)\s*(?:por\s+favor|porfavor|porfa)\s*$""")
    private val ABRE_PARENTESIS = Regex("""\babr(?:e|ir)\s+(?:el\s+)?parentesis\b""")
    private val CIERRA_PARENTESIS = Regex("""\bcierr(?:a|e|ar)\s+(?:el\s+)?parentesis\b""")
    private val ARTICULOS = Regex("""\b(el|la|los|las)\b""")

    private val DECENAS_ALTERNATION: String by lazy {
        SpanishNumberWords.decenasCompuesto.keys.sortedByDescending { it.length }
            .joinToString("|")
    }
    private val COMPUESTO_REGEX: Regex by lazy {
        Regex("""\b($DECENAS_ALTERNATION)\s+y\s+(\d+|[a-z]+)\b""")
    }
    private val SIMPLE_REGEX: Regex by lazy {
        Regex("""\b(${SpanishNumberWords.tokensPorLongitud().joinToString("|")}|(?:\d+))\b""")
    }
    private val CIENTOS_VECINO_NUM_REGEX: Regex by lazy {
        val cientos = listOf(
            "cien", "ciento", "doscientos", "doscientas", "trescientos", "trescientas",
            "cuatrocientos", "cuatrocientas", "quinientos", "quinientas",
            "seiscientos", "seiscientas", "setecientos", "setecientas",
            "ochocientos", "ochocientas", "novecientos", "novecientas"
        ).joinToString("|")
        // Cientos COMPUESTO (`ciento uno`, `doscientos 50`) → fuera de alcance (MATH-2).
        Regex("""\b($cientos)\s+(\d+|[a-z]+)\b|\b(\d+|[a-z]+)\s+($cientos)\b""")
    }

    fun toCanonical(trimmedYaNormalizado: String, original: String): String? {
        if (original.isBlank() && trimmedYaNormalizado.isBlank()) return null
        var w = sinTildes(original.lowercase()).trim()
        w = CORTESIA.replace(w, "").trim()
        w = w.trim(' ', '?', '!', '¡', '¿', ';', ':').trimEnd('.').trim()
        if (w.isEmpty()) return null

        // Paréntesis verbales.
        w = ABRE_PARENTESIS.replace(w, " ( ")
        w = CIERRA_PARENTESIS.replace(w, " ) ")

        // Artículos residuales (`el quince por ciento...`).
        w = ARTICULOS.replace(w, " ")

        // Guardia cientos: composición con cientos → MATH-2 → null. El vecino
        // debe RESOLVER como número (`ciento uno` → null) pero `por` no
        // resuelve (`doscientos por ciento` sigue vivo → 200/100).
        for (m in CIENTOS_VECINO_NUM_REGEX.findAll(w)) {
            val vecino = m.groupValues[2].ifEmpty { m.groupValues[3] }
            if (vecino.isNotEmpty() && SpanishNumberWords.resolve(vecino) != null) return null
        }

        // Compuestos `decena y unidad` (válidos → dígitos; inválidos → null,
        // rechazo completo B3: `veinte y diez` NO puede degradar a `20+10`).
        val compuestoFallido = booleanArrayOf(false)
        w = COMPUESTO_REGEX.replace(w) { m ->
            val valor = SpanishNumberWords.resolve(m.value)
            if (valor == null) {
                compuestoFallido[0] = true
                m.value
            } else {
                " $valor "
            }
        }
        if (compuestoFallido[0]) return null

        // Fusión `por ciento` ANTES de convertir palabras: `ciento` es token
        // numérico (=100) y lo destruiría (`quince por ciento` → `15 por 100`).
        w = Regex("""\bpor\s+ciento\b""").replace(w, " porciento ")

        // Simples (palabras → dígitos; los dígitos literales pasan intactos).
        w = SIMPLE_REGEX.replace(w) { m ->
            val valor = SpanishNumberWords.resolve(m.value)
            if (valor != null && !m.value.first().isDigit()) " $valor " else m.value
        }

        // Raíz: `raiz [cuadrada] de <número|(...)>`.
        w = Regex("""\braiz(?:\s+cuadrada)?\s+de\s+(\d+(?:\.\d+)?|\([^()]*\))""").replace(w) { m ->
            "sqrt(${m.groupValues[1]})"
        }
        if (Regex("""\braiz\b""").containsMatchIn(w)) return null

        // Potencias (base dígito o `)`).
        w = Regex("""(\d+(?:\.\d+)?|\))\s+al\s+cuadrado\b""").replace(w, "$1^2")
        w = Regex("""(\d+(?:\.\d+)?|\))\s+al\s+cubo\b""").replace(w, "$1^3")
        w = Regex("""(\d+(?:\.\d+)?|\))\s+elevad[oa]s?\s+al?\s+a?\s*(\d+(?:\.\d+)?)""")
            .replace(w, "$1^$2")

        // Porcentajes (`X% de Y` / `X por ciento de Y` → `X/100*Y`; suelto → `/100`).
        w = Regex("""(\d+(?:\.\d+)?|\))\s*%\s*de\s+(\d+(?:\.\d+)?|\()""").replace(w, "$1/100*$2")
        w = Regex("""(\d+(?:\.\d+)?|\))\s+porciento\s+de\s+(\d+(?:\.\d+)?|\()""")
            .replace(w, "$1/100*$2")
        w = Regex("""(\d+(?:\.\d+)?)\s+porciento\b""").replace(w, "$1/100")
        w = Regex("""(\d+(?:\.\d+)?)%(?!\d)""").replace(w, "$1/100")
        if (Regex("""\bporciento\b""").containsMatchIn(w)) return null

        // Operadores en palabra (largas primero: `dividido por` antes que `por`).
        w = Regex("""\bdividido\s+por\b""").replace(w, " / ")
        w = Regex("""\bdividido\s+entre\b""").replace(w, " / ")
        w = Regex("""\bentre\b""").replace(w, " / ")
        w = Regex("""\bpor\b""").replace(w, " * ")
        w = Regex("""\bmas\b""").replace(w, " + ")
        w = Regex("""\bmenos\b""").replace(w, " - ")
        // `y` solo es `+` entre operandos (`suma 3 y 4`); en el resto es null.
        w = Regex("""(?<=[0-9)])\s+y\s+(?=[0-9s(])""").replace(w, "+")

        // Decimales/miles ES: puntos de miles fuera, coma decimal a punto.
        w = Regex("""(?<=\d)\.(?=\d{3}(?!\d))""").replace(w, "")
        w = Regex(""",(?=\d{3}(?!\d))""").replace(w, "")
        w = Regex("""(?<=\d),(?=\d)""").replace(w, ".")

        w = w.replace(Regex("""\s+"""), "")
        if (w.isEmpty() || w.length > MathEvaluator.MAX_LENGTH) return null
        if (!w.any { it.isDigit() }) return null
        // Sin operador ni sqrt es un número suelto (`cuanto es 5`) → null
        // (fall-through legacy, nunca eco). `-5` SÍ emite (unario completo).
        if (!w.contains("sqrt") && !w.any { it in "+-*/%^" }) return null
        if (!Regex("""^(?:sqrt|[0-9.+\-*/%^()])+$""").matches(w)) return null
        if (w.last() in "+-*/%^(" || w.first() in "*/%^)") return null
        if (!parentesisBalanceados(w)) return null
        var profundidad = 0
        for (c in w) {
            if (c == '(') {
                profundidad++
                if (profundidad > MathEvaluator.MAX_DEPTH) return null
            } else if (c == ')') profundidad--
        }
        return w
    }

    private fun parentesisBalanceados(w: String): Boolean {
        var nivel = 0
        for (c in w) {
            if (c == '(') nivel++
            if (c == ')') {
                nivel--
                if (nivel < 0) return false
            }
        }
        return nivel == 0
    }

    private fun sinTildes(s: String): String =
        s.replace('á', 'a').replace('é', 'e').replace('í', 'i')
            .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')
            .replace('ñ', 'n')
}
