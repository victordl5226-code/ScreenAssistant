package com.screenassistant.core.ai.memory.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.screenassistant.core.ai.memory.FactExtractorImpl
import com.screenassistant.core.ai.memory.KnowledgeFactDao
import com.screenassistant.core.ai.memory.LongTermMemoryDao
import com.screenassistant.core.ai.memory.MemoryEnricher
import com.screenassistant.core.ai.memory.MemoryLogger
import com.screenassistant.core.ai.memory.MemoryStoreImpl
import com.screenassistant.core.ai.memory.local.InMemoryShortTermMemory
import com.screenassistant.core.domain.repository.ai.FactExtractor
import com.screenassistant.core.domain.repository.ai.MemoryStore
import com.screenassistant.core.domain.repository.ai.ShortTermMemory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo DI para AI Memory.
 *
 * Proporciona:
 * - [ShortTermMemory] — Buffer in-memory (no Room)
 * - Room DB para memoria persistente
 * - DAOs para acceso a datos
 * - Migración v1 → v2 (semantic_facts para RAG)
 */
@Module
@InstallIn(SingletonComponent::class)
object MemoryModule {

    /**
     * Migración v1 → v2: Agrega tabla semantic_facts para RAG.
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS semantic_facts (
                    id TEXT NOT NULL PRIMARY KEY,
                    factId TEXT NOT NULL,
                    text TEXT NOT NULL,
                    embedding BLOB NOT NULL,
                    category TEXT NOT NULL,
                    confidence REAL NOT NULL,
                    timestamp INTEGER NOT NULL
                )
                """
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_semantic_facts_factId ON semantic_facts(factId)"
            )
        }
    }

    /**
     * Room DB para memoria a largo plazo.
     */
    @Provides
    @Singleton
    fun provideMemoryDatabase(
        @ApplicationContext context: Context,
    ): com.screenassistant.core.ai.memory.MemoryDatabase {
        return Room.databaseBuilder(
            context,
            com.screenassistant.core.ai.memory.MemoryDatabase::class.java,
            "ai_memory.db",
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    /**
     * DAO para memoria a largo plazo.
     */
    @Provides
    fun provideLongTermMemoryDao(
        db: com.screenassistant.core.ai.memory.MemoryDatabase,
    ): LongTermMemoryDao = db.longTermMemoryDao()

    /**
     * DAO para hechos de conocimiento.
     */
    @Provides
    fun provideKnowledgeFactDao(
        db: com.screenassistant.core.ai.memory.MemoryDatabase,
    ): KnowledgeFactDao = db.knowledgeFactDao()

    /**
     * DAO para embeddings semánticos (RAG).
     */
    @Provides
    fun provideSemanticFactDao(
        db: com.screenassistant.core.ai.memory.MemoryDatabase,
    ): com.screenassistant.core.ai.memory.semantic.SemanticFactDao = db.semanticFactDao()

    /**
     * Memoria a corto plazo — buffer in-memory.
     */
    @Provides
    @Singleton
    fun provideShortTermMemory(): ShortTermMemory {
        return InMemoryShortTermMemory(maxCapacity = 50)
    }

    /**
     * Extractor de hechos basado en reglas (sin LLM).
     */
    @Provides
    @Singleton
    fun provideFactExtractor(): FactExtractor {
        return FactExtractorImpl()
    }

    /**
     * Almacenamiento persistente de memoria (Room).
     */
    @Provides
    @Singleton
    fun provideMemoryStore(
        longTermMemoryDao: LongTermMemoryDao,
        knowledgeFactDao: KnowledgeFactDao,
    ): MemoryStore {
        return MemoryStoreImpl(longTermMemoryDao, knowledgeFactDao)
    }

    /**
     * Fachada de memoria para el ViewModel.
     * Centraliza búsqueda, guardado y extracción con fail-soft.
     *
     * Acepta SemanticMemoryRepository como nullable para compatibilidad
     * con la migración gradual a RAG (ADR-026).
     */
    @Provides
    @Singleton
    fun provideMemoryEnricher(
        shortTermMemory: ShortTermMemory,
        memoryStore: MemoryStore,
        factExtractor: FactExtractor,
        logger: MemoryLogger,
        semanticMemoryRepository: com.screenassistant.core.domain.repository.ai.SemanticMemoryRepository,
    ): MemoryEnricher {
        return MemoryEnricher(
            shortTermMemory,
            memoryStore,
            factExtractor,
            semanticMemoryRepository,
            logger,
        )
    }
}
