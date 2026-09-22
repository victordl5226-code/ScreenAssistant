# ADR-026: Memoria Semantica con RAG (Retrieval-Augmented Generation)

## Estado: Propuesto (2026-09-06)

## Contexto

El sistema de memoria actual (`core/ai/memory`) almacena hechos extraidos por regex (`FactExtractorImpl`) y conversaciones en Room DB. La busqueda es **texto exacto** via queries `LIKE`:

```sql
-- KnowledgeFactDao.searchByQuery()
WHERE subject LIKE '%' || :query || '%'
   OR predicate LIKE '%' || :query || '%'
   OR `object` LIKE '%' || :query || '%'
```

**Problema**: No existe comprension semantica. Ejemplo:
- Usuario: "¿Que hora tengo que ir al trabajo?"
- Hecho almacenado: `{subject: "lugar trabajo", predicate: "lugar trabajo", object: "Google"}`
- Busqueda LIKE "trabajo" → **NO MATCH** (porque el query contiene "ir al trabajo", no "trabajo" directamente)

**Objetivo**: Implementar embeddings vectoriales para busqueda semantica que entienda significado, no solo texto literal.

## Decision

### Opcion Seleccionada: Opcion A — Sentence Transformers ligero (ONNX)

| Criterio | Opcion A (ONNX+MiniLM) | Opcion B (TF-IDF) | Opcion C (SimHash) |
|---|---|---|---|
| Precision semantica | **Alta** (95%+ cosine sim) | Media (keyword overlap) | Baja (hash collision) |
| Tamano modelo | ~23MB (INT8 quantized) | 0MB | 0MB |
| Latencia inference | ~50-100ms/dispositivo | ~5ms | ~2ms |
| Calidad "trabajo" → "empleo" | **Si** | No | Aproximada |
| Dependencias | ONNX Runtime (~3MB AAR) | Ninguna | Ninguna |
| Complejidad implementacion | Media | Baja | Baja |

**Justificacion**: Para RAG funcional, la precision semantica es critica. Un modelo de 23MB con 384 dimensiones es aceptable considerando que el APK ya contiene un modelo LLM de ~2.3GB. La latencia de 50-100ms es irrelevante para busquedas de memoria (no es hot path).

**Alternativas descartadas**:
- **TF-IDF (Opcion B)**: No resuelve el problema core — "¿Que hora tengo que ir al trabajo?" no matchea con "Mi empleo empieza a las 9am" porque no hay overlap de keywords significativo.
- **SimHash (Opcion C)**: Muy impreciso para este caso de uso. La colision de hashes produce falsos positivos y negativos.

### Modelo: all-MiniLM-L6-v2 (INT8 quantized)

| Aspecto | Valor |
|---|---|
| Dimensiones | 384 |
| Tamano archivo | ~23MB (quantized INT8) |
| Max sequence length | 256 tokens |
| Licencia | Apache 2.0 |
| Language support | Multilingual (incluye espanol) |
| Latencia estimada | 50-100ms en CPU (ARM64) |

**Fuente del modelo**: `sentence-transformers/all-MiniLM-L6-v2` via ONNX Runtime

**Libreria de referencia**: `io.gitlab.shubham0204:sentence-embeddings:v6.1` (Maven Central)
- Usa Rust + ONNX Runtime para inferencia en-device
- Soporta all-MiniLM-L6-v2 nativamente
- AAR disponible, sin necesidad de compilar desde fuente
- Apache 2.0 license

### Busqueda Vectorial: Brute-force cosine similarity

**Por que NO sqlite-vec/sqlite-vss**:
- El dataset de memoria es pequeno (maximo ~10K hechos en uso real)
- Brute-force sobre 10K vectores de 384 dim = ~15ms (aceptable)
- Evita complejidad de integrar extensiones SQLite nativas
- Room DAO ya maneja la persistencia; los vectores van como BLOB

**Estrategia**:
1. Obtener TODOS los vectores de la tabla (cargados en memoria)
2. Calcular cosine similarity contra el query embedding
3. Retornar top-K mas relevantes

Para el volumen esperado (<10K hechos), esto es O(15ms) y completamente funcional.

