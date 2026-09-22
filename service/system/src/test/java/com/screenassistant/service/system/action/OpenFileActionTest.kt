package com.screenassistant.service.system.action

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenFileActionTest {

    private lateinit var context: Context
    private lateinit var contentResolver: android.content.ContentResolver
    private lateinit var cursor: Cursor
    private lateinit var action: OpenFileAction

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        contentResolver = mockk<android.content.ContentResolver>(relaxed = true)
        cursor = mockk<Cursor>(relaxed = true)
        every { context.contentResolver } returns contentResolver
        action = OpenFileAction(context)
    }

    @Test
    fun `query vacio devuelve error`() {
        val result = action.openFile("")
        assertEquals("Error: ¿Qué archivo quieres abrir?", result)
    }

    @Test
    fun `query solo espacios devuelve error`() {
        val result = action.openFile("   ")
        assertEquals("Error: ¿Qué archivo quieres abrir?", result)
    }

    @Test
    fun `archivo encontrado abre intent`() {
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getLong(0) } returns 123L
        every { cursor.getString(1) } returns "foto.jpg"
        every { cursor.getString(2) } returns "image/jpeg"
        every { cursor.close() } returns Unit

        val result = action.openFile("foto")
        assertEquals("Abriendo archivo: foto.jpg", result)
    }

    @Test
    fun `archivo no encontrado devuelve error`() {
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor
        every { cursor.moveToFirst() } returns false
        every { cursor.close() } returns Unit

        val result = action.openFile("inexistente")
        assertEquals("Error: No encontré ningún archivo que coincida con 'inexistente'.", result)
    }

    @Test
    fun `cursor nulo devuelve error`() {
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns null

        val result = action.openFile("foto")
        assertEquals("Error: No encontré ningún archivo que coincida con 'foto'.", result)
    }

    @Test
    fun `mime type nulo usa default`() {
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getLong(0) } returns 456L
        every { cursor.getString(1) } returns "documento.pdf"
        every { cursor.getString(2) } returns null
        every { cursor.close() } returns Unit

        val result = action.openFile("documento")
        assertEquals("Abriendo archivo: documento.pdf", result)
    }
}