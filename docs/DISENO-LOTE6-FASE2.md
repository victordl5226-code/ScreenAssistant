# Lote 6 — Fase 2 del puente Tasker: transporte (entrada broadcast + salida TTS/broadcast)

Documento de diseño del Arquitecto — 02/08/2026. Estado: **v1.1 CERRADO — APROBADO CON CAMBIOS**
(revisión QA shift-left aplicada: H1–H11; sin cambios estructurales, solo documentales/decisión).
El Desarrollador implementa por P0–P4 tras esta aprobación; P5 (gate + validación manual) se hace
tras el gate de compilación. Puntos de testabilidad marcados **QA #n** (QA #5 ACEPTADO, ver H4).

**Historial de revisión QA (v1.0 → v1.1):**
- **H1 (bloqueante documental)**: el smoke `adb shell am broadcast` no entregaba en Android 8+ (broadcast implícito) → corregido con paquete posicional + fallback de componente explícito (secciones 2 y 9, ADR T1).
- **H2**: afirmación de seguridad falsa — `ACTION_CALL` marca DIRECTO sin confirmación del sistema (CallAction.kt:18,34) → riesgo máximo reescrito (sección 6, ADR T5).
- **H3**: invariante "el emisor nunca espera sin respuesta" acotado al handler hacia arriba; extra ausente → silencio deliberado (secciones 3 y 9, ADR T6).
- **H4**: QA #5 cerrado formalmente como ACEPTADO (sección 6).
- **H5 (aceptado)**: test P0 "8192 no se trunca" (passthrough íntegro al handler) (sección 9).
- **H6 (aceptado)**: `error_timeout` con id eco best-effort del wire (secciones 7 y 9, ADR T6).
- **H7**: riesgo TTS corregido — dos motores propios, no QUEUE_FLUSH pisa (sección 5, ADR T4).
- **H8**: "sin android.util" pasa de test meta a verificación de code review (sección 9).
- **H9 (aceptado)**: try/catch defensivo en la cáscara del receiver → `fallo_ejecucion` (sección 7, ADR T6).
- **H10**: conteo corregido — 8 archivos de producción nuevos + 4 archivos editados; ~38 tests (sección 9).
- **H11**: guion manual ampliado — caso negativo (sin extras → silencio), latencia de Tasker en cached state y quoting JSON en PowerShell (sección 9).

---

## 0. Contexto verificado en el código (Fase 1 cerrada)

- `core/domain/.../bridge/CommandBridge.kt`: seam `suspend fun handle(input: String): String`.
- `service/system/.../bridge/SystemCommandBridgeImpl.kt`: `@Singleton` Hilt, decode→clasifica→ejecuta→responde
  SIEMPRE con el esquema fijo de 6 claves `{version,id,estado,resultado,error,mensaje}` (ADR-013 H4).
- `SystemCommandJsonCodec.encodeResult` (core/domain): emisor único de respuestas; `explicitNulls=true`.
- `AlarmReceiver` (service/system, registrado en `app/src/main/AndroidManifest.xml`): patrón canónico
  receiver = `@AndroidEntryPoint` + `goAsync()` + `withTimeoutOrNull(10s)` + `finish()` en finally.
- `service/system` es **JVM puro testeable** (JUnit 4 + MockK + coroutines-test; `TaskerBridgeTest` ya
  testea el puente completo sin Robolectric). `core/domain` es JVM puro sin Android.
- TTS: `feature/overlay/TextToSpeechManager` implementa la interfaz `core/domain/service/TextToSpeech`;
  **hoy NO tiene binding Hilt** (se crea en `AssistantOverlayUI`). `service/system` ya depende de `feature:overlay`.
- Manifest de componentes: todos los receivers/services viven en `app/src/main/AndroidManifest.xml`
  (el de `service/system` está vacío). `namespace = com.screenassistant`, minSdk 26, targetSdk 35
  (→ `android:exported` OBLIGATORIO en receivers con intent-filter).
- `kotlinx-serialization-json:1.7.3` ya es `implementation` en `service/system` (verificado por QA en Fase 1).
- ADR-013 NO-ALCANCE: la Fase 2 (transporte) quedó documentada como fuera de alcance → este Lote 6 la cierra.

---

## 1. Diagrama de capas (objetivo)

