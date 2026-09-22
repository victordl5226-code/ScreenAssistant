package com.screenassistant.service.system.action

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchActionTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var action: SearchAction

    @Before
    fun setup() {
        contentResolver = mockk(relaxed = true)
        context = mockk(relaxed = true)
        every { context.contentResolver } returns contentResolver
        action = SearchAction(context)
    }

    @Test
    fun `searchFile encontrado retorna exito con nombre y ruta`() {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns true
        every { cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA) } returns 0
        every { cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME) } returns 1
        every { cursor.getString(0) } returns "/sdcard/photo.jpg"
        every { cursor.getString(1) } returns "photo.jpg"
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor

        val result = action.searchFile("photo")
        assertTrue(result.contains("Éxito"))
        assertTrue(result.contains("photo.jpg"))
    }

    @Test
    fun `searchFile no encontrado retorna error`() {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns false
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor

        val result = action.searchFile("noexiste")
        assertTrue(result.contains("Error"))
    }

    @Test
    fun `searchFile cursor null retorna error`() {
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns null

        val result = action.searchFile("test")
        assertTrue(result.contains("Error"))
    }

    @Test
    fun `searchFile exception retorna error sin exponer detalle`() {
        every { contentResolver.query(any(), any(), any(), any(), any()) } throws RuntimeException("crash")

        val result = action.searchFile("test")
        assertTrue(result.contains("Error"))
        assertTrue(!result.contains("crash"))
    }
}
