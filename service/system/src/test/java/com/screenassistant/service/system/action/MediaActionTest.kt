package com.screenassistant.service.system.action

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * MediaAction M9: la query de YouTube se codifica con Uri.encode (espacios/`&`/`?`
 * → URIs malformados sin codificar) y el fallback del navegador no puede relanzar
 * startActivity fuera de ningún try (excepción cruda al usuario).
 *
 * Patrón del repo (AlarmActionTest): mockkStatic(Uri::class) para Uri.parse y
 * Uri.encode (los stubs de android.jar devuelven null con returnDefaultValues en
 * JVM unit → la query quedaría "null"). Uri.encode se emula con URLEncoder (Java
 * puro, mismo resultado: espacios a %20, & a %26) y mockkConstructor(Intent::class)
 * para los constructores de Intent. La query se captura en un slot y el resultado
 * se verifica contra el string codificado.
 */
class MediaActionTest {

    private lateinit var context: Context
    private lateinit var mediaAction: MediaAction
    private val uriSlot = slot<String>()

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        mediaAction = MediaAction(context)
        mockkStatic(Uri::class)
        every { Uri.parse(capture(uriSlot)) } answers { mockk() }
        // Uri.encode es stub de android.jar (devuelve null): se emula con
        // URLEncoder (espacio → %20, & → %26 — idéntico a Uri.encode).
        every { Uri.encode(any()) } answers {
            java.net.URLEncoder.encode(firstArg<String>(), "UTF-8").replace("+", "%20")
        }
        mockkConstructor(Intent::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
        unmockkConstructor(Intent::class)
    }

    @Test
    fun `openYouTube codifica la query en la uri de la app`() {
        val result = mediaAction.openYouTube("gato & perro")

        assertEquals("Éxito: YouTube abierto.", result)
        // M9: Uri.encode → espacios a %20 y & a %26 (antes la query viajaba cruda
        // y el & partía la query del intent).
        assertEquals("vnd.youtube:results?search_query=gato%20%26%20perro", uriSlot.captured)
    }

    @Test
    fun `openYouTube con query null abre la uri base de la app`() {
        val result = mediaAction.openYouTube(null)

        assertEquals("Éxito: YouTube abierto.", result)
        // M9 caso 3: query null → uri BASE vnd.youtube:// (sin search_query, sin encode).
        assertEquals("vnd.youtube://", uriSlot.captured)
    }

    @Test
    fun `openYouTube sin app usa el fallback del navegador con la query codificada`() {
        var startAttempts = 0
        every { context.startActivity(any()) } answers {
            startAttempts++
            if (startAttempts == 1) throw RuntimeException("app no instalada")
            Unit
        }

        val result = mediaAction.openYouTube("a & b")

        assertEquals("Éxito: YouTube abierto en el navegador.", result)
        // El fallback codifica la query igual que la uri de la app (M9) — el slot
        // captura la ÚLTIMA llamada a Uri.parse (la del navegador).
        assertEquals("https://www.youtube.com/results?search_query=a%20%26%20b", uriSlot.captured)
    }
}
