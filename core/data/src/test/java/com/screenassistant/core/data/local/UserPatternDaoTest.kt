package com.screenassistant.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserPatternDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: UserPatternDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = db.userPatternDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createPattern(
        id: String = "pattern-1",
        action: String = "open_app",
        dayOfWeek: String = "MONDAY",
        hourStart: Int = 9,
        hourEnd: Int = 12,
        locationType: String = "HOME",
        frequency: Int = 5,
        lastSeen: Long = System.currentTimeMillis(),
        confidence: Float = 0.8f
    ) = UserPatternEntity(
        id = id,
        action = action,
        dayOfWeek = dayOfWeek,
        hourStart = hourStart,
        hourEnd = hourEnd,
        locationType = locationType,
        frequency = frequency,
        lastSeen = lastSeen,
        confidence = confidence
    )

    @Test
    fun `insertPattern and getPattern retrieves it`() = runTest {
        val pattern = createPattern()

        dao.insertPattern(pattern)

        val result = dao.getPattern("pattern-1")
        assertEquals(pattern, result)
    }

    @Test
    fun `getPattern returns null for non-existent id`() = runTest {
        val result = dao.getPattern("nonexistent")

        assertNull(result)
    }

    @Test
    fun `getAllPatterns returns patterns ordered by frequency descending`() = runTest {
        dao.insertPattern(createPattern(id = "low", frequency = 1))
        dao.insertPattern(createPattern(id = "high", frequency = 100))
        dao.insertPattern(createPattern(id = "mid", frequency = 50))

        val patterns = dao.getAllPatterns().first()

        assertEquals(3, patterns.size)
        assertEquals("high", patterns[0].id)
        assertEquals("mid", patterns[1].id)
        assertEquals("low", patterns[2].id)
    }

    @Test
    fun `getAllPatterns returns empty when no patterns exist`() = runTest {
        val patterns = dao.getAllPatterns().first()

        assertTrue(patterns.isEmpty())
    }

    @Test
    fun `getFrequentPatterns returns patterns above minFrequency`() = runTest {
        dao.insertPattern(createPattern(id = "low", frequency = 1))
        dao.insertPattern(createPattern(id = "high", frequency = 100))
        dao.insertPattern(createPattern(id = "mid", frequency = 50))

        val results = dao.getFrequentPatterns(10)

        assertEquals(2, results.size)
        assertEquals("high", results[0].id)
        assertEquals("mid", results[1].id)
    }

    @Test
    fun `getFrequentPatterns returns empty when no patterns meet threshold`() = runTest {
        dao.insertPattern(createPattern(frequency = 1))

        val results = dao.getFrequentPatterns(10)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `getFrequentPatterns includes patterns at exact threshold`() = runTest {
        dao.insertPattern(createPattern(id = "exact", frequency = 10))

        val results = dao.getFrequentPatterns(10)

        assertEquals(1, results.size)
        assertEquals("exact", results[0].id)
    }

    @Test
    fun `deleteOldPatterns removes patterns before timestamp`() = runTest {
        val now = System.currentTimeMillis()
        dao.insertPattern(createPattern(id = "old", lastSeen = now - 100000L))
        dao.insertPattern(createPattern(id = "new", lastSeen = now))

        dao.deleteOldPatterns(now - 50000L)

        val remaining = dao.getAllPatterns().first()
        assertEquals(1, remaining.size)
        assertEquals("new", remaining[0].id)
    }

    @Test
    fun `deleteOldPatterns does not remove patterns at exactly the boundary`() = runTest {
        val now = System.currentTimeMillis()
        dao.insertPattern(createPattern(id = "exact", lastSeen = now))

        dao.deleteOldPatterns(now)

        val remaining = dao.getAllPatterns().first()
        assertEquals(1, remaining.size)
    }

    @Test
    fun `deletePattern removes specific pattern`() = runTest {
        dao.insertPattern(createPattern(id = "to-delete"))
        dao.insertPattern(createPattern(id = "to-keep"))

        dao.deletePattern("to-delete")

        assertNull(dao.getPattern("to-delete"))
        assertEquals("to-keep", dao.getPattern("to-keep")?.id)
    }

    @Test
    fun `deletePattern with non-existent id does nothing`() = runTest {
        dao.insertPattern(createPattern(id = "existing"))

        dao.deletePattern("nonexistent")

        assertEquals(1, dao.getAllPatterns().first().size)
    }

    @Test
    fun `insertPattern with replace strategy updates existing pattern`() = runTest {
        dao.insertPattern(createPattern(id = "pattern-1", frequency = 1))
        dao.insertPattern(createPattern(id = "pattern-1", frequency = 99))

        val result = dao.getPattern("pattern-1")

        assertEquals(99, result?.frequency)
    }

    @Test
    fun `screenApp null field is preserved through round-trip`() = runTest {
        dao.insertPattern(createPattern(id = "no-app", action = "test"))

        val result = dao.getPattern("no-app")

        assertNull(result?.screenApp)
    }

    @Test
    fun `screenApp with value is preserved through round-trip`() = runTest {
        dao.insertPattern(
            createPattern(id = "with-app", action = "test").copy(screenApp = "com.example.app")
        )

        val result = dao.getPattern("with-app")

        assertEquals("com.example.app", result?.screenApp)
    }

    // ── findByActionAndContext ───────────────────────────────────────────

    @Test
    fun `findByActionAndContext retorna patrón cuando coincide`() = runTest {
        val pattern = createPattern(
            id = "match-1",
            action = "OpenApp",
            dayOfWeek = "WEDNESDAY",
            hourStart = 9,
            hourEnd = 12,
            locationType = "HOME"
        )
        dao.insertPattern(pattern)

        val result = dao.findByActionAndContext(
            action = "OpenApp",
            dayOfWeek = "WEDNESDAY",
            hourStart = 9,
            hourEnd = 12,
            location = "HOME"
        )

        assertEquals(pattern, result)
    }

    @Test
    fun `findByActionAndContext retorna null cuando no coincide`() = runTest {
        dao.insertPattern(
            createPattern(
                id = "no-match",
                action = "OpenApp",
                dayOfWeek = "WEDNESDAY",
                hourStart = 9,
                hourEnd = 12,
                locationType = "HOME"
            )
        )

        val result = dao.findByActionAndContext(
            action = "CloseApp",
            dayOfWeek = "WEDNESDAY",
            hourStart = 9,
            hourEnd = 12,
            location = "HOME"
        )

        assertNull(result)
    }

    @Test
    fun `findByActionAndContext respeta los 5 parámetros de filtro`() = runTest {
        dao.insertPattern(createPattern(
            id = "p1",
            action = "SetWifi",
            dayOfWeek = "MONDAY",
            hourStart = 8,
            hourEnd = 10,
            locationType = "WORK"
        ))
        dao.insertPattern(createPattern(
            id = "p2",
            action = "SetWifi",
            dayOfWeek = "MONDAY",
            hourStart = 8,
            hourEnd = 10,
            locationType = "HOME"
        ))
        dao.insertPattern(createPattern(
            id = "p3",
            action = "SetWifi",
            dayOfWeek = "FRIDAY",
            hourStart = 8,
            hourEnd = 10,
            locationType = "WORK"
        ))
        dao.insertPattern(createPattern(
            id = "p4",
            action = "SetWifi",
            dayOfWeek = "MONDAY",
            hourStart = 14,
            hourEnd = 16,
            locationType = "WORK"
        ))

        val result = dao.findByActionAndContext(
            action = "SetWifi",
            dayOfWeek = "MONDAY",
            hourStart = 8,
            hourEnd = 10,
            location = "WORK"
        )

        assertEquals("p1", result?.id)
    }
}
