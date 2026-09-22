# ADR-MULTI: Navegación Multi-Paso por Voz

**Fecha**: 2026-08-24  
**Estado**: Aprobado  
**Autor**: Arquitecto

## Contexto

El usuario quiere encadenar comandos en una sola frase:
- "primero pon una alarma a las 7, luego envía un mensaje a Ana diciendo hola"
- "activa el monitoreo y después busca en Google el tiempo"
- "llama a Pedro, luego pon un temporizador de 10 minutos"

Actualmente `SystemCommandParser.parse()` devuelve **UN** `SystemCommand` o **UN** `CommandMarkers`. No hay forma de expresar secuencias.

## Decisión

Introducir un modelo `CommandSequence` que represente una lista ordenada de pasos ejecutables (`CommandStep`), donde cada paso puede ser un `SystemCommand` o un `CommandMarker`. El parser detecta conectores de secuencia y devuelve este modelo. La ejecución se delega a un nuevo `MultiStepExecutor` que orquesta la ejecución secuencial con feedback por paso y soporte de cancelación.

## Estructura de paquetes/módulos

### Nuevos archivos (core:domain)

```
core/domain/src/main/kotlin/com/screenassistant/core/domain/model/
├── CommandStep.kt              # Paso individual: SystemCommand | CommandMarker
├── CommandSequence.kt          # Secuencia ordenada de pasos + metadatos
├── MultiStepResult.kt          # Resultado agregado de ejecución multi-paso
└── StepFeedback.kt             # Feedback por paso para UI reactiva

core/domain/src/main/kotlin/com/screenassistant/core/domain/usecase/
├── SequenceParser.kt           # Interface: Detecta conectores + delega parsing
├── SequenceParserImpl.kt       # Impl concreta: usa parseCommand() interno
├── MultiStepExecutor.kt        # Interface: Orquesta ejecución + StateFlow feedback
└── MultiStepExecutorImpl.kt    # Impl: inyecta SystemAction + Dispatcher + maxSteps
```

### Archivos modificados

```
core/domain/src/main/kotlin/com/screenassistant/core/domain/usecase/
└── SystemCommandParser.kt      # +parseSingleCommand() público (firma idéntica a parse())

feature/overlay/src/main/kotlin/com/screenassistant/feature/overlay/
├── OverlayUiState.kt           # +5 campos multi-paso (executingStep, totalSteps, ...)
└── OverlayViewModel.kt         # Integración: SequenceParser + MultiStepExecutor inyectados
```

## Justificación

| Alternativa | Por qué se descarta |
|-------------|---------------------|
| Modificar `SystemCommandParser.parse()` para devolver `List<Any>` | Rompe contrato actual (String?/CommandMarkers), impacto masivo en tests y OverlayViewModel |
| Usar Gemini para parsear secuencias | Latencia, coste, no determinista; los conectores son patrones fijos en español |
| Ejecutar en paralelo | Requisito explícito: "esperar a que cada uno termine antes del siguiente" |
| Añadir campo `nextCommand` a `SystemCommand` | Viola SRP (SystemCommand = intención atómica), rompe biyección con wires (AccionRegistry) |

## Testabilidad (QA shift-left)

- **SequenceParser**: Pure function `parse(text): CommandSequence?` — 100% testable unitario sin mocks (usa método package-private `parseCommand()` del parser)
- **MultiStepExecutor**: Recibe `SystemAction` + `CoroutineDispatcher` + `maxSteps` por constructor — mockeable, testable con `TestCoroutineDispatcher`
- **CommandStep/CommandSequence**: Data classes inmutables — equals/hashCode automáticos, ideales para aserciones
- **Contratos inyectables**: `SystemAction` (interface), `CoroutineDispatcher` (Hilt qualifier `@IoDispatcher`) — zero Android en domain
- **StepFeedback**: StateFlow reactivo — testeable via `testing` coroutines y `StateFlow` assertions

## Contratos de Interfaces

