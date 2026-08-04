package com.screenassistant.feature.chat

import com.screenassistant.core.domain.model.AssistantLanguage
import com.screenassistant.core.domain.model.ImageData
import com.screenassistant.core.domain.repository.GeminiRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ChatViewModel con MockK (M26 — regla 6 del equipo: sin fakes manuales; los
 * antiguos FakeGeminiRepository/FakeGeminiRepositoryThatFails se migraron a
 * stubs de mockk). GeminiRepository se mockea y el flujo se verifica con
 * coVerify; el dispatcher principal es el testDispatcher del test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var geminiRepository: GeminiRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        geminiRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ChatViewModel(
        geminiRepository = geminiRepository,
        ioDispatcher = testDispatcher
    )

    @Test
    fun `sendMessage adds user message and assistant response`() {
        coEvery { geminiRepository.sendMessage("Hola", null) } returns "Respuesta de prueba"

        val viewModel = createViewModel()
        viewModel.onInputChanged("Hola")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        val messages = viewModel.messages.value
        assertEquals(2, messages.size)
        assertEquals("Hola", messages[0].text)
        assertTrue(messages[0].isFromUser)
        assertEquals("Respuesta de prueba", messages[1].text)
        assertFalse(messages[1].isFromUser)
        coVerify(exactly = 1) { geminiRepository.sendMessage("Hola", null) }
    }

    @Test
    fun `sendMessage with blank input does nothing`() {
        val viewModel = createViewModel()
        viewModel.onInputChanged("   ")
        viewModel.sendMessage()

        assertTrue(viewModel.messages.value.isEmpty())
        coVerify(exactly = 0) { geminiRepository.sendMessage(any(), any()) }
    }

    @Test
    fun `onInputChanged updates inputText`() {
        val viewModel = createViewModel()
        viewModel.onInputChanged("Prueba")

        assertEquals("Prueba", viewModel.inputText.value)
    }

    @Test
    fun `sendMessage changes isLoading correctly`() {
        coEvery { geminiRepository.sendMessage("Hola", null) } returns "Respuesta de prueba"

        val viewModel = createViewModel()
        viewModel.onInputChanged("Hola")
        viewModel.sendMessage()
        assertTrue(viewModel.isLoading.value)

        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `sendMessage handles error gracefully`() {
        coEvery { geminiRepository.sendMessage(any(), any()) } throws java.io.IOException("Error simulado de red")

        val viewModel = createViewModel()
        viewModel.onInputChanged("Hola")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        val messages = viewModel.messages.value
        assertEquals(2, messages.size)
        assertTrue(messages[1].text.contains("Error"))
    }
}
