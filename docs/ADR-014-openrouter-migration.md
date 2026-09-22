# ADR-014: Migración de Gemini SDK a OpenRouter HTTP API

**Estado**: Aprobado
**Fecha**: 2026-08-31
**Arquitecto**: Arquitecto (equipo Android)

---

## Contexto

ScreenAssistant usa actualmente `com.google.ai.client.generativeai:generativeai:0.9.0`
para comunicarse con `gemini-2.0-flash`. El SDK gestiona internamente la sesión de
chat, el historial de mensajes y la serialización de function calls.

Queremos migrar a OpenRouter API (formato OpenAI-compatible) con el modelo
`opencode/mimo-v2.5-free` para:
- Reducir dependencia de un solo proveedor de IA
- Obtener acceso a múltiples modelos vía un solo endpoint
- Controlar explícitamente el formato wire (sin capa de abstracción opaca del SDK)

## Decisión

**Reemplazar el SDK de Gemini por llamadas HTTP directas vía OkHttp** al endpoint
`https://openrouter.ai/api/v1/chat/completions`, gestionando el historial de
conversación en nuestra capa de datos.

## Alternativas consideradas

### 1. Retrofit (descartada)
- **Pros**: Tipado fuerte, interceptores, converters.
- **Contras**: Overkill para un solo endpoint; Retrofit añade anotaciones, generación de código y
  complejidad para SSE streaming (requiere adapter custom o `okhttp-sse`).
- **Veredicto**: OkHttp directo es más simple, menos dependencias, más control sobre SSE.

### 2..okhttp-sse (EventSource API) (descartada)
- **Pros**: API tipada para SSE.
- **Contras**: Dependencia adicional; para nuestro caso (parsear `data:` lines y extraer
  `choices[0].delta.content`), un `BufferedReader` sobre `ResponseBody` es suficiente
  y más transparente.
- **Veredicto**: Parseo manual con `BufferedReader` — más simple, cero dependencias extra.

### 3. Mantener SDK de Gemini (descartada)
- **Pros**: Ya funciona, battle-tested.
- **Contras**: Vendor lock-in; formato cerrado; no podemos controlar el wire;
  el SDK gestiona el historial internamente (opaco para testing).
- **Veredicto**: El objetivo es salir del vendor lock-in.

## Diseño propuesto

### Estructura de capas

```
┌─────────────────────────────────────────────────────────────┐
│                    core:domain (SIN CAMBIOS)                 │
│  GeminiRepository (interfaz) ───────────────────────>       │
│  AssistantLanguage, ImageData, SystemCommand                 │
└──────────────────────────┬──────────────────────────────────┘
                           │ implementa
┌──────────────────────────▼──────────────────────────────────┐
│                    core:data (REESCRITO)                     │
│                                                              │
│  GeminiRepositoryImpl                                        │
│    ├─ depende de: OpenRouterApiClient (interfaz)             │
│    ├─ depende de: OpenRouterToolCatalog (object)             │
│    ├─ gestiona: conversationHistory (MutableList)            │
│    ├─ gestiona: systemInstruction (String)                   │
│    └─ ejecuta: tool-call loop (mismo patrón actual)          │
│                                                              │
│  OpenRouterApiClient (interfaz) ──── testable                │
│    └─ DefaultOpenRouterApiClient (impl con OkHttp)           │
│                                                              │
│  OpenRouterModels (data classes serializables)               │
│  OpenRouterToolCatalog (tool definitions OpenAI format)      │
│                                                              │
│  ApiKeyProvider (modificado: key name)                       │
│  DataModule (modificado: DI bindings)                        │
│                                                              │
│  ❌ ELIMINADOS:                                              │
│    GenerativeModelFactory (interfaz)                         │
│    DefaultGenerativeModelFactory (impl)                      │
│    GeminiFunctionCatalog (reemplazado)                       │
└─────────────────────────────────────────────────────────────┘
```

### Nuevas clases (core:data)

#### 1. `OpenRouterModels.kt` — Modelos de request/response

