package com.screenassistant.core.domain.usecase

import com.screenassistant.core.domain.action.SystemAction
import com.screenassistant.core.domain.model.ActionResult
import com.screenassistant.core.domain.model.ChatMessage
import com.screenassistant.core.domain.model.Memory
import com.screenassistant.core.domain.model.SystemCommand
import com.screenassistant.core.domain.repository.ConversationRepository
import com.screenassistant.core.domain.repository.GeminiRepository
import com.screenassistant.core.domain.repository.MemoryRepository
import com.screenassistant.core.domain.repository.ScreenContextRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SendMessageUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `invoke with offline command returns CommandExecuted`() = runTest {
        // Crear un commandParser que devuelva un resultado para "llama a Juan"
        val commandParser = object : SystemCommandParser(mockSystemAction()) {
            override fun parse(text: String): String? {
                return if (text.contains("llama")) "Llamando a Juan..." else null
            }
        }

        // Mock repos
        val geminiRepo = FakeGeminiRepository()
        val memoryRepo = FakeMemoryRepository()
        val conversationRepo = FakeConversationRepository()
        val screenContextRepo = FakeScreenContextRepository()

        val useCase = SendMessageUseCase(
            geminiRepository = geminiRepo,
            memoryRepository = memoryRepo,
            conversationRepository = conversationRepo,
            screenContextRepository = screenContextRepo,
            systemAction = mockSystemAction(),
            ioDispatcher = testDispatcher
        )

        val result = useCase(
            text = "llama a Juan",
            commandParser = commandParser,
            isNetworkAvailable = false
        )

        assertTrue(result is SendMessageResult.CommandExecuted)
        assertTrue((result as SendMessageResult.CommandExecuted).result is ActionResult.Success)
    }

    @Test
    fun `invoke without network and without direct command returns Error`() = runTest {
        val commandParser = object : SystemCommandParser(mockSystemAction()) {
            override fun parse(text: String): String? = null
        }

        val useCase = SendMessageUseCase(
            geminiRepository = FakeGeminiRepository(),
            memoryRepository = FakeMemoryRepository(),
            conversationRepository = FakeConversationRepository(),
            screenContextRepository = FakeScreenContextRepository(),
            systemAction = mockSystemAction(),
            ioDispatcher = testDispatcher
        )

        val result = useCase(
            text = "Hola",
            commandParser = commandParser,
            isNetworkAvailable = false
        )

        assertTrue(result is SendMessageResult.Error)
    }

    @Test
    fun `invoke with network sends to AI and returns AiResponse`() = runTest {
        val commandParser = object : SystemCommandParser(mockSystemAction()) {
            override fun parse(text: String): String? = null
        }

        val geminiRepo = FakeGeminiRepository(response = "¡Hola! ¿En qué puedo ayudarte?")
        val conversationRepo = FakeConversationRepository()

        val useCase = SendMessageUseCase(
            geminiRepository = geminiRepo,
            memoryRepository = FakeMemoryRepository(),
            conversationRepository = conversationRepo,
            screenContextRepository = FakeScreenContextRepository(),
            systemAction = mockSystemAction(),
            ioDispatcher = testDispatcher
        )

        val result = useCase(
            text = "Hola",
            commandParser = commandParser,
            isNetworkAvailable = true
        )

        assertTrue(result is SendMessageResult.AiResponse)
        assertEquals("¡Hola! ¿En qué puedo ayudarte?", (result as SendMessageResult.AiResponse).text)
    }
}

// Fakes
class FakeGeminiRepository(private val response: String? = "Respuesta de prueba") : GeminiRepository {
    override suspend fun sendMessage(message: String, image: com.screenassistant.core.domain.model.ImageData?): String? = response
    override fun streamMessage(message: String): Flow<String> = flowOf(response ?: "")

    override fun setLanguage(language: com.screenassistant.core.domain.model.AssistantLanguage) {
        // No-op: el fake no aplica el cambio de idioma
    }
}

class FakeMemoryRepository : MemoryRepository {
    private val _memories = MutableStateFlow<List<Memory>>(emptyList())
    override fun getAllMemories(): Flow<List<Memory>> = _memories
    override suspend fun saveMemory(content: String) {}
    override suspend fun clearMemories() {}
    override suspend fun getUserName(memories: List<Memory>): String? = null
    override suspend fun getAssistantName(memories: List<Memory>): String? = null
}

class FakeConversationRepository : ConversationRepository {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    override val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    override suspend fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }
    override suspend fun clearConversation() {
        _messages.value = emptyList()
    }
}

class FakeScreenContextRepository : ScreenContextRepository {
    private val _screenText = MutableStateFlow("")
    override val screenText: StateFlow<String> = _screenText.asStateFlow()
    override suspend fun captureScreenshot(): com.screenassistant.core.domain.model.ImageData? = null
    override fun updateScreenText(text: String) { _screenText.value = text }
}

fun mockSystemAction(): SystemAction = object : SystemAction {
    override suspend fun execute(command: SystemCommand): ActionResult {
        return ActionResult.Success("Comando ejecutado")
    }
}
