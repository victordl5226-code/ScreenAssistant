# Lote 7 — Cierre del puente Tasker (Fase 3A) — v1.2: token compartido + URL callback por flag explícito

Documento de diseño del Arquitecto — 02/08/2026. Estado: **v1.2a IMPLEMENTADO — GATE VERDE 740/0
(03/08/2026)**; el veto (B1 + H1, §0.1–0.2) ordenó el rediseño del v1.1 (token compartido en el
transporte); QA shift-left aprobó con cambios (v1.2a, §12), QA final **APROBÓ** y UI/UX aprobó con
cambios aplicados (§13). Pendiente único: validación manual en dispositivo (guion §9). Puntos de
testabilidad marcados **QA #n**.

**Historial de revisiones:**
- **v1.0 → v1.1 (02/08/2026, APROBADO CON CAMBIOS por QA shift-left)**: H1 (materialización del default
  de F2 en el store), H2 (igualdad EXACTA en F1, prefijo solo en F3), H3 (PuenteConfig/Store en
  core:data/util), M1–M6, O1–O2. v1.1 se implementó y pasó el gate (752 tests, 57 del lote) con una
  enmienda de implementación documentada (API pública `getSentFromUid/getSentFromPackage` API 34+ en
  lugar de los `getSentUid/getSentPackage` API 28 no compilables).
- **VETO (02/08/2026, cierre QA final + Supervisor)**: dos bugs de premisa verificados sobre fuente real
  (AOSP android15-release + doc oficial de seguridad de Android + javap): **B1** (orden de llamadas con
  `goAsync`) y **H1** (semántica de opt-in de la identidad compartida — invalida el diseño entero de F1
  y la decisión de canal de F3). Ver §0.
- **v1.2 (este documento)**: rediseño F1 (token compartido en el TRANSPORTE, no en el contrato JSON) y
  F3 (flag explícito `enviarRespuestaURL`, desacoplado del origen). F2 no cambia.

**Resumen ejecutivo del veto (por qué F1/F3 murieron y qué las sustituye):**

| Pieza v1.1 | Veredicto | Sustituto v1.2 |
|---|---|---|
| F1 allowlist por paquete (origen real vía getSentFromUid/…Package) | **MUERTA (B1+H1)**: tras `goAsync()` las API devuelven SIEMPRE -1/null (B1); y aunque se lean antes, el remitente NO activa `setShareIdentityEnabled` (H1) → en producción el paquete es SIEMPRE null | **F1 token compartido**: extra `token` del transporte, validado en el handler (fail-closed `token_invalido`), configurable en la UI |
| `OrigenTasker` + `obtenerOrigenDe` + `TaskerOrigenVerifier` (allowlist) | **ELIMINADOS** (código muerto: su input nunca existe en producción; 15 tests del verifier eliminados) | — (el seam vuelve a `handle(extra, token)`; cero lecturas de identidad) |
| F3 URL callback con decisión `origen.paquete.startsWith("com.bighugegiraffe")` | **MUERTA (H1)**: el prefijo dependía del origen, que nunca se obtiene | **F3 flag explícito** `enviarRespuestaURL` (default OFF) + key; el callback ya no recibe origen |
| F2 setPackage + default taskerm | **INTACTA** (no depende del origen) | F2 intacta |

---

## 0. Verificaciones técnicas previas (hechas sobre fuente real, no especulación)

### 0.1 VETO B1 — `goAsync()` anula `mPendingResult`: orden de llamadas (Supervisor)

Fuente: AOSP `frameworks/base/core/java/android/content/BroadcastReceiver.java` (rama android15-release)
y `BroadcastQueueModernImpl.java`; verificación con `javap` sobre la SDK pública.

- `BroadcastReceiver.goAsync()` (método público, javap) ejecuta internamente `mPendingResult = null`
  (el PendingResult se transfiere al llamante y el receiver queda sin estado de entrega).
- `getSentFromUid()` / `getSentFromPackage()` (API 34+) **leen `mPendingResult`**:
  `mPendingResult != null ? mPendingResult.getSentFromUid() : INVALID_UID` (análogo con el paquete).
- **Consecuencia**: llamarlas DESPUÉS de `goAsync()` devuelve SIEMPRE `-1`/`null`, pase lo que pase con
  la identidad real del emisor. El código implementado del v1.1 lo hace exactamente así
  (TaskerCommandReceiver.kt:58-59: `val result = goAsync()` y DESPUÉS `obtenerOrigenDe(this)`).
- **Regla documentada (aplica a v1.2 y a cualquier futuro)**: cualquier lectura de
  `getSentFromUid/getSentFromPackage` DEBE hacerse en `onReceive` ANTES de `goAsync()`. En v1.2 no hay
  ninguna lectura de identidad que ordenar (H1 las vuelve inútiles) — el bug queda moot por eliminación
  del código, no por corrección de orden.

### 0.2 VETO H1 — la identidad del remitente NO es obtenible con API pública en nuestro escenario (QA)

Fuente: AOSP `frameworks/base/services/core/java/com/android/server/broadcast/` —
`BroadcastRecord.java` (campo `shareIdentity`, default FALSE en el constructor, línea ~489) y
`BroadcastQueueModernImpl.java` (líneas ~1190-1220: al entregar, pasa
`r.shareIdentity ? r.callingUid : Process.INVALID_UID` al receiver); doc oficial de seguridad de
Android (referencia `BroadcastReceiver.getSentFromUid`): la identidad del remitente se expone **solo
si el emisor hizo opt-in** con `BroadcastOptions.setShareIdentityEnabled(true)` — cita literal de la
doc oficial: *"if the sender opted in to sharing identity"*.

- `setShareIdentityEnabled` es una opción del **EMISOR** al llamar `sendBroadcast`. Default: **false**.
- Tasker "Send Intent", AutoRemote y `adb shell am broadcast` **NO activan el opt-in** (no es
  configurable desde esas apps; el shell no usa BroadcastOptions). Por tanto, en los únicos escenarios
  reales de entrada del puente, `getSentFromUid()/getSentFromPackage()` devuelven `-1`/`null`
  **aunque se lean antes de goAsync** (B1 corregido).
