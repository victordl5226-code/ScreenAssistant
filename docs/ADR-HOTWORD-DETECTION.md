# ADR-HOTWORD: Detección de Hotword "Hey JARVIS"

**Estado**: Propuesto
**Fecha**: 2026-09-09
**Decisor**: Arquitecto
**Stakeholders**: Arquitecto, QA (shift-left), Desarrollador, Supervisor

---

## 1. Contexto

### Problema Actual
El asistente requiere que el usuario toque el botón de micrófono en el overlay para iniciar la escucha. Esto rompe el flujo natural de interacción voz-a-voz y force al usuario a usar las manos.

### Objetivo
Implementar detección de hotword "Hey JARVIS" (o nombre personalizado) para que:
1. El asistente escuche continuamente en background (baja potencia)
2. Al detectar el hotword → activa el overlay y escucha comandos
3. El usuario NO necesita tocar nada — solo hablar

### Restricciones
- Offline (sin internet)
- Eficiente en batería (< 1% CPU, < LOW battery)
- Rápido (< 500ms latencia de detección)
- Compatible con la arquitectura existente (Clean Architecture + Hilt + Coroutines)
- No agregar dependencias pesadas

---

## 2. Decisión: Porcupine (Picovoice) como Motor de Hotword

### Opciones Evaluadas

| Opción | Pros | Contras | Veredicto |
|--------|------|---------|-----------|
| **A: Porcupine** | Built-in `JARVIS`, ~1% CPU, Apache 2.0, Maven Central, API simple | Requiere AccessKey (free tier) | **ELEGIDA** |
| B: Snowboy | Open source, sin API key | Deprecated desde 2020, sin soporte | Descartada |
| C: Mycroft Precise | Open source | Sin SDK Android oficial, Integración JNI manual | Descartada |
| D: WebRTC VAD + classifier | Sin dependencias externas | Complejidad alta, necesita entrenamiento ML, sin garantía de accuracy | Descartada |

### Justificación de Porcupine

1. **Built-in keyword JARVIS**: `Porcupine.BuiltInKeyword.JARVIS` — sin necesidad de entrenar modelo personalizado
2. **Eficiencia demostrada**: CPU ≤ 1%, Memory ≤ 128 MB, Battery <= LOW (benchmarks en Pixel 3)
3. **SDK Android oficial**: `ai.picovoice:porcupine-android` en Maven Central, Apache 2.0
4. **Offline**: Procesamiento 100% on-device
5. **Latencia**: ~10ms por frame de audio (512 samples @ 16kHz)
6. **Free tier**: Uso personal gratuito con AccessKey
7. **Arquitectura limpia**: Low-Level API permite integrar sin manejar audio propio
8. **Escalable**: Soporta múltiples wake words concurrentes sin overhead

### Alternativas Descartadas (Detalle)

**Snowboy**: Aunque era popular, fue deprecated en 2020. No recibe actualizaciones de seguridad, modelos desactualizados, y la comunidad se movió a Porcupine. Riesgo de incompatibilidad con Android 14+.

**Mycroft Precise**: No tiene SDK Android oficial. Requeriría compilar el modelo C++ con NDK, crear JNI bindings manualmente, y mantener la compilación. Esfuerzo desproporcionado para el beneficio.

**WebRTC VAD + classifier**: WebRTC VAD solo detecta presencia de voz (no identifica palabras). Necesitaríamos entrenar un clasificador CNN/RNN para detectar "Hey JARVIS". Requiere dataset de audio, entrenamiento GPU, y validación de accuracy. 4-6 semanas de trabajo solo para el ML pipeline.

---

## 3. Arquitectura Propuesta

### 3.1 Diagrama de Capas

