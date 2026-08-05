# DISEÑO Lote 12 — Gate mecanizado (M13) + Tests de migración Room (M6)

- **Autor**: Arquitecto (diseño) — revisión shift-left de QA
- **Fecha**: 04/08/2026 — **v1.1 (04/08/2026): APROBADO CON EXIGENCIAS P1 por QA shift-left**; correcciones incorporadas: P1 cableado de assets de esquema (§3.2/§4.3), P2-1 `:app` fuera del gate (§5.2/§5.4), P2-2 exclusión `*_GeneratedInjector` (§5.1), P2-3 `dependsOn` interno (§5.1), P2-4 controles positivos del bootstrap (§4.4), verificaciones QA (§4.5/§5.3/§8), checklist de cierre (§8.1)
- **Base**: commit `7c0aab1` (Lote 11 cerrado, **795 tests / 0 fallos**, todo JVM + MockK, sin Robolectric)
- **Excepción de regla**: AUTORIZADA por el usuario para M6/M13 ("sin Robolectric ni deps nuevas" queda suspendida SOLO para los artefactos listados en §3; nada más entra)

---

## 1. Resumen de decisiones

| Decisión | Elección | Por qué |
|---|---|---|
| Mecanismo M6 (migraciones) | **JVM + Robolectric** en `core:data` (`testDebugUnitTest`) | El gate local es JVM puro; la única forma de que los tests de migración corran en el gate SIN dispositivo. La alternativa (instrumentación en androidTest) los convertiría en peso muerto: el entorno no tiene dispositivo siempre |
| Dónde viven los tests de migración | `core:data/src/test` (la DB es de core:data, no de :app) | `AppDatabase` (@Database v3) y `MIGRATION_2_3` viven en `core:data/local`; el builder de :app (AppModule) NO es testeable aquí (queda documentado como fuera de alcance) |
| Esquemas Room | **`exportSchema = true` + `room.schemaLocation` + `sourceSets["test"].assets.srcDir("$projectDir/schemas")`** (P1) en core:data, JSON commiteados | `MigrationTestHelper` exige los JSON de esquema como assets de TEST; hoy `exportSchema = false` y NO hay carpeta schemas. `isIncludeAndroidResources=true` NO basta: `schemas/` es salida de KSP, no un source set → hay que cablearla como asset de test (P1 bloqueante, §4.3). El esquema `2.json` (v2 = v3 sin `alarms`) se bootstrapa con build temporal version=2 (§4.4) |
| Alcance M6 | Migración **2→3 únicamente** + CRUD de `pending_messages` in-memory | Es la ÚNICA migración que existe (la DB nació en v3 en este repo; no existe 1→2). `MessageDao` es @Insert/@Query/@Delete — la sugerencia @Upsert/deleteAll del BACKLOG era genérica y NO refleja el DAO real (ya aclarado en el propio BACKLOG M6) |
| JaCoCo | Plugin `jacoco` con `toolVersion 0.8.13` en 5 módulos; report + verification **por módulo** | Umbral global exige merge de exec de 5 módulos (más infra, menos accionable); por módulo es accionable y aísla módulos altos (core:domain) de bajos (app) |
| Umbrales | **Por módulo, calibrados en 2 fases** (medir → fijar con margen). `:app` SIN enforcement inicial | Sin medición previa (cero reportes existen), fijar umbrales agresivos ROMPERÍA el gate. Regla: el cierre del lote 12 deja el gate mecanizado VERDE |
| UI tests (M13) | **Composables puros** (ApiKeySection/MainScreen) con `createComposeRule`/`createAndroidComposeRule<ComponentActivity>`, **SIN Hilt en el test** | `hiltViewModel()` vive solo en MainActivity; los composables reciben estado+callbacks → testear MainActivity exigiría HiltTestApplication + grafo completo + Room real → frágil. Camino más simple y robusto |
| Gate mecanizado | Tarea raíz **`gate`** (JVM: unit tests + assemble + JaCoCo enforcement). `:app:connectedDebugAndroidTest` queda como **tarea separada documentada** | Exigir connected en el gate rompería la iteración local (sin dispositivo siempre). El gate mecanizado cubre lo que se puede verificar sin hardware |
| androidTest | 2-3 archivos, ~8 tests, deps `androidx.test` mínimas (ext:junit, runner, rules) | Sin espresso (Compose puro no lo necesita), sin hilt-android-testing (composables puros) |

---

## 2. Estado verificado (hechos, no supuestos)

### 2.1 Migraciones Room
- **Una sola migración**: `AppDatabase.MIGRATION_2_3` (companion de `AppDatabase.kt`, NO existe `Migrations.kt`) — `object : Migration(2, 3)` que ejecuta `CREATE TABLE IF NOT EXISTS alarms (...)`, replicando el CREATE TABLE que Room genera para `AlarmEntity` (PK a nivel de tabla: `PRIMARY KEY(requestCode)`).
- **NO existe 1→2**: `git log` del repo muestra `AppDatabase.kt` creado en el commit inicial (`e25a72d`) ya con `version = 3`, `exportSchema = false` y las 3 entidades. La v2 es un estado histórico (memories + pending_messages, sin alarms) del que v3 migra.
- **`fallbackToDestructiveMigration()`** registrado en el builder de `:app` (AppModule.kt:71). No testeable desde core:data (vive en el builder de :app); el contrato testeable es `MIGRATION_2_3`.
- **`exportSchema = false`** → NO existe carpeta `schemas/` ni `room.schemaLocation` en ningún build. **Bloqueante para MigrationTestHelper** (§4.3).
- Room 2.6.1: **`androidx.room:room-testing:2.6.1` existe** (misma línea de versión, publicada para todo 2.x).