```kotlin
// core/domain/model/CommandStep.kt
sealed interface CommandStep {
    data class Command(val command: SystemCommand) : CommandStep
    data class Marker(val marker: String) : CommandStep  // CommandMarkers.*
}

// core/domain/model/CommandSequence.kt
enum class ConnectorType {
    FIRST_THEN,      // "primero ... luego ..."
    AND_THEN,        // "... y después ..." / "... y luego ..."
    COMMA_THEN,      // "..., luego ..."
    SEMICOLON_THEN   // "...; luego ..."
}

data class CommandSequence(
    val steps: List<CommandStep>,
    val originalText: String,
    val connectorType: ConnectorType
) {
    val isMultiStep: Boolean get() = steps.size > 1
    val totalSteps: Int get() = steps.size
}

// core/domain/model/MultiStepResult.kt
data class StepResult(
    val step: CommandStep,
    val result: String,  // "Éxito: ..." | "Error: ..."
    val executedAt: Long = System.currentTimeMillis()
)

sealed class MultiStepResult {
    data class Success(val stepResults: List<StepResult>) : MultiStepResult()
    data class Cancelled(val completedSteps: List<StepResult>) : MultiStepResult()
    data class Error(
        val failedStepIndex: Int,
        val reason: String,
        val completedSteps: List<StepResult>
    ) : MultiStepResult()
}

// core/domain/model/StepFeedback.kt
data class StepFeedback(
    val currentStepIndex: Int,      // 0-based
    val totalSteps: Int,
    val currentStepDescription: String,
    val status: StepStatus,
    val stepResult: String? = null
) {
    val currentStepNumber: Int get() = currentStepIndex + 1
}

enum class StepStatus { PENDING, EXECUTING, COMPLETED, FAILED, CANCELLED }

// core/domain/usecase/SequenceParser.kt
interface SequenceParser {
    fun parse(text: String): CommandSequence?
}

// core/domain/usecase/MultiStepExecutor.kt
interface MultiStepExecutor {
    suspend fun execute(sequence: CommandSequence): MultiStepResult
    fun cancel()
    val stepFeedback: kotlinx.coroutines.flow.StateFlow<StepFeedback?>
    val isExecuting: Boolean
}
```

## Flujo de ejecución

```
Usuario: "primero pon alarma a las 7, luego busca el tiempo"
         │
         ▼
OverlayViewModel.sendMessage()
         │
         ▼
SequenceParser.parse(text) → CommandSequence?  // null = no hay conectores → flujo actual
         │
         ├─ null ──────────────────────────────► SystemCommandParser.parse() (flujo legacy)
         │
         └─ CommandSequence(steps=[SetAlarm, SearchGoogle])
                │
                ▼
         MultiStepExecutor.execute(sequence)
                │
                ├─ Paso 1: SystemAction.execute(SetAlarm) → StepResult
                │       │
                │       ▼
                │  stepFeedback.emit(StepFeedback(0, 2, "Poner alarma a las 7:00", EXECUTING))
                │       │
                │       ▼ (await)
                ├─ Paso 2: SystemAction.execute(SearchGoogle) → StepResult
                │       │
                │       ▼
                │  stepFeedback.emit(StepFeedback(1, 2, "Buscar en Google: el tiempo", EXECUTING))
                │       │
                │       ▼ (await)
                └─ MultiStepResult.Success([StepResult, StepResult])
                       │
                       ▼
              handleMultiStepResult() → concatena resultados → TTS
```

## Cancelación

- `MultiStepExecutor` mantiene `Job` de la corrutina de ejecución
- `cancel()` → `job.cancel()` → propaga `CancellationException` en `SystemAction.execute()`
- `SystemActionHandler` ya re-lanza `CancellationException` (B5) ✓
- Resultado: `MultiStepResult.Cancelled(completedSteps)` → feedback al usuario

## Compatibilidad

- `SequenceParser.parse()` retorna `null` si no detecta conectores → `OverlayViewModel` usa flujo actual
- `SystemCommandParser` **no cambia** su firma pública; se expone `parseSingleCommand()` para compatibilidad legacy
- Zero breaking changes en módulos existentes
- Los 112 tests existentes de `SystemCommandParser` **deben seguir pasando sin cambios**

## Conectores soportados (v1) — Matriz de Conectores

| Patrón | ConnectorType | Ejemplo | Precedencia |
|--------|---------------|---------|-------------|
| `primero ... luego ...` | FIRST_THEN | "primero alarma, luego mensaje" | 1 (más alta) |
| `... y después ...` | AND_THEN | "alarma y después mensaje" | 2 |
| `... y luego ...` | AND_THEN | "alarma y luego mensaje" | 2 |
| `... , luego ...` | COMMA_THEN | "alarma a las 7, luego mensaje" | 3 |
| `... ; luego ...` | SEMICOLON_THEN | "alarma; luego mensaje" | 4 (más baja) |

**Reglas de parsing**:
1. Normaliza el texto (igual que `SystemCommandParser.normalize`)
2. Busca conectores con regex ordenados por precedencia
3. Divide por conector, preserva el conector para metadatos, trim cada segmento
4. **Delegación**: Cada segmento → `SystemCommandParser.parseCommand()` (package-private, SIN ejecutar)
5. **Validación**: Si algún segmento devuelve `null` → toda la secuencia es `null` (fallback a Gemini)
6. **CommandMarkers en secuencia**: Permitidos (ej. "activa monitoreo y luego busca...") — se ejecutan en orden
7. **Límite práctico**: Máx 10 pasos por secuencia (configurable via `maxSteps` en `MultiStepExecutorImpl`)

## Feedback via StateFlow (A2)

- `MultiStepExecutor.stepFeedback: StateFlow<StepFeedback?>` — expuesto en interface
- Implementación usa `MutableStateFlow` internamente
- Emite en cada transición de paso: PENDING → EXECUTING → COMPLETED/FAILED
- `OverlayViewModel` observa en `init` y actualiza `OverlayUiState` reactivamente
- Latencia UI < 200ms entre pasos (delay interno 50ms + 100ms entre pasos)