```
┌─────────────────────────────────────────────────────┐
│                  FEATURE:OVERLAY                     │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────┐ │
│  │ OverlayVM   │←─│ HotwordVM    │←─│ OverlayUI  │ │
│  │ (existente) │  │ (NUEVO)      │  │ (toggle)   │ │
│  └──────┬──────┘  └──────┬───────┘  └────────────┘ │
│         │                │                           │
│  ┌──────┴──────┐  ┌──────┴───────────────────────┐ │
│  │ OverlaySvc  │←─│ HotwordListeningService (NUEVO)│ │
│  │ (existente) │  │  - LifecycleService            │ │
│  └─────────────┘  │  - Foreground notification     │ │
│                   │  - State machine                │ │
│                   └──────────┬────────────────────┘ │
└──────────────────────────────┼──────────────────────┘
                               │
┌──────────────────────────────┼──────────────────────┐
│                  CORE:DOMAIN │                       │
│  ┌───────────────────────────┴───────────────────┐  │
│  │  HotwordDetector (interfaz)                   │  │
│  │  HotwordDetectionState (sealed class)         │  │
│  └───────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
                               │
┌──────────────────────────────┼──────────────────────┐
│              FEATURE:OVERLAY │ (implementación)      │
│  ┌───────────────────────────┴───────────────────┐  │
│  │  PorcupineHotwordDetector (impl concreta)     │  │
│  │  HotwordPreferences (DataStore)               │  │
│  └───────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

### 3.2 Machine State (Estados del Hotword)

```
                    ┌──────────────┐
                    │   DISABLED   │ ← usuario desactiva
                    └──────┬───────┘
                           │ activate()
                           ▼
                    ┌──────────────┐
            ┌──────│  LISTENING   │←──────────────┐
            │      │ (Porcupine)  │               │
            │      └──────┬───────┘               │
            │             │ hotword detected       │
            │             ▼                        │
            │      ┌──────────────┐               │
            │      │  DETECTED    │               │
            │      │ (activar     │               │
            │      │  overlay)    │               │
            │      └──────┬───────┘               │
            │             │ Vosk started           │
            │             ▼                        │
            │      ┌──────────────┐               │
            └──────│  COMMAND     │  timeout/error │
                   │  LISTENING   │───────────────┘
                   │  (Vosk STT)  │
                   └──────┬───────┘
                          │ result received
                          ▼
                   ┌──────────────┐
                   │  PROCESSING  │ → sendMessage()
                   └──────┬───────┘
                          │ TTS done
                          ▼
                   (vuelve a LISTENING si hotword enabled)
```

### 3.3 Flujo de Datos

```
1. App inicia → AssistantOverlayService.onCreate()
2. HotwordListeningService.start() → Porcupine escuchando
3. Usuario dice "Hey JARVIS"
4. Porcupine callback → HotwordDetectionEvent.KeywordDetected
5. HotwordListeningService:
   a. Para Porcupine (libera micrófono)
   b. Notifica a OverlayViewModel → uiState.showOverlay = true
   c. OverlayViewModel.startListening() → Vosk toma el micrófono
6. Usuario dice comando (ej: "abre WhatsApp")
7. Vosk resultado → OverlayViewModel.sendMessage(text)
8. TTS responde → Piper habla
9. Cuando TTS termina → HotwordListeningService reinicia Porcupine
10. Ciclo se repite
```

---

## 4. Contratos Clave (Interfaces)

### 4.1 Interfaz en `core/domain`

```kotlin
// core/domain - HotwordDetector.kt
package com.screenassistant.core.domain.service

import kotlinx.coroutines.flow.Flow

/**
 * Interfaz abstracta para detección de hotword/wake word.
 * Permite intercambiar implementaciones (Porcupine, WebRTC, etc.)
 * sin afectar a la capa de presentación.
 */
interface HotwordDetector {

    /**
     * Flujo de eventos de detección de hotword.
     * Emite [HotwordDetectionEvent] cuando se detecta el hotword o hay errores.
     */
    fun detectionEvents(): Flow<HotwordDetectionEvent>

    /**
     * Inicia la escucha continua de hotword.
     * Requiere permiso RECORD_AUDIO concedido.
     *
     * @throws SecurityException si no hay permiso de micrófono
     * @throws IllegalStateException si el detector no está inicializado
     */
    fun startListening()