## Arquitectura

### Diagrama de Capas

```
┌─────────────────────────────────────────────────────┐
│                   PRESENTATION                       │
│  OverlayViewModel                                    │
│    └─ MemoryEnricher.getRelevantContext(query)       │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│                     DOMAIN                           │
│  SemanticMemoryRepository (interfaz)                 │
│    ├─ searchSimilar(query, limit) → List<FactResult> │
│    ├─ storeFact(fact, text)                          │
│    └─ isReady(): Boolean                             │
│                                                      │
│  CosineSimilarity (util pura, JVM)                   │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│                  DATA/IMPLEMENTATION                  │
│  core/ai/memory/semantic/                            │
│    ├─ OnDeviceEmbeddingGenerator                     │
│    │    └─ SentenceEmbedding (ONNX Runtime)          │
│    ├─ BruteForceVectorStore                          │
│    │    └─ Cosine similarity sobre FloatArray        │
│    ├─ RoomVectorDao                                  │
│    │    └─ Almacena embedding como BLOB              │
│    └─ SemanticMemoryRepositoryImpl                   │
│         └─ Coordina embedding + store + search       │
└─────────────────────────────────────────────────────┘
```

### Nuevos Archivos

```
core/ai/memory/src/main/kotlin/com/screenassistant/core/ai/memory/
├── semantic/
│   ├── EmbeddingGenerator.kt           # Interfaz (domain-like, en memory module)
│   ├── OnDeviceEmbeddingGenerator.kt   # Implementacion con ONNX Runtime
│   ├── VectorStore.kt                  # Interfaz de almacenamiento vectorial
│   ├── BruteForceVectorStore.kt        # Implementacion brute-force
│   ├── CosineSimilarity.kt             # Utilidad pura de similaridad
│   ├── SemanticFactEntity.kt           # Room Entity con embedding BLOB
│   ├── SemanticFactDao.kt              # Room DAO
│   └── SemanticMemoryRepositoryImpl.kt # Implementacion del repositorio
├── di/
│   └── SemanticMemoryModule.kt         # Modulo Hilt nuevo
└── MemoryDatabase.kt                   # MODIFICADO: +1 entity, +1 DAO, version 2
```

### Archivos a Modificar

```
core/ai/memory/
├── build.gradle.kts                    # +ONNX Runtime dependency
├── src/main/kotlin/.../
│   ├── MemoryDatabase.kt               # +SemanticFactEntity, +SemanticFactDao, version=2
│   ├── MemoryEnricher.kt               # MODIFICADO: usar SemanticMemoryRepository
│   └── di/MemoryModule.kt              # MODIFICADO: proveer SemanticMemoryRepository
│
core/domain/
└── src/main/kotlin/.../repository/ai/
    └── SemanticMemoryRepository.kt     # NUEVO: interfaz en domain
```

## Contratos Clave

### 1. Interfaz de Dominio (core/domain)

```kotlin
// core/domain/src/main/kotlin/com/screenassistant/core/domain/repository/ai/SemanticMemoryRepository.kt
package com.screenassistant.core.domain.repository.ai

/**
 * Repositorio de memoria semantica con embeddings vectoriales.
 * Permite busqueda por significado, no por texto exacto.
 *
 * Implementado en core/ai:memory con ONNX Runtime.
 */
interface SemanticMemoryRepository {

    /**
     * Busca hechos semanticamente similares a la query.
     *
     * @param query Texto del usuario (ej: "¿Que hora tengo que ir al trabajo?")
     * @param limit Maximo de resultados
     * @return Lista de hechos ordenados por similitud descendente
     */
    suspend fun searchSimilar(query: String, limit: Int = 5): List<SemanticFactResult>

    /**
     * Almacena un hecho con su embedding vectorial.
     *
     * @param factId ID del hecho existente en knowledge_facts
     * @param text Texto para generar embedding (subject + predicate + object)
     * @param category Categoria del hecho
     */
    suspend fun storeFactEmbedding(factId: String, text: String, category: String)

    /**
     * Indica si el generador de embeddings esta listo.
     * Puede ser false durante la inicializacion del modelo.
     */
    fun isReady(): Boolean

    /**
     * Reconstruye todos los embeddings desde knowledge_facts.
     * Util para migraciones o si el modelo cambia.
     */
    suspend fun rebuildAllEmbeddings()
}

/**
 * Resultado de busqueda semantica.
 */
data class SemanticFactResult(
    val factId: String,
    val text: String,
    val category: String,
    val confidence: Float,
    val similarity: Float,  // 0.0 a 1.0 (cosine similarity)
    val timestamp: Long,
)
```

