package com.screenassistant.core.domain.usecase

import com.screenassistant.core.domain.action.SystemAction
import com.screenassistant.core.domain.model.ActionResult
import com.screenassistant.core.domain.model.AssistantLanguage
import com.screenassistant.core.domain.model.CommandMarkers
import com.screenassistant.core.domain.model.SystemCommand
import com.screenassistant.core.domain.model.VolumeAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * SystemCommandParser contra la lógica real:
 *  - "llama a X" / "llamar a X" → SystemCommand.Call
 *  - "llama al <número>" → SystemCommand.CallNumber (sin consultar contactos)
 *  - "busca X" / "buscar X" → SystemCommand.SearchGoogle
 *  - "alarma" con hora hablada → SystemCommand.SetAlarm(h, m, label opcional);
 *    sin hora → SystemCommand.OpenAlarms
 *  - ayuda/repite → marcadores CommandMarkers.HELP/REPEAT (sin pasar por el handler)
 *  - acciones fallidas → "Error: <razón>" (prefijo que OverlayViewModel.handleResponse limpia)
 *  - sin comando → null (se delega a Gemini)
 */
class SystemCommandParserTest {

    private lateinit var systemAction: SystemAction
    private lateinit var parser: SystemCommandParser

    @Before
    fun setup() {
        systemAction = mockk()
        parser = SystemCommandParser(systemAction)
    }

    @Test
    fun `parse de llamada devuelve el mensaje y ejecuta Call con el contacto`() {
        coEvery { systemAction.execute(SystemCommand.Call("Ana")) } returns
            ActionResult.Success("Llamando a Ana...")

        val result = parser.parse("llama a Ana")

        assertEquals("Llamando a Ana...", result)
        coVerify { systemAction.execute(SystemCommand.Call("Ana")) }
    }

    @Test
    fun `parse de busqueda devuelve el mensaje y ejecuta SearchGoogle con la query`() {
        coEvery { systemAction.execute(SystemCommand.SearchGoogle("gatos")) } returns
            ActionResult.Success("Buscando gatos...")

        val result = parser.parse("busca gatos")

        assertEquals("Buscando gatos...", result)
        coVerify { systemAction.execute(SystemCommand.SearchGoogle("gatos")) }
    }