```kotlin
// Request
@Serializable data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val tools: List<OpenRouterToolDefinition>? = null,
    val stream: Boolean = false
)

@Serializable data class OpenRouterMessage(
    val role: String,
    val content: JsonElement? = null,       // String o Array<ContentPart>
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null
)

@Serializable data class OpenRouterToolDefinition(
    val type: String = "function",
    val function: FunctionSpec
)

@Serializable data class FunctionSpec(
    val name: String,
    val description: String,
    val parameters: JsonElement               // JSON Schema como JsonElement
)

// Response
@Serializable data class OpenRouterResponse(
    val id: String,
    val choices: List<Choice>,
    val error: OpenRouterError? = null
)

@Serializable data class Choice(
    val index: Int,
    val message: AssistantMessage? = null,    // Non-streaming
    val delta: Delta? = null,                 // Streaming
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable data class AssistantMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null
)

@Serializable data class Delta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<DeltaToolCall>? = null
)

@Serializable data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: FunctionCall
)

@Serializable data class FunctionCall(
    val name: String,
    val arguments: String                     // JSON string, parsear con JSONObject
)

@Serializable data class DeltaToolCall(
    val index: Int,
    val id: String? = null,
    val type: String? = null,
    val function: FunctionCall? = null
)

@Serializable data class OpenRouterError(
    val message: String,
    val code: Int
)
```

**Justificación**: Usamos `JsonElement` para `content` porque OpenAI format
permite `"content": "texto"` (string) o `"content": [{...}, {...}]` (array
multimodal). `kotlinx.serialization` ya está en el proyecto (`1.7.3`).

#### 2. `OpenRouterToolCatalog.kt` — Reemplaza `GeminiFunctionCatalog`

```kotlin
object OpenRouterToolCatalog {
    val tools: List<OpenRouterToolDefinition> = listOf(
        // 8 tools en formato OpenAI:
        // { type: "function", function: { name, description, parameters: JSONSchema } }
    )

    val names: Set<String> get() = tools.map { it.function.name }.toSet()

    // Mantiene el mapeo LLM→wire para el guardián de paridad
    val wirePorNombre: Map<String, String> = mapOf(
        "open_alarms" to "abrir_alarmas",
        // ... las mismas 8 entradas
    )
}
```

**Justificación**: Misma responsabilidad que `GeminiFunctionCatalog` pero con
formato OpenAI. Se mantiene como `object` (singleton, fuente única de tools).

#### 3. `OpenRouterApiClient.kt` — Interfaz de bajo nivel

```kotlin
interface OpenRouterApiClient {
    /** Non-streaming: envía request completa, devuelve respuesta completa */
    suspend fun chatCompletion(request: OpenRouterRequest): OpenRouterResponse

    /** Streaming: envía request con stream=true, emite chunks SSE */
    fun chatCompletionStream(request: OpenRouterRequest): Flow<OpenRouterResponse>
}
```

#### 4. `DefaultOpenRouterApiClient.kt` — Implementación OkHttp

```kotlin
class DefaultOpenRouterApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : OpenRouterApiClient {

    companion object {
        const val BASE_URL = "https://openrouter.ai/api/v1/"
        const val MODEL = "opencode/mimo-v2.5-free"
    }

    override suspend fun chatCompletion(request: OpenRouterRequest): OpenRouterResponse {
        // POST /chat/completions con JSON body
        // Parsear response body con json.decodeFromString<OpenRouterResponse>()
    }

    override fun chatCompletionStream(request: OpenRouterRequest): Flow<OpenRouterResponse> = flow {
        // POST /chat/completions con stream=true
        // Leer response body línea por línea con BufferedReader
        // Parsear cada línea "data: {...}" con json.decodeFromString<OpenRouterResponse>()
        // emit() cada chunk
        // Detectar "data: [DONE]" para completar
    }
}
```

#### 5. `GeminiRepositoryImpl.kt` — Reescrito

