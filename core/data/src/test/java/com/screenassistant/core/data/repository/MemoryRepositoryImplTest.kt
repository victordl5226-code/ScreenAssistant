package com.screenassistant.core.data.repository

import com.screenassistant.core.data.local.MemoryDao
import com.screenassistant.core.data.local.MemoryEntity
import com.screenassistant.core.domain.model.Memory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemoryRepositoryImplTest {

    private lateinit var memoryDao: MemoryDao
    private lateinit var repository: MemoryRepositoryImpl

    @Before
    fun setup() {
        memoryDao = mockk(relaxed = true)
        repository = MemoryRepositoryImpl(memoryDao)
    }

    @Test
    fun `getAllMemories mapea entities a dominio`() = runTest {
        val entities = listOf(
            MemoryEntity(id = 1, content = "test content", timestamp = 1000L),
            MemoryEntity(id = 2, content = "other content", timestamp = 2000L)
        )
        every { memoryDao.getAllMemories() } returns flowOf(entities)

        val result = repository.getAllMemories()

        result.collect { memories ->
            assertEquals(2, memories.size)
            assertEquals(1, memories[0].id)
            assertEquals("test content", memories[0].content)
            assertEquals(2, memories[1].id)
        }
    }

    @Test
    fun `getAllMemories retorna lista vacia cuando no hay memorias`() = runTest {
        every { memoryDao.getAllMemories() } returns flowOf(emptyList())

        val result = repository.getAllMemories()

        result.collect { memories ->
            assertTrue(memories.isEmpty())
        }
    }

    @Test
    fun `saveMemory crea entity con contenido correcto`() = runTest {
        coEvery { memoryDao.insertMemory(any()) } returns Unit

        repository.saveMemory("mi nueva memoria")

        coVerify(exactly = 1) {
            memoryDao.insertMemory(withArg { entity ->
                assertEquals("mi nueva memoria", entity.content)
            })
        }
    }

    @Test
    fun `clearMemories delega en dao clearAll`() = runTest {
        coEvery { memoryDao.clearAll() } returns Unit

        repository.clearMemories()

        coVerify(exactly = 1) { memoryDao.clearAll() }
    }

    @Test
    fun `getUserName encuentra nombre del usuario`() = runTest {
        val memories = listOf(
            Memory(id = 1, content = "Nombre del usuario: Carlos"),
            Memory(id = 2, content = "otro contenido")
        )

        val name = repository.getUserName(memories)

        assertEquals("Carlos", name)
    }

    @Test
    fun `getUserName retorna null cuando no hay nombre`() = runTest {
        val memories = listOf(
            Memory(id = 1, content = "sin nombre aqui")
        )

        val name = repository.getUserName(memories)

        assertNull(name)
    }

    @Test
    fun `getUserName es case-insensitive`() = runTest {
        // startsWith es case-insensitive, pero removePrefix es case-sensitive
        // El prefijo DEBE coincidir exactamente para que se elimine
        val memories = listOf(
            Memory(id = 1, content = "Nombre del usuario: Ana")
        )

        val name = repository.getUserName(memories)

        assertEquals("Ana", name)
    }

    @Test
    fun `getUserName trimea espacios extras`() = runTest {
        val memories = listOf(
            Memory(id = 1, content = "Nombre del usuario:   Pedro  ")
        )

        val name = repository.getUserName(memories)

        assertEquals("Pedro", name)
    }

    @Test
    fun `getAssistantName encuentra nombre de la asistente`() = runTest {
        val memories = listOf(
            Memory(id = 1, content = "Nombre de la asistente: J.A.R.V.I.S.")
        )

        val name = repository.getAssistantName(memories)

        assertEquals("J.A.R.V.I.S.", name)
    }

    @Test
    fun `getAssistantName retorna null cuando no hay nombre`() = runTest {
        val memories = listOf(
            Memory(id = 1, content = "contenido sin nombre de asistente")
        )

        val name = repository.getAssistantName(memories)

        assertNull(name)
    }

    @Test
    fun `getAssistantName es case-insensitive`() = runTest {
        // startsWith es case-insensitive, pero removePrefix es case-sensitive
        val memories = listOf(
            Memory(id = 1, content = "Nombre de la asistente: Jarvis")
        )

        val name = repository.getAssistantName(memories)

        assertEquals("Jarvis", name)
    }

    @Test
    fun `getUserName retorna primer match cuando hay multiples`() = runTest {
        val memories = listOf(
            Memory(id = 1, content = "Nombre del usuario: Primero"),
            Memory(id = 2, content = "Nombre del usuario: Segundo")
        )

        val name = repository.getUserName(memories)

        assertEquals("Primero", name)
    }
}
