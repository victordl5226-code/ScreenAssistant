# Diseño Lote 9 — Pago de la deuda estructural D1–D7

Autor: Arquitecto. Estado: **IMPLEMENTADO — GATE VERDE (03/08/2026, Desarrollador)**.
Base: commit `6ada101` (Lote 8 cerrado, 775 tests verdes). Referencia: docs/BACKLOG.md líneas 31–39.
Fecha: 03/08/2026.

> **Nota de cierre (Desarrollador)**: implementación conforme al diseño. Dos desviaciones
> menores anotadas en §1.3.4 y §3.3 (ver al final de cada sección). Gate real: **777 tests
> verdes / 0 fallos** (objetivo exacto: 775 − 8 + 10 = 777; desglose por módulo en §11).

---

## 0. Resumen ejecutivo

| Ítem | Decisión | Delta tests | Riesgo |
|------|----------|-------------|--------|
| **D1** | Pagar: mover `AssistantOverlayService` a `feature:overlay` (movimiento real, no interfaces) | 0 (sin tests del servicio) | MEDIO (manifest merge + imports) |
| **D2** | Pagar: **ELIMINAR** `feature:chat` (veto a integrar) | −8 (5 ChatViewModelTest + 3 SendMessageUseCaseTest) | BAJO |
| **D3** | Pagar parcialmente: catálogo extraído + test de paridad unidireccional. **VETO a la unificación en tabla única** | +2 | BAJO |
| **D4** | Pagar: base `EncryptedPrefsStore` en core:data/util | +6 (3 base + 3 ApiKeyProvider; los 8 de PuenteConfigStoreTest intactos) | BAJO |
| **D5** | Pagar: `compileOnly` → `implementation` en `:app` (1 línea) | 0 | NULO |
| **D6** | Pagar: `@Singleton` en `provideMessageQueueManager` (1 línea) — **DESVIACIÓN: se eliminó el provider completo, vía única `@Inject` + `@Singleton` de clase** | 0 | NULO |
| **D7** | Pagar (residuo de B3): auto-registro del servicio en el repo; eliminar companion/`instancia()`/`ServiceScreenCaptureProvider` | +2 (2 adaptados) | BAJO |

**Gate objetivo Lote 9: ≈ 777 tests verdes** (775 − 8 + 10).

Orden de ejecución: **D5 → D6 → D2 → D1 → D4 ∥ D3 → D7**.

Revisión visual: cierre con smoke test en dispositivo del overlay (arranque del servicio tras mover el manifest + un "analiza mi pantalla" para D7). Sin cambios de UX.

> **CIERRE (03/08/2026)**: gate real `testDebugUnitTest :core:domain:test :app:assembleDebug --rerun-tasks` →
> **BUILD SUCCESSFUL — 777 tests verdes / 0 fallos** (objetivo exacto). Smoke test en dispositivo pendiente
> de QA (arranque del overlay y "analiza mi pantalla" en API 30+); el manifest fusionado se verificó en
> build: contiene `com.screenassistant.feature.overlay.AssistantOverlayService`.

---

## 1. D1 — Inversión de límites `service:system → feature:overlay/chat`

### 1.1 Estado real verificado

- `service/system/build.gradle.kts:40-41`: `implementation(project(":feature:overlay"))` + `implementation(project(":feature:chat"))` — la única violación de capas del proyecto.
- `AssistantOverlayService` (service/system) importa de feature: `ChatViewModel` (chat), `AssistantOverlayUI`, `OverlayViewModel`, `OverlayWindowManager` (overlay).
- `NavGraph.kt` (service/system/navigation) importa `ChatScreen`/`ChatViewModel` — **sin call sites** (grep: solo definición).
- `MainActivity.kt:40` importa `com.screenassistant.service.system.AssistantOverlayService` y lo arranca vía `Intent(this, AssistantOverlayService::class.java)` (líneas 175 y 180).
- Los `AndroidManifest.xml` de feature:overlay y service:system son `<manifest />` vacíos; el servicio se declara en `app/src/main/AndroidManifest.xml:42-50`.

### 1.2 Evaluación de acoplamientos (¿crea la inversión opuesta?)

El servicio **NO usa nada** de service:system: no importa `SystemActionHandler`, acciones, bridge, receivers, ni `ScreenContextService`. Sus dependencias reales:

