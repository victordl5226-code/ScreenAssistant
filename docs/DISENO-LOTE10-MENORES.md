# Diseño Lote 10 — Deuda menor restante M1–M26 (verificación por ítem + cierre)

Base: commit `0c24bac` (Lote 9 cerrado, **777 tests verdes / 0 fallos**). Referencia: docs/BACKLOG.md líneas 43–70 (menores) + 21–33 (bugs/deuda ya pagados).
Criterio de este lote: **todo lo que el BACKLOG describe se verifica contra el código real antes de decidir** — varios menores ya se pagaron en Lotes 8/9 (M25 parcial, M26 parcial, M22 parcial, M15 parcial).

Comando de gate (patrón de los lotes anteriores):
```
gradlew testDebugUnitTest :core:domain:test :app:assembleDebug --rerun-tasks
```

---

## 0. Resumen ejecutivo

- **Gate base: 777** (`:app 20 · :core:data 46 · :core:domain 445 · :feature:overlay 38 · :service:system 228`).
- **Ítems incluidos: M1, M2, M10, M15, M18, M20, M22, M23, M24, M25 (solo KDoc), EXTRA-1 (@Volatile), EXTRA-2 (onRebind)**.
- **Ítems CERRADOS sin trabajo (verificado): M26** (voz fijada + stub tipado ya pagados; fakes de chat muertos con D2) y **parte de M25** (catch colapsado + imports de ConnectivityWorker ya pagados en Lote 8).
- **Ítems DIFERIDOS con justificación: M6, M13** (rompen la regla sin-Robolectric/sin-deps o son infra de build sin pipeline que las ejecute).
- **Gate objetivo: ≈ 787 tests verdes** (777 + 10; desglose en §7).

---

## 1. Estado real verificado por ítem (acta de verificación)

