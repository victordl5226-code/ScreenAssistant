package com.screenassistant.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class AlarmDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AlarmDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = db.alarmDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createAlarm(
        requestCode: Int = 450,
        hour: Int = 7,
        minute: Int = 30,
        label: String = "Alarma",
        triggerAtMillis: Long = 1000L,
        createdAt: Long = 1000L
    ) = AlarmEntity(
        requestCode = requestCode,
        hour = hour,
        minute = minute,
        label = label,
        triggerAtMillis = triggerAtMillis,
        createdAt = createdAt
    )

    @Test
    fun `upsert inserts a new alarm and getByRequestCode retrieves it`() = runTest {
        val alarm = createAlarm()

        dao.upsert(alarm)

        val result = dao.getByRequestCode(450)
        assertEquals(alarm, result)
    }

    @Test
    fun `upsert updates an existing alarm with same requestCode`() = runTest {
        val original = createAlarm(label = "Original")
        val updated = createAlarm(label = "Updated", triggerAtMillis = 2000L)

        dao.upsert(original)
        dao.upsert(updated)

        val result = dao.getByRequestCode(450)
        assertEquals("Updated", result?.label)
        assertEquals(2000L, result?.triggerAtMillis)
    }

    @Test
    fun `getByRequestCode returns null for non-existent requestCode`() = runTest {
        val result = dao.getByRequestCode(9999)

        assertNull(result)
    }

    @Test
    fun `getAll returns all alarms ordered by requestCode ascending`() = runTest {
        dao.upsert(createAlarm(requestCode = 1200, hour = 20, minute = 0))
        dao.upsert(createAlarm(requestCode = 300, hour = 5, minute = 0))
        dao.upsert(createAlarm(requestCode = 450, hour = 7, minute = 30))

        val result = dao.getAll()

        assertEquals(3, result.size)
        assertEquals(300, result[0].requestCode)
        assertEquals(450, result[1].requestCode)
        assertEquals(1200, result[2].requestCode)
    }

    @Test
    fun `getAll returns empty list when no alarms exist`() = runTest {
        val result = dao.getAll()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `deleteByRequestCode removes only the specified alarm`() = runTest {
        dao.upsert(createAlarm(requestCode = 450, hour = 7, minute = 30))
        dao.upsert(createAlarm(requestCode = 1200, hour = 20, minute = 0))

        dao.deleteByRequestCode(450)

        assertNull(dao.getByRequestCode(450))
        assertEquals(1, dao.getAll().size)
    }

    @Test
    fun `deleteByRequestCode with non-existent requestCode does nothing`() = runTest {
        dao.upsert(createAlarm(requestCode = 450))

        dao.deleteByRequestCode(9999)

        assertEquals(1, dao.getAll().size)
    }

    @Test
    fun `deleteAll removes all alarms`() = runTest {
        dao.upsert(createAlarm(requestCode = 450))
        dao.upsert(createAlarm(requestCode = 1200))
        dao.upsert(createAlarm(requestCode = 300))

        dao.deleteAll()

        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun `deleteAll on empty table does not throw`() = runTest {
        dao.deleteAll()

        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun `upsert preserves createdAt from original when re-inserting`() = runTest {
        val alarm = createAlarm(createdAt = 5000L)
        dao.upsert(alarm)

        val reInserted = createAlarm(createdAt = 9999L)
        dao.upsert(reInserted)

        val result = dao.getByRequestCode(450)
        assertEquals(9999L, result?.createdAt)
    }

    @Test
    fun `getAll with single alarm returns that alarm`() = runTest {
        val alarm = createAlarm(requestCode = 100, hour = 12, minute = 0, label = "Mediodía")

        dao.upsert(alarm)

        val result = dao.getAll()
        assertEquals(1, result.size)
        assertEquals("Mediodía", result[0].label)
    }
}