### 2.2 DAOs
- `MessageDao`: `@Insert insertMessage`, `@Query getAllPendingMessages` (Flow, ORDER BY timestamp ASC), `@Query deleteMessage(id)`, `@Query clearAll`. **SIN @Upsert**.
- `PendingMessageEntity`: tabla `pending_messages`, PK autoincrement `id`, campos platform/contactName/contactNumber(nullable)/message/timestamp.
- **No existen tests de DAO hoy** (los 7 archivos de core:data test son stores/repos/remote — los "tests de DAO" mencionados en el toml murieron con feature:chat en Lote 9).

### 2.3 Conteo real de tests (verificado por @Test)
| Módulo | Archivos | @Test | Nota |
|---|---|---|---|
| app | 2 | 20 | 2 VMs (ApiKeyViewModel, PuenteSettingsViewModel) |
| core:data | 7 | 49 | Sin DAO ni Room en tests |
| core:domain | 9 | 456 | JVM puro |
| core:ui | 0 | 0 | Solo tema |
| feature:overlay | 3 | 38 | |
| service:system | 16 | 232 | |
| **Total** | | **795** | Coincide con el gate |

### 2.4 UI (testeabilidad)
- `MainScreen` y `ApiKeySection` son **composables puros**: todo estado y callbacks por parámetro; `hiltViewModel()` se invoca en MainActivity (setContent), NO dentro de MainScreen.
- `MainScreen` usa `LocalContext` (para `isAccessibilityServiceEnabled` → lee `Settings.Secure`) y `LifecycleResumeEffect` → funciona con `createAndroidComposeRule<ComponentActivity>` (contexto real del dispositivo). El estado de accesibilidad es del dispositivo real → los asserts NO deben fijarse en ese texto.
- `PuenteSettingsSection`: mismo patrón puro (candidato a 1-2 tests opcionales).
- `:app` ya tiene `testInstrumentationRunner = AndroidJUnitRunner`, `androidTestImplementation(compose-bom)`, `ui-test-junit4` y `debugImplementation(ui-test-manifest)` (registra ComponentActivity en debug). Falta solo el set `androidx.test`.

### 2.5 Build
- Gradle **9.3.0** (wrapper), AGP **8.7.3**, Kotlin **2.0.0**, KSP **2.0.0-1.0.22**, Hilt **2.53.1**. jvmTarget 11. compileSdk 35 / minSdk 26 / targetSdk 35.
- Catalog mínimo (`gradle/libs.versions.toml`): room 2.6.1, junit 4.13.2, mockk 1.13.12, coroutines-test 1.9.0.
- **Cero JaCoCo, cero Robolectric, cero room-testing, cero androidx.test** en el grafo (grep verificado).
- Compatibilidades confirmadas:
  - **Robolectric 4.14** (recomendado 4.14.1): API 21→35, era AGP 8.7.x — casa con el stack.
  - **JaCoCo 0.8.13** (estable, 04/2025): soporte oficial Java 23/24 + mejora de line coverage de inline functions Kotlin; compatible con Gradle 9.x (el mínimo soportado por Gradle 9 es la línea 0.8.13+).
  - `MigrationTestHelper` (room-testing 2.6.1) lee los esquemas de **assets** en `schemas/<canonicalName>/<version>.json` — patrón Robolectric documentado ("Testing Room as JUnit Test Not AndroidTest": CI sin emulador).

---

## 3. Excepción de regla FORMAL

Regla suspendida: *"sin Robolectric ni dependencias nuevas"* (ADR-014/T8). Alcance: SOLO los artefactos de este lote. Todo lo demás sigue la regla.

### 3.1 Dependencias nuevas (todas al catalog — convención desde Lote 10)

| Artefacto (grupo:nombre) | Versión | Módulo | Scope |
|---|---|---|---|
| `androidx.room:room-testing` | 2.6.1 (ref `room`) | `core:data` | `testImplementation` |
| `org.robolectric:robolectric` | 4.14.1 | `core:data` | `testImplementation` |
| `androidx.test:core` | 1.6.1 | `core:data` | `testImplementation` (ApplicationProvider/InstrumentationRegistry en Robolectric) |
| `androidx.test.ext:junit` | 1.2.1 | `app` | `androidTestImplementation` |
| `androidx.test:runner` | 1.6.2 | `app` | `androidTestImplementation` |
| `androidx.test:rules` | 1.6.1 | `app` | `androidTestImplementation` |