- core:domain: `GeminiRepository` (interfaz), `ConversationRepository` (interfaz), `ScreenContextRepository` (interfaz), `CaptureScreenContextUseCase`, `SystemCommandParser`, `IoDispatcher` — todas **interfaces/usecases de dominio**.
- feature:overlay: `AssistantOverlayUI`, `OverlayViewModel`, `OverlayWindowManager`.
- feature:chat: `ChatViewModel` (muerto — ver D2).
- AndroidX: `LifecycleService`, `ViewModelStore`/`SavedStateRegistry`, `ComposeView`, `NotificationCompat`, `WorkManager` (solo para `cancelConnectivityWorkerZombie`, M22).

→ **No hay acoplamiento hacia service:system: el movimiento es factible AHORA y no crea inversión opuesta.** El servicio ES la feature overlay (hostea su UI Compose y sus ViewModels).

### 1.3 Solución concreta (movimiento real)

1. **Mover** `service/system/.../AssistantOverlayService.kt` → `feature/overlay/src/main/kotlin/com/screenassistant/feature/overlay/AssistantOverlayService.kt` con package `com.screenassistant.feature.overlay`. Imports internos a feature:overlay (OverlayViewModel, AssistantOverlayUI, OverlayWindowManager) quedan en el mismo módulo.
2. **Manifest**: quitar el `<service android:name=".service.system.AssistantOverlayService">` (app manifest, líneas 42-50) y declararlo en `feature/overlay/src/main/AndroidManifest.xml` (la library YA tiene manifest propio; se fusiona en el de app):
   ```xml
   <service
       android:name="com.screenassistant.feature.overlay.AssistantOverlayService"
       android:enabled="true"
       android:exported="false"
       android:foregroundServiceType="specialUse">
       <property
           android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
           android:value="Floating AI Assistant overlay for screen interaction" />
   </service>
   ```
   Los permisos (`FOREGROUND_SERVICE`, `SYSTEM_ALERT_WINDOW`, etc.) ya están en el manifest de app — intactos.
3. **`feature/overlay/build.gradle.kts`** — añadir:
   - `implementation("androidx.lifecycle:lifecycle-service:2.8.7")` (LifecycleService; el módulo ya tiene lifecycle-viewmodel-compose/runtime-compose).
   - `implementation("androidx.work:work-runtime-ktx:2.10.0")` (único uso: `cancelConnectivityWorkerZombie`).
   - Nota: `androidx.savedstate` llega transitivo por compose (el módulo ya compila `SavedStateRegistryOwner` vía el BOM).
4. **`service/system/build.gradle.kts`** — quitar `implementation(project(":feature:overlay"))` y (con D2) `implementation(project(":feature:chat"))`. El grafo de service:system queda: core:domain + core:data + libs.
   - **DESVIACIÓN (implementación)**: además se eliminaron las dependencias AndroidX que quedaron DEMOSTRADAMENTE muertas tras D1+D2 (grep previo de `androidx.lifecycle|navigation|compose|work` en el módulo: 0 usos fuera de AssistantOverlayService y NavGraph): `navigation-compose`, `lifecycle-service`, `lifecycle-runtime-compose`, `work-runtime-ktx`, BOM compose + `ui` + `material3`, y el plugin `org.jetbrains.kotlin.plugin.compose` con `buildFeatures.compose`. Grafo final: core:domain + core:data + core-ktx + kotlinx-serialization-json + Hilt.
5. **`app/src/main/java/com/screenassistant/MainActivity.kt:40`** — import → `com.screenassistant.feature.overlay.AssistantOverlayService`.
6. **`service/system/src/main/kotlin/.../navigation/`** — directorio completo eliminado con D2 (NavGraph es código muerto).

### 1.4 Grafo de dependencias resultante

```
app → core:domain, core:data, core:ui, feature:overlay, service:system
feature:overlay → core:domain, core:ui (+ lifecycle-service, work-runtime-ktx)
service:system → core:domain, core:data           ← SIN dependencias de features ✓
core:data → core:domain
core:ui → (core:domain? verificar en el propio módulo)
```

### 1.5 Alternativas evaluadas

- **Extraer interfaces a core:domain (contratos de VM/UI)**: VETO. El servicio hostea UI Compose por naturaleza; abstraer `AssistantOverlayUI` (composables) o `OverlayViewModel` en contratos de core:domain es más caro que mover el archivo y no elimina la raíz (la feature de UI debe vivir en feature:overlay, punto). La alternativa real y barata del BACKLOG (mover manifest) es la correcta.
- **No mover, solo documentar**: rechazada — la violación es la única del proyecto y el movimiento es mecánico.

