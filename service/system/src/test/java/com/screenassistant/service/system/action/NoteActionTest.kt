package com.screenassistant.service.system.action

import android.content.Context
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * NoteAction contra el sistema de archivos REAL (TemporaryFolder de JUnit):
 * directorio de notas apuntando a tempFolder.root/notas y reloj fijo
 * (now = { 1750000000000L }) para nombres deterministas.
 */
class NoteActionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val fixedMillis = 1750000000000L

    private fun createAction(
        notesDir: File = File(tempFolder.root, "notas"),
        now: () -> Long = { fixedMillis }
    ): NoteAction = NoteAction(
        context = mockk(relaxed = true),
        notesDirProvider = { notesDir },
        now = now
    )

    @Test
    fun `saveNote escribe el archivo con el contenido exacto en UTF-8`() = runTest {
        val action = createAction()

        val result = action.saveNote("comprar leche y café")

        assertEquals("Éxito: Nota guardada. Empieza así: «comprar leche y café»", result)
        val file = File(tempFolder.root, "notas/1750000000000.txt")
        assertTrue(file.exists())
        assertEquals("comprar leche y café", file.readText(Charsets.UTF_8))
    }

    @Test
    fun `saveNote con reloj fijo genera un nombre determinista`() = runTest {
        val action = createAction()

        action.saveNote("nota determinista")

        assertTrue(File(tempFolder.root, "notas/1750000000000.txt").exists())
    }

    @Test
    fun `saveNote dos veces crea dos archivos con nombres unicos`() = runTest {
        val action = createAction()

        action.saveNote("primera nota")
        action.saveNote("segunda nota")

        val names = File(tempFolder.root, "notas").listFiles().orEmpty()
            .map { it.name }.sorted()
        assertEquals(listOf("1750000000000.txt", "1750000000001.txt"), names)
    }

    @Test
    fun `saveNote con colision de timestamp incrementa el nombre`() = runTest {
        val action = createAction()

        action.saveNote("primera nota")
        action.saveNote("segunda nota")

        val second = File(tempFolder.root, "notas/1750000000001.txt")
        assertTrue(second.exists())
        assertEquals("segunda nota", second.readText(Charsets.UTF_8))
    }

    @Test
    fun `saveNote con 1000 caracteres exactos guarda integro sin aviso`() = runTest {
        val text = "a".repeat(1000)
        val action = createAction()

        val result = action.saveNote(text)

        assertFalse(result.contains("aviso"))
        assertEquals(
            1000,
            File(tempFolder.root, "notas/1750000000000.txt").readText(Charsets.UTF_8).length
        )
    }

    @Test
    fun `saveNote con 1200 caracteres trunca a 1000 y avisa`() = runTest {
        val text = "a".repeat(1200)
        val action = createAction()

        val result = action.saveNote(text)

        assertTrue(result.contains("aviso"))
        assertTrue(result.contains("1200"))
        assertEquals(
            1000,
            File(tempFolder.root, "notas/1750000000000.txt").readText(Charsets.UTF_8).length
        )
    }

    @Test
    fun `saveNote con 1500 caracteres trunca y avisa con 1500`() = runTest {
        val text = "b".repeat(1500)
        val action = createAction()

        val result = action.saveNote(text)

        assertTrue(result.contains("aviso"))
        assertTrue(result.contains("1500"))
        assertEquals(
            1000,
            File(tempFolder.root, "notas/1750000000000.txt").readText(Charsets.UTF_8).length
        )
    }

    @Test
    fun `saveNote con texto largo recorta el extracto a 80 mas puntos`() = runTest {
        val text = "c".repeat(200)
        val action = createAction()

        val result = action.saveNote(text)

        assertTrue(result.startsWith("Éxito: Nota guardada. Empieza así: «"))
        val excerpt = result.substringAfter("«").substringBefore("»")
        assertEquals(83, excerpt.length)
        assertTrue(excerpt.endsWith("..."))
    }

    @Test
    fun `saveNote con directorio no creable devuelve error`() = runTest {
        val blocker = tempFolder.newFile("bloqueo")
        val action = createAction(notesDir = File(blocker, "sub/notas"))

        val result = action.saveNote("hola")

        assertEquals("Error: No pude guardar la nota.", result)
    }

    @Test
    fun `saveNote con texto vacio devuelve error y no crea archivo`() = runTest {
        val action = createAction()

        val result = action.saveNote("   ")

        assertEquals("Error: La nota está vacía.", result)
        assertFalse(File(tempFolder.root, "notas/1750000000000.txt").exists())
    }

    // ===== N-OBS4: catch del fallo de escritura (provider writeNote inyectado) =====

    @Test
    fun `saveNote con fallo de escritura devuelve error y el provider recibe el archivo`() = runTest {
        // La lambda inyectada captura los argumentos (equivalente a un slot capture) y
        // lanza IOException para cubrir el catch del writeText real.
        var receivedFile: File? = null
        val action = NoteAction(
            context = mockk(relaxed = true),
            notesDirProvider = { File(tempFolder.root, "notas") },
            now = { fixedMillis },
            writeNote = { file, _ ->
                receivedFile = file
                throw java.io.IOException("disco lleno")
            }
        )

        val result = action.saveNote("hola")

        assertEquals("Error: No pude guardar la nota.", result)
        assertEquals(File(tempFolder.root, "notas"), receivedFile?.parentFile)
        assertEquals("1750000000000.txt", receivedFile?.name)
    }

    // ===== N-OBS5: truncado por code points (nunca parte un surrogate pair) =====

    @Test
    fun `saveNote con emoji en el limite no parte el surrogate pair`() = runTest {
        // 999 'a' + 😀 (2 unidades UTF-16) + 20 'b': el corte en 1000 code points debe
        // dejar el 😀 COMPLETO (999+2 = 1001 unidades UTF-16) y avisar de que cortó.
        val text = "a".repeat(999) + "😀" + "b".repeat(20)
        val action = createAction()

        val result = action.saveNote(text)

        assertTrue(result.contains("aviso"))
        val content = File(tempFolder.root, "notas/1750000000000.txt").readText(Charsets.UTF_8)
        assertEquals(1001, content.length)
        assertTrue(content.endsWith("😀"))
    }

    @Test
    fun `saveNote recorta el extracto sin partir un emoji en el borde`() = runTest {
        val text = "c".repeat(79) + "😀" + "d".repeat(120)
        val action = createAction()

        val result = action.saveNote(text)

        assertTrue(result.startsWith("Éxito: Nota guardada. Empieza así: «"))
        val excerpt = result.substringAfter("«").substringBefore("»")
        assertEquals("c".repeat(79) + "😀...", excerpt)
    }

    // ===== N1: readNotesSummary (resumen) =====

    @Test
    fun `readNotesSummary sin notas devuelve error`() = runTest {
        val action = createAction()

        val result = action.readNotesSummary()

        assertEquals("Error: No tienes notas.", result)
    }

    @Test
    fun `readNotesSummary con una nota devuelve extracto en singular`() = runTest {
        val action = createAction()
        action.saveNote("comprar leche y café")

        val result = action.readNotesSummary()

        assertEquals("Éxito: Tienes 1 nota. Empieza así: «comprar leche y café»", result)
    }

    @Test
    fun `readNotesSummary con varias notas devuelve la mas reciente en plural`() = runTest {
        val action = createAction()
        action.saveNote("primera nota")
        action.saveNote("segunda nota")

        val result = action.readNotesSummary()

        assertEquals("Éxito: Tienes 2 notas. La más reciente empieza así: «segunda nota»", result)
    }

    @Test
    fun `readNotesSummary ignora archivos ajenos al formato de nota`() = runTest {
        val action = createAction()
        action.saveNote("única nota")
        File(tempFolder.root, "notas/README.txt").writeText("no soy una nota")

        val result = action.readNotesSummary()

        assertEquals("Éxito: Tienes 1 nota. Empieza así: «única nota»", result)
    }

    @Test
    fun `readNotesSummary con nota larga recorta el extracto a 80 mas puntos`() = runTest {
        val action = createAction()
        action.saveNote("e".repeat(200))

        val result = action.readNotesSummary()

        val excerpt = result.substringAfter("«").substringBefore("»")
        assertEquals(83, excerpt.length)
        assertTrue(excerpt.endsWith("..."))
    }

    // ===== N1: readNote (búsqueda por contenido) =====

    @Test
    fun `readNote sin coincidencia devuelve error con la query`() = runTest {
        val action = createAction()
        action.saveNote("comprar leche")

        val result = action.readNote("pan")

        assertEquals("Error: No encontré ninguna nota con «pan».", result)
    }

    @Test
    fun `readNote con coincidencia corta devuelve el contenido completo`() = runTest {
        val action = createAction()
        action.saveNote("comprar leche y café")

        val result = action.readNote("leche")

        assertEquals("Éxito: La nota dice: «comprar leche y café»", result)
    }

    @Test
    fun `readNote con coincidencia larga trunca a 400 y avisa de la longitud`() = runTest {
        val action = createAction()
        action.saveNote("f".repeat(500))

        val result = action.readNote("fffff")

        assertTrue(result.startsWith("Éxito: La nota es larga, 500 caracteres; te leo el principio: «"))
        val chunk = result.substringAfter("«").substringBefore("»")
        assertEquals(403, chunk.length) // 400 f + "..."
    }

    @Test
    fun `readNote con varias coincidencias lee la mas reciente`() = runTest {
        val action = createAction()
        action.saveNote("nota de prueba uno")
        action.saveNote("prueba de la segunda nota")

        val result = action.readNote("prueba")

        assertEquals(
            "Éxito: Encontré 2 notas con «prueba»; te leo la más reciente: «prueba de la segunda nota»",
            result
        )
    }

    @Test
    fun `readNote normaliza tildes y enie para la coincidencia`() = runTest {
        val action = createAction()
        action.saveNote("reunión con el médico")

        val result = action.readNote("reunion")

        assertEquals("Éxito: La nota dice: «reunión con el médico»", result)
    }

    @Test
    fun `readNote normaliza la u con dieresis para la coincidencia`() = runTest {
        // Ronda de cierre: normalizeLocal replica la cadena del parser, incluido ü→u
        val action = createAction()
        action.saveNote("pingüino")

        val result = action.readNote("pinguino")

        assertEquals("Éxito: La nota dice: «pingüino»", result)
    }

    @Test
    fun `readNote normaliza la puntuacion interior a espacio para la coincidencia`() = runTest {
        // Ronda de cierre: "pan, leche" (puntuación→espacio) matchea "pan leche"
        val action = createAction()
        action.saveNote("comprar pan, leche y café")

        val result = action.readNote("pan leche")

        assertEquals("Éxito: La nota dice: «comprar pan, leche y café»", result)
    }

    @Test
    fun `readNote con emoji en la posicion 400 no parte el surrogate del chunk`() = runTest {
        // Ronda de cierre: 399 'a' + 😀 (2 unidades UTF-16) + 50 'b' → la nota es
        // "larga" (>400 code points), el corte a 400 code points deja el 😀 COMPLETO
        // al final del chunk (399+2 = 401 UTF-16) — sin half-surrogate.
        val action = createAction()
        action.saveNote("a".repeat(399) + "😀" + "b".repeat(50))

        val result = action.readNote("aaaa")

        assertTrue(result.startsWith("Éxito: La nota es larga"))
        val chunk = result.substringAfter("«").substringBefore("...»")
        assertEquals("a".repeat(399) + "😀", chunk)
    }

    @Test
    fun `readNote con 400 code points incluyendo emoji lee completo sin puntos suspensivos`() = runTest {
        // Ronda de cierre: 399 'a' + 😀 = 400 code points pero 401 UTF-16. La nota CABE
        // en 400 code points percibidos → lectura COMPLETA, sin "larga" ni "..." (el
        // truncado por code points devolvió el contenido íntegro).
        val action = createAction()
        action.saveNote("a".repeat(399) + "😀")

        val result = action.readNote("aaaa")

        assertFalse(result.contains("larga"))
        assertFalse(result.contains("..."))
        assertEquals("Éxito: La nota dice: «" + "a".repeat(399) + "😀»", result)
    }

    // ===== QA ronda de cierre: nota 100% emoji (code points < max pero UTF-16 > max) =====

    @Test
    fun `saveNote con 250 emojis puros guarda integro y trunca el extracto por code points`() = runTest {
        // 250 emojis = 250 code points pero 500 unidades UTF-16. El extracto (80 code
        // points) corta limpio: 80 emojis COMPLETOS (160 UTF-16), sin half-surrogate
        // ni excepción.
        val action = createAction()

        val result = action.saveNote("😀".repeat(250))

        assertEquals("Éxito: Nota guardada. Empieza así: «" + "😀".repeat(80) + "...»", result)
        val saved = File(tempFolder.root, "notas/1750000000000.txt").readText(Charsets.UTF_8)
        assertEquals("😀".repeat(250), saved)
    }

    @Test
    fun `readNote con 250 emojis puros lee completa sin crash`() = runTest {
        // Bug latente QA: 250 emojis = 250 code points < 400 pero 500 UTF-16 > 400. El
        // guard inicial del truncado entraba al bucle y, sin el guard `idx < text.length`,
        // codePointAt(500) lanzaba StringIndexOutOfBoundsException → el catch genérico
        // devolvía "Error: No pude leer las notas.". La nota cabe en 400 code points →
        // lectura COMPLETA de los 250 emojis.
        val action = createAction()
        action.saveNote("😀".repeat(250))

        val result = action.readNote("😀")

        assertFalse(result.startsWith("Error:"))
        assertEquals("Éxito: La nota dice: «" + "😀".repeat(250) + "»", result)
    }

    @Test
    fun `readNote con 450 emojis trunca el chunk a 400 emojis completos`() = runTest {
        // 450 emojis = 450 code points > 400 → el chunk se trunca a 400 code points =
        // 400 emojis COMPLETOS (800 UTF-16 exactos), sin half-surrogate (el equals
        // fallaría si quedara un char suelto en rango surrogate).
        val action = createAction()
        action.saveNote("😀".repeat(450))

        val result = action.readNote("😀")

        assertTrue(result.startsWith("Éxito: La nota es larga, 900 caracteres; te leo el principio: «"))
        val chunk = result.substringAfter("«").substringBefore("...»")
        assertEquals("😀".repeat(400), chunk)
    }
}
