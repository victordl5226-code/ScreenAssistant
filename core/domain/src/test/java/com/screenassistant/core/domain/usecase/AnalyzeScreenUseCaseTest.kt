package com.screenassistant.core.domain.usecase

import com.screenassistant.core.domain.model.AssistantLanguage
import com.screenassistant.core.domain.model.ImageData
import com.screenassistant.core.domain.repository.GeminiRepository
import com.screenassistant.core.domain.repository.ScreenContextRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnalyzeScreenUseCaseTest {

    private lateinit var screenContextRepository: ScreenContextRepository
    private lateinit var geminiRepository: GeminiRepository
    private lateinit var promptBuilder: ScreenAnalysisPromptBuilder
    private lateinit var useCase: AnalyzeScreenUseCase

    private val screenTextFlow = MutableStateFlow("")

    @Before
    fun setup() {
        screenContextRepository = mockk()
        geminiRepository = mockk()
        promptBuilder = ScreenAnalysisPromptBuilder(AssistantLanguage.SPANISH)
        useCase = AnalyzeScreenUseCase(
            screenContextRepository = screenContextRepository,
            geminiRepository = geminiRepository,
            promptBuilder = promptBuilder,
            ioDispatcher = Dispatchers.Unconfined // Test dispatcher - runs immediately
        )

        every { screenContextRepository.screenText } returns screenTextFlow
        coEvery { screenContextRepository.captureScreenshot() } returns null
        coEvery { geminiRepository.sendMessage(any(), any()) } returns null
    }

    @Test
    fun `invoke screenshotOk textoOk devuelve respuesta de Gemini`() = runBlocking {
        val fakeScreenshot = mockk<ImageData>()
        coEvery { screenContextRepository.captureScreenshot() } returns fakeScreenshot
        screenTextFlow.value = "Texto de prueba"
        coEvery { geminiRepository.sendMessage(any(), any()) } returns "Esta es la respuesta"

        val result = useCase("¿Qué veo?")

        assertTrue(result.isSuccess)
        assertEquals("Esta es la respuesta", result.getOrNull())
    }

    @Test
    fun `invoke screenshotNull textoOk responde solo con texto`() = runBlocking {
        screenTextFlow.value = "Hola"
        coEvery { geminiRepository.sendMessage(any(), any()) } returns "RespuestaGemini"

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals("RespuestaGemini", result.getOrNull())
        coVerify { geminiRepository.sendMessage(any(), null) }
    }

    @Test
    fun `invoke GeminiReturnsNull muestra error de servicio`() = runBlocking {
        screenTextFlow.value = "Texto"
        coEvery { geminiRepository.sendMessage(any(), any()) } returns null

        val result = useCase()

        assertTrue(result.isFailure)
        assertEquals("No se pudo obtener respuesta del asistente", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke excepcion de red muestra error de conexion`() = runBlocking {
        coEvery { geminiRepository.sendMessage(any(), any()) } throws RuntimeException("Sin conexión")

        val result = useCase()

        assertTrue(result.isFailure)
        assertEquals("Sin conexión", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke verify que prompt se construye correctamente`() = runBlocking {
        screenTextFlow.value = "Pantalla Android"
        coEvery { geminiRepository.sendMessage(any(), any()) } returns "ok"

        useCase("¿Qué hay?")

        coVerify {
            geminiRepository.sendMessage(
                match { it.contains("Analiza visualmente esta pantalla de Android") && it.contains("Pantalla Android") && it.contains("¿Qué hay?") },
                any()
            )
        }
    }
}
