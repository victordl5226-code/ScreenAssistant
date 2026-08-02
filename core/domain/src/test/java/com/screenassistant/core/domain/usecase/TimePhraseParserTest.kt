package com.screenassistant.core.domain.usecase

import com.screenassistant.core.domain.model.HourMinute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TimePhraseParser: horas habladas en español para alarmas.
 *  - Sin período → 24h literal ("las siete" → 7:00, "las veintiuna" → 21:00)
 *  - "menos cuarto" → minute=45 y hora con floorMod (nunca -1, B2)
 *  - "y <palabra no soportada>" → rechazo completo (B3, nunca hora parcial silenciosa)
 *  - Lookbehind no-letra: "sala 4"/"clasificar" NO son horas (M1)
 *  - findMatch expone matchEnd para extraer la etiqueta "para X" tras la hora
 */
class TimePhraseParserTest {

    // ===== Horas en palabra (24h literal sin período) =====

    @Test
    fun `las siete es 7 00`() {
        assertEquals(HourMinute(7, 0), TimePhraseParser.parse("las siete"))
    }

    @Test
    fun `la una es 1 00`() {
        assertEquals(HourMinute(1, 0), TimePhraseParser.parse("la una"))
    }

    @Test
    fun `las cero es 0 00`() {
        assertEquals(HourMinute(0, 0), TimePhraseParser.parse("las cero"))
    }

    @Test
    fun `las doce es 12 00`() {
        assertEquals(HourMinute(12, 0), TimePhraseParser.parse("las doce"))
    }

    @Test
    fun `las veintiuna es 21 00`() {
        assertEquals(HourMinute(21, 0), TimePhraseParser.parse("las veintiuna"))
    }

    @Test
    fun `las veintitres es 23 00`() {
        assertEquals(HourMinute(23, 0), TimePhraseParser.parse("las veintitres"))
    }

    @Test
    fun `las veinticinco no es una hora valida`() {
        assertNull(TimePhraseParser.parse("las veinticinco"))
    }

    @Test
    fun `las treinta no es una hora valida`() {
        assertNull(TimePhraseParser.parse("las treinta"))
    }

    // ===== Modificadores: en punto / y X / menos cuarto =====

    @Test
    fun `las siete en punto es 7 00`() {
        assertEquals(HourMinute(7, 0), TimePhraseParser.parse("las siete en punto"))
    }

    @Test
    fun `las siete y media es 7 30`() {
        assertEquals(HourMinute(7, 30), TimePhraseParser.parse("las siete y media"))
    }

    @Test
    fun `las siete y cuarto es 7 15`() {
        assertEquals(HourMinute(7, 15), TimePhraseParser.parse("las siete y cuarto"))
    }

    @Test
    fun `las siete y cinco es 7 05`() {
        assertEquals(HourMinute(7, 5), TimePhraseParser.parse("las siete y cinco"))
    }

    @Test
    fun `las siete y diez es 7 10`() {
        assertEquals(HourMinute(7, 10), TimePhraseParser.parse("las siete y diez"))
    }

    @Test
    fun `las siete y veinte es 7 20`() {
        assertEquals(HourMinute(7, 20), TimePhraseParser.parse("las siete y veinte"))
    }

    @Test
    fun `las siete y veinticinco es 7 25`() {
        assertEquals(HourMinute(7, 25), TimePhraseParser.parse("las siete y veinticinco"))
    }

    @Test
    fun `las siete y 10 con digitos es 7 10`() {
        // Alineado con el diseño O9: "Dígitos: \d{1,2} se mantiene" — resolveMinute resuelve
        // dígitos 0-59; la validación final rechaza los fuera de rango ("y 75" → null).
        assertEquals(HourMinute(7, 10), TimePhraseParser.parse("las siete y 10"))
    }

    @Test
    fun `las siete menos cuarto es 6 45`() {
        assertEquals(HourMinute(6, 45), TimePhraseParser.parse("las siete menos cuarto"))
    }

    @Test
    fun `las cero menos cuarto usa floorMod y es 23 45`() {
        assertEquals(HourMinute(23, 45), TimePhraseParser.parse("las cero menos cuarto"))
    }

    @Test
    fun `las doce menos cuarto de la noche es 23 45`() {
        // O3: el modificador "menos cuarto" se aplica ANTES del período: la hora ya no es
        // 12 cuando llega al caso especial de la noche → 23:45 (no 0:45)
        assertEquals(HourMinute(23, 45), TimePhraseParser.parse("las doce menos cuarto de la noche"))
    }