| Ítem | Estado verificado en el código (commit 0c24bac) | Veredicto |
|------|------------------------------------------------|-----------|
| **M1** | `SystemCommandParser.kt` rama 3 (L224-230): `parseDurationMinutes` sin límite → `SetTimer(0)` y `SetTimer(5000)` se emiten. El wire SÍ valida: `AccionRegistry.kt:125` `Limites(rangoMinutos = 1..1440)` consultado por la Fase B' del codec (`SystemCommandJsonCodec.validarRango`, L267-271). `SystemCommand.SetTimer(val minutes: Int)` (L19) sin invariante. `TimerAction.setTimer` (L13-27) acepta cualquier Int (EXTRA_LENGTH = minutes*60_000). Ningún test de 0/5000 en `SystemCommandParserTest`. | **INCLUIR** |
| **M2** | `callOrNumber` (L347-354): `arg.matches(phoneRegex)` — `"+34 600 123 456 por favor"` NO matchea (matches = cadena completa) → `Call("600 123 456 por favor")` (contacto basura). Tests existentes de llamadas sin "por favor" (L750-857); solo hay "por favor" en ramas ayuda/silencio. | **INCLUIR** |
| **M6** | `AppDatabase.MIGRATION_2_3` (CREATE TABLE IF NOT EXISTS alarms) registrada en AppModule (L68) + `fallbackToDestructiveMigration`. Cero tests de migración. `MessageDao` usa @Insert/@Query (no @Upsert — el texto del BACKLOG no refleja el DAO real). `MigrationTestHelper` (room-testing) exige **instrumentación** (InstrumentationRegistry) o Robolectric — no hay camino JVM-puro. Regla del equipo: sin Robolectric ni deps nuevas. | **DIFERIR** (§3.1) |
| **M10** | `SystemActionHandler.kt:72-76` B5 OK (CancellationException re-lanzada). **PERO** el catch externo L77-78 `ActionResult.Error(e.message ?: ...)` sigue vivo y el camino es REAL: `MemoryAction.saveMemory` (L11-14) NO tiene catch propio → un fallo de Room (SQLiteException) sube al catch externo y el mensaje crudo llega al usuario. 3 tests fijan `Error("boom")` (SystemActionHandlerTest L93-99, L199-205, L221-227). | **INCLUIR** |
| **M13** | Sin JaCoCo en ningún build.gradle.kts; `app/src/androidTest/` vacío (verificado: 0 archivos); `testInstrumentationRunner` configurado y deps `ui-test-junit4`/`ui-test-manifest` presentes pero sin ningún test que las use. | **DIFERIR** (§3.2) |
| **M15** | `conversationRepository` ya eliminado (D2). Queda: (a) `screenContextRepository` en constructor de `OverlayViewModel` (L23) **sin uso en el cuerpo** (el use case ya lo lleva inyectado: `CaptureScreenContextUseCase.kt:6-8`); el servicio lo inyecta SOLO para pasarlo (AssistantOverlayService L43, L62-63); (b) `isNetworkAvailable = true` muerta (OverlayViewModel L63); (c) `OverlayUiState.currentOutfitRes` (L10) nunca escrito NI leído (el UI usa `characterState.currentAssetRes` — AssistantOverlayUI L176; `CharacterState.currentOutfitRes` es un var real, distinto). | **INCLUIR** |
| **M18** | `EncryptedPrefsStore` (base, D4) ya documenta el fail-soft en su KDoc (L18-20). Falta declarar el contrato "aceptado por diseño v1.2a" en los puntos de entrada del consumidor: `PuenteConfigStore.guardar()` (L85-96) y `ApiKeyProvider.storeApiKey()/sembrarDesdeBuildConfig()` — hoy el KDoc documenta el comportamiento crudo pero no el fallo silencioso. | **INCLUIR** (solo KDoc) |
| **M20** | Confirmado: `AssistantOverlayService` construye los VMs manualmente por lazy (L57-68, limitación KSP multi-módulo documentada); `_viewModelStore` (L51) NUNCA recibe registros → `_viewModelStore.clear()` (L164) es no-op; `onCleared` del VM (destroy TTS/STT) nunca se dispara. La limpieza real la hace el `DisposableEffect` de la UI (AssistantOverlayUI L86-92: ttsManager/sttManager.destroy()). | **INCLUIR** (corregir la garantía documental) |
| **M22** | `ConnectivityWorker` ELIMINADO + `cancelConnectivityWorkerZombie` (AssistantOverlayService L93-97) ✓ (Lote 8). Quedan: `TIMEOUT_MS = 10_000L` duplicada — `TaskerBridgeContract.kt:25` y `AlarmReceiver.kt:45` (mismo módulo, mismo patrón goAsync); `NetworkUtils.kt` (core:data/util) con **cero call sites** (grep: solo auto-referencia; SystemActionHandler:82-90 reimplementa hasNetwork inline). | **INCLUIR** |
| **M23** | Premisa falsa confirmada en dos sitios: `NoteAction.kt:115-119` ("core:domain no debe filtrarse a service:system") y `BACKLOG.md:183` (ADR-003). La verdad: service:system depende de core:domain por diseño (SystemCommandParser, SystemCommand, SystemAction, ActionResult…). La duplicación SÍ está justificada pero por CONTRATO DISTINTO (normalize del parser = longitud invariante con ':' conservado para TimePhraseParser; normalizeLocal = colapso de espacios + trim para MATCH por substring). **Tercer consumidor: NO alcanzado** (solo 2: `SystemCommandParser.normalize` + `NoteAction.normalizeLocal`; GeminiFunctionCatalog/TaskerMessageHandler no normalizan). | **INCLUIR** (KDoc + ADR) |
| **M24** | Confirmado: `core/data/repository/MemoryRepository.kt` y `core/data/remote/GeminiRepository.kt` con el MISMO nombre que sus interfaces de core:domain → FQCN feos en `AppModule.kt:279,288,297`. 3 @Provides redundantes (L275-300) convertibles a @Binds (las 3 impls tienen @Inject constructor). `Room 2.6.1` duplicada (`app/build.gradle.kts:99` vs `core/data/build.gradle.kts:32`). NO existe `gradle/libs.versions.toml`. | **INCLUIR** |
| **M25** | Parcial verificado: catch IOException+Exception colapsado ✓ (NoteAction L72-74, comentario M25 del Lote 8); imports Assisted/AssistedInject muertos ✓ (worker eliminado). Queda: `AlarmAction.kt:108-109` `hour ?: 0` con KDoc (L105-107) Y test que fija la semántica (`AlarmActionTest:523` "cancelAlarm con hora y sin minutos documenta que cancela la hora en punto"). Canales reales: parser → null/null o ambos (TimePhraseParser); codec → rechaza medio-null (`validarCancelarAlarma`, codec L273-281); Gemini → NO tiene función cancelar_alarma (catálogo sin ella; GeminiRepository solo maneja set_alarm con validación B2). | **INCLUIR solo KDoc** (§2.10: mantener el contrato) |
| **M26** | Parcial verificado: fakes de ChatViewModelTest muertos con D2 (feature:chat eliminada) ✓; `coEvery(any())` débil ya tipado ✓ (SystemCommandParserTest L84-85, comentario M26); los 3 casos de voz YA FIJADOS con tests (L99-125: "a las 7 y media"→null, "temporizador de 5"→null, "las 7"→null). | **CERRADO** (0 trabajo, veredicto en §3.3) |
| **EXTRA-1** | `ScreenContextService.kt:50` `private var capturaEnCurso = false` **sin @Volatile**, con acceso cross-thread REAL: escribe el hilo IO del VM (vía suspendCancellableCoroutine) y el executor de screenshots (onSuccess/onFailure). `ScreenContextRepositoryImpl.kt:21` lo cita como patrón: "`@Volatile` suficiente (mismo patrón que capturaEnCurso del servicio)" — el campo no lo cumple. | **INCLUIR** (1 línea + KDoc) |
| **EXTRA-2** | `ScreenContextService.onUnbind` (L60-66) desregistra y devuelve `super.onUnbind` (true = reconectable). Si la reconexión del framework no re-lanza `onServiceConnected` (no garantizado por contrato entre versiones), el proveedor quedaría desregistrado → captura null (fail-soft, sin crash, pero captura muerta). Guard de 3 líneas: `onRebind` re-registra. | **INCLUIR** (defensivo, 3 líneas) |