    /**
     * Detiene la escucha de hotword y libera el micrófono.
     * Seguro de llamar múltiples veces (idempotente).
     */
    fun stopListening()

    /**
     * Libia todos los recursos (nativo, audio, memoria).
     * Después de llamar, el detector no es reutilizable.
     */
    fun destroy()

    /**
     * Indica si el detector está actualmente escuchando.
     */
    fun isListening(): Boolean
}

/**
 * Eventos emitidos por el detector de hotword.
 */
sealed class HotwordDetectionEvent {
    /** Hotword detectado exitosamente */
    data class KeywordDetected(
        val keywordIndex: Int,
        val keywordName: String
    ) : HotwordDetectionEvent()

    /** Error en la detección */
    data class Error(
        val message: String,
        val exception: Exception? = null
    ) : HotwordDetectionEvent()

    /** El detector comenzó a escuchar */
    data object ListeningStarted : HotwordDetectionEvent()

    /** El detector dejó de escuchar */
    data object ListeningStopped : HotwordDetectionEvent()
}
```

### 4.2 Estado UI del Hotword

```kotlin
// feature/overlay - HotwordUiState.kt
package com.screenassistant.feature.overlay

/**
 * Estado de la UI relacionado con hotword detection.
 * Se agrega a OverlayUiState existente.
 */
data class HotwordUiState(
    val isHotwordEnabled: Boolean = false,
    val isHotwordListening: Boolean = false,
    val detectedKeyword: String? = null,
    val hotwordError: String? = null
)
```

### 4.3 Preferencias

```kotlin
// feature/overlay - HotwordPreferences.kt
package com.screenassistant.feature.overlay

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.hotwordDataStore by preferencesDataStore(name = "hotword_prefs")

/**
 * Persistencia de configuración de hotword usando DataStore.
 * Sigue el patrón existente en MonitoringPreferences.
 */
object HotwordPreferences {

    private val KEY_HOTWORD_ENABLED = booleanPreferencesKey("hotword_enabled")

    fun isEnabled(context: Context): Flow<Boolean> {
        return hotwordDataStore.data.map { prefs ->
            prefs[KEY_HOTWORD_ENABLED] ?: false
        }
    }

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        hotwordDataStore.edit { prefs ->
            prefs[KEY_HOTWORD_ENABLED] = enabled
        }
    }
}
```

---

## 5. Archivos a Crear

| # | Archivo | Módulo | Responsabilidad |
|---|---------|--------|-----------------|
| 1 | `core/domain/.../service/HotwordDetector.kt` | `core:domain` | Interfaz + sealed class de eventos |
| 2 | `feature/overlay/.../PorcupineHotwordDetector.kt` | `feature:overlay` | Implementación con Porcupine SDK |
| 3 | `feature/overlay/.../HotwordListeningService.kt` | `feature:overlay` | LifecycleService con state machine |
| 4 | `feature/overlay/.../HotwordPreferences.kt` | `feature:overlay` | DataStore para toggle on/off |
| 5 | `feature/overlay/.../HotwordUiState.kt` | `feature:overlay` | Estado UI del hotword |
| 6 | `feature/overlay/.../di/HotwordModule.kt` | `feature:overlay` | Bindings Hilt |

## 6. Archivos a Modificar

| # | Archivo | Cambio |
|---|---------|--------|
| 1 | `feature/overlay/build.gradle.kts` | Agregar dependencia `ai.picovoice:porcupine-android` |
| 2 | `feature/overlay/.../OverlayUiState.kt` | Agregar campo `hotword: HotwordUiState` |
| 3 | `feature/overlay/.../OverlayViewModel.kt` | Agregar métodos `toggleHotword()`, integración con HotwordDetector |
| 4 | `feature/overlay/.../AssistantOverlayService.kt` | Iniciar/detener HotwordListeningService |
| 5 | `feature/overlay/.../AssistantOverlayUI.kt` | Agregar toggle de hotword en la barra de control |
| 6 | `gradle/libs.versions.toml` | Agregar versión de Porcupine |
| 7 | `app/src/main/AndroidManifest.xml` | Permiso `WAKE_LOCK` (opcional, para mantener CPU durante detección) |

---

## 7. Integración con OverlayViewModel

```kotlin
// Cambios en OverlayViewModel (pseudocódigo)