- **NO entran**: espresso-core (Compose puro), hilt-android-testing (composables puros), androidx.sqlite (transitiva de room-testing, misma versión del árbol Room 2.6.1).
- Entradas nuevas en el toml: `[versions]` → `robolectric = "4.14.1"`, `androidx-test-core = "1.6.1"`, `androidx-test-runner = "1.6.2"`, `androidx-test-rules = "1.6.1"`, `androidx-test-ext-junit = "1.2.1"`, `jacoco = "0.8.13"`; `[libraries]` → room-testing, robolectric, androidx-test-core, androidx-test-ext-junit, androidx-test-runner, androidx-test-rules. (Jacoco no es una library: es toolVersion del plugin — la versión entra al toml igualmente como fuente única.)
- Versiones elegidas = época del stack probado (Compose BOM 2024.11.00, activity 1.9.3, Room 2.6.1). Existen versiones más nuevas (ext:junit 1.3.x, runner 1.7.x, Robolectric 4.15/4.16, JaCoCo 0.8.14/0.8.15): NO se adoptan — política de pinning conservador del proyecto (migración gradual documentada).

### 3.2 Cambios de build (no son dependencias)

| Cambio | Módulo | Nota |
|---|---|---|
| `id("jacoco")` + `jacoco { toolVersion = "0.8.13" }` | root (toolVersion), app, core:data, core:domain, feature:overlay, service:system | core:ui sin tests → fuera |
| `buildTypes.debug.enableUnitTestCoverage = true` | app, core:data, feature:overlay, service:system | módulos Android (core:domain usa el plugin jvm nativo) |
| `testOptions.unitTests.isIncludeAndroidResources = true` | core:data | **OBLIGATORIO** para el Context/application de Robolectric (ApplicationProvider) — pero NO expone `schemas/` (es salida de KSP, no un source set): ver fila siguiente (P1) |
| `sourceSets["test"].assets.srcDir("$projectDir/schemas")` | core:data | **BLOQUEANTE P1 (QA shift-left)**: expone `schemas/com/screenassistant/core/data/local/AppDatabase/{2,3}.json` como assets de unit test; sin esto `context.assets.open("schemas/...")` lanza FileNotFoundException en T1/T2/T3. Patrón oficial "Testing Room as JUnit test" con Robolectric |
| `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` | core:data | genera `schemas/com/screenassistant/core/data/local/AppDatabase/{2,3}.json` |
| `exportSchema = true` | core:data (AppDatabase.kt) | **ÚNICA línea de producción tocada** (1 palabra; cero impacto runtime — solo emite JSON en build) |
| tarea raíz `gate` | root | agrega tests + assemble + enforcement (§6.3) |

---

## 4. M6 — Tests de migración Room (JVM + Robolectric)

### 4.1 Por qué Robolectric y no androidTest para M6
- `MigrationTestHelper` exige un `Instrumentation` y un Context reales → o instrumentación o Robolectric.
- Instrumentación = `connectedDebugAndroidTest`: NO corre en el gate local (sin dispositivo) → los tests de migración quedarían fuera de toda verificación automatizada (peso muerto, riesgo real: la migración 2→3 solo se validaría a mano).
- Robolectric = `testDebugUnitTest` → corre en el gate JVM con SQLite REAL (Robolectric usa SQLite nativo, no mock). La integridad de la migración se verifica en cada cierre de lote.
- Coste: ~150 MB de descarga única de android-all + la excepción de regla ya autorizada. Decisión: **M6 → JVM/Robolectric**; la instrumentación queda solo para M13 (UI), que genuinamente la necesita.

### 4.2 El esquema v2 (¿qué hay que producir?)
- v2 = v3 **menos** la tabla `alarms` (memories + pending_messages idénticas). Es el estado "histórico" del que parte la migración.
- `MigrationTestHelper` valida la identidad completa (nombres de tabla, tipos, PK, FK, índices) contra el JSON esperado → el test VERIFICA la afirmación del KDoc de `MIGRATION_2_3` ("el SQL replica EXACTAMENTE el CREATE TABLE que Room generaría") de forma objetiva.

### 4.3 Habilitar esquemas (paso bloqueante)
1. `AppDatabase.kt`: `exportSchema = true` (1 palabra).
2. `core:data/build.gradle.kts`: ksp arg `room.schemaLocation = "$projectDir/schemas"`.
3. `core:data/build.gradle.kts`: `sourceSets["test"].assets.srcDir("$projectDir/schemas")` — **P1 BLOQUEANTE (QA shift-left)**: `isIncludeAndroidResources=true` NO empaqueta `schemas/` (es salida de KSP, no un source set de `main`/`test`); `MigrationTestHelper` lee `context.assets.open("schemas/<canonicalName>/<version>.json")` y sin este cableado lanza FileNotFoundException en T1/T2/T3 (patrón oficial Room + Robolectric, verificado por QA).
4. La carpeta `core/data/schemas/` se **commitea** (no va al .gitignore).