    @Test
    fun `parse de alarma con hora 7 30 ejecuta SetAlarm sin etiqueta`() {
        coEvery { systemAction.execute(SystemCommand.SetAlarm(7, 30, null)) } returns
            ActionResult.Success("Alarma a las 7:30.")

        val result = parser.parse("pon una alarma a las 7:30")

        assertEquals("Alarma a las 7:30.", result)
        coVerify { systemAction.execute(SystemCommand.SetAlarm(7, 30, null)) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenAlarms) }
    }

    @Test
    fun `parse de texto sin comando devuelve null y no ejecuta ninguna accion`() {
        val result = parser.parse("texto sin comando")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse devuelve prefijo Error cuando la accion falla`() {
        // M26: stub TIPADO (antes coEvery(any()) no detectaba ramas no ejecutadas).
        coEvery { systemAction.execute(SystemCommand.Call("Ana")) } returns
            ActionResult.Error("no pude llamar")

        val result = parser.parse("llama a Ana")

        assertEquals("Error: no pude llamar", result)
        // El stub TIPADO ya es la aserción fuerte: si el parser ejecutara otra
        // acción (SearchGoogle/OpenApp), MockK lanzaría "no answer found" → rojo.
        coVerify { systemAction.execute(SystemCommand.Call("Ana")) }
    }

    // ===== M26: casos de voz reales — comportamiento FIJADO (null → Gemini) =====

    @Test
    fun `parse a las 7 y media suelto devuelve null y no ejecuta ninguna accion`() {
        // Respuesta a "¿a qué hora?" SIN la palabra "alarma": no es un comando
        // (solo la rama alarma usa el TimePhraseParser) → se delega a Gemini.
        val result = parser.parse("a las 7 y media")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse temporizador de 5 sin unidad devuelve null y no ejecuta ninguna accion`() {
        // "temporizador de 5" sin unidad de duración: la rama 7 exige unidad
        // (minutos/segundos/horas) → no es un comando fiable → Gemini.
        val result = parser.parse("temporizador de 5")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse las 7 suelto devuelve null y no ejecuta ninguna accion`() {
        // Hora suelta sin verbo ni contexto: no matchea ningún prefijo → Gemini.
        val result = parser.parse("las 7")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    // ===== Tests de la liquidación de deuda técnica (abre/abrir + fixes de case y 'r' colgante) =====

    @Test
    fun `parse abre whatsapp devuelve mensaje y ejecuta OpenApp con la query`() {
        coEvery { systemAction.execute(SystemCommand.OpenApp("whatsapp")) } returns
            ActionResult.Success("Abriendo WhatsApp...")

        val result = parser.parse("abre whatsapp")

        assertEquals("Abriendo WhatsApp...", result)
        coVerify { systemAction.execute(SystemCommand.OpenApp("whatsapp")) }
        // Ramas no ejecutadas (B5)
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenAlarms) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.Call("cualquier contacto")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.SearchGoogle("cualquier query")) }
    }

    @Test
    fun `parse abrir spotify ejecuta OpenApp con la query`() {
        coEvery { systemAction.execute(SystemCommand.OpenApp("spotify")) } returns
            ActionResult.Success("Abriendo Spotify...")

        val result = parser.parse("abrir spotify")

        assertEquals("Abriendo Spotify...", result)
        coVerify { systemAction.execute(SystemCommand.OpenApp("spotify")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenAlarms) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.Call("cualquier contacto")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.SearchGoogle("cualquier query")) }
    }

    @Test
    fun `parse ABRE WhatsApp ejecuta OpenApp preservando el case del argumento`() {
        coEvery { systemAction.execute(SystemCommand.OpenApp("WhatsApp")) } returns
            ActionResult.Success("Abriendo WhatsApp...")

        val result = parser.parse("ABRE WhatsApp")

        assertEquals("Abriendo WhatsApp...", result)
        coVerify { systemAction.execute(SystemCommand.OpenApp("WhatsApp")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenAlarms) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.Call("cualquier contacto")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.SearchGoogle("cualquier query")) }
    }

    @Test
    fun `parse LLAMA A Ana en mayusculas extrae solo el contacto preservando su case`() {
        coEvery { systemAction.execute(SystemCommand.Call("Ana")) } returns
            ActionResult.Success("Llamando a Ana...")

        val result = parser.parse("LLAMA A Ana")

        assertEquals("Llamando a Ana...", result)
        coVerify { systemAction.execute(SystemCommand.Call("Ana")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenAlarms) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenApp("cualquier app")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.SearchGoogle("cualquier query")) }
    }

    @Test
    fun `parse BUSCA GATOS en mayusculas ejecuta SearchGoogle con la query preservada`() {
        coEvery { systemAction.execute(SystemCommand.SearchGoogle("GATOS")) } returns
            ActionResult.Success("Buscando GATOS...")

        val result = parser.parse("BUSCA GATOS")

        assertEquals("Buscando GATOS...", result)
        coVerify { systemAction.execute(SystemCommand.SearchGoogle("GATOS")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenAlarms) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenApp("cualquier app")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.Call("cualquier contacto")) }
    }

    @Test
    fun `parse abre la alarma ejecuta OpenApp y no la heuristica de alarmas`() {
        coEvery { systemAction.execute(SystemCommand.OpenApp("la alarma")) } returns
            ActionResult.Success("Abriendo la app...")

        val result = parser.parse("abre la alarma")

        assertEquals("Abriendo la app...", result)
        coVerify { systemAction.execute(SystemCommand.OpenApp("la alarma")) }
        // El verbo explícito gana a la heurística contains("alarma")
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenAlarms) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.Call("cualquier contacto")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.SearchGoogle("cualquier query")) }
    }

    @Test
    fun `parse abre sin argumento devuelve null y no ejecuta ninguna accion`() {
        val result = parser.parse("abre")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse de OpenApp fallido devuelve prefijo Error con la razon`() {
        coEvery { systemAction.execute(SystemCommand.OpenApp("whatsapp")) } returns
            ActionResult.Error("no tengo permiso")

        val result = parser.parse("abre whatsapp")

        assertEquals("Error: no tengo permiso", result)
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenAlarms) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.Call("cualquier contacto")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.SearchGoogle("cualquier query")) }
    }

    @Test
    fun `parse buscar gatos extrae la query sin la r colgante`() {
        coEvery { systemAction.execute(SystemCommand.SearchGoogle("gatos")) } returns
            ActionResult.Success("Buscando gatos...")

        val result = parser.parse("buscar gatos")

        assertEquals("Buscando gatos...", result)
        coVerify { systemAction.execute(SystemCommand.SearchGoogle("gatos")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenAlarms) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenApp("cualquier app")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.Call("cualquier contacto")) }
    }

    @Test
    fun `parse BUSCAR gatos con verbo en mayusculas extrae la query sin la r colgante`() {
        coEvery { systemAction.execute(SystemCommand.SearchGoogle("gatos")) } returns
            ActionResult.Success("Buscando gatos...")

        val result = parser.parse("BUSCAR gatos")

        assertEquals("Buscando gatos...", result)
        coVerify { systemAction.execute(SystemCommand.SearchGoogle("gatos")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenAlarms) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenApp("cualquier app")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.Call("cualquier contacto")) }
    }

    @Test
    fun `parse ABRIR Spotify ejecuta OpenApp con el case preservado`() {
        coEvery { systemAction.execute(SystemCommand.OpenApp("Spotify")) } returns
            ActionResult.Success("Abriendo Spotify...")

        val result = parser.parse("ABRIR Spotify")

        assertEquals("Abriendo Spotify...", result)
        coVerify { systemAction.execute(SystemCommand.OpenApp("Spotify")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenAlarms) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.Call("cualquier contacto")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.SearchGoogle("cualquier query")) }
    }

    @Test
    fun `parse LLAMAR a Ana ejecuta Call con el contacto extraido`() {
        coEvery { systemAction.execute(SystemCommand.Call("Ana")) } returns
            ActionResult.Success("Llamando a Ana...")

        val result = parser.parse("LLAMAR a Ana")

        assertEquals("Llamando a Ana...", result)
        coVerify { systemAction.execute(SystemCommand.Call("Ana")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenAlarms) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenApp("cualquier app")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.SearchGoogle("cualquier query")) }
    }

    // ===== Fase A: ayuda → marcador HELP (sin handler) =====

    @Test
    fun `parse ayuda devuelve HELP sin ejecutar ninguna accion`() {
        val result = parser.parse("ayuda")

        assertEquals(CommandMarkers.HELP, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse que puedes hacer devuelve HELP`() {
        val result = parser.parse("que puedes hacer")

        assertEquals(CommandMarkers.HELP, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse comandos devuelve HELP`() {
        val result = parser.parse("comandos")

        assertEquals(CommandMarkers.HELP, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse ayuda por favor devuelve HELP`() {
        val result = parser.parse("ayuda por favor")

        assertEquals(CommandMarkers.HELP, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse pregunta en que me puedo ayudar devuelve HELP`() {
        val result = parser.parse("¿En qué me puedo ayudar?")

        assertEquals(CommandMarkers.HELP, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse en que me puedo ayudar sin signos devuelve HELP`() {
        val result = parser.parse("en que me puedo ayudar")

        assertEquals(CommandMarkers.HELP, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse que puedes hacer por mi devuelve HELP`() {
        val result = parser.parse("que puedes hacer por mi")

        assertEquals(CommandMarkers.HELP, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse pregunta con signos que puedes hacer devuelve HELP`() {
        val result = parser.parse("¿Qué puedes hacer?")

        assertEquals(CommandMarkers.HELP, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse pregunta ayuda con signos devuelve HELP`() {
        val result = parser.parse("¿Ayuda?")

        assertEquals(CommandMarkers.HELP, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse me puedes ayudar devuelve HELP`() {
        val result = parser.parse("¿Me puedes ayudar?")

        assertEquals(CommandMarkers.HELP, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse en que me puede ayudar ella devuelve HELP`() {
        val result = parser.parse("¿en qué me puede ayudar ella?")

        assertEquals(CommandMarkers.HELP, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse en que puedes ayudarme devuelve HELP`() {
        val result = parser.parse("¿En qué puedes ayudarme?")

        assertEquals(CommandMarkers.HELP, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse me puedes ayudar a poner una alarma no devuelve HELP y ejecuta la alarma`() {
        coEvery { systemAction.execute(SystemCommand.SetAlarm(7, 0, null)) } returns
            ActionResult.Success("Alarma a las 7:00.")

        val result = parser.parse("¿me puedes ayudar a poner una alarma a las 7?")

        // No es una petición de ayuda genérica: la rama 12 resuelve la alarma directamente
        assertNotEquals(CommandMarkers.HELP, result)
        assertEquals("Alarma a las 7:00.", result)
        coVerify { systemAction.execute(SystemCommand.SetAlarm(7, 0, null)) }
    }

    @Test
    fun `parse que puedo hacer devuelve HELP`() {
        val result = parser.parse("¿Qué puedo hacer?")

        assertEquals(CommandMarkers.HELP, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse que sabes hacer devuelve HELP`() {
        val result = parser.parse("¿Qué sabes hacer?")

        assertEquals(CommandMarkers.HELP, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse que comandos tienes devuelve HELP`() {
        val result = parser.parse("¿Qué comandos tienes?")

        assertEquals(CommandMarkers.HELP, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse para que sirves devuelve HELP`() {
        val result = parser.parse("¿Para qué sirves?")

        assertEquals(CommandMarkers.HELP, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse pregunta sobre la alarma devuelve HELP por la precedencia de la rama ayuda`() {
        val result = parser.parse("¿qué puedes hacer con la alarma?")

        assertEquals(CommandMarkers.HELP, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse piensa que puedes hacerlo devuelve null por el limite posterior de palabra`() {
        val result = parser.parse("piensa que puedes hacerlo")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse se que puedes hacerlo devuelve null por el limite posterior de palabra`() {
        val result = parser.parse("se que puedes hacerlo")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    // ===== Fase A: repite → marcador REPEAT (sin handler) =====

    @Test
    fun `parse repite devuelve REPEAT sin ejecutar ninguna accion`() {
        val result = parser.parse("repite")

        assertEquals(CommandMarkers.REPEAT, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse otra vez devuelve REPEAT`() {
        val result = parser.parse("otra vez")

        assertEquals(CommandMarkers.REPEAT, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse repite eso devuelve REPEAT`() {
        val result = parser.parse("repite eso")

        assertEquals(CommandMarkers.REPEAT, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    // ===== Fase A: volumen =====

    @Test
    fun `parse sube el volumen ejecuta SetVolume UP`() {
        coEvery { systemAction.execute(SystemCommand.SetVolume(VolumeAction.UP)) } returns
            ActionResult.Success("Volumen subido a 10.")

        val result = parser.parse("sube el volumen")

        assertEquals("Volumen subido a 10.", result)
        coVerify { systemAction.execute(SystemCommand.SetVolume(VolumeAction.UP)) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenAlarms) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.Call("cualquier contacto")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.SearchGoogle("cualquier query")) }
    }

    @Test
    fun `parse sube volumen sin articulo ejecuta SetVolume UP`() {
        coEvery { systemAction.execute(SystemCommand.SetVolume(VolumeAction.UP)) } returns
            ActionResult.Success("Volumen subido a 10.")

        val result = parser.parse("sube volumen")

        assertEquals("Volumen subido a 10.", result)
        coVerify { systemAction.execute(SystemCommand.SetVolume(VolumeAction.UP)) }
    }

    @Test
    fun `parse baja el volumen ejecuta SetVolume DOWN`() {
        coEvery { systemAction.execute(SystemCommand.SetVolume(VolumeAction.DOWN)) } returns
            ActionResult.Success("Volumen bajado a 0.")

        val result = parser.parse("baja el volumen")

        assertEquals("Volumen bajado a 0.", result)
        coVerify { systemAction.execute(SystemCommand.SetVolume(VolumeAction.DOWN)) }
    }

    @Test
    fun `parse silencio ejecuta SetVolume MUTE`() {
        coEvery { systemAction.execute(SystemCommand.SetVolume(VolumeAction.MUTE)) } returns
            ActionResult.Success("Teléfono en silencio.")

        val result = parser.parse("silencio")

        assertEquals("Teléfono en silencio.", result)
        coVerify { systemAction.execute(SystemCommand.SetVolume(VolumeAction.MUTE)) }
    }

    @Test
    fun `parse silencia ejecuta SetVolume MUTE`() {
        coEvery { systemAction.execute(SystemCommand.SetVolume(VolumeAction.MUTE)) } returns
            ActionResult.Success("Teléfono en silencio.")

        val result = parser.parse("silencia")

        assertEquals("Teléfono en silencio.", result)
        coVerify { systemAction.execute(SystemCommand.SetVolume(VolumeAction.MUTE)) }
    }

    @Test
    fun `parse sube el volumen al maximo ejecuta SetVolume MAX`() {
        coEvery { systemAction.execute(SystemCommand.SetVolume(VolumeAction.MAX)) } returns
            ActionResult.Success("Volumen al máximo.")

        val result = parser.parse("sube el volumen al maximo")

        assertEquals("Volumen al máximo.", result)
        coVerify { systemAction.execute(SystemCommand.SetVolume(VolumeAction.MAX)) }
    }

    @Test
    fun `parse sube el volumen al minimo ejecuta SetVolume MIN`() {
        coEvery { systemAction.execute(SystemCommand.SetVolume(VolumeAction.MIN)) } returns
            ActionResult.Success("Volumen al mínimo.")

        val result = parser.parse("sube el volumen al minimo")

        assertEquals("Volumen al mínimo.", result)
        coVerify { systemAction.execute(SystemCommand.SetVolume(VolumeAction.MIN)) }
    }

    @Test
    fun `parse silencio por favor ejecuta SetVolume MUTE`() {
        coEvery { systemAction.execute(SystemCommand.SetVolume(VolumeAction.MUTE)) } returns
            ActionResult.Success("Teléfono en silencio.")

        val result = parser.parse("silencio por favor")

        assertEquals("Teléfono en silencio.", result)
        coVerify { systemAction.execute(SystemCommand.SetVolume(VolumeAction.MUTE)) }
    }

    // ===== Fase A: idioma =====

    @Test
    fun `parse habla en ingles ejecuta SetLanguage ENGLISH`() {
        coEvery { systemAction.execute(SystemCommand.SetLanguage(AssistantLanguage.ENGLISH)) } returns
            ActionResult.Success("Entendido. A partir de ahora hablaré en inglés.")

        val result = parser.parse("habla en ingles")

        assertEquals("Entendido. A partir de ahora hablaré en inglés.", result)
        coVerify { systemAction.execute(SystemCommand.SetLanguage(AssistantLanguage.ENGLISH)) }
    }

    @Test
    fun `parse cambia a espanol ejecuta SetLanguage SPANISH`() {
        coEvery { systemAction.execute(SystemCommand.SetLanguage(AssistantLanguage.SPANISH)) } returns
            ActionResult.Success("Entendido. A partir de ahora hablaré en español.")

        val result = parser.parse("cambia a espanol")

        assertEquals("Entendido. A partir de ahora hablaré en español.", result)
        coVerify { systemAction.execute(SystemCommand.SetLanguage(AssistantLanguage.SPANISH)) }
    }

    @Test
    fun `parse habla en frances devuelve null por no ser idioma soportado`() {
        val result = parser.parse("habla en frances")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    // ===== Fase B: temporizador =====

    @Test
    fun `parse temporizador de 5 minutos ejecuta SetTimer 5`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(5)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 5 minutos.")

        val result = parser.parse("pon un temporizador de 5 minutos")

        assertEquals("Éxito: Temporizador configurado para 5 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(5)) }
    }

    @Test
    fun `parse pasa 2 horas ejecuta SetTimer 120`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(120)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 120 minutos.")

        val result = parser.parse("pasa 2 horas")

        assertEquals("Éxito: Temporizador configurado para 120 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(120)) }
    }

    @Test
    fun `parse temporizador de 10 minutos con texto extra ejecuta SetTimer 10`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(10)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 10 minutos.")

        val result = parser.parse("pon un temporizador de 10 minutos y espera")

        assertEquals("Éxito: Temporizador configurado para 10 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(10)) }
    }

    @Test
    fun `parse busca un temporizador sin duracion delega en busqueda`() {
        coEvery { systemAction.execute(SystemCommand.SearchGoogle("un temporizador")) } returns
            ActionResult.Success("Buscando un temporizador...")

        val result = parser.parse("busca un temporizador")

        assertEquals("Buscando un temporizador...", result)
        coVerify { systemAction.execute(SystemCommand.SearchGoogle("un temporizador")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.SetTimer(999)) }
    }

    // ===== M1 (Lote 10): invariante del temporizador [1, 1440] — fuente única
    // SystemCommand.TIMER_* (compartida con el wire vía AccionRegistry) =====

    @Test
    fun `parse temporizador de 0 minutos devuelve error y no ejecuta SetTimer`() {
        val result = parser.parse("pon un temporizador de 0 minutos")

        assertEquals("Error: La duración debe estar entre 1 minuto y 24 horas.", result)
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.SetTimer(0)) }
    }

    @Test
    fun `parse temporizador de 5000 minutos devuelve error y no ejecuta SetTimer`() {
        val result = parser.parse("pon un temporizador de 5000 minutos")

        assertEquals("Error: La duración debe estar entre 1 minuto y 24 horas.", result)
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.SetTimer(5000)) }
    }

    @Test
    fun `parse temporizador de 1441 minutos devuelve error y no ejecuta SetTimer`() {
        val result = parser.parse("pon un temporizador de 1441 minutos")

        assertEquals("Error: La duración debe estar entre 1 minuto y 24 horas.", result)
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.SetTimer(1441)) }
    }

    @Test
    fun `parse temporizador de 24 horas ejecuta SetTimer 1440`() {
        // Boundary alto VÁLIDO del invariante: 24 horas = 1440 minutos → sí ejecuta.
        coEvery { systemAction.execute(SystemCommand.SetTimer(1440)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 1440 minutos.")

        val result = parser.parse("pon un temporizador de 24 horas")

        assertEquals("Éxito: Temporizador configurado para 1440 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(1440)) }
    }

    @Test
    fun `parse temporizador de 1 minuto ejecuta SetTimer 1`() {
        // P1-2 (Lote 10): boundary bajo VÁLIDO — lockea el invariante por ambos extremos.
        coEvery { systemAction.execute(SystemCommand.SetTimer(1)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 1 minutos.")

        val result = parser.parse("pon un temporizador de 1 minuto")

        assertEquals("Éxito: Temporizador configurado para 1 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(1)) }
    }

    @Test
    fun `parse temporizador de un rato devuelve null y no ejecuta nada`() {
        // P2-1 (Lote 10): no-numérico EXPLÍCITO — sin unidad de duración → null (Gemini).
        val result = parser.parse("pon un temporizador de un rato")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    // ===== O5: duración compuesta (suma de TODAS las unidades + fracciones) =====

    @Test
    fun `parse temporizador de 1 hora y 30 minutos ejecuta SetTimer 90`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(90)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 90 minutos.")

        val result = parser.parse("pon un temporizador de 1 hora y 30 minutos")

        assertEquals("Éxito: Temporizador configurado para 90 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(90)) }
    }

    @Test
    fun `parse temporizador de 1 hora 30 minutos sin y ejecuta SetTimer 90`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(90)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 90 minutos.")

        val result = parser.parse("pon un temporizador de 1 hora 30 minutos")

        assertEquals("Éxito: Temporizador configurado para 90 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(90)) }
    }

    @Test
    fun `parse temporizador de 30 minutos y 1 hora suma sin importar el orden`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(90)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 90 minutos.")

        val result = parser.parse("pon un temporizador de 30 minutos y 1 hora")

        assertEquals("Éxito: Temporizador configurado para 90 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(90)) }
    }

    @Test
    fun `parse pasa 1 hora y media ejecuta SetTimer 90`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(90)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 90 minutos.")

        val result = parser.parse("pasa 1 hora y media")

        assertEquals("Éxito: Temporizador configurado para 90 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(90)) }
    }

    @Test
    fun `parse pasa 2 horas y media ejecuta SetTimer 150`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(150)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 150 minutos.")

        val result = parser.parse("pasa 2 horas y media")

        assertEquals("Éxito: Temporizador configurado para 150 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(150)) }
    }

    @Test
    fun `parse temporizador de 1 hora y cuarto ejecuta SetTimer 75`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(75)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 75 minutos.")

        val result = parser.parse("pon un temporizador de 1 hora y cuarto")

        assertEquals("Éxito: Temporizador configurado para 75 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(75)) }
    }

    @Test
    fun `parse temporizador de 1 hora y 30 minutos y 20 segundos ignora los segundos`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(90)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 90 minutos.")

        val result = parser.parse("pon un temporizador de 1 hora y 30 minutos y 20 segundos")

        assertEquals("Éxito: Temporizador configurado para 90 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(90)) }
    }

    @Test
    fun `parse temporizador de 5 minutos y 10 segundos ejecuta SetTimer 5`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(5)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 5 minutos.")

        val result = parser.parse("pon un temporizador de 5 minutos y 10 segundos")

        assertEquals("Éxito: Temporizador configurado para 5 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(5)) }
    }

    // ===== Fase B: llamada directa a número =====

    @Test
    fun `parse llama al 600 123 456 ejecuta CallNumber sin espacios`() {
        coEvery { systemAction.execute(SystemCommand.CallNumber("600123456")) } returns
            ActionResult.Success("Llamando al 600123456...")

        val result = parser.parse("llama al 600 123 456")

        assertEquals("Llamando al 600123456...", result)
        coVerify { systemAction.execute(SystemCommand.CallNumber("600123456")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.Call("cualquier contacto")) }
    }

    @Test
    fun `parse llamar al con prefijo internacional preserva el mas`() {
        coEvery { systemAction.execute(SystemCommand.CallNumber("+34600123456")) } returns
            ActionResult.Success("Llamando al +34600123456...")

        val result = parser.parse("llamar al +34 600 123 456")

        assertEquals("Llamando al +34600123456...", result)
        coVerify { systemAction.execute(SystemCommand.CallNumber("+34600123456")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.Call("cualquier contacto")) }
    }

    @Test
    fun `parse llama al jefe ejecuta Call con el contacto`() {
        coEvery { systemAction.execute(SystemCommand.Call("jefe")) } returns
            ActionResult.Success("Llamando a jefe...")

        val result = parser.parse("llama al jefe")

        assertEquals("Llamando a jefe...", result)
        coVerify { systemAction.execute(SystemCommand.Call("jefe")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.CallNumber("cualquier numero")) }
    }

    @Test
    fun `parse llama a 600 123 456 ejecuta CallNumber y no Call`() {
        coEvery { systemAction.execute(SystemCommand.CallNumber("600123456")) } returns
            ActionResult.Success("Llamando al 600123456...")

        val result = parser.parse("llama a 600 123 456")

        assertEquals("Llamando al 600123456...", result)
        coVerify { systemAction.execute(SystemCommand.CallNumber("600123456")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.Call("600 123 456")) }
    }

    @Test
    fun `parse llama a 600123456 compacto ejecuta CallNumber`() {
        coEvery { systemAction.execute(SystemCommand.CallNumber("600123456")) } returns
            ActionResult.Success("Llamando al 600123456...")

        val result = parser.parse("llama a 600123456")

        assertEquals("Llamando al 600123456...", result)
        coVerify { systemAction.execute(SystemCommand.CallNumber("600123456")) }
    }

    @Test
    fun `parse LLAMA AL 600123456 en mayusculas ejecuta CallNumber`() {
        coEvery { systemAction.execute(SystemCommand.CallNumber("600123456")) } returns
            ActionResult.Success("Llamando al 600123456...")

        val result = parser.parse("LLAMA AL 600123456")

        assertEquals("Llamando al 600123456...", result)
        coVerify { systemAction.execute(SystemCommand.CallNumber("600123456")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.Call("cualquier contacto")) }
    }

    // ===== H4: números con puntos separadores =====

    @Test
    fun `parse llama al 600 123 456 con puntos ejecuta CallNumber sin puntos`() {
        coEvery { systemAction.execute(SystemCommand.CallNumber("600123456")) } returns
            ActionResult.Success("Llamando al 600123456...")

        val result = parser.parse("llama al 600.123.456")

        assertEquals("Llamando al 600123456...", result)
        coVerify { systemAction.execute(SystemCommand.CallNumber("600123456")) }
        // H4: no debe caer en la rama de contacto con el punto pegado
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.Call("600.123.456")) }
    }

    @Test
    fun `parse llama al 600 ejecuta CallNumber`() {
        coEvery { systemAction.execute(SystemCommand.CallNumber("600")) } returns
            ActionResult.Success("Llamando al 600...")

        val result = parser.parse("llama al 600")

        assertEquals("Llamando al 600...", result)
        coVerify { systemAction.execute(SystemCommand.CallNumber("600")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.Call("600")) }
    }

    @Test
    fun `parse llamar al 600 123 456 con guiones ejecuta CallNumber sin guiones`() {
        coEvery { systemAction.execute(SystemCommand.CallNumber("600123456")) } returns
            ActionResult.Success("Llamando al 600123456...")

        val result = parser.parse("llamar al 600-123-456")

        assertEquals("Llamando al 600123456...", result)
        coVerify { systemAction.execute(SystemCommand.CallNumber("600123456")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.Call("600-123-456")) }
    }

    // ===== M2 (Lote 10): sufijo de cortesía del dictado ("por favor") en llamadas =====

    @Test
    fun `parse llama a 600 123 456 por favor ejecuta CallNumber y no Call`() {
        coEvery { systemAction.execute(SystemCommand.CallNumber("600123456")) } returns
            ActionResult.Success("Llamando al 600123456...")

        val result = parser.parse("llama a 600 123 456 por favor")

        assertEquals("Llamando al 600123456...", result)
        coVerify { systemAction.execute(SystemCommand.CallNumber("600123456")) }
        // M2: antes el cortesía se colaba en el argumento → Call("600 123 456 por favor").
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.Call("600 123 456 por favor")) }
    }

    @Test
    fun `parse llama a 600 123 456 porfavor ejecuta CallNumber`() {
        // P1-1 (Lote 10): variante de dictado SIN espacio ("porfavor").
        coEvery { systemAction.execute(SystemCommand.CallNumber("600123456")) } returns
            ActionResult.Success("Llamando al 600123456...")

        val result = parser.parse("llama a 600 123 456 porfavor")

        assertEquals("Llamando al 600123456...", result)
        coVerify { systemAction.execute(SystemCommand.CallNumber("600123456")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.Call("600 123 456 porfavor")) }
    }

    @Test
    fun `parse llama a Ana por favor ejecuta Call con el contacto`() {
        coEvery { systemAction.execute(SystemCommand.Call("Ana")) } returns
            ActionResult.Success("Llamando a Ana...")

        val result = parser.parse("llama a Ana por favor")

        assertEquals("Llamando a Ana...", result)
        coVerify { systemAction.execute(SystemCommand.Call("Ana")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.Call("Ana por favor")) }
    }

    @Test
    fun `parse llama al jefe por favor ejecuta Call con el contacto`() {
        coEvery { systemAction.execute(SystemCommand.Call("jefe")) } returns
            ActionResult.Success("Llamando a jefe...")

        val result = parser.parse("llama al jefe por favor")

        assertEquals("Llamando a jefe...", result)
        coVerify { systemAction.execute(SystemCommand.Call("jefe")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.Call("jefe por favor")) }
    }

    @Test
    fun `parse llama a por favor devuelve error y no ejecuta nada`() {
        // M2: tras el strip del cortesía no queda target → error SIN ejecutar
        // (antes emitía Call("por favor"), contacto basura).
        val result = parser.parse("llama a por favor")

        assertEquals("Error: ¿A quién quieres que llame?", result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    // ===== Fase B: recuerda (memoria offline) =====

    @Test
    fun `parse recuerda que me gusta el cafe ejecuta SaveMemory con el hecho`() {
        coEvery { systemAction.execute(SystemCommand.SaveMemory("me gusta el cafe")) } returns
            ActionResult.Success("Entendido, lo recordaré.")

        val result = parser.parse("recuerda que me gusta el cafe")

        assertEquals("Entendido, lo recordaré.", result)
        coVerify { systemAction.execute(SystemCommand.SaveMemory("me gusta el cafe")) }
    }

    @Test
    fun `parse recuerda comprar leche ejecuta SaveMemory sin el que`() {
        coEvery { systemAction.execute(SystemCommand.SaveMemory("comprar leche")) } returns
            ActionResult.Success("Entendido, lo recordaré.")

        val result = parser.parse("recuerda comprar leche")

        assertEquals("Entendido, lo recordaré.", result)
        coVerify { systemAction.execute(SystemCommand.SaveMemory("comprar leche")) }
    }

    // ===== Fase B: navegación Maps =====

    @Test
    fun `parse llevame a la oficina ejecuta Navigate con el destino`() {
        coEvery { systemAction.execute(SystemCommand.Navigate("la oficina")) } returns
            ActionResult.Success("Éxito: Abriendo Maps hacia la oficina.")

        val result = parser.parse("llevame a la oficina")

        assertEquals("Éxito: Abriendo Maps hacia la oficina.", result)
        coVerify { systemAction.execute(SystemCommand.Navigate("la oficina")) }
    }

    @Test
    fun `parse navega a la gasolinera ejecuta Navigate con el destino`() {
        coEvery { systemAction.execute(SystemCommand.Navigate("la gasolinera")) } returns
            ActionResult.Success("Éxito: Abriendo Maps hacia la gasolinera.")

        val result = parser.parse("navega a la gasolinera")

        assertEquals("Éxito: Abriendo Maps hacia la gasolinera.", result)
        coVerify { systemAction.execute(SystemCommand.Navigate("la gasolinera")) }
    }

    @Test
    fun `parse LLEVAME a la playa ejecuta Navigate preservando el case`() {
        coEvery { systemAction.execute(SystemCommand.Navigate("la playa")) } returns
            ActionResult.Success("Éxito: Abriendo Maps hacia la playa.")

        val result = parser.parse("LLEVAME a la playa")

        assertEquals("Éxito: Abriendo Maps hacia la playa.", result)
        coVerify { systemAction.execute(SystemCommand.Navigate("la playa")) }
    }

    // ===== Fase B: ajustes =====

    @Test
    fun `parse abre los ajustes ejecuta OpenSettings y no OpenApp`() {
        coEvery { systemAction.execute(SystemCommand.OpenSettings) } returns
            ActionResult.Success("Éxito: Abriendo ajustes del sistema.")

        val result = parser.parse("abre los ajustes")

        assertEquals("Éxito: Abriendo ajustes del sistema.", result)
        coVerify { systemAction.execute(SystemCommand.OpenSettings) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenApp("cualquier app")) }
    }

    @Test
    fun `parse abre la configuracion ejecuta OpenSettings`() {
        coEvery { systemAction.execute(SystemCommand.OpenSettings) } returns
            ActionResult.Success("Éxito: Abriendo ajustes del sistema.")

        val result = parser.parse("abre la configuracion")

        assertEquals("Éxito: Abriendo ajustes del sistema.", result)
        coVerify { systemAction.execute(SystemCommand.OpenSettings) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenApp("cualquier app")) }
    }

    @Test
    fun `parse abrir los ajustes ejecuta OpenSettings`() {
        coEvery { systemAction.execute(SystemCommand.OpenSettings) } returns
            ActionResult.Success("Éxito: Abriendo ajustes del sistema.")

        val result = parser.parse("abrir los ajustes")

        assertEquals("Éxito: Abriendo ajustes del sistema.", result)
        coVerify { systemAction.execute(SystemCommand.OpenSettings) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenApp("cualquier app")) }
    }

    @Test
    fun `parse abre ajustes sin articulo ejecuta OpenSettings`() {
        coEvery { systemAction.execute(SystemCommand.OpenSettings) } returns
            ActionResult.Success("Éxito: Abriendo ajustes del sistema.")

        val result = parser.parse("abre ajustes")

        assertEquals("Éxito: Abriendo ajustes del sistema.", result)
        coVerify { systemAction.execute(SystemCommand.OpenSettings) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenApp("cualquier app")) }
    }

    @Test
    fun `parse abre configuracion sin articulo ejecuta OpenSettings`() {
        coEvery { systemAction.execute(SystemCommand.OpenSettings) } returns
            ActionResult.Success("Éxito: Abriendo ajustes del sistema.")

        val result = parser.parse("abre configuracion")

        assertEquals("Éxito: Abriendo ajustes del sistema.", result)
        coVerify { systemAction.execute(SystemCommand.OpenSettings) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenApp("cualquier app")) }
    }

    @Test
    fun `parse abrir la configuracion ejecuta OpenSettings`() {
        coEvery { systemAction.execute(SystemCommand.OpenSettings) } returns
            ActionResult.Success("Éxito: Abriendo ajustes del sistema.")

        val result = parser.parse("abrir la configuracion")

        assertEquals("Éxito: Abriendo ajustes del sistema.", result)
        coVerify { systemAction.execute(SystemCommand.OpenSettings) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenApp("cualquier app")) }
    }

    // ===== Fase B: alarma con hora hablada =====

    @Test
    fun `parse alarma con hora palabra y etiqueta ejecuta SetAlarm con la etiqueta`() {
        coEvery { systemAction.execute(SystemCommand.SetAlarm(7, 30, "despertarme")) } returns
            ActionResult.Success("Alarma a las 7:30.")

        val result = parser.parse("pon una alarma a las siete y media para despertarme")

        assertEquals("Alarma a las 7:30.", result)
        coVerify { systemAction.execute(SystemCommand.SetAlarm(7, 30, "despertarme")) }
    }

    @Test
    fun `parse alarma a las 19 45 ejecuta SetAlarm 19 45`() {
        coEvery { systemAction.execute(SystemCommand.SetAlarm(19, 45, null)) } returns
            ActionResult.Success("Alarma a las 19:45.")

        val result = parser.parse("pon una alarma a las 19:45")

        assertEquals("Alarma a las 19:45.", result)
        coVerify { systemAction.execute(SystemCommand.SetAlarm(19, 45, null)) }
    }

    @Test
    fun `parse alarma a las 7 30 de la tarde ejecuta SetAlarm 19 30`() {
        coEvery { systemAction.execute(SystemCommand.SetAlarm(19, 30, null)) } returns
            ActionResult.Success("Alarma a las 19:30.")

        val result = parser.parse("pon una alarma a las 7:30 de la tarde")

        assertEquals("Alarma a las 19:30.", result)
        coVerify { systemAction.execute(SystemCommand.SetAlarm(19, 30, null)) }
    }

    @Test
    fun `parse alarma para las siete ejecuta SetAlarm 7 00`() {
        coEvery { systemAction.execute(SystemCommand.SetAlarm(7, 0, null)) } returns
            ActionResult.Success("Alarma a las 7:00.")

        val result = parser.parse("pon una alarma para las siete")

        assertEquals("Alarma a las 7:00.", result)
        coVerify { systemAction.execute(SystemCommand.SetAlarm(7, 0, null)) }
    }

    @Test
    fun `parse alarma sin hora ejecuta OpenAlarms`() {
        coEvery { systemAction.execute(SystemCommand.OpenAlarms) } returns
            ActionResult.Success("Abriendo alarmas...")

        val result = parser.parse("pon una alarma")

        assertEquals("Abriendo alarmas...", result)
        coVerify { systemAction.execute(SystemCommand.OpenAlarms) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.SetAlarm(0, 0, "nunca")) }
    }

    // ===== B1: alarma precedida de "la" (bug del backlog: "pon la alarma a las 7" → OpenAlarms) =====
    // El anchor (las|la) de TimePhraseParser capturaba el "la" del sustantivo y trataba
    // "alarma..." como palabra de hora → null → OpenAlarms. Fix: buscar la hora solo
    // sobre el subtexto posterior a "alarma" (mismo patrón que la rama 11b).

    @Test
    fun `parse pon la alarma a las 7 ejecuta SetAlarm 7 0 y no OpenAlarms`() {
        coEvery { systemAction.execute(SystemCommand.SetAlarm(7, 0, null)) } returns
            ActionResult.Success("Alarma a las 7:00.")

        val result = parser.parse("pon la alarma a las 7")

        assertEquals("Alarma a las 7:00.", result)
        coVerify { systemAction.execute(SystemCommand.SetAlarm(7, 0, null)) }
        // Bug del backlog: hoy esta frase ejecutaba OpenAlarms
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenAlarms) }
    }

    @Test
    fun `parse la alarma de las 7 30 ejecuta SetAlarm 7 30`() {
        coEvery { systemAction.execute(SystemCommand.SetAlarm(7, 30, null)) } returns
            ActionResult.Success("Alarma a las 7:30.")

        val result = parser.parse("la alarma de las 7:30")

        assertEquals("Alarma a las 7:30.", result)
        coVerify { systemAction.execute(SystemCommand.SetAlarm(7, 30, null)) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenAlarms) }
    }

    @Test
    fun `parse activa la alarma a las 7 30 ejecuta SetAlarm 7 30`() {
        coEvery { systemAction.execute(SystemCommand.SetAlarm(7, 30, null)) } returns
            ActionResult.Success("Alarma a las 7:30.")

        val result = parser.parse("activa la alarma a las 7:30")

        assertEquals("Alarma a las 7:30.", result)
        coVerify { systemAction.execute(SystemCommand.SetAlarm(7, 30, null)) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenAlarms) }
    }

    @Test
    fun `parse pon la alarma para las 8 ejecuta SetAlarm 8 0 sin etiqueta`() {
        coEvery { systemAction.execute(SystemCommand.SetAlarm(8, 0, null)) } returns
            ActionResult.Success("Alarma a las 8:00.")

        val result = parser.parse("pon la alarma para las 8")

        assertEquals("Alarma a las 8:00.", result)
        // El "para" ANTERIOR a la hora no es etiqueta: la etiqueta solo se re-ancla
        // sobre el subtexto posterior a "alarma" y DESPUÉS del match de hora.
        coVerify { systemAction.execute(SystemCommand.SetAlarm(8, 0, null)) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.SetAlarm(8, 0, "las 8")) }
    }

    @Test
    fun `parse pon una alarma para las 7 30 para despertarme ejecuta SetAlarm 7 30 con etiqueta`() {
        coEvery { systemAction.execute(SystemCommand.SetAlarm(7, 30, "despertarme")) } returns
            ActionResult.Success("Alarma a las 7:30.")

        val result = parser.parse("pon una alarma para las 7:30 para despertarme")

        assertEquals("Alarma a las 7:30.", result)
        // Regresión del re-anclaje: el primer "para" tras el match de hora gana.
        coVerify { systemAction.execute(SystemCommand.SetAlarm(7, 30, "despertarme")) }
    }

    // ===== O4: cancelar alarma (rama 11b, precedencia sobre la rama alarma) =====

    @Test
    fun `parse cancela la alarma de las 7 ejecuta CancelAlarm 7 0 y no SetAlarm`() {
        coEvery { systemAction.execute(SystemCommand.CancelAlarm(7, 0)) } returns
            ActionResult.Success("He abierto la lista de alarmas: desliza para borrar la de las 7:0.")

        val result = parser.parse("cancela la alarma de las 7")

        assertEquals("He abierto la lista de alarmas: desliza para borrar la de las 7:0.", result)
        coVerify { systemAction.execute(SystemCommand.CancelAlarm(7, 0)) }
        // Bug del backlog: hoy esta frase ejecutaba SetAlarm
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.SetAlarm(7, 0, null)) }
    }

    @Test
    fun `parse cancela la alarma de las siete de la noche ejecuta CancelAlarm 19 0`() {
        coEvery { systemAction.execute(SystemCommand.CancelAlarm(19, 0)) } returns
            ActionResult.Success("He abierto la lista de alarmas: desliza para borrar la de las 19:0.")

        val result = parser.parse("cancela la alarma de las siete de la noche")

        assertEquals("He abierto la lista de alarmas: desliza para borrar la de las 19:0.", result)
        coVerify { systemAction.execute(SystemCommand.CancelAlarm(19, 0)) }
    }

    @Test
    fun `parse cancela la alarma sin hora ejecuta CancelAlarm sin parametros`() {
        coEvery { systemAction.execute(SystemCommand.CancelAlarm(null, null)) } returns
            ActionResult.Success("He abierto la lista de alarmas: desliza para borrar la que quieras.")

        val result = parser.parse("cancela la alarma")

        assertEquals("He abierto la lista de alarmas: desliza para borrar la que quieras.", result)
        coVerify { systemAction.execute(SystemCommand.CancelAlarm(null, null)) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenAlarms) }
    }

    @Test
    fun `parse cancela las alarmas de las 7 ejecuta CancelAlarm 7 0 con el plural`() {
        coEvery { systemAction.execute(SystemCommand.CancelAlarm(7, 0)) } returns
            ActionResult.Success("He abierto la lista de alarmas: desliza para borrar la de las 7:0.")

        val result = parser.parse("cancela las alarmas de las 7")

        assertEquals("He abierto la lista de alarmas: desliza para borrar la de las 7:0.", result)
        coVerify { systemAction.execute(SystemCommand.CancelAlarm(7, 0)) }
    }

    @Test
    fun `parse pregunta de cancelar alarma con signos ejecuta CancelAlarm sin hora`() {
        coEvery { systemAction.execute(SystemCommand.CancelAlarm(null, null)) } returns
            ActionResult.Success("He abierto la lista de alarmas: desliza para borrar la que quieras.")

        val result = parser.parse("¿Cancela la alarma?")

        assertEquals("He abierto la lista de alarmas: desliza para borrar la que quieras.", result)
        coVerify { systemAction.execute(SystemCommand.CancelAlarm(null, null)) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.OpenAlarms) }
    }

    @Test
    fun `parse quita la alarma ejecuta CancelAlarm sin parametros`() {
        coEvery { systemAction.execute(SystemCommand.CancelAlarm(null, null)) } returns
            ActionResult.Success("He abierto la lista de alarmas: desliza para borrar la que quieras.")

        val result = parser.parse("quita la alarma")

        assertEquals("He abierto la lista de alarmas: desliza para borrar la que quieras.", result)
        coVerify { systemAction.execute(SystemCommand.CancelAlarm(null, null)) }
    }

    @Test
    fun `parse elimina las alarmas ejecuta CancelAlarm sin parametros`() {
        coEvery { systemAction.execute(SystemCommand.CancelAlarm(null, null)) } returns
            ActionResult.Success("He abierto la lista de alarmas: desliza para borrar la que quieras.")

        val result = parser.parse("elimina las alarmas")

        assertEquals("He abierto la lista de alarmas: desliza para borrar la que quieras.", result)
        coVerify { systemAction.execute(SystemCommand.CancelAlarm(null, null)) }
    }

    @Test
    fun `parse cancela mi alarma de las 7 ejecuta CancelAlarm 7 0`() {
        coEvery { systemAction.execute(SystemCommand.CancelAlarm(7, 0)) } returns
            ActionResult.Success("He abierto la lista de alarmas: desliza para borrar la de las 7:0.")

        val result = parser.parse("cancela mi alarma de las 7")

        assertEquals("He abierto la lista de alarmas: desliza para borrar la de las 7:0.", result)
        coVerify { systemAction.execute(SystemCommand.CancelAlarm(7, 0)) }
    }

    @Test
    fun `parse quita el volumen devuelve null`() {
        // "quita" + "el volumen": el sustantivo de cancelAlarmRegex no matchea → Gemini.
        val result = parser.parse("quita el volumen")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse cancela el temporizador devuelve null`() {
        val result = parser.parse("cancela el temporizador")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    // ===== Fallbacks: texto que parece hora pero no lo es =====

    @Test
    fun `parse sala 4 devuelve null por el word boundary de la`() {
        val result = parser.parse("sala 4")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse veinticinco suelto devuelve null`() {
        val result = parser.parse("veinticinco")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    // ===== Crear notas por voz (rama 0: precedencia máxima) =====

    // Grupo A: extracción

    @Test
    fun `parse crea una nota con dos puntos extrae el texto y ejecuta CreateNote`() {
        coEvery { systemAction.execute(SystemCommand.CreateNote("comprar leche")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «comprar leche»")

        val result = parser.parse("crea una nota: comprar leche")

        assertEquals("Nota guardada. Empieza así: «comprar leche»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("comprar leche")) }
    }

    @Test
    fun `parse crea una nota con espacio extrae el texto sin el prefijo`() {
        coEvery { systemAction.execute(SystemCommand.CreateNote("comprar leche")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «comprar leche»")

        val result = parser.parse("crea una nota comprar leche")

        assertEquals("Nota guardada. Empieza así: «comprar leche»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("comprar leche")) }
    }

    @Test
    fun `parse crear una nota con dos puntos extrae el texto`() {
        coEvery { systemAction.execute(SystemCommand.CreateNote("llamar al fontanero")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «llamar al fontanero»")

        val result = parser.parse("crear una nota: llamar al fontanero")

        assertEquals("Nota guardada. Empieza así: «llamar al fontanero»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("llamar al fontanero")) }
    }

    @Test
    fun `parse anota con dos puntos preserva las tildes del texto`() {
        coEvery { systemAction.execute(SystemCommand.CreateNote("reunión con el médico a las 3")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «reunión con el médico a las 3»")

        val result = parser.parse("anota: reunión con el médico a las 3")

        assertEquals("Nota guardada. Empieza así: «reunión con el médico a las 3»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("reunión con el médico a las 3")) }
    }

    @Test
    fun `parse ANOTA en mayusculas extrae el texto preservando el case`() {
        coEvery { systemAction.execute(SystemCommand.CreateNote("comprar leche")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «comprar leche»")

        val result = parser.parse("ANOTA comprar leche")

        assertEquals("Nota guardada. Empieza así: «comprar leche»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("comprar leche")) }
    }

    @Test
    fun `parse toma nota de extrae el texto completo`() {
        coEvery { systemAction.execute(SystemCommand.CreateNote("la reunión de mañana")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «la reunión de mañana»")

        val result = parser.parse("toma nota de la reunión de mañana")

        assertEquals("Nota guardada. Empieza así: «la reunión de mañana»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("la reunión de mañana")) }
    }

    @Test
    fun `parse toma nota del extrae el texto sin la contraccion`() {
        coEvery { systemAction.execute(SystemCommand.CreateNote("pan")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «pan»")

        val result = parser.parse("toma nota del pan")

        assertEquals("Nota guardada. Empieza así: «pan»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("pan")) }
    }

    @Test
    fun `parse crea una nota del extrae el texto sin la contraccion`() {
        coEvery { systemAction.execute(SystemCommand.CreateNote("proyecto")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «proyecto»")

        val result = parser.parse("crea una nota del proyecto")

        assertEquals("Nota guardada. Empieza así: «proyecto»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("proyecto")) }
    }

    @Test
    fun `parse toma nota del sin argumento devuelve null`() {
        val result = parser.parse("toma nota del")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse toma nota con dos puntos extrae el texto`() {
        coEvery { systemAction.execute(SystemCommand.CreateNote("comprar pan y leche")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «comprar pan y leche»")

        val result = parser.parse("toma nota: comprar pan y leche")

        assertEquals("Nota guardada. Empieza así: «comprar pan y leche»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("comprar pan y leche")) }
    }

    @Test
    fun `parse escribe una nota con dos puntos extrae el texto`() {
        coEvery { systemAction.execute(SystemCommand.CreateNote("idea para la app")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «idea para la app»")

        val result = parser.parse("escribe una nota: idea para la app")

        assertEquals("Nota guardada. Empieza así: «idea para la app»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("idea para la app")) }
    }

    @Test
    fun `parse escribir una nota extrae el texto`() {
        coEvery { systemAction.execute(SystemCommand.CreateNote("sobre el proyecto")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «sobre el proyecto»")

        val result = parser.parse("escribir una nota sobre el proyecto")

        assertEquals("Nota guardada. Empieza así: «sobre el proyecto»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("sobre el proyecto")) }
    }

    @Test
    fun `parse tomar nota de extrae el texto`() {
        coEvery { systemAction.execute(SystemCommand.CreateNote("que la wifi no funciona")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «que la wifi no funciona»")

        val result = parser.parse("tomar nota de que la wifi no funciona")

        assertEquals("Nota guardada. Empieza así: «que la wifi no funciona»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("que la wifi no funciona")) }
    }

    @Test
    fun `parse crea una nota con punto tras el prefijo extrae el texto`() {
        coEvery { systemAction.execute(SystemCommand.CreateNote("comprar leche")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «comprar leche»")

        val result = parser.parse("crea una nota. comprar leche")

        assertEquals("Nota guardada. Empieza así: «comprar leche»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("comprar leche")) }
    }

    @Test
    fun `parse anota con dos puntos internos preserva los dos puntos`() {
        coEvery { systemAction.execute(SystemCommand.CreateNote("comprar: leche y pan")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «comprar: leche y pan»")

        val result = parser.parse("anota: comprar: leche y pan")

        assertEquals("Nota guardada. Empieza así: «comprar: leche y pan»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("comprar: leche y pan")) }
    }

    @Test
    fun `parse con signos de apertura y cierre extrae la nota limpia`() {
        coEvery { systemAction.execute(SystemCommand.CreateNote("pan")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «pan»")

        val result = parser.parse("¡Crea una nota: pan!")

        // cleanNoteText quita la '!' final del original → la nota es "pan".
        assertEquals("Nota guardada. Empieza así: «pan»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("pan")) }
    }

    // Grupo B: precedencia sobre ayuda / alarma / temporizador / búsqueda

    @Test
    fun `parse crea una nota con frase de ayuda no devuelve HELP`() {
        coEvery { systemAction.execute(SystemCommand.CreateNote("que puedes hacer")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «que puedes hacer»")

        val result = parser.parse("crea una nota: que puedes hacer")

        assertNotEquals(CommandMarkers.HELP, result)
        assertEquals("Nota guardada. Empieza así: «que puedes hacer»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("que puedes hacer")) }
    }

    @Test
    fun `parse crea una nota con alarma no ejecuta la rama alarma`() {
        coEvery { systemAction.execute(SystemCommand.CreateNote("pon una alarma a las 7")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «pon una alarma a las 7»")

        val result = parser.parse("crea una nota: pon una alarma a las 7")

        assertEquals("Nota guardada. Empieza así: «pon una alarma a las 7»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("pon una alarma a las 7")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.SetAlarm(7, 0, null)) }
    }

    @Test
    fun `parse anota con temporizador no ejecuta la rama temporizador`() {
        coEvery { systemAction.execute(SystemCommand.CreateNote("pon un temporizador de 5 minutos")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «pon un temporizador de 5 minutos»")

        val result = parser.parse("anota: pon un temporizador de 5 minutos")

        assertEquals("Nota guardada. Empieza así: «pon un temporizador de 5 minutos»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("pon un temporizador de 5 minutos")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.SetTimer(5)) }
    }

    @Test
    fun `parse crea una nota con busqueda no ejecuta la rama busqueda`() {
        coEvery { systemAction.execute(SystemCommand.CreateNote("busca gatos")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «busca gatos»")

        val result = parser.parse("crea una nota: busca gatos")

        assertEquals("Nota guardada. Empieza así: «busca gatos»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("busca gatos")) }
        coVerify(exactly = 0) { systemAction.execute(SystemCommand.SearchGoogle("gatos")) }
    }

    // Grupo C: falsos positivos (→ null, fall-through a Gemini)

    @Test
    fun `parse anotar los gastos devuelve null por no ser prefijo de nota`() {
        val result = parser.parse("anotar los gastos")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse tengo una nota pendiente devuelve null`() {
        val result = parser.parse("tengo una nota pendiente")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse nota suelto devuelve null`() {
        val result = parser.parse("nota")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse toma nota sin contenido devuelve null`() {
        val result = parser.parse("toma nota")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse crea una nota sin contenido devuelve null`() {
        val result = parser.parse("crea una nota")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse toma nota de sin argumento devuelve null`() {
        val result = parser.parse("toma nota de")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    // Grupo D: el parser NO trunca (el límite lo aplica la acción)

    @Test
    fun `parse nota de 1200 caracteres no trunca el texto`() {
        val longText = "a".repeat(1200)
        coEvery { systemAction.execute(SystemCommand.CreateNote(longText)) } returns
            ActionResult.Success("Nota guardada. Empieza así: «${longText.take(80)}...»")

        val result = parser.parse("crea una nota: $longText")

        assertEquals("Nota guardada. Empieza así: «${longText.take(80)}...»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote(longText)) }
    }

    // ===== N-OBS2: variantes "de:" (dos puntos tras preposición) =====

    @Test
    fun `parse crea una nota de con dos puntos extrae el texto`() {
        coEvery { systemAction.execute(SystemCommand.CreateNote("pan")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «pan»")

        val result = parser.parse("crea una nota de: pan")

        assertEquals("Nota guardada. Empieza así: «pan»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("pan")) }
    }

    @Test
    fun `parse toma nota de con dos puntos extrae el texto`() {
        coEvery { systemAction.execute(SystemCommand.CreateNote("pan")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «pan»")

        val result = parser.parse("toma nota de: pan")

        assertEquals("Nota guardada. Empieza así: «pan»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("pan")) }
    }

    @Test
    fun `parse crear una nota de con dos puntos extrae el texto`() {
        coEvery { systemAction.execute(SystemCommand.CreateNote("pan")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «pan»")

        val result = parser.parse("crear una nota de: pan")

        assertEquals("Nota guardada. Empieza así: «pan»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("pan")) }
    }

    @Test
    fun `parse escribir una nota de con dos puntos extrae el texto`() {
        coEvery { systemAction.execute(SystemCommand.CreateNote("pan")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «pan»")

        val result = parser.parse("escribir una nota de: pan")

        assertEquals("Nota guardada. Empieza así: «pan»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("pan")) }
    }

    @Test
    fun `parse crea una nota de la lista sigue extrayendo sin cambio`() {
        // Regresión: la variante "de:" no debe alterar el comportamiento de "de "
        coEvery { systemAction.execute(SystemCommand.CreateNote("la lista")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «la lista»")

        val result = parser.parse("crea una nota de la lista")

        assertEquals("Nota guardada. Empieza así: «la lista»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("la lista")) }
    }

    @Test
    fun `parse crea una nota de con dos puntos sin contenido devuelve null`() {
        // Guard vacío: "de:" colgante no es contenido real → Gemini
        val result = parser.parse("crea una nota de:")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse crea una nota del con dos puntos devuelve null por no ser soportado`() {
        // No-goal: "del:" no está en los prefijos → Gemini
        val result = parser.parse("crea una nota del: pan")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse crea una nota de con dos puntos y frase de ayuda no devuelve HELP`() {
        // Precedencia de la rama 0 sobre la 1: el contenido "que puedes hacer" es una nota
        coEvery { systemAction.execute(SystemCommand.CreateNote("que puedes hacer")) } returns
            ActionResult.Success("Nota guardada. Empieza así: «que puedes hacer»")

        val result = parser.parse("crea una nota de: que puedes hacer")

        assertEquals("Nota guardada. Empieza así: «que puedes hacer»", result)
        coVerify { systemAction.execute(SystemCommand.CreateNote("que puedes hacer")) }
    }

    // ===== N1: lectura de notas (rama 0b) =====

    @Test
    fun `parse lee mis notas ejecuta ReadNotes`() {
        coEvery { systemAction.execute(SystemCommand.ReadNotes) } returns
            ActionResult.Success("Éxito: Tienes 2 notas. La más reciente empieza así: «comprar leche»")

        val result = parser.parse("lee mis notas")

        assertEquals("Éxito: Tienes 2 notas. La más reciente empieza así: «comprar leche»", result)
        coVerify { systemAction.execute(SystemCommand.ReadNotes) }
    }

    @Test
    fun `parse lee mis notas con dos puntos finales ejecuta ReadNotes`() {
        // El ':' final se acepta como separador de comando (H5/ADR-011)
        coEvery { systemAction.execute(SystemCommand.ReadNotes) } returns
            ActionResult.Success("Éxito: Tienes 1 nota. Empieza así: «pan»")

        val result = parser.parse("lee mis notas:")

        assertEquals("Éxito: Tienes 1 nota. Empieza así: «pan»", result)
        coVerify { systemAction.execute(SystemCommand.ReadNotes) }
    }

    @Test
    fun `parse lee mis notas de la semana devuelve null`() {
        // BLOQUEANTE resuelto: igualdad EXACTA — la expansión NO debe capturarse
        // (el lookahead (?=\s|:|$) de la rama 1 no aplica aquí: el espacio lo atraviesa)
        val result = parser.parse("lee mis notas de la semana")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse lee mis notas con espacio final ejecuta ReadNotes`() {
        // Ronda de cierre (estilo O6): el trim de parse elimina el trailing space →
        // trimmed = "lee mis notas" → igualdad exacta (el helper añade además el
        // espacio inicial, cubriendo ambos bordes).
        assertCommandWithLeadingSpace("lee mis notas ", SystemCommand.ReadNotes)
    }

    @Test
    fun `parse lee mis notas con punto final ejecuta ReadNotes`() {
        // Ronda de cierre (estilo O6): normalize convierte el '.' final en espacio y
        // trim() lo elimina → trimmed = "lee mis notas" → igualdad exacta.
        assertCommandWithLeadingSpace("lee mis notas.", SystemCommand.ReadNotes)
    }

    @Test
    fun `parse lee mis notas con dos puntos y expansion devuelve null`() {
        // Ronda de cierre: "lee mis notas: de la semana" NO es ReadNotes — el ':' no se
        // normaliza (queda excluido de normalize) y el texto tras él rompe la igualdad
        // exacta → Gemini. Tampoco es ReadNote (ningún prefijo "lee la nota" matchea).
        val result = parser.parse("lee mis notas: de la semana")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse lee la nota de pan ejecuta ReadNote pan`() {
        coEvery { systemAction.execute(SystemCommand.ReadNote("pan")) } returns
            ActionResult.Success("Éxito: La nota dice: «comprar pan»")

        val result = parser.parse("lee la nota de pan")

        assertEquals("Éxito: La nota dice: «comprar pan»", result)
        coVerify { systemAction.execute(SystemCommand.ReadNote("pan")) }
    }

    @Test
    fun `parse lee la nota de con dos puntos ejecuta ReadNote pan`() {
        coEvery { systemAction.execute(SystemCommand.ReadNote("pan")) } returns
            ActionResult.Success("Éxito: La nota dice: «pan»")

        val result = parser.parse("lee la nota de: pan")

        assertEquals("Éxito: La nota dice: «pan»", result)
        coVerify { systemAction.execute(SystemCommand.ReadNote("pan")) }
    }

    @Test
    fun `parse lee la nota del ejecuta ReadNote sin la contraccion`() {
        coEvery { systemAction.execute(SystemCommand.ReadNote("proyecto")) } returns
            ActionResult.Success("Éxito: La nota dice: «proyecto»")

        val result = parser.parse("lee la nota del proyecto")

        assertEquals("Éxito: La nota dice: «proyecto»", result)
        coVerify { systemAction.execute(SystemCommand.ReadNote("proyecto")) }
    }

    @Test
    fun `parse lee la nota con dos puntos ejecuta ReadNote`() {
        coEvery { systemAction.execute(SystemCommand.ReadNote("comprar leche")) } returns
            ActionResult.Success("Éxito: La nota dice: «comprar leche»")

        val result = parser.parse("lee la nota: comprar leche")

        assertEquals("Éxito: La nota dice: «comprar leche»", result)
        coVerify { systemAction.execute(SystemCommand.ReadNote("comprar leche")) }
    }

    @Test
    fun `parse lee la nota con dos puntos pegado ejecuta ReadNote`() {
        coEvery { systemAction.execute(SystemCommand.ReadNote("pan")) } returns
            ActionResult.Success("Éxito: La nota dice: «pan»")

        val result = parser.parse("lee la nota:pan")

        assertEquals("Éxito: La nota dice: «pan»", result)
        coVerify { systemAction.execute(SystemCommand.ReadNote("pan")) }
    }

    @Test
    fun `parse lee la nota de la alarma de las 7 no ejecuta la rama alarma`() {
        // Precedencia de la rama 0b sobre la 12 (contains): es una búsqueda de nota
        coEvery { systemAction.execute(SystemCommand.ReadNote("la alarma de las 7")) } returns
            ActionResult.Success("Éxito: La nota dice: «pon una alarma a las 7»")

        val result = parser.parse("lee la nota de la alarma de las 7")

        assertEquals("Éxito: La nota dice: «pon una alarma a las 7»", result)
        coVerify { systemAction.execute(SystemCommand.ReadNote("la alarma de las 7")) }
        // match en vez de any<SetAlarm>: el child-matching de data class falla en
        // MockK 1.13.12 (mismo patrón que el test de "busca: 19:45" más abajo).
        coVerify(exactly = 0) { systemAction.execute(match { it is SystemCommand.SetAlarm }) }
    }

    @Test
    fun `parse lee la nota desnudo devuelve null`() {
        // "lee la nota" sin más NO está en los prefijos → Gemini
        val result = parser.parse("lee la nota")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse lee la nota de con dos puntos sin contenido devuelve null`() {
        // Colgante "de:" → no es una búsqueda real → Gemini
        val result = parser.parse("lee la nota de:")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse lee la nota del con dos puntos devuelve null por no ser soportado`() {
        // No-goal simétrico a "crea una nota del: pan" (N-OBS2): la contracción "del:"
        // no es un prefijo ni un colgante real → el guard lo rechaza → Gemini
        val result = parser.parse("lee la nota del: pan")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse lee la nota con frase de ayuda no devuelve HELP`() {
        // Precedencia de la rama 0b sobre la 1: la query es contenido de búsqueda
        coEvery { systemAction.execute(SystemCommand.ReadNote("que puedes hacer")) } returns
            ActionResult.Success("Éxito: La nota dice: «que puedes hacer»")

        val result = parser.parse("lee la nota: que puedes hacer")

        assertEquals("Éxito: La nota dice: «que puedes hacer»", result)
        coVerify { systemAction.execute(SystemCommand.ReadNote("que puedes hacer")) }
    }

    // ===== TMP-2: dígito desnudo tras "X hora(s)/minuto(s) y N" (ADR-TMP-5) =====

    @Test
    fun `parse temporizador de 1 hora y 30 sin unidad ejecuta SetTimer 90`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(90)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 90 minutos.")

        val result = parser.parse("pon un temporizador de 1 hora y 30")

        assertEquals("Éxito: Temporizador configurado para 90 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(90)) }
    }

    @Test
    fun `parse temporizador de 1 hora y 1 ejecuta SetTimer 61`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(61)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 61 minutos.")

        val result = parser.parse("pon un temporizador de 1 hora y 1")

        assertEquals("Éxito: Temporizador configurado para 61 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(61)) }
    }

    @Test
    fun `parse temporizador de 1 hora y 2 ejecuta SetTimer 62`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(62)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 62 minutos.")

        val result = parser.parse("pon un temporizador de 1 hora y 2")

        assertEquals("Éxito: Temporizador configurado para 62 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(62)) }
    }

    @Test
    fun `parse temporizador de 2 minutos y 5 ejecuta SetTimer 7`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(7)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 7 minutos.")

        val result = parser.parse("pon un temporizador de 2 minutos y 5")

        assertEquals("Éxito: Temporizador configurado para 7 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(7)) }
    }

    @Test
    fun `parse temporizador de 1 hora y 2 minutos y 5 ejecuta SetTimer 67`() {
        // "2 minutos y 5": el 5 es desnudo (el lookahead bloquea el 2, que sí tiene unidad)
        coEvery { systemAction.execute(SystemCommand.SetTimer(67)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 67 minutos.")

        val result = parser.parse("pon un temporizador de 1 hora y 2 minutos y 5")

        assertEquals("Éxito: Temporizador configurado para 67 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(67)) }
    }

    @Test
    fun `parse temporizador de 1 hora y 30 minutos y 5 ejecuta SetTimer 95`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(95)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 95 minutos.")

        val result = parser.parse("pon un temporizador de 1 hora y 30 minutos y 5")

        assertEquals("Éxito: Temporizador configurado para 95 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(95)) }
    }

    @Test
    fun `parse temporizador de 1 hora y 60 devuelve null por rango del desnudo`() {
        // El desnudo es SIEMPRE minutos 0..59 (ADR-TMP-5): 60 fuera de rango → Gemini
        val result = parser.parse("pon un temporizador de 1 hora y 60")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse temporizador de 1 hora y 30 segundos ejecuta SetTimer 60`() {
        // El lookahead bloquea el 30 (tiene unidad "segundos") → no doble conteo
        coEvery { systemAction.execute(SystemCommand.SetTimer(60)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 60 minutos.")

        val result = parser.parse("pon un temporizador de 1 hora y 30 segundos")

        assertEquals("Éxito: Temporizador configurado para 60 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(60)) }
    }

    @Test
    fun `parse temporizador de 1 hora y 30 y 5 ejecuta SetTimer 90`() {
        // Trailing sin unidad se ignora (ADR-TMP-5): el 5 no suma, el 30 sí
        coEvery { systemAction.execute(SystemCommand.SetTimer(90)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 90 minutos.")

        val result = parser.parse("pon un temporizador de 1 hora y 30 y 5")

        assertEquals("Éxito: Temporizador configurado para 90 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(90)) }
    }

    // ===== TMP-num: unidades habladas y fracciones con unidad propia (ADR-TMP-6) =====

    @Test
    fun `parse temporizador de una hora ejecuta SetTimer 60`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(60)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 60 minutos.")

        val result = parser.parse("pon un temporizador de una hora")

        assertEquals("Éxito: Temporizador configurado para 60 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(60)) }
    }

    @Test
    fun `parse temporizador de una hora y media ejecuta SetTimer 90`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(90)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 90 minutos.")

        val result = parser.parse("pon un temporizador de una hora y media")

        assertEquals("Éxito: Temporizador configurado para 90 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(90)) }
    }

    @Test
    fun `parse temporizador de dos horas y media ejecuta SetTimer 150`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(150)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 150 minutos.")

        val result = parser.parse("pon un temporizador de dos horas y media")

        assertEquals("Éxito: Temporizador configurado para 150 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(150)) }
    }

    @Test
    fun `parse temporizador de media hora ejecuta SetTimer 30`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(30)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 30 minutos.")

        val result = parser.parse("pon un temporizador de media hora")

        assertEquals("Éxito: Temporizador configurado para 30 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(30)) }
    }

    @Test
    fun `parse temporizador de un cuarto de hora ejecuta SetTimer 15`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(15)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 15 minutos.")

        val result = parser.parse("pon un temporizador de un cuarto de hora")

        assertEquals("Éxito: Temporizador configurado para 15 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(15)) }
    }

    @Test
    fun `parse temporizador de un minuto ejecuta SetTimer 1`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(1)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 1 minutos.")

        val result = parser.parse("pon un temporizador de un minuto")

        assertEquals("Éxito: Temporizador configurado para 1 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(1)) }
    }

    @Test
    fun `parse temporizador de veinte y cinco minutos ejecuta SetTimer 25`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(25)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 25 minutos.")

        val result = parser.parse("pon un temporizador de veinte y cinco minutos")

        assertEquals("Éxito: Temporizador configurado para 25 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(25)) }
    }

    @Test
    fun `parse temporizador de cuarenta y cinco segundos ejecuta SetTimer 1`() {
        // 45 segundos → 0 minutos + ceil a 1 (solo segundos)
        coEvery { systemAction.execute(SystemCommand.SetTimer(1)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 1 minutos.")

        val result = parser.parse("pon un temporizador de cuarenta y cinco segundos")

        assertEquals("Éxito: Temporizador configurado para 1 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(1)) }
    }

    @Test
    fun `parse temporizador de 45 segundos ejecuta SetTimer 1 por el ceil`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(1)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 1 minutos.")

        val result = parser.parse("pon un temporizador de 45 segundos")

        assertEquals("Éxito: Temporizador configurado para 1 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(1)) }
    }

    @Test
    fun `parse temporizador de tres minutos y medio devuelve null`() {
        // "X minutos y medio" residual (fracción no ligada a horas) → ambiguo → Gemini
        val result = parser.parse("pon un temporizador de tres minutos y medio")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse temporizador de una hora y treinta minutos ejecuta SetTimer 90`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(90)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 90 minutos.")

        val result = parser.parse("pon un temporizador de una hora y treinta minutos")

        assertEquals("Éxito: Temporizador configurado para 90 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(90)) }
    }

    @Test
    fun `parse sesenta y cinco minutos devuelve null por no ser comando directo`() {
        // Sin "temporizador" ni "pasa" → no entra en la rama 3 → Gemini
        val result = parser.parse("sesenta y cinco minutos")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse temporizador de 30 minutos y media hora ejecuta SetTimer 60`() {
        // "media hora" sí está ligada a horas (no es residual)
        coEvery { systemAction.execute(SystemCommand.SetTimer(60)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 60 minutos.")

        val result = parser.parse("pon un temporizador de 30 minutos y media hora")

        assertEquals("Éxito: Temporizador configurado para 60 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(60)) }
    }

    @Test
    fun `parse temporizador de una hora y media y 5 ejecuta SetTimer 90`() {
        // La fracción ligada a horas suma 30; el 5 trailing se ignora
        coEvery { systemAction.execute(SystemCommand.SetTimer(90)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 90 minutos.")

        val result = parser.parse("pon un temporizador de una hora y media y 5")

        assertEquals("Éxito: Temporizador configurado para 90 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(90)) }
    }

    @Test
    fun `parse pasa una hora ejecuta SetTimer 60`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(60)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 60 minutos.")

        val result = parser.parse("pasa una hora")

        assertEquals("Éxito: Temporizador configurado para 60 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(60)) }
    }

    // ===== B1: rechazo de decenas no soportadas y compuestos inválidos (sin parcial silencioso) =====

    @Test
    fun `parse temporizador de sesenta y cinco minutos devuelve null`() {
        // 65 > 59 con decena no soportada: durationSpokenUnitRegex saltaría la decena y
        // capturaría "cinco minutos" → rechazo completo (B3/ADR-TMP-6)
        val result = parser.parse("pon un temporizador de sesenta y cinco minutos")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse temporizador de veinte y diez minutos devuelve null`() {
        // Compuesto inválido (token2 fuera de 1-9): el match parcial "diez minutos" no vale
        val result = parser.parse("pon un temporizador de veinte y diez minutos")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse temporizador de una hora y cinco minutos ejecuta SetTimer 65`() {
        // La decena NO precede a "cinco minutos" (va "hora y "): el residual B1 no aplica
        coEvery { systemAction.execute(SystemCommand.SetTimer(65)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 65 minutos.")

        val result = parser.parse("pon un temporizador de una hora y cinco minutos")

        assertEquals("Éxito: Temporizador configurado para 65 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(65)) }
    }

    // ===== Anti-doble-conteo de fracciones ("1 hora y media hora" → 90, no 120) =====

    @Test
    fun `parse temporizador de 1 hora y media hora ejecuta SetTimer 90`() {
        // La fracción compuesta "1 hora y media" NO suma aquí: la suma la hace "media hora"
        coEvery { systemAction.execute(SystemCommand.SetTimer(90)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 90 minutos.")

        val result = parser.parse("pon un temporizador de 1 hora y media hora")

        assertEquals("Éxito: Temporizador configurado para 90 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(90)) }
    }

    @Test
    fun `parse temporizador de 1 hora y cuarto de hora ejecuta SetTimer 75`() {
        coEvery { systemAction.execute(SystemCommand.SetTimer(75)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 75 minutos.")

        val result = parser.parse("pon un temporizador de 1 hora y cuarto de hora")

        assertEquals("Éxito: Temporizador configurado para 75 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(75)) }
    }

    // ===== Guard ampliado de la rama 3 (sin duración válida → Gemini) =====

    @Test
    fun `parse temporizador de arena devuelve null por no tener duracion`() {
        val result = parser.parse("temporizador de arena")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse quita el temporizador devuelve null`() {
        val result = parser.parse("quita el temporizador")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse pon un temporizador sin duracion devuelve null`() {
        val result = parser.parse("pon un temporizador")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    // ===== M3: fracciones compuestas con findAll (ADR-TMP-1: suma de TODAS) =====

    @Test
    fun `parse temporizador de 1 hora y media y 2 horas y cuarto ejecuta SetTimer 225`() {
        // 60 + 120 (unidades) + 30 + 15 (fracciones findAll): ninguna fracción se pierde
        coEvery { systemAction.execute(SystemCommand.SetTimer(225)) } returns
            ActionResult.Success("Éxito: Temporizador configurado para 225 minutos.")

        val result = parser.parse("pon un temporizador de 1 hora y media y 2 horas y cuarto")

        assertEquals("Éxito: Temporizador configurado para 225 minutos.", result)
        coVerify { systemAction.execute(SystemCommand.SetTimer(225)) }
    }

    // ===== O6: espacios iniciales reales (trim en parse — ADR-010) =====
    // El comando debe parsear igual con espacios delante (" busca gatos" = "busca gatos").

    // Helper estilo JUnit4 clásico: verifica el comando esperado y su ejecución con MockK.
    private fun assertCommandWithLeadingSpace(input: String, expected: SystemCommand) {
        coEvery { systemAction.execute(expected) } returns ActionResult.Success("OK")
        val result = parser.parse(input)
        assertEquals("OK", result)
        coVerify { systemAction.execute(expected) }
    }

    @Test
    fun `parse con espacio inicial busca gatos ejecuta SearchGoogle gatos`() {
        assertCommandWithLeadingSpace(" busca gatos", SystemCommand.SearchGoogle("gatos"))
    }

    @Test
    fun `parse con doble espacio inicial busca gatos ejecuta SearchGoogle gatos`() {
        assertCommandWithLeadingSpace("  busca gatos", SystemCommand.SearchGoogle("gatos"))
    }

    @Test
    fun `parse con espacio inicial abre whatsapp ejecuta OpenApp whatsapp`() {
        assertCommandWithLeadingSpace(" abre whatsapp", SystemCommand.OpenApp("whatsapp"))
    }

    @Test
    fun `parse con espacio inicial repite eso devuelve REPEAT`() {
        val result = parser.parse(" repite eso")

        assertEquals(CommandMarkers.REPEAT, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse con espacio inicial sube el volumen ejecuta SetVolume UP`() {
        assertCommandWithLeadingSpace(" sube el volumen", SystemCommand.SetVolume(VolumeAction.UP))
    }

    @Test
    fun `parse con espacio inicial llevame a la oficina ejecuta Navigate la oficina`() {
        assertCommandWithLeadingSpace(" llevame a la oficina", SystemCommand.Navigate("la oficina"))
    }

    @Test
    fun `parse con espacio inicial recuerda comprar leche ejecuta SaveMemory comprar leche`() {
        assertCommandWithLeadingSpace(" recuerda comprar leche", SystemCommand.SaveMemory("comprar leche"))
    }

    @Test
    fun `parse con espacio inicial llama a ana ejecuta Call ana`() {
        assertCommandWithLeadingSpace(" llama a ana", SystemCommand.Call("ana"))
    }

    @Test
    fun `parse con espacio inicial habla en ingles ejecuta SetLanguage ENGLISH`() {
        assertCommandWithLeadingSpace(" habla en ingles", SystemCommand.SetLanguage(AssistantLanguage.ENGLISH))
    }

    @Test
    fun `parse con espacio inicial pasa 2 horas ejecuta SetTimer 120`() {
        assertCommandWithLeadingSpace(" pasa 2 horas", SystemCommand.SetTimer(120))
    }

    @Test
    fun `parse con espacio inicial pon una alarma a las 7 ejecuta SetAlarm 0700`() {
        assertCommandWithLeadingSpace(" pon una alarma a las 7", SystemCommand.SetAlarm(7, 0, null))
    }

    @Test
    fun `parse con doble espacio inicial cancela la alarma de las 7 ejecuta CancelAlarm`() {
        assertCommandWithLeadingSpace("  cancela la alarma de las 7", SystemCommand.CancelAlarm(7, 0))
    }

    @Test
    fun `parse con espacio inicial toma nota del pan ejecuta CreateNote pan`() {
        assertCommandWithLeadingSpace(" toma nota del pan", SystemCommand.CreateNote("pan"))
    }

    @Test
    fun `parse con espacio inicial que puedes hacer devuelve HELP`() {
        val result = parser.parse(" que puedes hacer")

        assertEquals(CommandMarkers.HELP, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    // ===== H3: "busca:"/"repite:" — el ':' como separador de comando (ADR-011) =====

    @Test
    fun `parse busca colon gatos ejecuta SearchGoogle gatos`() {
        assertCommandWithLeadingSpace("busca: gatos", SystemCommand.SearchGoogle("gatos"))
    }

    @Test
    fun `parse busca colon gatos pegado ejecuta SearchGoogle gatos`() {
        assertCommandWithLeadingSpace("busca:gatos", SystemCommand.SearchGoogle("gatos"))
    }

    @Test
    fun `parse busca espacio colon espacio gatos ejecuta SearchGoogle gatos`() {
        // Bug preexistente ("busca : gatos" caía a Gemini): el trimStart(':') lo arregla
        assertCommandWithLeadingSpace("busca : gatos", SystemCommand.SearchGoogle("gatos"))
    }

    @Test
    fun `parse BUSCA colon GATOS ejecuta SearchGoogle GATOS preservando el case`() {
        // El guard decide sobre trimmed pero la query se extrae del original: case intacto
        assertCommandWithLeadingSpace("BUSCA: GATOS", SystemCommand.SearchGoogle("GATOS"))
    }

    @Test
    fun `parse repite colon eso devuelve REPEAT`() {
        val result = parser.parse("repite: eso")

        assertEquals(CommandMarkers.REPEAT, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse repite colon 19 colon 45 devuelve REPEAT y no ejecuta SetAlarm`() {
        // "repite: 19:45" NO es una hora: el ':' tras palabra es separador de comando,
        // solo el dígito-: -dígito de la rama alarma se interpreta como hora (ADR-011)
        val result = parser.parse("repite: 19:45")

        assertEquals(CommandMarkers.REPEAT, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    // ===== H5: "que puedes hacer:" con ':' al final (ADR-011) =====

    @Test
    fun `parse que puedes hacer con colon final devuelve HELP`() {
        val result = parser.parse("que puedes hacer:")

        assertEquals(CommandMarkers.HELP, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse que puedes hacer colon con contenido devuelve HELP`() {
        val result = parser.parse("que puedes hacer: x")

        assertEquals(CommandMarkers.HELP, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse piensa que puedes hacer colon nada devuelve HELP aceptando el falso positivo`() {
        // Falso positivo aceptado y documentado (ADR-011): el límite (?=\s|:|$) matchea
        // el ':' de "hacer:" aunque la frase real no sea una petición de ayuda
        val result = parser.parse("piensa que puedes hacer: nada")

        assertEquals(CommandMarkers.HELP, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    // ===== Ronda de cierre: QA + Supervisor =====

    @Test
    fun `parse busca con doble dos puntos y espacios limpia la query`() {
        // Fija el orden trim → trimStart(':') → trim de la rama 10: "busca: : gatos"
        // matchea "busca:" y el ':' colgante se limpia del argumento
        assertCommandWithLeadingSpace("busca: : gatos", SystemCommand.SearchGoogle("gatos"))
    }

    @Test
    fun `parse busca con dos puntos y hora no confunde la rama de alarma`() {
        // Lado SearchGoogle de la desambiguación dígito-: vs palabra-: (ADR-011):
        // "busca: 19:45" es un comando de búsqueda, no una hora de la rama alarma.
        // Patrón explícito (no helper): permite verificar que la rama alarma NO se ejecutó.
        coEvery { systemAction.execute(SystemCommand.SearchGoogle("19:45")) } returns ActionResult.Success("OK")
        val result = parser.parse("busca: 19:45")
        assertEquals("OK", result)
        coVerify { systemAction.execute(SystemCommand.SearchGoogle("19:45")) }
        // Nota: no usar SetAlarm(any(), any(), any()) — el child-matching de data class
        // falla en MockK 1.13.12 (MockKException). El matcher directo sobre el arg es estable.
        coVerify(exactly = 0) { systemAction.execute(match { it is SystemCommand.SetAlarm }) }
    }

    @Test
    fun `parse ayudame sin dos puntos no matchea HELP`() {
        // F2: "ayudame" no es "ayuda" ni "ayuda:" ni HELP_PHRASE — cae a Gemini
        val result = parser.parse("ayudame")

        assertNull(result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse ayuda con dos puntos devuelve HELP`() {
        // M1: "ayuda:" — palabra-: = separador de comando (consistencia con H3)
        val result = parser.parse("ayuda:")

        assertEquals(CommandMarkers.HELP, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse comandos con dos puntos devuelve HELP`() {
        // M1: "comandos:" igual que "ayuda:"
        val result = parser.parse("comandos:")

        assertEquals(CommandMarkers.HELP, result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `parse abre whatsapp con signos de apertura y cierre ejecuta OpenApp whatsapp limpio`() {
        // M2: O6 expuso el residuo de puntuación — "¿abre whatsapp?" → OpenApp("whatsapp?")
        // (el '?' final se colaba en el argumento; antes caía a Gemini por el espacio inicial)
        assertCommandWithLeadingSpace("¿abre whatsapp?", SystemCommand.OpenApp("whatsapp"))
    }

    @Test
    fun `parse busca colon gatos con signo de cierre ejecuta SearchGoogle gatos`() {
        // M2: solo se limpian los BORDES; la puntuación interior del argumento no se toca
        assertCommandWithLeadingSpace("busca: gatos?", SystemCommand.SearchGoogle("gatos"))
    }

    @Test
    fun `parse con espacio inicial y colon busca gatos ejecuta SearchGoogle gatos`() {
        // M4: caso combinado del backlog — espacio inicial + separador ':' a la vez
        assertCommandWithLeadingSpace(" busca: gatos", SystemCommand.SearchGoogle("gatos"))
    }

    @Test
    fun `parse con espacio inicial llama al 600 ejecuta CallNumber 600`() {
        // M4: rama 4a con espacio inicial (antes no cubierta en O6)
        assertCommandWithLeadingSpace(" llama al 600", SystemCommand.CallNumber("600"))
    }

    @Test
    fun `parse con espacio inicial abre los ajustes ejecuta OpenSettings`() {
        // M4: rama 7 con espacio inicial (antes no cubierta en O6)
        assertCommandWithLeadingSpace(" abre los ajustes", SystemCommand.OpenSettings)
    }
}
