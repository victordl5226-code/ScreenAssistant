package com.screenassistant.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.screenassistant.core.data.local.iot.CarAppStateEntity
import com.screenassistant.core.data.local.iot.HealthMetricsEntity
import com.screenassistant.core.data.local.iot.IotDao
import com.screenassistant.core.data.local.iot.MatterDeviceEntity
import com.screenassistant.core.data.local.iot.MatterSceneEntity
import com.screenassistant.core.data.local.iot.WearTileEntity

@Database(
    entities = [
        MemoryEntity::class,
        PendingMessageEntity::class,
        AlarmEntity::class,
        ConversationTurnEntity::class,
        UserPatternEntity::class,
        ProactiveRuleEntity::class,
        // IoT entities (v5)
        MatterDeviceEntity::class,
        HealthMetricsEntity::class,
        WearTileEntity::class,
        CarAppStateEntity::class,
        // IoT scenes (v6)
        MatterSceneEntity::class,
    ],
    version = 6,
    // Lote 12 (M6): exportSchema=true emite los JSON de esquema a core/data/schemas
    // (room.schemaLocation) para MigrationTestHelper. Cero impacto runtime.
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun messageDao(): MessageDao
    abstract fun alarmDao(): AlarmDao
    abstract fun conversationTurnDao(): ConversationTurnDao
    abstract fun userPatternDao(): UserPatternDao
    abstract fun proactiveRuleDao(): ProactiveRuleDao
    // IoT DAO (v5)
    abstract fun iotDao(): IotDao

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

        /**
         * Migración v3→v4: crea las tablas para conversaciones, patrones de usuario
         * y reglas proactivas.
         *
         * Las tablas se crean con IF NOT EXISTS para ser idempotentes.
         * Los índices se crean con IF NOT EXISTS para evitar duplicados.
         *
         * - conversation_turns: almacena turnos de conversación con índice en timestamp
         * - user_patterns: almacena patrones de comportamiento con índice en action
         * - proactive_rules: almacena reglas proactivas (sin índices adicionales)
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // conversation_turns
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `conversation_turns` (" +
                        "`id` TEXT NOT NULL, " +
                        "`userMessage` TEXT NOT NULL, " +
                        "`assistantResponse` TEXT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "`screenContextJson` TEXT, " +
                        "`topic` TEXT, " +
                        "`sentiment` TEXT, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_conversation_turns_timestamp` " +
                        "ON `conversation_turns` (`timestamp`)"
                )

                // user_patterns
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_patterns` (" +
                        "`id` TEXT NOT NULL, " +
                        "`action` TEXT NOT NULL, " +
                        "`dayOfWeek` TEXT NOT NULL, " +
                        "`hourStart` INTEGER NOT NULL, " +
                        "`hourEnd` INTEGER NOT NULL, " +
                        "`locationType` TEXT NOT NULL, " +
                        "`screenApp` TEXT, " +
                        "`frequency` INTEGER NOT NULL, " +
                        "`lastSeen` INTEGER NOT NULL, " +
                        "`confidence` REAL NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_user_patterns_action` " +
                        "ON `user_patterns` (`action`)"
                )

                // proactive_rules
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `proactive_rules` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`conditionsJson` TEXT NOT NULL, " +
                        "`actionType` TEXT NOT NULL, " +
                        "`actionData` TEXT NOT NULL, " +
                        "`priority` TEXT NOT NULL, " +
                        "`cooldownMinutes` INTEGER NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        /**
         * Migración v4→v5: añade tablas IoT (Matter, Health, Wear, Auto).
         *
         * Las tablas se crean con IF NOT EXISTS para ser idempotentes.
         * Los índices se crean con IF NOT EXISTS para evitar duplicados.
         *
         * - matter_devices: dispositivos Matter con atributos serializados
         * - health_metrics: métricas de salud agregadas (snapshots)
         * - wear_tiles: estado de Tiles de Wear OS
         * - car_app_states: estados de la app en Android Auto/AAOS
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // M25: Re-creación de tablas IoT con orden alfabético estricto (PK primero)
                // y todos los índices esperados por Room para evitar fallos de validación.

                // matter_devices
                db.execSQL("DROP TABLE IF EXISTS `matter_devices`")
                db.execSQL("""
                    CREATE TABLE `matter_devices` (
                        `deviceId` TEXT NOT NULL,
                        `attributesJson` TEXT NOT NULL,
                        `firmwareVersion` TEXT NOT NULL,
                        `isOnline` INTEGER NOT NULL,
                        `lastSeen` INTEGER NOT NULL,
                        `manufacturer` TEXT NOT NULL,
                        `model` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `room` TEXT NOT NULL,
                        `serialNumber` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`deviceId`)
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_matter_devices_room` ON `matter_devices` (`room`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_matter_devices_type` ON `matter_devices` (`type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_matter_devices_online` ON `matter_devices` (`isOnline`)")

                // health_metrics
                db.execSQL("DROP TABLE IF EXISTS `health_metrics`")
                db.execSQL("""
                    CREATE TABLE `health_metrics` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `metricsJson` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `syncedAt` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_health_metrics_timestamp` ON `health_metrics` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_health_metrics_source` ON `health_metrics` (`source`)")

                // wear_tiles
                db.execSQL("DROP TABLE IF EXISTS `wear_tiles`")
                db.execSQL("""
                    CREATE TABLE `wear_tiles` (
                        `tileId` TEXT NOT NULL,
                        `lastUpdated` INTEGER NOT NULL,
                        `stateJson` TEXT NOT NULL,
                        PRIMARY KEY(`tileId`)
                    )
                """.trimIndent())

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_wear_tiles_tileId` ON `wear_tiles` (`tileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wear_tiles_updated` ON `wear_tiles` (`lastUpdated`)")

                // car_app_states
                db.execSQL("DROP TABLE IF EXISTS `car_app_states`")
                db.execSQL("""
                    CREATE TABLE `car_app_states` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `carModel` TEXT,
                        `connectionType` TEXT NOT NULL,
                        `isDriving` INTEGER NOT NULL,
                        `speedKmh` REAL NOT NULL,
                        `stateJson` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_car_app_states_timestamp` ON `car_app_states` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_car_app_states_connection` ON `car_app_states` (`connectionType`)")
            }
        }

        /**
         * Migración v5→v6: añade tabla matter_scenes para escenas Matter.
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `matter_scenes` (
                        `sceneId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `icon` TEXT NOT NULL,
                        `devicesJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`sceneId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_matter_scenes_name` ON `matter_scenes` (`name`)")
            }
        }

        /** Todas las migraciones en orden, para uso en tests y DI. */
        val ALL_MIGRATIONS = arrayOf(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
    }

}