### 4.4 Bootstrap de `2.json` (paso controlado del lote — el único "hack")
Room solo exporta el esquema de la versión ACTUAL. Para obtener `2.json`:
1. Edición TEMPORAL (no se commitea): `AppDatabase.kt` → `entities = [MemoryEntity, PendingMessageEntity]`, `version = 2`.
2. `gradlew :core:data:kspDebugKotlin --rerun-tasks` → genera `schemas/.../AppDatabase/2.json`.
3. **Revertir** a `version = 3` + las 3 entidades (estado final de producción).
4. `gradlew :core:data:kspDebugKotlin --rerun-tasks` → genera `3.json`.
5. **Control de calidad del bootstrap** (obligatorio, P2-4 QA):
   - `2.json` — control **NEGATIVO**: NO debe contener `"alarms"`; control **POSITIVO**: DEBE contener `"memories"` Y `"pending_messages"` (detecta esquema vacío/truncado).
   - `3.json` — DEBE contener `"alarms"` (además de `memories`/`pending_messages`).
   - Si falla, repetir con limpieza (`gradlew :core:data:clean`).
6. Commitear ambos JSON.

### 4.5 Tests a crear (en `core:data/src/test`)

**`AppDatabaseMigrationTest.kt`** (`@RunWith(RobolectricTestRunner::class)` — NO AndroidJUnit4, verificación QA; `@Config(sdk = [34])`) — ~3 tests:
- Regla: `MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)` (patrón Room + Robolectric documentado; androidx.test:core declara la instrumentación).
- T1 **validación de esquema**: `runMigrationsAndValidate("mig-test", 3, true, MIGRATION_2_3)` → identidad completa contra `3.json`.
- T2 **preservación de datos**: `createDatabase("mig-test", 2)` + INSERTs raw en `memories` y `pending_messages` (2 filas c/u con timestamps distintos) → `runMigrationsAndValidate(...)` → abrir la DB con Room (openHelper/inMemory NO — abrir la misma) y verificar vía DAO que las 4 filas sobreviven con sus valores (PK, contenido, orden por timestamp).
- T3 **tabla alarms operativa post-migración**: tras T2 (o T1), insertar una `AlarmEntity` vía `alarmDao()` y leerla — la tabla creada por la migración es funcional contra el DAO real (no solo "existe").

**`MessageDaoTest.kt`** (`@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [34])`) — ~5 tests con `Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()`:
- **SIN `allowMainThreadQueries()`** (verificación QA): los tests corren en `runTest`/`first()` sobre hilos reales de coroutines y NO relajan el chequeo de hilo de Room — se valida la disciplina de threading del DAO tal como está en producción.
- insert + primera emisión del Flow (`getAllPendingMessages`, con `runTest`/`first()`).
- insert de 2 con timestamps desordenados → emisión ordenada ASC por timestamp.
- `deleteMessage(id)` → desaparece de la emisión; id inexistente → no-op sin excepción.
- `clearAll()` → lista vacía.
- round-trip de campos nullable (`contactNumber = null` → null al leer).

**Delta M6: +8 tests JVM** → gate estimado **803** (795 + 8). Alcance NO cubierto (documentado): `fallbackToDestructiveMigration` (builder de :app), 1→2 (no existe).

---

## 5. M13 — Gate mecanizado

### 5.1 JaCoCo — mecanismo
- **Por módulo** (no global): cada módulo con tests recibe `jacoco` + report + verification propios.
  - `core:domain` (plugin kotlin-jvm): tareas nativas `jacocoTestReport` / `jacocoTestCoverageVerification` (creadas por el plugin `jacoco`).
  - Módulos Android (app, core:data, feature:overlay, service:system): tareas CUSTOM con el recetario canónico:
    - `executionData` = `testDebugUnitTest` (AGP inyecta el agente al habilitar `enableUnitTestCoverage`).
    - `classDirectories` = `build/tmp/kotlin-classes/debug` **con exclusiones** (crítico): `**/BuildConfig*`, `**/R.class`, `**/Hilt_*.class`, `**/*_Factory*.class`, `**/*_HiltModules*`, `**/*_GeneratedInjector.class` (P2-2 QA: Hilt KSP los genera en módulos con @AndroidEntryPoint), generados de KSP/Room (`*_Impl.class`). Sin esto, Hilt/KSP generan decenas de clases que hunden el umbral artificialmente.
    - `sourceDirectories` = `src/main/java` + `src/main/kotlin`.
    - Report: HTML + XML (XML por si llega CI).
- Enforcement: `JacocoCoverageVerification` con `violationRules { rule { limit { counter = "INSTRUCTION"; minimum = <umbral> } } }` + `haltOnFailure = true` (default). Mismo filtro de clases que el report.
- **Orden de dependencias (P2-3 QA — footgun)**: las tareas custom de los módulos Android DEBEN declarar `dependsOn("testDebugUnitTest")` (report y verification) para leer el `.exec` FRESCO; sin eso, Gradle puede ejecutar la verificación contra un `.exec` ausente/obsoleto → coverage 0 → gate rojo intermitente. En `core:domain` es automático (la tarea nativa `jacocoTestCoverageVerification` depende de `test`).
- Nombre uniforme de tareas en los 5 módulos: `jacocoTestReport` / `jacocoTestCoverageVerification` (en core:domain son las nativas; en Android, custom) → el gate raíz puede depender de ellas sin distinción.