// Nuevos campos inyectados
private val hotwordDetector: HotwordDetector
private val personalityRepository: PersonalityRepository // ya existe

// Nuevo estado
private val _hotwordUiState = MutableStateFlow(HotwordUiState())
val hotwordUiState: StateFlow<HotwordUiState> = _hotwordUiState.asStateFlow()

// Init: escuchar eventos de hotword
init {
    viewModelScope.launch(ioDispatcher) {
        hotwordDetector.detectionEvents().collect { event ->
            when (event) {
                is HotwordDetectionEvent.KeywordDetected -> {
                    _hotwordUiState.update { it.copy(
                        detectedKeyword = event.keywordName,
                        isHotwordListening = false
                    )}
                    // Activar overlay y escuchar comando
                    startListening()
                }
                is HotwordDetectionEvent.ListeningStarted -> {
                    _hotwordUiState.update { it.copy(isHotwordListening = true) }
                }
                is HotwordDetectionEvent.ListeningStopped -> {
                    _hotwordUiState.update { it.copy(isHotwordListening = false) }
                }
                is HotwordDetectionEvent.Error -> {
                    _hotwordUiState.update { it.copy(hotwordError = event.message) }
                }
            }
        }
    }
}

// Toggle hotword
fun toggleHotword() {
    val newState = !_hotwordUiState.value.isHotwordEnabled
    viewModelScope.launch(ioDispatcher) {
        HotwordPreferences.setEnabled(context, newState)
        _hotwordUiState.update { it.copy(isHotwordEnabled = newState) }
        if (newState) hotwordDetector.startListening()
        else hotwordDetector.stopListening()
    }
}

// Cuando Vosk termina, reiniciar hotword
fun onListeningComplete() {
    if (_hotwordUiState.value.isHotwordEnabled) {
        hotwordDetector.startListening()
    }
}
```

---

## 8. Integración con AssistantOverlayService

```kotlin
// Cambios en AssistantOverlayService (pseudocódigo)

@Inject lateinit var hotwordDetector: HotwordDetector

override fun onCreate() {
    // ... código existente ...
    startHotwordListening()
}

private fun startHotwordListening() {
    viewModelScope.launch(ioDispatcher) {
        val enabled = HotwordPreferences.isEnabled(applicationContext).first()
        if (enabled) {
            hotwordDetector.startListening()
        }
    }
}

