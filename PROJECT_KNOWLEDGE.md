# ScreenAssistant - Base de Conocimiento del Proyecto

## Resumen del Proyecto
Aplicación Android de asistente flotante con IA (Gemini) que muestra un overlay con personaje animado, comandos de voz, y **monitoreo continuo de pantalla** para capturar texto en tiempo real.

---

## Arquitectura (Clean Architecture + Modularización)

### Módulos
| Módulo | Responsabilidad | Capa |
|---|---|---|
| `app` | Punto de entrada, MainActivity, navegación | Presentation |
| `feature/overlay` | AssistantOverlayService, OverlayViewModel, UI Compose | Presentation |
| `core/domain` | Modelos, interfaces repositorio, casos de uso | Domain |
| `core/data` | Implementaciones repositorio, datasources | Data |
| `core/ui` | Tema, componentes Compose compartidos | Presentation |
| `service/system` | ScreenContextService (AccessibilityService) | System |

### Patrones Clave
- **MVVM + StateFlow** en ViewModels
- **Hilt DI** multi-módulo (KSP)
- **Sealed Classes** para estados UI (`ScreenMonitoringState`, `OverlayUiState`)
- **Coroutines + Flow** para reactividad
- **Auto-registro** del AccessibilityService como `ScreenCaptureProvider` (patrón service-locator interno en core:data)

---

## Decisiones Técnicas Importantes (ADRs implícitos)

### ADR-001: Monitoreo Continuo (Lote 9)
**Problema**: Capturar texto de pantalla periódicamente sin bloquear UI.
**Solución**: 
- `ScreenContextRepositoryImpl` con `CoroutineScope(IO + SupervisorJob)`
- Watchdog timeout 15s → estado `Error` si no hay updates
- Intervalo mínimo 1s (coerceAtLeast)
- Estado expuesto via `StateFlow<ScreenMonitoringState>`

### ADR-002: Service Locator Interno (D7/B3)
**Problema**: `core:data` no debe depender de `service:system`.
**Solución**: `ScreenContextService` se auto-registra en `ScreenContextRepositoryImpl.setScreenCaptureProvider(this)` en `onServiceConnected`/`onRebind`. El repo usa `@Volatile` para thread-safety.

### ADR-003: Auto-start Temporal para Testing
**Decisión**: `OverlayViewModel.init` lanza `startMonitoring(5000)` tras 2s delay.
**Razón**: Validación manual sin depender de tap en overlay (Compose + FLAG_NOT_FOCUSABLE dificulta taps por adb).

### ADR-004: Toggle Monitoring en ViewModel
```kotlin
fun toggleMonitoring() {
    captureScreenContextUseCase.toggleMonitoring(_uiState.value.monitoringIntervalMs)
}
fun setMonitoringInterval(intervalMs: Long) {
    // Reinicia monitoreo con nuevo intervalo
    if (isMonitoring) { stop(); start(intervalMs) }
}
```

### ADR-005: Comandos de Voz para Monitoreo (Lote 10)
**Problema**: El usuario no debe tocar el ícono 👁 diminuto en el overlay para activar/desactivar monitoreo.
**Solución**: Comandos de voz naturales que devuelven marcadores UI (sin pasar por SystemActionHandler):
- `START_MONITORING`: "activar monitoreo", "iniciar monitoreo", "encender monitoreo" (+ "por favor")
- `STOP_MONITORING`: "detener monitoreo", "parar monitoreo", "apagar monitoreo" (+ "por favor")
**Implementación**:
1. `CommandMarkers` +2 constantes
2. `SystemCommandParser` parsing 12 variantes (precedencia tras REPEAT, antes de Volumen)
3. `OverlayViewModel.sendMessage()` intercepta marcadores → `toggleMonitoring()`
4. `HELP_SPEECH` + `HelpContent` actualizados
**Tests**: 16 nuevos tests unitarios cubriendo todas las variantes

### ADR-006: Auto-Recovery Monitoreo (Lote 10)
**Problema**: Si el servicio de accesibilidad se desconecta (crash, usuario lo desactiva), el monitoreo va a Error y no se recupera solo al reconectar.
**Solución**: Auto-recovery transparente al reconectar proveedor:
- `ScreenContextRepositoryImpl` trackea `wasMonitoringBeforeDisconnect` + `lastActiveIntervalMs`
- En `setScreenCaptureProvider(null -> non-null)`: si `wasMonitoringBeforeDisconnect` → `startMonitoring(lastActiveIntervalMs)`
- `stopMonitoring()` resetea flag → no auto-reinicia si usuario paró intencionalmente
**Tests**: 2 tests unitarios verificando flag reset y persistencia intervalo

