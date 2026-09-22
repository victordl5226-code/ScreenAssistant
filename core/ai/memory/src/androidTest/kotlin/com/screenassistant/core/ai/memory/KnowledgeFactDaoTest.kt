package com.screenassistant.core.ai.memory

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KnowledgeFactDaoTest {

    private lateinit var db: MemoryDatabase
    private lateinit var dao: KnowledgeFactDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MemoryDatabase::class.java,
        ).build()
        dao = db.knowledgeFactDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun fakeFact(
        subject: String = " usuario",
        predicate: String = "prefiere",
        obj: String = "azul",
        category: String = "PREFERENCE",
    ) = KnowledgeFactEntity(
        id = java.util.UUID.randomUUID().toString(),
        category = category,
        subject = subject,
        predicate = predicate,
        `object` = obj,
        confidence = 0.9f,
        timestamp = System.currentTimeMillis(),
    )

    @Test
    fun insert_and_getByCategory() = runTest {
        dao.insert(fakeFact(category = "PREFERENCE"))
        dao.insert(fakeFact(category = "CONTACT"))
        val prefs = dao.getByCategory("PREFERENCE")
        assertEquals(1, prefs.size)
        assertEquals("PREFERENCE", prefs[0].category)
    }

    @Test
    fun getBySubject_finds_matching_facts() = runTest {
        dao.insert(fakeFact(subject = "Juan"))
        dao.insert(fakeFact(subject = "Maria"))
        val results = dao.getBySubject("Juan")
        assertEquals(1, results.size)
        assertTrue(results[0].subject.contains("Juan"))
    }

    @Test
    fun insertAll_inserts_multiple_facts() = runTest {
        val facts = listOf(fakeFact("a"), fakeFact("b"), fakeFact("c"))
        dao.insertAll(facts)
        assertEquals(3, dao.count())
    }

    @Test
    fun count_returns_correct_number() = runTest {
        assertEquals(0, dao.count())
        dao.insert(fakeFact())
        assertEquals(1, dao.count())
    }

    @Test
    fun delete_removes_specific_fact() = runTest {
        val fact = fakeFact()
        dao.insert(fact)
        assertEquals(1, dao.count())
        dao.delete(fact.id)
        assertEquals(0, dao.count())
    }

    @Test
    fun clear_removes_all_facts() = runTest {
        dao.insert(fakeFact("a"))
        dao.insert(fakeFact("b"))
        dao.clear()
        assertEquals(0, dao.count())
    }
}
