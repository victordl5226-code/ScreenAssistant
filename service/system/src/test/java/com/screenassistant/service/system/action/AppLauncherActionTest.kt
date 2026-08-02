package com.screenassistant.service.system.action

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests de AppLauncherAction.
 * PackageManager es clase abstracta -> mockk la mockea.
 * ApplicationInfo es un POJO del mockable android.jar: se instancia real
 * (constructor y campos públicos funcionan en JVM).
 * El reloj es inyectable (var mutable) para ejercitar el TTL.
 */
class AppLauncherActionTest {

    private lateinit var context: Context
    private lateinit var pm: PackageManager
    private lateinit var action: AppLauncherAction

    private var currentTime = 1_000L

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        pm = mockk(relaxed = true)
        every { context.packageManager } returns pm
        currentTime = 1_000L
        action = AppLauncherAction(context, now = { currentTime })
    }

    /**
     * Intent "lanzable" con el flag registrado. NO se usa Intent() real: en JVM el
     * mockable android.jar no implementa setFlags ("Method setFlags not mocked"),
     * así que mockeamos el setter y reflejamos su valor en el getter (mismo
     * comportamiento que un Intent real en dispositivo).
     */
    private fun launchableIntent(): Intent {
        val intent = mockk<Intent>()
        var storedFlags = 0
        // setFlags es fluent (devuelve Intent) y el mockable jar de JVM no lo
        // implementa: se mockea y se refleja el valor en el getter.
        every { intent.setFlags(any()) } answers { storedFlags = firstArg(); intent }
        every { intent.flags } answers { storedFlags }
        return intent
    }

    // 1. Package directo: intent resuelto, startActivity con FLAG_ACTIVITY_NEW_TASK (QA 2)
    @Test
    fun `package directo abre con flag NEW_TASK`() {
        val intent = launchableIntent()
        every { pm.getLaunchIntentForPackage("com.whatsapp") } returns intent

        val result = action.launchApp("com.whatsapp")

        assertEquals("Éxito: Abriendo com.whatsapp.", result)
        val intentSlot = slot<Intent>()
        verify { context.startActivity(capture(intentSlot)) }
        assertTrue(intentSlot.captured.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    // 2. Package directo NO dispara escaneo del índice (QA 3: corto-circuito)
    @Test
    fun `package directo no dispara escaneo de aplicaciones`() {
        every { pm.getLaunchIntentForPackage("com.whatsapp") } returns launchableIntent()

        action.launchApp("com.whatsapp")

        verify(exactly = 0) { pm.getInstalledApplications(any<Int>()) }
    }

    // 3. Nombre amigable por label: match por índice + apertura con el label ganador
    @Test
    fun `nombre amigable abre la app por label`() {
        val app = ApplicationInfo().apply { packageName = "com.whatsapp" }
        every { pm.getLaunchIntentForPackage("whatsapp") } returns null
        every { pm.getInstalledApplications(0) } returns listOf(app)
        every { pm.getApplicationLabel(app) } returns "WhatsApp"
        every { pm.getLaunchIntentForPackage("com.whatsapp") } returns launchableIntent()

        val result = action.launchApp("whatsapp")

        assertEquals("Éxito: Abriendo WhatsApp.", result)
        verify { context.startActivity(any()) }
    }

    // 4. Query vacía/en blanco: corto-circuito ANTES de tocar PackageManager (QA 3)
    @Test
    fun `query en blanco no toca el PackageManager`() {
        val result = action.launchApp("   ")

        assertEquals("Error: No me dijiste qué aplicación abrir.", result)
        verify(exactly = 0) { pm.getLaunchIntentForPackage(any()) }
        verify(exactly = 0) { pm.getInstalledApplications(any<Int>()) }
    }

    // 5. Sin coincidencia: ni package directo ni label
    @Test
    fun `sin coincidencia devuelve mensaje de no encontrado`() {
        every { pm.getLaunchIntentForPackage("xyz") } returns null
        every { pm.getInstalledApplications(0) } returns emptyList()

        val result = action.launchApp("xyz")

        assertEquals("Error: No encontré ninguna aplicación llamada 'xyz'.", result)
    }

    // 6. startActivity lanza ActivityNotFoundException -> error genérico
    @Test
    fun `startActivity lanzando excepcion devuelve error generico`() {
        every { pm.getLaunchIntentForPackage("com.whatsapp") } returns launchableIntent()
        every { context.startActivity(any()) } throws android.content.ActivityNotFoundException()

        val result = action.launchApp("com.whatsapp")

        assertEquals("Error: No se pudo abrir la aplicación.", result)
    }

    // 7. matchApp puro: exacta gana sobre contains
    @Test
    fun `matchApp prioriza la coincidencia exacta sobre contains`() {
        val candidates = listOf(
            "com.spotify.lite" to "Spotify Lite",
            "com.spotify.music" to "Spotify"
        )

        val result = action.matchApp("spotify", candidates)

        assertNotNull(result)
        assertEquals("com.spotify.music" to "Spotify", result)
    }

    // 8. matchApp puro: case-insensitive
    @Test
    fun `matchApp es case-insensitive`() {
        val candidates = listOf(
            "com.spotify.lite" to "Spotify Lite",
            "com.spotify.music" to "Spotify"
        )

        val result = action.matchApp("SPOTIFY", candidates)

        assertNotNull(result)
        assertEquals("com.spotify.music" to "Spotify", result)
    }

    // 9. matchApp puro: labels null se ignoran sin crash y no empatan
    @Test
    fun `matchApp ignora labels null sin crash`() {
        val result = action.matchApp("xyz", listOf("com.foo" to null, "com.bar" to null))

        assertNull(result)
    }

    // 10. matchApp puro tie-break: labels duplicados -> gana el menor packageName (QA 4)
    @Test
    fun `matchApp resuelve empate de labels por packageName`() {
        val candidates = listOf(
            "b" to "Música",
            "a" to "Música"
        )

        val result = action.matchApp("música", candidates)

        assertNotNull(result)
        assertEquals("a" to "Música", result)
    }

    // 11. Memoización dentro del TTL: 2 llamadas -> 1 solo escaneo
    @Test
    fun `indice se memoiza dentro del TTL`() {
        val app = ApplicationInfo().apply { packageName = "com.whatsapp" }
        every { pm.getLaunchIntentForPackage("whatsapp") } returns null
        every { pm.getInstalledApplications(any<Int>()) } returns listOf(app)
        every { pm.getApplicationLabel(app) } returns "WhatsApp"
        every { pm.getLaunchIntentForPackage("com.whatsapp") } returns launchableIntent()

        action.launchApp("whatsapp")
        action.launchApp("whatsapp")

        verify(exactly = 1) { pm.getInstalledApplications(any<Int>()) }
    }

    // 12. TTL expirado (QA 1: reloj inyectable): 2 llamadas -> 2 escaneos
    @Test
    fun `indice se rescanea al expirar el TTL`() {
        val app = ApplicationInfo().apply { packageName = "com.whatsapp" }
        every { pm.getLaunchIntentForPackage("whatsapp") } returns null
        every { pm.getInstalledApplications(any<Int>()) } returns listOf(app)
        every { pm.getApplicationLabel(app) } returns "WhatsApp"
        every { pm.getLaunchIntentForPackage("com.whatsapp") } returns launchableIntent()

        action.launchApp("whatsapp")
        verify(exactly = 1) { pm.getInstalledApplications(any<Int>()) }

        currentTime = 1_000L + AppLauncherAction.INDEX_TTL_MS + 1L
        action.launchApp("whatsapp")

        verify(exactly = 2) { pm.getInstalledApplications(any<Int>()) }
    }

    // 13. Flujo completo: candidato con label null se ignora, el válido gana
    @Test
    fun `launchApp ignora candidato con label null y abre el valido`() {
        val hiddenApp = ApplicationInfo().apply { packageName = "com.something.hidden" }
        val whatsappApp = ApplicationInfo().apply { packageName = "com.whatsapp" }
        every { pm.getLaunchIntentForPackage("whatsapp") } returns null
        every { pm.getInstalledApplications(any<Int>()) } returns listOf(hiddenApp, whatsappApp)
        // La firma del stub de Android declara CharSequence no-null, pero en runtime
        // puede devolver null: TestNulls lo entrega desde Java sin chequeo de Kotlin.
        every { pm.getApplicationLabel(hiddenApp) } answers { TestNulls.nullCharSequence() }
        every { pm.getApplicationLabel(whatsappApp) } returns "WhatsApp"
        every { pm.getLaunchIntentForPackage("com.whatsapp") } returns launchableIntent()

        val result = action.launchApp("whatsapp")

        assertEquals("Éxito: Abriendo WhatsApp.", result)
        verify { context.startActivity(any()) }
    }
}