### 1.6 Riesgo y verificación

- Riesgo: manifest merge (el servicio debe aparecer en el APK final), imports de MainActivity, deps nuevas de feature:overlay.
- Verificación: `:app:assembleDebug` + inspección del manifest fusionado (p. ej. `aapt2 dump xmltree` del APK, o `:app:processDebugMainManifest` + grep `AssistantOverlayService`).
- Tests afectados: **0** (no existe test del servicio; OverlayViewModelTest/CharacterStateTest/TextToSpeechManagerTest no lo tocan).

---

## 2. D2 — Feature chat muerta: **ELIMINAR** (recomendación firme)

### 2.1 Estado real verificado (todo lo que dice el BACKLOG se confirma)

- `NavGraph.kt` / `ScreenAssistantNavGraph`: sin call sites (grep global: solo definición).
- `AssistantOverlayService.kt:70-75`: `chatViewModel` lazy construido y **nunca consumido** (`showOverlay` solo pasa `overlayViewModel`).
- `ChatScreen`: sin punto de entrada.
- `SendMessageUseCase` (core/domain): sin call sites de producción; solo `SendMessageUseCaseTest` (3 tests). Su lógica está triplicada y **divergente** con `OverlayViewModel.sendMessage` (el camino real: cola de mensajes propia, comandos directos, captura de pantalla M21, TTS).
- `ConversationRepository` (core/domain) + `ConversationRepositoryImpl` (core/data): inyectados en `OverlayViewModel` (constructor, sin uso — M15), en `AssistantOverlayService` (campo `@Inject`, sin uso) y provider en `AppModule.kt:311-318`. El `OverlayUiState.messages: List<ChatMessage>` **nunca se escribe** (M15) y `AssistantOverlayUI` no lo lee (grep: 0 usos).
- `Message` (core/domain/model) usado SOLO por feature:chat. `ChatMessage` usado por chat + ConversationRepository + OverlayUiState.
- `ChatViewModel` expone `e.localizedMessage` crudo (línea 62), sin Job de cancelación, sin guard de isLoading (el BACKLOG lo documenta; el OverlayViewModel ya resuelve todo esto con captura de pantalla y comandos directos).

### 2.2 ¿Integrar o eliminar? — ELIMINAR

- **Costo de integrar**: alto — unificar `Message`/`ChatMessage`, reconciliar la cola del overlay con el historial, dar una entrada de UI real (no existe pantalla de chat en la app; MainActivity es solo Settings), decisiones de UX, y tests nuevos. **Valor para el usuario final: ~cero** (el overlay ya muestra y habla la conversación en `assistantText`; el historial de ChatMessage es in-memory y nunca se lee).
- **Costo de eliminar**: bajo y mecánico (lista abajo).
- El OverlayViewModel ya tiene su propia cola/mensajería desde Lotes 6/8 → el chat es código muerto mantenido en paralelo. La regla del equipo (YAGNI + fuente única) manda: eliminar.

### 2.3 Lista exacta de archivos

**Borrar (13 archivos + 1 dir + 1 línea de settings):**

| Archivo | Notas |
|---------|-------|
| `feature/chat/build.gradle.kts` | módulo completo |
| `feature/chat/src/main/AndroidManifest.xml` | `<manifest />` vacío |
| `feature/chat/src/main/kotlin/com/screenassistant/feature/chat/ChatScreen.kt` | |
| `feature/chat/src/main/kotlin/com/screenassistant/feature/chat/ChatViewModel.kt` | |
| `feature/chat/src/test/java/com/screenassistant/feature/chat/ChatViewModelTest.kt` | **−5 tests** |
| `service/system/src/main/kotlin/com/screenassistant/service/system/navigation/NavGraph.kt` | dir `navigation/` completo, sin call sites |
| `core/domain/src/main/kotlin/com/screenassistant/core/domain/usecase/SendMessageUseCase.kt` | sin call sites |
| `core/domain/src/test/java/com/screenassistant/core/domain/usecase/SendMessageUseCaseTest.kt` | **−3 tests** (fakes manuales M26) |
| `core/domain/src/main/kotlin/com/screenassistant/core/domain/model/Message.kt` | solo lo usaba chat |
| `core/domain/src/main/kotlin/com/screenassistant/core/domain/model/ChatMessage.kt` | junto con el campo muerto de OverlayUiState |
| `core/domain/src/main/kotlin/com/screenassistant/core/domain/repository/ConversationRepository.kt` | inyectado y nunca usado |
| `core/data/src/main/kotlin/com/screenassistant/core/data/repository/ConversationRepositoryImpl.kt` | idem |
| `settings.gradle.kts:27` | quitar `include(":feature:chat")` |

