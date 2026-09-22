package com.screenassistant.core.data.repository

import com.screenassistant.core.data.local.ConversationTurnDao
import com.screenassistant.core.data.local.ConversationTurnEntity
import com.screenassistant.core.domain.model.personality.ConversationTurn
import com.screenassistant.core.domain.model.proactive.ScreenInfo
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
import java.time.Instant

class ConversationMemoryRepositoryImplTest {

    private lateinit var dao: ConversationTurnDao
    private lateinit var repository: ConversationMemoryRepositoryImpl

    private fun testEntity(
        id: String = "turn-1",
        userMessage: String = "Hola",
        assistantResponse: String = "Hola, como estas?",
        timestamp: Long = 1000L,
        screenContextJson: String? = null,
        topic: String? = null,
        sentiment: String? = null
    ) = ConversationTurnEntity(
        id = id, userMessage = userMessage, assistantResponse = assistantResponse,
        timestamp = timestamp, screenContextJson = screenContextJson, topic = topic,
        sentiment = sentiment
    )

    private fun testTurn(
        id: String = "turn-1",
        userMessage: String = "Hola",
        assistantResponse: String = "Hola, como estas?",
        timestamp: Long = 1000L,
        screenContext: ScreenInfo? = null,
        topic: String? = null,
        sentiment: ConversationTurn.Sentiment? = null
    ) = ConversationTurn(
        id = id, userMessage = userMessage, assistantResponse = assistantResponse,
        timestamp = Instant.ofEpochMilli(timestamp), screenContext = screenContext,
        topic = topic, sentiment = sentiment
    )

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        repository = ConversationMemoryRepositoryImpl(dao)
    }

    @Test
    fun `getRecentTurns mapea entities a dominio`() = runTest {
        val entities = listOf(
            testEntity(id = "t1", userMessage = "msg1", assistantResponse = "resp1"),
            testEntity(id = "t2", userMessage = "msg2", assistantResponse = "resp2")
        )
        every { dao.getRecentTurns(10) } returns flowOf(entities)

        repository.getRecentTurns(10).collect { turns ->
            assertEquals(2, turns.size)
            assertEquals("msg1", turns[0].userMessage)
            assertEquals("resp2", turns[1].assistantResponse)
        }
    }

    @Test
    fun `getRecentTurns retorna lista vacia`() = runTest {
        every { dao.getRecentTurns(10) } returns flowOf(emptyList())

        repository.getRecentTurns(10).collect { turns ->
            assertTrue(turns.isEmpty())
        }
    }

    @Test
    fun `saveTurn delega en dao con entity correcta`() = runTest {
        coEvery { dao.insertTurn(any()) } returns Unit
        val turn = testTurn(userMessage = "Hola", assistantResponse = "Hi!")

        repository.saveTurn(turn)

        coVerify(exactly = 1) {
            dao.insertTurn(withArg { entity ->
                assertEquals("Hola", entity.userMessage)
                assertEquals("Hi!", entity.assistantResponse)
            })
        }
    }

    @Test
    fun `saveTurn genera UUID si id esta en blanco`() = runTest {
        coEvery { dao.insertTurn(any()) } returns Unit
        val turn = testTurn(id = "")

        repository.saveTurn(turn)

        coVerify(exactly = 1) {
            dao.insertTurn(withArg { entity ->
                assertTrue(entity.id.isNotBlank())
            })
        }
    }

    @Test
    fun `searchByContent delega en dao`() = runTest {
        val entities = listOf(testEntity(userMessage = "buscado"))
        coEvery { dao.searchByContent("buscado") } returns entities

        val result = repository.searchByContent("buscado")

        assertEquals(1, result.size)
        assertEquals("buscado", result[0].userMessage)
    }

    @Test
    fun `searchByTopic delega en dao`() = runTest {
        val entities = listOf(testEntity(topic = "tecnologia"))
        coEvery { dao.searchByTopic("tecnologia") } returns entities

        val result = repository.searchByTopic("tecnologia")

        assertEquals(1, result.size)
        assertEquals("tecnologia", result[0].topic)
    }

    @Test
    fun `getTurnsBetween delega en dao con rangos correctos`() = runTest {
        val entities = listOf(testEntity(timestamp = 1500L))
        coEvery { dao.getTurnsBetween(1000L, 2000L) } returns entities

        val result = repository.getTurnsBetween(1000L, 2000L)

        assertEquals(1, result.size)
        assertEquals(1500L, result[0].timestamp.toEpochMilli())
    }

    @Test
    fun `clearOldTurns calcula cutoff correcto`() = runTest {
        coEvery { dao.deleteOldTurns(any()) } returns Unit

        repository.clearOldTurns(7)

        coVerify(exactly = 1) {
            dao.deleteOldTurns(withArg { cutoff ->
                val now = System.currentTimeMillis()
                val diff = now - cutoff
                assertTrue(diff in 7L * 86_400_000L - 1000L..7L * 86_400_000L + 1000L)
            })
        }
    }

    @Test
    fun `fromDomain serializa sentiment correctamente`() = runTest {
        coEvery { dao.insertTurn(any()) } returns Unit
        val turn = testTurn(sentiment = ConversationTurn.Sentiment.POSITIVE)

        repository.saveTurn(turn)

        coVerify {
            dao.insertTurn(withArg { entity ->
                assertEquals("POSITIVE", entity.sentiment)
            })
        }
    }

    @Test
    fun `fromDomain serializa screenContext null como null`() = runTest {
        coEvery { dao.insertTurn(any()) } returns Unit
        val turn = testTurn(screenContext = null)

        repository.saveTurn(turn)

        coVerify {
            dao.insertTurn(withArg { entity ->
                assertNull(entity.screenContextJson)
            })
        }
    }

    @Test
    fun `fromDomain serializa screenContext con packageName`() = runTest {
        coEvery { dao.insertTurn(any()) } returns Unit
        val turn = testTurn(screenContext = ScreenInfo(packageName = "com.test.app", text = "Hello"))

        repository.saveTurn(turn)

        coVerify {
            dao.insertTurn(withArg { entity ->
                assertTrue(entity.screenContextJson?.contains("com.test.app") ?: false)
                assertTrue(entity.screenContextJson?.contains("Hello") ?: false)
            })
        }
    }

    @Test
    fun `fromDomain serializa screenContext sin packageName`() = runTest {
        coEvery { dao.insertTurn(any()) } returns Unit
        val turn = testTurn(screenContext = ScreenInfo(packageName = null, text = "text"))

        repository.saveTurn(turn)

        coVerify {
            dao.insertTurn(withArg { entity ->
                assertTrue(entity.screenContextJson?.contains("null") ?: false)
            })
        }
    }
}
