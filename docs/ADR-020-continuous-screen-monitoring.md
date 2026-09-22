# ADR-020: Monitoreo Continuo de Pantalla

## Estado: APROBADO (con correcciones de QA)

## Fecha: 2026-08-23

## Contexto

La app necesita mostrar en tiempo real el texto que el servicio de accesibilidad detecta en pantalla, permitiendo al usuario activar/desactivar el monitoreo y visualizar el estado actual.

### Requisitos
- Intervalo configurable (mínimo 1s, default 5s)
- Detección de desconexión del servicio de accesibilidad
- Estados claros: Idle, Active, Error
- Toggle on/off desde el overlay

## Decisiones

### Decisión 1: NO WorkManager
WorkManager tiene un mínimo de 15 minutos (inaceptable para intervalos de 5s). El ScreenContextService YA ejecuta `onAccessibilityEvent` cada ~1s. El monitoreo reutiliza el mecanismo existente con 0 coste adicional.

### Decisión 2: CoroutineScope en Repository (no en ViewModel)
El repository es el dueño del `CoroutineScope` y del `MutableStateFlow<ScreenMonitoringState>`. Esto garantiza que:
- El scope sobrevive a rotaciones de pantalla
- El estado es un single source of truth
- El ViewModel solo delega, no posee el lifecycle del monitoreo

### Decisión 3: UseCase como passthrough limpio
`CaptureScreenContextUseCase.toggleMonitoring()` es un delegador sin lógica. La cadena es:

```
ViewModel.toggleMonitoring()
  → CaptureScreenContextUseCase.toggleMonitoring()  [delega, sin lógica]
    → ScreenContextRepository.toggleMonitoring()    [cambia el Job + emite estado]
```

### Decisión 4: @IoDispatcher inyectado
El `ScreenContextRepositoryImpl` recibe `@IoDispatcher` por constructor. Esto permite:
- Control total del dispatcher en tests (reemplazar por TestDispatcher)
- Alineación con el patrón existente del proyecto (OverlayViewModel ya lo usa)
- El `AppModule` YA provee el binding `@IoDispatcher → Dispatchers.IO` (línea 278-280)

### Decisión 5: Detección de Error por timeout
El repository implementa un watchdog que:
1. Registra `lastUpdateTime` cuando el texto cambia
2. Cada intervalo, verifica si pasó `TIMEOUT_MS` (15s) sin cambios
3. Si hay timeout → emite `ScreenMonitoringState.Error`
4. El usuario puede reintentar con `toggleMonitoring()` (vuelve a Active)

### Decisión 6: Seguridad de coroutineScope
El `CoroutineScope` se crea con `ioDispatcher + SupervisorJob()`. El `SupervisorJob` evita que un error en la coroutine de monitoreo crashee el scope completo. El scope se cancela en `onCleared` del repository (implícito por singleton + supervisor).

## Consecuencias

### Positivas
- Testabilidad: todos los dispatchers son inyectables
- Claridad: cadena ViewModel → UseCase → Repository bien definida
- Robustez: timeout detecta servicio muerto sin rely en callbacks

### Negativas
- Complejidad adicional: 1 sealed class + 1 companion object con constante
- El timeout de 15s es un valor arbitrario que podría necesitar ajuste

## Diagrama de Componentes (Corregido)

```
┌─────────────────────────────────────────────────────────┐
│                    OVERLAY FEATURE                       │
│                                                         │
│  OverlayViewModel                                       │
│    ├── @IoDispatcher (inyectado)                        │
│    ├── toggleMonitoring() → useCase.toggleMonitoring()  │
│    ├── observa useCase.monitoringState                  │
│    └── mapea a OverlayUiState                           │
└─────────────────────┬───────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────┐
│                    CORE:DOMAIN                           │
│                                                         │
│  ScreenMonitoringState (sealed class)                   │
│    ├── Idle                                             │
│    ├── Active(intervalMs, screenText)                   │
│    └── Error(message)                                   │
│                                                         │
│  ScreenContextRepository (interfaz)                     │
│    ├── monitoringState: StateFlow<ScreenMonitoringState>│
│    ├── startMonitoring(intervalMs)                      │
│    └── stopMonitoring()                                 │
│                                                         │
│  CaptureScreenContextUseCase                             │
│    ├── toggleMonitoring() → delega al repo              │
│    └── monitoringState → expose del repo                 │
└─────────────────────┬───────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────┐
│                    CORE:DATA                             │
│                                                         │
│  ScreenContextRepositoryImpl                            │
│    ├── @IoDispatcher (inyectado)                        │
│    ├── CoroutineScope(ioDispatcher + SupervisorJob())   │
│    ├── monitoringJob: Job?                              │
│    ├── _monitoringState: MutableStateFlow                │
│    ├── startMonitoring() → lanza coroutine watchdog     │
│    ├── stopMonitoring() → cancela job + Idle            │
│    └── TIMEOUT_MS = 15000L                              │
│                                                         │
│  DataModule                                             │
│    └── @Binds ScreenContextRepositoryImpl → Repository  │
└─────────────────────────────────────────────────────────┘
```

## Estructura de Archivos