**Editar:**

| Archivo | Cambio |
|---------|--------|
| `AssistantOverlayService.kt` | quitar `chatViewModel` lazy + campo `conversationRepository` @Inject (y sus imports) |
| `feature/overlay/.../OverlayViewModel.kt` | quitar parámetro `conversationRepository` del constructor (M15) |
| `feature/overlay/.../OverlayUiState.kt` | quitar `messages: List<ChatMessage>` + import (nunca escrito) |
| `feature/overlay/src/test/.../OverlayViewModelTest.kt` | quitar el mock `conversationRepository` del setup (24 tests restantes intactos — nunca se usaba) |
| `app/.../di/AppModule.kt` | quitar `provideConversationRepository` (líneas 311-318) |
| `app/build.gradle.kts:71` | quitar `implementation(project(":feature:chat"))` |
| `service/system/build.gradle.kts:41` | quitar `implementation(project(":feature:chat"))` |

**NO se toca**: `MessageQueueManager`/`MessageDao`/`PendingMessageEntity` (cola de SMS del puente — `encolar_mensaje`, usada por `MessagingAction`). Ojo: no confundir con el historial de chat.

### 2.4 Sobre SendMessageUseCase y ChatViewModelTest

- `SendMessageUseCase`: se elimina con sus 3 tests. Su lógica ya vive (mejor) en `OverlayViewModel.sendMessage` — no se migra nada.
- `ChatViewModelTest` (5 tests): se eliminan junto al módulo. No hay lógica que preservar (ChatViewModel no aporta nada que OverlayViewModel no tenga).

---

## 3. D3 — Segundo catálogo de acciones (Gemini vs AccionRegistry)

### 3.1 Estado real verificado

- 8 `FunctionDeclaration` privadas en `GeminiRepository.kt:41-118` (nombres en inglés: `open_alarms`, `set_alarm`, `search_google`, `open_youtube`, `open_whatsapp`, `play_music`, `save_memory`, `open_app`) + el `when` de ejecución inline (líneas 229-302).
- `AccionRegistry` (core:domain, JVM puro): 22 wires en español (`abrir_alarmas`...), custodiados por `CorrespondenciaWireTest` (M4: detecta adiciones vía `todosLosWires`).
- `GenerativeModelFactory.create(apiKey, tools: List<Tool>, systemInstruction)` — los tools se pasan al modelo.

### 3.2 Decisión: **NO unificar** — extraer catálogo + test de paridad unidireccional

Por qué NO una tabla única generada desde AccionRegistry:

1. **Capas**: `FunctionDeclaration`/`Schema` son tipos del SDK de Gemini (core:data). `AccionRegistry` es core:domain JVM puro. Generar tools desde el registro obligaría a acoplar domain al SDK — la inversión exacta que este lote elimina.
2. **Vocabulario distinto POR DISEÑO**: el LLM emite nombres en inglés (vocabulario del modelo, probado en producción); el wire es NUESTRO vocabulario español para Tasker (ADR-013 H1). No hay razón semántica para igualarlos.
3. **Cobertura distinta**: 8 funciones LLM vs 22 wires. La unificación exigiría un meta-modelo de acciones (nombre LLM + wire + prompt + handler + límites) para 22 acciones cuando Gemini solo expone 8 — YAGNI.

### 3.3 Diseño concreto

1. **Extraer** en core:data/remote un objeto `GeminiFunctionCatalog` (nombre propuesto; alternativa: `GeminiToolsCatalog`):
   - `val declaraciones: List<FunctionDeclaration>` — las 8 declaraciones actuales, sin lógica.
   - `val nombres: Set<String>` — los 8 nombres.
   - KDoc cruzado: "catálogo del canal LLM; el canal wire vive en `AccionRegistry` (core:domain). Vocabularios independientes por diseño."
