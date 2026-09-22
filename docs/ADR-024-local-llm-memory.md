# ADR-024: Local LLM + Memory System

## Estado: Implementado (Fase 6 — Phase 0)

### Estado de implementación (2026-08-31)

| Componente | Estado | Módulo | Notas |
|---|---|---|---|
| Interfaces AI (8) | ✅ | core/domain/repository/ai/ | AiOrchestrator, AiRepository, LocalInferenceEngine, MemoryStore, ShortTermMemory, FactExtractor, ModelManager, ConnectivityMonitor |
| Modelos de datos | ✅ | core/domain/repository/ai/ | AiProvider, AiResponse, MemoryEntry, MemoryTurn, KnowledgeFact |
| AiOrchestratorImpl | ✅ | core/data | Strategy pattern: LOCAL → GEMINI fallback |
| ConnectivityMonitorImpl | ✅ | core/data | ConnectivityManager → ConnectivityInfo |
| StubLocalInferenceEngine | ✅ | core/ai/local | Stub para testing sin MLC LLM |
| FactExtractorImpl | ✅ | core/ai/memory | 30+ reglas regex, 6 categorías |
| InMemoryShortTermMemory | ✅ | core/ai/memory | Buffer in-memory (ArrayDeque) |
| Room DB (MemoryDatabase) | ✅ | core/ai/memory | 2 entidades: LongTermMemory, KnowledgeFact |
| OverlayViewModel integrado | ✅ | feature/overlay | Usa AiOrchestrator, no GeminiRepository directo |
| DI wiring | ✅ | core/data, core/ai/* | DataModule, LocalAiModule, MemoryModule |
| MLC LLM engine | ⏳ | core/ai/local | Placeholder (MLC sin release estable) |
| SemanticSearcher | ⏳ | core/ai/memory | Pendiente |
| MemorySystem (unified) | ⏳ | pendiente | Pendiente |
| Room DB integration tests | ⏳ | pendiente | Pendiente |

### Desviaciones del diseño original

1. **AiOrchestrator vs AiRepository**: Se usó `AiOrchestrator` como punto de entrada principal en vez de `AiRepository` unificado. `GeminiRepository` se mantiene para migración gradual.
2. **MemoryTurn vs ConversationTurn**: Se renombró para evitar colisión con el modelo existente en `model/personality/`.
3. **FactExtractor como interfaz**: Se definió en `core/domain` (JVM puro) en vez de como clase concreta en `core/ai:memory`.
4. **Short-term in-memory**: Se implementó con `ArrayDeque` (no Room) siguiendo recomendación de QA.
5. **ConnectivityMonitor**: Reutiliza `ConnectivityInfo` existente en vez de crear un nuevo modelo.

### Tests nuevos en Phase 6

| Suite | Tests | Módulo |
|---|---|---|
| InMemoryShortTermMemoryTest | 10 | core:ai:memory |
| FactExtractorImplTest | 25 | core:ai:memory |
| AiOrchestratorImplTest | 7 | core:data |
| ConnectivityMonitorImplTest | 5 | core:data |
| AiOrchestratorIntegrationTest | 9 | core:data |
| **Total nuevos** | **56** | |
| Tests modificados | 78 (overlay) | feature:overlay |

---

## Contexto (diseño original)

ScreenAssistant depende actualmente de Gemini (API cloud) para inferencia de IA. El usuario quiere reemplazarlo con un LLM local (Phi-3 Mini 3.8B via MLC LLM) para:
1. Funcionamiento offline completo
2. Eliminar dependencia de Google/Gemini
3. Sistema de memoria a largo plazo con búsqueda semántica
4. Fallback opcional a Gemini cuando haya internet

**Restricciones críticas**:
- `core/domain` debe ser JVM puro (sin dependencias Android)
- Phi-3 Mini modelo bundle: ~2.3GB en assets del APK
- MLC LLM: motor de inferencia (Apache 2.0)
- Patrones existentes: MVVM + StateFlow, Hilt DI, Coroutines + Flow

## Decisión

### 1. Nuevos Módulos

| Módulo | Tipo | Responsabilidad |
|---|---|---|
| `core:ai:local` | Android Library | Inferencia LLM local via MLC LLM, gestión del modelo, PromptEngine local |
| `core:ai:memory` | Android Library | Sistema de memoria: Room entities, DAOs, repositorios de memoria, embeddings, búsqueda semántica |

**Justificación de separación**:
- `core:ai:local` se enfoca EXCLUSIVAMENTE en la inferencia. No conoce persistencia.
- `core:ai:memory` se enfoca EXCLUSIVAMENTE en memoria. No conoce el motor de inferencia.
- Ambos módulos son consumidos por `feature:overlay` y `core:data` (para wiring).
- Separación permite testing independiente: mocks de inferencia para tests de memoria, y viceversa.

### 2. Nuevas Interfaces en `core/domain` (JVM Puro)

#### 2.1 Interfaz Unificada de AI (reemplaza GeminiRepository)

```kotlin
// core/domain/src/main/kotlin/com/screenassistant/core/domain/repository/AiRepository.kt
package com.screenassistant.core.domain.repository

import com.screenassistant.core.domain.model.AiProvider
import com.screenassistant.core.domain.model.ImageData
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz unificada para cualquier proveedor de inferencia de IA.
 * Abstrae local vs cloud. El ViewModel solo conoce esta interfaz.
 */