- **Por qué el gate no lo detectó**: los 57 tests del lote ejercitan la capa pura con inputs fabricados
  (`OrigenTasker(paquete = "net.dinglisch.android.taskerm")`, `paquete = "com.bighugegiraffe.andromeda"`,
  etc.) que la producción NUNCA produce: la cadena `obtenerOrigenDe` (cáscara, sin tests) no es
  testeable sin Robolectric y el verifier/callback puros no pueden saber que su input es imposible.
  Los tests son verdes sobre un contrato que el ecosistema emisor no puede cumplir — el caso clásico
  de prueba de capa pura desacoplada de la realidad del transporte (lección QA #7, ver §8).
- **Conclusión de diseño**: la identidad del emisor NO es una entrada disponible. Toda decisión de
  seguridad/autenticación debe usar material que el emisor SÍ controla: el contenido del propio
  Intent. Ese es el fundamento del token compartido (F1 v1.2) y del flag explícito (F3 v1.2).

### 0.3 AutoRemote — mecanismo real (intacto de v1.1; verificado en joaoapps.com/autoremote/direct)

- Entrada por push FCM con re-emisión a nuestra action (targetPackage del usuario, coste ~0).
- Salida por URL: endpoint HTTPS público
  `https://autoremotejoaomgcd.appspot.com/sendmessage?key=<KEY>&message=<urlencoded>` — HTTP puro,
  sin SDK, cualquiera con la key puede mandar mensajes. Las propias keys de AutoRemote son el
  precedente de facto del token compartido de F1 (modelo "clave secreta en el comando").
- AutoRemote NO consume `TASKER_RESPONSE` (solo re-emite la ENTRADA); `setPackage` en la respuesta no
  puede "romper AutoRemote" (corrección documental de ADR-014/T4, vigente).

### 0.4 Stack y restricciones verificadas (intactas de v1.1, UNA CLAVE NUEVA)

- `service/system`: JVM puro + Android fino; JUnit 4 + MockK 1.13.12 + coroutines-test 1.9.0; SIN
  Robolectric (cáscaras → validación manual).
- **NUEVO — decisivo para F1**: `SystemCommandJsonCodec` (core:domain) decodifica con
  `Json { ignoreUnknownKeys = false; explicitNulls = true }` (ADR-013/S5): **un wire que contenga la
  clave `token` SIN estar registrada en el contrato se rechaza con `json_invalido`**. Cualquier
  adición al contrato JSON de Fase 1 obliga a tocar core/domain + el codec + los 95 tests de Fase 1
  (cuantificación en §2.1). El token NO entra por el JSON.
- NO hay OkHttp/Retrofit → `HttpURLConnection` (JDK). `INTERNET` ya en manifest de app. Sin cambios
  de build ni de manifest.
- `core:data/util` tiene el patrón `ApiKeyProvider` (EncryptedSharedPreferences AES256 + fallback)
  → replicado por `PuenteConfigStore` (ya implementado, se edita).
- Seam actual implementado: `TaskerMessageHandler.handle(extra, OrigenTasker)`; `TaskerPayloadExtractor`
  (message/cmd, contexto silencioso); `TaskerResponseEmitter` (F2, setPackage desde config);
  `AutoRemoteUrlCallback` (F3); `TaskerBridgeContract` (acciones/extras/códigos).

---

## 1. Diagrama de capas (objetivo v1.2)

```
[Emisor]  Tasker (Send Intent + extra token) · AutoRemote (push + re-emisión + extra token) · adb shell (debug)
   │  broadcast: action "com.screenassistant.TASKER_COMMAND", extras "message"/"cmd" + "token" (v1.2)
   ▼
┌─ service:system (transporte, módulo único) ──────────────────────────────────────────┐
│ TaskerCommandReceiver (Android cáscara, Hilt, goAsync)                               │
│   ├─ TaskerPayloadExtractor (PURA) → PayloadTasker(texto?, silencioso, token?) [edit]│
│   │     (B1: ya NO hay lecturas de identidad que ordenar antes de goAsync)           │
│   ▼                                                                                  │
│ TaskerMessageHandler (PURA) — seam v1.2: handle(extra, token: String?)               │
│   ├─ PuenteConfigStore.cargar() → PuenteConfig v2 (config compartida, UNA fuente)    │
│   ├─ F1 v1.2: tokenCompartido no-blank → exige extra token exacto (fail-closed)      │
│   │     └─ DENY → encodeResult "token_invalido" (6 claves, id eco, sin ejecutar)     │
│   └─ withTimeoutOrNull(10s) → CommandBridge (sin cambios)                            │
│   ▼  SIEMPRE JSON de 6 claves                                                        │
│ TaskerResponseEmitter (Android fino) — emitir(json, hablar) SIN CAMBIOS (F2 intacta) │
│   ├─ setPackage(PuenteConfig.packageRespuesta) si configurado — F2: privacidad       │
│   ├─► broadcast "com.screenassistant.TASKER_RESPONSE" {respuesta, id}                │
│   └─► TTS (TextToSpeech) — suprimido si contexto:"silencioso"                        │
│ AutoRemoteUrlCallback (PURA con UrlSender inyectado) — F3 v1.2: flag + key           │
│   ├─ decide: config.enviarRespuestaURL == true && key no-blank (SIN origen)          │
│   ├─ AutoRemoteUrlBuilder.construir(key, json) (PURA) → URL HTTPS                    │
│   └─ HttpUrlSender (cáscara JDK, sin deps) → fire-and-forget en IO, no bloquea       │
└───────────────────────────────────────────────────────────────────────────────────────┘
   ▲  depende hacia adentro: core:domain NO conoce el transporte
core:domain: CommandBridge · SystemCommandJsonCodec · CommandEnvelope · AccionRegistry (INTACTOS —
            Fase 1 sin tocar: 644 tests del contrato siguen validando el wire tal cual)
core:data/util: PuenteConfig v2 · PuenteConfigStore (cifrado + fallback — junto a ApiKeyProvider)
app: UI PuenteSettingsSection (token + flag + packageRespuesta + key)
```

Regla de arquitectura (vigente): el transporte completo (extras, broadcast, TTS, HTTP, token, config
del puente) es infraestructura de `service:system` + capa UI en `app`. `core/domain` NO cambia: el
código `token_invalido` es aditivo (encodeResult acepta String libre, precedente `error_timeout`) y
el token es un extra de transporte, no semántica de protocolo (igual que `message`/`cmd` hoy). El
flujo de voz NO usa token ni config del puente (vía `SystemCommandParser` distinta).

---

## 2. F1 v1.2 — Token compartido (secret) en el TRANSPORTE

### 2.1 DECISIÓN de contrato: extra del intent, NO campo del JSON (con cuantificación)

| Opción | Impacto | Veredicto |
|---|---|---|
| **Token como campo `token` del envelope JSON** (wire) | TOCA core/domain: abstract val nuevo en la base sealed `CommandEnvelope` + override en las **22 subclases** (18 data class + 4 data object) + validación de longitud en `validarBase` (≤ 200, patrón id/contexto). Tests de Fase 1 afectados: **EnvelopeSerializationTest 23 + SystemCommandJsonCodecTest 70 + CorrespondenciaWireTest 2 = 95**, y por arrastre AccionRegistryTest 24 + ClasificadorAccionTest 22 (141 en core/domain). Peor aún: el codec decodifica con `ignoreUnknownKeys=false` → o el campo se registra (el contrato wire de Fase 1 cambia y Tasker debe mandar el token dentro del JSON, mezclando autenticación de canal con semántica de comando) o cualquier wire con token se rechaza con `json_invalido`. Además la respuesta fija de 6 claves (H4) no podría llevarlo de vuelta | **RECHAZADA** |
| **Token como extra del TRANSPORTE (`intent.getStringExtra("token")`) — ELEGIDA** | CERO impacto en core/domain y en los **644 tests** del contrato de Fase 1. El token es autenticación de CANAL, no semántica de comando — exactamente el mismo argumento por el que `message`/`cmd` son extras hoy (ADR-014/T3: "el vocabulario message/cmd es de transporte, no de protocolo → core/domain no se filtra"). Tasker "Send Intent" admite tantos extras como se quiera (el usuario añade `token`); AutoRemote re-emite con los extras del mensaje; adb lo pasa con `--es token '...'` | **ELEGIDA** |

Justificación adicional: la Fase 1 fue validada con 644 tests y su contrato wire es la biyección
registro↔codec (CorrespondenciaWireTest la vigila). Cualquier cambio de contrato obliga a re-validar
toda la Fase 1 y a Tasker a mandar el secreto dentro de un JSON que además es el cuerpo del comando
(peor higiene: el secreto viajaría en el `resultado` de logs del puente). El token como extra es el
modelo del propio AutoRemote (keys en el comando, fuera del payload semántico).

### 2.2 Contrato del extra y extracción (firmas exactas)

```kotlin
// TaskerBridgeContract.kt — EDICIÓN
object TaskerBridgeContract {
    // ... acciones/extras existentes intactos ...
    const val EXTRA_TOKEN = "token" // v1.2 (F1): autenticación de CANAL (extra del transporte,
                                    // NO campo del contrato JSON — §2.1). Lo pone el emisor en
                                    // Send Intent / AutoRemote / adb (`--es token '...'`).
    // ... F2/F3 intactos ...
    /** v1.2: código NUEVO aditivo de F1 (precedente error_timeout/origen_no_autorizado eliminado). */
    const val CODIGO_TOKEN_INVALIDO = "token_invalido"
    /** Patrón ADR-009: "Error: <razón>." — llega al emisor en `mensaje`. */
    const val MSG_TOKEN_INVALIDO = "Error: Token inválido."
}
```

```kotlin
// TaskerPayloadExtractor.kt — EDICIÓN (default null → los 19 tests existentes NO cambian)
data class PayloadTasker(
    val texto: String?,
    val silencioso: Boolean,
    /** v1.2 (F1): extra de autenticación de canal; null si el emisor no lo mandó. */
    val token: String? = null,
)

object TaskerPayloadExtractor {
    /** v1.2: tercer parámetro con default — los call sites de Fase 2 y los 19 tests
     *  existentes siguen compilando sin cambios. */
    fun extraer(message: String?, cmd: String?, token: String? = null): PayloadTasker =
        PayloadTasker(message ?: cmd ?: return PayloadTasker(null, false), esContextoSilencioso(texto), token)
    // idDe / esContextoSilencioso intactos.
}
```

El receiver lee el extra ANTES de goAsync (con la extracción del payload, paso que ya ocurre antes —
sin relación con B1: no hay identidad que leer, pero el hábito "leer el Intent antes de goAsync" se
mantiene como norma).

### 2.3 Validación fail-closed en el handler (capa pura testeable — QA #1)

```kotlin
// TaskerMessageHandler.kt — seam v1.2 (firma FINAL)
interface TaskerMessageHandler {
    /**
     * v1.2 (F1, rediseño tras veto B1/H1): `token` = extra del transporte con el secreto
     * compartido. La identidad del emisor (getSentFromUid/getSentFromPackage) NO es
     * obtenible sin opt-in del emisor (H1) → la autenticación usa material que el emisor
     * SÍ controla: el extra token del propio Intent.
     */
    suspend fun handle(extra: String, token: String?): String
}
```

```kotlin
// TaskerMessageHandlerImpl.kt — EDICIÓN (verifier ELIMINADO, §2.6)
class TaskerMessageHandlerImpl @Inject constructor(
    private val bridge: CommandBridge,
    private val codec: SystemCommandJsonCodec = SystemCommandJsonCodec(),
    private val configStore: PuenteConfigStore,   // sin TaskerOrigenVerifier
) : TaskerMessageHandler {

    override suspend fun handle(extra: String, token: String?): String {
        val config = configStore.cargar()
        val tokenConfig = config.tokenCompartido
        if (tokenConfig.isNotBlank() && token?.trim() != tokenConfig.trim()) {
            // F1 v1.2 fail-closed: el bridge NO se ejecuta; el emisor debe VER el error
            // (invariante H3 intacto). El token NUNCA se loguea ni se expone en el JSON.
            return codec.encodeResult(
                "error",
                TaskerPayloadExtractor.idDe(extra),
                null,
                TaskerBridgeContract.CODIGO_TOKEN_INVALIDO,
                TaskerBridgeContract.MSG_TOKEN_INVALIDO,
            )
        }
        // ... resto intacto: withTimeoutOrNull(10s) → error_timeout; catch → fallo_ejecucion ...
    }
}
```

Semántica exacta (tabla de decisión del token):

| tokenCompartido (config) | extra token recibido | Resultado |
|---|---|---|
| blank (default) | cualquiera / ausente | **Canal abierto** — se ejecuta (cero regresión; compat modo actual, documentado "no protegido") |
| no-blank | == config (trim) | Ejecuta |
| no-blank | ausente | **token_invalido** (fail-closed: todo comando sin token correcto) |
| no-blank | != config | **token_invalido** |

- La comparación es igualdad simple tras trim (la UI trima al guardar). Comparación en tiempo
  constante (MessageDigest.isEqual): **descartada por honestidad** — el token viaja EN CLARO en el
  intent y cualquier app que espíe broadcasts lo lee (§2.5); un timing attack sobre la comparación no
  añade superficie real. Documentado, no teatral.
- La respuesta `token_invalido` usa las 6 claves + id eco best-effort del wire (precedente
  `error_timeout`); el bridge NO se ejecuta (coVerify exactly=0 en tests) y la respuesta SÍ se emite
  (el emisor debe ver el error — invariante H3).

### 2.4 Error aditivo (JSON de salida, SIEMPRE 6 claves)

```json
{"version":1,"id":"t-42","estado":"error","resultado":null,
 "error":"token_invalido","mensaje":"Error: Token inválido."}
```

Broadcast sin extras de un emisor sin token → **silencio** (H3, antes del handler — intacto). El
token NO se expone en el JSON ni en logs (solo log de rechazo a nivel debug sin el valor).

### 2.5 Seguridad honesta del token (sección formal — QA #8)

1. **El token viaja en claro** en el extra del Intent de broadcast implícito. Cualquier app del
   dispositivo que registre un receiver para `com.screenassistant.TASKER_COMMAND` (o espíe broadcasts)
   puede verlo. Este es EXACTAMENTE el modelo de AutoRemote keys (la key viaja en la URL HTTPS del
   endpoint y la conoce cualquiera que la capture). No es una autenticación fuerte; es un
   **secreto de canal compartido** que eleva la barra del atacante casual (de "mandar un broadcast
   cualquiera" a "conocer el secreto del canal").
2. **Mitigaciones que SÍ se aplican**:
   - F2 `setPackage` sigue protegiendo la RESPUESTA (el JSON con `leer_nota` no es global).
   - El token NO se loguea (ni en aceptación ni en rechazo) ni se expone en el JSON de respuesta.
   - El flujo de voz NO usa token (el parser hablado no pasa por el canal broadcast).
   - La UI advierte del riesgo y del significado ("vacío = canal abierto").
3. **Lo que NO se promete**: no es un secreto criptográfico a prueba de espionaje; el hueco
   estructural sigue siendo F5 (confirmación humana de irreversibles). Rotación manual del token
   = reescribir el campo en la UI y en Tasker (documentado en la UI).
4. Comparación: si el token se filtrara, un atacante con el secreto puede emitir comandos — pero YA
   podía hacerlo cualquier app con permiso de broadcast y comandos conocidos antes de F1. El token
   convierte "abierto de fábrica" en "protegido por defecto solo si el usuario lo activa", con la
   contrapartida de una configuración explícita (fail-open por defecto elegido: cero regresión, §2.6).
5. **Fail-open del catch de `cargar()` (verificado en el código, v1.2a)**: el catch defensivo de
   `PuenteConfigStore.cargar()` ante una excepción de lectura de prefs devuelve la config default →
   token `""` → canal abierto. Fail-open aceptado y documentado (patrón ApiKeyProvider); la
   alternativa fail-closed (denegar todo) se descarta por disponibilidad. Escenario muy raro (prefs
   corruptas); el efecto neto es degradación a "sin protección", nunca a "bloqueo del puente".

### 2.6 Qué se elimina (confirmación) y por qué

**SE ELIMINA (código muerto tras H1+B1):**
- `OrigenTasker.kt` (data class + `obtenerOrigenDe`): su fuente de datos (getSentFromUid/…Package)
  devuelve -1/null en producción (opt-in nunca activado, H1) y el orden actual la hace fallar siempre
  (B1). 0 call sites sobreviven.
- `TaskerOrigenVerifier.kt` + `TaskerOrigenVerifierTest` (15 tests): la política de allowlist recibe
  inputs que la producción nunca produce (paquete SIEMPRE null) — el verifier era una decisión sobre
  un contrato imposible. Elisión = eliminación de falsa seguridad (una allowlist que solo ve
  "desconocido" es peor que ninguna: da apariencia de protección).
- `TaskerBridgeContract.CODIGO_ORIGEN_NO_AUTORIZADO` / `MSG_ORIGEN_NO_AUTORIZADO` /
  `PACKAGE_AUTOREMOTE_PREFIX`.
- Campos `allowlistActivada` / `paquetesPermitidos` + `normalizarPaquetes` de PuenteConfig (v1.2, §5).
- Provider `provideTaskerOrigenVerifier` de AppModule (5 → 4 providers).
- UI: switch "Solo emisores autorizados" + campo multilínea de paquetes + error
  `ALLOWLIST_SIN_PAQUETES`.

**B1 aplicado**: al no conservarse NINGUNA lectura de identidad, el bug de orden queda sin efecto.
Regla documentada para el futuro: si se reintroduce cualquier lectura de
getSentFromUid/getSentFromPackage, DEBE ir en onReceive ANTES de goAsync() (mPendingResult se anula
en goAsync — fuente AOSP §0.1).

**SE CONSERVA:**
- F2 completa (setPackage + default taskerm materializado en `cargar()`) y sus 9 tests.
- Handler base (timeout, fallo_ejecucion, id eco, cancelación) — 14 tests revertidos a la firma nueva.
- `TaskerPayloadExtractor` base (message/cmd/silencio) — 19 tests intactos (default param).
- `AutoRemoteUrlBuilder`/`UrlSender`/`HttpUrlSender` (6 tests) — F3 canal, intactos.
- Patrón `PuenteConfigStore` (cifrado + fallback) y `packageRespuesta`/`autoRemoteKey`.
- El seam vuelve al espíritu de ADR-014/T6 (un solo parámetro de comando + material de transporte),
  con la enmienda v1.1 revertida y documentada (§10).

---

## 3. F2 — Privacidad de la respuesta (setPackage configurable) — INTACTA (v1.1)

Sin cambios frente a v1.1 (sección 3 del documento anterior): `TaskerResponseEmitter.emitir(json,
hablar)` sin cambio de firma; `setPackage(PuenteConfig.packageRespuesta)` si no-blank; default
`net.dinglisch.android.taskerm` materializado en `PuenteConfigStore.cargar()` (H1); el modo global
sin setPackage no es alcanzable desde la UI. `TaskerResponseEmitterTest` (9) y el store (clave
`packageRespuesta`) NO cambian en este lote salvo por las claves nuevas de la config (§5).

---

## 4. F3 v1.2 — URL callback desacoplado del origen (flag explícito)

### 4.1 DECISIÓN: flag `enviarRespuestaURL` (default OFF) + key — sin decisión por emisor

| Opción | Efecto | Veredicto |
|---|---|---|
| Disparar la URL SIEMPRE que hay key no-blank (independiente del emisor) | Cualquier comando (incluido adb/sistema) con key configurada → URL de la respuesta al dispositivo de la key. El envío es "inofensivo" si AutoRemote no existe (HTTP falla → false + log), pero genera **llamadas URL sorpresa** que el usuario no pidió (una respuesta de un comando local por voz NO debería ir a AutoRemote) | Rechazada |
| **Flag explícito `enviarRespuestaURL: Boolean` default OFF — ELEGIDA** | La URL solo se envía cuando el usuario lo activa Y hay key. Cero llamadas sorpresa; el flag + la key son independientes del emisor real (que es inobtenible, H1). El orquestador deja de recibir origen | **ELEGIDA** |

Justificación: con H1 muerto, la antigua condición `origen.paquete.startsWith("com.bighugegiraffe")`
no tiene equivalente real (el origen nunca se sabe). El sustituto honesto es config explícita: el
usuario que usa AutoRemote como canal de retorno activa el flag; el que no, no recibe URLs. El envío
URL sigue siendo inofensivo si la app AutoRemote no está (fail-soft), pero no se dispara sin pedirlo.

### 4.2 Firmas exactas (v1.2)

```kotlin
// AutoRemoteUrlCallback.kt — EDICIÓN: orquestador PURA testeable, SIN origen (QA #4)
class AutoRemoteUrlCallback @Inject constructor(
    private val configStore: PuenteConfigStore,
    private val urlSender: UrlSender,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** v1.2: decide SOLO por config (flag && key no-blank). Sin origen: la identidad del
     *  emisor es inobtenible (H1). Fire-and-forget en IO, hermana del goAsync. */
    fun responderSiAplica(respuestaJson: String) {
        val config = configStore.cargar()
        if (!config.enviarRespuestaURL) return
        val key = config.autoRemoteKey
        if (key.isBlank()) return
        val url = AutoRemoteUrlBuilder.construir(key, respuestaJson) ?: return
        CoroutineScope(io).launch {
            try { urlSender.enviar(url) } catch (e: Exception) { /* fail-soft defensivo */ }
        }
    }
}
```

`AutoRemoteUrlBuilder`, `UrlSender`, `HttpUrlSender`: INTACTOS (v1.1, §4.2). Se elimina
`PACKAGE_AUTOREMOTE_PREFIX` del contrato (única política de prefijo del diseño — muerta con H1).

### 4.3 Decisión de canal (intacta en lo esencial)

**ADEMÁS** (canales independientes, nunca exclusivos): broadcast local + TTS se emiten SIEMPRE; la
URL SOLO si flag ON + key. Fracaso de red → false + log, sin reintento (YAGNI 1 dispositivo). La
corrutina es hermana del bloque goAsync (finish() no la espera ni la cancela; pérdida por muerte de
proceso = best-effort aceptado). Con el flag OFF el canal URL está inerte aunque haya key.

### 4.4 Wiring en el receiver (cáscara final v1.2)

```kotlin
// TaskerCommandReceiver.onReceive (v1.2):
val payload = TaskerPayloadExtractor.extraer(
    intent.getStringExtra(TaskerBridgeContract.EXTRA_MESSAGE),
    intent.getStringExtra(TaskerBridgeContract.EXTRA_CMD),
    intent.getStringExtra(TaskerBridgeContract.EXTRA_TOKEN), // F1 v1.2: autenticación de canal
)
val extra = payload.texto ?: return            // silencio deliberado ANTES de goAsync (H3)
val result = goAsync()                          // B1: no hay identidad que leer (H1) — ver §2.6
CoroutineScope(Dispatchers.IO).launch {
    try {
        val respuesta = handler.handle(extra, payload.token)   // seam v1.2
        emitter.emitir(respuesta, hablar = !payload.silencioso)
        urlCallback.responderSiAplica(respuesta)               // F3 v1.2: sin origen
    } catch (e: CancellationException) { throw e }
    catch (t: Throwable) { /* H9 intacto: fallo_ejecucion con codec inyectado */ }
    finally { result.finish() }
}
```

---

## 5. Configuración compartida del puente — v2 (campos exactos)

### 5.1 Modelo y persistencia

```kotlin
// core/data/src/main/kotlin/com/screenassistant/core/data/util/PuenteConfig.kt — EDICIÓN
data class PuenteConfig(
    /** F2: paquete del receptor del broadcast de respuesta (CRUDO de guardado; el STORE
     *  materializa el default taskerm si ausente/blank — H1 intacto). */
    val packageRespuesta: String? = null,
    /** F3: key del dispositivo AutoRemote (secreto de control → prefs cifradas). */
    val autoRemoteKey: String = "",
    /** F1 v1.2: token compartido del canal (extra "token" del transporte). blank = canal
     *  abierto (compat modo actual, documentado "no protegido"); no-blank = fail-closed. */
    val tokenCompartido: String = "",
    /** F3 v1.2: flag explícito de URL callback (default OFF — cero llamadas URL sorpresa). */
    val enviarRespuestaURL: Boolean = false,
) {
    companion object {
        /** Default fail-closed de F2 (materializado en PuenteConfigStore.cargar(), H1). */
        const val PACKAGE_RESPUESTA_DEFAULT = "net.dinglisch.android.taskerm"
    }
}
// ELIMINADO: allowlistActivada, paquetesPermitidos, normalizarPaquetes (muertos, §2.6).
```

```kotlin
// PuenteConfigStore.kt — EDICIÓN (patrón EXACTO ApiKeyProvider intacto; claves nuevas)
@Singleton
class PuenteConfigStore @Inject constructor(@ApplicationContext context: Context) {
    // EncryptedSharedPreferences "secure_puente_prefs" (AES256_SIV/GCM, MasterKey) + fallback
    // "secure_puente_prefs_fallback" con isUsingFallback (copia literal de ApiKeyProvider).
    fun cargar(): PuenteConfig
    // H1 intacto: "packageRespuesta" ausente/blank → PACKAGE_RESPUESTA_DEFAULT.
    // NUEVAS: "tokenCompartido" → getString("tokenCompartido", "") ?: "" ;
    //         "enviarRespuestaURL" → getBoolean("enviarRespuestaURL", false).
    // ELIMINADAS: "allowlistActivada", "paquetesPermitidos" (y la re-normalización de paquetes
    //             al leer — la normalización de paquetes ya no existe).
    fun guardar(config: PuenteConfig) // 4 setters con apply(): packageRespuesta (crudo),
    // autoRemoteKey, tokenCompartido (se persiste tal cual; el trim lo hace la UI),
    // enviarRespuestaURL.
    var isUsingFallback: Boolean; private set
}
```

### 5.2 AppModule (providers finales — 4, antes 5)

```kotlin
// ELIMINADO: provideTaskerOrigenVerifier.
@Provides @Singleton fun provideTaskerMessageHandler(
    bridge: CommandBridge, codec: SystemCommandJsonCodec,
    configStore: PuenteConfigStore): TaskerMessageHandler        // sin verifier
@Provides @Singleton fun provideTaskerResponseEmitter(...)        // INTACTO
@Provides @Singleton fun provideUrlSender(): UrlSender = HttpUrlSender()  // INTACTO
@Provides @Singleton fun provideAutoRemoteUrlCallback(...)        // INTACTO (configStore + sender + io)
```

### 5.3 UI — `PuenteSettingsSection` v2 (patrón ApiKeySection)

`PuenteUiState` v2:
```kotlin
data class PuenteUiState(
    val packageRespuesta: String = "",    // F2 (intacto)
    val autoRemoteKey: String = "",       // F3 (intacto)
    val tokenCompartido: String = "",     // F1 v1.2 (enmascarado)
    val enviarRespuestaURL: Boolean = false, // F3 v1.2 (switch)
    val isDegraded: Boolean = false,
)
// v1.2a (hallazgo H1): ELIMINADO el enum PuenteError y el campo `error` del estado. Sin
// ALLOWLIST_SIN_PAQUETES el enum queda VACÍO (el token blank es un estado legítimo "canal
// abierto" → no hay validación de UI que pueda fallar) — un enum sin entries y su estado de
// error serían código muerto/basura. Los callbacks que limpiaban error se retiran con ellos.
```

Section (Card con):
1. **Campo "Token compartido (opcional)"** (F1 v1.2) — enmascarado con toggle de visibilidad (patrón
   ApiKeySection). Aviso: "inclúyelo como extra `token` en cada comando de Tasker (Send Intent),
   AutoRemote o adb; vacío = canal abierto (sin protección); no se guarda en logs".
2. **Campo "Paquete del receptor de la respuesta (vacío = default `net.dinglisch.android.taskerm`)"**
   (F2) — INTACTO.
3. **Switch "Responder por URL (AutoRemote)"** (F3 v1.2, default OFF) + campo "Key de AutoRemote"
   (enmascarado). Aviso: "envía la respuesta al endpoint de AutoRemote cuando el flag está activo y
   hay key; requiere INTERNET; canal adicional al broadcast".
4. Botones Guardar/Borrar key (patrón existente).

Montaje: `MainActivity`/`MainScreen` — callbacks `onTokenChange`, `onUrlFlagChange` (se retiran
`onAllowlistToggle`/`onPaquetesChange`); el `verticalScroll` (M1) se conserva. Strings actualizados
en `app/src/main/res/values/strings.xml`.

---

## 6. Compatibilidades y casos borde (tabla de decisión v1.2)

| Caso | token config blank | token config no-blank | F2 (setPackage taskerm) | F3 (flag ON + key) | F3 (flag OFF) |
|---|---|---|---|---|---|
| Tasker Send Intent con extra token correcto | ejecuta | **ejecuta** | Tasker Event SÍ recibe | envía URL | no envía |
| Tasker Send Intent sin extra token | ejecuta (abierto) | **token_invalido** | SÍ recibe | envía URL | no envía |
| AutoRemote re-emite con extra token correcto | ejecuta | **ejecuta** | no afecta (no consume) | envía URL | no envía |
| `adb shell am broadcast` con `--es token '<secreto>'` | ejecuta | ejecuta si coincide | n/a (respuesta existe) | envía URL si flag | no envía |
| adb sin token (config no-blank) | — | **token_invalido** | n/a | n/a | n/a |
| Broadcast sin extras (basura) | **silencio** (H3, antes del handler) | silencio | n/a | n/a | n/a |
| Flujo de voz (parser hablado) | **NO afectado** (vía distinta; sin token) | NO afectado | NO afectado | NO afectado | NO afectado |
| Identidad del emisor (getSentFromUid) | — | — | — | **NO usada (H1)** — inobtenible sin opt-in | — |

---

## 7. Estructura de paquetes final v1.2 (service/system/bridge + app/ui/puente)

```
service/system/src/main/kotlin/com/screenassistant/service/system/bridge/
├── TaskerBridgeContract.kt        (edit: +EXTRA_TOKEN, +CODIGO_TOKEN_INVALIDO, +MSG_TOKEN_INVALIDO;
│                                   -CODIGO_ORIGEN_NO_AUTORIZADO, -MSG_ORIGEN_NO_AUTORIZADO,
│                                   -PACKAGE_AUTOREMOTE_PREFIX; F2/F3 intactos)
├── TaskerPayloadExtractor.kt      (edit: +token con default null en extraer() y PayloadTasker)
├── TaskerMessageHandler.kt        (edit: firma handle(extra, token: String?); KDoc B1/H1)
├── TaskerMessageHandlerImpl.kt    (edit: sin verifier; validación token fail-closed)
├── TaskerRespuestaTexto.kt        (intacto)
├── TaskerResponseEmitter.kt       (intacto — la firma NO cambia, F2 vía config)
├── TaskerResponseEmitterImpl.kt   (intacto — F2 v1.1 no se toca)
├── AutoRemoteUrlBuilder.kt        (intacto)
├── UrlSender.kt                   (intacto)
├── HttpUrlSender.kt               (intacto)
├── AutoRemoteUrlCallback.kt       (edit: responderSiAplica(respuestaJson) — flag + key, sin origen)
├── TaskerCommandReceiver.kt       (edit: extra token; sin obtenerOrigenDe; urlCallback sin origen)
├── OrigenTasker.kt                (ELIMINADO — muerto tras B1+H1, §2.6)
└── TaskerOrigenVerifier.kt        (ELIMINADO — muerto tras B1+H1, §2.6)

core/data/src/main/kotlin/com/screenassistant/core/data/util/
├── PuenteConfig.kt                (edit: v2 — tokenCompartido, enviarRespuestaURL;
│                                   -allowlistActivada, -paquetesPermitidos, -normalizarPaquetes)
└── PuenteConfigStore.kt           (edit: claves v2; patrón cifrado+fallback intacto)

app/src/main/java/com/screenassistant/ui/puente/
├── PuenteSettingsViewModel.kt     (edit: campos v2; PuenteError ELIMINADO — v1.2a H1)
└── PuenteSettingsSection.kt       (edit: token + switch URL + key + packageRespuesta; sin manejo de error)

app/src/main/java/com/screenassistant/di/AppModule.kt   (edit: 4 providers — sin verifier)
app/src/main/java/com/screenassistant/MainActivity.kt   (edit: callbacks v2; verticalScroll conservado)
app/src/main/res/values/strings.xml                     (edit: strings v2)
app/src/main/AndroidManifest.xml                        (SIN CAMBIOS — INTERNET ya existe)
service/system/src/test/.../TaskerOrigenVerifierTest.kt (ELIMINADO — 15 tests)
```

**Resumen**: 0 archivos nuevos en producción, 12 editados, 3 eliminados (2 producción + 1 test).
(Nota de lectura del git: "0 nuevos" es relativo al working tree v1.1; frente a HEAD/Lote 6 el
commit de este lote incluye archivos nuevos — PuenteConfig, PuenteConfigStore, AutoRemoteUrlBuilder,
UrlSender, HttpUrlSender, AutoRemoteUrlCallback, PuenteSettingsViewModel, PuenteSettingsSection y
2 suites nuevas — y TaskerResponseEmitterImpl/Test aparecen "modificados" porque la F2 del v1.1
nunca se commiteó por separado.)

---

## 8. Plan de implementación P0–P5 v1.2 (archivos y conteos EXACTOS de tests)

Marco: JUnit 4 + MockK 1.13.12 + coroutines-test 1.9.0, SIN Robolectric ni dependencias nuevas.
Estado actual del lote: 57 tests implementados (gate 752) → v1.2 los reconvierte a **45**.
LECCIÓN QA #7 (registrada): los 15 tests del verifier y 4 del callback eran verdes sobre inputs que
la producción no puede producir (H1) — los tests de capa pura deben modelar lo que el transporte
REAL puede entregar, no contratos ideales; los 57 del lote se revisaron con esa lente y los
sobrevivientes de v1.2 (45) solo prueban decisiones sobre material que el emisor controla (extras).

| Paso | Archivos (touch) | Contenido | Tests (nuevos / editados / eliminados) |
|---|---|---|---|
| **P0** | **edit** `PuenteConfig.kt`, `PuenteConfigStore.kt` | Config v2: -allowlist/paquetes/normalizarPaquetes; +tokenCompartido, +enviarRespuestaURL. Store: claves nuevas; H1 (default taskerm) y patrón cifrado intactos | `PuenteConfigStoreTest` **8 → 8 EDITADOS**: default con prefs vacías (ahora con flag false y token ""); guardar escribe 4 claves (nuevas); roundtrip token/flag; **-2 de normalizarPaquetes** (eliminados); **+2 nuevos**: roundtrip tokenCompartido con caracteres especiales; roundtrip enviarRespuestaURL true/false; fallback flag (supuesto M5 intacto) |
| **P1** | **edit** `TaskerBridgeContract.kt`, `TaskerPayloadExtractor.kt`, `TaskerMessageHandler.kt`, `TaskerMessageHandlerImpl.kt`, `TaskerCommandReceiver.kt`; **eliminar** `OrigenTasker.kt`, `TaskerOrigenVerifier.kt` | F1 v1.2: extra token + fail-closed `token_invalido`; identidad eliminada (B1/H1); seam `handle(extra, token)` | `TaskerMessageHandlerTest` **19 → 22 EDITADOS** (v1.2a H4): **14 revertidos** a `handle(extra, token = null)` (mecánica trivial: se retira el 2º arg OrigenTasker; los de timeout/fallo/eco/cancelación intactos en semántica); **5 de allowlist ELIMINADOS**; **~8 NUEVOS = 6 de token + 2 de trim/case (H4)**: config token + extra correcto → ejecuta (coVerify 1); extra ausente → `token_invalido` 6 claves; extra erróneo → `token_invalido`; rechazo → coVerify(exactly=0) bridge; rechazo sin id → id null; config blank + extra con token → ejecuta (canal abierto); config blank + sin token → ejecuta; **(H4a) extra " misecreto " con espacios alrededor del valor correcto → EJECUTA (el trim del handler normaliza)**; **(H4b) extra "MiSecreto" vs config "misecreto" → `token_invalido` (comparación case-sensitive, sin lowercase)**. `TaskerPayloadExtractorTest` **19 → 22** (**19 INTACTOS** por default param + **3 NUEVOS**: token passthrough; message+cmd+token; token con message presente vacío). `TaskerOrigenVerifierTest` **15 ELIMINADOS** |
| **P2** | — | F2 intacta | `TaskerResponseEmitterTest` **9 INTACTOS** (0 cambios) |
| **P3** | **edit** `AutoRemoteUrlCallback.kt`, `TaskerCommandReceiver.kt` (wiring), `AppModule.kt` (4 providers) | F3 v1.2: flag + key, sin origen | `AutoRemoteUrlCallbackTest` **10 → 9** (**0 INTACTOS — v1.2a H2**): **-4 ELIMINADOS** (key+origen Tasker → no envía; origen null → no envía; variante `.beta` → envía; paquete no-bighugegiraffe → no envía — los 4 dependían del origen, muertos con H1); **6 CONSERVADOS PERO EDITADOS** fijando `enviarRespuestaURL = true` explícito en la config del stub: sin key → no envía y key blank con espacios → no envía (sin flag=true pasarían verdes por el early return `if (!enviarRespuestaURL) return` — falsos positivos que NO ejercitan la rama de key blank; con flag=true ejercitan lo que pretenden); key+AutoRemote → envía, fracaso de red → 1 envío, sender lanza → no propaga y respuesta vacía → 1 envío (sin flag=true FALLARÍAN: esperan envío); **+3 NUEVOS**: flag OFF + key → no envía (comprobación negativa del flag); flag ON + key blank → no envía; flag ON + key con espacios → envía URL con key trimeada. `AutoRemoteUrlBuilderTest` **6 INTACTOS**. `HttpUrlSender` cáscara (0 unit, manual P5) |
| **P4** | **edit** `PuenteSettingsViewModel.kt`, `PuenteSettingsSection.kt`, `MainActivity.kt`, `strings.xml` | UI v2: token + switch URL; PuenteError eliminado (v1.2a H1) | `PuenteSettingsViewModelTest` **10 → 8** (v1.2a H1 — "10 → 10 editados" era imposible): **-4 ELIMINADOS**: T3 (allowlist ON + lista vacía → error, usa `PuenteError.ALLOWLIST_SIN_PAQUETES` y `onAllowlistToggle` — no compila sin el enum); T4 (lista latente, usa `onAllowlistToggle`/`onPaquetesChange`/`paquetesPermitidos`); T9 (allowlist ON + paquetes válidos → sin error, usa `onAllowlistToggle`/`onPaquetesChange`/`state.error`); T10 (editar paquetes limpia el error, usa `PuenteError` — el enum se elimina entero). **6 EDITADOS**: T1 (refresh carga token/flag; se retiran allowlistActivada/paquetesTexto/`assertNull(error)`); T2 (save con `onTokenChange`/`onUrlFlagChange`; se retiran onAllowlistToggle/onPaquetesChange); T5/T6/T7 (compilan tal cual — se cuentan editados por arrastre de la suite: sin cambios o mínimos); T8 (clearKey: `PuenteConfig(autoRemoteKey=...)` sin campos allowlist; verifica que conserva packageRespuesta/token/flag). **+2 NUEVOS (los que QA listó)**: (a) `onUrlFlagChange(true/false)` se refleja en el estado ANTES de save; (b) refresh tras save devuelve token (TRIMeado — cubre el trim del token) y flag persistidos (roundtrip real con el fake STORED-MUTABLE). Section = Compose (sin unit, patrón del repo) |
| **P5** | **edit** `docs/BACKLOG.md` (ADR-015 v1.2 + estado Lote 7) | Cierre: gate + validación manual + estado | Compilation Gate + guion en dispositivo (§9). **O1 vigente**: code review — sin imports de PuenteConfig/PuenteConfigStore fuera de bridge + core:data/util + app/ui/puente; NINGUNA referencia a getSentFromUid/…Package en el código de producción |

**Conteo total v1.2a: 45 tests NUEVOS del lote sobre el baseline de 695** — desglose REAL por archivo
(v1.2a H3; el desglose anterior "8+6+3+6+9+10+3" no correspondía a nada medible):
store 8 · handler **8** (suite 22 − 14 base) · extractor **3** (suite 22 − 19 base) · emitter **3**
(suite 9 − 6 base) · urlBuilder **6** · urlCallback **9** · VM **8** = **45** →
**gate objetivo: 695 + 45 = 740 tests** (era 752). Delta frente a los 57 implementados:
−15 verifier, −5 allowlist-handler, −4 urlCallback-origen, −4 VM (T3/T4/T9/T10), +6 token-handler,
+3 extractor-token, +3 urlCallback-flag, +2 VM (a/b), +2 handler trim/case (H4) = −12 → 57 − 12 = 45.
**Nota de reconciliación del gate (v1.2a H1)**: QA propuso "742 (752−24+12)" asumiendo neto VM = 0;
el conteo físico por archivo da neto VM = **−2** (10 → 8), que se compensa exactamente con el +2 de
H4: 752 − 15 − 5 − 4 − 4 + 6 + 3 + 3 + 2 + 2 = **740**. Sin H4 el gate sería 738 (el "740 sin H4" de
QA solo cuadra con VM = 10, inconsistente con la suite de 8). El gate se verifica con Gradle; el
número de este documento es el conteo físico de tests por archivo. **Archivos**: 12 producción
editados, 3 eliminados (OrigenTasker.kt, TaskerOrigenVerifier.kt, TaskerOrigenVerifierTest.kt), 0
nuevos. **Build/Manifest: SIN cambios** (HttpURLConnection JDK; sin deps nuevas; INTERNET ya declarado).

---

## 9. Validación manual en dispositivo (guion — P5, QA #6 v1.2)

1. **Regresión F2**: build + instalar → Tasker Send Intent (guion Lote 6, paso 2) → Event Broadcast
   Received sigue recibiendo `%respuesta` (default setPackage=taskerm intacto). También con
   `adb shell am broadcast -a com.screenassistant.TASKER_COMMAND --es cmd '<json>' com.screenassistant`.
2. **Humo canal abierto (default)**: sin token en la UI → Tasker y adb funcionan sin token
   (cero regresión — verificación de que blank = abierto).
3. **PRUEBA DE HUMO F1 (token erróneo → token_invalido)**: configurar un token en la UI (p. ej.
   `misecreto`); enviar con token erróneo o sin token →
   `adb shell am broadcast -a com.screenassistant.TASKER_COMMAND --es cmd '<json>' com.screenassistant`
   (sin `--es token`) → respuesta `token_invalido` con 6 claves e id eco; logcat: rechazo sin
   ejecutar el bridge; el token NO aparece en logcat (grep del secreto = 0 resultados).
4. **PRUEBA DE HUMO F1 (token correcto → ejecuta)**:
   `adb shell am broadcast -a com.screenassistant.TASKER_COMMAND --es cmd '<json>' --es token 'misecreto' com.screenassistant`
   → la acción se ejecuta y la respuesta es el JSON de 6 claves normal (estado ok o error del puente,
   NUNCA token_invalido). Con Tasker Send Intent: añadir extra `token` con variable `%token`.
5. **F3 flag OFF (comprobación negativa)**: con key configurada y flag OFF → tras un comando no
   aparece nada en AutoRemote (log: canal URL inerte). **F3 flag ON**: activar flag → comando vía
   AutoRemote (o el mismo comando con key) → la respuesta aparece en AutoRemote (JSON de 6 claves).
   flag ON + sin key → no envía. Avión/red cortada → broadcast local intacto + log de fallo, sin crash.
6. **Privacidad F2 (comprobación negativa, intacta)**: con default taskerm, una app de log de
   broadcasts NO recibe `TASKER_RESPONSE`; dejar el campo vacío NO abre el broadcast (blank se
   materializa a taskerm en `cargar()`).
7. Notas de latencia/quoting heredadas (H11 del Lote 6) aplican igual.

---

## 10. ADR-015 v1.2 — ENMIENDA DE LA ENMIENDA (historial preservado; fuente del veto: §0.1–0.2)

> **ADR-015 (código)** — v1.1 (histórico, APROBADO CON CAMBIOS y luego VETADO): puente Tasker Fase 3A
> con allowlist real por paquete (F1, origen vía identidad del remitente), privacidad de respuesta
> (F2, setPackage) y URL callback de AutoRemote (F3, prefijo de paquete). Implementado y gate verde
> (752 tests) con enmienda de implementación (API pública getSentFromUid/getSentFromPackage API 34+
> en lugar de getSentUid/getSentPackage API 28 no compilables). **SUPERSEDIDA en F1 y F3 por la
> enmienda v1.2; F2 vigente sin cambios.**
>
> **VETO (02/08/2026, QA final + Supervisor) — razones con fuentes:**
> - **B1 (orden)**: `goAsync()` anula `mPendingResult` (AOSP BroadcastReceiver.java, android15-release);
>   `getSentFromUid()/getSentFromPackage()` leen `mPendingResult` → llamadas DESPUÉS de goAsync
>   devuelven SIEMPRE -1/null. El código implementado lo hacía así (TaskerCommandReceiver.kt:58-59).
> - **H1 (opt-in, invalida el diseño)**: la identidad del remitente solo se expone si el EMISOR hizo
>   `BroadcastOptions.setShareIdentityEnabled(true)` — default FALSE (BroadcastRecord.java:489;
>   BroadcastQueueModernImpl.java:1190-1220 pasa `r.shareIdentity ? r.callingUid : INVALID_UID`).
>   Doc oficial de Android, literal: "if the sender opted in to sharing identity". Tasker Send Intent,
>   AutoRemote y adb shell NO activan el opt-in (no configurable) → la identidad NO es obtenible con
>   API pública en nuestro escenario, ni antes ni después de goAsync. Los 57 tests del lote ejercitan
>   la capa pura con inputs que producción nunca produce (gate insuficiente: sin pruebas sobre el
>   transporte real no se detecta; lección QA #7).
>
> **ENMIENDA v1.2 (rediseño F1/F3 ordenado por el Orquestador):**
> - **P1' (origen ELIMINADO)**: se eliminan `OrigenTasker`, `obtenerOrigenDe`, `TaskerOrigenVerifier`
>   y sus 15 tests (falsa seguridad: decidir sobre un input que producción nunca produce). El seam
>   pasa a `TaskerMessageHandler.handle(extra: String, token: String?)` — revierte la enmienda T6 de
>   v1.1 y vuelve al espíritu de ADR-014/T6 (material de transporte por parámetro). Regla documentada:
>   cualquier futura lectura de identidad debe ir en onReceive ANTES de goAsync (B1).
> - **P2' (token compartido, F1)**: autenticación de canal por extra `token` del INTENT (NO campo del
>   contrato JSON: el codec decodifica con ignoreUnknownKeys=false y tocar la base sealed implicaría
>   22 overrides + validarBase + 95 tests de Fase 1 — EnvelopeSerializationTest 23, CodecTest 70,
>   CorrespondenciaWireTest 2; **cero impacto en core/domain y en los 644 tests de Fase 1**).
>   `EXTRA_TOKEN` en TaskerBridgeContract; `TaskerPayloadExtractor.extraer(message, cmd, token = null)`
>   (19 tests intactos); validación en el handler: `tokenCompartido` no-blank → exige igualdad exacta
>   (trim) → DENY = código NUEVO aditivo `token_invalido` (6 claves, id eco, bridge NO ejecutado,
>   respuesta emitida — invariante H3). Blank = canal abierto (default, cero regresión, documentado
>   "no protegido"). El token NO se loguea; el flujo de voz NO lo usa. Seguridad honesta: el token
>   viaja en claro (modelo AutoRemote keys); F2 sigue protegiendo la respuesta; F5 sigue siendo el
>   control estructural pendiente.
> - **P3 (F2, INTACTA)**: setPackage con default taskerm materializado en cargar() — sin cambios
>   (9 tests intactos).
> - **P4' (URL callback por flag, F3)**: se elimina la decisión `origen.paquete.startsWith(...)`
>   (muerta con H1). `AutoRemoteUrlCallback.responderSiAplica(respuestaJson: String)` decide SOLO por
>   config: `enviarRespuestaURL == true` (default OFF — cero llamadas URL sorpresa) && key no-blank.
>   La key y el flag son independientes del emisor real. Resto del canal intacto (builder, sender,
>   fire-and-forget, best-effort sin reintento).
> - **P5' (config v2)**: `PuenteConfig` = packageRespuesta, autoRemoteKey, **tokenCompartido**,
>   **enviarRespuestaURL**; se eliminan allowlistActivada/paquetesPermitidos/normalizarPaquetes.
>   `PuenteConfigStore` con las 4 claves nuevas (patrón ApiKeyProvider intacto, core:data/util).
>   Providers: 4 (sin verifier).
> - **P6' (tests v1.2, CORREGIDO por la enmienda v1.2a H1–H4)**: 45 tests del lote (57 − 15 verifier
>   − 5 allowlist-handler − 4 urlCallback-origen − 4 VM + 6 token-handler + 3 extractor-token + 3
>   urlCallback-flag + 2 VM + 2 handler trim/case); gate objetivo **740**. Eliminados:
>   TaskerOrigenVerifierTest (15) + 4 tests del VM (T3/T4/T9/T10). Editados: store 8, handler **22**
>   (14 revertidos + 6 token + 2 trim/case — H4), extractor 22 (19 intactos + 3), urlCallback **9**
>   (**0 INTACTOS**: 6 conservados editados con flag=true explícito + 3 nuevos — H2), VM **8**
>   (6 editados + 2 nuevos — H1). Intactos: emitter 9, urlBuilder 6. Cáscaras (receiver, HttpUrlSender,
>   store) → validación manual (§9). PuenteError ELIMINADO (v1.2a H1: enum vacío + campo error muerto).

---

## 11. Fuera de alcance (Fase 3B / próximos lotes) — v1.2

- **F5**: confirmación humana de acciones irreversibles (llamada/SMS) — sigue pendiente; es el
  control estructural que cierra el hueco real (el token reduce el abuso casual, no lo elimina).
- **Identidad del emisor**: obtenible solo con opt-in del emisor (setShareIdentityEnabled), que
  Tasker/AutoRemote/adb no activan y no es configurable desde esas apps → documentado como
  imposible con API pública en nuestro escenario (H1); la nota de "deprecación getSentUid en API 36"
  queda obsoleta (getSentFromUid YA es la API actual, y es inútil sin opt-in).
- URL callback con parámetro `sender`/round-trip al escritorio (requiere config AutoRemote del usuario).
- Reintentos/cola de respuestas URL (WorkManager) — YAGNI en ecosistema 1 dispositivo.
- Modo "respuesta SOLO por URL" (exclusivo) — YAGNI; canales siempre paralelos.
- Modo "respuesta global" (sin setPackage) — no alcanzable: el campo vacío se materializa a taskerm
  (H1); requeriría un centinela explícito en la UI.
- Comparación de token en tiempo constante (MessageDigest.isEqual) — descartada por honestidad: el
  token viaja en claro; un timing attack no añade superficie real (§2.5).

---

## 12. Enmienda QA shift-left v1.2a — hallazgos H1–H4 aplicados (02/08/2026)

QA shift-left revisó el diseño v1.2 contra el repo (752 tests contados físicamente; descomposición de
los 57 del lote verificada; baseline 695 ✅) y lo **APROBÓ CON CAMBIOS**. Los 4 hallazgos, todos
documentales (sin cambios de arquitectura — el diseño de fondo de v1.2 queda intacto):

- **H1 (MAYOR — rompía el gate)**: "VM 10 → 10 editados" era imposible: `PuenteSettingsViewModelTest`
  T9/T10 usan `onAllowlistToggle`/`onPaquetesChange` y `PuenteError.ALLOWLIST_SIN_PAQUETES` (todo
  eliminado en v2 → NO compilan) y T8 usa `PuenteConfig(allowlistActivada=…, paquetesPermitidos=…)`
  (campos eliminados → exige adaptación; verificado en el archivo: también T1 y T2 usan símbolos
  eliminados). Corrección aplicada en §8 P4: **VM = 6 editados + 4 ELIMINADOS (T3/T4/T9/T10) + 2
  NUEVOS = 8**; los 2 nuevos son (a) `onUrlFlagChange` reflejado en el estado antes de save y (b)
  refresh tras save con token trimeado y flag persistidos (roundtrip real). **Decisión tomada**:
  `PuenteError` y el campo `error` del `PuenteUiState` se **ELIMINAN** (enum vacío sin entries +
  estado de error sin emisores posibles = basura; el token blank es estado legítimo, no hay
  validación de UI que fallar) — reflejado en §5.3 y §7. **Consecuencia aritmética**: el neto del VM
  es **−2**, no 0; la fórmula de QA "752−24+12=740" solo cuadra con VM=10 → con el conteo físico el
  gate sin H4 sería 738.
- **H2 (MAYOR — falsos positivos)**: los 6 tests "INTACTOS" del callback pasaban por el camino
  equivocado: con el flag default OFF, "sin key → no envía" (T1/T2) quedan verdes sin tocarlos pero
  por el early return `if (!enviarRespuestaURL) return` (no ejercitan la rama de key blank), y los 4
  que esperan envío (T3/T8/T9/T10) FALLARÍAN sin flag=true. Corrección aplicada en §8 P3:
  **"0 INTACTOS"** — los 6 conservados se editan fijando `enviarRespuestaURL = true` explícito en el
  stub (los de no-envío por key blank para no mentir, los de envío para no fallar); el conteo 9 no
  cambia (6 editados + 3 nuevos). Eliminada la contradicción interna del doc (§8 decía "INTACTOS",
  §10 decía "6 adaptados + 3").
- **H3 (MENOR — desglose incoherente)**: la línea "8 store + 6 handler + 3 emitter + 6 urlBuilder + 9
  urlCallback + 10 VM + 3 extractor = 45" no correspondía a ninguna métrica real (handler 20,
  extractor 22, emitter 9, VM 10). Corrección aplicada en §8: desglose real por archivo (store 8,
  handler 8, extractor 3, emitter 3, urlBuilder 6, urlCallback 9, VM 8 = 45) + el delta oficial
  57 − 15 − 5 − 4 − 4 + 6 + 3 + 3 + 2 + 2 = 45.
