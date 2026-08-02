package com.screenassistant.service.system.action

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.mockk.EqMatcher
import io.mockk.OfTypeMatcher
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * MapsAction con MockK puro:
 *  - Uri se mockea estático: parse captura el uri en uriSlot y encode se stubea
 *    (en JVM Uri.encode devuelve null sin Robolectric)
 *  - Intent se mockea a nivel de constructor con constructedWith (patrón del repo)
 */
class MapsActionTest {

    private lateinit var context: Context
    private lateinit var mapsAction: MapsAction

    private val uriSlot = slot<String>()
    private val uriMock = mockk<Uri>()

    @Before
    fun setup() {
        context = mockk(relaxed = true)

        mockkStatic(Uri::class)
        every { Uri.parse(capture(uriSlot)) } returns uriMock
        every { Uri.encode(any()) } returns "la%20oficina"

        mockkConstructor(Intent::class)
        every {
            constructedWith<Intent>(EqMatcher(Intent.ACTION_VIEW), OfTypeMatcher<Uri>(Uri::class))
                .setFlags(any())
        } returns mockk()

        mapsAction = MapsAction(context)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
        unmockkConstructor(Intent::class)
    }

    @Test
    fun `navigateTo codifica el destino y construye geo con la query`() {
        val result = mapsAction.navigateTo("la oficina")

        assertEquals("Éxito: Abriendo Maps hacia la oficina.", result)
        verify { Uri.encode("la oficina") }
        assertEquals("geo:0,0?q=la%20oficina", uriSlot.captured)
        verify { context.startActivity(any()) }
    }

    @Test
    fun `navigateTo marca el intent con NEW_TASK`() {
        mapsAction.navigateTo("la oficina")

        verify {
            constructedWith<Intent>(EqMatcher(Intent.ACTION_VIEW), OfTypeMatcher<Uri>(Uri::class))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    @Test
    fun `navigateTo devuelve error si startActivity lanza excepcion`() {
        every { context.startActivity(any()) } throws RuntimeException("boom")

        val result = mapsAction.navigateTo("la oficina")

        assertEquals("Error: No se pudo abrir Maps.", result)
    }
}