interface AiRepository {
    /** Envía mensaje y retorna respuesta completa (suspend). */
    suspend fun sendMessage(message: String, image: ImageData? = null): String?

    /** Streaming de tokens (para UI reactiva). */
    fun streamMessage(message: String): Flow<String>

    /** Cambia idioma del sistema prompt. */
    fun setLanguage(language: com.screenassistant.core.domain.model.AssistantLanguage)

    /** Informa qué proveedor está activo (para UI/status). */
    fun getCurrentProvider(): AiProvider
}
```

#### 2.2 Interfaz del Motor de Inferencia Local

```kotlin
// core/domain/src/main/kotlin/com/screenassistant/core/domain/ai/LocalInferenceEngine.kt
package com.screenassistant.core.domain.ai

import kotlinx.coroutines.flow.Flow

/**
 * Motor de inferencia local abstracto.
 * Implementado en core:ai:local (Android).
 * Domain solo conoce el contrato.
 */
interface LocalInferenceEngine {
    /** Inicializa el motor con la ruta del modelo en assets. */
    suspend fun initialize(modelPath: String)

    /** Genera respuesta completa dado un prompt. */
    suspend fun generate(prompt: String): String

    /** Streaming de tokens. */
    fun generateStream(prompt: String): Flow<String>

    /** Libera recursos del motor. */
    fun release()

    /** Estado de salud del motor. */
    fun isReady(): Boolean
}
```

#### 2.3 Interfaz del Sistema de Memoria

```kotlin
// core/domain/src/main/kotlin/com/screenassistant/core/domain/ai/MemorySystem.kt
package com.screenassistant.core.domain.ai

import com.screenassistant.core.domain.model.MemoryEntry
import kotlinx.coroutines.flow.Flow

/**
 * Sistema de memoria unificado: corto y largo plazo.
 * Implementado en core:ai:memory (Android).
 * Domain solo conoce el contrato.
 */
interface MemorySystem {
    // === Corto plazo (conversación actual) ===

    /** Obtiene los últimos N turns de la conversación actual. */
    fun getRecentContext(limit: Int = 10): Flow<List<MemoryEntry>>

    /** Guarda un turno en memoria de corto plazo. */
    suspend fun addToShortTermMemory(userMessage: String, assistantResponse: String)

    // === Largo plazo (historial persistente) ===

    /** Busca contexto relevante de conversaciones pasadas (búsqueda semántica). */
    suspend fun searchRelevantContext(query: String, limit: Int = 5): List<MemoryEntry>

    /** Guarda una entrada en memoria de largo plazo. */
    suspend fun saveToLongTermMemory(entry: MemoryEntry)

    /** Extrae y almacena hechos/prefrencias del usuario de una conversación. */
    suspend fun extractAndStoreFacts(conversationTurn: com.screenassistant.core.domain.model.personality.ConversationTurn)

    /** Obtiene todos los hechos conocidos del usuario. */
    fun getAllFacts(): Flow<List<com.screenassistant.core.domain.model.KnowledgeFact>>

    /** Elimina hechos obsoletos o contradichos. */
    suspend fun purgeConflictingFacts()

    // === Gestión ===

    /** Limpia la memoria de corto plazo (nueva sesión). */
    fun clearShortTermMemory()

    /** Estadísticas de la memoria. */
    suspend fun getMemoryStats(): MemoryStats
}

/** Estadísticas del sistema de memoria. */
data class MemoryStats(
    val shortTermTurns: Int,
    val longTermEntries: Int,
    val extractedFacts: Int,
    val lastConversationTimestamp: Long?
)
```

#### 2.4 Interfaz de Monitor de Conectividad

```kotlin
// core/domain/src/main/kotlin/com/screenassistant/core/domain/ai/ConnectivityMonitor.kt
package com.screenassistant.core.domain.ai

import kotlinx.coroutines.flow.Flow

/**
 * Monitor de conectividad para fallback AI.
 * Implementado en core:data (usa ConnectivityManager).
 */
interface ConnectivityMonitor {
    /** Emite true cuando hay internet disponible. */
    fun isOnline(): Flow<Boolean>

    /** Verificación one-shot (para decisiones síncronas). */
    suspend fun checkNow(): Boolean
}
```

#### 2.5 Interfaz del Orquestador de IA (Strategy Pattern)

```kotlin
// core/domain/src/main/kotlin/com/screenassistant/core/domain/ai/AiOrchestrator.kt
package com.screenassistant.core.domain.ai

import com.screenassistant.core.domain.model.AiProvider
import kotlinx.coroutines.flow.Flow

/**
 * Orquestador que coordina Local LLM + Gemini fallback.
 * Strategy pattern: decide qué proveedor usar según conectividad.
 */
interface AiOrchestrator {
    /** Envía mensaje usando la estrategia óptima (local o cloud). */
    suspend fun sendMessage(message: String): String?

    /** Streaming que puede cambiar de proveedor mid-stream. */
    fun streamMessage(message: String): Flow<String>

    /** Proveedor activo actualmente. */
    fun activeProvider(): Flow<AiProvider>

    /** Fuerza un proveedor específico (para debugging/settings). */
    fun setPreferredProvider(provider: AiProvider?)
}
```

### 3. Nuevos Modelos de Dominio (core/domain)

```kotlin
// core/domain/src/main/kotlin/com/screenassistant/core/domain/model/AiProvider.kt
package com.screenassistant.core.domain.model