---

## 2. Ítems incluidos — diseño concreto

### 2.1 M24 — Naming `*Impl` + convención DI (@Binds) + version catalog ⚠️ PRIMERO

**Decisión**: pagar completo. Toca imports de todo el repo → se ejecuta primero.

**(a) Renombrado de impls** (un nombre = un concepto; precedente ScreenContextRepositoryImpl):

| Archivo | Cambio |
|---------|--------|
| `core/data/src/main/kotlin/com/screenassistant/core/data/repository/MemoryRepository.kt` | → `MemoryRepositoryImpl.kt`; clase `MemoryRepositoryImpl : com...core.domain.repository.MemoryRepository` |
| `core/data/src/main/kotlin/com/screenassistant/core/data/remote/GeminiRepository.kt` | → `GeminiRepositoryImpl.kt`; clase `GeminiRepositoryImpl : com...core.domain.repository.GeminiRepository` |
| `app/src/main/java/com/screenassistant/di/AppModule.kt` | L279/288/297: los FQCN feos desaparecen (los 3 providers se ELIMINAN — ver (b)) |
| `core/data/src/test/.../GeminiRepositoryTest.kt` | L62: `GeminiRepository(...)` → `GeminiRepositoryImpl(...)` (1 línea) |
| `service/system/.../ScreenContextService.kt` | **NO cambia** (ya inyecta `ScreenContextRepositoryImpl`, nombre *Impl correcto) |
| `core/data/.../ScreenContextRepositoryImpl.kt` | **NO cambia** (ya correcto) |

Impacto: `OverlayViewModel`, `AssistantOverlayService`, `MemoryAction`, `LanguageAction`, `GeminiRepositoryImpl` (interno) usan las INTERFACES → sin cambios. No existe MemoryRepositoryTest (verificado) → sin más test tocado.

**(b) Convención DI: 4 @Binds en DataModule** (las impls viven en core:data → el binding les pertenece, no a :app):

`core/data/.../di/DataModule.kt` pasa de 1 a 4 @Binds:
```kotlin
@Binds @Singleton abstract fun bindGenerativeModelFactory(impl: DefaultGenerativeModelFactory): GenerativeModelFactory  // existente
@Binds @Singleton abstract fun bindMemoryRepository(impl: MemoryRepositoryImpl): MemoryRepository
@Binds @Singleton abstract fun bindGeminiRepository(impl: GeminiRepositoryImpl): GeminiRepository
@Binds @Singleton abstract fun bindScreenContextRepository(impl: ScreenContextRepositoryImpl): ScreenContextRepository
```
`AppModule.kt`: eliminar `provideGeminiRepository` (L275-282), `provideMemoryRepository` (L284-291), `provideScreenContextRepository` (L293-300). El resto de providers (Room/DAOs/acciones) se quedan — no son bindings de interfaz (los DAOs son @Provides legítimos: Room no se bindea).

Riesgo: bajo-medio. La resolución de Hilt no cambia (mismos tipos, misma instancia @Singleton); `provideCaptureScreenContextUseCase` (AppModule L178-182) consume la interfaz → resuelve igual. Verificación: build + 3 tests de inyección implícita ya existentes (ScreenContextRepositoryImplTest 4, GeminiRepositoryTest).

**(c) Version catalog (Room + deps de test repetidas)**: crear `gradle/libs.versions.toml` — el catálogo por defecto se activa solo al existir (Gradle 7.4+). Ámbito **mínimo y deliberado**: las versiones duplicadas reales (Room 2.6.1 en 2 módulos; junit 4.13.2, mockk 1.13.12, coroutines-test 1.9.0 en 4-5 módulos). Migrar SOLO esas 4 en los 5 módulos (`app`, `core:data`, `core:domain`, `feature:overlay`, `service:system`). El resto de versiones (Compose BOM, Hilt, serialization…) queda hardcodeado: migración gradual documentada (nota en el toml) — tocar todo el build en un lote de menores eleva el riesgo sin valor funcional.

```toml
[versions]
room = "2.6.1"
junit = "4.13.2"
mockk = "1.13.12"
coroutines-test = "1.9.0"

[libraries]
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines-test" }
```
Archivos: `app/build.gradle.kts` (L99-101, L104-106), `core/data/build.gradle.kts` (L32-35, L55-56), `core/domain/build.gradle.kts` (L23-25), `feature/overlay/build.gradle.kts` (L59-60), `service/system/build.gradle.kts` (L51-52).

**Tests**: 0 nuevos; ediciones: AppModule, DataModule, GeminiRepositoryTest (1 línea). **Riesgo**: build (verificar con `gradlew help` + gate completo).

### 2.2 M1 + M2 — Parser (se ejecutan juntos: mismo archivo de producción y mismo test)

#### M1 — Invariante del temporizador (fuente única, no hardcodeo)

**Decisión**: el límite 1..1440 del wire se convierte en **invariante del modelo de voz** declarado en `SystemCommand` (precedente EXACTO: `MAX_NOTE_CHARS` — AccionRegistry ya consulta `SystemCommand.MAX_NOTE_CHARS` en L56). Los 3 productores quedan alineados: wire (codec → AccionRegistry → constante), voz (parser), ejecución (guard TimerAction).