### 2. EmbeddingGenerator (core/ai/memory)

```kotlin
// core/ai/memory/src/main/kotlin/com/screenassistant/core/ai/memory/semantic/EmbeddingGenerator.kt
package com.screenassistant.core.ai.memory.semantic

/**
 * Genera embeddings vectoriales a partir de texto.
 * Implementado con ONNX Runtime + all-MiniLM-L6-v2.
 */
interface EmbeddingGenerator {

    /**
     * Genera un embedding de 384 dimensiones para el texto dado.
     *
     * @param text Texto a embeber (max 256 tokens)
     * @return FloatArray de 384 dimensiones
     * @throws IllegalStateException si el modelo no esta inicializado
     */
    suspend fun embed(text: String): FloatArray

    /**
     * Genera embeddings para multiples textos (batch).
     * Mas eficiente que llamar embed() repetidamente.
     */
    suspend fun embedBatch(texts: List<String>): List<FloatArray>

    /**
     * Indica si el modelo esta listo para inferencia.
     */
    fun isReady(): Boolean
}
```

### 3. VectorStore (core/ai/memory)

```kotlin
// core/ai/memory/src/main/kotlin/com/screenassistant/core/ai/memory/semantic/VectorStore.kt
package com.screenassistant.core.ai.memory.semantic

/**
 * Almacen y busqueda de vectores en memoria.
 * Para el volumen esperado (<10K vectores), brute-force es suficiente.
 */
interface VectorStore {

    /**
     * Agrega un vector con metadata asociada.
     */
    suspend fun add(id: String, vector: FloatArray, metadata: VectorMetadata)

    /**
     * Busca los K vectores mas similares al query.
     *
     * @param queryVector Vector de consulta
     * @param k Numero de vecinos cercanos
     * @return Lista ordenada por similitud descendente
     */
    suspend fun search(queryVector: FloatArray, k: Int = 5): List<VectorSearchResult>

    /**
     * Elimina un vector por ID.
     */
    suspend fun remove(id: String)

    /**
     * Elimina todos los vectores.
     */
    suspend fun clear()

    /**
     * Carga todos los vectores desde la fuente persistente.
     */
    suspend fun loadAll(entries: List<VectorEntry>)
}

data class VectorMetadata(
    val factId: String,
    val text: String,
    val category: String,
    val confidence: Float,
    val timestamp: Long,
)

data class VectorEntry(
    val id: String,
    val vector: FloatArray,
    val metadata: VectorMetadata,
)

data class VectorSearchResult(
    val id: String,
    val similarity: Float,
    val metadata: VectorMetadata,
)
```

### 4. Room Entity y DAO (core/ai/memory)

```kotlin
// core/ai/memory/src/main/kotlin/com/screenassistant/core/ai/memory/semantic/SemanticFactEntity.kt
@Entity(
    tableName = "semantic_facts",
    indices = [Index(value = ["factId"], unique = true)],
)
data class SemanticFactEntity(
    @PrimaryKey
    val id: String,
    val factId: String,           // FK a knowledge_facts.id
    val text: String,             // Texto embebido (subject + predicate + object)
    val embedding: ByteArray,     // Vector serializado como BLOB (384 floats = 1536 bytes)
    val category: String,
    val confidence: Float,
    val timestamp: Long,
) {
    // ByteArray equality override
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SemanticFactEntity) return false
        return id == other.id && factId == other.factId
    }
    override fun hashCode(): Int = id.hashCode() * 31 + factId.hashCode()
}
```

