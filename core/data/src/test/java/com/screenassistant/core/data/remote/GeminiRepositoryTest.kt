package com.screenassistant.core.data.remote

import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.FunctionCallPart
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.QuotaExceededException
import com.google.ai.client.generativeai.type.TextPart
import com.screenassistant.core.data.util.ApiKeyProvider
import com.screenassistant.core.domain.action.SystemAction
import com.screenassistant.core.domain.model.ActionResult
import com.screenassistant.core.domain.model.AssistantLanguage
import com.screenassistant.core.domain.model.Memory
import com.screenassistant.core.domain.model.SystemCommand
import com.screenassistant.core.domain.repository.MemoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests de GeminiRepository (data) con la factory extraída.
 * Nota SDK 0.9.0: response.functionCalls es List<FunctionCallPart>
 * (name + args), NO existe com.google.ai.client.generativeai.type.FunctionCall.
 * Todos los tests pasan image = null: BitmapFactory no está mockeado en JVM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GeminiRepositoryTest {

    private lateinit var apiKeyProvider: ApiKeyProvider
    private lateinit var systemAction: SystemAction
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var modelFactory: GenerativeModelFactory
    private lateinit var repo: GeminiRepositoryImpl

    @Before
    fun setup() {
        apiKeyProvider = mockk()
        systemAction = mockk()
        memoryRepository = mockk()
        modelFactory = mockk()

        every { apiKeyProvider.getApiKey() } returns "test-key"
        coEvery { memoryRepository.getAllMemories() } returns flowOf(emptyList())
        coEvery { memoryRepository.getUserName(any()) } returns null
        coEvery { memoryRepository.getAssistantName(any()) } returns null
        coEvery { memoryRepository.saveMemory(any()) } returns Unit

        // Instancia FRESCA por test (el apiKey es lazy, se evalúa en el 1er mensaje)
        // M24: clase renombrada a *Impl (un nombre = un concepto, precedente ScreenContextRepositoryImpl).
        repo = GeminiRepositoryImpl(apiKeyProvider, systemAction, memoryRepository, modelFactory)
    }

    // 1. sendMessage con key vacía: aviso claro y cero efectos secundarios
    @Test
    fun `sendMessage con key vacia devuelve aviso sin tocar repos ni factory`() = runTest {
        every { apiKeyProvider.getApiKey() } returns ""

        val result = repo.sendMessage("hola")

        assertEquals(
            "Aún no tengo mi clave de IA configurada. Revisa la configuración de la API key e inténtalo de nuevo.",
            result
        )
        coVerify(exactly = 0) { memoryRepository.getAllMemories() }
        verify(exactly = 0) { modelFactory.create(any(), any(), any()) }
    }

    // 2. streamMessage con key vacía: flujo fallback sin tocar la factory
    @Test
    fun `streamMessage con key vacia emite fallback sin llamar factory`() = runTest {
        every { apiKeyProvider.getApiKey() } returns ""

        val values = repo.streamMessage("hola").toList()

        assertEquals(listOf("No pude inicializar el modelo de IA."), values)
        verify(exactly = 0) { modelFactory.create(any(), any(), any()) }
    }

    // 3. streamMessage con key válida y factory → null: mismo fallback
    @Test
    fun `streamMessage con factory null emite fallback`() = runTest {
        every { modelFactory.create(any(), any(), any()) } returns null

        val values = repo.streamMessage("hola").toList()

        assertEquals(listOf("No pude inicializar el modelo de IA."), values)
        verify(exactly = 1) { modelFactory.create(any(), any(), any()) }
    }

    // 4. sendMessage con key válida y factory → null: aviso de inicialización
    @Test
    fun `sendMessage con factory null devuelve aviso de inicializacion`() = runTest {
        every { modelFactory.create(any(), any(), any()) } returns null

        val result = repo.sendMessage("hola")

        assertEquals("No pude inicializar el modelo de IA. Inténtalo de nuevo en un momento.", result)
    }

    // 5. Retry: factory falla 1ª vez (null) y responde en la 2ª
    @Test
    fun `sendMessage reintenta cuando la factory empieza a responder`() = runTest {
        val modelMock = mockk<GenerativeModel>()
        val chatMock = mockk<Chat>()
        val responseMock = mockk<GenerateContentResponse>()
        every { modelMock.startChat() } returns chatMock
        coEvery { chatMock.sendMessage(any<Content>()) } returns responseMock
        every { responseMock.functionCalls } returns emptyList()
        every { responseMock.text } returns "Hola"
        every { modelFactory.create(any(), any(), any()) } returns null andThen modelMock

        val first = repo.sendMessage("hola")
        assertEquals("No pude inicializar el modelo de IA. Inténtalo de nuevo en un momento.", first)

        val second = repo.sendMessage("hola")
        assertEquals("Hola", second)
        verify(exactly = 2) { modelFactory.create(any(), any(), any()) }
    }

    // 6. Happy path + prompt enriquecido con memoria
    @Test
    fun `sendMessage incluye la memoria reciente en el prompt`() = runTest {
        val modelMock = mockk<GenerativeModel>()
        val chatMock = mockk<Chat>()
        val responseMock = mockk<GenerateContentResponse>()
        every { modelMock.startChat() } returns chatMock
        every { responseMock.functionCalls } returns emptyList()
        every { responseMock.text } returns "Hola"
        every { modelFactory.create(any(), any(), any()) } returns modelMock

        coEvery { memoryRepository.getAllMemories() } returns flowOf(
            listOf(Memory(content = "Nombre del usuario: Ana"))
        )
        coEvery { memoryRepository.getUserName(any()) } returns "Ana"

        val contentSlot = slot<Content>()
        coEvery { chatMock.sendMessage(capture(contentSlot)) } returns responseMock

        val result = repo.sendMessage("¿Cómo me llamo?")

        assertEquals("Hola", result)
        val sentText = contentSlot.captured.parts.filterIsInstance<TextPart>().joinToString { it.text }
        assertTrue(sentText.contains("¿Cómo me llamo?"))
        assertTrue(sentText.contains("Ana"))
    }

    // 7. ACTION_INIT_CONVERSATION: saludo personalizado + chat reconstruido
    @Test
    fun `ACTION_INIT_CONVERSATION saluda con los nombres de memoria`() = runTest {
        val modelMock = mockk<GenerativeModel>()
        val chatMock = mockk<Chat>()
        val responseMock = mockk<GenerateContentResponse>()
        every { modelMock.startChat() } returns chatMock
        every { responseMock.functionCalls } returns emptyList()
        every { responseMock.text } returns "Hola de nuevo, Ana. Soy Luna."
        every { modelFactory.create(any(), any(), any()) } returns modelMock

        coEvery { memoryRepository.getUserName(any()) } returns "Ana"
        coEvery { memoryRepository.getAssistantName(any()) } returns "Luna"

        val contentSlot = slot<Content>()
        coEvery { chatMock.sendMessage(capture(contentSlot)) } returns responseMock

        repo.sendMessage("ACTION_INIT_CONVERSATION")

        val sentText = contentSlot.captured.parts.filterIsInstance<TextPart>().joinToString { it.text }
        assertEquals("Hola de nuevo, Ana. Soy Luna.", sentText)

        // Tras init (chat = null) el chat se reconstruye con startChat:
        // el init mismo crea el chat fresco y el 2º mensaje lo reutiliza.
        repo.sendMessage("Hola")
        verify(exactly = 1) { modelMock.startChat() }
        coVerify(exactly = 2) { chatMock.sendMessage(any<Content>()) }
    }

    // 8. Bucle de function calls: save_memory se ejecuta y se devuelve el texto final
    @Test
    fun `sendMessage ejecuta function calls y guarda la memoria`() = runTest {
        val modelMock = mockk<GenerativeModel>()
        val chatMock = mockk<Chat>()
        val callResponse = mockk<GenerateContentResponse>()
        val finalResponse = mockk<GenerateContentResponse>()
        every { modelMock.startChat() } returns chatMock
        coEvery { chatMock.sendMessage(any<Content>()) } returns callResponse andThen finalResponse
        every { callResponse.functionCalls } returns listOf(
            FunctionCallPart("save_memory", mapOf("fact" to "dato"))
        )
        every { callResponse.text } returns null
        every { finalResponse.functionCalls } returns emptyList()
        every { finalResponse.text } returns "Guardado."
        every { modelFactory.create(any(), any(), any()) } returns modelMock

        val result = repo.sendMessage("recuerda que me gusta el café")

        assertEquals("Guardado.", result)
        coVerify(exactly = 1) { memoryRepository.saveMemory("dato") }
    }

    // 9. QuotaExceededException → mensaje de cupo
    @Test
    fun `sendMessage captura QuotaExceededException con mensaje de cupo`() = runTest {
        val modelMock = mockk<GenerativeModel>()
        val chatMock = mockk<Chat>()
        every { modelMock.startChat() } returns chatMock
        coEvery { chatMock.sendMessage(any<Content>()) } throws QuotaExceededException("Quota exceeded", null)
        every { modelFactory.create(any(), any(), any()) } returns modelMock

        val result = repo.sendMessage("hola")

        assertEquals("He hablado mucho por ahora. Espera unos segundos y volvemos a charlar.", result)
    }

    // 10. Error de serialización → mensaje de formato + reset del chat
    @Test
    fun `sendMessage con error de serializacion resetea el chat`() = runTest {
        val modelMock = mockk<GenerativeModel>()
        val chatMock = mockk<Chat>()
        every { modelMock.startChat() } returns chatMock
        coEvery { chatMock.sendMessage(any<Content>()) } throws Exception("Field missing")
        every { modelFactory.create(any(), any(), any()) } returns modelMock

        val first = repo.sendMessage("hola")
        assertEquals(
            "He tenido un pequeño error de formato. Por favor, ¿podrías repetirme lo último?",
            first
        )

        // El chat se reseteó (chat = null): el 2º mensaje vuelve a llamar startChat
        val second = repo.sendMessage("hola")
        assertEquals(
            "He tenido un pequeño error de formato. Por favor, ¿podrías repetirme lo último?",
            second
        )
        verify(exactly = 2) { modelMock.startChat() }
    }

    // 11. open_app con app_name: ejecuta OpenApp con el nombre y llega el texto final
    @Test
    fun `open_app con app_name ejecuta OpenApp con el nombre`() = runTest {
        val modelMock = mockk<GenerativeModel>()
        val chatMock = mockk<Chat>()
        val callResponse = mockk<GenerateContentResponse>()
        val finalResponse = mockk<GenerateContentResponse>()
        every { modelMock.startChat() } returns chatMock
        coEvery { chatMock.sendMessage(any<Content>()) } returns callResponse andThen finalResponse
        every { callResponse.functionCalls } returns listOf(
            FunctionCallPart("open_app", mapOf("app_name" to "whatsapp"))
        )
        every { callResponse.text } returns null
        every { finalResponse.functionCalls } returns emptyList()
        every { finalResponse.text } returns "Listo."
        every { modelFactory.create(any(), any(), any()) } returns modelMock
        coEvery { systemAction.execute(SystemCommand.OpenApp("whatsapp")) } returns
            ActionResult.Success("Abriendo whatsapp")

        val result = repo.sendMessage("abre whatsapp")

        assertEquals("Listo.", result)
        coVerify(exactly = 1) { systemAction.execute(SystemCommand.OpenApp("whatsapp")) }
    }

    // 12. open_app con package_name: alias defensivo del arg
    @Test
    fun `open_app con package_name usa el alias defensivo`() = runTest {
        val modelMock = mockk<GenerativeModel>()
        val chatMock = mockk<Chat>()
        val callResponse = mockk<GenerateContentResponse>()
        val finalResponse = mockk<GenerateContentResponse>()
        every { modelMock.startChat() } returns chatMock
        coEvery { chatMock.sendMessage(any<Content>()) } returns callResponse andThen finalResponse
        every { callResponse.functionCalls } returns listOf(
            FunctionCallPart("open_app", mapOf("package_name" to "com.whatsapp"))
        )
        every { callResponse.text } returns null
        every { finalResponse.functionCalls } returns emptyList()
        every { finalResponse.text } returns "Listo."
        every { modelFactory.create(any(), any(), any()) } returns modelMock
        coEvery { systemAction.execute(SystemCommand.OpenApp("com.whatsapp")) } returns
            ActionResult.Success("Abriendo com.whatsapp")

        val result = repo.sendMessage("abre whatsapp")

        assertEquals("Listo.", result)
        coVerify(exactly = 1) { systemAction.execute(SystemCommand.OpenApp("com.whatsapp")) }
    }

    // 13. open_app con args vacíos: query "" llega al sistema (QA menor 6)
    @Test
    fun `open_app sin args ejecuta OpenApp con query vacia`() = runTest {
        val modelMock = mockk<GenerativeModel>()
        val chatMock = mockk<Chat>()
        val callResponse = mockk<GenerateContentResponse>()
        val finalResponse = mockk<GenerateContentResponse>()
        every { modelMock.startChat() } returns chatMock
        coEvery { chatMock.sendMessage(any<Content>()) } returns callResponse andThen finalResponse
        every { callResponse.functionCalls } returns listOf(
            FunctionCallPart("open_app", mapOf())
        )
        every { callResponse.text } returns null
        every { finalResponse.functionCalls } returns emptyList()
        every { finalResponse.text } returns "Listo."
        every { modelFactory.create(any(), any(), any()) } returns modelMock
        coEvery { systemAction.execute(SystemCommand.OpenApp("")) } returns
            ActionResult.Success("No me dijiste qué aplicación abrir.")

        val result = repo.sendMessage("abre algo")

        assertEquals("Listo.", result)
        coVerify(exactly = 1) { systemAction.execute(SystemCommand.OpenApp("")) }
    }

    // 14. Contrato B4: 1 lectura de la key por mensaje, propagada a la factory
    @Test
    fun `sendMessage lee la key fresca una vez por mensaje`() = runTest {
        val modelMock = mockk<GenerativeModel>()
        val chatMock = mockk<Chat>()
        val responseMock = mockk<GenerateContentResponse>()
        every { modelMock.startChat() } returns chatMock
        coEvery { chatMock.sendMessage(any<Content>()) } returns responseMock
        every { responseMock.functionCalls } returns emptyList()
        every { responseMock.text } returns "Hola"
        every { apiKeyProvider.getApiKey() } returns "AIzaKey1" andThen "AIzaKey2"
        every { modelFactory.create(any(), any(), any()) } returns modelMock

        repo.sendMessage("hola")
        repo.sendMessage("hola")

        verify(exactly = 2) { apiKeyProvider.getApiKey() }
        verify(exactly = 1) { modelFactory.create(eq("AIzaKey1"), any(), any()) }
        verify(exactly = 1) { modelFactory.create(eq("AIzaKey2"), any(), any()) }
    }

    // 15. Cambio de key entre mensajes resetea el chat; key constante lo reutiliza
    @Test
    fun `sendMessage resetea el chat cuando cambia la key`() = runTest {
        val modelMock = mockk<GenerativeModel>()
        val chatMock = mockk<Chat>()
        val responseMock = mockk<GenerateContentResponse>()
        every { modelMock.startChat() } returns chatMock
        coEvery { chatMock.sendMessage(any<Content>()) } returns responseMock
        every { responseMock.functionCalls } returns emptyList()
        every { responseMock.text } returns "Hola"
        every { modelFactory.create(any(), any(), any()) } returns modelMock

        // Key A constante: 2 mensajes reutilizan el mismo chat
        every { apiKeyProvider.getApiKey() } returns "AIzaKeyA"
        repo.sendMessage("hola")
        repo.sendMessage("hola")
        verify(exactly = 1) { modelMock.startChat() }

        // Key B: 1 mensaje resetea y reconstruye el chat
        every { apiKeyProvider.getApiKey() } returns "AIzaKeyB"
        repo.sendMessage("hola")
        verify(exactly = 2) { modelMock.startChat() }

        // Contrato B4: 1 lectura de key por mensaje (3 mensajes → 3 lecturas)
        verify(exactly = 3) { apiKeyProvider.getApiKey() }
    }

    // 16. setLanguage(ENGLISH): reconstruye el chat con la instrucción en inglés
    @Test
    fun `setLanguage a ingles reconstruye el chat con instruccion en ingles`() = runTest {
        val modelMock = mockk<GenerativeModel>()
        val chatMock = mockk<Chat>()
        val responseMock = mockk<GenerateContentResponse>()
        every { modelMock.startChat() } returns chatMock
        coEvery { chatMock.sendMessage(any<Content>()) } returns responseMock
        every { responseMock.functionCalls } returns emptyList()
        every { responseMock.text } returns "Hello"
        every { modelFactory.create(any(), any(), any()) } returns modelMock

        repo.sendMessage("hola")
        repo.setLanguage(AssistantLanguage.ENGLISH)
        val result = repo.sendMessage("hola")

        assertEquals("Hello", result)
        // chat = null tras setLanguage → el 2º mensaje reconstruye el chat
        verify(exactly = 2) { modelMock.startChat() }
        // El modelo del 2º mensaje se crea con la instrucción en inglés
        verify(exactly = 1) {
            modelFactory.create(eq("test-key"), any(), match {
                it != null && it.parts.filterIsInstance<TextPart>()
                    .joinToString { p -> p.text }
                    .contains("User language (English by default).")
            })
        }
    }

    // 17. setLanguage(SPANISH): vuelve a la instrucción por defecto
    @Test
    fun `setLanguage a espanol vuelve a la instruccion por defecto`() = runTest {
        val modelMock = mockk<GenerativeModel>()
        val chatMock = mockk<Chat>()
        val responseMock = mockk<GenerateContentResponse>()
        every { modelMock.startChat() } returns chatMock
        coEvery { chatMock.sendMessage(any<Content>()) } returns responseMock
        every { responseMock.functionCalls } returns emptyList()
        every { responseMock.text } returns "Hola"
        every { modelFactory.create(any(), any(), any()) } returns modelMock

        repo.setLanguage(AssistantLanguage.SPANISH)
        repo.sendMessage("hola")

        verify(exactly = 1) {
            modelFactory.create(eq("test-key"), any(), match {
                it != null && it.parts.filterIsInstance<TextPart>()
                    .joinToString { p -> p.text }
                    .contains("Idioma del usuario (español por defecto).")
            })
        }
    }

    // 18. setLanguage mantiene la key: no se relee del provider al cambiar idioma
    @Test
    fun `setLanguage mantiene la key sin releerla del provider`() = runTest {
        val modelMock = mockk<GenerativeModel>()
        val chatMock = mockk<Chat>()
        val responseMock = mockk<GenerateContentResponse>()
        every { modelMock.startChat() } returns chatMock
        coEvery { chatMock.sendMessage(any<Content>()) } returns responseMock
        every { responseMock.functionCalls } returns emptyList()
        every { responseMock.text } returns "Hello"
        every { modelFactory.create(any(), any(), any()) } returns modelMock

        repo.sendMessage("hola") // lectura 1
        repo.setLanguage(AssistantLanguage.ENGLISH)
        repo.sendMessage("hola") // lectura 2 (el rebuild reutiliza lastApiKey)

        verify(exactly = 2) { apiKeyProvider.getApiKey() }
        // Ambas creaciones usan la MISMA key (sin relectura tras setLanguage)
        verify(exactly = 2) { modelFactory.create(eq("test-key"), any(), any()) }
    }

    // 19. setLanguage antes del primer mensaje: la instrucción nueva ya se usa
    @Test
    fun `setLanguage antes del primer mensaje se refleja en la instruccion`() = runTest {
        val modelMock = mockk<GenerativeModel>()
        val chatMock = mockk<Chat>()
        val responseMock = mockk<GenerateContentResponse>()
        every { modelMock.startChat() } returns chatMock
        coEvery { chatMock.sendMessage(any<Content>()) } returns responseMock
        every { responseMock.functionCalls } returns emptyList()
        every { responseMock.text } returns "Hello"
        every { modelFactory.create(any(), any(), any()) } returns modelMock

        repo.setLanguage(AssistantLanguage.ENGLISH)
        repo.sendMessage("hola")

        verify(exactly = 1) {
            modelFactory.create(eq("test-key"), any(), match {
                it != null && it.parts.filterIsInstance<TextPart>()
                    .joinToString { p -> p.text }
                    .contains("User language (English by default).")
            })
        }
    }

    // ===== B2: set_alarm con args inválidos (antes: fallback a 0 → alarma 0:00 fantasma) =====

    // Rama VÁLIDA del branch (QA Lote 8): hora y minuto dentro de rango SÍ ejecutan
    // la acción — la rama else de la validación B2 no tenía cobertura.
    @Test
    fun `set_alarm con hora y minuto validos ejecuta la accion`() = runTest {
        val modelMock = mockk<GenerativeModel>()
        val chatMock = mockk<Chat>()
        val callResponse = mockk<GenerateContentResponse>()
        val finalResponse = mockk<GenerateContentResponse>()
        every { modelMock.startChat() } returns chatMock
        coEvery { chatMock.sendMessage(any<Content>()) } returns callResponse andThen finalResponse
        every { callResponse.functionCalls } returns listOf(
            FunctionCallPart("set_alarm", mapOf("hour" to "7", "minute" to "30", "label" to "despertarme"))
        )
        every { callResponse.text } returns null
        every { finalResponse.functionCalls } returns emptyList()
        every { finalResponse.text } returns "Listo."
        every { modelFactory.create(any(), any(), any()) } returns modelMock
        coEvery { systemAction.execute(SystemCommand.SetAlarm(7, 30, "despertarme")) } returns
            ActionResult.Success("Alarma a las 7:30")

        val result = repo.sendMessage("pon la alarma a las 7:30")

        assertEquals("Listo.", result)
        // B2 rama válida: se ejecuta SetAlarm con la hora/minuto parseados y el label.
        coVerify(exactly = 1) { systemAction.execute(SystemCommand.SetAlarm(7, 30, "despertarme")) }
    }

    @Test
    fun `set_alarm con hora fuera de rango devuelve error y no ejecuta la accion`() = runTest {
        val modelMock = mockk<GenerativeModel>()
        val chatMock = mockk<Chat>()
        val callResponse = mockk<GenerateContentResponse>()
        val finalResponse = mockk<GenerateContentResponse>()
        every { modelMock.startChat() } returns chatMock
        coEvery { chatMock.sendMessage(any<Content>()) } returns callResponse andThen finalResponse
        every { callResponse.functionCalls } returns listOf(
            FunctionCallPart("set_alarm", mapOf("hour" to "25", "minute" to "0"))
        )
        every { callResponse.text } returns null
        every { finalResponse.functionCalls } returns emptyList()
        every { finalResponse.text } returns "Listo."
        every { modelFactory.create(any(), any(), any()) } returns modelMock

        val result = repo.sendMessage("pon la alarma a las 25")

        assertEquals("Listo.", result)
        // B2: hora 25 fuera de 0..23 → NUNCA se ejecuta la acción (antes: SetAlarm(0, 0)).
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `set_alarm con minuto fuera de rango devuelve error y no ejecuta la accion`() = runTest {
        val modelMock = mockk<GenerativeModel>()
        val chatMock = mockk<Chat>()
        val callResponse = mockk<GenerateContentResponse>()
        val finalResponse = mockk<GenerateContentResponse>()
        every { modelMock.startChat() } returns chatMock
        coEvery { chatMock.sendMessage(any<Content>()) } returns callResponse andThen finalResponse
        every { callResponse.functionCalls } returns listOf(
            FunctionCallPart("set_alarm", mapOf("hour" to "7", "minute" to "75"))
        )
        every { callResponse.text } returns null
        every { finalResponse.functionCalls } returns emptyList()
        every { finalResponse.text } returns "Listo."
        every { modelFactory.create(any(), any(), any()) } returns modelMock

        val result = repo.sendMessage("pon la alarma a las 7 y 75")

        assertEquals("Listo.", result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `set_alarm con hora no numerica devuelve error y no ejecuta la accion`() = runTest {
        val modelMock = mockk<GenerativeModel>()
        val chatMock = mockk<Chat>()
        val callResponse = mockk<GenerateContentResponse>()
        val finalResponse = mockk<GenerateContentResponse>()
        every { modelMock.startChat() } returns chatMock
        coEvery { chatMock.sendMessage(any<Content>()) } returns callResponse andThen finalResponse
        every { callResponse.functionCalls } returns listOf(
            FunctionCallPart("set_alarm", mapOf("hour" to "mañana", "minute" to "30"))
        )
        every { callResponse.text } returns null
        every { finalResponse.functionCalls } returns emptyList()
        every { finalResponse.text } returns "Listo."
        every { modelFactory.create(any(), any(), any()) } returns modelMock

        val result = repo.sendMessage("pon la alarma mañana")

        assertEquals("Listo.", result)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    // ===== M3: streamMessage captura IOException durante la RECOLECCIÓN del flujo =====

    @Test
    fun `streamMessage captura IOException durante la recoleccion y emite error sin propagar`() = runTest {
        val modelMock = mockk<GenerativeModel>()
        val chatMock = mockk<Chat>()
        every { modelMock.startChat() } returns chatMock
        coEvery { chatMock.sendMessageStream(any<String>()) } returns flow {
            throw java.io.IOException("red caída")
        }
        every { modelFactory.create(any(), any(), any()) } returns modelMock

        val values = repo.streamMessage("hola").toList()

        assertEquals(listOf("Error en streaming: red caída"), values)
    }
}
