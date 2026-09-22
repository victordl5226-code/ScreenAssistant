package com.screenassistant.service.system.action

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContactsActionTest {

    private lateinit var context: Context
    private lateinit var contentResolver: android.content.ContentResolver
    private lateinit var cursor: Cursor
    private lateinit var action: ContactsAction

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        contentResolver = mockk<android.content.ContentResolver>(relaxed = true)
        cursor = mockk<Cursor>(relaxed = true)
        every { context.contentResolver } returns contentResolver
        every { ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) } returns PackageManager.PERMISSION_GRANTED
        action = ContactsAction(context)
    }

    @Test
    fun `sin permiso devuelve error`() {
        every { ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) } returns PackageManager.PERMISSION_DENIED
        val result = action.listContacts()
        assertEquals("Error: No tengo permiso para leer tus contactos. Concede el permiso en ajustes.", result)
    }

    @Test
    fun `sin contactos devuelve mensaje vacio`() {
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns null
        val result = action.listContacts()
        assertEquals("No tienes contactos guardados.", result)
    }

    @Test
    fun `con contactos devuelve lista limitada`() {
        val moveNextValues = listOf(true, true, true, true, true, false)
        var moveNextIndex = 0
        every { cursor.moveToNext() } answers { moveNextValues[moveNextIndex++] }

        val nameValues = listOf("Ana", "Carlos", "Beatriz", "David", "Elena")
        var nameIndex = 0
        every { cursor.getString(0) } answers { nameValues[nameIndex++] }

        every { cursor.close() } returns Unit
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor

        val result = action.listContacts()
        assertTrue(result.contains("Tienes 5 contactos"))
        assertTrue(result.contains("Ana"))
        assertTrue(result.contains("Elena"))
    }

    @Test
    fun `muchos contactos muestra 20 y cuenta resto`() {
        val moveNextValues = List(25) { true } + false
        var moveNextIndex = 0
        every { cursor.moveToNext() } answers { moveNextValues[moveNextIndex++] }

        val nameValues = List(25) { "Contacto $it" }
        var nameIndex = 0
        every { cursor.getString(0) } answers { nameValues[nameIndex++] }

        every { cursor.close() } returns Unit
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor

        val result = action.listContacts()
        assertTrue(result.contains("Tienes 25 contactos"))
        assertTrue(result.contains("y 5 más"))
    }

    @Test
    fun `cursor vacio devuelve sin contactos`() {
        every { cursor.moveToNext() } returns false
        every { cursor.close() } returns Unit
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor

        val result = action.listContacts()
        assertEquals("No tienes contactos guardados.", result)
    }

    @Test
    fun `contactos con nombres nulos se filtran`() {
        val moveNextValues = listOf(true, true, false)
        var moveNextIndex = 0
        every { cursor.moveToNext() } answers { moveNextValues[moveNextIndex++] }

        val nameValues = listOf("Ana", null)
        var nameIndex = 0
        every { cursor.getString(0) } answers { nameValues[nameIndex++] }

        every { cursor.close() } returns Unit
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor

        val result = action.listContacts()
        assertTrue(result.contains("Tienes 1 contactos"))
        assertTrue(result.contains("Ana"))
    }
}