/** Proveedores de IA disponibles. */
enum class AiProvider {
    LOCAL_MLC,   // Phi-3 Mini via MLC LLM
    GEMINI,      // Google Gemini (cloud, fallback)
    AUTO         // Seleccionar automáticamente según conectividad
}

// core/domain/src/main/kotlin/com/screenassistant/core/domain/model/MemoryEntry.kt
package com.screenassistant.core.domain.model

/**
 * Entrada unificada de memoria (corto y largo plazo).
 */
data class MemoryEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val source: MemorySource,
    val timestamp: Long = System.currentTimeMillis(),
    val relevanceScore: Float = 0f,  // Para búsqueda semántica
    val metadata: Map<String, String> = emptyMap()
)

enum class MemorySource {
    SHORT_TERM,    // Conversación actual
    LONG_TERM,     // Conversación pasada persistida
    EXTRACTED      // Hecho/prefrencia extraído
}

// core/domain/src/main/kotlin/com/screenassistant/core/domain/model/KnowledgeFact.kt
package com.screenassistant.core.domain.model

/**
 * Hecho o preferencia extraído del usuario.
 * Almacenado como memoria de largo plazo estructurada.
 */
data class KnowledgeFact(
    val id: String = java.util.UUID.randomUUID().toString(),
    val fact: String,
    val category: FactCategory,
    val confidence: Float = 1.0f,
    val extractedAt: Long = System.currentTimeMillis(),
    val sourceConversationId: String? = null
)

enum class FactCategory {
    USER_NAME,
    PREFERENCE,
    HABIT,
    RELATIONSHIP,
    LOCATION,
    OTHER
}
```

### 4. Room Entities para Largo Plazo (core/ai/memory)

```kotlin
// core/ai/memory/src/main/kotlin/com/screenassistant/core/ai/memory/data/local/LongTermMemoryEntity.kt
package com.screenassistant.core.ai.memory.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "long_term_memory",
    indices = [Index("timestamp"), Index("content")]
)
data class LongTermMemoryEntity(
    @PrimaryKey val id: String,
    val content: String,
    val userMessage: String,
    val assistantResponse: String,
    val timestamp: Long,
    val topic: String? = null,
    val embeddingJson: String? = null,  // Vector de embedding serializado
    val accessCount: Int = 0,
    val lastAccessedAt: Long = System.currentTimeMillis()
)

// core/ai/memory/src/main/kotlin/com/screenassistant/core/ai/memory/data/local/KnowledgeFactEntity.kt
package com.screenassistant.core.ai.memory.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "knowledge_facts",
    indices = [Index("category"), Index("confidence")]
)
data class KnowledgeFactEntity(
    @PrimaryKey val id: String,
    val fact: String,
    val category: String,
    val confidence: Float,
    val extractedAt: Long,
    val sourceConversationId: String? = null,
    val isSuperseded: Boolean = false,
    val supersededBy: String? = null
)

// core/ai/memory/src/main/kotlin/com/screenassistant/core/ai/memory/data/local/ShortTermMemoryEntity.kt
package com.screenassistant.core.ai.memory.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "short_term_memory")
data class ShortTermMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userMessage: String,
    val assistantResponse: String,
    val timestamp: Long,
    val sessionId: String
)
```

### 5. DAOs

```kotlin
// core/ai/memory/src/main/kotlin/com/screenassistant/core/ai/memory/data/local/LongTermMemoryDao.kt
package com.screenassistant.core.ai.memory.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LongTermMemoryDao {
    @Query("SELECT * FROM long_term_memory ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<LongTermMemoryEntity>

    @Query("SELECT * FROM long_term_memory WHERE content LIKE '%' || :query || '%' ORDER BY relevanceScore DESC LIMIT :limit")
    suspend fun searchByContent(query: String, limit: Int = 10): List<LongTermMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LongTermMemoryEntity)

    @Update
    suspend fun update(entry: LongTermMemoryEntity)

    @Query("DELETE FROM long_term_memory WHERE timestamp < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)

    @Query("SELECT COUNT(*) FROM long_term_memory")
    suspend fun count(): Int
}

// core/ai/memory/src/main/kotlin/com/screenassistant/core/ai/memory/data/local/KnowledgeFactDao.kt
package com.screenassistant.core.ai.memory.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeFactDao {
    @Query("SELECT * FROM knowledge_facts WHERE isSuperseded = 0 ORDER BY confidence DESC")
    fun getAllActive(): Flow<List<KnowledgeFactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fact: KnowledgeFactEntity)

    @Query("UPDATE knowledge_facts SET isSuperseded = 1, supersededBy = :newFactId WHERE id = :factId")
    suspend fun supersede(factId: String, newFactId: String)

    @Query("SELECT * FROM knowledge_facts WHERE fact LIKE '%' || :query || '%' AND isSuperseded = 0")
    suspend fun searchByContent(query: String): List<KnowledgeFactEntity>

    @Query("DELETE FROM knowledge_facts WHERE isSuperseded = 1")
    suspend fun purgeSuperseded()
}