### 5.2 Umbrales — política de calibración (NO fijar a ciegas)
No existe ningún reporte previo: fijar umbrales sin medir es romper el gate. El lote se ejecuta en 2 fases:
- **Fase A (medir)**: JaCoCo con report SOLO (verification sin reglas). Correr con el **estado FINAL de Fase 1** (core:data con los +8 tests de M6) y el **MISMO comando canónico de Fase B** (`gradlew gate --rerun-tasks`) — verificación QA-3: la medición debe reflejar el estado que se va a exigir. **Ningún test nuevo entre A y B** (si entra uno, re-medir antes de fijar). Leer los 5 reportes HTML y registrar números en el BACKLOG.
- **Fase B (fijar)**: umbral = medición − margen ≥ 0.05, con suelo sugerido por módulo:

| Módulo | Tests | Estimación inicial (a confirmar en Fase A) | Umbral sugerido |
|---|---|---|---|
| core:domain | 456 | alta (0.85–0.93) — JVM puro, disciplina fuerte | **0.80** |
| service:system | 232 | media-alta (0.70–0.80) — parser/acciones muy testeados | **0.60** |
| feature:overlay | 38 | media (0.50–0.65) — VMs testeados, UI sin test | **0.45–0.50** |
| core:data | 49 + 8 (M6) | media-baja (0.40–0.55) — stores/remote; DAO nuevo | **0.40–0.45** |
| app | 20 | baja (0.15–0.30) — solo 2 VMs; MainActivity/AppModule/MainScreen sin test JVM | **SIN enforcement** (deuda documentada) — tarea report-only sin violationRules y **EXCLUIDA del gate** (P2-1 QA: decisión tomada = excluir, más limpio que una "verificación vacía") |

- **`:app` SIN enforcement (P2-1)**: su cobertura JVM es estructuralmente baja (composables/Activity/DI no se testean en JVM) y su cobertura real de UI vendrá de androidTest (que JaCoCo no mide sin dispositivo). Exigirle umbral JVM obligaría a Robolectric de UI en :app — fuera de alcance del lote. Por tanto `:app:jacocoTestCoverageVerification` se configura **report-only (sin violationRules)** y **NO entra en la tarea `gate`** (decisión P2-1: excluir, no "verificación vacía" que daría falso verde). Deuda registrada en el BACKLOG.
- **Regla de no-ruptura**: si en Fase A algún módulo mide por debajo de su suelo, su enforcement se activa en un lote posterior con meta de subida; el gate mecanizado del Lote 12 DEBE quedar verde. El enforcement es un freno de mejora, no un cuchillo de cierre.

### 5.3 androidTest mínimo (UI Compose, `:app`)
**Decisiones**:
- **Sin Hilt en los tests**: se testean los composables puros (ApiKeySection, MainScreen) pasando estados y callbacks falsos. `MainActivity` (con `hiltViewModel`) queda fuera — testearla exigiría HiltTestApplication + grafo DI completo + Room real + prefs cifradas → frágil y lento.
- **Sin espresso**: los matchers de Compose (`onNodeWithText`, `performClick`, `performTextInput`) cubren todo lo que se testea.
- **Tema (verificación QA-4)**: cada composable se envuelve en `ScreenAssistantTheme` dentro del `setContent` del test — `stringResource` y colores de `MaterialTheme` lo requieren; en producción el tema lo provee MainActivity, el test debe replicarlo.
- **Rules (verificación QA-5)**: `createComposeRule` para `ApiKeySectionTest` (sin activity; `ui-test-manifest` ya registra `ComponentActivity` en debug); `createAndroidComposeRule<ComponentActivity>` solo para `MainScreenTest` (usa `LocalContext`/`LifecycleResumeEffect`).

**Tests** (2 archivos, ~8 tests):
- `ApiKeySectionTest` (createComposeRule, sin activity) — 5 tests:
  1. estado no configurado → badge "no configurada" y botón save deshabilitado con input vacío.
  2. `performTextInput` → callback `onInputChange` con el texto.
  3. input no vacío → save habilitado; click → `onSaveKey(texto)`.
  4. `isConfigured` → badge con masked key + botón "Quitar" visible.
  5. flujo del diálogo: click "Quitar" → diálogo → confirmar → `onClearKey`; cancelar → no callback.
- `MainScreenTest` (createAndroidComposeRule<ComponentActivity>) — 3 tests:
  1. render: título, sección API key, sección Puente, botones Start/Stop presentes.
  2. click "Iniciar asistente" → `onStartService` invocado.
  3. click "Detener asistente" → `onStopService` invocado.
  - **Restricción**: NO assertar sobre el badge de accesibilidad (lee `Settings.Secure` del dispositivo real, estado no determinista).

**Deps** (de nuevo: ya existen compose-bom, ui-test-junit4, ui-test-manifest): las 3 de §3.1. `testInstrumentationRunner` ya apunta a AndroidJUnitRunner. **0 cambios de manifest** (ui-test-manifest registra ComponentActivity en debug).

### 5.4 Integración con el gate
- **Nueva tarea raíz `gate`** (registrada en root build.gradle.kts):
  `dependsOn(:app:testDebugUnitTest, :core:data:testDebugUnitTest, :feature:overlay:testDebugUnitTest, :service:system:testDebugUnitTest, :core:domain:test, :app:assembleDebug, :core:data:jacocoTestCoverageVerification, :core:domain:jacocoTestCoverageVerification, :feature:overlay:jacocoTestCoverageVerification, :service:system:jacocoTestCoverageVerification)`