- **H4 (MENOR — cobertura)**: la semántica `token?.trim() != tokenConfig.trim()` (case-sensitive) no
  tenía tests explícitos. Corrección aplicada en §8 P1: **+2 tests** → (H4a) extra " misecreto " con
  espacios alrededor del valor correcto → EJECUTA (el trim normaliza); (H4b) "MiSecreto" vs config
  "misecreto" → `token_invalido` (case-sensitive, sin lowercase). `TaskerMessageHandlerTest` pasa de
  20 a **22** (14 base + 6 token + 2).

**Gate objetivo final (con H1–H4): 740** — desglose físico verificable en §8; la reconciliación del
"742 recomendado" está documentada en la nota de conteo (§8): el −2 neto del VM se compensa con el
+2 de H4. El gate de Gradle (`testDebugUnitTest :core:domain:test :app:assembleDebug --rerun-tasks`)
es el árbitro final; cualquier desviación se reporta con el conteo por archivo.

## 13. Ronda UI/UX (02/08/2026) — APROBADO CON CAMBIOS, aplicados y gate re-verificado

UI/UX revisó `PuenteSettingsSection` contra el patrón ApiKeySection (Material Design 3 +
accesibilidad) y aprobó la v1.2a con cambios, todos aplicados por el Desarrollador:

