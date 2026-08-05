package com.screenassistant.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Lote 12 (M6): migración 2→3 verificada con SQLite REAL (Robolectric) en el gate
 * JVM. MigrationTestHelper lee los esquemas de core/data/schemas (P1: el JSON de
 * assets de TEST está cableado en build.gradle.kts; 2.json bootstrapado con build
 * temporal v2 y controles NEGATIVO/POSITIVO verificados en el cierre del lote).
 *
 * El contrato que se valida es la afirmación del KDoc de MIGRATION_2_3: el SQL de
 * la migración replica EXACTAMENTE el CREATE TABLE que Room generaría para
 * AlarmEntity (PK a nivel de tabla) — runMigrationsAndValidate compara la
 * identidad completa (tablas, tipos, PK, índices) contra 3.json.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class) // NO AndroidJUnit4: esto corre en testDebugUnitTest (QA-1)
@Config(sdk = [34])
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    // T1: identidad completa de esquema contra 3.json.
    @Test
    fun `migrar de v2 a v3 produce el esquema identico al esperado por Room`() {
        helper.createDatabase("mig-identidad", 2).close()

        helper.runMigrationsAndValidate("mig-identidad", 3, true, AppDatabase.MIGRATION_2_3)
    }

    // T2: preservación de datos — 4 filas (2 memories + 2 pending_messages) con
    // timestamps distintos; el orden de emisión de cada Flow es el declarado por el
    // DAO (memories DESC, pending_messages ASC) y las PK asignadas en v2 se conservan.
    @Test
    fun `migrar de v2 a v3 preserva memories y pending_messages con valores y orden`() = runTest {
        helper.createDatabase("mig-datos", 2).use { db ->
            db.execSQL("INSERT INTO memories (content, timestamp) VALUES ('memoria temprana', 1000)")
            db.execSQL("INSERT INTO memories (content, timestamp) VALUES ('memoria reciente', 2000)")
            db.execSQL(
                "INSERT INTO pending_messages (platform, contactName, contactNumber, message, timestamp) " +
                    "VALUES ('WhatsApp', 'Ana', '600111222', 'hola', 3000)"
            )
            db.execSQL(
                "INSERT INTO pending_messages (platform, contactName, contactNumber, message, timestamp) " +
                    "VALUES ('SMS', 'Luis', NULL, 'sin numero', 4000)"
            )
        }
        helper.runMigrationsAndValidate("mig-datos", 3, true, AppDatabase.MIGRATION_2_3)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, "mig-datos").build()
        try {
            val memories = db.memoryDao().getAllMemories().first()
            assertEquals(2, memories.size)
            assertEquals(listOf("memoria reciente", "memoria temprana"), memories.map { it.content })
            assertEquals(listOf(2000L, 1000L), memories.map { it.timestamp })
            assertEquals(setOf(1, 2), memories.map { it.id }.toSet())

            val messages = db.messageDao().getAllPendingMessages().first()
            assertEquals(2, messages.size)
            assertEquals(listOf("hola", "sin numero"), messages.map { it.message })
            assertEquals(listOf("Ana", "Luis"), messages.map { it.contactName })
            assertEquals(listOf(3000L, 4000L), messages.map { it.timestamp })
            assertEquals("600111222", messages[0].contactNumber)
            assertNull(messages[1].contactNumber)
            assertEquals(setOf(1, 2), messages.map { it.id }.toSet())
        } finally {
            db.close()
        }
    }

    // T3: la tabla alarms creada por la migración es funcional contra el DAO real
    // (no solo "existe"): upsert + lectura (cubre también AlarmDao.@Upsert).
    @Test
    fun `la tabla alarms creada por la migracion es funcional contra el DAO real`() = runTest {
        helper.createDatabase("mig-alarmas", 2).close()
        helper.runMigrationsAndValidate("mig-alarmas", 3, true, AppDatabase.MIGRATION_2_3)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, "mig-alarmas").build()
        try {
            val alarm = AlarmEntity(
                requestCode = 300, // 5:00 (hora*60+minuto)
                hour = 5,
                minute = 0,
                label = "despertar",
                triggerAtMillis = 1234L,
                createdAt = 5678L
            )
            db.alarmDao().upsert(alarm)

            val read = db.alarmDao().getByRequestCode(300)

            assertEquals(alarm, read)
            assertTrue(db.alarmDao().getAll().contains(alarm))
        } finally {
            db.close()
        }
    }
}