- **`:app` fuera del enforcement (P2-1 QA)**: `:app:jacocoTestCoverageVerification` NO está en el `dependsOn` — se configura report-only sin violationRules y se excluye del gate (resuelve la contradicción con §5.2; más limpio que una verificación vacía). Su report queda disponible bajo demanda: `gradlew :app:jacocoTestReport`.
- **Comando mecanizado**: `gradlew gate --rerun-tasks` → sustituye el comando manual de "Notas de proceso". El conteo de tests pasa a ser: XML de resultados + cobertura JaCoCo verificada por tarea (ya no "a mano").
- **`gradlew :app:connectedDebugAndroidTest`** (androidTest): tarea documentada y SEPARADA — corre solo cuando hay dispositivo/emulador. NO se añade a `gate` (rompería la iteración local). El informe del lote incluye su guion de ejecución.
- Documentación a actualizar: BACKLOG (cierre M6/M13), "Notas de proceso" (comando nuevo), este diseño.

---

## 6. Riesgos (con mitigación)

| # | Riesgo | Impacto | Mitigación |
|---|---|---|---|
| R1 | Robolectric primera ejecución descarga android-all (~150 MB) | Lote más lento una vez; red necesaria | Documentar en Notas de proceso; es una sola vez |
| R2 | `@Config(sdk = [34])` vs compileSdk 35 / targetSdk 35 | Migraciones corren en API 34 en vez de 35 | Robolectric 4.14 soporta 35, pero 34 es la referencia estable de Room (sus propios tests usan 33/34); SQLite nativo de Robolectric es idéntico para DDL básico. Subir a 35 solo si un test futuro lo exige |
| R3 | `exportSchema=false` hoy → el lote DEBE añadir `exportSchema=true` + schemaLocation | Si se omite: MigrationTestHelper lanza FileNotFoundException en assets | Cambio explícito en el alcance del lote (§4.3); cero impacto runtime (solo genera JSON) |
| R4 | Bootstrap de `2.json` con entities/version temporal | JSON incorrecto → test verde falso | Controles NEGATIVO ("alarms" ausente) + POSITIVO ("memories"/"pending_messages" presentes) en §4.4.5 (P2-4) + revisión QA del diff |
| R5 | **P1**: MigrationTestHelper sin el cableado de assets (`sourceSets["test"].assets.srcDir("$projectDir/schemas")`) | FileNotFoundException en `schemas/...` (T1/T2/T3 rojos) | `isIncludeAndroidResources=true` NO basta (solo empaqueta source sets main/test); el srcDir de `schemas/` es BLOQUEANTE (§3.2, §4.3) — verificado en Fase 1 antes de continuar |
| R6 | JaCoCo + Kotlin 2.0 / Gradle 9.3 | Reportes con clases generadas o toolVersion no soportado | toolVersion 0.8.13 explícito (compatible Gradle 9, soporte Kotlin inline functions); exclusiones de generados (§5.1). Si Gradle 9.3 rechazara 0.8.13 (no esperado), usar el default embebido del plugin |
| R7 | Robolectric + InstrumentationRegistry | `NoClassDefFoundError` si falta androidx.test:core | Declarada explícitamente (§3.1) |
| R8 | Umbrales sin calibrar | Gate roto en el cierre del lote | Fases A/B obligatorias (§5.2); umbral = medición − 0.05; `:app` sin enforcement; regla de no-ruptura |
| R9 | androidTest frágil (badge de accesibilidad, animaciones) | Flakes en dispositivo | Asserts solo sobre elementos estables (§5.3); los tests de Compose usan IDs de texto de strings.xml |
| R10 | `connectedDebugAndroidTest` en Windows sin emulador | No ejecutable localmente | No está en `gate`; guion documentado; CI futuro (emulador) fuera de alcance |
| R11 | JaCoCo cuenta clases de Room/Hilt generadas si el filtro falla | Umbral irrealmente bajo → enforcement inútil | Filtro verificado en Fase A: comparar % con y sin exclusiones en el report HTML |
| R12 | **P2-3**: tareas JaCoCo custom sin `dependsOn("testDebugUnitTest")` interno | `.exec` ausente/obsoleto → coverage 0 → gate rojo intermitente | Declaración explícita del dependsOn en report y verification (§5.1); en core:domain es automático |

---

## 7. Orden de ejecución y delta

