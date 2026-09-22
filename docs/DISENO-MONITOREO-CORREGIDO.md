# Diseño Corregido: Monitoreo Continuo de Pantalla

## Fecha: 2026-08-23
## Estado: Corregido tras revisión de QA
## Referencia: ADR-020-continuous-screen-monitoring.md

---

## Resumen de Correcciones

QA identificó 3 problemas críticos en el diseño original. Este documento presenta las correcciones aplicadas.

| # | Problema | Severidad | Estado |
|---|---------|-----------|--------|
| CRÍTICO-1 | `ScreenContextRepositoryImpl` sin `@IoDispatcher` | CRÍTICO | ✅ Corregido |
| CRÍTICO-2 | Sin mecanismo de detección de Error | CRÍTICO | ✅ Corregido |
| CRÍTICO-3 | Ownership del estado ambiguo | CRÍTICO | ✅ Corregido |

---

## Archivo 1: `core/domain/model/ScreenMonitoringState.kt` (CREAR)

```kotlin
package com.screenassistant.core.domain.model

/**
 * Estado del monitoreo continuo de pantalla.
 *
 * Transiciones:
 *   Idle → toggleMonitoring() → Active(intervalMs, screenText="")
 *   Active → toggleMonitoring() → Idle
 *   Active + timeout(15s sin cambios) → Error(message)
 *   Error → toggleMonitoring() → Active (reintento)
 */
sealed class ScreenMonitoringState {
    /** Monitoreo desactivado. Estado inicial. */
    data object Idle : ScreenMonitoringState()

    /** Monitoreo activo con intervalo y último texto detectado. */
    data class Active(
        val intervalMs: Long,
        val screenText: String = ""
    ) : ScreenMonitoringState()

    /** Error detectado (servicio desconectado, timeout). */
    data class Error(val message: String) : ScreenMonitoringState()
}
```

**Justificación**: Sealed class con 3 estadosmutuamente excluyentes. `data object` para Idle (sin datos), `data class` para Active y Error (con datos). Patrón consistente con `AnimationState` del proyecto.

---

## Archivo 2: `core/domain/repository/ScreenContextRepository.kt` (MODIFICAR)

### Estado actual (líneas 1-10)
```kotlin
interface ScreenContextRepository {
    val screenText: StateFlow<String>
    suspend fun captureScreenshot(): ImageData?
    fun updateScreenText(text: String)
}
```

### Estado corregido
```kotlin
package com.screenassistant.core.domain.repository

import com.screenassistant.core.domain.model.ImageData
import com.screenassistant.core.domain.model.ScreenMonitoringState
import kotlinx.coroutines.flow.StateFlow

interface ScreenContextRepository {
    val screenText: StateFlow<String>
    val monitoringState: StateFlow<ScreenMonitoringState>
    suspend fun captureScreenshot(): ImageData?
    fun updateScreenText(text: String)
    fun startMonitoring(intervalMs: Long)
    fun stopMonitoring()
}
```

**Cambios**:
- `+monitoringState: StateFlow<ScreenMonitoringState>` — expose del estado
- `+startMonitoring(intervalMs: Long)` — inicia el watchdog
- `+stopMonitoring()` — cancela job + emite Idle

---

## Archivo 3: `core/data/repository/ScreenContextRepositoryImpl.kt` (MODIFICAR)

### Estado actual (líneas 1-43)
Constructor vacío, sin monitoreo.