// core/ai/memory/src/main/kotlin/com/screenassistant/core/ai/memory/data/local/ShortTermMemoryDao.kt
package com.screenassistant.core.ai.memory.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShortTermMemoryDao {
    @Query("SELECT * FROM short_term_memory WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(sessionId: String, limit: Int): Flow<List<ShortTermMemoryEntity>>

    @Insert
    suspend fun insert(entry: ShortTermMemoryEntity)

    @Query("DELETE FROM short_term_memory WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: String)
}
```

### 6. Database

```kotlin
// core/ai/memory/src/main/kotlin/com/screenassistant/core/ai/memory/data/local/MemoryDatabase.kt
package com.screenassistant.core.ai.memory.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        LongTermMemoryEntity::class,
        KnowledgeFactEntity::class,
        ShortTermMemoryEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun longTermMemoryDao(): LongTermMemoryDao
    abstract fun knowledgeFactDao(): KnowledgeFactDao
    abstract fun shortTermMemoryDao(): ShortTermMemoryDao
}
```

### 7. Estructura de Archivos Completa

```
ScreenAssistant/
├── settings.gradle.kts                    # +include(":core:ai:local", ":core:ai:memory")
├── core/
│   ├── domain/src/main/kotlin/com/screenassistant/core/domain/
│   │   ├── ai/
│   │   │   ├── LocalInferenceEngine.kt     # Interfaz motor local
│   │   │   ├── MemorySystem.kt            # Interfaz sistema de memoria
│   │   │   ├── ConnectivityMonitor.kt     # Monitor de red
│   │   │   └── AiOrchestrator.kt          # Strategy pattern orquestador
│   │   ├── model/
│   │   │   ├── AiProvider.kt              # Enum LOCAL_MLC, GEMINI, AUTO
│   │   │   ├── MemoryEntry.kt             # Entrada unificada de memoria
│   │   │   └── KnowledgeFact.kt           # Hecho extraído del usuario
│   │   └── repository/
│   │       └── AiRepository.kt            # Reemplaza GeminiRepository
│   │
│   ├── ai/
│   │   ├── local/                          # Android Library
│   │   │   ├── build.gradle.kts
│   │   │   └── src/main/kotlin/.../
│   │   │       ├── MlcLlmEngine.kt        # Implementación LocalInferenceEngine
│   │   │       ├── MlcModelManager.kt     # Gestión de carga/descarga del modelo
│   │   │       ├── PromptTemplate.kt      # Templates de prompt para Phi-3
│   │   │       ├── di/
│   │   │       │   └── LocalAiModule.kt   # Hilt module
│   │   │       └── util/
│   │   │           └── AssetModelLoader.kt # Carga modelo desde assets
│   │   │
│   │   └── memory/                         # Android Library
│   │       ├── build.gradle.kts
│   │       └── src/main/kotlin/.../
│   │           ├── data/
│   │           │   ├── local/
│   │           │   │   ├── MemoryDatabase.kt
│   │           │   │   ├── LongTermMemoryEntity.kt
│   │           │   │   ├── LongTermMemoryDao.kt
│   │           │   │   ├── KnowledgeFactEntity.kt
│   │           │   │   ├── KnowledgeFactDao.kt
│   │           │   │   ├── ShortTermMemoryEntity.kt
│   │           │   │   └── ShortTermMemoryDao.kt
│   │           │   └── repository/
│   │           │       └── MemorySystemImpl.kt
│   │           ├── domain/
│   │           │   ├── SemanticSearcher.kt  # Búsqueda semántica (cosine similarity)
│   │           │   ├── FactExtractor.kt     # Extracción de hechos
│   │           │   └── EmbeddingProvider.kt # Interfaz para embeddings
│   │           └── di/
│   │               └── MemoryModule.kt     # Hilt module
│   │
│   └── data/src/main/kotlin/.../
│       ├── remote/
│       │   ├── GeminiRepositoryImpl.kt     # MODIFICADO: implementa AiRepository
│       │   └── FallbackAiRepository.kt     # NUEVO: implementa AiRepository con fallback
│       ├── ai/
│       │   └── ConnectivityMonitorImpl.kt  # Implementa ConnectivityMonitor
│       └── di/
│           ├── DataModule.kt               # MODIFICADO: nuevo binding AiRepository
│           └── AiModule.kt                 # NUEVO: bindings de AI
│
├── feature/overlay/
│   └── src/main/kotlin/.../
│       └── OverlayViewModel.kt             # MODIFICADO: usa AiRepository en vez de GeminiRepository
│
├── app/
│   └── src/main/java/.../
│       └── di/
│           └── AppModule.kt                # MODIFICADO: wiring de AiOrchestrator
│
└── docs/
    └── ADR-024-local-llm-memory.md         # Este documento
```

### 8. DI Wiring

#### 8.1 Módulo Hilt para AI Local (`core:ai:local`)

```kotlin
// core/ai/local/src/main/kotlin/com/screenassistant/core/ai/local/di/LocalAiModule.kt
@Module
@InstallIn(SingletonComponent::class)
object LocalAiModule {

    @Provides
    @Singleton
    fun provideMlcLlmEngine(
        @ApplicationContext context: Context
    ): LocalInferenceEngine {
        return MlcLlmEngine(context)
    }

    @Provides
    @Singleton
    fun provideMlcModelManager(
        engine: LocalInferenceEngine,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): MlcModelManager {
        return MlcModelManager(engine, ioDispatcher)
    }
}
```

#### 8.2 Módulo Hilt para Memoria (`core:ai:memory`)

```kotlin
// core/ai/memory/src/main/kotlin/com/screenassistant/core/ai/memory/di/MemoryModule.kt
@Module
@InstallIn(SingletonComponent::class)
object MemoryModule {

    @Provides
    @Singleton
    fun provideMemoryDatabase(
        @ApplicationContext context: Context
    ): MemoryDatabase {
        return Room.databaseBuilder(
            context,
            MemoryDatabase::class.java,
            "memory_db"
        ).build()
    }

    @Provides
    fun provideLongTermMemoryDao(db: MemoryDatabase): LongTermMemoryDao {
        return db.longTermMemoryDao()
    }

    @Provides
    fun provideKnowledgeFactDao(db: MemoryDatabase): KnowledgeFactDao {
        return db.knowledgeFactDao()
    }

    @Provides
    fun provideShortTermMemoryDao(db: MemoryDatabase): ShortTermMemoryDao {
        return db.shortTermMemoryDao()
    }

    @Provides
    @Singleton
    fun provideMemorySystem(
        longTermDao: LongTermMemoryDao,
        factDao: KnowledgeFactDao,
        shortTermDao: ShortTermMemoryDao,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): MemorySystem {
        return MemorySystemImpl(longTermDao, factDao, shortTermDao, ioDispatcher)
    }
}
```

#### 8.3 Módulo Hilt para Orquestador (`app`)

```kotlin
// app/src/main/java/com/screenassistant/di/AiModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindAiRepository(
        impl: FallbackAiRepository
    ): AiRepository

    @Binds
    @Singleton
    abstract fun bindConnectivityMonitor(
        impl: ConnectivityMonitorImpl
    ): ConnectivityMonitor

    companion object {
        @Provides
        @Singleton
        fun provideAiOrchestrator(
            localEngine: LocalInferenceEngine,
            geminiRepository: dagger.Lazy<GeminiRepository>,
            memorySystem: MemorySystem,
            connectivityMonitor: ConnectivityMonitor,
            @IoDispatcher ioDispatcher: CoroutineDispatcher
        ): AiOrchestrator {
            return AiOrchestratorImpl(
                localEngine, geminiRepository, memorySystem,
                connectivityMonitor, ioDispatcher
            )
        }
    }
}
```

### 9. Fallback Mechanism (Strategy Pattern)

```kotlin
// core/data/src/main/kotlin/com/screenassistant/core/data/remote/FallbackAiRepository.kt
package com.screenassistant.core.data.remote