    // ===== O9: minutos hablados completos 0-59 (y X / menos X) =====

    @Test
    fun `las siete y cero es 7 0`() {
        assertEquals(HourMinute(7, 0), TimePhraseParser.parse("las siete y cero"))
    }

    @Test
    fun `las siete y tres es 7 3`() {
        assertEquals(HourMinute(7, 3), TimePhraseParser.parse("las siete y tres"))
    }

    @Test
    fun `las siete y once es 7 11`() {
        assertEquals(HourMinute(7, 11), TimePhraseParser.parse("las siete y once"))
    }

    @Test
    fun `las siete y treinta y cinco es 7 35`() {
        assertEquals(HourMinute(7, 35), TimePhraseParser.parse("las siete y treinta y cinco"))
    }

    @Test
    fun `las siete y cincuenta y nueve es 7 59`() {
        assertEquals(HourMinute(7, 59), TimePhraseParser.parse("las siete y cincuenta y nueve"))
    }

    @Test
    fun `las siete y veinte y uno es 7 21`() {
        // Decisión del Arquitecto (opción A): "veinte" admite compuesto arcaico "X y Y"
        // con token2 = unidad 1-9. "veintiuno" (una palabra) sigue funcionando también.
        assertEquals(HourMinute(7, 21), TimePhraseParser.parse("las siete y veinte y uno"))
    }

    @Test
    fun `las siete y veinte y veinte se rechaza`() {
        // Regla estricta aprobada: token2 solo unidades 1-9 ("veinte y veinte" no es español)
        assertNull(TimePhraseParser.parse("las siete y veinte y veinte"))
    }

    @Test
    fun `las siete y cero de la tarde es 19 0`() {
        assertEquals(HourMinute(19, 0), TimePhraseParser.parse("las siete y cero de la tarde"))
    }

    @Test
    fun `las siete y cinco de la manana es 7 5`() {
        assertEquals(HourMinute(7, 5), TimePhraseParser.parse("las siete y cinco de la manana"))
    }

    @Test
    fun `las tres menos cinco es 2 55`() {
        assertEquals(HourMinute(2, 55), TimePhraseParser.parse("las tres menos cinco"))
    }

    @Test
    fun `las tres menos cero se rechaza por rango`() {
        assertNull(TimePhraseParser.parse("las tres menos cero"))
    }

    @Test
    fun `las siete y sesenta se rechaza`() {
        assertNull(TimePhraseParser.parse("las siete y sesenta"))
    }

    @Test
    fun `las siete y ochenta y cinco se rechaza`() {
        assertNull(TimePhraseParser.parse("las siete y ochenta y cinco"))
    }

    @Test
    fun `las siete y perro se rechaza`() {
        assertNull(TimePhraseParser.parse("las siete y perro"))
    }

    @Test
    fun `las siete y cuarto y media se rechaza`() {
        assertNull(TimePhraseParser.parse("las siete y cuarto y media"))
    }

    @Test
    fun `las tres menos veinte es 2 40`() {
        assertEquals(HourMinute(2, 40), TimePhraseParser.parse("las tres menos veinte"))
    }

    @Test
    fun `las siete y treinta es 7 30`() {
        assertEquals(HourMinute(7, 30), TimePhraseParser.parse("las siete y treinta"))
    }

    // ===== MIN-dígito: token2 dígito 1-9 en el compuesto "decena y unidad" (ADR-TMP-7) =====

    @Test
    fun `las siete y veinte y 5 es 7 25`() {
        // Antes "y 5" se descartaba silenciosamente (regex de token2 solo [a-z]+)
        assertEquals(HourMinute(7, 25), TimePhraseParser.parse("las siete y veinte y 5"))
    }

    @Test
    fun `las siete y veinte y 05 es 7 25`() {
        assertEquals(HourMinute(7, 25), TimePhraseParser.parse("las siete y veinte y 05"))
    }

    @Test
    fun `las siete menos veinte y 5 es 6 35`() {
        assertEquals(HourMinute(6, 35), TimePhraseParser.parse("las siete menos veinte y 5"))
    }

    @Test
    fun `las siete y veinte y 10 se rechaza`() {
        // token2 dígito recibe la MISMA validación 1-9 que las palabras → 10 rechazado
        assertNull(TimePhraseParser.parse("las siete y veinte y 10"))
    }

    @Test
    fun `las siete y media y 5 se rechaza`() {
        // token1 "media" no es decena → compuesto inválido (B3)
        assertNull(TimePhraseParser.parse("las siete y media y 5"))
    }

