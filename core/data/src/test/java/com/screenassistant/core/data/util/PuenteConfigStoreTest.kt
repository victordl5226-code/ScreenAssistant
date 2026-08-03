package com.screenassistant.core.data.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PuenteConfigStore (P0, ADR-015 v1.2): cáscara fina con MockK de
 * SharedPreferences/Editor (patrón "Notas de proceso": la crypto real se valida
 * en dispositivo). Los tests JVM caen SIEMPRE al fallback:
 *
 * Supuesto declarado (M5): en el unit test JVM de core:data (SIN
 * isReturnDefaultValues) `MasterKey.Builder.build()` lanza "not mocked" → el lazy
 * de prefs cae al catch y marca isUsingFallback. Consecuencia: `Log.w` (android.jar
 * stub) también lanzaría "not mocked" dentro del catch → se mockea con
 * mockkStatic(Log::class) para poder leer el fallback (getSharedPreferences mock).
 *
 * v1.2: la config gana tokenCompartido y enviarRespuestaURL; la normalización de
 * paquetes (y sus 2 tests) se ELIMINA con la allowlist muerta (ADR-015 §2.6).
 */
class PuenteConfigStoreTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var store: PuenteConfigStore

    /** Backing store real del mock: lo que guardar() escribe lo ve cargar(). */
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
        every { editor.apply() } returns Unit
        every { prefs.getString(any(), any()) } answers { stored[firstArg<String>()] as? String }
        every { prefs.getBoolean(any(), any()) } answers {
            (stored[firstArg<String>()] as? Boolean) ?: secondArg()
        }

        // Log.w lanzaría "not mocked" en JVM sin isReturnDefaultValues (core:data
        // no lo activa) → se mockea estáticamente para que el catch del lazy y el
        // catch defensivo de cargar()/guardar() no propaguen.
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0

        store = PuenteConfigStore(context)
    }

    // ===== H1: materialización del default en el STORE =====

    @Test
    fun `cargar con prefs vacias devuelve defaults con packageRespuesta materializado`() {
        val config = store.cargar()

        // H1 (corrección): clave ausente → PACKAGE_RESPUESTA_DEFAULT, NUNCA null.
        assertEquals(PuenteConfig.PACKAGE_RESPUESTA_DEFAULT, config.packageRespuesta)
        assertEquals("", config.autoRemoteKey)
        // v1.2 (F1): token ausente → "" (canal abierto).
        assertEquals("", config.tokenCompartido)
        // v1.2 (F3): flag ausente → false (OFF por defecto, cero llamadas URL sorpresa).
        assertFalse(config.enviarRespuestaURL)
    }

    @Test
    fun `packageRespuesta blank guardado se materializa al default al cargar`() {
        store.guardar(PuenteConfig(packageRespuesta = "   "))

        // H1: el blank (crudo de guardado) → default al cargar.
        assertEquals(PuenteConfig.PACKAGE_RESPUESTA_DEFAULT, store.cargar().packageRespuesta)
    }

    // ===== Persistencia =====

    @Test
    fun `guardar escribe las 4 claves y aplica`() {
        store.guardar(
            PuenteConfig(
                packageRespuesta = null,
                autoRemoteKey = "mi-key",
                tokenCompartido = "misecreto",
                enviarRespuestaURL = true,
            )
        )

        verify(exactly = 1) { editor.putString("packageRespuesta", null) }
        verify(exactly = 1) { editor.putString("autoRemoteKey", "mi-key") }
        verify(exactly = 1) { editor.putString("tokenCompartido", "misecreto") }
        verify(exactly = 1) { editor.putBoolean("enviarRespuestaURL", true) }
        verify(exactly = 1) { editor.apply() }
    }

    @Test
    fun `roundtrip guardar y cargar devuelve la config completa`() {
        val config = PuenteConfig(
            packageRespuesta = "com.otro.paquete",
            autoRemoteKey = "key-123",
            tokenCompartido = "misecreto",
            enviarRespuestaURL = true,
        )

        store.guardar(config)
        val cargada = store.cargar()

        assertEquals(config, cargada)
    }

    // ===== v1.2: roundtrips de las claves nuevas (F1 token / F3 flag) =====

    @Test
    fun `roundtrip del tokenCompartido con caracteres especiales`() {
        store.guardar(PuenteConfig(tokenCompartido = "  tóken ¿extraño?  áéí "))

        // El store persiste TAL CUAL (el trim lo hace la UI/VM — diseño §5.1).
        assertEquals("  tóken ¿extraño?  áéí ", store.cargar().tokenCompartido)
    }

    @Test
    fun `roundtrip de enviarRespuestaURL true y false`() {
        store.guardar(PuenteConfig(enviarRespuestaURL = true))
        assertTrue(store.cargar().enviarRespuestaURL)

        store.guardar(PuenteConfig(enviarRespuestaURL = false))
        assertFalse(store.cargar().enviarRespuestaURL)
    }

    // ===== Fallback (supuesto M5) =====

    @Test
    fun `la crypto no disponible en el unit test JVM marca isUsingFallback`() {
        // Disparar el lazy (cargar/guardar acceden a prefs) — en JVM la crypto
        // lanza y el catch del lazy marca el flag ANTES del Log/fallback (M5).
        store.cargar()

        assertTrue(store.isUsingFallback)
    }

    @Test
    fun `el fallback funciona end-to-end con prefs normales mockeadas`() {
        // Con el flag activo, guardar/cargar siguen funcionando vía las prefs del fallback.
        store.guardar(PuenteConfig(autoRemoteKey = "k-fallback", tokenCompartido = "t-fallback"))
        assertEquals("k-fallback", store.cargar().autoRemoteKey)
        assertEquals("t-fallback", store.cargar().tokenCompartido)
        assertTrue(store.isUsingFallback)
        // H1: aunque el crudo esté ausente, cargar() materializa el default (nunca null).
        assertEquals(PuenteConfig.PACKAGE_RESPUESTA_DEFAULT, store.cargar().packageRespuesta)
    }
}