import com.screenassistant.core.domain.ai.AiOrchestrator
import com.screenassistant.core.domain.model.AiProvider
import com.screenassistant.core.domain.model.ImageData
import com.screenassistant.core.domain.repository.AiRepository
import com.screenassistant.core.domain.repository.GeminiRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de AiRepository con fallback automático.
 *
 * Estrategia:
 * 1. Siempre intentar LOCAL primero (offline-first)
 * 2. Si local falla o no está listo → GEMINI (si hay internet)
 * 3. Si no hay internet ni local → mensaje de error claro
 */
@Singleton
class FallbackAiRepository @Inject constructor(
    private val orchestrator: AiOrchestrator
) : AiRepository {

    override suspend fun sendMessage(message: String, image: ImageData?): String? {
        return orchestrator.sendMessage(message)
    }

    override fun streamMessage(message: String): Flow<String> {
        return orchestrator.streamMessage(message)
    }

    override fun setLanguage(language: com.screenassistant.core.domain.model.AssistantLanguage) {
        // Delegar al orquestador que propagará a ambos motores
        orchestrator.setLanguage(language)
    }

    override fun getCurrentProvider(): AiProvider {
        return AiProvider.AUTO  // El orquestador decide
    }
}
```

```kotlin
// core/data/src/main/kotlin/com/screenassistant/core/data/ai/AiOrchestratorImpl.kt
package com.screenassistant.core.data.ai