2. **Centralizar** el mapeo nombre→SystemCommand (hoy inline en el `when` de `sendMessage`) en una función del catálogo o del propio repositorio (decisión del Desarrollador; mínimo: dejar el `when` pero alimentado por `nombres`).
3. **Test de paridad nuevo** en core:data: `CorrespondenciaGeminiWireTest` (2 tests):
   - Para cada SystemCommand que Gemini puede emitir → `AccionRegistry.wireDe(subtipo) != null` (todo lo ejecutable por el LLM tiene wire Tasker registrado).
   - Paridad **UNIDIRECCIONAL y documentada**: añadir una función Gemini sin wire → rojo; añadir un wire sin función Gemini → verde (el LLM no cubre los 22).
4. `GeminiRepository.kt` consume el catálogo (tools = `GeminiFunctionCatalog.declaraciones`).
   - **DESVIACIÓN (implementación)**: el `when` de EJECUCIÓN de function-calls se mantiene inline en `sendMessage` (opción "mínimo" del propio diseño: "dejar el when pero alimentado por nombres" — la ejecución lleva validación B2, args y memoria; moverla exigiría un meta-modelo). El mapeo nombre→destino (P1-3) vive en el catálogo como `wirePorNombre`; P1-4 se resuelve mapeando `save_memory → recordar_dato` con KDoc + aserción en el test (la ejecución runtime sigue yendo directa a MemoryRepository).

### 3.4 Escala

Con 8 vs 22 la duplicación es tolerable y **custodiada**. El umbral de revisión: si se añade una 9ª función Gemini, el test de paridad obliga a decidir (wire nuevo o exclusión documentada). Unificación total diferida (requiere meta-modelo; no aporta al usuario).

Tests: +2. Riesgo: bajo (cero cambio de runtime si el refactor es puramente estructural).

---

## 4. D4 — Base `EncryptedPrefsStore` (cifrado + fallback)

### 4.1 Estado real verificado

`ApiKeyProvider` y `PuenteConfigStore` (ambos en core:data/util) duplican ~1:1: `MasterKey.Builder(AES256_GCM)` + `EncryptedSharedPreferences.create(...)` + catch → `isUsingFallback = true` + `getSharedPreferences("..._fallback")`. Firmas actuales:

- `ApiKeyProvider`: `getApiKey(): String`, `storeApiKey(key)`, `clearApiKey()`, `sembrarDesdeBuildConfig(buildConfigKey)` (con flag `api_key_seeded` — B4), `isUsingFallback` (private set).
- `PuenteConfigStore`: `cargar(): PuenteConfig` (materializa `PACKAGE_RESPUESTA_DEFAULT` — H1), `guardar(config)`, `isUsingFallback`.

### 4.2 Diseño: `EncryptedPrefsStore` (core:data/util)

```kotlin
/**
 * Base de prefs cifradas con fallback (D4): EncryptedSharedPreferences AES256_GCM
 * + degradación a SharedPreferences normales si Keystore/Tink no está disponible.
 * Fuente única del patrón que duplicaban ApiKeyProvider y PuenteConfigStore.
 */
class EncryptedPrefsStore private constructor(
    private val prefs: SharedPreferences,
    val isUsingFallback: Boolean          // true si cayó al fallback
) {
    companion object {
        fun create(
            context: Context,
            prefsName: String,             // "secure_api_prefs" | "secure_puente_prefs"
            fallbackName: String,          // "secure_api_prefs_fallback" | "secure_puente_prefs_fallback"
            tag: String                    // log
        ): EncryptedPrefsStore             // lazy NO — creación explícita al construir el store
    }

    fun getString(key: String): String?    // catch → null + Log.w
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putString(key: String, value: String?)  // null = remove (semántica SharedPreferences)
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
}
```

Notas de diseño:
- `isUsingFallback` se expone igual que hoy (lectura pública, set privado) — los consumidores no cambian.
- El flag `isUsingFallback` se marca solo en el catch de `create()` (mismo comportamiento actual).
- `create()` no es lazy: el patrón actual usa `by lazy` en cada store; se conserva el lazy **en el store consumidor** (ApiKeyProvider/PuenteConfigStore mantienen `private val store by lazy { EncryptedPrefsStore.create(...) }`) para no tocar el momento de creación. Comportamiento idéntico.

