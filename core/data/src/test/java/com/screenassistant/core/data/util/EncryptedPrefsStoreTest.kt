package com.screenassistant.core.data.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * EncryptedPrefsStore (D4, Lote 9): base cifrada+fallback extraída del patrón
 * duplicado ApiKeyProvider/PuenteConfigStore. Patrón M5 (precedente
 * PuenteConfigStoreTest): MockK de Context/SharedPreferences/Editor + mockkStatic
 * de Log; en el unit test JVM la crypto lanza "not mocked" → el catch de create()
 * cae al fallback y marca isUsingFallback.
 */
class EncryptedPrefsStoreTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    /** Backing store real del mock: lo que putString guarda lo ve getString. */
    private val stored = mutableMapOf<String, Any?>()

    @Before
    fun setup() {
        stored.clear()
        context = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        // Fallback (M5): create() llama getSharedPreferences("..._fallback", MODE_PRIVATE).
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            stored[firstArg<String>()] = secondArg()
            editor
        }
        every { editor.putBoolean(any(), any()) } answers {
            stored[firstArg<String>()] = secondArg()
            editor
        }
        every { editor.remove(any()) } answers {
            stored.remove(firstArg<String>())
            editor
        }
        every { editor.apply() } returns Unit
        every { prefs.getString(any(), any()) } answers { stored[firstArg<String>()] as? String }
        every { prefs.getBoolean(any(), any()) } answers {
            (stored[firstArg<String>()] as? Boolean) ?: secondArg()
        }

        // Log.w lanzaría "not mocked" en JVM sin isReturnDefaultValues (core:data
        // no lo activa) → se mockea estáticamente para el catch de create().
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
    }

    // 1. La crypto no disponible en el unit test JVM → fallback + flag (M5).
    @Test
    fun `create cae al fallback en JVM y marca isUsingFallback`() {
        val store = EncryptedPrefsStore.create(context, "secure_api_prefs", "secure_api_prefs_fallback", "tag")

        assertTrue(store.isUsingFallback)
        verify(exactly = 1) { context.getSharedPreferences("secure_api_prefs_fallback", Context.MODE_PRIVATE) }
    }

    // 2. Roundtrip putString/getString + putBoolean/getBoolean vía fallback.
    @Test
    fun `roundtrip putString getString putBoolean getBoolean funciona sobre el fallback`() {
        val store = EncryptedPrefsStore.create(context, "secure_api_prefs", "secure_api_prefs_fallback", "tag")

        store.putString("clave_texto", "valor-secreto")
        store.putBoolean("clave_flag", true)

        assertEquals("valor-secreto", store.getString("clave_texto"))
        assertEquals(true, store.getBoolean("clave_flag", false))
        // Clave ausente: null / default.
        assertNull(store.getString("clave_ausente"))
        assertEquals(false, store.getBoolean("clave_ausente", false))
    }

    // 3. remove borra la clave (semántica SharedPreferences).
    @Test
    fun `remove borra la clave persistida`() {
        val store = EncryptedPrefsStore.create(context, "secure_api_prefs", "secure_api_prefs_fallback", "tag")

        store.putString("clave", "valor")
        assertEquals("valor", store.getString("clave"))

        store.remove("clave")

        assertNull(store.getString("clave"))
    }
}