### Estado corregido
```kotlin
package com.screenassistant.core.data.repository

import com.screenassistant.core.domain.di.IoDispatcher
import com.screenassistant.core.domain.model.ImageData
import com.screenassistant.core.domain.model.ScreenMonitoringState
import com.screenassistant.core.domain.repository.ScreenCaptureProvider
import com.screenassistant.core.domain.repository.ScreenContextRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CRÍTICO-1 (QA): @IoDispatcher inyectado para testabilidad.
 * CRÍTICO-2 (QA): Watchdog con timeout para detección de Error.
 *
 * D7 (Lote 9): la captura se delega en el proveedor REGISTRADO dinámicamente.
 * Fail-soft (contrato B3): sin proveedor registrado → null.
 */
@Singleton
class ScreenContextRepositoryImpl @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ScreenContextRepository {

    @Volatile
    private var screenCaptureProvider: ScreenCaptureProvider? = null

    // === Texto de pantalla (existente) ===
    private val _screenText = MutableStateFlow("")
    override val screenText: StateFlow<String> = _screenText.asStateFlow()

    // === Monitoreo continuo (nuevo - CRÍTICO-2) ===
    private var monitoringJob: Job? = null
    private val monitoringScope = CoroutineScope(ioDispatcher + SupervisorJob())
    private val _monitoringState = MutableStateFlow<ScreenMonitoringState>(ScreenMonitoringState.Idle)
    override val monitoringState: StateFlow<ScreenMonitoringState> = _monitoringState.asStateFlow()

    /** Registro dinámico del proveedor real (el servicio de accesibilidad). */
    fun setScreenCaptureProvider(provider: ScreenCaptureProvider?) {
        screenCaptureProvider = provider
    }

    override suspend fun captureScreenshot(): ImageData? =
        screenCaptureProvider?.captureScreenshot()

    override fun updateScreenText(text: String) {
        _screenText.value = text
    }

    // === Monitoreo continuo (nuevo - CRÍTICO-2/3) ===

    override fun startMonitoring(intervalMs: Long) {
        monitoringJob?.cancel()
        val safeInterval = intervalMs.coerceAtLeast(MIN_INTERVAL_MS)
        _monitoringState.value = ScreenMonitoringState.Active(
            intervalMs = safeInterval,
            screenText = _screenText.value
        )

        monitoringJob = monitoringScope.launch {
            var lastText = _screenText.value
            var lastUpdateTime = System.currentTimeMillis()

            while (true) {
                delay(safeInterval)

                val currentText = _screenText.value
                if (currentText != lastText) {
                    // Texto cambió → servicio activo
                    lastText = currentText
                    lastUpdateTime = System.currentTimeMillis()
                    _monitoringState.update { state ->
                        (state as? ScreenMonitoringState.Active)
                            ?.copy(screenText = currentText)
                            ?: state
                    }
                } else if (System.currentTimeMillis() - lastUpdateTime > TIMEOUT_MS) {
                    // Timeout → servicio desconectado
                    _monitoringState.value = ScreenMonitoringState.Error(
                        "Servicio de accesibilidad desconectado"
                    )
                    return@launch
                }
            }
        }
    }

    override fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        _monitoringState.value = ScreenMonitoringState.Idle
    }

    companion object {
        /** Intervalo mínimo de monitoreo (1 segundo). */
        const val MIN_INTERVAL_MS = 1000L

        /** Timeout sin cambios de texto → Error (15 segundos). */
        const val TIMEOUT_MS = 15000L
    }
}
```

**Cambios**:
- **CRÍTICO-1**: Constructor ahora recibe `@IoDispatcher private val ioDispatcher: CoroutineDispatcher`
- **CRÍTICO-2**: Nuevo `monitoringScope`, `monitoringJob`, `_monitoringState`, `startMonitoring()`, `stopMonitoring()`, watchdog con timeout
- **CRÍTICO-3**: Repository es el ÚNICO dueño del Job y del MutableStateFlow

---

## Archivo 4: `core/domain/usecase/CaptureScreenContextUseCase.kt` (MODIFICAR)

### Estado actual (líneas 1-16)
```kotlin
class CaptureScreenContextUseCase(
    private val screenContextRepository: ScreenContextRepository
) {
    suspend fun captureScreenshot(): ImageData? {
        return screenContextRepository.captureScreenshot()
    }

    fun getScreenText(): String {
        return screenContextRepository.screenText.value
    }
}
```

