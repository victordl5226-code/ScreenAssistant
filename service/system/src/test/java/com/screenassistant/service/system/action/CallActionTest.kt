package com.screenassistant.service.system.action

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * CallAction con MockK puro (sin Robolectric):
 *  - Uri se mockea estático y el argumento se captura en uriSlot
 *  - Intent se mockea a nivel de constructor; el action se verifica en el
 *    CONSTRUCTOR (B2) mediante constructedWith (patrón documentado de MockK;
 *    verify { Intent(...) } no matchea en 1.13.12)
 *  - anyConstructed y constructedWith son PROTOTIPOS DISTINTOS (mockk #1501):
 *    las llamadas se graban solo en el prototipo registrado ANTES de la
 *    llamada de producción, por eso los stubs usan constructedWith en @Before
 *    y el verify reutiliza los MISMO matchers (mockk #1426)
 *  - mockkConstructor NO acepta relaxed en 1.13.12 (verificado contra el jar):
 *    se stuban los métodos que la producción invoca sobre los prototipos
 */
class CallActionTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var cursor: Cursor
    private lateinit var callAction: CallAction

    private val uriSlot = slot<String>()
    private val intentSlot = slot<Intent>()
    private val uriMock = mockk<Uri>()

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        cursor = mockk(relaxed = true)
        every { context.contentResolver } returns contentResolver

        mockkStatic(Uri::class)
        every { Uri.parse(capture(uriSlot)) } returns uriMock   // M4: tipado

        mockkConstructor(Intent::class)
        // Prototipos por constructor registrados ANTES de la producción (B2),
        // con los métodos que usa cada rama de CallAction.
        every {
            constructedWith<Intent>(EqMatcher(Intent.ACTION_CALL), OfTypeMatcher<Uri>(Uri::class))
                .setFlags(any())
        } returns mockk()
        every {
            constructedWith<Intent>(EqMatcher(Intent.ACTION_SENDTO)).setFlags(any())
        } returns mockk()
        every {
            constructedWith<Intent>(EqMatcher(Intent.ACTION_SENDTO)).setData(any())
        } returns mockk()
        every {
            constructedWith<Intent>(EqMatcher(Intent.ACTION_SENDTO)).putExtra(any(), any<String>())
        } returns mockk()

        callAction = CallAction(context)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)          // B1
        unmockkConstructor(Intent::class)  // B1
    }

    // ===== makeCall =====

    @Test
    fun `makeCall con contacto encontrado construye tel y arranca la llamada`() {
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getString(0) } returns "600123456"

        val result = callAction.makeCall("Ana")

        assertEquals("Éxito: Llamando a Ana...", result)
        assertEquals("tel:600123456", uriSlot.captured)
        // B2: el action se verifica en el constructor registrado
        verify {
            constructedWith<Intent>(EqMatcher(Intent.ACTION_CALL), OfTypeMatcher<Uri>(Uri::class))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        verify { context.startActivity(capture(intentSlot)) }
    }

    @Test
    fun `makeCall con cursor vacio no encuentra contacto y no arranca nada`() {
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor
        every { cursor.moveToFirst() } returns false

        val result = callAction.makeCall("Ana")

        assertEquals("Error: No encontré el número de Ana en tus contactos.", result)
        verify(exactly = 0) { context.startActivity(any()) }
        verify(exactly = 0) { Uri.parse(any()) }
    }

    @Test
    fun `makeCall con query nulo devuelve mensaje de no encontrado sin arrancar nada`() {
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns null

        val result = callAction.makeCall("Ana")

        assertEquals("Error: No encontré el número de Ana en tus contactos.", result)
        verify(exactly = 0) { context.startActivity(any()) }
    }

    @Test
    fun `makeCall devuelve error de permiso si startActivity lanza ActivityNotFoundException`() {
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getString(0) } returns "600123456"
        every { context.startActivity(any()) } throws ActivityNotFoundException()

        val result = callAction.makeCall("Ana")

        assertEquals("Error: No tengo permiso para llamar o hubo un error.", result)
    }

    // ===== makeCallToNumber (Fase B: "llama al 600 123 456") =====

    @Test
    fun `makeCallToNumber construye tel con el numero y arranca la llamada`() {
        val result = callAction.makeCallToNumber("600123456")

        assertEquals("Éxito: Llamando al 600123456...", result)
        assertEquals("tel:600123456", uriSlot.captured)
        verify {
            constructedWith<Intent>(EqMatcher(Intent.ACTION_CALL), OfTypeMatcher<Uri>(Uri::class))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        verify { context.startActivity(capture(intentSlot)) }
    }

    @Test
    fun `makeCallToNumber no consulta los contactos`() {
        callAction.makeCallToNumber("600123456")

        verify(exactly = 0) { contentResolver.query(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `makeCallToNumber con prefijo internacional preserva el mas`() {
        val result = callAction.makeCallToNumber("+34600123456")

        assertEquals("Éxito: Llamando al +34600123456...", result)
        assertEquals("tel:+34600123456", uriSlot.captured)
    }

    @Test
    fun `makeCallToNumber devuelve error de permiso si startActivity lanza`() {
        every { context.startActivity(any()) } throws ActivityNotFoundException()

        val result = callAction.makeCallToNumber("600123456")

        assertEquals("Error: No tengo permiso para llamar o hubo un error.", result)
    }

    // ===== sendSms =====

    @Test
    fun `sendSms con contacto construye smsto con el numero y los extras`() {
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getString(0) } returns "600123456"

        val result = callAction.sendSms("Ana", "Hola Ana")

        assertEquals("Éxito: Preparando SMS para Ana.", result)
        assertEquals("smsto:600123456", uriSlot.captured)
        verify {
            constructedWith<Intent>(EqMatcher(Intent.ACTION_SENDTO))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        verify {
            constructedWith<Intent>(EqMatcher(Intent.ACTION_SENDTO)).putExtra("sms_body", "Hola Ana")
        }
        verify { context.startActivity(capture(intentSlot)) }
    }

    @Test
    fun `sendSms sin contacto usa el nombre como fallback en el smsto`() {
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor
        every { cursor.moveToFirst() } returns false

        val result = callAction.sendSms("Ana", "Hola Ana")

        assertEquals("Éxito: Preparando SMS para Ana.", result)
        assertEquals("smsto:Ana", uriSlot.captured)
        verify {
            constructedWith<Intent>(EqMatcher(Intent.ACTION_SENDTO)).putExtra("sms_body", "Hola Ana")
        }
        verify { context.startActivity(capture(intentSlot)) }
    }

    @Test
    fun `sendSms devuelve error si startActivity lanza excepcion`() {
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getString(0) } returns "600123456"
        every { context.startActivity(any()) } throws RuntimeException("boom")

        val result = callAction.sendSms("Ana", "Hola Ana")

        assertEquals("Error: No se pudo enviar el SMS.", result)
    }

    // ===== findContactNumber =====

    @Test
    fun `findContactNumber consulta el content resolver con proyeccion y seleccion correctas`() {
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getString(0) } returns "600123456"

        val number = callAction.findContactNumber("Ana")

        assertEquals("600123456", number)
        // B3: CONTENT_URI con any() — límite JVM: sin Robolectric no es comparable por identity
        verify {
            contentResolver.query(
                any(),
                match { it.contentEquals(arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)) },
                eq("${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"),
                match { it.contentEquals(arrayOf("%Ana%")) },
                any()
            )
        }
    }

    @Test
    fun `findContactNumber con cursor vacio devuelve null`() {
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor
        every { cursor.moveToFirst() } returns false

        assertNull(callAction.findContactNumber("Ana"))
    }

    @Test
    fun `findContactNumber con query nulo devuelve null`() {
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns null

        assertNull(callAction.findContactNumber("Ana"))
    }

    @Test
    fun `findContactNumber cierra el cursor despues de leerlo`() {
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getString(0) } returns "600123456"

        callAction.findContactNumber("Ana")

        verify { cursor.close() }
    }
}
