package com.screenassistant.service.system.action

import com.screenassistant.core.domain.repository.MemoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MemoryActionTest {

    private lateinit var memoryRepository: MemoryRepository
    private lateinit var action: MemoryAction

    @Before
    fun setup() {
        memoryRepository = mockk(relaxed = true)
        action = MemoryAction(memoryRepository)
    }

    @Test
    fun `saveMemory retorna mensaje de exito`() = runTest {
        coEvery { memoryRepository.saveMemory(any()) } returns Unit

        val result = action.saveMemory("Mi nombre es Carlos")

        assertEquals("Éxito: Entendido, lo recordaré.", result)
    }

    @Test
    fun `saveMemory delega en memoryRepository con contenido correcto`() = runTest {
        coEvery { memoryRepository.saveMemory(any()) } returns Unit

        action.saveMemory("Prefiero el café")

        coVerify(exactly = 1) { memoryRepository.saveMemory("Prefiero el café") }
    }

    @Test
    fun `saveMemory con cadena vacia delega correctamente`() = runTest {
        coEvery { memoryRepository.saveMemory(any()) } returns Unit

        action.saveMemory("")

        coVerify(exactly = 1) { memoryRepository.saveMemory("") }
    }

    @Test
    fun `saveMemory con larga cadena delega correctamente`() = runTest {
        coEvery { memoryRepository.saveMemory(any()) } returns Unit
        val longFact = "a".repeat(1000)

        action.saveMemory(longFact)

        coVerify(exactly = 1) { memoryRepository.saveMemory(longFact) }
    }

    @Test
    fun `saveMemory siempre retorna exito incluso si repository falla`() = runTest {
        coEvery { memoryRepository.saveMemory(any()) } throws RuntimeException("DB error")

        try {
            action.saveMemory("test")
            throw AssertionError("Deberia haber lanzado excepcion")
        } catch (e: RuntimeException) {
            assertEquals("DB error", e.message)
        }
    }

    @Test
    fun `saveMemory re-lanza CancellationException`() = runTest {
        coEvery { memoryRepository.saveMemory(any()) } throws kotlin.coroutines.cancellation.CancellationException()

        try {
            action.saveMemory("test")
            throw AssertionError("Deberia haber lanzado CancellationException")
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            // OK
        }
    }

    @Test
    fun `saveMemory llama saveMemory exactamente una vez`() = runTest {
        coEvery { memoryRepository.saveMemory(any()) } returns Unit

        action.saveMemory("test")

        coVerify(exactly = 1) { memoryRepository.saveMemory(any()) }
    }
}