```kotlin
// core/ai/memory/src/main/kotlin/com/screenassistant/core/ai/memory/semantic/SemanticFactDao.kt
@Dao
interface SemanticFactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SemanticFactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SemanticFactEntity>)

    @Query("SELECT * FROM semantic_facts")
    suspend fun getAll(): List<SemanticFactEntity>

    @Query("SELECT * FROM semantic_facts WHERE factId = :factId")
    suspend fun getByFactId(factId: String): SemanticFactEntity?

    @Query("DELETE FROM semantic_facts WHERE factId = :factId")
    suspend fun deleteByFactId(factId: String)

    @Query("DELETE FROM semantic_facts")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM semantic_facts")
    suspend fun count(): Int
}
```

### 5. MemoryEnricher (MODIFICADO)

```kotlin
// CAMBIO en MemoryEnricher:
// ANTES: memoryStore.search(query, maxFacts)
// DESPUES: semanticMemoryRepository.searchSimilar(query, maxFacts) con fallback a LIKE
@Singleton
class MemoryEnricher @Inject constructor(
    private val shortTermMemory: ShortTermMemory,
    private val memoryStore: MemoryStore,
    private val factExtractor: FactExtractor,
    private val semanticMemoryRepository: SemanticMemoryRepository,  // NUEVO
    private val logger: MemoryLogger,
) {

    suspend fun getRelevantContext(query: String, maxFacts: Int = 3): String {
        return try {
            val facts = if (semanticMemoryRepository.isReady()) {
                // Busqueda semantica (RAG)
                semanticMemoryRepository.searchSimilar(query, maxFacts)
                    .map { result ->
                        MemoryEntry(
                            id = result.factId,
                            role = MemoryRole.USER,
                            content = result.text,
                            timestamp = result.timestamp,
                            type = MemoryType.FACT,
                        )
                    }
            } else {
                // Fallback: busqueda LIKE (actual)
                memoryStore.search(query, maxFacts)
            }

            if (facts.isNotEmpty()) {
                val factsText = facts.joinToString("; ") { it.content }
                "\n[CONOCIMIENTO DEL USUARIO]: $factsText"
            } else {
                ""
            }
        } catch (e: Exception) {
            logger.w(TAG, "Semantic memory search failed, falling back to LIKE", e)
            // Fallback a busqueda LIKE
            try {
                val fallbackFacts = memoryStore.search(query, maxFacts)
                if (fallbackFacts.isNotEmpty()) {
                    val factsText = fallbackFacts.joinToString("; ") { it.content }
                    "\n[CONOCIMIENTO DEL USUARIO]: $factsText"
                } else ""
            } catch (e2: Exception) {
                ""
            }
        }
    }

    suspend fun persistTurn(userMessage: String, assistantResponse: String) {
        // ... existente ...

        // NUEVO: generar embedding para hechos nuevos
        try {
            val facts = factExtractor.extractFromMessage(userMessage)
            for (fact in facts) {
                memoryStore.saveFact(fact)
                // Generar embedding para busqueda semantica
                val textToEmbed = "${fact.subject}: ${fact.predicate} = ${fact.`object`}"
                semanticMemoryRepository.storeFactEmbedding(
                    factId = fact.id,
                    text = textToEmbed,
                    category = fact.category.name,
                )
            }
        } catch (e: Exception) {
            logger.w(TAG, "Fact extraction/embedding failed", e)
        }
    }
}
```

### 6. Cosine Similarity (Utilidad pura, JVM)

```kotlin
// core/ai/memory/src/main/kotlin/com/screenassistant/core/ai/memory/semantic/CosineSimilarity.kt
package com.screenassistant.core.ai.memory.semantic

import kotlin.math.sqrt

/**
 * Calcula la similitud coseno entre dos vectores.
 * Utilidad pura — sin dependencias Android.
 */
object CosineSimilarity {

    /**
     * Similitud coseno entre dos vectores de misma dimension.
     * @return Float entre -1.0 y 1.0 (1.0 = identicos, 0.0 = ortogonales)
     */
    fun calculate(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have same dimension: ${a.size} vs ${b.size}" }

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator == 0f) 0f else dotProduct / denominator
    }
}
```

## Modificaciones de Room DB

### Migracion v1 → v2