```
[Emisor]  Tasker (Send Intent)  ·  AutoRemote (opcional, mismo canal)
   │  broadcast: action "com.screenassistant.TASKER_COMMAND", extra "message" (AutoRemote) / "cmd" (Tasker)
   ▼
┌─ service:system (transporte) ─────────────────────────────────────────────┐
│ TaskerCommandReceiver (Android, cáscara ~15 líneas, Hilt, goAsync)        │
│   │ getStringExtra × 2                                                    │
│   ▼                                                                       │
│ TaskerPayloadExtractor (PURA JVM) ──► PayloadTasker(texto?, silencioso)   │
│   │ texto == null → SILENCIO (return, patrón AlarmReceiver)               │
│   ▼                                                                       │
│ TaskerMessageHandler (PURA) ──► withTimeoutOrNull(10s) ──► CommandBridge  │
│   │  (CommandBridge está en core:domain — el seam de Fase 1)              │
│   ▼  SIEMPRE JSON de 6 claves (incl. "error_timeout" si se agota)         │
│ TaskerResponseEmitter (Android fino) ──► TaskerRespuestaTexto (PURA)      │
│   ├─► broadcast "com.screenassistant.TASKER_RESPONSE" {respuesta, id}     │
│   └─► TTS (TextToSpeech domain) — suprimido si contexto:"silencioso"      │
└────────────────────────────────────────────────────────────────────────────┘
   ▲
   │ depende hacia adentro: core:domain NO conoce este transporte
core:domain: CommandBridge · SystemCommandJsonCodec · SystemAction · TextToSpeech(interfaz)
```

Regla de arquitectura: el transporte (extracción de extras, broadcast, TTS) es **infraestructura de
service:system**; `core/domain` conserva solo el protocolo (`CommandBridge`) — coherencia con ADR-003
(core:domain no se filtra hacia abajo) y ADR-013 (el wire es vocabulario del protocolo; los extras
`message`/`cmd` son vocabulario del transporte).

---

## 2. D1 — Mecanismo de entrada: (b) broadcast nativo de Tasker, con (c) AutoRemote como wrapper opcional de coste ~0

| Criterio | (a) AutoRemote | (b) Send Intent nativo | (c) Ambos |
|---|---|---|---|
| App extra instalada | Sí (AutoRemote + plugin/escritorio) | No | Solo si se usa |
| Restricciones Android 8+ | Resueltas (usa targetPackage) | Resuelto con campo **Package** de Send Intent | Igual que (b) |
| Dependencia Gradle/SDK | SDK opcional | Ninguna | Ninguna |
| Respuesta | URL callback (requiere key del dispositivo) | Broadcast de vuelta (Event Broadcast Received) | Broadcast + TTS |
| Testeable sin dispositivo | No | Sí (adb am broadcast) | Sí |
| Complejidad del guion de video | Alta (key, red, escritorio) | Baja (1 acción Send Intent) | Baja |

**DECISIÓN: (b) como canal primario y único declarado; (c) como compatibilidad pasiva de coste ~0.**

Detalles que cierran la decisión:

1. **Ecosistema real del usuario**: un solo dispositivo Android con Tasker instalado (o no). AutoRemote
   añade una app extra, una app de escritorio y una **key por dispositivo** solo para la respuesta;
   el broadcast nativo de Tasker funciona en cualquier dispositivo Android con Tasker, sin permisos raros.
2. **Android 8+ (minSdk 26)**: un broadcast 100% implícito NO llega a un receiver estático. La solución
   es que el emisor dirija el intent: Tasker "Send Intent" tiene el campo **Package** (broadcast
   package-specific, sí entregado) y el campo **Class** (explícito total). El guion manual del Lote 6
   rellena `Package = com.screenassistant`. AutoRemote re-emite con targetPackage → también llega.
   **Sin dependencia del SDK de AutoRemote**: para RECIBIR su broadcast solo hace falta el intent-filter
   (no se integra su SDK; el SDK solo haría falta si quisiéramos su UI de configuración embebida — no es el caso).
3. **Compatibilidad (c) a coste ~0**: AutoRemote entrega el payload en el extra **"message"**; el
   receiver acepta `message` con fallback `cmd` (ver D3). El usuario configura en AutoRemote la
   action de re-emisión a `com.screenassistant.TASKER_COMMAND` y target package `com.screenassistant`.
   No hay nada más que hacer: mismo receiver, mismo filtro, misma respuesta. Si AutoRemote no está
   instalada, el coste es cero (un `getStringExtra` más).
4. La action única `com.screenassistant.TASKER_COMMAND` es NOMBRE NUESTRO (como `com.screenassistant.ALARM_FIRED`),
   no depende de convenciones de AutoRemote (`com.bighugegiraffe.*` se queda solo como nota documental).

**QA #1** (v1.1, H1): testabilidad — el canal (b) es reproducible sin Tasker por adb, pero en
Android 8+ (minSdk 26) el broadcast debe ser **package-specific** (los implícitos no llegan a
receivers de manifest). Smoke QA correcto:
`adb shell am broadcast -a com.screenassistant.TASKER_COMMAND --es cmd '<json>' com.screenassistant`
(el paquete como argumento POSICIONAL final = package-specific → sí entregado).
Fallback con componente explícito (idéntico resultado):
`adb shell am broadcast -n com.screenassistant/.service.system.bridge.TaskerCommandReceiver --es cmd '<json>'`

---

## 3. D2 — Receiver y ciclo de vida

**Ubicación**: `service/system/.../bridge/TaskerCommandReceiver.kt` (mismo módulo que el bridge y las
acciones; patrón exacto de `AlarmReceiver`). **Registro**: en `app/src/main/AndroidManifest.xml`:

