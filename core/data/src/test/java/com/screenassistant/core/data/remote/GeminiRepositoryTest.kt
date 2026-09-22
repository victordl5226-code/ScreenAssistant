package com.screenassistant.core.data.remote

import com.screenassistant.core.data.remote.openrouter.OpenRouterApiClient
import com.screenassistant.core.data.remote.openrouter.OpenRouterException
import com.screenassistant.core.data.remote.openrouter.OpenRouterMessage
import com.screenassistant.core.data.remote.openrouter.OpenRouterRequest
import com.screenassistant.core.data.remote.openrouter.OpenRouterResponse
import com.screenassistant.core.data.remote.openrouter.OpenRouterChoice
import com.screenassistant.core.data.remote.openrouter.OpenRouterToolCall
import com.screenassistant.core.data.remote.openrouter.OpenRouterFunctionCall
import com.screenassistant.core.data.util.ApiKeyProvider
import com.screenassistant.core.domain.action.SystemAction
import com.screenassistant.core.domain.model.ActionResult
import com.screenassistant.core.domain.model.AssistantLanguage
import com.screenassistant.core.domain.model.AssistantMode
import com.screenassistant.core.domain.model.Memory
import com.screenassistant.core.domain.model.SystemCommand
import com.screenassistant.core.domain.repository.MemoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests de GeminiRepository (data) con OpenRouterApiClient (reemplaza
 * GenerativeModelFactory del SDK de Gemini).
 *
 * C2: tool-call responses incluyen tool_call_id OBLIGATORIO.
 * C4: conversationHistory accesible vía synchronized(this) — no se testea
 *     directamente pero se verifica que los mensajes se acumulan correctamente.
 * C7: CancellationException se propaga, nunca se traga.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GeminiRepositoryTest {

    private lateinit var apiKeyProvider: ApiKeyProvider
    private lateinit var systemAction: SystemAction
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var apiClient: OpenRouterApiClient
    private lateinit var repo: GeminiRepositoryImpl

    @Before
    fun setup() {
        apiKeyProvider = mockk()
        systemAction = mockk()
        memoryRepository = mockk()
        apiClient = mockk()

        every { apiKeyProvider.getApiKey() } returns "test-key"
        every { apiKeyProvider.isUsingFallback } returns false
        every { systemAction.assistantMode } returns MutableStateFlow(AssistantMode.CENTINELA)
        coEvery { memoryRepository.getAllMemories() } returns flowOf(emptyList())
        coEvery { memoryRepository.getUserName(any()) } returns null
        coEvery { memoryRepository.getAssistantName(any()) } returns null
        coEvery { memoryRepository.saveMemory(any()) } returns Unit

        // Instancia FRESCA por test (el apiKey es lazy, se evalúa en el 1er mensaje)
        repo = GeminiRepositoryImpl(apiKeyProvider, systemAction, memoryRepository, apiClient)
    }

    // 1. sendMessage con key vacía: aviso claro y cero efectos secundarios
    @Test
    fun `sendMessage con key vacia devuelve aviso sin tocar repos ni apiClient`() = runTest {
        every { apiKeyProvider.getApiKey() } returns ""

        val result = repo.sendMessage("hola")

        assertEquals(
            "Aún no tengo mi clave de IA configurada. Revisa la configuración de la API key e inténtalo de nuevo.",
            result
        )
        coVerify(exactly = 0) { memoryRepository.getAllMemories() }
        coVerify(exactly = 0) { apiClient.chatCompletion(any()) }
    }

    // 2. streamMessage con key vacía: flujo fallback sin tocar el apiClient
    // (streamMessage delega en sendMessage → fuente :59; cero llamadas a chatCompletion)
    @Test
    fun `streamMessage con key vacia emite fallback sin llamar apiClient`() = runTest {
        every { apiKeyProvider.getApiKey() } returns ""

        val values = repo.streamMessage("hola").toList()

        assertEquals(
            listOf("Aún no tengo mi clave de IA configurada. Revisa la configuración de la API key e inténtalo de nuevo."),
            values
        )
        coVerify(exactly = 0) { apiClient.chatCompletion(any()) }
    }

    // 3. Happy path: respuesta simple sin tool calls
    @Test
    fun `sendMessage happy path devuelve texto del LLM`() = runTest {
        val response = buildTextResponse("Hola")
        coEvery { apiClient.chatCompletion(any()) } returns response

        val result = repo.sendMessage("hola")

        assertEquals("Hola", result)
    }

    // 4. Happy path + prompt enriquecido con memoria
    @Test
    fun `sendMessage incluye la memoria reciente en el prompt`() = runTest {
        val response = buildTextResponse("Hola Ana")
        coEvery { apiClient.chatCompletion(any()) } returns response
        coEvery { memoryRepository.getAllMemories() } returns flowOf(
            listOf(Memory(content = "Nombre del usuario: Ana"))
        )
        coEvery { memoryRepository.getUserName(any()) } returns "Ana"

        val requestSlot = slot<OpenRouterRequest>()
        coEvery { apiClient.chatCompletion(capture(requestSlot)) } returns response

        val result = repo.sendMessage("¿Cómo me llamo?")

        assertEquals("Hola Ana", result)
        // Verificar que el prompt contiene "Ana" (memoria inyectada en instrucción system)
        val sentMessages = requestSlot.captured.messages
        val systemMessage = sentMessages.firstOrNull { it.role == "system" }
        assertTrue(
            "la instrucción system debe contener la memoria",
            systemMessage?.content.toString().contains("Ana")
        )
    }

    // 5. ACTION_INIT_CONVERSATION: saludo personalizado + historial reseteado
    @Test
    fun `ACTION_INIT_CONVERSATION saluda con los nombres de memoria`() = runTest {
        coEvery { memoryRepository.getUserName(any()) } returns "Ana"
        coEvery { memoryRepository.getAssistantName(any()) } returns "Luna"
        val response = buildTextResponse("Hola de nuevo, Ana. Soy Luna.")
        coEvery { apiClient.chatCompletion(any()) } returns response

        val result = repo.sendMessage("ACTION_INIT_CONVERSATION")

        assertEquals("Hola de nuevo, Ana. Soy Luna.", result)
    }

    // 6. Bucle de function calls: save_memory se ejecuta y se devuelve el texto final
    @Test
    fun `sendMessage ejecuta function calls y guarda la memoria`() = runTest {
        val toolResponse = buildToolCallResponse(
            id = "call_sm1",
            toolCalls = listOf(
                OpenRouterToolCall(
                    id = "call_sm1",
                    function = OpenRouterFunctionCall(
                        name = "save_memory",
                        arguments = buildJsonObject { put("fact", "dato") }.toString()
                    )
                )
            )
        )
        val finalResponse = buildTextResponse("Guardado.")
        coEvery { apiClient.chatCompletion(any()) } returns toolResponse andThen finalResponse

        val result = repo.sendMessage("recuerda que me gusta el café")

        assertEquals("Guardado.", result)
        coVerify(exactly = 1) { memoryRepository.saveMemory("dato") }
    }

    // 7. C2: tool-call response incluye tool_call_id OBLIGATORIO
    @Test
    fun `sendMessage usa tool_call_id en la respuesta de herramienta`() = runTest {
        val toolResponse = buildToolCallResponse(
            id = "call_abc123",
            toolCalls = listOf(
                OpenRouterToolCall(
                    id = "call_abc123",
                    function = OpenRouterFunctionCall(
                        name = "open_alarms",
                        arguments = "{}"
                    )
                )
            )
        )
        val finalResponse = buildTextResponse("Abriendo alarmas.")
        coEvery { apiClient.chatCompletion(any()) } returns toolResponse andThen finalResponse

        val requestSlot = slot<OpenRouterRequest>()
        coEvery { apiClient.chatCompletion(capture(requestSlot)) } returns toolResponse andThen finalResponse

        // Mock system action for open_alarms
        coEvery { systemAction.execute(SystemCommand.OpenAlarms) } returns ActionResult.Success("Alarmas abiertas")

        repo.sendMessage("abre las alarmas")

        // Verificar que la respuesta de tool incluye tool_call_id
        val allMessages = requestSlot.captured.messages
        val toolMessages = allMessages.filter { it.role == "tool" }
        assertTrue("debe haber al menos un mensaje tool", toolMessages.isNotEmpty())
        assertTrue(
            "el mensaje tool debe tener tool_call_id",
            toolMessages.all { it.toolCallId != null }
        )
    }

    // 8. QuotaExceededException → mensaje de cupo (C3: rate limit)
    @Test
    fun `sendMessage captura RateLimited con mensaje de cupo`() = runTest {
        coEvery { apiClient.chatCompletion(any()) } throws
            OpenRouterException.RateLimited("rate limited")

        val result = repo.sendMessage("hola")

        assertEquals("He hablado mucho por ahora. Espera unos segundos y volvemos a charlar.", result)
    }

    // 9. ApiKeyInvalid → mensaje de key inválida (C3)
    @Test
    fun `sendMessage captura ApiKeyInvalid con mensaje de key`() = runTest {
        coEvery { apiClient.chatCompletion(any()) } throws
            OpenRouterException.ApiKeyInvalid(
                "Aún no tengo mi clave de IA configurada. Revisa la configuración de la API key e inténtalo de nuevo."
            )

        val result = repo.sendMessage("hola")

        assertEquals(
            "Aún no tengo mi clave de IA configurada. Revisa la configuración de la API key e inténtalo de nuevo.",
            result
        )
    }

    // 10. Error de serialización → mensaje de formato + reset del historial
    @Test
    fun `sendMessage con error de parsing resetea el historial`() = runTest {
        coEvery { apiClient.chatCompletion(any()) } throws
            OpenRouterException.ParsingError("Field missing")

        val first = repo.sendMessage("hola")
        assertEquals(
            "He tenido un pequeño error de formato. Por favor, ¿podrías repetirme lo último?",
            first
        )

        // El historial se reseteó: el 2º mensaje empieza limpio
        coEvery { apiClient.chatCompletion(any()) } returns buildTextResponse("OK")
        val second = repo.sendMessage("hola")
        assertEquals("OK", second)
    }

    // 11. open_app con app_name: ejecuta OpenApp con el nombre
    @Test
    fun `open_app con app_name ejecuta OpenApp con el nombre`() = runTest {
        val toolResponse = buildToolCallResponse(
            id = "call_app1",
            toolCalls = listOf(
                OpenRouterToolCall(
                    id = "call_app1",
                    function = OpenRouterFunctionCall(
                        name = "open_app",
                        arguments = buildJsonObject { put("app_name", "whatsapp") }.toString()
                    )
                )
            )
        )
        val finalResponse = buildTextResponse("Listo.")
        coEvery { apiClient.chatCompletion(any()) } returns toolResponse andThen finalResponse
        coEvery { systemAction.execute(SystemCommand.OpenApp("whatsapp")) } returns
            ActionResult.Success("Abriendo whatsapp")

        val result = repo.sendMessage("abre whatsapp")

        assertEquals("Listo.", result)
        coVerify(exactly = 1) { systemAction.execute(SystemCommand.OpenApp("whatsapp")) }
    }

    // 12. open_app con package_name: alias defensivo del arg
    @Test
    fun `open_app con package_name usa el alias defensivo`() = runTest {
        val toolResponse = buildToolCallResponse(
            id = "call_app2",
            toolCalls = listOf(
                OpenRouterToolCall(
                    id = "call_app2",
                    function = OpenRouterFunctionCall(
                        name = "open_app",
                        arguments = buildJsonObject { put("package_name", "com.whatsapp") }.toString()
                    )
                )
            )
        )
        val finalResponse = buildTextResponse("Listo.")
        coEvery { apiClient.chatCompletion(any()) } returns toolResponse andThen finalResponse
        coEvery { systemAction.execute(SystemCommand.OpenApp("com.whatsapp")) } returns
            ActionResult.Success("Abriendo com.whatsapp")

        val result = repo.sendMessage("abre whatsapp")

        assertEquals("Listo.", result)
        coVerify(exactly = 1) { systemAction.execute(SystemCommand.OpenApp("com.whatsapp")) }
    }

    // 13. open_app con args vacíos: query "" llega al sistema (QA menor 6)
    @Test
    fun `open_app sin args ejecuta OpenApp con query vacia`() = runTest {
        val toolResponse = buildToolCallResponse(
            id = "call_app3",
            toolCalls = listOf(
                OpenRouterToolCall(
                    id = "call_app3",
                    function = OpenRouterFunctionCall(
                        name = "open_app",
                        arguments = "{}"
                    )
                )
            )
        )
        val finalResponse = buildTextResponse("Listo.")
        coEvery { apiClient.chatCompletion(any()) } returns toolResponse andThen finalResponse
        coEvery { systemAction.execute(SystemCommand.OpenApp("")) } returns
            ActionResult.Success("No me dijiste qué aplicación abrir.")

        val result = repo.sendMessage("abre algo")

        assertEquals("Listo.", result)
        coVerify(exactly = 1) { systemAction.execute(SystemCommand.OpenApp("")) }
    }

    // 14. Contrato B4: 1 lectura de la key por mensaje, propagada al request
    @Test
    fun `sendMessage lee la key fresca una vez por mensaje`() = runTest {
        val response = buildTextResponse("Hola")
        coEvery { apiClient.chatCompletion(any()) } returns response
        every { apiKeyProvider.getApiKey() } returns "AIzaKey1" andThen "AIzaKey2"

        repo.sendMessage("hola")
        repo.sendMessage("hola")

        verify(exactly = 2) { apiKeyProvider.getApiKey() }
    }

    // 15. setLanguage(ENGLISH): limpia el historial; la instrucción sigue siendo la fija en español (J.A.R.V.I.S.)
    @Test
    fun `setLanguage a ingles limpia el historial y mantiene la instruccion fija en espanol`() = runTest {
        val response = buildTextResponse("Hello")
        coEvery { apiClient.chatCompletion(any()) } returns response

        repo.sendMessage("hola")
        repo.setLanguage(AssistantLanguage.ENGLISH)

        val requestSlot = slot<OpenRouterRequest>()
        coEvery { apiClient.chatCompletion(capture(requestSlot)) } returns response

        val result = repo.sendMessage("hola")

        assertEquals("Hello", result)
        // setLanguage limpió el historial: el request solo lleva system + el mensaje actual
        val messages = requestSlot.captured.messages
        assertEquals(2, messages.size)
        assertTrue(
            "solo debe viajar el mensaje actual tras el reset",
            messages.count { it.role == "user" } == 1
        )
        // La instrucción es la fija en español (J.A.R.V.I.S.), sin rastro de i18n
        val systemMessage = messages.firstOrNull { it.role == "system" }
        assertTrue(
            "el system message debe mantener la instrucción fija en español (J.A.R.V.I.S.)",
            systemMessage?.content.toString().contains("J.A.R.V.I.S.")
        )
    }

    // 16. setLanguage(SPANISH): la instrucción fija en español se mantiene
    @Test
    fun `setLanguage a espanol mantiene la instruccion fija en espanol`() = runTest {
        val response = buildTextResponse("Hola")
        val requestSlot = slot<OpenRouterRequest>()
        coEvery { apiClient.chatCompletion(capture(requestSlot)) } returns response

        repo.setLanguage(AssistantLanguage.SPANISH)
        val result = repo.sendMessage("hola")

        assertEquals("Hola", result)
        val messages = requestSlot.captured.messages
        assertEquals(2, messages.size)
        val systemMessage = messages.firstOrNull { it.role == "system" }
        assertTrue(
            "el system message debe mantener la instrucción fija en español (J.A.R.V.I.S.)",
            systemMessage?.content.toString().contains("J.A.R.V.I.S.")
        )
    }

    // 17. setLanguage mantiene la key: no se relee del provider al cambiar idioma
    @Test
    fun `setLanguage mantiene la key sin releerla del provider`() = runTest {
        val response = buildTextResponse("Hola")
        coEvery { apiClient.chatCompletion(any()) } returns response

        repo.sendMessage("hola") // lectura 1
        repo.setLanguage(AssistantLanguage.ENGLISH)
        repo.sendMessage("hola") // lectura 2

        verify(exactly = 2) { apiKeyProvider.getApiKey() }
    }

    // 18. setLanguage antes del primer mensaje: el reset es no-op y la instrucción fija ya se usa
    @Test
    fun `setLanguage antes del primer mensaje mantiene la instruccion fija en espanol`() = runTest {
        val response = buildTextResponse("Hello")
        val requestSlot = slot<OpenRouterRequest>()
        coEvery { apiClient.chatCompletion(capture(requestSlot)) } returns response

        repo.setLanguage(AssistantLanguage.ENGLISH)
        val result = repo.sendMessage("hola")

        assertEquals("Hello", result)
        val messages = requestSlot.captured.messages
        assertEquals(2, messages.size)
        val systemMessage = messages.firstOrNull { it.role == "system" }
        assertTrue(
            "el system message debe mantener la instrucción fija en español (J.A.R.V.I.S.)",
            systemMessage?.content.toString().contains("J.A.R.V.I.S.")
        )
    }

    // ===== B2: set_alarm con args inválidos =====

    @Test
    fun `set_alarm con hora y minuto validos ejecuta la accion`() = runTest {
        val toolResponse = buildToolCallResponse(
            id = "call_alarm1",
            toolCalls = listOf(
                OpenRouterToolCall(
                    id = "call_alarm1",
                    function = OpenRouterFunctionCall(
                        name = "set_alarm",
                        arguments = buildJsonObject {
                            put("hour", "7")
                            put("minute", "30")
                            put("label", "despertarme")
                        }.toString()
                    )
                )
            )
        )
        val finalResponse = buildTextResponse("Listo.")
        coEvery { apiClient.chatCompletion(any()) } returns toolResponse andThen finalResponse
        coEvery { systemAction.execute(SystemCommand.SetAlarm(7, 30, "despertarme")) } returns
            ActionResult.Success("Alarma a las 7:30")

        val result = repo.sendMessage("pon la alarma a las 7:30")

        assertEquals("Listo.", result)
        coVerify(exactly = 1) { systemAction.execute(SystemCommand.SetAlarm(7, 30, "despertarme")) }
    }

    @Test
    fun `set_alarm con hora fuera de rango devuelve error y no ejecuta la accion`() = runTest {
        val toolResponse = buildToolCallResponse(
            id = "call_alarm2",
            toolCalls = listOf(
                OpenRouterToolCall(
                    id = "call_alarm2",
                    function = OpenRouterFunctionCall(
                        name = "set_alarm",
                        arguments = buildJsonObject {
                            put("hour", "25")
                            put("minute", "0")
                        }.toString()
                    )
                )
            )
        )
        val finalResponse = buildTextResponse("Listo.")
        coEvery { apiClient.chatCompletion(any()) } returns toolResponse andThen finalResponse

        val result = repo.sendMessage("pon la alarma a las 25")

        assertEquals("Listo.", result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `set_alarm con minuto fuera de rango devuelve error y no ejecuta la accion`() = runTest {
        val toolResponse = buildToolCallResponse(
            id = "call_alarm3",
            toolCalls = listOf(
                OpenRouterToolCall(
                    id = "call_alarm3",
                    function = OpenRouterFunctionCall(
                        name = "set_alarm",
                        arguments = buildJsonObject {
                            put("hour", "7")
                            put("minute", "75")
                        }.toString()
                    )
                )
            )
        )
        val finalResponse = buildTextResponse("Listo.")
        coEvery { apiClient.chatCompletion(any()) } returns toolResponse andThen finalResponse

        val result = repo.sendMessage("pon la alarma a las 7 y 75")

        assertEquals("Listo.", result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `set_alarm con hora no numerica devuelve error y no ejecuta la accion`() = runTest {
        val toolResponse = buildToolCallResponse(
            id = "call_alarm4",
            toolCalls = listOf(
                OpenRouterToolCall(
                    id = "call_alarm4",
                    function = OpenRouterFunctionCall(
                        name = "set_alarm",
                        arguments = buildJsonObject {
                            put("hour", "mañana")
                            put("minute", "30")
                        }.toString()
                    )
                )
            )
        )
        val finalResponse = buildTextResponse("Listo.")
        coEvery { apiClient.chatCompletion(any()) } returns toolResponse andThen finalResponse

        val result = repo.sendMessage("pon la alarma mañana")

        assertEquals("Listo.", result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    // ===== C7: CancellationException se propaga =====

    @Test
    fun `sendMessage propaga CancellationException sin convertirla en mensaje`() = runTest {
        coEvery { apiClient.chatCompletion(any()) } throws CancellationException("cancel")

        val ex = try {
            repo.sendMessage("hola")
            null
        } catch (e: CancellationException) {
            e
        }

        assertTrue("la cancelación debe propagarse, no devolver mensaje", ex is CancellationException)
    }

    @Test
    fun `streamMessage propaga CancellationException durante la construccion del flujo`() = runTest {
        coEvery { apiClient.chatCompletion(any()) } throws CancellationException("cancel")

        val ex = try {
            repo.streamMessage("hola").toList()
            null
        } catch (e: CancellationException) {
            e
        }

        assertTrue("la cancelación debe propagarse, no devolver flujo de error", ex is CancellationException)
    }

    // ===== C3: Error HTTP 500/502/503 → FALLO_TECNICO_GENERICO =====

    @Test
    fun `sendMessage captura ServerError con mensaje generico`() = runTest {
        coEvery { apiClient.chatCompletion(any()) } throws
            OpenRouterException.ServerError("server error", 500)

        val result = repo.sendMessage("hola")

        assertEquals(
            "He tenido un problema técnico momentáneo. Inténtalo de nuevo en un segundo, Señor.",
            result
        )
    }

    @Test
    fun `streamMessage captura IOException y emite error generico`() = runTest {
        coEvery { apiClient.chatCompletion(any()) } throws java.io.IOException("red caída")

        val values = repo.streamMessage("hola").toList()

        assertEquals(
            listOf("He tenido un problema técnico momentáneo. Inténtalo de nuevo en un segundo, Señor."),
            values
        )
    }

    // ===== F2: open_youtube / open_whatsapp / play_music =====

    // F2-1. open_youtube con query="gatos" → execute(OpenYouTube("gatos")) + message de Success
    @Test
    fun `open_youtube con query ejecuta OpenYouTube con la query`() = runTest {
        val toolResponse = buildToolCallResponse(
            id = "call_yt1",
            toolCalls = listOf(
                OpenRouterToolCall(
                    id = "call_yt1",
                    function = OpenRouterFunctionCall(
                        name = "open_youtube",
                        arguments = buildJsonObject { put("query", "gatos") }.toString()
                    )
                )
            )
        )
        val finalResponse = buildTextResponse("Listo.")
        val requestSlot = slot<OpenRouterRequest>()
        coEvery { apiClient.chatCompletion(capture(requestSlot)) } returns toolResponse andThen finalResponse
        coEvery { systemAction.execute(SystemCommand.OpenYouTube("gatos")) } returns
            ActionResult.Success("Abriendo YouTube: gatos")

        val result = repo.sendMessage("busca gatos en youtube")

        assertEquals("Listo.", result)
        coVerify(exactly = 1) { systemAction.execute(SystemCommand.OpenYouTube("gatos")) }
        val toolMessages = requestSlot.captured.messages.filter { it.role == "tool" }
        assertTrue("debe haber al menos un mensaje tool", toolMessages.isNotEmpty())
        assertEquals("Abriendo YouTube: gatos", toolMessages.first().content.toString().trim('"'))
    }

    // F2-2. open_youtube sin args → execute(OpenYouTube(null))
    @Test
    fun `open_youtube sin args ejecuta OpenYouTube con null`() = runTest {
        val toolResponse = buildToolCallResponse(
            id = "call_yt2",
            toolCalls = listOf(
                OpenRouterToolCall(
                    id = "call_yt2",
                    function = OpenRouterFunctionCall(
                        name = "open_youtube",
                        arguments = "{}"
                    )
                )
            )
        )
        val finalResponse = buildTextResponse("Listo.")
        coEvery { apiClient.chatCompletion(any()) } returns toolResponse andThen finalResponse
        coEvery { systemAction.execute(SystemCommand.OpenYouTube(null)) } returns
            ActionResult.Success("Abriendo YouTube")

        val result = repo.sendMessage("abre youtube")

        assertEquals("Listo.", result)
        coVerify(exactly = 1) { systemAction.execute(SystemCommand.OpenYouTube(null)) }
    }

    // F2-3. open_whatsapp → execute(OpenWhatsApp)
    @Test
    fun `open_whatsapp ejecuta OpenWhatsApp`() = runTest {
        val toolResponse = buildToolCallResponse(
            id = "call_wa1",
            toolCalls = listOf(
                OpenRouterToolCall(
                    id = "call_wa1",
                    function = OpenRouterFunctionCall(
                        name = "open_whatsapp",
                        arguments = "{}"
                    )
                )
            )
        )
        val finalResponse = buildTextResponse("Listo.")
        coEvery { apiClient.chatCompletion(any()) } returns toolResponse andThen finalResponse
        coEvery { systemAction.execute(SystemCommand.OpenWhatsApp) } returns
            ActionResult.Success("Abriendo WhatsApp")

        val result = repo.sendMessage("abre whatsapp")

        assertEquals("Listo.", result)
        coVerify(exactly = 1) { systemAction.execute(SystemCommand.OpenWhatsApp) }
    }

    // F2-4. play_music con query → execute(PlayMusic(query))
    @Test
    fun `play_music con query ejecuta PlayMusic con la query`() = runTest {
        val toolResponse = buildToolCallResponse(
            id = "call_pm1",
            toolCalls = listOf(
                OpenRouterToolCall(
                    id = "call_pm1",
                    function = OpenRouterFunctionCall(
                        name = "play_music",
                        arguments = buildJsonObject { put("query", "rock") }.toString()
                    )
                )
            )
        )
        val finalResponse = buildTextResponse("Listo.")
        coEvery { apiClient.chatCompletion(any()) } returns toolResponse andThen finalResponse
        coEvery { systemAction.execute(SystemCommand.PlayMusic("rock")) } returns
            ActionResult.Success("Reproduciendo: rock")

        val result = repo.sendMessage("pon música rock")

        assertEquals("Listo.", result)
        coVerify(exactly = 1) { systemAction.execute(SystemCommand.PlayMusic("rock")) }
    }

    // F2-5. play_music sin query → execute(PlayMusic(null))
    @Test
    fun `play_music sin args ejecuta PlayMusic con null`() = runTest {
        val toolResponse = buildToolCallResponse(
            id = "call_pm2",
            toolCalls = listOf(
                OpenRouterToolCall(
                    id = "call_pm2",
                    function = OpenRouterFunctionCall(
                        name = "play_music",
                        arguments = "{}"
                    )
                )
            )
        )
        val finalResponse = buildTextResponse("Listo.")
        coEvery { apiClient.chatCompletion(any()) } returns toolResponse andThen finalResponse
        coEvery { systemAction.execute(SystemCommand.PlayMusic(null)) } returns
            ActionResult.Success("Reproduciendo música")

        val result = repo.sendMessage("pon música")

        assertEquals("Listo.", result)
        coVerify(exactly = 1) { systemAction.execute(SystemCommand.PlayMusic(null)) }
    }

    // ===== D2: blank→null + Error→reason (observación QA de F2) =====

    // D2-1. open_youtube con query blank ("   ") → execute(OpenYouTube(null))
    // (takeIf { isNotBlank } en GeminiRepositoryImpl:321-323 mapea blank a null)
    @Test
    fun `open_youtube con query blank ejecuta OpenYouTube con null`() = runTest {
        val toolResponse = buildToolCallResponse(
            id = "call_yt3",
            toolCalls = listOf(
                OpenRouterToolCall(
                    id = "call_yt3",
                    function = OpenRouterFunctionCall(
                        name = "open_youtube",
                        arguments = buildJsonObject { put("query", "   ") }.toString()
                    )
                )
            )
        )
        val finalResponse = buildTextResponse("Listo.")
        coEvery { apiClient.chatCompletion(any()) } returns toolResponse andThen finalResponse
        coEvery { systemAction.execute(SystemCommand.OpenYouTube(null)) } returns
            ActionResult.Success("Abriendo YouTube")

        val result = repo.sendMessage("abre youtube")

        assertEquals("Listo.", result)
        coVerify(exactly = 1) { systemAction.execute(SystemCommand.OpenYouTube(null)) }
    }

    // D2-2. play_music con query blank ("   ") → execute(PlayMusic(null))
    // (takeIf { isNotBlank } en GeminiRepositoryImpl:334-336 mapea blank a null)
    @Test
    fun `play_music con query blank ejecuta PlayMusic con null`() = runTest {
        val toolResponse = buildToolCallResponse(
            id = "call_pm3",
            toolCalls = listOf(
                OpenRouterToolCall(
                    id = "call_pm3",
                    function = OpenRouterFunctionCall(
                        name = "play_music",
                        arguments = buildJsonObject { put("query", "   ") }.toString()
                    )
                )
            )
        )
        val finalResponse = buildTextResponse("Listo.")
        coEvery { apiClient.chatCompletion(any()) } returns toolResponse andThen finalResponse
        coEvery { systemAction.execute(SystemCommand.PlayMusic(null)) } returns
            ActionResult.Success("Reproduciendo música")

        val result = repo.sendMessage("pon música")

        assertEquals("Listo.", result)
        coVerify(exactly = 1) { systemAction.execute(SystemCommand.PlayMusic(null)) }
    }

    // D2-3. open_youtube con Error del sistema → el contenido tool es el reason
    // (rama Error→reason en GeminiRepositoryImpl:323-326)
    @Test
    fun `open_youtube con Error propaga el reason como contenido tool`() = runTest {
        val toolResponse = buildToolCallResponse(
            id = "call_yt4",
            toolCalls = listOf(
                OpenRouterToolCall(
                    id = "call_yt4",
                    function = OpenRouterFunctionCall(
                        name = "open_youtube",
                        arguments = buildJsonObject { put("query", "gatos") }.toString()
                    )
                )
            )
        )
        val finalResponse = buildTextResponse("Listo.")
        val requestSlot = slot<OpenRouterRequest>()
        coEvery { apiClient.chatCompletion(capture(requestSlot)) } returns toolResponse andThen finalResponse
        coEvery { systemAction.execute(SystemCommand.OpenYouTube("gatos")) } returns
            ActionResult.Error("No se pudo abrir YouTube")

        val result = repo.sendMessage("busca gatos en youtube")

        assertEquals("Listo.", result)
        coVerify(exactly = 1) { systemAction.execute(SystemCommand.OpenYouTube("gatos")) }
        val toolMessages = requestSlot.captured.messages.filter { it.role == "tool" }
        assertTrue("debe haber al menos un mensaje tool", toolMessages.isNotEmpty())
        assertEquals("No se pudo abrir YouTube", toolMessages.first().content.toString().trim('"'))
    }

    // D2-4. open_whatsapp con Error del sistema → el contenido tool es el reason
    // (rama Error→reason en GeminiRepositoryImpl:328-333)
    @Test
    fun `open_whatsapp con Error propaga el reason como contenido tool`() = runTest {
        val toolResponse = buildToolCallResponse(
            id = "call_wa2",
            toolCalls = listOf(
                OpenRouterToolCall(
                    id = "call_wa2",
                    function = OpenRouterFunctionCall(
                        name = "open_whatsapp",
                        arguments = "{}"
                    )
                )
            )
        )
        val finalResponse = buildTextResponse("Listo.")
        val requestSlot = slot<OpenRouterRequest>()
        coEvery { apiClient.chatCompletion(capture(requestSlot)) } returns toolResponse andThen finalResponse
        coEvery { systemAction.execute(SystemCommand.OpenWhatsApp) } returns
            ActionResult.Error("WhatsApp no está instalado")

        val result = repo.sendMessage("abre whatsapp")

        assertEquals("Listo.", result)
        coVerify(exactly = 1) { systemAction.execute(SystemCommand.OpenWhatsApp) }
        val toolMessages = requestSlot.captured.messages.filter { it.role == "tool" }
        assertTrue("debe haber al menos un mensaje tool", toolMessages.isNotEmpty())
        assertEquals("WhatsApp no está instalado", toolMessages.first().content.toString().trim('"'))
    }

    // D2-5. play_music con Error del sistema → el contenido tool es el reason
    // (rama Error→reason en GeminiRepositoryImpl:334-340)
    @Test
    fun `play_music con Error propaga el reason como contenido tool`() = runTest {
        val toolResponse = buildToolCallResponse(
            id = "call_pm4",
            toolCalls = listOf(
                OpenRouterToolCall(
                    id = "call_pm4",
                    function = OpenRouterFunctionCall(
                        name = "play_music",
                        arguments = buildJsonObject { put("query", "rock") }.toString()
                    )
                )
            )
        )
        val finalResponse = buildTextResponse("Listo.")
        val requestSlot = slot<OpenRouterRequest>()
        coEvery { apiClient.chatCompletion(capture(requestSlot)) } returns toolResponse andThen finalResponse
        coEvery { systemAction.execute(SystemCommand.PlayMusic("rock")) } returns
            ActionResult.Error("No se pudo reproducir la música")

        val result = repo.sendMessage("pon música rock")

        assertEquals("Listo.", result)
        coVerify(exactly = 1) { systemAction.execute(SystemCommand.PlayMusic("rock")) }
        val toolMessages = requestSlot.captured.messages.filter { it.role == "tool" }
        assertTrue("debe haber al menos un mensaje tool", toolMessages.isNotEmpty())
        assertEquals("No se pudo reproducir la música", toolMessages.first().content.toString().trim('"'))
    }

    // F2-6. else intacto: función desconocida → "Función ejecutada." sin tocar systemAction
    @Test
    fun `funcion desconocida devuelve Funcion ejecutada sin tocar systemAction`() = runTest {
        val toolResponse = buildToolCallResponse(
            id = "call_unk1",
            toolCalls = listOf(
                OpenRouterToolCall(
                    id = "call_unk1",
                    function = OpenRouterFunctionCall(
                        name = "funcion_inexistente_xyz",
                        arguments = "{}"
                    )
                )
            )
        )
        val finalResponse = buildTextResponse("Listo.")
        val requestSlot = slot<OpenRouterRequest>()
        coEvery { apiClient.chatCompletion(capture(requestSlot)) } returns toolResponse andThen finalResponse

        val result = repo.sendMessage("haz algo raro")

        assertEquals("Listo.", result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
        val toolMessages = requestSlot.captured.messages.filter { it.role == "tool" }
        assertTrue("debe haber al menos un mensaje tool", toolMessages.isNotEmpty())
        assertEquals("Función ejecutada.", toolMessages.first().content.toString().trim('"'))
    }

    // ===== PO anti-relleno: systemInstruction con las 4 reglas =====

    @Test
    fun `systemInstruction anti-relleno contiene las 4 reglas`() = runTest {
        val response = buildTextResponse("Hola")
        val requestSlot = slot<OpenRouterRequest>()
        coEvery { apiClient.chatCompletion(capture(requestSlot)) } returns response

        repo.sendMessage("hola")

        val systemMessage = requestSlot.captured.messages.firstOrNull { it.role == "system" }
        val systemText = systemMessage?.content.toString()
        assertTrue(
            "el system debe pedir responder PRIMERO con lo pedido",
            systemText.contains("PRIMERO con lo pedido")
        )
        assertTrue(
            "el system debe prohibir responder solo protocolo",
            systemText.contains("PROHIBIDO")
        )
        assertTrue(
            "el system debe exigir español",
            systemText.lowercase().contains("español")
        )
        assertTrue(
            "el system debe limitar a 3 frases",
            systemText.contains("3 frases")
        )
    }

    @Test
    fun `init tambien lleva anti-relleno`() = runTest {
        val response = buildTextResponse("Hola")
        val requestSlot = slot<OpenRouterRequest>()
        coEvery { apiClient.chatCompletion(capture(requestSlot)) } returns response

        repo.sendMessage("ACTION_INIT_CONVERSATION")

        val systemMessage = requestSlot.captured.messages.firstOrNull { it.role == "system" }
        val systemText = systemMessage?.content.toString()
        assertTrue(
            "el system de init debe pedir responder PRIMERO con lo pedido",
            systemText.contains("PRIMERO con lo pedido")
        )
        assertTrue(
            "el system de init debe prohibir responder solo protocolo",
            systemText.contains("PROHIBIDO")
        )
        assertTrue(
            "el system de init debe exigir español",
            systemText.lowercase().contains("español")
        )
        assertTrue(
            "el system de init debe limitar a 3 frases",
            systemText.contains("3 frases")
        )
    }

    // ===== Helpers ──────────────────────────────────────────────────────

    private fun buildTextResponse(text: String): OpenRouterResponse {
        return OpenRouterResponse(
            id = "chatcmpl-test",
            choices = listOf(
                OpenRouterChoice(
                    message = OpenRouterMessage(
                        role = "assistant",
                        content = JsonPrimitive(text)
                    )
                )
            )
        )
    }

    private fun buildToolCallResponse(
        id: String,
        toolCalls: List<OpenRouterToolCall>
    ): OpenRouterResponse {
        return OpenRouterResponse(
            id = "chatcmpl-test",
            choices = listOf(
                OpenRouterChoice(
                    message = OpenRouterMessage(
                        role = "assistant",
                        content = null,
                        toolCalls = toolCalls
                    )
                )
            )
        )
    }
}