### 4.3 Cómo quedan ambos stores

- `ApiKeyProvider`: `getApiKey` = `store.getString("gemini_api_key") ?: ""`; `storeApiKey` = `store.putString(...)`; `clearApiKey` = `store.remove(...)`; `sembrarDesdeBuildConfig` = flag `getBoolean("api_key_seeded", false)` + `putBoolean` + `putString` (un solo edit: verificar que la base permita transacción por lotes o aceptar 2 operaciones — **requisito: la base debe poder ejecutar varias ops y `apply()` una vez, o el Desarrollador conserva el edit único del sembrado**; propuesta: método `edit { editor -> ... }` en la base).
  - Añadir: `fun edit(block: (Editor) -> Unit)` — permite el edit atómico del sembrado (B4: sin estados intermedios observables).
- `PuenteConfigStore`: `cargar`/`guardar` usan getString/getBoolean/putString/putBoolean sobre sus 4 claves; la materialización H1 sigue en `cargar()` (no se mueve a la base).
- API pública de ambos: **intacta** → `PuenteConfigStoreTest` (8 tests) pasa sin cambios; los mocks de `getSharedPreferences` del fallback siguen funcionando (la base llama a la misma API).

### 4.4 Tests

- `EncryptedPrefsStoreTest` (nuevo, patrón M5 de PuenteConfigStoreTest): +3 (fallback flag al crear en JVM; roundtrip putString/getString/putBoolean; remove borra).
- `ApiKeyProviderTest` (nuevo — hoy sin cobertura): +3 (roundtrip get/store/clear; sembrar una sola vez con flag; no re-sembrar tras clear — B4).
- PuenteConfigStoreTest: 0 delta.

---

## 5. D5 — `compileOnly` → `implementation` en `:app` (1 línea)

**Verificado**: `app/build.gradle.kts:82` → `compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")`. Cambio a `implementation`.

**Por qué es seguro**:
1. La versión 1.7.3 es la misma en todo el grafo (service:system, core:data, core:domain) → Gradle resuelve 1.7.3 en todos los caminos, cero conflictos.
2. El runtime YA llegaba a `:app` transitivamente por service:system → el APK no cambia de tamaño ni de contenido.
3. El cambio hace explícito el contrato de compilación de `:app` (AppModule compila contra el constructor sintético de `SystemCommandJsonCodec` con `Json`): si service:system dejara de exponer la librería, `:app` compila y ejecuta sin `NoClassDefFoundError` silencioso.
4. Actualizar el comentario de las líneas 79-81 (ya no aplica la justificación de compileOnly).

Tests: 0. Riesgo: nulo.

---

## 6. D6 — `provideMessageQueueManager` sin `@Singleton`

**Verificado**:
- `AppModule.kt:320-323`: `@Provides fun provideMessageQueueManager(messageDao: MessageDao): MessageQueueManager` — **sin scope**.
- `MessageQueueManager.kt:7`: clase anotada `@Singleton` (con `@Provides` no-scoped, Dagger usa el scope del PROVIDER, no el de la clase → instancias nuevas por inyección).
- Único consumidor de producción: `MessagingAction` (inyectada por `provideMessagingAction` con `@Singleton`) → de facto singleton hoy; el bug es **preventivo** (un futuro consumidor obtendría otra instancia).

**Decisión**: añadir `@Singleton` al `@Provides` (1 línea). Se alinea con la clase y con el resto de provides del AppModule. La alternativa "vía única" (quitar el `@Provides` y dejar `@Inject constructor` + `@Singleton`, precedente PuenteConfigStore — O2) es más idiomática pero toca el patrón de 4 DAO providers de AppModule → diferida a M24 (naming/convención DI).

Tests: 0. Riesgo: nulo.

---

## 7. D7 — ScreenContextService: pagar el residuo del service-locator

### 7.1 Estado real verificado (B3 ya pagó el 90%)

- ✅ `suspendCancellableCoroutine` + fail-soft null + `hardwareBuffer.close()` en finally + JPEG q80 scale 1024 (B3/M8).
- ✅ Callback único/estáticos `takeScreenshot` ELIMINADOS; `capturaEnCurso` anti-solapamiento (por captura, no global).
- ✅ `ScreenContextRepositoryImpl` delega en `ScreenCaptureProvider` inyectado (core:domain interfaz).
- ❌ **Sigue existiendo**: companion con `instanceRef: WeakReference` + `instancia()` (usado por `ServiceScreenCaptureProvider` — object en service:system/bridge) + `lastScreenText` estático (**MUERTO**: nadie lo lee; la vía real del texto es `updateScreenText()` → StateFlow del repo; grep confirma solo escritura).
- ❌ `AppModule.provideScreenCaptureProvider` (líneas 278-282) devuelve el object.

