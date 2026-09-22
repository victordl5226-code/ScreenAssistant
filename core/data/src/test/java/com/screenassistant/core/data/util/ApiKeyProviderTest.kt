package com.screenassistant.core.data.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * ApiKeyProvider (D4, Lote 9 — hoy sin cobertura): cáscara fina sobre
 * EncryptedPrefsStore. Patrón M5 (precedente PuenteConfigStoreTest): MockK de
 * Context/SharedPreferences/Editor + mockkStatic(Log); en JVM la crypto lanza
 * "not mocked" → fallback. Incluye el contrato B4 (Lote 8): sembrar UNA sola
 * vez con flag api_key_seeded y NO re-sembrar tras clear.
 */
class ApiKeyProviderTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var provider: ApiKeyProvider

    /** Backing store real del mock: lo que se escribe lo lee getString/getBoolean. */
    private val stored = mutableMapOf<String, Any?>()

    @Before
    fun setup() {
        stored.clear()
        context = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        // Fallback (M5): el lazy llama getSharedPreferences("..._fallback", MODE_PRIVATE).
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

        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0

        provider = ApiKeyProvider(context)
    }

    // 1. Roundtrip get/store/clear.
    @Test
    fun `roundtrip storeApiKey getApiKey clearApiKey`() {
        assertEquals("", provider.getApiKey())

        provider.storeApiKey("AIza-123")
        assertEquals("AIza-123", provider.getApiKey())

        provider.clearApiKey()
        assertEquals("", provider.getApiKey())
    }

    // 2. B4: sembrar UNA sola vez con flag (dos llamadas → una sola escritura).
    @Test
    fun `sembrarDesdeBuildConfig siembra una sola vez con el flag`() {
        provider.sembrarDesdeBuildConfig("AIza-build")

        assertEquals("AIza-build", provider.getApiKey())
        verify(exactly = 1) { editor.putBoolean("api_key_seeded", true) }
        verify(exactly = 1) { editor.putString("openrouter_api_key", "AIza-build") }

        // Segunda siembra: el flag ya está → cero escrituras nuevas.
        provider.sembrarDesdeBuildConfig("OTRA-KEY")
        verify(exactly = 1) { editor.putString("openrouter_api_key", any()) }
    }

    // 3. B4: tras clear NO se re-siembra (el flag persiste; el control es del usuario).
    @Test
    fun `tras clear no se vuelve a sembrar aunque llegue otra siembra`() {
        provider.sembrarDesdeBuildConfig("AIza-build")
        provider.clearApiKey()

        assertEquals("", provider.getApiKey())

        // El flag sigue → la siembra posterior es no-op (no re-escribe la key).
        provider.sembrarDesdeBuildConfig("AIza-build")
        assertEquals("", provider.getApiKey())
        verify(exactly = 1) { editor.putString("openrouter_api_key", any()) }
    }
}