```kotlin
class GeminiRepositoryImpl @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider,
    private val systemAction: SystemAction,
    private val memoryRepository: MemoryRepository,
    private val apiClient: OpenRouterApiClient            // ← Reemplaza GenerativeModelFactory
) : GeminiRepository {

    private var systemInstruction: String = buildInstruction(SPANISH)
    private val conversationHistory = mutableListOf<OpenRouterMessage>()  // ← NUEVO
    private var lastApiKey: String? = null

    // sendMessage: misma lógica pero usando OpenRouter format
    // streamMessage: parsea SSE chunks en vez de SDK stream
    // setLanguage: resetea history + systemInstruction
}
```

**Cambio clave**: El SDK gestionaba el historial internamente (`Chat.startChat()`).
Ahora mantenemos `conversationHistory: MutableList<OpenRouterMessage>` explícitamente.
Esto es más transparente, más testeable y más predecible.

### Cambios en `ApiKeyProvider`

```kotlin
// Cambiar todas las ocurrencias de "gemini_api_key" a "openrouter_api_key"
fun getApiKey(): String {
    return store.getString("openrouter_api_key")?.takeIf { it.isNotBlank() } ?: ""
}

fun storeApiKey(key: String) {
    store.putString("openrouter_api_key", key)
}

fun sembrarDesdeBuildConfig(buildConfigKey: String) {
    // ...
    editor.putString("openrouter_api_key", buildConfigKey)
}

fun clearApiKey() {
    store.remove("openrouter_api_key")
}
```

### Cambios en `DataModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds @Singleton
    abstract fun bindOpenRouterApiClient(impl: DefaultOpenRouterApiClient): OpenRouterApiClient

    // ELIMINAR: bindGenerativeModelFactory

    @Binds @Singleton
    abstract fun bindGeminiRepository(impl: GeminiRepositoryImpl): GeminiRepository

    // ... resto sin cambios
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
}
```

### Cambios en `build.gradle.kts`

```kotlin
dependencies {
    // ELIMINAR:
    // implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // AGREGAR:
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")   // Opcional, para EventSource
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")  // Ya existe

    // Kotlin serialization plugin (agregar al bloque plugins si no está):
    // id("org.jetbrains.kotlin.plugin.serialization")
}
```

## Testabilidad (para QA shift-left)

### Unit Tests (Mock del `OpenRouterApiClient`)

```kotlin
class GeminiRepositoryTest {
    private lateinit var apiClient: OpenRouterApiClient
    private lateinit var repo: GeminiRepositoryImpl

    @Before fun setup() {
        apiClient = mockk()
        repo = GeminiRepositoryImpl(apiKeyProvider, systemAction, memoryRepository, apiClient)
    }

    @Test fun `sendMessage envia request con formato OpenAI`() = runTest {
        coEvery { apiClient.chatCompletion(any()) } returns OpenRouterResponse(
            id = "test",
            choices = listOf(Choice(0, message = AssistantMessage("assistant", "Hola")))
        )
        val result = repo.sendMessage("hola")
        assertEquals("Hola", result)
        coVerify {
            apiClient.chatCompletion(match { req ->
                req.messages.any { it.role == "user" && it.content?.jsonPrimitive?.content == "hola" }
            })
        }
    }

    @Test fun `sendMessage ejecuta function calls`() = runTest {
        // Primera llamada: tool call
        // Segunda llamada: respuesta final
    }