| Fase | Contenido | Verificación |
|---|---|---|
| 0 | Catalog + deps (§3.1) + toml | `gradlew help` OK |
| 1 | M6: exportSchema + schemaLocation + **srcDir de assets de test (P1)** + bootstrap 2.json/3.json (controles §4.4.5) + MigrationTest (3) + MessageDaoTest (5) | `gradlew :core:data:testDebugUnitTest` verde (49+8=57 en core:data); **verificación P1: T1/T2/T3 resuelven los assets de esquema sin FileNotFoundException** |
| 2 | M13-a: JaCoCo en 5 módulos (exclusiones §5.1 + dependsOn P2-3), report SOLO, correr **`gradlew gate --rerun-tasks` con el estado FINAL de Fase 1** (mismo comando que Fase B; **cero tests nuevos entre A y B** — QA-3) | 5 reportes HTML leídos, números al BACKLOG |
| 3 | M13-b: enforcement con umbrales calibrados (5 módulos; **`:app` excluida — P2-1**) + tarea `gate` raíz | `gradlew gate --rerun-tasks` VERDE con enforcement |
| 4 | M13-c: androidTest deps + ApiKeySectionTest (5) + MainScreenTest (3) | `:app:assembleDebug` compila; `connectedDebugAndroidTest` en dispositivo (manual, documentado) |
| 5 | Docs: BACKLOG (M6/M13 cerrados), Notas de proceso, este diseño | Revisión final |

**Delta de tests**:
- Gate JVM: **+8** (M6) → **803 tests / 0 fallos** (objetivo).
- androidTest: **+8** (M13, solo dispositivo) → no cuentan en el gate JVM.
- Umbrales JaCoCo: según Fase A (sugeridos en §5.2).

**Objetivo de cierre**: `gradlew gate --rerun-tasks` → BUILD SUCCESSFUL con enforcement activo (JVM completo, sin dispositivo); `connectedDebugAndroidTest` documentado con guion.

---

## 8. Contratos clave (esqueletos de referencia para el Desarrollador)

```kotlin
// core:data/src/test/.../local/AppDatabaseMigrationTest.kt
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java
    )
    // T1: helper.runMigrationsAndValidate("mig-test", 3, true, AppDatabase.MIGRATION_2_3)
    // T2: helper.createDatabase("mig-test", 2) + INSERT raw en memories/pending_messages
    //     → runMigrationsAndValidate → abrir Room (misma DB) → asserts vía DAOs
    // T3: insertar AlarmEntity vía alarmDao() post-migración → leerla
}

// core:data/src/test/.../local/MessageDaoTest.kt
@RunWith(RobolectricTestRunner::class)   // NO AndroidJUnit4 (verificación QA-1)
@Config(sdk = [34])
class MessageDaoTest {
    // db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
    //     .build()   // SIN allowMainThreadQueries() (verificación QA-2)
    // runTest + first() para el Flow; CRUD completo de pending_messages
}

// app/src/androidTest/.../ui/apikey/ApiKeySectionTest.kt
@RunWith(AndroidJUnit4::class)
class ApiKeySectionTest {
    @get:Rule val compose = createComposeRule()   // sin activity (verificación QA-5)
    // setContent { ScreenAssistantTheme { ApiKeySection(uiState = fake, ...) } }   // QA-4: tema
    // UiState fake; onNodeWithText/performTextInput/performClick; callbacks capturados en vars
}

// app/src/androidTest/.../MainScreenTest.kt
@RunWith(AndroidJUnit4::class)
class MainScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    // setContent { ScreenAssistantTheme { MainScreen(uiState/fakes + callbacks capturados) } }   // QA-4: tema
    // assert solo elementos estables; onStartService/onStopService capturados
}
```

---

### 8.1 Checklist de cierre — exigencias QA shift-left (P1/P2)

| # | Exigencia | Verificación en el cierre del lote |
|---|---|---|
| **P1** | Cableado de assets de esquema: `sourceSets["test"].assets.srcDir("$projectDir/schemas")` en core:data | T1/T2/T3 resuelven los JSON (sin FileNotFoundException) |
| **P2-1** | `:app` fuera del enforcement: `:app:jacocoTestCoverageVerification` report-only (sin violationRules) y EXCLUIDA de `gate` | `gradlew gate --rerun-tasks` verde sin depender de :app; `gradlew :app:jacocoTestReport` funciona bajo demanda |
| **P2-2** | Exclusión `**/*_GeneratedInjector.class` en report y verification de módulos Android | Report HTML sin clases Hilt/KSP generadas (comparar % con/sin filtro en Fase A) |
| **P2-3** | `dependsOn("testDebugUnitTest")` interno en tareas custom (report + verification) | `gradlew :core:data:jacocoTestCoverageVerification` verde con `.exec` fresco; sin rojos intermitentes |
| **P2-4** | Bootstrap: control NEGATIVO ("alarms" ausente en 2.json) + POSITIVO ("memories" y "pending_messages" presentes en 2.json) | Grep verificado en el diff del commit |
| QA-1 | `@RunWith(RobolectricTestRunner::class)` (NO AndroidJUnit4) y `@Config(sdk = [34])` en los tests de core:data | Los 8 tests JVM nuevos corren en `testDebugUnitTest` |
| QA-2 | `inMemoryDatabaseBuilder` SIN `allowMainThreadQueries()` | MessageDaoTest usa `runTest`/`first()` sin relajar el chequeo de hilo de Room |
| QA-3 | Fase A medida con el estado FINAL de Fase 1 (+8 tests) y el comando canónico `gate --rerun-tasks`; cero tests nuevos entre A y B | Números del BACKLOG = estado exigido en Fase B |
| QA-4 | Composables de androidTest envueltos en `ScreenAssistantTheme` | ApiKeySectionTest/MainScreenTest compilan y pasan en dispositivo |
| QA-5 | `createComposeRule` para ApiKeySectionTest (sin activity) | No requiere activity propia (ui-test-manifest registra ComponentActivity) |

