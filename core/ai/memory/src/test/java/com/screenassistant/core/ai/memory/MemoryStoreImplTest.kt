package com.screenassistant.core.ai.memory

import com.screenassistant.core.domain.repository.ai.FactCategory
import com.screenassistant.core.domain.repository.ai.KnowledgeFact
import com.screenassistant.core.domain.repository.ai.MemoryEntry
import com.screenassistant.core.domain.repository.ai.MemoryRole
import com.screenassistant.core.domain.repository.ai.MemoryType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemoryStoreImplTest {

    private lateinit var longTermMemoryDao: LongTermMemoryDao
    private lateinit var knowledgeFactDao: KnowledgeFactDao
    private lateinit var store: MemoryStoreImpl

    private fun testMemoryEntity(
        id: String = "mem-1",
        role: String = "USER",
        content: String = "test content",
        timestamp: Long = 1000L,
        type: String = "CONVERSATION"
    ) = LongTermMemoryEntity(
        id = id, role = role, content = content, timestamp = timestamp, type = type
    )

    private fun testFactEntity(
        id: String = "fact-1",
        category: String = "PREFERENCE",
        subject: String = "user",
        predicate: String = "likes",
        obj: String = "coffee",
        confidence: Float = 0.8f,
        timestamp: Long = 2000L,
        source: String = "conversation"
    ) = KnowledgeFactEntity(
        id = id, category = category, subject = subject, predicate = predicate,
        `object` = obj, confidence = confidence, timestamp = timestamp, source = source
    )

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0
        longTermMemoryDao = mockk(relaxed = true)
        knowledgeFactDao = mockk(relaxed = true)
        store = MemoryStoreImpl(longTermMemoryDao, knowledgeFactDao)
    }

    @Test
    fun `save crea entity y delega en dao`() = runTest {
        coEvery { longTermMemoryDao.insert(any()) } returns Unit
        val entry = MemoryEntry(
            id = "e1", role = MemoryRole.USER, content = "Hello",
            timestamp = 1000L, type = MemoryType.CONVERSATION
        )

        store.save(entry)

        coVerify(exactly = 1) {
            longTermMemoryDao.insert(withArg { entity ->
                assertEquals("e1", entity.id)
                assertEquals("USER", entity.role)
                assertEquals("Hello", entity.content)
                assertEquals(1000L, entity.timestamp)
                assertEquals("CONVERSATION", entity.type)
            })
        }
    }

    @Test
    fun `getRecent mapea entities a MemoryEntry`() = runTest {
        val entities = listOf(
            testMemoryEntity(id = "m1", role = "USER", content = "Hi"),
            testMemoryEntity(id = "m2", role = "ASSISTANT", content = "Hello!")
        )
        coEvery { longTermMemoryDao.getRecent(10) } returns entities

        val result = store.getRecent(10)

        assertEquals(2, result.size)
        assertEquals("m1", result[0].id)
        assertEquals(MemoryRole.USER, result[0].role)
        assertEquals("Hi", result[0].content)
        assertEquals(MemoryRole.ASSISTANT, result[1].role)
    }

    @Test
    fun `getRecent usa valor default limit 20`() = runTest {
        coEvery { longTermMemoryDao.getRecent(20) } returns emptyList()

        val result = store.getRecent()

        assertEquals(0, result.size)
    }

    @Test
    fun `getRecent maneja rol invalido con default USER`() = runTest {
        val entities = listOf(testMemoryEntity(role = "INVALID_ROLE"))
        coEvery { longTermMemoryDao.getRecent(10) } returns entities

        val result = store.getRecent(10)

        assertEquals(MemoryRole.USER, result[0].role)
    }

    @Test
    fun `getRecent maneja tipo invalido con default CONVERSATION`() = runTest {
        val entities = listOf(testMemoryEntity(type = "INVALID_TYPE"))
        coEvery { longTermMemoryDao.getRecent(10) } returns entities

        val result = store.getRecent(10)

        assertEquals(MemoryType.CONVERSATION, result[0].type)
    }

    @Test
    fun `search mergea resultados de ambos dao`() = runTest {
        val memEntities = listOf(testMemoryEntity(id = "mem1", content = "test query"))
        val factEntities = listOf(testFactEntity(id = "fact1", subject = "query"))
        coEvery { longTermMemoryDao.search("query", 10) } returns memEntities
        coEvery { knowledgeFactDao.searchByQuery("query", 10) } returns factEntities

        val result = store.search("query", 10)

        assertEquals(2, result.size)
    }

    @Test
    fun `search ordena por timestamp descendente y limita`() = runTest {
        val memEntities = listOf(
            testMemoryEntity(id = "old", timestamp = 100L),
            testMemoryEntity(id = "new", timestamp = 500L)
        )
        val factEntities = listOf(
            testFactEntity(id = "fact-old", timestamp = 200L),
            testFactEntity(id = "fact-new", timestamp = 600L)
        )
        coEvery { longTermMemoryDao.search("q", 2) } returns memEntities
        coEvery { knowledgeFactDao.searchByQuery("q", 2) } returns factEntities

        val result = store.search("q", 2)

        assertEquals(2, result.size)
        assertEquals(600L, result[0].timestamp)
        assertEquals(500L, result[1].timestamp)
    }

    @Test
    fun `search construye contenido de fact correctamente`() = runTest {
        val factEntities = listOf(
            testFactEntity(subject = "user", predicate = "likes", obj = "coffee")
        )
        coEvery { longTermMemoryDao.search("coffee", 10) } returns emptyList()
        coEvery { knowledgeFactDao.searchByQuery("coffee", 10) } returns factEntities

        val result = store.search("coffee", 10)

        assertEquals(1, result.size)
        assertEquals("user: likes = coffee", result[0].content)
        assertEquals(MemoryType.FACT, result[0].type)
    }

    @Test
    fun `count suma ambos dao`() = runTest {
        coEvery { longTermMemoryDao.count() } returns 5
        coEvery { knowledgeFactDao.count() } returns 3

        val result = store.count()

        assertEquals(8, result)
    }

    @Test
    fun `deleteOlderThan delega en longTermMemoryDao`() = runTest {
        coEvery { longTermMemoryDao.deleteOlderThan(1000L) } returns 7

        val result = store.deleteOlderThan(1000L)

        assertEquals(7, result)
    }

    @Test
    fun `saveFact crea entity y delega en knowledgeFactDao`() = runTest {
        coEvery { knowledgeFactDao.insert(any()) } returns Unit
        val fact = KnowledgeFact(
            id = "kf1", category = FactCategory.PREFERENCE, subject = "user",
            predicate = "likes", `object` = "tea", confidence = 0.9f,
            timestamp = 3000L, source = "chat"
        )

        store.saveFact(fact)

        coVerify(exactly = 1) {
            knowledgeFactDao.insert(withArg { entity ->
                assertEquals("kf1", entity.id)
                assertEquals("PREFERENCE", entity.category)
                assertEquals("user", entity.subject)
                assertEquals("tea", entity.`object`)
                assertEquals(0.9f, entity.confidence)
            })
        }
    }

    @Test
    fun `getRecentFacts mapea entities a KnowledgeFact`() = runTest {
        val entities = listOf(
            testFactEntity(id = "f1", category = "CONTACT", subject = "name", obj = "Carlos")
        )
        coEvery { knowledgeFactDao.getRecent(10) } returns entities

        val result = store.getRecentFacts(10)

        assertEquals(1, result.size)
        assertEquals("f1", result[0].id)
        assertEquals(FactCategory.CONTACT, result[0].category)
        assertEquals("Carlos", result[0].`object`)
    }

    @Test
    fun `getRecentFacts maneja categoria invalida con default ROUTINE`() = runTest {
        val entities = listOf(testFactEntity(category = "INVALID_CAT"))
        coEvery { knowledgeFactDao.getRecent(10) } returns entities

        val result = store.getRecentFacts(10)

        assertEquals(FactCategory.ROUTINE, result[0].category)
    }

    @Test
    fun `clear limpia ambos dao`() = runTest {
        coEvery { longTermMemoryDao.clear() } returns Unit
        coEvery { knowledgeFactDao.clear() } returns Unit

        store.clear()

        coVerify(exactly = 1) { longTermMemoryDao.clear() }
        coVerify(exactly = 1) { knowledgeFactDao.clear() }
    }
}