    @Test fun `sendMessage sin API key devuelve aviso`() = runTest { ... }
    @Test fun `streamMessage parsea chunks SSE`() = runTest { ... }
    @Test fun `setLanguage resetea historial`() = runTest { ... }
}
```

### Integration Tests (MockWebServer)

```kotlin
class OpenRouterApiClientIntegrationTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: DefaultOpenRouterApiClient

    @Before fun setup() {
        mockWebServer = MockWebServer()
        // Configurar OkHttpClient contra mockWebServer.url("/")
    }

    @Test fun `chatCompletion parsea respuesta exitosa`() { ... }
    @Test fun `chatCompletionStream parsea chunks SSE`() { ... }
    @Test fun `chatCompletion maneja error 401`() { ... }
    @Test fun `chatCompletion maneja error 429 rate limit`() { ... }
}
```

### Cómo testeamos sin golpear la API real

| Capa | Estrategia | Mock |
|------|-----------|------|
| `GeminiRepositoryImpl` | Unit test | Mock de `OpenRouterApiClient` (interfaz) |
| `OpenRouterApiClient` | Integration test | MockWebServer (OkHttp) |
| Tool-call loop | Unit test | Respuestas predefinidas del mock |
| SSE streaming | Unit test | Flujo de `OpenRouterResponse` simulado |
| `ApiKeyProvider` | Unit test | Mock de `EncryptedPrefsStore` |

## Orden de migración (Plan paso a paso)

### Fase 1: Preparación (sin romper nada)
1. Agregar `org.jetbrains.kotlin.plugin.serialization` al plugin block de `core:data`
2. Agregar `com.squareup.okhttp3:okhttp:4.12.0` a dependencies
3. Crear `NetworkModule.kt` en `core:data/di/`
4. Crear `OpenRouterModels.kt` con todos los data classes
5. Crear `OpenRouterToolCatalog.kt` (las 8 tools)
6. Crear `OpenRouterApiClient.kt` (interfaz)
7. Crear `DefaultOpenRouterApiClient.kt` (implementación)
8. **NO eliminar nada del SDK aún**

### Fase 2: Rewriting del Repository
9. Reescribir `GeminiRepositoryImpl.kt`:
   - Cambiar constructor: `GenerativeModelFactory` → `OpenRouterApiClient`
   - Eliminar `com.google.ai.client.generativeai.*` imports
   - Implementar `sendMessage` con HTTP calls
   - Implementar `streamMessage` con SSE parsing
   - Mantener tool-call loop intacto
   - Mantener sistema de errores (M3, B5)

### Fase 3: Cleanup
10. Actualizar `ApiKeyProvider.kt`: key name
11. Actualizar `DataModule.kt`: bindings
12. Eliminar `GenerativeModelFactory.kt`
13. Eliminar `DefaultGenerativeModelFactory.kt`
14. Eliminar `GeminiFunctionCatalog.kt`
15. Actualizar `build.gradle.kts`: quitar Gemini SDK

### Fase 4: Tests
16. Reescribir `GeminiRepositoryTest.kt` (mock de `OpenRouterApiClient`)
17. Crear `OpenRouterApiClientIntegrationTest.kt` (MockWebServer)
18. Verificar que `AiOrchestratorImplTest` no necesita cambios
19. Verificar que `OverlayViewModelTest` no necesita cambios

## Archivos afectados

| Archivo | Acción | Riesgo |
|---------|--------|--------|
| `GeminiRepositoryImpl.kt` | REESCRIBIR | Alto — lógica core |
| `GeminiFunctionCatalog.kt` | ELIMINAR | Bajo |
| `DefaultGenerativeModelFactory.kt` | ELIMINAR | Bajo |
| `GenerativeModelFactory.kt` | ELIMINAR | Bajo |
| `ApiKeyProvider.kt` | MODIFICAR (key name) | Medio — migración de prefs |
| `DataModule.kt` | MODIFICAR (DI) | Medio |
| `build.gradle.kts` | MODIFICAR (deps) | Bajo |
| `NetworkModule.kt` | CREAR | Bajo |
| `OpenRouterModels.kt` | CREAR | Bajo |
| `OpenRouterToolCatalog.kt` | CREAR | Bajo |
| `OpenRouterApiClient.kt` | CREAR | Bajo |
| `DefaultOpenRouterApiClient.kt` | CREAR | Bajo |

## Notas de migración de API Key

La key de OpenRouter tiene un formato diferente a Gemini (empieza con `sk-or-...`).
El usuario deberá re-configurar la key en la app tras la migración.
La función `sembrarDesdeBuildConfig` debe actualizarse para el nuevo BuildConfig field.

## Referencias

- OpenRouter API docs: https://openrouter.ai/docs
- OpenAI Chat Completions format: https://platform.openai.com/docs/api-reference/chat
- OkHttp SSE: https://square.github.io/okhttp/4.x/okhttp-sse/okhttp3.sse/