1. `core/domain/model/SystemCommand.kt` (L19 + companion):
   - `data class SetTimer(val minutes: Int)` con KDoc del invariante: `minutes ∈ [TIMER_MIN_MINUTOS, TIMER_MAX_MINUTOS]`.
   - `companion object`: `const val TIMER_MIN_MINUTOS: Int = 1` y `const val TIMER_MAX_MINUTOS: Int = 1440` con KDoc ("24 horas; fuente única compartida con el wire — AccionRegistry.poner_temporizador").
2. `core/domain/bridge/model/AccionRegistry.kt:125`: `Limites(rangoMinutos = SystemCommand.TIMER_MIN_MINUTOS..SystemCommand.TIMER_MAX_MINUTOS)`. **CorrespondenciaWireTest:89 y SystemCommandJsonCodecTest:312 pasan intactos** (mismos valores).
3. `core/domain/usecase/SystemCommandParser.kt` rama 3 (L224-230): tras `parseDurationMinutes`, validar antes de ejecutar:
   ```kotlin
   val minutes = parseDurationMinutes(trimmed) ?: return null
   if (minutes !in SystemCommand.TIMER_MIN_MINUTOS..SystemCommand.TIMER_MAX_MINUTOS) {
       return "Error: La duración debe estar entre 1 minuto y 24 horas."  // sin ejecutar
   }
   execute(SystemCommand.SetTimer(minutes))
   ```
   Patrón coherente con B2 (set_alarm inválido → error SIN ejecutar) y con el rechazo a Gemini del desnudo >59. El parser es core:domain puro → puede consultar la constante del modelo (no el wire).
4. `service/system/action/TimerAction.kt`: guard defensivo ANTES del try (red final, precedente B2 en AlarmAction.setAlarm):
   ```kotlin
   fun setTimer(minutes: Int): String {
       if (minutes !in SystemCommand.TIMER_MIN_MINUTOS..SystemCommand.TIMER_MAX_MINUTOS) {
           return "Error: La duración debe estar entre 1 minuto y 24 horas."
       }
       ...try...
   }
   ```

Tests nuevos (SystemCommandParserTest +4, TimerActionTest +2):
- `temporizador de 0 minutos` → mensaje de error, `coVerify(exactly = 0) { execute(SetTimer(0)) }`
- `temporizador de 5000 minutos` → mensaje de error, sin SetTimer(5000)
- `temporizador de 24 horas` → `SetTimer(1440)` (boundary alto válido)
- `temporizador de 1441 minutos` → mensaje de error, sin SetTimer(1441)
- TimerActionTest: `setTimer(0)` → error sin tocar context; `setTimer(1441)` → error sin tocar context (verify startActivity exactly 0)

#### M2 — Cortesía "por favor" en llamadas

**Decisión**: el cortesía es artefacto del dictado, no parte del argumento. Se limpia el sufijo final de cortesía en `callOrNumber` (SOLO la rama de llamadas — el cortesía en notas/búsquedas es otro menor fuera de alcance, no reportado):

`SystemCommandParser.kt` `callOrNumber` (L347-354):
```kotlin
private val cortesiaSufijoRegex = Regex("""(?i)\s*(?:por\s+favor|porfa)\s*$""")

private fun callOrNumber(arg: String): String {
    val limpio = cortesiaSufijoRegex.replace(arg.trim(), "").trim()
    if (limpio.isEmpty()) return "Error: ¿A quién quieres que llame?"  // "llama a por favor" → sin target
    return if (limpio.matches(phoneRegex)) {
        execute(SystemCommand.CallNumber(limpio.replace(Regex("""[.\s-]"""), "")))
    } else {
        execute(SystemCommand.Call(limpio))
    }
}
```
Notas: el strip se aplica sobre el ORIGINAL (case preservado — regex (?i)); "llama a la oficina" no matchea el sufijo → intacto; el guard vacío cierra el caso latente "llama a por favor" → Call("") (hoy ya roto: Call("por favor")).

Tests nuevos (SystemCommandParserTest +4):
- `llama a 600 123 456 por favor` → `CallNumber("600123456")` y `coVerify(exactly = 0) { Call("600 123 456 por favor") }`
- `llama a Ana por favor` → `Call("Ana")`
- `llama al jefe por favor` → `Call("jefe")`
- `llama a por favor` → mensaje de error, sin ejecutar nada

**UI/UX**: las frases con cortesía que hoy llaman al contacto basura "600 123 456 por favor" ahora funcionan. Smoke en dispositivo por voz.

### 2.3 M10 — Sanear el mensaje del catch externo del handler

**Decisión**: el catch externo de `SystemActionHandler` (L77-78) NO debe pasar `e.message` al usuario (puede contener clases internas, rutas, SQL — espíritu ADR-009/O7; B5 solo cubrió la cancelación). El detalle va a logcat, el usuario recibe mensaje limpio (mismo contrato que las 11 acciones):

