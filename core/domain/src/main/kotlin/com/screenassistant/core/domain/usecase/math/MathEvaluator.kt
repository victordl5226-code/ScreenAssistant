package com.screenassistant.core.domain.usecase.math

import com.screenassistant.core.domain.model.MathResult
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Evaluador aritmético puro (ADR-MATH Nivel 1, P1).
 *
 * Recursive descent SIN eval/ScriptEngine/reflection — la allowlist de charset
 * + los límites de longitud/profundidad son la mitigación de inyección (§11).
 *
 * Precondición: `canonical` solo dígitos, ".", "+", "-", "*", "/", "%", "^",
 * "(", ")", espacio y el token "sqrt".
 * Gramática:
 * ```
 * expr    := term (('+'|'-') term)*
 * term    := factor (('*'|'/'|'%') factor)*      // '%' binario = resto
 * factor  := unary ('^' factor)?                 // '^' right-assoc
 * unary   := '-' unary | primary
 * primary := número | 'sqrt' '(' expr ')' | '(' expr ')' | átomo '%' postfijo
 * ```
 * Precedencia: `()` > `sqrt`/unario > `^` (right) > `* / %` (left) > `+ -` (left).
 *
 * Límites: longitud ≤ 200, profundidad de paréntesis ≤ 10 → [MathResult.Error].
 * `%` postfijo (p. ej. `50%` suelto ya reescrito por el normalizador a `50/100`,
 * pero también aceptado aquí como `/100`) se distingue del `%` binario por
 * lookahead: si tras `%` sigue número/`(`/`sqrt` es resto binario; si no, /100.
 */
object MathEvaluator {

    const val MAX_LENGTH: Int = 200
    const val MAX_DEPTH: Int = 10

    fun evaluate(canonical: String): MathResult {
        if (canonical.length > MAX_LENGTH || canonical.isBlank()) {
            return MathResult.Error(MathResult.EXPRESION_INVALIDA)
        }
        val tokens = try {
            tokenize(canonical)
        } catch (_: ParseError) {
            return MathResult.Error(MathResult.EXPRESION_INVALIDA)
        }
        if (maxParenDepth(tokens) > MAX_DEPTH) {
            return MathResult.Error(MathResult.EXPRESION_INVALIDA)
        }
        return try {
            val parser = Parser(tokens)
            val value = parser.parseExpr()
            parser.expectEof()
            when {
                value.isNaN() -> MathResult.Error(MathResult.EXPRESION_INVALIDA)
                !value.isFinite() -> MathResult.Error(MathResult.NUMERO_FUERA_DE_RANGO)
                else -> MathResult.Success(value)
            }
        } catch (e: ParseError) {
            MathResult.Error(e.reason)
        }
    }

    // ===== Tokens =====

    private sealed interface Token {
        data class Number(val value: Double) : Token
        data object Plus : Token
        data object Minus : Token
        data object Star : Token
        data object Slash : Token
        data object Percent : Token
        data object Caret : Token
        data object LParen : Token
        data object RParen : Token
        data object Sqrt : Token
        data object Eof : Token
    }

    private class ParseError(val reason: String) : Exception()