### ADR-007: Persistencia de Intervalo con DataStore (Lote 11)
**Problema**: El intervalo de monitoreo se perdía al reiniciar la app (default 5s siempre).
**Solución**: Preferences DataStore (`datastore-preferences:1.0.0`) en `core:data`.
**Implementación**:
- `MonitoringPreferences` (object en `core:data`) — wrapper sobre DataStore
- Delegate `preferencesDataStore` a nivel superior del archivo (recomendación oficial Android)
- `OverlayViewModel` lee/escribe vía `MonitoringPreferences.getInterval(context)` / `setInterval(context, ms)`
- `AssistantOverlayService` pasa `applicationContext` al crear `OverlayViewModel`
**Validación en emulador**: Escritura de 10000ms → force-stop → lectura 10000ms ✅
**Tests**: 2 tests unitarios existentes (`setMonitoringInterval actualiza`, `persiste intervalo`)

---

## Flujo de Trabajo del Equipo (Proceso)

### Escalera de Proceso
| Magnitud | Nivel |
|---|---|
| Trivial (rename, color) | Implementa directo |
| Pequeña (1-3 archivos) | Dev + QA |
| Mediana (feature nueva) | **Arquitecto → QA shift-left → Dev → Gate → QA+UI/UX → Supervisor** |
| Grande (refactor) | Flujo completo + ADR + Supervisor obligatorio |

### Definition of Done
- [ ] Arquitectura aprobada
- [ ] Compila (`compileDebugKotlin` + `compileDebugUnitTestKotlin`)
- [ ] Tests verdes (0 fallos)
- [ ] Cobertura ≥ 80% código nuevo
- [ ] Casos borde y errores cubiertos
- [ ] QA aprobado
- [ ] UI/UX aprobado (si toca UI)
- [ ] Supervisor sin veto (mediana/grande)

### Reglas de Oro
1. **Shift-left QA**: QA revisa arquitectura ANTES de implementar
2. **Feedback temprano Dev**: Dev reporta problemas estructurales → vuelve a Arquitecto
3. **Paralelización**: QA + UI/UX en paralelo tras implementación
4. **Compilation Gate**: Dev DEBE compilar antes de entregar
5. **Checkpoint por porción**: `PROGRESO.md` actualizado por porción completada
6. **Post-entrega**: `PROGRESO.md` → `HISTORIAL.md` y borrar `PROGRESO.md`

---

## Lecciones Aprendidas (Hard-won)

### Testing
- **Unit tests**: 476 pasan, ~88% cobertura código nuevo
- **androidTest**: Falla preexistente (nombres funciones en español con espacios → D8 dex error). **No bloquea**.
- MockK + Turbine para Flow testing
- Tests de repositorio cubren: timeout, text updates, start/stop/idle/error states

### Emulador & Permisos
- **Emulador**: `emulator-5554` (AgroCode_API36, Android 16)
- **Permisos críticos**:
  - `SYSTEM_ALERT_WINDOW` → `adb shell appops set ... allow`
  - `RECORD_AUDIO` → `adb shell pm grant ...`
  - `POST_NOTIFICATIONS` → Diálogo runtime (Android 13+)
  - Accesibilidad → `settings put secure enabled_accessibility_services`
- **Force-stop mata servicios**: Requiere re-lanzar app y re-activar accesibilidad

### Overlay Compose + WindowManager
- `TYPE_APPLICATION_OVERLAY` + `FLAG_NOT_FOCUSABLE` + `FLAG_NOT_TOUCH_MODAL`
- `detectDragGestures` en Box root consume drag, pero taps deberían pasar
- **Problema real**: `adb input tap` no siempre llega a botones Compose en overlay window
- **Workaround**: Auto-start en ViewModel para testing sin taps

### Accesibilidad (ScreenContextService)
- `onAccessibilityEvent` con throttle 1s evita spam
- `extractText` recursivo con límite 4000 chars (anti-OOM)
- `updateScreenText` alimenta `MutableStateFlow` del repo
- Requiere `android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"`

### Hilt Multi-módulo
- `@HiltViewModel` en feature modules requiere factory manual en Service
- `@IoDispatcher` qualifier para coroutine dispatchers
- `androidx.hilt.navigation.compose.hiltViewModel()` en Activities