---

### 8.2 Acta de cierre del Lote 12 (04/08/2026) — checklist §8.1 cumplimentado

Verificación realizada por el Desarrollador al cierre del lote, punto por punto (criterio de aceptación):

| # | Exigencia | Resultado en el cierre |
|---|---|---|
| **P1** | Cableado de assets de esquema en core:data | ✅ `sourceSets { getByName("test").assets.srcDir("$projectDir/schemas") }` presente; T1/T2/T3 corren en `testDebugUnitTest` resolviendo los JSON sin FileNotFoundException (57/57 en core:data, XML verificado) |
| **P2-1** | `:app` fuera del enforcement | ✅ `:app:jacocoTestCoverageVerification` sin violationRules (report-only), EXCLUIDA del dependsOn de `gate`; `gradlew gate --rerun-tasks` verde sin depender de :app; `gradlew :app:jacocoTestReport` y `:app:jacocoTestCoverageVerification` funcionan bajo demanda (BUILD SUCCESSFUL, ejecutados) |
| **P2-2** | Exclusión `**/*_GeneratedInjector.class` (y demás generados) | ✅ Filtro completo en report y verification de los 4 módulos Android + core:domain; verificado por grep: 0 clases `*_Impl/_Factory/_HiltModules/_HiltComponents/_GeneratedInjector/Dagger/hilt_aggregated_deps/Hilt_` en los 5 XML de JaCoCo |
| **P2-3** | `dependsOn("testDebugUnitTest")` interno | ✅ Declarado en report y verification de los 4 módulos Android (core:domain usa las tareas nativas, que ya dependen de `test`); dos ejecuciones consecutivas de `gate --rerun-tasks` verdes, sin rojos intermitentes ni .exec obsoleto |
| **P2-4** | Bootstrap: controles del `2.json` | ✅ Control NEGATIVO: `"alarms"` con 0 ocurrencias en 2.json; control POSITIVO: `"memories"` y `"pending_messages"` presentes (1 ocurrencia c/u); 3.json contiene `"alarms"`; versiones 2 y 3 con identityHash propios |
| QA-1 | `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [34])` | ✅ Los 8 tests nuevos (AppDatabaseMigrationTest 3 + MessageDaoTest 5) usan RobolectricTestRunner directo (sin AndroidJUnit4) y corren en `testDebugUnitTest` (57/57) |
| QA-2 | `inMemoryDatabaseBuilder` SIN `allowMainThreadQueries()` | ✅ MessageDaoTest usa `Room.inMemoryDatabaseBuilder(...).build()` sin relajar el chequeo de hilo; `runTest`/`first()` sobre los executors reales de Room; SQLite real (Robolectric) |
| QA-3 | Fase A con estado final Fase 1 + comando canónico; cero tests entre A y B | ✅ Fase A ejecutada con el MISMO comando (`gate --rerun-tasks`) tras Fase 1 completa (+8 tests ya dentro); entre A y B solo cambiaron los umbrales (cero tests nuevos); mediciones en BACKLOG: domain 91.02%, data 62.63%, overlay 37.22%, system 65.07%, app 11.07% |
| QA-4 | Composables envueltos en `ScreenAssistantTheme` | ✅ `ApiKeySectionTest` y `MainScreenTest` envuelven todo `setContent` en `ScreenAssistantTheme`; `compileDebugAndroidTestKotlin` BUILD SUCCESSFUL (ejecución en dispositivo pendiente — sin emulador en el entorno) |
| QA-5 | `createComposeRule` para ApiKeySectionTest | ✅ ApiKeySectionTest usa `createComposeRule()` (sin activity propia; ui-test-manifest ya registra ComponentActivity en debug); MainScreenTest usa `createAndroidComposeRule<ComponentActivity>()` (LocalContext/LifecycleResumeEffect) |

**Gate final**: `gradlew gate --rerun-tasks` → **BUILD SUCCESSFUL con enforcement activo** (4m41s); comando canónico JVM `gradlew testDebugUnitTest :core:domain:test :app:assembleDebug --rerun-tasks` → **BUILD SUCCESSFUL, 803 tests / 0 fallos** (app 20, core:data 57, core:domain 456, feature:overlay 38, service:system 232). `:app:connectedDebugAndroidTest` pendiente de dispositivo (guion: `gradlew :app:connectedDebugAndroidTest` con emulador/dispositivo; los +8 androidTest ya compilan).

---

## 9. Fuera de alcance (documentado en el BACKLOG)

- `fallbackToDestructiveMigration` (builder de :app) — requeriría test del builder en :app con Robolectric; red de seguridad aceptada, no testeada.
- Migración 1→2 — no existe en el repo.
- Umbral JaCoCo para `:app` — deuda registrada (su cobertura real llega vía androidTest).
- CI con emulador (`connected` automatizado + `enableAndroidTestCoverage`) — infra futura, sin pipeline hoy.
- Bump de Room/Robolectric/JaCoCo a versiones más nuevas — política de pinning conservador.