### Estado corregido
```kotlin
package com.screenassistant.core.domain.usecase

import com.screenassistant.core.domain.model.ImageData
import com.screenassistant.core.domain.model.ScreenMonitoringState
import com.screenassistant.core.domain.repository.ScreenContextRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * CRÍTICO-3 (QA): UseCase como passthrough limpio.
 * Toggle delega al repository sin lógica adicional.
 */
class CaptureScreenContextUseCase(
    private val screenContextRepository: ScreenContextRepository
) {
    suspend fun captureScreenshot(): ImageData? {
        return screenContextRepository.captureScreenshot()
    }

    fun getScreenText(): String {
        return screenContextRepository.screenText.value
    }

    // === Monitoreo continuo (nuevo - CRÍTICO-3) ===

    /** State expuesto del monitoreo (delega al repository). */
    val monitoringState: StateFlow<ScreenMonitoringState>
        get() = screenContextRepository.monitoringState

    /**
     * Alterna el estado del monitoreo.
     * CRÍTICO-3: UseCase es un passthrough SIN lógica.
     * La lógica de cambio de estado vive en el Repository.
     */
    fun toggleMonitoring(intervalMs: Long = DEFAULT_INTERVAL_MS) {
        val currentState = screenContextRepository.monitoringState.value
        when (currentState) {
            is ScreenMonitoringState.Active -> screenContextRepository.stopMonitoring()
            else -> screenContextRepository.startMonitoring(intervalMs)
        }
    }

    companion object {
        const val DEFAULT_INTERVAL_MS = 5000L
    }
}
```

**Cambios**:
- **CRÍTICO-3**: `toggleMonitoring()` es un delegador limpio (sin lógica de negocio)
- `monitoringState` expuesto como property delegada
- UseCase NO posee el Job ni el StateFlow — solo delega

---

## Archivo 5: `feature/overlay/OverlayUiState.kt` (MODIFICAR)

### Estado actual (líneas 1-14)
```kotlin
data class OverlayUiState(
    val assistantText: String = "",
    val animationState: AnimationState = AnimationState.IDLE,
    val isListening: Boolean = false,
    val inputText: String = "",
    val isLoading: Boolean = false,
    val currentOutfitIndex: Int = 0,
    val showHelpCard: Boolean = false
)
```

### Estado corregido
```kotlin
package com.screenassistant.feature.overlay

data class OverlayUiState(
    val assistantText: String = "",
    val animationState: AnimationState = AnimationState.IDLE,
    val isListening: Boolean = false,
    val inputText: String = "",
    val isLoading: Boolean = false,
    val currentOutfitIndex: Int = 0,
    val showHelpCard: Boolean = false,
    // === Monitoreo continuo (nuevo) ===
    val isMonitoring: Boolean = false,
    val monitoringIntervalMs: Long = 5000L,
    val monitoringScreenText: String = ""
)
```

**Cambios**: +3 campos para reflejar el estado de monitoreo en la UI.

---

## Archivo 6: `feature/overlay/OverlayViewModel.kt` (MODIFICAR)

### Cambios a implementar

Agregar al ViewModel:

```kotlin
// === Monitoreo continuo (nuevo) ===

/** Alterna el monitoreo. Delega al UseCase (CRÍTICO-3). */
fun toggleMonitoring() {
    captureScreenContextUseCase.toggleMonitoring()
}

/** Observa el estado de monitoreo y lo mapea al UI state. */
// En init{}:
init {
    viewModelScope.launch(ioDispatcher) {
        captureScreenContextUseCase.monitoringState.collect { state ->
            _uiState.update { ui ->
                ui.copy(
                    isMonitoring = state is ScreenMonitoringState.Active,
                    monitoringIntervalMs = (state as? ScreenMonitoringState.Active)?.intervalMs ?: ui.monitoringIntervalMs,
                    monitoringScreenText = (state as? ScreenMonitoringState.Active)?.screenText ?: ""
                )
            }
        }
    }
}
```

