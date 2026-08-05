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

/**
 * Lote 12 (M6): CRUD real de pending_messages con Room in-memory + SQLite real
 * (Robolectric, corre en testDebugUnitTest → gate).
 *
 * SIN allowMainThreadQueries() (QA-2): los DAOs suspend corren en los executors
 * de Room y el Flow se recolecta con runTest/first() — la disciplina de threading
 * de producción se valida tal cual, sin relajar el chequeo de hilo de Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class) // NO AndroidJUnit4 (QA-1)
@Config(sdk = [34])
class MessageDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: MessageDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = db.messageDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insert emite el mensaje en la primera emision del flow`() = runTest {
        val message = PendingMessageEntity(
            platform = "WhatsApp",
            contactName = "Ana",
            message = "hola",
            timestamp = 1000L
        )

        dao.insertMessage(message)

        val messages = dao.getAllPendingMessages().first()
        assertEquals(1, messages.size)
        assertEquals(message.copy(id = messages.single().id), messages.single())
        assertTrue(messages.single().id > 0)
    }

    @Test
    fun `el flow ordena por timestamp ascendente`() = runTest {
        dao.insertMessage(
            PendingMessageEntity(platform = "SMS", contactName = "Luis", message = "segundo", timestamp = 2000L)
        )
        dao.insertMessage(
            PendingMessageEntity(platform = "SMS", contactName = "Ana", message = "primero", timestamp = 1000L)
        )

        val messages = dao.getAllPendingMessages().first()

        assertEquals(listOf("primero", "segundo"), messages.map { it.message })
        assertEquals(listOf(1000L, 2000L), messages.map { it.timestamp })
    }

    @Test
    fun `deleteMessage elimina el mensaje y con id inexistente es un no-op sin excepcion`() = runTest {
        dao.insertMessage(
            PendingMessageEntity(platform = "WhatsApp", contactName = "Ana", message = "hola", timestamp = 1000L)
        )
        val inserted = dao.getAllPendingMessages().first().single()

        dao.deleteMessage(inserted.id)

        assertTrue(dao.getAllPendingMessages().first().isEmpty())

        // id inexistente: no-op, sin excepción
        dao.deleteMessage(9999)
    }

    @Test
    fun `clearAll vacia la cola completa`() = runTest {
        dao.insertMessage(
            PendingMessageEntity(platform = "WhatsApp", contactName = "Ana", message = "hola", timestamp = 1000L)
        )
        dao.insertMessage(
            PendingMessageEntity(platform = "Telegram", contactName = "Luis", message = "chau", timestamp = 2000L)
        )

        dao.clearAll()

        assertTrue(dao.getAllPendingMessages().first().isEmpty())
    }

    @Test
    fun `contactNumber null hace round-trip como null`() = runTest {
        dao.insertMessage(
            PendingMessageEntity(
                platform = "SMS",
                contactName = "Luis",
                contactNumber = null,
                message = "sin numero",
                timestamp = 1000L
            )
        )

        val read = dao.getAllPendingMessages().first().single()

        assertNull(read.contactNumber)
        assertEquals("Luis", read.contactName)
    }
}