### 7.2 Diseño: auto-registro del servicio en el repositorio

El problema de fondo: la instancia viva de un `AccessibilityService` la crea el sistema — no es inyectable desde el grafo Hilt de la app. La solución canónica (y la que pide el BACKLOG: "inyección vía ScreenContextRepository") es que el **servicio se registre a sí mismo** en el repo (dependencia service→data→domain, unidireccional y sin inversión):

1. **`ScreenContextRepositoryImpl` (core:data)**:
   - Constructor SIN `screenCaptureProvider` (los 2 tests existentes se adaptan).
   - `@Volatile private var screenCaptureProvider: ScreenCaptureProvider? = null`
   - `fun setScreenCaptureProvider(provider: ScreenCaptureProvider?)` — registro dinámico (main thread: onServiceConnected/onUnbind/onDestroy).
   - `captureScreenshot()` = `screenCaptureProvider?.captureScreenshot()` (null fail-soft si el servicio no está conectado — mismo contrato).
2. **`ScreenContextService` (service:system)**:
   - Inyecta `ScreenContextRepository` (interfaz core:domain) en vez de la impl concreta.
   - `onServiceConnected`: `screenContextRepository.setScreenCaptureProvider(this)` (ya implementa `ScreenCaptureProvider`).
   - `onUnbind`/`onDestroy`: `setScreenCaptureProvider(null)`.
   - **Eliminar el companion completo** (instanceRef, `instancia()`, `lastScreenText`).
3. **Borrar**: `service/system/.../bridge/ServiceScreenCaptureProvider.kt` + `AppModule.provideScreenCaptureProvider`.
4. **KDoc** de `ScreenCaptureProvider` (core:domain): actualizar el contrato ("el proveedor real es el propio servicio de accesibilidad, que se auto-registra en el repositorio").

Riesgos controlados:
- Proceso muerto sin `onDestroy`: el repo retiene una referencia al objeto del servicio desconectado; `captureScreenshot()` lanza → catch existente → null (fail-soft ya probado en B3). Al recrearse el proceso, `onServiceConnected` re-registra.
- Carrera de hilos: registro en main, lectura en IO → `@Volatile` suficiente (mismo patrón que `capturaEnCurso`).
- **No rompe B3**: la cadena use case → repo → provider → servicio es idéntica; solo cambia el mecanismo de obtención del provider (registro vs WeakReference estático).

Tests: `ScreenContextRepositoryImplTest` 2 adaptados + 2 nuevos ("sin provider registrado → null fail-soft"; "setScreenCaptureProvider(provider) delega y propaga"). Delta +2.

---

## 8. Orden de ejecución y dependencias

```
1. D5 (1 línea) ─┐  calentamiento, sin dependencias
2. D6 (1 línea) ─┘
3. D2 (eliminar chat)  ← PRIMERO: quita feature:chat del grafo y los campos muertos
                          (chatViewModel/conversationRepository) del servicio ANTES de moverlo
4. D1 (mover servicio) ← DESPUÉS de D2: el servicio se mueve sin el lastre del chat;
                          service:system pierde AMBAS deps de features en un solo paso
5. D4 (EncryptedPrefsStore) ─┐ paralelos e independientes (solo core:data + tests)
6. D3 (catálogo Gemini)     ─┘
7. D7 (auto-registro)  ← al final: toca AppModule (quitar provider) y ScreenContextService;
                          si se hace junto a D1, editar el archivo YA movido (solo
                          AssistantOverlayService se mueve; ScreenContextService se queda)
```

Dependencias críticas: **D2 → D1** (el servicio se mueve sin el chat). D7 es independiente pero conviene tras D1 para no tocar AppModule dos veces en el mismo archivo.

---

## 9. Impacto UI/UX