    private fun tokenize(input: String): List<Token> {
        val out = ArrayList<Token>(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c == ' ' -> i++
                c.isDigit() || c == '.' -> {
                    var j = i
                    while (j < input.length && (input[j].isDigit() || input[j] == '.')) j++
                    val literal = input.substring(i, j)
                    // Forma válida: 12, 12.5, .5 — nunca ".", "1.2.3" ni vacío.
                    val ok = literal.any { it.isDigit() } &&
                        literal.count { it == '.' } <= 1
                    if (!ok) throw ParseError(MathResult.EXPRESION_INVALIDA)
                    val value = literal.toDoubleOrNull()
                        ?: throw ParseError(MathResult.EXPRESION_INVALIDA)
                    if (!value.isFinite()) {
                        throw ParseError(MathResult.NUMERO_FUERA_DE_RANGO)
                    }
                    out.add(Token.Number(value))
                    i = j
                }
                c.isLetter() -> {
                    if (input.startsWith("sqrt", i)) {
                        out.add(Token.Sqrt)
                        i += 4
                    } else {
                        // Cualquier otra letra (inyección: "__import__", "rm", "e") → rechazo.
                        throw ParseError(MathResult.EXPRESION_INVALIDA)
                    }
                }
                c == '+' -> { out.add(Token.Plus); i++ }
                c == '-' -> { out.add(Token.Minus); i++ }
                c == '*' -> { out.add(Token.Star); i++ }
                c == '/' -> { out.add(Token.Slash); i++ }
                c == '%' -> { out.add(Token.Percent); i++ }
                c == '^' -> { out.add(Token.Caret); i++ }
                c == '(' -> { out.add(Token.LParen); i++ }
                c == ')' -> { out.add(Token.RParen); i++ }
                else -> throw ParseError(MathResult.EXPRESION_INVALIDA)
            }
        }
        out.add(Token.Eof)
        return out
    }

    private fun maxParenDepth(tokens: List<Token>): Int {
        var depth = 0
        var max = 0
        for (t in tokens) {
            when (t) {
                is Token.LParen -> { depth++; if (depth > max) max = depth }
                is Token.RParen -> depth--
                else -> {}
            }
        }
        return max
    }

    // ===== Parser =====

    private class Parser(private val tokens: List<Token>) {
        private var pos: Int = 0

        private fun peek(): Token = tokens[pos]
        private fun peekNext(): Token = tokens.getOrElse(pos + 1) { Token.Eof }
        private fun next(): Token = tokens[pos++]

        fun expectEof() {
            if (peek() != Token.Eof) throw ParseError(MathResult.EXPRESION_INVALIDA)
        }

        fun parseExpr(): Double {
            var value = parseTerm()
            while (true) {
                when (peek()) {
                    is Token.Plus -> { next(); value += parseTerm() }
                    is Token.Minus -> { next(); value -= parseTerm() }
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (true) {
                when (peek()) {
                    is Token.Star -> { next(); value *= parseFactor() }
                    is Token.Slash -> {
                        next()
                        val rhs = parseFactor()
                        if (rhs == 0.0) throw ParseError(MathResult.DIVISION_POR_CERO)
                        value /= rhs
                    }
                    is Token.Percent -> {
                        next()
                        val rhs = parseFactor()
                        if (rhs == 0.0) throw ParseError(MathResult.DIVISION_POR_CERO)
                        value %= rhs
                    }
                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double {
            val base = parseUnary()
            return if (peek() is Token.Caret) {
                next()
                val exp = parseFactor() // right-assoc: 2^3^2 = 2^(3^2) = 512
                val result = base.pow(exp)
                when {
                    result.isNaN() -> throw ParseError(MathResult.EXPRESION_INVALIDA)
                    !result.isFinite() -> throw ParseError(MathResult.NUMERO_FUERA_DE_RANGO)
                    else -> result
                }
            } else {
                base
            }
        }

        private fun parseUnary(): Double {
            return if (peek() is Token.Minus) {
                next()
                -parseUnary()
            } else {
                parsePrimary()
            }
        }

        private fun parsePrimary(): Double {
            var value = when (val t = next()) {
                is Token.Number -> t.value
                is Token.Sqrt -> {
                    if (next() != Token.LParen) throw ParseError(MathResult.EXPRESION_INVALIDA)
                    val inner = parseExpr()
                    if (next() != Token.RParen) throw ParseError(MathResult.EXPRESION_INVALIDA)
                    if (inner < 0.0) throw ParseError(MathResult.EXPRESION_INVALIDA)
                    sqrt(inner)
                }
                is Token.LParen -> {
                    val inner = parseExpr()
                    if (next() != Token.RParen) throw ParseError(MathResult.EXPRESION_INVALIDA)
                    inner
                }
                else -> throw ParseError(MathResult.EXPRESION_INVALIDA)
            }
            // '%' postfijo (= /100) salvo que abra un resto binario: tras '%' sigue
            // número / '(' / 'sqrt' → se deja para el nivel term.
            while (peek() is Token.Percent) {
                when (peekNext()) {
                    is Token.Number, is Token.LParen, is Token.Sqrt -> break
                    else -> { next(); value /= 100.0 }
                }
            }
            return value
        }
    }
}