```xml
<receiver
    android:name=".service.system.bridge.TaskerCommandReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="com.screenassistant.TASKER_COMMAND" />
    </intent-filter>
</receiver>
```

- `exported="true"`: obligatorio (targetSdk 35) y necesario (el emisor es otra app). Ver D5 para seguridad.
- `directBootAware`: **NO** (por defecto). El puente necesita usuario desbloqueado: Room (`filesDir`),
  TTS, permisos de acciones. No aplica device-protected. Documentado, no silencioso.
- **goAsync() + `withTimeoutOrNull(10_000)` + `finish()` en finally**: idéntico a `AlarmReceiver`
  (patrón probado). **NO START_FOREGROUND_SERVICE en esta fase**:
  - Las 22 acciones actuales son cortas (Room upsert < 100 ms; la más lenta, `buscar_archivo`, es un
    scan de FS — medir en dispositivo, estimación < 2 s). La ventana goAsync (~10 s) sobra.
  - Si una acción se cuelga, el timeout devuelve `error_timeout` **estructurado** (el puente SIEMPRE
    responde) y el proceso no se alarga (ANR). El emisor nunca espera sin respuesta.
    **ACOTACIÓN DEL INVARIANTE (v1.1, H3)**: "el emisor nunca espera sin respuesta" aplica desde el
    **handler hacia arriba** (todo comando extraído recibe su JSON de 6 claves). El **extra ausente
    → silencio** (decisión del receiver ANTES de invocar al handler, D3) es una **excepción
    deliberada** anti-spam (precedente: `AlarmReceiver` con `requestCode < 0`): un broadcast sin
    extras no es un comando, no invoca el contrato y no recibe respuesta.
  - **Criterio de migración documentado**: si una acción futura supera ~8 s medidos, se envuelve el
    MISMO handler en un FGS `specialUse` (el servicio overlay ya usa ese tipo; el seam no cambia,
    solo el contenedor). No se anticipa: YAGNI.

**QA #2**: el timeout no debe dejar el broadcast sin `finish()` → `finally` (invariante del patrón
AlarmReceiver, ya verificado en O4-P2).

---

## 4. D3 — Extracción del payload: pura, en service/system (no en core/domain)

La extracción tiene DOS partes: (1) leer los extras del `Intent` (Android, 2 líneas) y (2) decidir
precedencia/presencia/silencio (lógica). La lógica (2) vive en `TaskerPayloadExtractor`, **objeto JVM
puro sin `android.util`**, en `service/system/.../bridge/`:

- **Por qué no en core/domain**: el vocabulario `message`/`cmd` es del TRANSPORTE Android (extras de
  Intent), no del protocolo. `core/domain` ya tiene su seam (`CommandBridge`) y no debe filtrarse a
  vocabulario de transporte (precedente: ADR-003/N1). Además service/system ya demuestra que aloja
  clases puras testeables (`SystemCommandBridgeImpl`).
- **Contrato**: `fun extraer(message: String?, cmd: String?): PayloadTasker` con
  `data class PayloadTasker(texto: String?, silencioso: Boolean)`:
  - Precedencia: `message` (AutoRemote) gana a `cmd` (Tasker) — si `message` está presente aunque
    sea vacío, gana (un `message` vacío es un COMANDO vacío → el puente responde `json_invalido`, F0;
    al usuario le conviene ver el error estructurado antes que un silencio).
  - `texto == null` (ningún extra) → **SILENCIO**: el receiver hace `return` sin respuesta. Justificación:
    broadcasts de perfiles mal configurados o apps curiosas no merecen TTS ni broadcast de respuesta;
    patrón del proyecto (`AlarmReceiver` con `requestCode < 0`). El silencio lo decide el RECEIVER
    (no entra al handler): el handler recibe `extra: String` no-null.
  - **Límite 8192**: el transporte NO trunca ni pre-valida; el puente ya lo rechaza con
    `longitud_excedida` (fuente única de límites, ADR-013 S6/S7). Un extra de 2 MB llega al puente y
    este responde el error; no hay corte silencioso en el transporte.
  - `silencioso`: convención nueva del transporte — si el wire contiene `"contexto":"silencioso"`
    (parseo best-effort con try/catch, sin falsos positivos por subcadena), se suprime el TTS pero
    NO el broadcast de respuesta. `contexto` ya existe en el contrato (String ≤ 200) y estaba sin
    semántica: la Fase 2 le da UNA convención reservada. No toca core/domain.

**QA #3**: extractor 100% JVM → tests sin Robolectric (JUnit 4), tabla de precedencia exhaustiva.

---

## 5. D4 — Respuesta: mínimo viable (a) TTS + (b) broadcast; (c) URL callback FUERA

