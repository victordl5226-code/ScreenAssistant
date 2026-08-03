package com.screenassistant.service.system.bridge

import com.screenassistant.core.data.util.PuenteConfig
import com.screenassistant.core.data.util.PuenteConfigStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.net.URLEncoder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * AutoRemoteUrlCallback (P3, ADR-015 v1.2 — QA #4): orquestador PURA testeable
 * con UrlSender mock. M3: coroutine IO hermana inyectada con StandardTestDispatcher
 * compartiendo el testScheduler → `advanceUntilIdle()`/`runCurrent()` ejecutan
 * el `launch` fire-and-forget.
 *
 * Decisión de canal F3 v1.2 (enmienda v1.2a H2): SOLO por config —
 * `enviarRespuestaURL == true` Y key no-blank. SIN origen (muerto con el veto
 * H1). Los 6 tests conservados se editan fijando `enviarRespuestaURL = true`
 * explícito (los de no-envío por key blank para ejercitar la rama real de key,
 * los de envío para no fallar por el early return del flag). Los 4 que
 * dependían del origen (Tasker/.beta/otra app/sin paquete) se ELIMINAN. Suite = 9.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoRemoteUrlCallbackTest {

    private lateinit var configStore: PuenteConfigStore
    private lateinit var urlSender: UrlSender

    /** Config del store stub (se cambia por test). */
    private var config: PuenteConfig = PuenteConfig()

    private val respuestaJson =
        """{"version":1,"id":"a-1","estado":"ok","resultado":"Éxito: ok","error":null,"mensaje":null}"""

    @Before
    fun setup() {
        config = PuenteConfig()
        configStore = mockk()
        every { configStore.cargar() } answers { config }
        urlSender = mockk()
        coEvery { urlSender.enviar(any()) } returns true
    }

    private fun TestScope.nuevoCallback(): AutoRemoteUrlCallback =
        AutoRemoteUrlCallback(configStore, urlSender, StandardTestDispatcher(testScheduler))

    // ===== Conservados EDITADOS con flag=true explícito (v1.2a H2) =====

    @Test
    fun `flag on sin key no envia nada`() = runTest {
        config = PuenteConfig(enviarRespuestaURL = true, autoRemoteKey = "")
        val callback = nuevoCallback()

        callback.responderSiAplica(respuestaJson)
        advanceUntilIdle()

        coVerify(exactly = 0) { urlSender.enviar(any()) }
    }

    @Test
    fun `flag on con key blank con espacios no envia nada`() = runTest {
        config = PuenteConfig(enviarRespuestaURL = true, autoRemoteKey = "   ")
        val callback = nuevoCallback()

        callback.responderSiAplica(respuestaJson)
        advanceUntilIdle()

        coVerify(exactly = 0) { urlSender.enviar(any()) }
    }

    @Test
    fun `flag on con key envia la url con key y mensaje encodeados`() = runTest {
        config = PuenteConfig(enviarRespuestaURL = true, autoRemoteKey = "Mi Key")
        val callback = nuevoCallback()
        val urlSlot = slot<String>()

        callback.responderSiAplica(respuestaJson)
        advanceUntilIdle()

        coVerify(exactly = 1) { urlSender.enviar(capture(urlSlot)) }
        val url = urlSlot.captured
        assertTrue(url.startsWith("${AutoRemoteUrlBuilder.ENDPOINT}?key="))
        assertTrue(url.contains("key=" + URLEncoder.encode("Mi Key", "UTF-8")))
        assertTrue(url.contains("message=" + URLEncoder.encode(respuestaJson, "UTF-8")))
    }

    @Test
    fun `fracaso de red devuelve false sin reintento ni propagacion`() = runTest {
        config = PuenteConfig(enviarRespuestaURL = true, autoRemoteKey = "k")
        coEvery { urlSender.enviar(any()) } returns false
        val callback = nuevoCallback()

        callback.responderSiAplica(respuestaJson)
        advanceUntilIdle()

        // Best-effort (ADR-015 §4.3): se intenta UNA vez, sin reintento, sin lanzar.
        coVerify(exactly = 1) { urlSender.enviar(any()) }
    }

    @Test
    fun `sender que lanza no propaga la excepcion`() = runTest {
        // Fail-soft defensivo: aunque el sender viole su contrato ("no lanza"),
        // la corrutina hermana muere silenciosa — la URL nunca rompe el flujo local.
        config = PuenteConfig(enviarRespuestaURL = true, autoRemoteKey = "k")
        coEvery { urlSender.enviar(any()) } throws RuntimeException("red caida")
        val callback = nuevoCallback()

        callback.responderSiAplica(respuestaJson)
        advanceUntilIdle()

        coVerify(exactly = 1) { urlSender.enviar(any()) }
    }

    @Test
    fun `sin respuesta se envia la url con mensaje vacio`() = runTest {
        // Cobertura del `?: return` del builder dentro del orquestador: imposible en
        // producción (la key ya pasó isBlank arriba), defensivo directo.
        config = PuenteConfig(enviarRespuestaURL = true, autoRemoteKey = "k")
        val callback = nuevoCallback()
        val urlSlot = slot<String>()

        callback.responderSiAplica("")
        advanceUntilIdle()

        // capture es método del scope de coVerify (sin import top-level — MockK 1.13).
        coVerify(exactly = 1) { urlSender.enviar(capture(urlSlot)) }
        assertTrue(urlSlot.captured.endsWith("message="))
    }

    // ===== NUEVOS v1.2: semántica del flag (ADR-015 §4.2) =====

    @Test
    fun `flag off con key configurada no envia nada`() = runTest {
        // Comprobación NEGATIVA del flag: con key presente y flag OFF (default),
        // el canal URL está INERTE (cero llamadas URL sorpresa).
        config = PuenteConfig(autoRemoteKey = "k")
        val callback = nuevoCallback()

        callback.responderSiAplica(respuestaJson)
        advanceUntilIdle()

        coVerify(exactly = 0) { urlSender.enviar(any()) }
    }

    @Test
    fun `flag on con key blank no envia nada`() = runTest {
        config = PuenteConfig(enviarRespuestaURL = true, autoRemoteKey = "")
        val callback = nuevoCallback()

        callback.responderSiAplica(respuestaJson)
        advanceUntilIdle()

        coVerify(exactly = 0) { urlSender.enviar(any()) }
    }

    @Test
    fun `flag on con key con espacios envia la url con key trimeada`() = runTest {
        // AutoRemoteUrlBuilder.construir trimea la key (línea 18, intacta).
        config = PuenteConfig(enviarRespuestaURL = true, autoRemoteKey = "  k  ")
        val callback = nuevoCallback()
        val urlSlot = slot<String>()

        callback.responderSiAplica(respuestaJson)
        advanceUntilIdle()

        coVerify(exactly = 1) { urlSender.enviar(capture(urlSlot)) }
        assertTrue(urlSlot.captured.contains("key=" + URLEncoder.encode("k", "UTF-8")))
    }
}