import com.screenassistant.core.domain.ai.AiOrchestrator
import com.screenassistant.core.domain.ai.ConnectivityMonitor
import com.screenassistant.core.domain.ai.LocalInferenceEngine
import com.screenassistant.core.domain.ai.MemorySystem
import com.screenassistant.core.domain.model.AiProvider
import com.screenassistant.core.domain.repository.GeminiRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiOrchestratorImpl @Inject constructor(
    private val localEngine: LocalInferenceEngine,
    private val geminiRepository: dagger.Lazy<GeminiRepository>,
    private val memorySystem: MemorySystem,
    private val connectivityMonitor: ConnectivityMonitor,
    @com.screenassistant.core.domain.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AiOrchestrator {

    private val _activeProvider = MutableStateFlow(AiProvider.LOCAL_MLC)
    override fun activeProvider(): StateFlow<AiProvider> = _activeProvider

    private var preferredProvider: AiProvider? = null

    override suspend fun sendMessage(message: String): String? {
        return withContext(ioDispatcher) {
            // 1. Construir prompt con contexto de memoria
            val memoryContext = buildMemoryContext(message)
            val enrichedPrompt = "$memoryContext\n\nUsuario: $message"

            // 2. Seleccionar proveedor
            val provider = selectProvider()

            // 3. Ejecutar con fallback
            try {
                val response = when (provider) {
                    AiProvider.LOCAL_MLC -> localEngine.generate(enrichedPrompt)
                    AiProvider.GEMINI -> geminiRepository.get().sendMessage(enrichedPrompt)
                    AiProvider.AUTO -> localEngine.generate(enrichedPrompt)
                }

                // 4. Guardar en memoria
                memorySystem.addToShortTermMemory(message, response ?: "")

                response
            } catch (e: Exception) {
                // Fallback: si local falla, intentar Gemini
                if (provider == AiProvider.LOCAL_MLC && connectivityMonitor.checkNow()) {
                    try {
                        val fallbackResponse = geminiRepository.get().sendMessage(enrichedPrompt)
                        _activeProvider.value = AiProvider.GEMINI
                        memorySystem.addToShortTermMemory(message, fallbackResponse ?: "")
                        fallbackResponse
                    } catch (fallbackError: Exception) {
                        null
                    }
                } else {
                    null
                }
            }
        }
    }

    override fun streamMessage(message: String): Flow<String> = flow {
        val provider = selectProvider()
        val memoryContext = buildMemoryContext(message)
        val enrichedPrompt = "$memoryContext\n\nUsuario: $message"

        when (provider) {
            AiProvider.LOCAL_MLC -> emitAll(localEngine.generateStream(enrichedPrompt))
            AiProvider.GEMINI -> emitAll(geminiRepository.get().streamMessage(enrichedPrompt))
            AiProvider.AUTO -> emitAll(localEngine.generateStream(enrichedPrompt))
        }
    }

    override fun setLanguage(language: com.screenassistant.core.domain.model.AssistantLanguage) {
        // Propagar a ambos motores
        geminiRepository.get().setLanguage(language)
        // Local engine language set via PromptTemplate
    }

    override fun setPreferredProvider(provider: AiProvider?) {
        preferredProvider = provider
    }

    private suspend fun selectProvider(): AiProvider {
        // Si hay preferencia explícita, usarla
        preferredProvider?.let { return it }

        // Siempre intentar local primero (offline-first)
        if (localEngine.isReady()) return AiProvider.LOCAL_MLC

        // Fallback a Gemini si hay internet
        if (connectivityMonitor.checkNow()) return AiProvider.GEMINI

        // No hay opciones disponibles
        return AiProvider.LOCAL_MLC
    }

    private suspend fun buildMemoryContext(message: String): String {
        val recentContext = memorySystem.getRecentContext(10)
            .map { turns ->
                turns.joinToString("\n") { "Usuario: ${it.content}" }
            }

        val relevantMemory = memorySystem.searchRelevantContext(message, 3)
            .joinToString("\n") { "Recuerdo: ${it.content}" }

        return buildString {
            if (relevantMemory.isNotEmpty()) {
                appendLine("[MEMORIA RELEVANTE]:")
                appendLine(relevantMemory)
            }
            if (recentContext.isNotEmpty()) {
                appendLine("[CONTEXTO RECIENTE]:")
                appendLine(recentContext)
            }
        }
    }
}
```

### 10. Integración con OverlayViewModel

```kotlin
// CAMBIO en OverlayViewModel:
// ANTES: private val geminiRepository: GeminiRepository
// DESPUÉS:
@HiltViewModel
class OverlayViewModel @Inject constructor(
    private val aiRepository: AiRepository,  // ← CAMBIADO de GeminiRepository
    private val memorySystem: MemorySystem,   // ← NUEVO
    // ... resto igual
) : ViewModel() {

    fun sendMessage(text: String) {
        // ... resto igual hasta el prompt ...
        viewModelScope.launch(ioDispatcher) {
            try {
                // 1. Obtener contexto de memoria relevante
                val relevantMemory = memorySystem.searchRelevantContext(text, 3)

                // 2. Obtener contexto conversacional reciente
                val recentTurns = memorySystem.getRecentContext(10).first()

                // 3. Construir prompt enriquecido
                val memoryContext = if (relevantMemory.isNotEmpty()) {
                    val memoryText = relevantMemory.joinToString("\n") { it.content }
                    "\n\n[MEMORIA]: $memoryText"
                } else ""
                val conversationContext = if (recentTurns.isNotEmpty()) {
                    val history = recentTurns.joinToString("\n") { it.content }
                    "\n[CONVERSACIÓN RECIENTE]:\n$history"
                } else ""

                val prompt = "$text$temporalInfo$memoryContext$conversationContext"

                // 4. Enviar via AiRepository (local-first con fallback)
                val response = aiRepository.sendMessage(prompt, imageData)

                if (response != null) {
                    handleResponse(response)

                    // 5. Guardar turno en memoria
                    memorySystem.addToShortTermMemory(text, response)

                    // 6. Extraer hechos si aplica
                    val turn = ConversationTurn(
                        userMessage = text,
                        assistantResponse = response,
                        timestamp = Instant.now()
                    )
                    memorySystem.extractAndStoreFacts(turn)
                } else {
                    handleResponse("Vaya, me he despistado un segundo. ¿Me lo repites?")
                }
            } catch (e: Exception) {
                handleResponse("Parece que mi conexión está un poco lenta. ¿Intentamos de nuevo?")
            }
        }
    }
}
```

### 11. Implementación del Motor Local (core/ai/local)

```kotlin
// core/ai/local/src/main/kotlin/com/screenassistant/core/ai/local/MlcLlmEngine.kt
package com.screenassistant.core.ai.local

import android.content.Context
import com.screenassistant.core.domain.ai.LocalInferenceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de LocalInferenceEngine usando MLC LLM.
 *
 * Phi-3 Mini 3.8B se carga desde assets del APK (~2.3GB).
 * MLC LLM maneja la inferencia con GPU/NPU acceleration.
 */
@Singleton
class MlcLlmEngine @Inject constructor(
    private val context: Context
) : LocalInferenceEngine {

    private var mlcModel: Any? = null  // mlc-ai Tokenizer+Model
    private var isInitialized = false

    override suspend fun initialize(modelPath: String) {
        if (isInitialized) return
        try {
            // TODO: Inicializar MLC LLM con el modelo
            // val engine = MLCEngine()
            // engine.reload(modelPath)
            isInitialized = true
        } catch (e: Exception) {
            isInitialized = false
            throw e
        }
    }

    override suspend fun generate(prompt: String): String {
        if (!isInitialized) throw IllegalStateException("Motor no inicializado")
        // TODO: mlcModel?.chat.completions.create(...)
        return "Respuesta del modelo local"
    }

    override fun generateStream(prompt: String): Flow<String> = flow {
        if (!isInitialized) throw IllegalStateException("Motor no inicializado")
        // TODO: Streaming de tokens desde MLC LLM
        emit("Respuesta ")
        emit("del ")
        emit("modelo ")
        emit("local")
    }

    override fun release() {
        mlcModel = null
        isInitialized = false
    }

    override fun isReady(): Boolean = isInitialized
}
```

### 12. Extracción de Hechos (core/ai/memory)

```kotlin
// core/ai/memory/src/main/kotlin/com/screenassistant/core/ai/memory/domain/FactExtractor.kt
package com.screenassistant.core.ai.memory.domain

import com.screenassistant.core.domain.model.FactCategory
import com.screenassistant.core.domain.model.KnowledgeFact
import com.screenassistant.core.domain.model.personality.ConversationTurn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extrae hechos y prefrencias del usuario de un turno de conversación.
 * Usa reglas simples + patterns para evitar dependencia de IA externa.
 */
@Singleton
class FactExtractor @Inject constructor() {

    private val namePatterns = listOf(
        Regex("(?:me llamo|mi nombre es|soy)\\s+([A-ZÁÉÍÓÚ][a-záéíóú]+)", RegexOption.IGNORE_CASE),
        Regex("(?:tu nombre es|te llamas|eres)\\s+([A-ZÁÉÍÓÚ][a-záéíóú]+)", RegexOption.IGNORE_CASE)
    )

    private val preferencePatterns = listOf(
        Regex("(?:me gusta|prefiero|me encanta|amo)\\s+(.+)", RegexOption.IGNORE_CASE),
        Regex("(?:no me gusta|odio|detesto)\\s+(.+)", RegexOption.IGNORE_CASE)
    )

    fun extractFacts(turn: ConversationTurn): List<KnowledgeFact> {
        val facts = mutableListOf<KnowledgeFact>()
        val combinedText = "${turn.userMessage} ${turn.assistantResponse}"

        // Extraer nombres
        namePatterns.forEach { pattern ->
            pattern.find(combinedText)?.let { match ->
                val name = match.groupValues[1]
                val category = if (turn.userMessage.lowercase().contains("me llamo")) {
                    FactCategory.USER_NAME
                } else {
                    FactCategory.RELATIONSHIP
                }
                facts.add(KnowledgeFact(
                    fact = name,
                    category = category,
                    sourceConversationId = turn.id
                ))
            }
        }

        // Extraer preferencias
        preferencePatterns.forEach { pattern ->
            pattern.find(turn.userMessage)?.let { match ->
                val preference = match.groupValues[1]
                facts.add(KnowledgeFact(
                    fact = preference,
                    category = FactCategory.PREFERENCE,
                    sourceConversationId = turn.id
                ))
            }
        }

        return facts
    }
}
```

### 13. Búsqueda Semántica (core/ai/memory)

```kotlin
// core/ai/memory/src/main/kotlin/com/screenassistant/core/ai/memory/domain/SemanticSearcher.kt
package com.screenassistant.core.ai.memory.domain

import com.screenassistant.core.ai.memory.data.local.LongTermMemoryDao
import com.screenassistant.core.domain.model.MemoryEntry
import com.screenassistant.core.domain.model.MemorySource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Búsqueda semántica de memoria usando similitud de cosine simple.
 * Para Phi-3 Mini: los embeddings se calculan localmente.
 * Fallback: búsqueda por contenido LIKE cuando no hay embeddings.
 */
@Singleton
class SemanticSearcher @Inject constructor(
    private val memoryDao: LongTermMemoryDao
) {
    /**
     * Busca memoria relevante para una query dada.
     * Prioridad:
     * 1. Búsqueda semántica (si hay embeddings disponibles)
     * 2. Búsqueda por contenido LIKE (fallback)
     */
    suspend fun search(query: String, limit: Int = 5): List<MemoryEntry> {
        // Intentar búsqueda por contenido LIKE (siempre disponible)
        val results = memoryDao.searchByContent(query, limit)

        return results.map { entity ->
            MemoryEntry(
                id = entity.id,
                content = entity.content,
                source = MemorySource.LONG_TERM,
                timestamp = entity.timestamp,
                relevanceScore = calculateRelevance(query, entity.content)
            )
        }.sortedByDescending { it.relevanceScore }
    }

    private fun calculateRelevance(query: String, content: String): Float {
        // Similitud simple por palabras compartidas
        val queryWords = query.lowercase().split("\\s+".toRegex()).toSet()
        val contentWords = content.lowercase().split("\\s+".toRegex()).toSet()
        val intersection = queryWords.intersect(contentWords).size
        val union = queryWords.union(contentWords).size
        return if (union > 0) intersection.toFloat() / union else 0f
    }
}
```

### 14. Archivos build.gradle.kts

#### `settings.gradle.kts` (modificado)
```kotlin
include(":core:ai:local")
include(":core:ai:memory")
```

#### `core/ai/local/build.gradle.kts`
```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.screenassistant.core.ai.local"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // ModeloPhi-3 Mini en assets (~2.3GB)
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
        }
    }
}