```kotlin
} catch (e: Exception) {
    android.util.Log.w("SystemActionHandler", "Acción fallida: ${command.javaClass.simpleName}", e)
    ActionResult.Error("No pudo completarse la acción.")
}
```
El parser antepone "Error: " → "Error: No pudo completarse la acción." (handleResponse limpia el prefijo → texto plano).

Tests: **3 editados** en SystemActionHandlerTest (L93-99, L199-205, L221-227): `ActionResult.Error("boom")` → `ActionResult.Error("No pudo completarse la acción.")`. Cero tests eliminados.

### 2.4 M15 — Overlay: dependencia muerta, variable muerta y campo muerto

**Decisión**: eliminar los 3 residuos (el 4º, conversationRepository, ya murió en D2):

1. `feature/overlay/.../OverlayViewModel.kt`: quitar `screenContextRepository` del constructor (L23) y su import (L8). El use case ya lo lleva (verificado: `CaptureScreenContextUseCase` inyecta el repo y el VM llama SOLO al use case).
2. `feature/overlay/.../AssistantOverlayService.kt`: quitar `@Inject lateinit var screenContextRepository` (L43) y el argumento al VM (L63).
3. `OverlayViewModel.kt:63`: eliminar `val isNetworkAvailable = true // Se verifica dentro del use case` (muerta).
4. `OverlayUiState.kt:10`: eliminar `currentOutfitRes: Int? = null` (nunca escrito ni leído; el UI usa `characterState.currentAssetRes` — `CharacterState.currentOutfitRes` es un var real de otro objeto, SE MANTIENE con su test).

Tests: `OverlayViewModelTest` editado (quitar mock de screenContextRepository: L40, L53, L74) — las 24 aserciones intactas. Cero nuevos.

### 2.5 M22 — Constante duplicada + util muerto

**Decisión**:
1. `service/system/AlarmReceiver.kt`: eliminar `private const val TIMEOUT_MS` (L45) y usar `TaskerBridgeContract.TIMEOUT_MS` (mismo módulo service:system; el propio KDoc de la constante del contrato L25 la cita como "patrón AlarmReceiver, D2" — es LA MISMA semántica: ventana goAsync). Queda una única definición.
2. **ELIMINAR** `core/data/src/main/kotlin/com/screenassistant/core/data/util/NetworkUtils.kt` (cero call sites verificado; `SystemActionHandler.hasNetwork` L82-90 es la implementación viva, con la diferencia deliberada de no incluir ETHERNET — se mantiene tal cual).

Tests: 0 (no existe NetworkUtilsTest; AlarmReceiver no es testeable sin Robolectric — política de "Notas de proceso"). Verificación: grep `NetworkUtils` → solo docs/BACKLOG.

### 2.6 M23 — Corregir la premisa documental falsa (ADR-003 + KDoc)

**Decisión**: corregir la justificación, NO tocar la duplicación (el umbral del 3er consumidor NO se ha alcanzado).

1. `service/system/action/NoteAction.kt` KDoc L114-119 → texto correcto:
   > Normalización LOCAL (ADR-003): no se reutiliza `normalize` del parser porque su contrato es LONGITUD INVARIANTE (índices 1:1 para `extractAfterPrefix`; conserva ':' para TimePhraseParser) y aquí se necesita MATCH por substring con colapso de espacios (artefacto del dictado) + trim. service:system SÍ depende de core:domain (SystemCommandParser/SystemCommand/ActionResult…), así que la razón NO es de capas: es de contrato. Duplicación aceptada hasta un TERCER consumidor (hoy: 2 — parser.normalize y normalizeLocal); si aparece, extraer a un util compartido en core:domain.
2. `docs/BACKLOG.md:183` (ADR-003): enmendar el texto con la misma corrección (precedente: ADR-002/ADR-010 enmendados con precisión en lotes anteriores).

Tests: 0.

### 2.7 M18 — Declarar el contrato fail-soft (aceptado por diseño v1.2a)

**Decisión**: el comportamiento NO cambia (aceptado por diseño — los stores degradan con Log.w; eso es el contrato). Falta declararlo en los KDoc de los puntos de entrada del consumidor (la base ya lo hace en EncryptedPrefsStore L18-20):

1. `core/data/util/PuenteConfigStore.kt` — KDoc de `guardar()` (L78-84): añadir línea explícita: "Fail-soft aceptado por diseño (v1.2a): si la persistencia falla (Keystore/Tink/IO), la excepción se traga con Log.w y el EMISOR (PuenteSettingsViewModel.save → refresh) no distingue éxito de fallo — el estado mostrado refleja lo que `cargar()` devuelve (el valor previo si no persistió). Documentado en la base: EncryptedPrefsStore."
2. `core/data/util/ApiKeyProvider.kt` — KDoc de `storeApiKey()` (L44-46): misma declaración (la VM de API key marca "guardado" sin saber; aceptado v1.2a).

Tests: 0.

### 2.8 M20 — Corregir la falsa garantía de `_viewModelStore.clear()`

