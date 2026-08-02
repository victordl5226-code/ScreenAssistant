package com.screenassistant.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MemoryEntity::class, PendingMessageEntity::class, AlarmEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun messageDao(): MessageDao
    abstract fun alarmDao(): AlarmDao

    companion object {
        /**
         * QA #2: migración explícita v2→v3 (tabla alarms). NO se depende solo de
         * fallbackToDestructiveMigration: la creación de la tabla es idempotente
         * (IF NOT EXISTS) y se registra en el builder de Room.
         * El SQL replica EXACTAMENTE el CREATE TABLE que Room generaría para
         * AlarmEntity (PK a nivel de tabla) para que la validación de identidad
         * no falle al abrir.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `alarms` (" +
                        "`requestCode` INTEGER NOT NULL, " +
                        "`hour` INTEGER NOT NULL, " +
                        "`minute` INTEGER NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "`triggerAtMillis` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`requestCode`))"
                )
            }
        }
    }
}