dependencies {
    implementation(project(":core:domain"))

    // MLC LLM (Apache 2.0)
    implementation("org.mlc:mlc-android:0.1.0")  // Verificar versión actual

    // Hilt
    implementation("com.google.dagger:hilt-android:2.53.1")
    ksp("com.google.dagger:hilt-android-compiler:2.53.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
}
```

#### `core/ai/memory/build.gradle.kts`
```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.screenassistant.core.ai.memory"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:domain"))

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt
    implementation("com.google.dagger:hilt-android:2.53.1")
    ksp("com.google.dagger:hilt-android-compiler:2.53.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.room.testing)
}
```

### 15. Lista de Dependencias

| Librería | Versión | Uso | Licencia |
|---|---|---|---|
| MLC LLM Android | 0.1.0+ | Motor de inferencia local | Apache 2.0 |
| Phi-3 Mini 3.8B | 3.8B Q4 | Modelo bundled en assets | MIT |
| Room | 2.6.1 | Persistencia de memoria | Apache 2.0 |
| Hilt | 2.53.1 | DI multi-módulo | Apache 2.0 |
| Coroutines | 1.9.0 | Async/reactividad | Apache 2.0 |
| kotlinx-serialization | 1.7.3 | Serialización JSON | Apache 2.0 |
| Kotlin | 2.0.0 | Lenguaje | Apache 2.0 |

### 16. Testabilidad

**Para QA shift-left**:
- Todas las interfaces están en `core/domain` (JVM puro) → testables sin Android
- `LocalInferenceEngine` se mockea para tests de `AiOrchestratorImpl`
- `MemorySystem` se mockea para tests de `OverlayViewModel`
- `ConnectivityMonitor` se mockea para tests de fallback
- Room entities se testean con in-memory database

**Estructura de tests**:
```
core/ai/local/src/test/
├── MlcLlmEngineTest.kt
├── PromptTemplateTest.kt
└── MlcModelManagerTest.kt

