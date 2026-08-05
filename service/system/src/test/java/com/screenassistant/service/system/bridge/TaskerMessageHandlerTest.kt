package com.screenassistant.service.system.bridge

import com.screenassistant.core.data.util.PuenteConfig
import com.screenassistant.core.data.util.PuenteConfigStore
import com.screenassistant.core.domain.action.SystemAction
import com.screenassistant.core.domain.bridge.CommandBridge
import com.screenassistant.core.domain.bridge.SystemCommandJsonCodec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * TaskerMessageHandlerImpl (P1, ADR-014/T6; F1, ADR-015 v1.2): SIEMPRE JSON de 6
 * claves (timeout incluido con tiempo VIRTUAL — runTest + delay > TIMEOUT_MS, sin
 * Dispatchers en el handler), try/catch defensivo → fallo_ejecucion, id eco
 * best-effort (H6) y F1 v1.2 (token compartido del canal): los 14 tests base se
 * revierten a `handle(extra, token = null)` (se retira el 2º arg OrigenTasker —
 * muerto con el veto B1/H1, ADR-015 §2.6), los 5 de allowlist se eliminan y se
 * añaden 6 de token + 2 de trim/case (enmienda v1.2a H4). Suite = 22.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskerMessageHandlerTest {

    private lateinit var bridge: CommandBridge
    private lateinit var configStore: PuenteConfigStore
    private lateinit var handler: TaskerMessageHandler

    private val json = Json { ignoreUnknownKeys = false }
    private val wire = """{"version":1,"id":"h-1","accion":"poner_volumen","valor":"subir"}"""
    private val respuestaOk =
        """{"version":1,"id":"h-1","estado":"ok","resultado":"Éxito: Volumen subido.","error":null,"mensaje":null}"""

    /** Config del store stub: se cambia por test. Default = token blank (canal abierto). */
    private var config: PuenteConfig = PuenteConfig()

    @Before
    fun setup() {
        bridge = mockk()
        config = PuenteConfig()
        configStore = mockk()
        every { configStore.cargar() } answers { config }
        handler = TaskerMessageHandlerImpl(
            bridge,
            SystemCommandJsonCodec(),
            configStore,
        )
    }

    /** Asserter compartido: el esquema SIEMPRE es de 6 claves (H4), en orden fijo. */
    private fun assertSeisClaves(respuesta: String) {
        val obj = json.parseToJsonElement(respuesta).jsonObject
        assertEquals(
            listOf("version", "id", "estado", "resultado", "error", "mensaje"),
            obj.keys.toList(),
        )
    }

    private fun estadoDe(respuesta: String): String? =
        json.parseToJsonElement(respuesta).jsonObject["estado"]?.jsonPrimitive?.contentOrNull

    private fun errorDe(respuesta: String): String? =
        json.parseToJsonElement(respuesta).jsonObject["error"]?.jsonPrimitive?.contentOrNull

    private fun idDe(respuesta: String): String? =
        json.parseToJsonElement(respuesta).jsonObject["id"]?.jsonPrimitive?.contentOrNull

    private fun mensajeDe(respuesta: String): String? =
        json.parseToJsonElement(respuesta).jsonObject["mensaje"]?.jsonPrimitive?.contentOrNull

    // ===== Delegación al puente (14 base revertidos a handle(extra, token = null)) =====

    @Test
    fun `delega en el puente y devuelve su json literal con 6 claves`() = runTest {
        coEvery { bridge.handle(wire) } returns respuestaOk

        val respuesta = handler.handle(wire, null)

        assertEquals(respuestaOk, respuesta)
        coVerify(exactly = 1) { bridge.handle(wire) }
        assertSeisClaves(respuesta)
    }

    @Test
    fun `el error de decode del puente se propaga sin tocar`() = runTest {
        val errorJson =
            """{"version":1,"id":null,"estado":"error","resultado":null,"error":"json_invalido","mensaje":"Error: JSON invalido."}"""
        coEvery { bridge.handle(any()) } returns errorJson

        val respuesta = handler.handle(wire, null)

        assertEquals(errorJson, respuesta)
        assertEquals("json_invalido", errorDe(respuesta))
        assertSeisClaves(respuesta)
    }

    @Test
    fun `la excepcion del puente produce fallo_ejecucion con id eco`() = runTest {
        coEvery { bridge.handle(any()) } throws RuntimeException("boom")

        val respuesta = handler.handle(wire, null)

        assertEquals("error", estadoDe(respuesta))
        assertEquals("fallo_ejecucion", errorDe(respuesta))
        // Lote 11: mensaje saneado ADR-009 de fuente única (el wire se HABLA por TTS).
        assertEquals("Error: No pudo completarse la acción.", mensajeDe(respuesta))
        assertEquals("h-1", idDe(respuesta)) // eco best-effort del wire
        assertSeisClaves(respuesta)
    }

    @Test
    fun `la excepcion sin id en el wire devuelve id null en fallo_ejecucion`() = runTest {
        coEvery { bridge.handle(any()) } throws RuntimeException("boom")
        val wireSinId = """{"version":1,"accion":"abrir_app","aplicacion":"x"}"""

        val respuesta = handler.handle(wireSinId, null)

        assertNull(idDe(respuesta))
        assertEquals("fallo_ejecucion", errorDe(respuesta))
    }

    @Test
    fun `la excepcion con mensaje nulo produce mensaje generico`() = runTest {
        coEvery { bridge.handle(any()) } throws RuntimeException()

        val respuesta = handler.handle(wire, null)

        // Lote 11: null y no-null convergen al mismo texto saneado (el antiguo
        // "Error: Error desconocido" desaparece — el genérico cubre ambos casos).
        assertEquals("Error: No pudo completarse la acción.", mensajeDe(respuesta))
    }

    // ===== Timeout con tiempo virtual (D6, H6) =====

    @Test
    fun `el timeout produce error_timeout con 6 claves e id eco`() = runTest {
        coEvery { bridge.handle(any()) } coAnswers {
            delay(11_000) // > TIMEOUT_MS (10 s): el tiempo virtual dispara el timeout
            "tarde"
        }

        val respuesta = handler.handle(wire, null)

        assertEquals("error", estadoDe(respuesta))
        assertEquals("error_timeout", errorDe(respuesta))
        assertEquals("Error: Tiempo de espera agotado.", mensajeDe(respuesta))
        assertEquals("h-1", idDe(respuesta)) // H6: id eco best-effort del wire
        assertSeisClaves(respuesta)
    }

    @Test
    fun `el timeout con wire sin id devuelve id null`() = runTest {
        coEvery { bridge.handle(any()) } coAnswers {
            delay(11_000)
            "tarde"
        }
        val wireSinId = """{"version":1,"accion":"abrir_app","aplicacion":"x"}"""

        val respuesta = handler.handle(wireSinId, null)

        assertEquals("error_timeout", errorDe(respuesta))
        assertNull(idDe(respuesta))
        assertSeisClaves(respuesta)
    }

    @Test
    fun `el puente que tarda menos del timeout responde sin error_timeout`() = runTest {
        coEvery { bridge.handle(any()) } coAnswers {
            delay(1_000) // dentro de la ventana
            respuestaOk
        }

        val respuesta = handler.handle(wire, null)

        assertEquals("ok", estadoDe(respuesta))
        assertEquals(respuestaOk, respuesta)
    }

    // ===== F0 intacta: blank → json_invalido (vía puente real) =====

    @Test
    fun `blank produce json_invalido intacto`() = runTest {
        val puenteReal = SystemCommandBridgeImpl(mockk<SystemAction>(), SystemCommandJsonCodec())
        val handlerReal = TaskerMessageHandlerImpl(
            puenteReal,
            SystemCommandJsonCodec(),
            configStore,
        )

        val respuesta = handlerReal.handle("   ", null)

        assertEquals("error", estadoDe(respuesta))
        assertEquals("json_invalido", errorDe(respuesta))
        assertEquals("Error: JSON invalido.", mensajeDe(respuesta))
        assertSeisClaves(respuesta)
    }

    // ===== F1 v1.2: canal abierto con token blank (cero regresión, ADR-015 §2.3) =====

    @Test
    fun `config blank y token ausente ejecuta el puente`() = runTest {
        // Default PuenteConfig(): tokenCompartido "" → canal abierto (compat modo
        // actual, documentado "no protegido"). Cero regresión.
        coEvery { bridge.handle(wire) } returns respuestaOk

        val respuesta = handler.handle(wire, null)

        assertEquals("ok", estadoDe(respuesta))
        coVerify(exactly = 1) { bridge.handle(wire) }
    }

    @Test
    fun `config blank y token presente ejecuta el puente`() = runTest {
        // Fila de la tabla §2.3: config blank + extra con token → canal abierto.
        coEvery { bridge.handle(wire) } returns respuestaOk

        val respuesta = handler.handle(wire, "cualquier-token")

        assertEquals(respuestaOk, respuesta)
        coVerify(exactly = 1) { bridge.handle(wire) }
    }

    @Test
    fun `config blank y token presente no altera el json de respuesta`() = runTest {
        coEvery { bridge.handle(wire) } returns respuestaOk

        val sinToken = handler.handle(wire, null)
        val conToken = handler.handle(wire, "misecreto")

        assertEquals(sinToken, conToken)
    }

    @Test
    fun `config blank y token en blanco con espacios ejecuta el puente`() = runTest {
        coEvery { bridge.handle(wire) } returns respuestaOk

        val respuesta = handler.handle(wire, "   ")

        assertEquals("ok", estadoDe(respuesta))
        coVerify(exactly = 1) { bridge.handle(wire) }
    }

    // ===== F1 v1.2: token fail-closed (config no-blank, ADR-015 §2.3) =====

    @Test
    fun `config con token y extra correcto ejecuta el puente`() = runTest {
        config = PuenteConfig(tokenCompartido = "misecreto")
        coEvery { bridge.handle(wire) } returns respuestaOk

        val respuesta = handler.handle(wire, "misecreto")

        assertEquals("ok", estadoDe(respuesta))
        coVerify(exactly = 1) { bridge.handle(wire) }
    }

    @Test
    fun `extra ausente con config token produce token_invalido con 6 claves e id eco`() = runTest {
        config = PuenteConfig(tokenCompartido = "misecreto")

        val respuesta = handler.handle(wire, null)

        assertEquals("error", estadoDe(respuesta))
        assertEquals("token_invalido", errorDe(respuesta))
        assertEquals("Error: Token inválido.", mensajeDe(respuesta))
        assertEquals("h-1", idDe(respuesta)) // eco del wire
        assertSeisClaves(respuesta)
    }

    @Test
    fun `extra erroneo produce token_invalido`() = runTest {
        config = PuenteConfig(tokenCompartido = "misecreto")

        val respuesta = handler.handle(wire, "otro-valor")

        assertEquals("token_invalido", errorDe(respuesta))
        assertSeisClaves(respuesta)
    }

    @Test
    fun `el rechazo por token no ejecuta el puente`() = runTest {
        config = PuenteConfig(tokenCompartido = "misecreto")
        coEvery { bridge.handle(any()) } returns respuestaOk

        handler.handle(wire, "otro-valor")

        // Fail-closed: el bridge NO se ejecuta (no consume la ventana goAsync).
        coVerify(exactly = 0) { bridge.handle(any()) }
    }

    @Test
    fun `el rechazo sin id en el wire devuelve id null`() = runTest {
        config = PuenteConfig(tokenCompartido = "misecreto")
        val wireSinId = """{"version":1,"accion":"abrir_app","aplicacion":"x"}"""

        val respuesta = handler.handle(wireSinId, "otro-valor")

        assertEquals("token_invalido", errorDe(respuesta))
        assertNull(idDe(respuesta))
        assertSeisClaves(respuesta)
    }

    @Test
    fun `config con token y extra correcto con wire sin id ejecuta el puente`() = runTest {
        // La validación de token es independiente del wire (id eco best-effort).
        config = PuenteConfig(tokenCompartido = "misecreto")
        val wireSinId = """{"version":1,"accion":"abrir_app","aplicacion":"x"}"""
        coEvery { bridge.handle(any()) } returns respuestaOk

        val respuesta = handler.handle(wireSinId, "misecreto")

        assertEquals("ok", estadoDe(respuesta))
        coVerify(exactly = 1) { bridge.handle(any()) }
    }

    // ===== F1 v1.2: semántica exacta de la comparación (enmienda v1.2a H4) =====

    @Test
    fun `extra con espacios alrededor del valor correcto ejecuta`() = runTest {
        // H4a: `token?.trim() != tokenConfig.trim()` — el trim del handler
        // normaliza los espacios del extra " misecreto " → ejecuta.
        config = PuenteConfig(tokenCompartido = "misecreto")
        coEvery { bridge.handle(wire) } returns respuestaOk

        val respuesta = handler.handle(wire, " misecreto ")

        assertEquals("ok", estadoDe(respuesta))
        coVerify(exactly = 1) { bridge.handle(wire) }
    }

    @Test
    fun `extra con distinta capitalizacion produce token_invalido`() = runTest {
        // H4b: comparación case-sensitive SIN lowercase — "MiSecreto" != "misecreto".
        config = PuenteConfig(tokenCompartido = "misecreto")

        val respuesta = handler.handle(wire, "MiSecreto")

        assertEquals("token_invalido", errorDe(respuesta))
        assertSeisClaves(respuesta)
    }

    // ===== Cancelación del scope (cierre QA/Supervisor, punto 4) =====

    @Test
    fun `la cancelacion de la corrutina se propaga sin convertirse en fallo_ejecucion`() = runTest {
        // El puente se queda colgado en un delay largo (no en timeout): la cancelación
        // EXTERNA del job debe propagarse (catch CancellationException → rethrow), NO
        // capturarse como Exception → fallo_ejecucion (que completaría el job).
        coEvery { bridge.handle(any()) } coAnswers {
            delay(60_000)
            "tarde"
        }

        val deferred = async { handler.handle(wire, null) }
        testScheduler.advanceTimeBy(1_000) // el handler entra en bridge.handle (delay pendiente)
        deferred.cancel()
        deferred.join()

        assertTrue("la cancelación debe propagarse, no completar con JSON", deferred.isCancelled)
        assertTrue(
            "la causa de finalización debe ser CancellationException",
            deferred.getCompletionExceptionOrNull() is CancellationException,
        )
    }
}