### Crear
| Archivo | Descripción |
|---------|-------------|
| `core/domain/model/ScreenMonitoringState.kt` | Sealed class: Idle, Active, Error |

### Modificar
| Archivo | Cambios |
|---------|---------|
| `core/domain/repository/ScreenContextRepository.kt` | +monitoringState, +startMonitoring, +stopMonitoring |
| `core/data/repository/ScreenContextRepositoryImpl.kt` | +@IoDispatcher, +monitoringScope, +watchdog, +TIMEOUT_MS |
| `core/domain/usecase/CaptureScreenContextUseCase.kt` | +toggleMonitoring() (passthrough), +monitoringState |
| `feature/overlay/OverlayUiState.kt` | +isMonitoring, +monitoringIntervalMs, +monitoringScreenText |
| `feature/overlay/OverlayViewModel.kt` | +toggleMonitoring(), +observar monitoringState |
| `feature/overlay/AssistantOverlayUI.kt` | +Botón toggle + burbuja de monitoreo |
| `core/data/src/test/.../ScreenContextRepositoryImplTest.kt` | +Tests de monitoreo |
| `feature/overlay/src/test/.../OverlayViewModelTest.kt` | +Tests de toggle |

## Contratos de Interfaces Clave

### ScreenContextRepository (interfaz actualizada)
```kotlin
interface ScreenContextRepository {
    val screenText: StateFlow<String>
    val monitoringState: StateFlow<ScreenMonitoringState>
    suspend fun captureScreenshot(): ImageData?
    fun updateScreenText(text: String)
    fun startMonitoring(intervalMs: Long)
    fun stopMonitoring()
}
```

### ScreenMonitoringState (nueva)
```kotlin
sealed class ScreenMonitoringState {
    data object Idle : ScreenMonitoringState()
    data class Active(
        val intervalMs: Long,
        val screenText: String = ""
    ) : ScreenMonitoringState()
    data class Error(val message: String) : ScreenMonitoringState()
}
```

### CaptureScreenContextUseCase (actualizado)
```kotlin
class CaptureScreenContextUseCase(
    private val screenContextRepository: ScreenContextRepository
) {
    suspend fun captureScreenshot(): ImageData? =
        screenContextRepository.captureScreenshot()

    fun getScreenText(): String =
        screenContextRepository.screenText.value

    val monitoringState: StateFlow<ScreenMonitoringState>
        get() = screenContextRepository.monitoringState

    fun toggleMonitoring(intervalMs: Long = 5000L) {
        val current = screenContextRepository.monitoringState.value
        when (current) {
            is ScreenMonitoringState.Active -> screenContextRepository.stopMonitoring()
            else -> screenContextRepository.startMonitoring(intervalMs)
        }
    }
}
```

## Correcciones de QA (2026-08-23)

### CRÍTICO-1: @IoDispatcher en ScreenContextRepositoryImpl
**Problema**: Constructor vacío → imposible controlar dispatcher en tests.
**Solución**: Inyectar `@IoDispatcher private val ioDispatcher: CoroutineDispatcher` por constructor. El binding ya existe en `AppModule.provideIoDispatcher()` (línea 278-280). `DataModule` usa `@Binds` que resuelve el `@Inject constructor` automáticamente.

### CRÍTICO-2: Mecanismo de detección de Error
**Problema**: Estado `Error` en sealed class pero sin CÓMO detectarlo. El flow `screenText` simplemente deja de emitir.
**Solución**: Watchdog en `ScreenContextRepositoryImpl.startMonitoring()`:
1. Registra `lastUpdateTime` cuando el texto cambia
2. Cada `intervalMs`, verifica si pasó `TIMEOUT_MS` (15s) sin cambios
3. Timeout → `ScreenMonitoringState.Error("Servicio de accesibilidad desconectado")`
4. El usuario reintenta con `toggleMonitoring()` → vuelve a Active

### CRÍTICO-3: Ownership del estado ambiguo
**Problema**: Cadena de responsabilidad confusa sobre quién cambia el estado.
**Solución**: Cadena clara:
```
ViewModel.toggleMonitoring() 
  → CaptureScreenContextUseCase.toggleMonitoring()  [delega, sin lógica]
    → ScreenContextRepository.toggleMonitoring()    [cambia el Job + emite estado]
```
- El **Repository** es el dueño del `Job` y del `MutableStateFlow<ScreenMonitoringState>`
- El **UseCase** es un passthrough limpio (sin lógica)
- El **ViewModel** observa y mapea al UI state

## Testabilidad (para QA shift-left)

### Contratos mockeables
- `ScreenContextRepository` → interface completa mockeable
- `CaptureScreenContextUseCase` → constructor con repository mockeable
- `@IoDispatcher` → reemplazable por `StandardTestDispatcher` en tests

### Tests requeridos
1. `ScreenContextRepositoryImplTest`: startMonitoring emite Active, timeout emite Error, stopMonitoring emite Idle
2. `CaptureScreenContextUseCaseTest`: toggleMonitoring alterna entre Active e Idle
3. `OverlayViewModelTest`: toggleMonitoring refleja estado en UI