core/ai/memory/src/test/
├── MemorySystemImplTest.kt
├── FactExtractorTest.kt
├── SemanticSearcherTest.kt
├── LongTermMemoryDaoTest.kt
├── KnowledgeFactDaoTest.kt
└── ShortTermMemoryDaoTest.kt

core/data/src/test/
├── AiOrchestratorImplTest.kt
├── FallbackAiRepositoryTest.kt
└── ConnectivityMonitorImplTest.kt

feature/overlay/src/test/
└── OverlayViewModelTest.kt  # Actualizado con mocks de AiRepository
```

### 17. Consideraciones de Performance

| Aspecto | Estrategia |
|---|---|
| **Modelo loading** | Lazy initialization en background thread, ProgressIndicator en UI |
| **Inferencia** | GPU acceleration via MLC LLM, batch processing para múltiples requests |
| **Memoria** | Embeddings pre-calculados para búsquedas rápidas |
| **APK size** | Modelo en assets comprimido (~2.3GB), considerar AAB con on-demand delivery |
| **Batería** | Pausar inferencia cuando batería < 15%, reducir frecuencia en idle |
| **Almacenamiento** | LRU cache para embeddings, rotación automática de memoria antigua |

### 18. Plan de Migración

**Fase 1: Fundamentos** (1-2 semanas)
- Crear módulos vacíos `core:ai:local` y `core:ai:memory`
- Definir interfaces en `core/domain`
- Implementar Room entities y DAOs
- Tests unitarios para entidades y DAOs

**Fase 2: Inferencia Local** (2-3 semanas)
- Integrar MLC LLM en `core:ai:local`
- Bundle de Phi-3 Mini en assets
- Implementar `MlcLlmEngine`
- Tests de inferencia (requiere modelo)

**Fase 3: Sistema de Memoria** (1-2 semanas)
- Implementar `MemorySystemImpl`
- Implementar `FactExtractor`
- Implementar `SemanticSearcher`
- Tests unitarios completos

**Fase 4: Orquestador** (1 semana)
- Implementar `AiOrchestratorImpl`
- Implementar `FallbackAiRepository`
- Integrar con `OverlayViewModel`
- Tests de integración

**Fase 5: Validación** (1 semana)
- Testing completo en dispositivo real
- Validación de performance (latencia, batería)
- Validación de offline-first
- QA completo

---

**Autor**: Arquitecto
**Fecha**: 2026-08-31
**Revisores**: QA (shift-left), Desarrollador (viabilidad técnica)