**Decisión**: **mantener la infraestructura** (`ViewModelStore`/`ViewModelStoreOwner`/`SavedStateRegistryOwner` son el estándar del host Compose en un servicio y el `setViewTreeViewModelStoreOwner` (L133) los requiere) y **corregir la documentación** con la garantía real. No registrar los VMs: la limpieza de TTS/STT ya la hace el `DisposableEffect` de la UI (AssistantOverlayUI L86-92) en el orden correcto (descomposición del ComposeView ANTES del onDestroy del servicio); registrar añadiría doble destroy sin valor.

`AssistantOverlayService.kt` KDoc de `onDestroy` (L163-165) → texto correcto:
> Los ViewModels de la feature se construyen MANUALMENTE (limitación KSP multi-módulo, ver arriba) y NO se registran en el ViewModelStore → `clear()` es un no-op formal (garantía real: (1) el DisposableEffect de AssistantOverlayUI destruye TTS/STT al descomponerse el ComposeView; (2) los `by lazy` del servicio mueren con la instancia por GC; `onCleared` del VM no se dispara por diseño).

Tests: 0. Smoke en dispositivo: cerrar el servicio → sin fugas de TTS (el TTS actual ya muere por DisposableEffect — sin cambio de comportamiento).

### 2.9 M22-extra del BACKLOG — (ya cubierto en 2.5)

### 2.10 M25 — Decisión sobre `hour ?: 0` (AlarmAction)

**Decisión**: **MANTENER** el fallback como contrato defensivo de la API pública, con KDoc actualizado. Justificación: (1) `AlarmActionTest:523` fija la semántica "hora sin minutos = en punto" — eliminar el `?: 0` exige eliminar el test (menos cobertura de una decisión documentada); (2) la API pública `cancelAlarm(hour: Int?, minute: Int?)` acepta medio-null por contrato; (3) ningún canal REAL lo produce hoy (parser → null/null o ambos; codec → rechaza medio-null; Gemini → sin función de cancelación), así que el fallback es la red de un contrato, no código muerto silencioso.

Cambio: 1 línea en el KDoc L105-107 — añadir "ningún canal produce medio-null hoy (parser/codec/Gemini): el `?: 0` es la red de un contrato de API cubierto por test, no un camino de producción".
La otra mitad de M25 (NoteAction catch, imports ConnectivityWorker) ya está pagada — **CERRADA** (§3.4).

### 2.11 EXTRA-1 — `@Volatile` en `capturaEnCurso` (ScreenContextService)

**Decisión**: pagar (1 línea + KDoc). Acceso cross-thread real: escribe el hilo IO del VM (captureScreenshot vía suspendCancellableCoroutine desde `viewModelScope.launch(ioDispatcher)`) y el executor de screenshots (onSuccess/onFailure). Sin @Volatile, una segunda petición concurrente puede leer un valor obsoleto → dos capturas simultáneas (viola el guard M8). Precedente: B6 (TTS @Synchronized/@Volatile) y el propio KDoc de ScreenContextRepositoryImpl:21 que lo cita como patrón.

```kotlin
// M8 + EXTRA L10: guard anti-reentrada. @Volatile: acceso cross-thread real
// (hilo IO del VM vs executor de screenshots) — mismo patrón que el KDoc del repo.
@Volatile
private var capturaEnCurso = false
```

Tests: 0 (no testeable sin instrumentación; el contrato fail-soft ya está cubierto).

### 2.12 EXTRA-2 — Guard `onRebind` (ScreenContextService)

**Decisión**: incluir (3 líneas, defensivo). Si el framework reconecta sin re-lanzar `onServiceConnected` (no garantizado por contrato entre versiones), el repo quedaría sin proveedor → captura null silenciosa. Coste ~0:

```kotlin
override fun onRebind(intent: android.content.Intent?) {
    super.onRebind(intent)
    // EXTRA L10: reconexión sin onServiceConnected → re-registrar el proveedor.
    if (::screenContextRepository.isInitialized) {
        screenContextRepository.setScreenCaptureProvider(this)
    }
}
```

Tests: 0.

---

## 3. Ítems excluidos / diferidos / cerrados — justificación

### 3.1 M6 — Tests de migración Room: **DIFERIDO** (rompe la regla sin valor suficiente)

- **Técnica**: `MigrationTestHelper` (androidx.room:room-testing) exige `InstrumentationRegistry` (contexto Android real) → SOLO funciona en `androidTest` (emulador/dispositivo) o bajo Robolectric. `Room.inMemoryDatabaseBuilder` también requiere contexto Android. **No existe camino JVM-puro** que respete la regla "sin Robolectric ni deps nuevas".
- **Valor**: MIGRATION_2_3 es `CREATE TABLE IF NOT EXISTS alarms` — creación idempotente SIN transformación de datos (v2 no reescribe filas existentes; las entidades v2 — memories/pending_messages — no cambian de esquema). El riesgo real de migración es bajo y el `fallbackToDestructiveMigration` (AppModule L69) es la red final.
- **Coste de pagarlo hoy**: dep nueva (room-testing) + carpeta androidTest + un emulador que NINGÚN pipeline ejecuta (el gate es 100% JVM; app/src/androidTest está vacío y así seguirá — ver M13).
- **Veredicto**: diferir con nota en BACKLOG; si algún día se mecaniza instrumentación (M13), `room-testing` es la vía canónica y este ítem se reabre con infra ya pagada.
- **Aclaración al BACKLOG**: `MessageDao` NO tiene @Upsert/deleteAll (usa @Insert/@Query — verificado); la sugerencia original no refleja el DAO real.