## Estados UI Extendidos (A1) — OverlayUiState

```kotlin
data class OverlayUiState(
    // ... campos existentes ...
    // === Multi-paso ===
    val executingStep: Int = 0,           // Paso actual (1-based, 0 = ninguno)
    val totalSteps: Int = 0,              // Total de pasos en la secuencia
    val currentStepDescription: String = "", // Descripción legible del paso actual
    val isMultiStep: Boolean = false,     // True si hay secuencia multi-paso activa
    val multiStepError: String? = null    // Error del paso fallido (null si éxito)
)
```

Todos los campos son **opcionales con defaults** → zero breaking changes en UI existente.

## Inyección de Dependencias (Hilt)

```kotlin
// AppModule.kt
@Provides @Singleton
fun provideSequenceParser(systemCommandParser: SystemCommandParser): SequenceParser =
    SequenceParserImpl(systemCommandParser)

@Provides @Singleton
fun provideMultiStepExecutor(
    systemAction: SystemAction,
    @IoDispatcher ioDispatcher: CoroutineDispatcher
): MultiStepExecutor =
    MultiStepExecutorImpl(systemAction, ioDispatcher, maxSteps = 10)

// OverlayViewModel constructor:
@Inject constructor(
    ...,
    private val sequenceParser: SequenceParser,
    private val multiStepExecutor: MultiStepExecutor,
    ...
)
```

## Tests de Contrato (Documentados para QA)

### SystemCommandParserTest — parseSingleCommand

```kotlin
// Verificar que parseSingleCommand(input) == parse(input) para TODOS los inputs sin conectores
@Test
fun `parseSingleCommand devuelve mismo resultado que parse para comandos simples`() {
    val inputs = listOf(
        "llama a Ana",
        "pon alarma a las 7",
        "busca gatos",
        "temporizador de 5 minutos",
        "abre whatsapp",
        "sube el volumen",
        "habla en ingles",
        "recuerda comprar leche",
        "navega a la oficina",
        "ayuda",
        "repite",
        "activar monitoreo",
        "analiza mi pantalla"
    )
    inputs.forEach { input ->
        assertEquals(parser.parse(input), parser.parseSingleCommand(input))
    }
}
```

### SequenceParserTest — Cobertura ≥ 90%

- Detección de cada conector con precedencia correcta
- Split inteligente preservando conectores
- Segmentos inválidos → null (fallback Gemini)
- CommandMarkers en secuencia
- Límite 10 pasos
- Casos edge: texto vacío, solo conectores, espacios extra

### MultiStepExecutorTest — Cobertura ≥ 85%

- Ejecución secuencial 2-5 pasos verificada
- Feedback StateFlow emite en orden correcto
- Cancelación en medio: pasos previos completados, resto cancelados
- Error en paso: detiene secuencia, devuelve Error con completedSteps
- TestCoroutineDispatcher para control temporal
- SystemAction mockeado

## Métricas de Aceptación

- ✅ 100% comandos simples funcionan igual (regresión 0)
- ✅ Secuencias 2-5 pasos: ejecución secuencial verificada
- ✅ Cancelación en medio: pasos previos completados, resto cancelados
- ✅ Feedback por paso visible en UI (< 200ms latency entre pasos)
- ✅ Cobertura tests: SequenceParser ≥ 90%, MultiStepExecutor ≥ 85%
- ✅ 112 tests existentes de SystemCommandParser pasan sin cambios

## ⚠️ Ambigüedades Detectadas y Resolución

| Ambigüedad | Resolución Arquitectónica |
|------------|---------------------------|
| **B1 vs B3**: `SequenceParserImpl` debe delegar via `parseSingleCommand()` (público, retorna `String?` ejecutado) PERO necesita `SystemCommand` para `CommandStep.Command` | **Resuelto**: `SequenceParserImpl` usa método package-private `parseCommand(text): SystemCommand?` (mismo módulo `core:domain`) para construir la secuencia. `parseSingleCommand()` se mantiene público para compatibilidad legacy y testing (B3). La delegación "vía función pública" en B1 se interpreta como "usa la lógica de parsing del parser", no literalmente el retorno de `parseSingleCommand()`. |
| **B2**: `MultiStepExecutor` inyecta `SystemAction` + `CoroutineDispatcher` + `maxSteps` — ¿dónde vive `maxSteps`? | **Resuelto**: `maxSteps = 10` como parámetro por defecto en constructor de `MultiStepExecutorImpl`. Configurable vía DI en `AppModule`. |
| **A2**: `stepFeedback` en interface — ¿`StateFlow` o `Channel`? | **Resuelto**: `StateFlow<StepFeedback?>` (hot stream, último valor disponible para nuevos colectores). `MutableStateFlow` interno. |

## 🔴 Bloqueos

Ninguno. Todas las condiciones B1, B2, B3, A1, A2 resueltas en este diseño.