- **(a) TTS — canal humano**: el resultado se habla con la interfaz `core/domain/service/TextToSpeech`
  (el texto a hablar lo extrae `TaskerRespuestaTexto`: `resultado` si `estado==ok`, si no `error`).
  **Binding Hilt NUEVO en AppModule**: `@Provides @Singleton fun provideTextToSpeech(@ApplicationContext): TextToSpeech = TextToSpeechManager(context)`. Riesgo aceptado: coexistirán dos instancias
  TTS (overlay y puente); cada `TextToSpeechManager` crea su PROPIO motor → el riesgo real no es
  "pisar" una cola (no la comparten), sino **habla simultánea de dos motores** si ambos hablan a la
  vez — en la práctica el uso es secuencial (guion Tasker no corre con overlay hablando a la vez);
  reutilizar la instancia del overlay sería un refactor fuera de alcance (documentado).
- **(b) Broadcast de vuelta — canal máquina**: action fija `com.screenassistant.TASKER_RESPONSE`,
  extra `respuesta` = **el MISMO JSON de 6 claves** (contrato H4 intacto, sin envoltorio) y extra
  `id` = eco plano del `id` (para que Tasker filtre por variable sin parsear). Tasker la escucha con
  "Event → Broadcast Received" (registro dinámico → sí funciona en Android 8+).
- **(c) AutoRemote URL callback**: FUERA DE ALCANCE. Requiere la key del dispositivo (UI de Settings
  + almacenamiento), red y la app de escritorio; el mínimo viable cubre el guion del video. Extensión
  documentada para Fase 3, sin cambios de seam (el JSON de 6 claves es el mismo cuerpo de la llamada).
- El TTS se suprime si el wire mandó `contexto:"silencioso"` (D3); el broadcast de respuesta se emite
  SIEMPRE que hubo comando (incluso errores de decode — el guion lo necesita para ver el JSON de error).

**QA #4**: emitter testeable con MockK inyectando `TextToSpeech` (interfaz) y un Context mocked;
el parseo del JSON de salida es puro (`TaskerRespuestaTexto`, tests JVM).

---

## 6. D5 — Seguridad: exported + riesgo documentado + seam preparado; SIN permiso custom ni validación de origen en v1

- **Permiso custom (signature)**: inviable — el emisor (Tasker/AutoRemote) no está firmado con
  nuestra key; un permiso `normal` cualquiera lo declara → fricción sin seguridad real. Descartado.
- **Validación de origen por packageName**: descartada en v1 por dos motivos técnicos:
  1. Para broadcasts **no ordenados** no hay API fiable del emisor: `intent.getPackage()` devuelve el
     targetPackage si Tasker rellena el campo Package (¡sería `com.screenassistant`, no Tasker!);
     `getSentPackage()` solo existe en broadcasts ORDENADOS, y Tasker "Send Intent" no los usa.
  2. Bloquear por package rompe la universalidad (Tasker vs Tasker Beta vs MacroDroid...).
- **Mitigaciones reales que SÍ aplican**:
  1. **RIESGO MÁXIMO REAL (v1.1, H2 — corregido)**: el efecto máximo de un wire malicioso = ejecutar
     acciones que el usuario YA autorizó por permisos del sistema, y **`ACTION_CALL` marca llamadas
     DIRECTAS sin diálogo de confirmación** (CallAction.kt:18,34 — con `CALL_PHONE` concedido, una app
     maliciosa podría disparar llamadas automáticas; igual con `SEND_SMS`). La protección NO está en
     el sistema: está en (a) que los permisos se conceden explícitamente en Settings (gesto deliberado
     del usuario) y (b) que la app maliciosa ya tiene la superficie `CALL_PHONE`/`SEND_SMS` — el
     receiver no la amplía, solo la hace programable por broadcast. **F5 (confirmación humana de
     irreversibles) es el control de diseño pendiente que cierra este hueco.**
  2. **F5** (confirmación humana de irreversibles) sigue siendo el control de diseño pendiente — el
     puente ejecuta; la validación humana prevista (ADR-013 NO-ALCANCE) es lo que mitiga el riesgo de
     H2 de forma estructural.
  3. Riesgo documentado en el ADR (cualquier app puede disparar el receiver; ver H2 para el caso máximo).
- **Seam preparado sin coste**: el handler recibe `origen: String?` (el receiver pasa
  `intent.getPackage()`). **CORRECCIÓN (cierre QA/Supervisor)**: con la configuración SOPORTADA
  (Send Intent con el campo Package = `com.screenassistant`, broadcast package-specific) ese valor
  es el TARGET — nuestra app —, NO el emisor real; en un broadcast sin Package, `getPackage()`
  devuelve null y en API 26+ el broadcast implícito ni llega al receiver estático (la afirmación
  previa de que devolvería `net.dinglisch.android.taskerm` era FALSA). La allowlist de Fase 3 NO
  puede basarse en `getPackage()`: deberá evaluar `PendingResult.getSentUid()`/`getSentPackage()`
  (API 28+, guard minSdk 26) y validarse en dispositivo (ver ADR-014/T5). El seam no cambia
  interfaces ni tests.

**QA #5** — ✅ **ACEPTADO (v1.1, H4)**: sin validación de origen en v1, el handler se testea con
cualquier `origen` (casos null, "net.dinglisch.android.taskerm", "com.bighugegiraffe.andromeda")
— sin coste de test; riesgo máximo documentado en H2 (ACTION_CALL sin confirmación); decisión de
seguridad cerrada: exported + riesgo documentado + F5 pendiente + seam de allowlist preparado.