- **Ningún cambio visual** en D2–D6.
- **D1**: el overlay es idéntico; pero al mover el componente del manifest, el cierre del Lote 9 exige **smoke test en dispositivo**: arrancar el servicio desde MainActivity, verificar que la burbuja aparece, que responde a un comando directo ("pon una alarma a las 7") con TTS.
- **D7**: verificar "analiza mi pantalla" en API 30+ (la captura llega a Gemini como imagen — es la pieza O8 del backlog que este ítem desbloquea).
- No se requieren capturas de UI ni pruebas Compose nuevas.

---

## 10. Veredictos de veto / diferimiento

| Ítem | Veredicto |
|------|-----------|
| D2 — INTEGRAR el chat | **VETO a integrar.** Se elimina. Cero call sites + cero valor de usuario + lógica divergente con OverlayViewModel. |
| D3 — Tabla única generada desde AccionRegistry | **VETO a unificar.** Capas incompatibles (SDK vs domain puro), vocabularios distintos por diseño, cobertura 8 vs 22. Se paga con catálogo extraído + test de paridad unidireccional. |
| D1 — Alternativa de interfaces en core:domain | **VETO a la alternativa.** El movimiento es más barato y resuelve la raíz. |
| D6 — Quitar el @Provides (vía única @Inject) | Diferido a M24 (convención DI), se paga el mínimo (1 línea). |
| D7 | **Se paga completo** (residuo pequeño y acotado). Si QA lo prefiere, la alternativa documentada es diferirlo con KDoc del service-locator como patrón aceptado — no recomendado: el residuo es 2 archivos y el delta de tests es +2. |

---

## 11. Objetivo de gate del Lote 9

**Gate base: 775 (Lote 8, commit 6ada101).**

| Ítem | Deltas |
|------|--------|
| D2 | −8 (ChatViewModelTest −5, SendMessageUseCaseTest −3) |
| D3 | +2 (CorrespondenciaGeminiWireTest) |
| D4 | +6 (EncryptedPrefsStoreTest +3, ApiKeyProviderTest +3; PuenteConfigStoreTest 8 intactos) |
| D7 | +2 (ScreenContextRepositoryImplTest: 2 adaptados + 2 nuevos) |
| D1, D5, D6 | 0 |

**Gate objetivo: ≈ 777 tests verdes** (rango esperado 775–780; el +/− depende del conteo exacto del Desarrollador en SendMessageUseCaseTest y del redondeo de tests nuevos).

Comando de gate (patrón de los lotes anteriores):
```
gradlew testDebugUnitTest :core:domain:test :app:assembleDebug --rerun-tasks
```

> **GATE REAL (03/08/2026)**: BUILD SUCCESSFUL — **777 tests / 0 fallos** (objetivo exacto).
> Desglose por XML de `build/test-results`: **:app 20 · :core:data 46 · :core:domain 445 ·
> :feature:overlay 38 · :service:system 228** (total 777). Detalle: D2 −8 (5 ChatViewModelTest +
> 3 SendMessageUseCaseTest), D3 +2 (CorrespondenciaGeminiWireTest), D4 +6 (EncryptedPrefsStoreTest 3 +
> ApiKeyProviderTest 3; PuenteConfigStoreTest 8 intactos), D7 +2 (ScreenContextRepositoryImplTest
> 2 adaptados + 2 nuevos).

---

## 12. Checklist para QA shift-left

- [ ] D2: grep global de `ChatScreen|ChatViewModel|SendMessageUseCase|ConversationRepository|ChatMessage|model.Message` sin resultados tras el lote (excepto docs/BACKLOG).
- [ ] D1: manifest fusionado contiene `AssistantOverlayService` con `com.screenassistant.feature.overlay`; `service:system/build.gradle.kts` sin `project(":feature:overlay")` ni `project(":feature:chat")`.
- [ ] D1: MainActivity compila con el nuevo import; el servicio arranca (smoke).
- [ ] D4: `PuenteConfigStoreTest` (8) pasa SIN editar; `ApiKeyProvider` conserva B4 (sembrar 1 vez, no re-sembrar tras clear).
- [ ] D5: `:app` compila con `implementation` de serialization-json; APK sin cambios de tamaño relevantes.
- [ ] D6: `provideMessageQueueManager` con `@Singleton`.
- [ ] D7: grep de `instancia\(\)|ServiceScreenCaptureProvider|lastScreenText` sin resultados; `capturaEnCurso`/fail-soft intactos; smoke "analiza mi pantalla".
- [ ] Gate 777 verdes con el comando del §11.
