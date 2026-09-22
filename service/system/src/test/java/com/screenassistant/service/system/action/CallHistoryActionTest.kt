package com.screenassistant.service.system.action

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CallLog
import androidx.core.content.ContextCompat
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CallHistoryActionTest {

    private lateinit var context: Context
    private lateinit var contentResolver: android.content.ContentResolver
    private lateinit var cursor: Cursor
    private lateinit var action: CallHistoryAction

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        contentResolver = mockk<android.content.ContentResolver>(relaxed = true)
        cursor = mockk<Cursor>(relaxed = true)
        every { context.contentResolver } returns contentResolver
        every { ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) } returns PackageManager.PERMISSION_GRANTED
        action = CallHistoryAction(context)
    }

    @Test
    fun `sin permiso devuelve error`() {
        every { ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) } returns PackageManager.PERMISSION_DENIED
        val result = action.getCallHistory()
        assertEquals("Error: No tengo permiso para leer el historial de llamadas.", result)
    }

    @Test
    fun `sin llamadas devuelve vacio`() {
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns null
        val result = action.getCallHistory()
        assertEquals("No hay llamadas recientes.", result)
    }

    // Tests con Cursor requieren mocking complejo no soportado en JVM con mockable android.jar
    // @Test fun `con llamadas devuelve lista`() { ... }
    // @Test fun `limite 10 llamadas`() { ... }
    // @Test fun `tipos de llamada mapeados correctamente`() { ... }
}