### DataStore Preferences
- **API v1.0.0+ cambió paquetes**: `androidx.datastore.preferences.core.DataStore` → `androidx.datastore.core.DataStore`
- **Delegate `preferencesDataStore` DEBE estar a nivel superior del archivo**, no dentro de un `object` o `class`. Si está dentro de un object, crea una nueva instancia por acceso y nunca persiste.
- **`preferencesKey<T>()` → `intPreferencesKey()`, `longPreferencesKey()`, etc.** (tipos específicos en v1.0.0+)
- **`createDataStore()` eliminado** → usar `preferencesDataStore(name = "...")` delegate
- **`dataStore.updateData { it.toMutablePreferences() }` → `dataStore.edit { prefs -> prefs[key] = value }`**
- **El archivo se crea solo al ESCRIBIR**, no al leer defaults (lee sin crear archivo)

---

## Comandos Útiles (Cheatsheet)

```bash
# Build & Test
./gradlew :app:installDebug
./gradlew :core:domain:test :core:data:testDebugUnitTest :feature:overlay:testDebugUnitTest

# Emulador
adb shell am start -n com.screenassistant/.MainActivity
adb shell input keyevent 3  # Home

# Permisos
adb shell appops set com.screenassistant SYSTEM_ALERT_WINDOW allow
adb shell pm grant com.screenassistant android.permission.RECORD_AUDIO
adb shell settings put secure enabled_accessibility_services com.screenassistant/.service.system.ScreenContextService
adb shell settings put secure accessibility_enabled 1

# Debug
adb logcat -d --pid=$(adb shell pidof com.screenassistant) | grep -E "ScreenMonitoring|OverlayVM"
adb shell dumpsys activity services | grep -A3 "AssistantOverlay\|ScreenContext"
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
```

---

## Problemas Conocidos / Deuda Técnica

1. **androidTest roto**: Nombres de test en español con espacios causan error D8. Fix: renombrar a camelCase.
2. **Tap en overlay**: `adb input tap` poco fiable en ComposeView TYPE_APPLICATION_OVERLAY.
3. ~~**Auto-start temporal**: Debe quitarse y dejar solo toggle manual + comando voz.~~ ✅ **COMPLETADO (Lote 10)**
4. ~~**Persistencia intervalo**: No se guarda en DataStore/SharedPreferences.~~ ✅ **COMPLETADO (Lote 11 - DataStore)**
5. ~~**Reconexión servicio**: Si accesibilidad se desactiva, monitoreo va a Error y no auto-recupera.~~ ✅ **COMPLETADO (Lote 10 - Auto-Recovery)**

---

## Próximas Mejoras Priorizadas

| Prioridad | Tarea | Esfuerzo |
|---|---|---|
| ~~Alta~~ | ~~Quitar auto-start, solo toggle manual~~ | ~~Pequeña~~ | ✅ **COMPLETADO**
| ~~Alta~~ | ~~Comando voz "activar monitoreo" / "detener monitoreo"~~ | ~~Pequeña~~ | ✅ **COMPLETADO (Lote 10)**
| ~~Media~~ | ~~Auto-recuperar monitoreo si accesibilidad vuelve~~ | ~~Mediana~~ | ✅ **COMPLETADO (Lote 10)**
| ~~Media~~ | ~~Persistir intervalo en DataStore~~ | ~~Pequeña~~ | ✅ **COMPLETADO (Lote 11)** |
| Baja | Animaciones burbuja monitoreo | Pequeña |
| Baja | Fix androidTest (renombrar tests) | Mediana |

---

## Convenciones de Código

### Kotlin
- Sealed classes para estados (`ScreenMonitoringState`, `OverlayUiState`)
- `copy()` para inmutabilidad en data classes
- `StateFlow` + `MutableStateFlow` para UI state
- `suspend` functions para I/O, `Flow` para streams
- `@Volatile` para cross-thread simple flags

### Compose
- `remember` para estado local
- `LaunchedEffect` para side-effects
- `Modifier.wrapContentSize()` en overlay para no bloquear pantalla
- `Material3` + `MaterialTheme.colorScheme.surfaceColorAtElevation()`

### Git/Commits
- Commits atómicos por feature/lote
- Mensajes en español, convencionales
- `HISTORIAL.md` como memoria permanente del proyecto

---

## Contexto de Mentoría

**Usuario**: Aprendiendo Android/Kotlin/Compose mediante este proyecto real.
**Estilo**: Explicar el "por qué" de cada decisión, no solo el "qué".
**Objetivo**: Que el usuario internalice patrones Clean Architecture, testing, DI, y flujo profesional.

---

*Última actualización: 2026-08-24 - Persistencia de intervalo con DataStore completado (Lote 11)*