- **C1 (requerido — accesibilidad)**: fila del switch clicable — `Row` con
  `Modifier.toggleable(value, role = Role.Switch, onValueChange)` + `Switch(onCheckedChange = null)`
  (evita doble toggle): touch target >48dp y semántica agrupada para TalkBack.
- **C2 (requerido — lint i18n)**: placeholder del paquete hardcodeado → `stringResource`
  (`puente_package_respuesta_placeholder` = `net.dinglisch.android.taskerm`).
- **C3 (requerido — MD3)**: `Modifier.fillMaxWidth()` al Row del switch (switch alineado al final).
- **R1**: captions de token y privacidad movidos a `supportingText` del `OutlinedTextField`
  (asociación semántica campo↔caption).
- **R2**: `AlertDialog` de confirmación al "Borrar key" (patrón ApiKeySection M7; strings
  `puente_key_clear_dialog_*`).
- **R3**: badge de estado del canal — "Protegido" (`primary`) si `tokenCompartido.isNotBlank()`,
  si no "Canal abierto (sin token)" (`onSurfaceVariant`); strings `puente_channel_*`.
- **R4**: label del token sin "(opcional)" (la opcionalidad la comunica el caption).
- **R5**: caption del switch antepone "Desactivado por defecto. ".

`PuenteSettingsViewModel` sin cambios (ningún ítem toca contrato). Gate re-verificado tras la
ronda: **BUILD SUCCESSFUL 740/0 (03/08/2026)**. Sugerencia de UI/UX no aplicada (decisión):
derivar el badge del último guardado en lugar de las ediciones sin guardar (S1 del Supervisor,
higiene opcional).