    @Test
    fun `las siete y cuarto y 5 se rechaza`() {
        assertNull(TimePhraseParser.parse("las siete y cuarto y 5"))
    }

    @Test
    fun `las siete y veinte y 5 de la noche es 19 25`() {
        assertEquals(HourMinute(19, 25), TimePhraseParser.parse("las siete y veinte y 5 de la noche"))
    }

    // ===== Hora numérica =====

    @Test
    fun `las 7 es 7 00`() {
        assertEquals(HourMinute(7, 0), TimePhraseParser.parse("las 7"))
    }

    @Test
    fun `las 7 30 es 7 30`() {
        assertEquals(HourMinute(7, 30), TimePhraseParser.parse("las 7:30"))
    }

    @Test
    fun `las 19 45 es 19 45`() {
        assertEquals(HourMinute(19, 45), TimePhraseParser.parse("las 19:45"))
    }

    @Test
    fun `las 25 se rechaza por rango de hora`() {
        assertNull(TimePhraseParser.parse("las 25"))
    }

    @Test
    fun `las 7 75 se rechaza por rango de minuto`() {
        assertNull(TimePhraseParser.parse("las 7:75"))
    }

    // ===== Período am/pm =====

    @Test
    fun `las tres de la manana es 3 00`() {
        assertEquals(HourMinute(3, 0), TimePhraseParser.parse("las tres de la manana"))
    }

    @Test
    fun `las tres de la tarde es 15 00`() {
        assertEquals(HourMinute(15, 0), TimePhraseParser.parse("las tres de la tarde"))
    }

    @Test
    fun `las nueve de la noche es 21 00`() {
        assertEquals(HourMinute(21, 0), TimePhraseParser.parse("las nueve de la noche"))
    }

    @Test
    fun `las doce de la manana es 12 00`() {
        // O3 (cambio semántico aprobado por producto): las 12 son el mediodía, no la medianoche.
        assertEquals(HourMinute(12, 0), TimePhraseParser.parse("las doce de la manana"))
    }

    @Test
    fun `las doce de la noche es 0 00`() {
        // O3: "las doce de la noche" = medianoche = 0:00.
        assertEquals(HourMinute(0, 0), TimePhraseParser.parse("las doce de la noche"))
    }

    @Test
    fun `las doce y media de la noche es 0 30`() {
        assertEquals(HourMinute(0, 30), TimePhraseParser.parse("las doce y media de la noche"))
    }

    @Test
    fun `las once de la noche es 23 00`() {
        assertEquals(HourMinute(23, 0), TimePhraseParser.parse("las once de la noche"))
    }

    @Test
    fun `las once de la manana es 11 00`() {
        assertEquals(HourMinute(11, 0), TimePhraseParser.parse("las once de la manana"))
    }

    @Test
    fun `las doce de la tarde es 12 00`() {
        assertEquals(HourMinute(12, 0), TimePhraseParser.parse("las doce de la tarde"))
    }

    @Test
    fun `la una de la tarde es 13 00`() {
        assertEquals(HourMinute(13, 0), TimePhraseParser.parse("la una de la tarde"))
    }

    @Test
    fun `las siete y media de la noche es 19 30`() {
        assertEquals(HourMinute(19, 30), TimePhraseParser.parse("las siete y media de la noche"))
    }

    // ===== Word boundary (M1): "la" debe ser palabra independiente =====

    @Test
    fun `sala 4 no se interpreta como hora`() {
        assertNull(TimePhraseParser.parse("sala 4"))
    }

    @Test
    fun `clasificar no se interpreta como hora`() {
        assertNull(TimePhraseParser.parse("clasificar"))
    }

    // ===== Texto embebido y matchEnd (etiqueta "para X") =====

    @Test
    fun `parse encuentra la hora en medio de una frase`() {
        assertEquals(HourMinute(7, 30), TimePhraseParser.parse("pon una alarma a las 7:30"))
    }

    @Test
    fun `findMatch devuelve el final exacto del match al terminar la frase`() {
        val text = "pon una alarma a las 7:30"
        val match = TimePhraseParser.findMatch(text)!!

        assertEquals(HourMinute(7, 30), match.time)
        assertEquals(text.length, match.matchEnd)
    }

    @Test
    fun `findMatch deja la etiqueta despues del match`() {
        val text = "pon una alarma a las 7:30 para despertarme"
        val match = TimePhraseParser.findMatch(text)!!

        assertTrue(text.indexOf(" para ", match.matchEnd) >= 0)
    }
}