```kotlin
// En MemoryDatabase.kt
@Database(
    entities = [
        LongTermMemoryEntity::class,
        KnowledgeFactEntity::class,
        SemanticFactEntity::class,  // NUEVO
    ],
    version = 2,
    exportSchema = false,
)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun longTermMemoryDao(): LongTermMemoryDao
    abstract fun knowledgeFactDao(): KnowledgeFactDao
    abstract fun semanticFactDao(): SemanticFactDao  // NUEVO
}
```

```kotlin
// Migracion en MemoryModule.kt
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS semantic_facts (
                id TEXT NOT NULL PRIMARY KEY,
                factId TEXT NOT NULL,
                text TEXT NOT NULL,
                embedding BLOB NOT NULL,
                category TEXT NOT NULL,
                confidence REAL NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_semantic_facts_factId ON semantic_facts(factId)")
    }
}
```

## DI (Hilt)

### Nuevo Modulo: SemanticMemoryModule

```kotlin
// core/ai/memory/src/main/kotlin/com/screenassistant/core/ai/memory/di/SemanticMemoryModule.kt
@Module
@InstallIn(SingletonComponent::class)
object SemanticMemoryModule {

    @Provides
    @Singleton
    fun provideEmbeddingGenerator(
        @ApplicationContext context: Context,
    ): EmbeddingGenerator {
        return OnDeviceEmbeddingGenerator(context)
    }

    @Provides
    @Singleton
    fun provideVectorStore(): VectorStore {
        return BruteForceVectorStore()
    }

    @Provides
    @Singleton
    fun provideSemanticFactDao(
        db: MemoryDatabase,
    ): SemanticFactDao = db.semanticFactDao()

    @Provides
    @Singleton
    fun provideSemanticMemoryRepository(
        embeddingGenerator: EmbeddingGenerator,
        vectorStore: VectorStore,
        semanticFactDao: SemanticFactDao,
    ): SemanticMemoryRepository {
        return SemanticMemoryRepositoryImpl(embeddingGenerator, vectorStore, semanticFactDao)
    }
}
```

## Testing

### Estrategia de Test

| Capa | Tipo | Mock | Archivo |
|---|---|---|---|
| CosineSimilarity | Unit | Ninguno | `CosineSimilarityTest.kt` |
| BruteForceVectorStore | Unit | EmbeddingGenerator mock | `BruteForceVectorStoreTest.kt` |
| OnDeviceEmbeddingGenerator | Integration | Modelo real | `OnDeviceEmbeddingGeneratorTest.kt` |
| SemanticMemoryRepositoryImpl | Unit | EmbeddingGenerator + VectorStore mocks | `SemanticMemoryRepositoryImplTest.kt` |
| SemanticFactDao | AndroidTest | Room in-memory DB | `SemanticFactDaoTest.kt` |
| MemoryEnricher (modificado) | Unit | SemanticMemoryRepository mock | `MemoryEnricherTest.kt` (actualizado) |

### Ejemplo de Test Unitario

```kotlin
class BruteForceVectorStoreTest {

    private val vectorStore = BruteForceVectorStore()

    @Test
    fun `search returns most similar vector first`() = runTest {
        val v1 = floatArrayOf(1f, 0f, 0f)
        val v2 = floatArrayOf(0.9f, 0.1f, 0f)
        val v3 = floatArrayOf(0f, 0f, 1f)

        vectorStore.add("1", v1, mockMetadata("trabajo"))
        vectorStore.add("2", v2, mockMetadata("empleo"))
        vectorStore.add("3", v3, mockMetadata("casa"))

        val results = vectorStore.search(v1, k = 2)

        assertEquals("1", results[0].id)  // Exact match
        assertEquals("2", results[1].id)  // Similar
        assertTrue(results[0].similarity > results[1].similarity)
    }
}
```

## Dependencias Nuevas

```kotlin
// core/ai/memory/build.gradle.kts - agregar:
dependencies {
    // === ONNX Runtime para embeddings ===
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")

    // === Sentence Embeddings (alternativa: usar libreria directamente) ===
    // implementation("io.gitlab.shubham0204:sentence-embeddings:v6.1")
}
```