---

## 7. D6 — Seam de testabilidad (contrato exacto)

El seam del usuario se refina: el parámetro se llama `origen` (no `contexto`) para NO colisionar
con el campo `contexto` del contrato del wire (que en esta fase adquiere la convención "silencioso").

```kotlin
// ---- service/system/.../bridge/TaskerMessageHandler.kt ----
/** Recibe el wire ya extraído (no-null) y devuelve SIEMPRE el JSON de 6 claves
 *  (contrato H4 de ADR-013), incluido el timeout. Nunca excepciones hacia el emisor. */
interface TaskerMessageHandler {
    suspend fun handle(extra: String, origen: String?): String
}

// ---- service/system/.../bridge/TaskerResponseEmitter.kt ----
/** Emite la respuesta al mundo exterior: broadcast de vuelta + TTS. */
interface TaskerResponseEmitter {
    fun emitir(respuestaJson: String, hablar: Boolean)
}
```

- `TaskerMessageHandlerImpl` (pura, MockK): inyecta `CommandBridge` + `SystemCommandJsonCodec`.
  `withTimeoutOrNull(10s)` alrededor de `bridge.handle`; si se agota → `encodeResult("error",
  idEcoBestEffort, null, "error_timeout", "Error: Tiempo de espera agotado.")` — **código de error
  NUEVO, aditivo** (Fase 1 no lo conocía; no rompe consumidores: es un valor más del string `error`).
  **v1.1 (H6, aceptado)**: en el timeout el id del envelope se pierde → el handler extrae el id
  **best-effort del wire crudo** (`TaskerPayloadExtractor.idDe(extra)`, mismo parseo defensivo ya
  existente para `silencioso`) y lo echa en la respuesta: `id: null` solo si el wire no trae id
  string válido.
  Defensivo: try/catch alrededor del bridge (el invariante "nunca excepciones hacia Tasker" es del
  contrato Fase 1) → `fallo_ejecucion`.
- `TaskerResponseEmitterImpl` (Android fino): `sendBroadcast(Intent(ACTION_RESPUESTA).apply {
  putExtra(EXTRA_RESPUESTA, json); putExtra(EXTRA_RESPUESTA_ID, TaskerRespuestaTexto.idDe(json)) })`
  + `if (hablar) tts.speak(TaskerRespuestaTexto.textoDe(json))`. El parseo del JSON de salida es
  `TaskerRespuestaTexto` (objeto puro, try/catch best-effort: JSON inválido → `id null`, `texto null`
  → no habla).
- `TaskerCommandReceiver` (cáscara Android ~20 líneas): extrae 2 extras → extractor puro → si
  `texto == null` return; si no `goAsync` + coroutine IO + `handler.handle` + `emitter.emitir` +
  `finally { result.finish() }`. **v1.1 (H9, aceptado)**: try/catch adicional en la cáscara — si el
  handler VIOLA su contrato (lanza, cosa que no debe ocurrir), el receiver inyecta también
  `SystemCommandJsonCodec` y emite `fallo_ejecucion` estructurado (el emisor nunca recibe silencio
  ante un comando extraído; `finally { finish() }` sigue garantizado). **NO testeable sin Robolectric
  (el repo no tiene) → cobertura en la capa pura; el receiver se valida manualmente en dispositivo**
  (misma política documentada para AlarmReceiver en "Notas de proceso"). Estimación: ~20 líneas de
  lógica de pegado, riesgo mínimo.

---

## 8. Estructura de paquetes (nueva en service/system)

```
service/system/src/main/kotlin/com/screenassistant/service/system/bridge/
├── TaskerBridgeContract.kt        (objeto: actions, extras, timeout, packages, código error_timeout)
├── TaskerPayloadExtractor.kt      (puro: PayloadTasker, precedencia message>cmd, silencioso)
├── TaskerMessageHandler.kt        (interface)
├── TaskerMessageHandlerImpl.kt    (pura: timeout + defensivo, inyecta CommandBridge + codec)
├── TaskerRespuestaTexto.kt        (puro: idDe(json), textoDe(json))
├── TaskerResponseEmitter.kt       (interface)
├── TaskerResponseEmitterImpl.kt   (Android: broadcast + TTS)
└── TaskerCommandReceiver.kt       (Android cáscara, @AndroidEntryPoint, goAsync)
```

Constantes del contrato (`TaskerBridgeContract`):

