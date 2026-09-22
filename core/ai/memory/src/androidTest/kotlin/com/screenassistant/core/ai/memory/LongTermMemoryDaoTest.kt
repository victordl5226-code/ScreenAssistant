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
class LongTermMemoryDaoTest {

    private lateinit var db: MemoryDatabase
    private lateinit var dao: LongTermMemoryDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MemoryDatabase::class.java,
        ).build()
        dao = db.longTermMemoryDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun fakeEntry(content: String = "test", timestamp: Long = System.currentTimeMillis()) =
        LongTermMemoryEntity(
            id = java.util.UUID.randomUUID().toString(),
            role = "USER",
            content = content,
            timestamp = timestamp,
            type = "CONVERSATION",
        )

    @Test
    fun insert_and_getRecent() = runTest {
        val entry = fakeEntry("hello")
        dao.insert(entry)
        val recent = dao.getRecent(limit = 10)
        assertEquals(1, recent.size)
        assertEquals("hello", recent[0].content)
    }

    @Test
    fun getRecent_respects_limit() = runTest {
        repeat(5) { i ->
            dao.insert(fakeEntry("msg$i", timestamp = i.toLong()))
        }
        val recent = dao.getRecent(limit = 3)
        assertEquals(3, recent.size)
    }

    @Test
    fun search_finds_matching_content() = runTest {
        dao.insert(fakeEntry("Que hora es"))
        dao.insert(fakeEntry("Cuanto cuesta"))
        dao.insert(fakeEntry("Hola mundo"))
        val results = dao.search("hora")
        assertEquals(1, results.size)
        assertTrue(results[0].content.contains("hora"))
    }

    @Test
    fun count_returns_correct_number() = runTest {
        assertEquals(0, dao.count())
        dao.insert(fakeEntry("a"))
        dao.insert(fakeEntry("b"))
        assertEquals(2, dao.count())
    }

    @Test
    fun deleteOlderThan_removes_old_entries() = runTest {
        dao.insert(fakeEntry("old", timestamp = 1000))
        dao.insert(fakeEntry("new", timestamp = 9999))
        val deleted = dao.deleteOlderThan(5000)
        assertEquals(1, deleted)
        assertEquals(1, dao.count())
    }

    @Test
    fun clear_removes_all_entries() = runTest {
        dao.insert(fakeEntry("a"))
        dao.insert(fakeEntry("b"))
        dao.clear()
        assertEquals(0, dao.count())
    }
}
