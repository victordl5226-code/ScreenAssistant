package com.screenassistant.core.domain.usecase

import com.screenassistant.core.domain.model.CommandMarkers
import com.screenassistant.core.domain.model.CommandSequence
import com.screenassistant.core.domain.model.CommandStep
import com.screenassistant.core.domain.model.ConnectorType
import com.screenassistant.core.domain.model.SystemCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests para SequenceParserImpl - detección de conectores multi-paso.
 * Contrato puro: sin mocks, 100% testeable unitario.
 */
class SequenceParserTest {

    private val parser = SequenceParserImpl()

    @Test
    fun `parse primero luego conector válido devuelve 2 pasos`() {
        val result = parser.parse("primero pon alarma a las 7, luego busca el tiempo")!!

        assertEquals(ConnectorType.PRIMERO_LUEGO, result.connectorType)
        assertEquals(2, result.steps.size)
        assertTrue(result.steps[0] is CommandStep.Command)
        assertTrue(result.steps[1] is CommandStep.Command)
    }

    @Test
    fun `parse primero luego con coma devuelve 2 pasos`() {
        val result = parser.parse("primero pon alarma a las 7, luego busca el tiempo")!!

        assertEquals(ConnectorType.PRIMERO_LUEGO, result.connectorType)
        assertEquals(2, result.steps.size)
    }

    @Test
    fun `parse y después conector válido devuelve 2 pasos`() {
        val result = parser.parse("pon alarma a las 7 y después busca el tiempo")!!

        assertEquals(ConnectorType.Y_DESPUES, result.connectorType)
        assertEquals(2, result.steps.size)
    }

    @Test
    fun `parse y despues sin tilde devuelve 2 pasos`() {
        val result = parser.parse("pon alarma a las 7 y despues busca el tiempo")!!

        assertEquals(ConnectorType.Y_DESPUES, result.connectorType)
        assertEquals(2, result.steps.size)
    }

    @Test
    fun `parse y luego conector válido devuelve 2 pasos`() {
        val result = parser.parse("pon alarma a las 7 y luego busca el tiempo")!!

        assertEquals(ConnectorType.Y_LUEGO, result.connectorType)
        assertEquals(2, result.steps.size)
    }

    @Test
    fun `parse coma luego conector válido devuelve 2 pasos`() {
        val result = parser.parse("pon alarma a las 7, luego busca el tiempo")!!

        assertEquals(ConnectorType.COMA_LUEGO, result.connectorType)
        assertEquals(2, result.steps.size)
    }

    @Test
    fun `parse punto y coma luego conector válido devuelve 2 pasos`() {
        val result = parser.parse("pon alarma a las 7; luego busca el tiempo")!!

        assertEquals(ConnectorType.PUNTOCOMA_LUEGO, result.connectorType)
        assertEquals(2, result.steps.size)
    }

    @Test
    fun `parse sin conectores devuelve null`() {
        val result = parser.parse("pon alarma a las 7")

        assertNull(result)
    }

    @Test
    fun `parse conector parcial primero sin luego devuelve null`() {
        val result = parser.parse("primero pon alarma a las 7")

        assertNull(result)
    }

    @Test
    fun `parse case insensitive detecta conectores`() {
        val result = parser.parse("PRIMERO PON ALARMA A LAS 7 LUEGO BUSCA EL TIEMPO")!!

        assertEquals(ConnectorType.PRIMERO_LUEGO, result.connectorType)
    }

    @Test
    fun `parse limite 10 pasos OK`() {
        val commands = List(10) { "pon alarma a las ${it + 1}" }.joinToString(" y luego ")
        val result = parser.parse(commands)!!

        assertEquals(10, result.steps.size)
    }

    @Test
    fun `parse 11 pasos devuelve null`() {
        val commands = List(11) { "pon alarma a las ${it + 1}" }.joinToString(" y luego ")
        val result = parser.parse(commands)

        assertNull(result)
    }

    @Test
    fun `parse CommandMarkers HELP en secuencia`() {
        val result = parser.parse("primero ayuda luego busca el tiempo")!!

        assertEquals(2, result.steps.size)
        assertTrue(result.steps[0] is CommandStep.Marker)
        assertEquals(CommandMarkers.HELP, (result.steps[0] as CommandStep.Marker).marker)
        assertTrue(result.steps[1] is CommandStep.Command)
    }

    @Test
    fun `parse CommandMarkers REPEAT en secuencia`() {
        val result = parser.parse("primero repite luego busca el tiempo")!!

        assertEquals(2, result.steps.size)
        assertTrue(result.steps[0] is CommandStep.Marker)
        assertEquals(CommandMarkers.REPEAT, (result.steps[0] as CommandStep.Marker).marker)
    }

    @Test
    fun `parse CommandMarkers START_MONITORING en secuencia`() {
        val result = parser.parse("primero activar monitoreo luego busca el tiempo")!!

        assertEquals(2, result.steps.size)
        assertTrue(result.steps[0] is CommandStep.Marker)
        assertEquals(CommandMarkers.START_MONITORING, (result.steps[0] as CommandStep.Marker).marker)
    }

    @Test
    fun `parse espacios extra normalizados`() {
        val result = parser.parse("  primero   pon alarma a las 7   ,   luego   busca el tiempo  ")!!

        assertEquals(2, result.steps.size)
    }

    @Test
    fun `parse texto vacío devuelve null`() {
        val result = parser.parse("")

        assertNull(result)
    }

    @Test
    fun `parse conector dentro de palabra no detecta`() {
        val result = parser.parse("primeroz pon alarma a las 7 luego busca")

        assertNull(result)
    }

    @Test
    fun `parse y luego encadenado 3 pasos`() {
        val result = parser.parse("pon alarma a las 7 y luego busca el tiempo y luego llama a Ana")!!

        assertEquals(3, result.steps.size)
    }

    @Test
    fun `parse precedencia primero luego gana sobre y luego`() {
        val result = parser.parse("primero pon alarma a las 7 y luego busca el tiempo")!!

        assertEquals(ConnectorType.PRIMERO_LUEGO, result.connectorType)
    }

    @Test
    fun `parse marker y command en secuencia`() {
        val result = parser.parse("primero ayuda luego pon alarma a las 7")!!

        assertEquals(2, result.steps.size)
        assertTrue(result.steps[0] is CommandStep.Marker)
        assertTrue(result.steps[1] is CommandStep.Command)
    }

    @Test
    fun `parse command y marker en secuencia`() {
        val result = parser.parse("primero pon alarma a las 7 luego ayuda")!!

        assertEquals(2, result.steps.size)
        assertTrue(result.steps[0] is CommandStep.Command)
        assertTrue(result.steps[1] is CommandStep.Marker)
    }
}