```kotlin
object TaskerBridgeContract {
    const val ACTION_ENTRADA   = "com.screenassistant.TASKER_COMMAND"
    const val ACTION_RESPUESTA = "com.screenassistant.TASKER_RESPONSE"
    const val EXTRA_MESSAGE    = "message"   // AutoRemote (canónico)
    const val EXTRA_CMD        = "cmd"       // Tasker Send Intent (fallback)
    const val EXTRA_RESPUESTA  = "respuesta" // JSON 6 claves
    const val EXTRA_RESPUESTA_ID = "id"      // eco plano para filtrar en Tasker
    const val TIMEOUT_MS       = 10_000L     // ventana goAsync (patrón AlarmReceiver)
    const val CODIGO_TIMEOUT   = "error_timeout"
    const val CONTEXTO_SILENCIOSO = "silencioso" // convención reservada en el wire
    // Referencias documentales (no se usan en v1): net.dinglisch.android.taskerm,
    // com.bighugegiraffe.andromeda (AutoRemote).
}
```

---

## 9. Plan de implementación P0–P5 (con estimación de tests)

| Paso | Archivos (touch) | Contenido | Tests (JUnit 4 + MockK + coroutines-test) |
|---|---|---|---|
| **P0** | `TaskerBridgeContract.kt` + `TaskerPayloadExtractor.kt` (nuevos, service/system/bridge) | Constantes + extracción pura | `TaskerPayloadExtractorTest` **~15**: precedencia message>cmd; solo message; solo cmd; message vacío gana a cmd válido; ambos null → null; silencioso → true; contexto distinto → false; JSON inválido con "silencioso" en texto → false; cmd fallback con silencioso; blank cmd → texto ""; **H5: passthrough — wire ~9000 chars pasa ÍNTEGRO al handler (longitud conservada, el transporte no trunca; el límite 8192 lo decide el puente, ADR-013 S6)**; **H6: `idDe` — id string extraído; id ausente/no-string → null** |
| **P1** | `TaskerMessageHandler.kt` + `TaskerMessageHandlerImpl.kt` (nuevos) | Seam + timeout + defensivo | `TaskerMessageHandlerTest` **~11**: ok (JSON literal del bridge); error decode se propaga; excepción del bridge → fallo_ejecucion; **timeout con tiempo virtual** (coAnswers con delay > 10 s) → error_timeout con 6 claves **y con id eco best-effort del wire (H6)**; blank → json_invalido (F0 intacta); estructura SIEMPRE 6 claves (asserter compartido); id eco; origen null y no-null (QA #5 ACEPTADO); hablar no afecta al JSON |
| **P2** | `TaskerRespuestaTexto.kt` (nuevo) | Parseo puro de salida | `TaskerRespuestaTextoTest` **~6**: ok → texto de resultado; error → texto de error; resultado null → null; id extraído; JSON inválido → null/null; mensaje alternativo |
| **P3** | `TaskerResponseEmitter.kt` + `TaskerResponseEmitterImpl.kt` (nuevos); **edit** `app/.../di/AppModule.kt` | Emisión broadcast + TTS; bindings Hilt: `TextToSpeech`→`TextToSpeechManager`, `TaskerMessageHandler`, `TaskerResponseEmitter` | `TaskerResponseEmitterTest` **~6**: broadcast con extra respuesta e id (slot capture del Intent); hablar=true → tts.speak(texto); hablar=false → sin speak; resultado null → sin speak pese a hablar=true; contexto mocked |
| **P4** | `TaskerCommandReceiver.kt` (nuevo); **edit** `app/src/main/AndroidManifest.xml` | Cáscara + registro exported/intent-filter | **0 unit** (cáscara, patrón AlarmReceiver — cobertura en capa pura, validación manual) |
| **P5** | **edit** `docs/BACKLOG.md` (ADR-014 + NO-ALCANCE ADR-013 + Lote 6 en curso); este diseño | Cierre documental | Compilation Gate + validación manual en dispositivo |

**Total estimado: ~38 tests nuevos** (15 extractor + 11 handler + 6 respuesta texto + 6 emitter).
**Conteo de archivos (H10)**: 8 archivos de PRODUCCIÓN nuevos (`TaskerBridgeContract`, `TaskerPayloadExtractor`,
`TaskerMessageHandler`, `TaskerMessageHandlerImpl`, `TaskerRespuestaTexto`, `TaskerResponseEmitter`,
`TaskerResponseEmitterImpl`, `TaskerCommandReceiver`) + 4 archivos EDITADOS (`app/.../di/AppModule.kt`,
`app/src/main/AndroidManifest.xml`, `docs/BACKLOG.md`, este diseño) + 4 archivos de TEST nuevos
(1 por clase de test). **Sin dependencias nuevas en Gradle** (kotlinx-serialization-json ya es
`implementation` en service/system; AutoRemote NO añade SDK — verificado en D1).
**H8**: "TaskerPayloadExtractor sin android.\*" NO es test meta → verificación de CODE REVIEW
(la compilación en service/system lo permite; el revisor comprueba que no hay imports `android.`).

**Compilation Gate** (obligatorio antes de entregar, Notas de proceso):
`testDebugUnitTest :core:domain:test :app:assembleDebug --rerun-tasks`

**Validación manual en dispositivo (guion de video):**

1. Build + instalar. Smoke QA sin Tasker (v1.1, H1 — en Android 8+ el broadcast debe ser
   package-specific o explícito; el paquete va como argumento POSICIONAL final):
   `adb shell am broadcast -a com.screenassistant.TASKER_COMMAND --es cmd '{"version":1,"id":"s1","accion":"poner_volumen","valor":"subir"}' com.screenassistant`
   → esperado: TTS "Éxito: ..." + broadcast de respuesta (ver paso 4 para capturarlo).
   Fallback con componente explícito:
   `adb shell am broadcast -n com.screenassistant/.service.system.bridge.TaskerCommandReceiver --es cmd '{"version":1,"id":"s2","accion":"poner_volumen","valor":"bajar"}'`
   → mismo comportamiento esperado.
   **Caso negativo (v1.1, H3)**: broadcast SIN extras (solo action + package):
   `adb shell am broadcast -a com.screenassistant.TASKER_COMMAND com.screenassistant`
   → esperado: **sin crash, sin TTS, sin broadcast de respuesta** — silencio deliberado (H3: el
   invariante "el emisor nunca espera sin respuesta" aplica desde el handler hacia arriba; un
   broadcast sin extra no es un comando).
2. Tasker → Tarea nueva → Acción "Enviar Intent" (System → Send Intent):
   - Action: `com.screenassistant.TASKER_COMMAND` · Package: `com.screenassistant` (¡obligatorio en Android 8+!)
   - Extra: clave `cmd`, tipo Text, valor `{"version":1,"id":"v1","accion":"poner_alarma","hora":8,"minuto":0,"etiqueta":"prueba"}`
   - Target: Broadcast Receiver → Run → TTS + alarma creada.
3. Perfil → Evento → "Broadcast Received" → Action `com.screenassistant.TASKER_RESPONSE` →
   mapear extra `respuesta` → `%respuesta`; Tarea demo: Flash `%respuesta`.
   Repetir 2 con `accion` desconocida (`"hacer_magia"`) → el JSON de error de 6 claves aparece en `%respuesta`.
4. Repetir 2 con `"contexto":"silencioso"` en el wire → sin TTS, pero `%respuesta` SÍ llega.
5. (Opcional) AutoRemote: configurar la action de re-emisión a `com.screenassistant.TASKER_COMMAND`,
   target package `com.screenassistant`, mensaje con extra `message` → mismo comportamiento.
6. **Notas de latencia (v1.1, H11)**: si Tasker está en cached state, Android 14+ puede encolar
   broadcasts hacia receivers de registro dinámico (el evento "Broadcast Received") → la respuesta
   puede demorarse 1–2 s; no es un fallo del puente. Esperar antes de descartar.
7. **Nota de quoting (v1.1, H11)**: en PowerShell usa comillas SIMPLES alrededor del JSON
   (`--es cmd '{"version":1,...}'` — las internas son dobles) y evita espacios tras las comas si
   el comando se parte entre líneas; en cmd.exe usa comillas dobles y escapa las internas con `\"`.

---

## 10. ADR-014 (texto para BACKLOG.md)

Ver sección "Decisiones registradas" de `docs/BACKLOG.md` (editado en P5 con este diseño):

> **ADR-014 (código)**: puente Tasker — Fase 2, transporte: broadcast como entrada (Tasker Send
> Intent, canal primario; AutoRemote como wrapper opcional de coste ~0) y TTS + broadcast de vuelta
> como salida; receiver cáscara con patrón AlarmReceiver; seam puro `TaskerMessageHandler`:
> - **T1 (mecanismo)**: (b) Send Intent nativo de Tasker (action única NUESTRA `com.screenassistant.TASKER_COMMAND`,
>   extra `cmd`) como canal primario; (c) AutoRemote aceptado SIN dependencia/sdk (extra canónico
>   `message`, action de re-emisión configurable a la nuestra, target package). Se descarta (a) puro:
>   app extra + key por dispositivo + escritorio solo para la respuesta; ecosistema = 1 dispositivo.
>   Android 8+ (minSdk 26): broadcasts implícitos no llegan a receivers estáticos → el guion rellena
>   el campo Package de Send Intent (package-specific, sí entregado); smoke QA por adb: package
>   posicional final (`--es cmd '<json>' com.screenassistant`) o componente explícito
>   (`-n com.screenassistant/.service.system.bridge.TaskerCommandReceiver`) — v1.1, H1.
> - **T2 (receiver)**: service/system (mismo módulo que bridge/acciones), registrado en app manifest
>   exported=true + intent-filter (obligatorio targetSdk 35); sin directBootAware (necesita usuario
>   desbloqueado: Room/filesDir/TTS). goAsync + withTimeoutOrNull(10s) + finish en finally (patrón
>   AlarmReceiver); sin START_FOREGROUND: acciones actuales < ~2 s; timeout → error estructurado;
>   criterio de migración: acción > ~8 s medidos → FGS specialUse con el MISMO handler (YAGNI, no se anticipa).
> - **T3 (payload)**: extracción de DECISIÓN pura en service/system (`TaskerPayloadExtractor`, JVM sin
>   android.util — el vocabulario message/cmd es de transporte, no de protocolo; core/domain no se filtra,
>   precedente ADR-003). Precedencia message (AutoRemote) > cmd; message presente vacío GANA (→ F0
>   json_invalido estructurado, mejor que silencio); sin extra → SILENCIO (return, patrón AlarmReceiver
>   requestCode<0 — broadcasts basura no merecen TTS). 8192 NO se trunca en transporte (fuente única en
>   el puente, ADR-013 S6/S7). Convención nueva: `"contexto":"silencioso"` en el wire suprime TTS
>   (parseo best-effort, sin falsos positivos) pero no el broadcast — contexto del contrato gana semántica sin tocar domain.
> - **T4 (respuesta)**: mínimo viable (a)+(b): TTS (canal humano; binding Hilt NUEVO
>   TextToSpeech→TextToSpeechManager; coexisten 2 instancias TTS con overlay, cada una con su PROPIO
>   motor → riesgo real: habla SIMULTÁNEA de dos motores si coinciden, no colisión de cola — v1.1, H7;
>   uso secuencial aceptado) + broadcast `com.screenassistant.TASKER_RESPONSE` con extra `respuesta`
>   (MISMO JSON de 6 claves, contrato H4 intacto, sin envoltorio) e `id` plano (filtro Tasker por
>   variable). (c) AutoRemote URL callback FUERA (key + Settings + red; extensión Fase 3, mismo cuerpo JSON).
> - **T5 (seguridad)**: exported + intent-filter, SIN permiso custom (signature inviable: el emisor no
>   comparte firma; normal inútil) y SIN validación de origen v1 (broadcasts no ordenados: getPackage()
>   devuelve el TARGET si Tasker rellena Package; getSentPackage solo en ordered → no fiable). RIESGO
>   MÁXIMO REAL (v1.1, H2): con CALL_PHONE concedido, ACTION_CALL marca DIRECTO sin diálogo
>   (CallAction.kt:18,34) → una app maliciosa podría disparar llamadas automáticas; igual con SEND_SMS.
>   Mitigación real: permisos concedidos explícitamente en Settings (la app maliciosa ya los tiene — el
>   receiver no amplía la superficie, solo la hace programable por broadcast) + F5 (confirmación humana)
>   como control pendiente que cierra el hueco; riesgo documentado; seam preparado (`origen`
>   best-effort viaja al handler) → allowlist en Settings (Fase 3) sin cambiar interfaces.
> - **T6 (seam)**: `TaskerMessageHandler { suspend fun handle(extra: String, origen: String?): String }`
>   SIEMPRE JSON 6 claves (timeout incluido: withTimeoutOrNull → código NUEVO aditivo `error_timeout`
>   con id eco best-effort del wire — v1.1, H6; try/catch defensivo → fallo_ejecucion, invariante
>   "nunca excepciones hacia Tasker"). INVARIANTE ACOTADO (v1.1, H3): "el emisor nunca espera sin
>   respuesta" aplica desde el handler hacia arriba; extra ausente → silencio deliberado (anti-spam,
>   precedente requestCode<0 de AlarmReceiver) es la única excepción.
>   + `TaskerResponseEmitter { fun emitir(json: String, hablar: Boolean) }` (parseo puro en
>   TaskerRespuestaTexto: idDe/textoDe best-effort). Receiver cáscara ~20 líneas (patrón AlarmReceiver:
>   no testeable sin Robolectric → cobertura en capa pura, política de "Notas de proceso"); try/catch
>   defensivo en la cáscara: si el handler viola su contrato → `fallo_ejecucion` con codec inyectado
>   (v1.1, H9; `finally { finish() }` garantizado).
> - **T7 (tests)**: ~38 tests JVM (JUnit 4 + MockK + coroutines-test; SIN Robolectric ni dependencias
>   nuevas): extractor ~15 (precedencia/silencio/silencioso + H5 passthrough 8192 + H6 idDe), handler
>   ~11 (incl. timeout con tiempo virtual e id eco), respuesta texto ~6, emitter ~6; "sin android.*"
>   en el extractor es verificación de CODE REVIEW, no test (H8); receiver validado manualmente en
>   dispositivo (guion: Send Intent + Event Broadcast Received + adb am broadcast con paquete
>   posicional o -n + caso negativo sin extras + notas de latencia/quoting H11).
> - **T8 (build)**: sin dependencias Gradle nuevas (kotlinx-serialization-json ya es implementation en
>   service/system — verificado por QA Fase 1; AutoRemote sin SDK). Gate: testDebugUnitTest
>   :core:domain:test :app:assembleDebug --rerun-tasks.

---

## 11. Fuera de alcance (Fase 3 candidata)

- AutoRemote URL callback (requiere key + UI Settings + red).
- Allowlist de packages emisores (Settings) — seam ya preparado.
- F5: confirmación humana de acciones irreversibles (llamada/SMS) — sigue pendiente (ADR-013).
- Reutilización de la instancia TTS del overlay (refactor).
- Migración a FGS si una acción excede la ventana goAsync (criterio en D2).