### 3.2 M13 — Gate mecanizado (JaCoCo + androidTest Compose): **DIFERIDO**

- **Coste**: JaCoCo multi-módulo con AGP 8.x (plugin + tasks + exclusiones por módulo en 5 build.gradle.kts) = infra de build de riesgo medio-alto; el "mínimo androidTest Compose" exige emulador y NO aporta al gate JVM (no se ejecuta en el comando del gate).
- **Valor**: el gate actual es reproducible (comando documentado, conteo por XML en BACKLOG, 777/0) y la disciplina de cobertura del proyecto ya está interiorizada (todo cambio con tests asociados).
- **Veredicto**: diferir con nota; la palanca de menor fricción si QA lo exige es un script `scripts/gate.ps1` que encapsule el comando + verificación del conteo (sin tocar build). No recomendado en este lote: ningún cambio de producción se beneficia de él.

### 3.3 M26 — **CERRADO** (verificado, 0 trabajo)

Todo lo pendiente del BACKLOG ya se pagó: casos de voz fijados con tests (SystemCommandParserTest L99-125 — "a las 7 y media"→null, "temporizador de 5"→null, "las 7"→null, con coVerify de no-ejecución), stub débil tipado (L84-85, comentario M26), fakes manuales de ChatViewModelTest eliminados con feature:chat (D2). Se documenta el cierre en BACKLOG; no se toca nada.

### 3.4 M25 (parte) — **CERRADA**

NoteAction catch colapsado (L72-74, Lote 8) e imports Assisted/AssistedInject muertos (worker eliminado, Lote 8): verificado, 0 trabajo. Solo queda la decisión documental de AlarmAction (§2.10).

---

## 4. Orden de ejecución y dependencias

```
1. M24 (rename + @Binds + catalog)  ← PRIMERO: renombrar impls toca imports (AppModule,
                                       GeminiRepositoryTest); hacerlo antes evita tocar
                                       AppModule dos veces (los @Provides mueren aquí)
2. M1 + M2 (parser)                  ← juntos: mismo archivo de producción y mismo test;
                                       M1 además toca SystemCommand/AccionRegistry/TimerAction
3. M10 (handler + 3 tests)           ← independiente (service:system)
4. M15 (overlay VM/servicio/estado)  ← independiente (feature:overlay + OverlayViewModelTest)
5. M22 (AlarmReceiver + NetworkUtils)← independiente (service:system + core:data)
6. Docs: M23 + M18 + M20 + M25-KDoc + EXTRA-1 + EXTRA-2  ← paralelos, cero tests, cero riesgo
7. Gate + cierre (BACKLOG: estado Lote 10 + ADR-003 enmendado + M6/M13 diferidos)
```

Dependencias críticas: **M24 → todo** (evita re-tocar AppModule). M1/M2 comparten SystemCommandParser.kt — si se hacen en pasos separados, el segundo paga un rebase trivial del mismo archivo. Nada más se solapa (archivos disjuntos por ítem).

## 5. Impacto UI/UX

- **Ningún cambio visual** (cero Compose tocado; M15 solo elimina campos muertos del estado).
- Cambios de COMPORTAMIENTO percibido (todos positivos):
  - M1: "temporizador de 0/5000 minutos" ya no configura basura → mensaje claro.
  - M2: "llama a 600 123 456 por favor" llama al número real (hoy llamaría al contacto basura).
  - M10: un fallo interno muestra "No pudo completarse la acción." en vez del mensaje crudo de la excepción.
- Smoke en dispositivo requerido: comandos de voz M1/M2; overlay tras M15 (arranca igual); cierre del servicio tras M20 (sin fugas TTS); captura de pantalla tras EXTRA (API 30+).

## 6. Veredictos de veto / diferimiento

| Ítem | Veredicto |
|------|-----------|
| M6 — Tests de migración Room | **DIFERIDO.** room-testing exige instrumentación o Robolectric; regla del equipo sin Robolectric/sin deps. Riesgo real bajo (CREATE TABLE idempotente + fallback destructivo). Nota: el BACKLOG describe @Upsert/deleteAll que el DAO real no tiene. |
| M13 — JaCoCo + androidTest | **DIFERIDO.** Infra de build multi-módulo sin pipeline que ejecute androidTest; el gate JVM es reproducible. |
| M25 — Eliminar `hour ?: 0` | **VETO a eliminar.** Contrato de API pública fijado por test (AlarmActionTest:523); solo se actualiza el KDoc. |
| M20 — Registrar los VMs en el store | **VETO a registrar.** La limpieza real (DisposableEffect) ya existe; registrar añadiría doble destroy de TTS/STT. Se paga la corrección documental. |
| M23 — Extraer normalizeLocal a util compartido | **DIFERIDO al 3er consumidor** (hoy 2). Solo se corrige la premisa falsa. |
| M24 — Catalog completo | **Parcial.** Catalog mínimo (Room + deps de test repetidas); migración gradual del resto documentada. |