**Decision sobre dependencia**: Se recomienda usar `onnxruntime-android` directamente + modelo ONNX en assets, en vez de la libreria `sentence-embeddings`. Razon:
- Evita dependencia de un proyecto personal (shubham0204)
- Mayor control sobre la inicializacion y lifecycle
- El proyecto ya usa ONNX Runtime indirectamente (via `core/ai/local`)
- Modelo ONNX (~23MB) se descarga la primera vez o se incluye en assets

## Estimacion de Esfuerzo

| Fase | Tarea | Horas | Dependencia |
|---|---|---|---|
| 1 | Interfaces en core/domain | 2h | Ninguna |
| 2 | CosineSimilarity + tests | 1h | Fase 1 |
| 3 | OnDeviceEmbeddingGenerator | 6h | Fase 1 |
| 4 | BruteForceVectorStore + tests | 3h | Fase 2 |
| 5 | Room Entity + DAO + migracion | 3h | Fase 1 |
| 6 | SemanticMemoryRepositoryImpl | 4h | Fase 3,4,5 |
| 7 | Integracion MemoryEnricher | 3h | Fase 6 |
| 8 | DI wiring + tests | 2h | Fase 7 |
| 9 | Testing E2E en dispositivo | 4h | Fase 8 |
| **Total** | | **28h (~4 dias)** | |

## Orden de Implementacion

1. **Interfaces** (`core/domain`) — contratos puros, sin dependencias
2. **CosineSimilarity** — utilidad JVM pura, testeable inmediatamente
3. **EmbeddingGenerator** — integrar ONNX Runtime + modelo
4. **VectorStore** — brute-force con cosine similarity
5. **Room** — Entity + DAO + migracion v1→v2
6. **Repository** — orquestar embedding + store + dao
7. **MemoryEnricher** — integrar busqueda semantica con fallback
8. **DI** — modulo Hilt
9. **Tests** — unit + integration

## Consideraciones

### Performance
- **Embedding generation**: ~50-100ms por texto (aceptable, no es hot path)
- **Busqueda**: ~15ms para 10K vectores (brute-force)
- **Memoria**: 10K vectores × 384 floats × 4 bytes = ~15MB en RAM
- **Almacenamiento**: 10K vectores × 1536 bytes = ~15MB en disco

### Offline-First
- Modelo ONNX se incluye en assets del APK (~23MB)
- Todos los embeddings se generan en-device
- Sin dependencia de red para busqueda semantica

### Fallback
- Si el modelo no esta listo, `isReady()` retorna false
- `MemoryEnricher` cae automaticamente a busqueda LIKE
- Degradacion gradual, no fallo catastrofico

### Migracion
- Room DB migra de v1 a v2 automaticamente
- Hechos existentes NO tienen embeddings
- Se puede ejecutar `rebuildAllEmbeddings()` para poblar

## ⚠️ Ambiguedades Detectadas

1. **Modelo ONNX en assets vs descargado**: Se asume inclusion en assets (~23MB). Si el APK es demasiado grande, considerar descarga lazy a internal storage. **Interpretacion**: Assets (mas simple, fiabilidad garantizada).

2. **Manejo de hechos actualizados**: Cuando un hecho se actualiza (REPLACE), el embedding anterior queda obsoleto. **Interpretacion**: Usar `OnConflictStrategy.REPLACE` — el embedding se regenera automaticamente en `persistTurn()`.

3. **Idioma del modelo**: all-MiniLM-L6-v2 es multilingual pero optimizado para ingles. Para espanol puro, considerar `paraphrase-multilingual-MiniLM-L12-v2` (mas grande, ~420MB). **Interpretacion**: all-MiniLM-L6-v2 es suficiente para el caso de uso (hechos cortos en espanol simple).

## 🔴 Bloqueos

- **ONNX Runtime Android**: Verificar que la version 1.22.0 es compatible con el proyecto (minSdk 26, compileSdk 35)
- **Modelo ONNX**: Confirmar que el modelo INT8 quantized (~23MB) se puede incluir en assets sin exceder limites de APK

---

**Autor**: Arquitecto
**Fecha**: 2026-09-06
**Revisores**: QA (shift-left), Desarrollador (viabilidad tecnica)
