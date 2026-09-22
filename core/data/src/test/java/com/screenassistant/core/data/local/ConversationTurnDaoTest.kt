package com.screenassistant.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConversationTurnDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ConversationTurnDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = db.conversationTurnDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createTurn(
        id: String = "turn-1",
        userMessage: String = "Hola",
        assistantResponse: String = "Hola, ¿en qué puedo ayudarte?",
        timestamp: Long = 1000L,
        topic: String? = null,
        sentiment: String? = null
    ) = ConversationTurnEntity(
        id = id,
        userMessage = userMessage,
        assistantResponse = assistantResponse,
        timestamp = timestamp,
        topic = topic,
        sentiment = sentiment
    )

    @Test
    fun `insertTurn and getRecentTurns returns the turn`() = runTest {
        dao.insertTurn(createTurn())

        val turns = dao.getRecentTurns(10).first()

        assertEquals(1, turns.size)
        assertEquals("Hola", turns[0].userMessage)
    }

    @Test
    fun `getRecentTurns orders by timestamp descending`() = runTest {
        dao.insertTurn(createTurn(id = "old", timestamp = 1000L, userMessage = "Old"))
        dao.insertTurn(createTurn(id = "new", timestamp = 3000L, userMessage = "New"))
        dao.insertTurn(createTurn(id = "mid", timestamp = 2000L, userMessage = "Mid"))

        val turns = dao.getRecentTurns(10).first()

        assertEquals(3, turns.size)
        assertEquals("New", turns[0].userMessage)
        assertEquals("Mid", turns[1].userMessage)
        assertEquals("Old", turns[2].userMessage)
    }

    @Test
    fun `getRecentTurns respects limit`() = runTest {
        dao.insertTurn(createTurn(id = "1", timestamp = 1000L))
        dao.insertTurn(createTurn(id = "2", timestamp = 2000L))
        dao.insertTurn(createTurn(id = "3", timestamp = 3000L))

        val turns = dao.getRecentTurns(2).first()

        assertEquals(2, turns.size)
        assertEquals("3", turns[0].id)
        assertEquals("2", turns[1].id)
    }

    @Test
    fun `getRecentTurns returns empty when no turns exist`() = runTest {
        val turns = dao.getRecentTurns(10).first()

        assertTrue(turns.isEmpty())
    }

    @Test
    fun `searchByContent finds turns by userMessage`() = runTest {
        dao.insertTurn(createTurn(id = "t1", userMessage = "¿Qué tiempo hace?", assistantResponse = "Soleado"))
        dao.insertTurn(createTurn(id = "t2", userMessage = "Hola", assistantResponse = "Hola"))

        val results = dao.searchByContent("tiempo")

        assertEquals(1, results.size)
        assertEquals("¿Qué tiempo hace?", results[0].userMessage)
    }

    @Test
    fun `searchByContent finds turns by assistantResponse`() = runTest {
        dao.insertTurn(createTurn(id = "t1", userMessage = "Hola", assistantResponse = "Hola, ¿en qué puedo ayudarte?"))
        dao.insertTurn(createTurn(id = "t2", userMessage = "Adiós", assistantResponse = "Hasta luego"))

        val results = dao.searchByContent("ayudarte")

        assertEquals(1, results.size)
    }

    @Test
    fun `searchByContent is case-insensitive`() = runTest {
        dao.insertTurn(createTurn(userMessage = "Hola Mundo"))

        val results = dao.searchByContent("hola mundo")

        assertEquals(1, results.size)
    }

    @Test
    fun `searchByContent returns empty when no match`() = runTest {
        dao.insertTurn(createTurn(userMessage = "Hola"))

        val results = dao.searchByContent("noexiste")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchByTopic finds turns by topic`() = runTest {
        dao.insertTurn(createTurn(id = "t1", topic = "clima"))
        dao.insertTurn(createTurn(id = "t2", topic = "noticias"))
        dao.insertTurn(createTurn(id = "t3", topic = "clima"))

        val results = dao.searchByTopic("clima")

        assertEquals(2, results.size)
    }

    @Test
    fun `searchByTopic returns empty when no match`() = runTest {
        dao.insertTurn(createTurn(topic = "clima"))

        val results = dao.searchByTopic("deportes")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `getTurnsBetween returns turns in range`() = runTest {
        dao.insertTurn(createTurn(id = "1", timestamp = 1000L))
        dao.insertTurn(createTurn(id = "2", timestamp = 2000L))
        dao.insertTurn(createTurn(id = "3", timestamp = 3000L))
        dao.insertTurn(createTurn(id = "4", timestamp = 4000L))

        val results = dao.getTurnsBetween(1500L, 3500L)

        assertEquals(2, results.size)
        assertEquals("2", results[0].id)
        assertEquals("3", results[1].id)
    }

    @Test
    fun `getTurnsBetween orders by timestamp ascending`() = runTest {
        dao.insertTurn(createTurn(id = "3", timestamp = 3000L))
        dao.insertTurn(createTurn(id = "1", timestamp = 1000L))
        dao.insertTurn(createTurn(id = "2", timestamp = 2000L))

        val results = dao.getTurnsBetween(500L, 3500L)

        assertEquals(3, results.size)
        assertEquals("1", results[0].id)
        assertEquals("2", results[1].id)
        assertEquals("3", results[2].id)
    }

    @Test
    fun `deleteOldTurns removes turns before timestamp`() = runTest {
        dao.insertTurn(createTurn(id = "old", timestamp = 1000L))
        dao.insertTurn(createTurn(id = "keep", timestamp = 3000L))

        dao.deleteOldTurns(2000L)

        val remaining = dao.getRecentTurns(10).first()
        assertEquals(1, remaining.size)
        assertEquals("keep", remaining[0].id)
    }

    @Test
    fun `deleteOldTurns does not remove turns at exactly the boundary`() = runTest {
        dao.insertTurn(createTurn(id = "exact", timestamp = 2000L))

        dao.deleteOldTurns(2000L)

        val remaining = dao.getRecentTurns(10).first()
        assertEquals(1, remaining.size)
    }

    @Test
    fun `insertTurn with replace strategy updates existing turn`() = runTest {
        dao.insertTurn(createTurn(id = "turn-1", userMessage = "Old"))
        dao.insertTurn(createTurn(id = "turn-1", userMessage = "New"))

        val turns = dao.getRecentTurns(10).first()

        assertEquals(1, turns.size)
        assertEquals("New", turns[0].userMessage)
    }
}