## 7. Objetivo de gate del Lote 10

**Gate base: 777 (Lote 9, commit 0c24bac).**

| Ítem | Deltas |
|------|--------|
| M1 | +6 (parser +4: 0 min, 5000 min, 24h→1440, 1441 min; TimerActionTest +2: guard 0/1441) |
| M2 | +4 (por favor: número→CallNumber, contacto→Call, "al jefe", "llama a por favor"→error) |
| M10 | 0 (3 tests editados: Error("boom") → mensaje saneado) |
| M15 | 0 (OverlayViewModelTest editado: mock del repo fuera; 24 aserciones intactas) |
| M22, M23, M18, M20, M25, EXTRA-1, EXTRA-2 | 0 |
| M24 | 0 (ediciones; GeminiRepositoryTest 1 línea) |
| M6, M13, M26 | 0 (diferidos/cerrados) |

**Gate objetivo: 787 tests verdes** (777 + 10). Rango esperado 785–789 (el ± depende del conteo exacto del Desarrollador y de si añade más boundaries al parser).

## 8. Checklist para QA shift-left

- [x] M24: grep sin FQCN `com.screenassistant.core.data.repository.MemoryRepository` / `core.data.remote.GeminiRepository` (solo docs); DataModule con 4 @Binds; `gradle/libs.versions.toml` existe y `gradlew help` OK; Room sigue 2.6.1 en el grafo (dependencia resuelta, sin bump).
- [x] M1: `CorrespondenciaWireTest` y `SystemCommandJsonCodecTest` pasan INTACTOS (el rango 1..1440 no cambió de valor); parser no emite SetTimer fuera de rango (coVerify exactly=0 en los 4 tests); TimerAction guard ANTES del try (sin tocar context en el test).
- [x] M2: "llama a 600 123 456 por favor" → CallNumber("600123456"); "llama a la oficina" sigue → Call(contacto) (regresión cubierta por tests existentes L774-783).
- [x] M10: grep `e.message` en SystemActionHandler sin resultados; 3 tests con el nuevo mensaje; B5 intacto (test de 22 comandos con cancelación, L345-395, sin editar).
- [x] M15: grep `isNetworkAvailable` = 0 resultados en producción; `OverlayUiState.currentOutfitRes` = 0 (solo queda CharacterState.currentOutfitRes con su test); OverlayViewModelTest pasa sin el mock.
- [x] M22: grep `TIMEOUT_MS` → 1 definición (TaskerBridgeContract); grep `NetworkUtils` → solo docs/BACKLOG.
- [x] M23: grep `no debe filtrarse a service:system` = 0 en KDoc; ADR-003 enmendado en BACKLOG.
- [x] M18: KDoc de guardar()/storeApiKey() declaran el fail-soft v1.2a.
- [x] M20: KDoc del servicio declara la garantía real; smoke: cerrar servicio sin fugas.
- [x] EXTRA: `@Volatile` presente en capturaEnCurso; onRebind registra (grep).
- [x] Gate 787 verdes con el comando del §0.

---

## 9. Acta de cierre (Desarrollador, 03/08/2026)

**Gate final: 791 tests verdes / 0 fallos** (base 777 + 14). Comando ejecutado:
```
.\gradlew.bat compileDebugKotlin compileDebugUnitTestKotlin   → BUILD SUCCESSFUL (3m 7s)
.\gradlew.bat testDebugUnitTest :core:domain:test :app:assembleDebug --rerun-tasks → BUILD SUCCESSFUL (7m 54s + 1m 5s)
```
Desglose por módulo (XML en build/test-results): `:app 20 · :core:data 46 · :core:domain 456 · :feature:overlay 38 · :service:system 231` (core:ui sin tests). Sistema: app=testDebugUnitTest, core/data=testDebugUnitTest, core/domain=test (java-library), feature/overlay=testDebugUnitTest, service/system=testDebugUnitTest.

**Desviación vs §7 (gate objetivo 787)**: gate real 791 (+4). Causa: las exigencias QA shift-left de la tarea añadieron tests baratos no contados en el diseño — P1-2 ("temporizador de 1 minuto" → SetTimer(1)), P2-1 ("temporizador de un rato" → null), P1-1 (variante `porfavor`), P2-3 (SQL interno no filtrado). Delta exacto: M1 +6 (diseño) → +6 real (los 4 del diseño + P1-2 + P2-1); M2 +4 (diseño) → +5 real (4 + variante porfavor); M10 0 → +1 (P2-3); TimerAction +2 ✓. Todo lo demás coincide con el diseño. No hay desviaciones de comportamiento: los 12 ítems se implementaron tal cual, con los vetos respetados (M20 no registrar VMs, M25 mantener `hour ?: 0` y el catch, M23 sin refactor, Room sin bump, sin Robolectric ni deps nuevas).

Verificaciones QA de la sección 8: todas OK (grep documentados en el informe del Desarrollador). Smoke en dispositivo pendiente (comandos de voz M1/M2, overlay tras M15, cierre del servicio sin fugas TTS, captura de pantalla API 30+).