**Justificación**: ViewModel solo observa y mapea. No posee el Job ni el Scope de monitoreo. Cadena clara: VM → UseCase → Repository.

---

## Cadena de Responsabilidad (CRÍTICO-3 - Final)

```
┌──────────────────────────────────────────────────────────────┐
│ OverlayViewModel                                              │
│   toggleMonitoring()                                         │
│     → captureScreenContextUseCase.toggleMonitoring()          │
│       [DELEGA, sin lógica]                                   │
│         → screenContextRepository.toggleMonitoring()          │
│           [CAMBIA el Job + EMITE el estado]                  │
│                                                              │
│ Repository es el ÚNICO dueño de:                             │
│   - monitoringScope (CoroutineScope)                         │
│   - monitoringJob (Job?)                                     │
│   - _monitoringState (MutableStateFlow<ScreenMonitoringState>)│
└──────────────────────────────────────────────────────────────┘
```

---

## Verificación de Correcciones

### ✅ CRÍTICO-1: @IoDispatcher
- [x] `ScreenContextRepositoryImpl` recibe `@IoDispatcher` por constructor
- [x] `AppModule.provideIoDispatcher()` ya existe (línea 278-280)
- [x] `DataModule.bindScreenContextRepository()` resuelve el `@Inject constructor` automáticamente
- [x] Tests pueden inyectar `StandardTestDispatcher` en su lugar
- [x] `core/domain/di/IoDispatcher.kt` existe y contiene la anotación

### ✅ CRÍTICO-2: Detección de Error
- [x] Watchdog implementado en `startMonitoring()`
- [x] `TIMEOUT_MS = 15000L` (15 segundos sin cambios → Error)
- [x] `lastUpdateTime` se actualiza cuando el texto cambia
- [x] Timeout → `ScreenMonitoringState.Error("Servicio de accesibilidad desconectado")`
- [x] Usuario puede reintentar con `toggleMonitoring()` → vuelve a Active

### ✅ CRÍTICO-3: Ownership del estado
- [x] Repository: dueño del Job + MutableStateFlow
- [x] UseCase: passthrough limpio (sin lógica)
- [x] ViewModel: solo observa y mapea al UI state
- [x] Cadena: VM → UseCase → Repository (dirección clara)
- [x] Ningún otro componente puede modificar el estado del monitoreo

---

## Testabilidad (para QA)

### Dependencias inyectables
| Componente | Dependencia | Reemplazo en test |
|-----------|------------|-------------------|
| `ScreenContextRepositoryImpl` | `@IoDispatcher CoroutineDispatcher` | `StandardTestDispatcher()` |
| `CaptureScreenContextUseCase` | `ScreenContextRepository` | `mockk<ScreenContextRepository>()` |
| `OverlayViewModel` | `CaptureScreenContextUseCase` | `mockk<CaptureScreenContextUseCase>()` |

### Contratos mockeables
- `ScreenContextRepository` → interfaz completa mockeable
- `ScreenContextRepository.monitoringState` → `MutableStateFlow` configurable
- `CaptureScreenContextUseCase.monitoringState` → delegada del repository

### Tests requeridos
1. **ScreenContextRepositoryImplTest**:
   - `startMonitoring` emite `Active`
   - `stopMonitoring` emite `Idle`
   - Timeout de 15s emite `Error`
   - Texto que cambia resetea el timer

2. **CaptureScreenContextUseCaseTest**:
   - `toggleMonitoring` desde Idle → llama `startMonitoring`
   - `toggleMonitoring` desde Active → llama `stopMonitoring`
   - `monitoringState` delega al repository

3. **OverlayViewModelTest**:
   - `toggleMonitoring` refleja estado en UI
   - Estado Active → `isMonitoring=true`
   - Estado Error → `isMonitoring=false`
