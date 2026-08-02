package com.screenassistant.feature.chat

import com.screenassistant.core.domain.repository.GeminiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

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
    fun `sendMessage adds user message and assistant response`() {
        val fakeRepository = FakeGeminiRepository()
        val viewModel = ChatViewModel(
            geminiRepository = fakeRepository,
            ioDispatcher = testDispatcher
        )

        viewModel.onInputChanged("Hola")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        val messages = viewModel.messages.value
        assertEquals(2, messages.size)
        assertEquals("Hola", messages[0].text)
        assertTrue(messages[0].isFromUser)
        assertEquals("Respuesta de prueba", messages[1].text)
        assertFalse(messages[1].isFromUser)
    }

    @Test
    fun `sendMessage with blank input does nothing`() {
        val fakeRepository = FakeGeminiRepository()
        val viewModel = ChatViewModel(
            geminiRepository = fakeRepository,
            ioDispatcher = testDispatcher
        )

        viewModel.onInputChanged("   ")
        viewModel.sendMessage()

        assertTrue(viewModel.messages.value.isEmpty())
    }

    @Test
    fun `onInputChanged updates inputText`() {
        val fakeRepository = FakeGeminiRepository()
        val viewModel = ChatViewModel(
            geminiRepository = fakeRepository,
            ioDispatcher = testDispatcher
        )

        viewModel.onInputChanged("Prueba")

        assertEquals("Prueba", viewModel.inputText.value)
    }

    @Test
    fun `sendMessage changes isLoading correctly`() {
        val fakeRepository = FakeGeminiRepository()
        val viewModel = ChatViewModel(
            geminiRepository = fakeRepository,
            ioDispatcher = testDispatcher
        )

        viewModel.onInputChanged("Hola")
        viewModel.sendMessage()
        assertTrue(viewModel.isLoading.value)

        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `sendMessage handles error gracefully`() {
        val failingRepository = FakeGeminiRepositoryThatFails()
        val viewModel = ChatViewModel(
            geminiRepository = failingRepository,
            ioDispatcher = testDispatcher
        )

        viewModel.onInputChanged("Hola")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        val messages = viewModel.messages.value
        assertEquals(2, messages.size)
        assertTrue(messages[1].text.contains("Error"))
    }
}

class FakeGeminiRepository : GeminiRepository {
    override suspend fun sendMessage(message: String, image: com.screenassistant.core.domain.model.ImageData?): String? {
        return "Respuesta de prueba"
    }

    override fun streamMessage(message: String): Flow<String> {
        return flowOf("Respuesta de prueba")
    }

    override fun setLanguage(language: com.screenassistant.core.domain.model.AssistantLanguage) {
        // No-op: el fake no aplica el cambio de idioma
    }
}

class FakeGeminiRepositoryThatFails : GeminiRepository {
    override suspend fun sendMessage(message: String, image: com.screenassistant.core.domain.model.ImageData?): String? {
        throw java.io.IOException("Error simulado de red")
    }

    override fun streamMessage(message: String): Flow<String> {
        throw java.io.IOException("Error simulado de red")
    }

    override fun setLanguage(language: com.screenassistant.core.domain.model.AssistantLanguage) {
        // No-op: el fake no aplica el cambio de idioma
    }
}