override fun onDestroy() {
    hotwordDetector.destroy()
    // ... código existente ...
}
```

---

## 9. Permisos

| Permiso | Estado | Acción |
|---------|--------|--------|
| `RECORD_AUDIO` | ✅ Ya existe | Ninguna |
| `SYSTEM_ALERT_WINDOW` | ✅ Ya existe | Ninguna |
| `FOREGROUND_SERVICE` | ✅ Ya existe | Ninguna |
| `WAKE_LOCK` | ⚠️ Nuevo | Agregar a manifest (mantener CPU durante frames de audio) |
| `POST_NOTIFICATIONS` | ✅ Ya existe | Ninguna |

---

## 10. Testing (QA Shift-Left)

### 10.1 Testabilidad de la Arquitectura

La interfaz `HotwordDetector` en `core/domain` permite:

1. **Unit Tests del ViewModel**: Mockear `HotwordDetector` con fakes
   ```kotlin
   class FakeHotwordDetector : HotwordDetector {
       private val events = MutableSharedFlow<HotwordDetectionEvent>()
       override fun detectionEvents() = events
       override fun startListening() { /* no-op */ }
       override fun stopListening() { /* no-op */ }
       override fun destroy() { /* no-op */ }
       override fun isListening() = false

       // Para tests:
       suspend fun emitEvent(event: HotwordDetectionEvent) {
           events.emit(event)
       }
   }
   ```

2. **Unit Tests del HotwordListeningService**: Mockear `HotwordDetector` y verificar transiciones de estado

3. **Unit Tests de HotwordPreferences**: Verificar escritura/lectura en DataStore

4. **Integration Tests**: Verificar que `PorcupineHotwordDetector` se instancia correctamente con Hilt

### 10.2 Tests Propuestos

| Test | Tipo | Qué verifica |
|------|------|-------------|
| `HotwordDetector` interface compliance | Unit | Implementación cumple contrato |
| `OverlayViewModel.toggleHotword()` | Unit | Estado se actualiza, detector inicia/para |
| `OverlayViewModel on KeywordDetected` | Unit | Se activa listening, UI se actualiza |
| `HotwordPreferences` read/write | Unit | DataStore persiste toggle |
| `HotwordListeningService` state machine | Unit | Transiciones de estado correctas |
| `PorcupineHotwordDetector` init | Integration | Se instancia con AccessKey válido |
| `PorcupineHotwordDetector` keyword detection | Integration | Detecta keyword built-in JARVIS |

---

## 11. Estimación de Esfuerzo

| Tarea | Horas | Dependencias |
|-------|-------|-------------|
| Interface `HotwordDetector` + sealed class | 1h | Ninguna |
| `PorcupineHotwordDetector` | 4h | Interface + SDK |
| `HotwordListeningService` (state machine) | 4h | Detector + Service |
| `HotwordPreferences` (DataStore) | 1h | Patrón existente |
| Integración `OverlayViewModel` | 3h | Detector + Preferences |
| Integración `AssistantOverlayService` | 2h | Service + ViewModel |
| UI toggle en `AssistantOverlayUI` | 2h | ViewModel |
| Tests unitarios | 4h | Todo lo anterior |
| Tests de integración | 2h | Todo lo anterior |
| Documentación + ADR | 1h | Todo lo anterior |
| **Total** | **~24h (3 días laborales)** | |

### Orden de Implementación

1. **Interface + sealed class** (core:domain) — sin dependencias
2. **HotwordPreferences** (feature:overlay) — patrón existente
3. **PorcupineHotwordDetector** (feature:overlay) — depende de interface
4. **HotwordUiState** (feature:overlay) — data class simple
5. **HotwordListeningService** (feature:overlay) — depende de detector
6. **Integración OverlayViewModel** — depende de todo
7. **Integración AssistantOverlayService** — depende de ViewModel
8. **UI toggle** (AssistantOverlayUI) — depende de ViewModel
9. **Tests** — depende de todo
10. **Gradle + Manifest** — integración final

---

## 12. Riesgos y Mitigaciones

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|-------------|---------|------------|
| Conflicto de micrófono Porcupine ↔ Vosk | Alta | Alto | State machine secuencial: nunca ambos activos |
| AccessKey caduca o rate-limited | Baja | Medio | Free tier generoso (1000 activaciones/día) |
| Batería drain en dispositivos antiguos | Media | Medio | Toggle on/off + detectar estado de batería |
| Porcupine SDK cambia API | Baja | Baja | Interface abstracta permite swap de implementación |
| Detección falsa positiva | Media | Baja | Threshold configurable, built-in model optimizado |

---

## 13. Próximos Pasos (Post-Implementación)

1. **Hotword personalizado**: Entrenar modelo "Hey JARVIS" via Picovoice Console para mejor accuracy
2. **Múltiples wake words**: Soportar "Hey JARVIS" + nombre personalizado
3. **Detección de dirección**: Usar micrófono direccional si hardware lo permite
4. **Aprendizaje adaptativo**: Ajustar sensibilidad según ambiente (ruido)
5. **Wear OS**: Hotword en smartwatch como segundo punto de escucha

---

*Documento generado por el Arquitecto — 2026-09-09*
