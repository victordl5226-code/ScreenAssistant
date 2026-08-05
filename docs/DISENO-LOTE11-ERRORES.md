# Diseño Lote 11 — Sanitización de mensajes de error restantes (M3 streaming + puente Tasker + constante M1)

Cierre: Lotes 8-10 (commits 6ada101, 0c24bac, ca16e21) · Gate actual: **791 tests / 0 fallos**.
Criterio de este lote (precedente Lote 10): **todo se verifica contra el código real antes de decidir**.
Alcance: SOLO diseño e investigación — el Desarrollador implementa; no se toca código de producción en este documento.

---

## 0. Resumen ejecutivo

| Ítem | Veredicto de verificación | Decisión |
|---|---|---|
| **M3 streaming** | El Lote 8 pagó la ESTRUCTURA (IOException → emit, no propagar). El residuo son los LITERALES `"Error en streaming: ${e.message}"` en los 2 caminos (L280 emit, L287 flowOf). Sin consumidor en producción (latente). | **SANEAR el literal** (mensaje genérico idéntico al catch de `sendMessage` + Log.w). NO tocar la estructura (precedente respetado). |
| **Puente Tasker** | `e.message` en 2 call sites (handler L68, bridge L71) + **1 más descubierto**: `t.message` en el catch H9 del receiver (L77). **CRÍTICO**: el `mensaje` del wire se HABLA por TTS por defecto (`hablar = !silencioso`; `TaskerRespuestaTexto.textoDe` es el canal humano H7) → el e.message llega al OÍDO del usuario. | **SANEAR los 3** (mensaje genérico `"Error: No pudo completarse la acción."` + Log.w con detalle). El canal NO es solo de diagnóstico: tiene voz. `fallo_accion`/reason verbatim de ADR-B7 quedan INTACTOS (ya saneados de fábrica por M10). |
| **Constante M1** | Literal duplicado idéntico en `SystemCommandParser.kt:233` y `TimerAction.kt:19`. | `const val TIMER_ERROR_MENSAJE` en el companion de `SystemCommand` (fuente única, junto a `TIMER_MIN/MAX_MINUTOS`). |
| **EXTRA-1 (descubierto)** | `SystemCommandBridgeImpl.kt:69` `catch (e: Exception)` TRAGA `CancellationException` — violación B5 (el handler ya la re-lanza; el bridge no). Con `withTimeoutOrNull(10s)`, la cancelación por timeout puede tragarse y la corrutina sigue trabajando tras el timeout. | **Añadir rethrow** de CancellationException en el bridge (mismo contrato que TaskerMessageHandlerImpl L58-61). |

Gate objetivo: **793 tests / 0 fallos** (791 + 2 nuevos; 4 editados que siguen contando).

---

## 1. Verificación del estado real (investigación sobre código)

### 1.1 M3 — streaming (GeminiRepositoryImpl.kt)

`core/data/.../remote/GeminiRepositoryImpl.kt:264-289`:

```kotlin
override fun streamMessage(message: String): Flow<String> {
    val apiKey = currentApiKey()
    if (apiKey.isBlank()) return flowOf("No pude inicializar el modelo de IA.")   // genérico, OK
    return try {
        currentChat(apiKey)?.sendMessageStream(message)?.mapNotNull { it.text }
            ?.catch { e: Throwable ->
                if (e is java.io.IOException) {
                    emit("Error en streaming: ${e.message}")                       // L280 ← CRUDO
                } else {
                    throw e                                                       // contrato: resto se re-lanza
                }
            }
            ?: flowOf("No pude inicializar el modelo de IA.")
    } catch (e: Exception) {
        flowOf("Error en streaming: ${e.message}")                                // L287 ← CRUDO
    }
}
```

**Veredicto**:
- **Pagado (Lote 8, BACKLOG L14)**: el `.catch` con `emit` en lugar de propagar el IOException durante la recolección. La estructura "emit vs rethrow" EXISTE y es correcta (incluida la re-lanzada del resto, contrato del repo).
- **Residuo (Lote 11)**: los DOS literales `"Error en streaming: ${e.message}"` (L280 y L287) filtran internals del SDK al usuario.
- **Sin consumidor**: grep `streamMessage` = solo interfaz (`core/domain/repository/GeminiRepository.kt:9`), impl y test. API latente — el riesgo de regresión observable es nulo, pero el contrato debe quedar limpio para el consumidor futuro (el overlay es el destino natural).
- **El test PIN el leak**: `GeminiRepositoryTest.kt:581` `assertEquals(listOf("Error en streaming: red caída"), values)` — el test congeló el mensaje crudo.
- El catch de CONSTRUCCIÓN (L287) no tiene test propio (el test existente cubre solo la recolección).

### 1.2 Puente Tasker (handler + bridge + receiver)

Call sites verificados (grep `e\.message` + barrido `t\.message`/`\w\.message ?:`):

| # | Archivo:línea | Código | Canal que recibe el mensaje |
|---|---|---|---|
| 1 | `TaskerMessageHandlerImpl.kt:68` | `"Error: ${e.message ?: "Error desconocido"}"` (fallo_ejecucion) | wire `mensaje` → **TTS + broadcast Tasker** |
| 2 | `SystemCommandBridgeImpl.kt:71` | idem | wire `mensaje` → **TTS + broadcast** |
| 3 | `TaskerCommandReceiver.kt:77` (H9) | `"Error: ${t.message ?: "Error desconocido"}"` | wire `mensaje` → **TTS + broadcast** |

**Hallazgo que decide**: la respuesta del wire NO es un canal puramente máquina:
- `TaskerResponseEmitterImpl.kt:48-50`: `if (hablar) { TaskerRespuestaTexto.textoDe(respuestaJson)?.let { tts.speak(it) } }`.
- `TaskerCommandReceiver.kt:65`: `hablar = !payload.silencioso` → **el TTS se activa POR DEFECTO** (solo el contexto `"silencioso"` del wire lo suprime).
- `TaskerRespuestaTexto.kt:27-35`: en el camino de error, `textoDe` devuelve el campo `mensaje` — **el texto humano (H7) ES el mensaje del wire**.

Conclusión: el `e.message` (SQL de Room, clases internas, rutas) puede **leerse en voz alta** por el TTS del asistente. ADR-009 ("los mensajes no exponen excepciones crudas") aplica directamente.

**Qué puede contener e.message hoy** (análisis de caminos):
- `systemAction.execute` ya NO lanza no-cancelación desde M10 (catch en `SystemActionHandler.kt:77-84` → `ActionResult.Error("No pudo completarse la acción.")`). Los catches del puente son casi código muerto → red de defensa (invariante ADR-013 "nunca excepciones hacia Tasker"). Su mensaje debe ser igual de limpio que el del canal de voz.
- `mapToSystemCommand` puede lanzar `IllegalStateException("valor de volumen sin validar")` — inalcanzable por construcción (Fase B' valida) pero con mensaje interno.
- `CommandClassifier`/`codec` son puros sin throw.
- **No es problema de privacidad inter-proceso** (el emisor es el mismo dispositivo) — es problema de CANAL HUMANO: el TTS es público (voz en alto), no lo lee solo el desarrollador.

**Qué NO se toca** (decisión ADR-B7 respetada):
- `SystemCommandBridgeImpl.kt:66` `"Error: ${result.reason}"` (ActionResult.Error): el reason ya está saneado de fábrica por M10 → verbatim de mensaje saneado. Intacto.
- `SystemCommandBridgeImpl.kt:55-59` `fallo_accion` con mensaje verbatim de la acción ("Error: No encontré la app"): mensajes AUTORADOS por las acciones (ADR-009), legítimos en ambos canales. Intacto.
- Mensajes estáticos del codec (`json_invalido`, `longitud_excedida`, etc.): estáticos, sin datos crudos. Intactos.

**EXTRA-1 (B5 gap en el bridge)**: `SystemCommandBridgeImpl.kt:69` `catch (e: Exception)` captura también `CancellationException` (es subclase de Exception). TaskerMessageHandlerImpl L58-61 ya la re-lanza correctamente; el bridge NO. Con el `withTimeoutOrNull(10s)` del handler, la cancelación por timeout puede ser tragada por el bridge → la corrutina continúa tras el timeout (side effects post-cancelación). Fix: rethrow antes del catch genérico (mismo patrón que el handler).

### 1.3 Constante M1

Verificado: literal IDÉNTICO en `SystemCommandParser.kt:233` (rama 3, guard de rango) y `TimerAction.kt:19` (guard defensivo). Tests que lo fijan: `SystemCommandParserTest.kt:664,672,680` y `TimerActionTest.kt:102,110` (literales — no se tocan, el texto visible es el requerimiento).
El wire NO usa este literal: `SystemCommandJsonCodec` emite `"Error: Valor invalido: minutos (X)."` (formato `valor_invalido` con el valor recibido — diagnóstico de protocolo). **No unificar**: son mensajes de canales distintos (regla para el usuario final vs valor inválido del protocolo).

### 1.4 Inventario completo de `e.message` (grep global, 17 matches reales)

| Categoría | Call sites | Decisión |
|---|---|---|
| **Al USUARIO (voz/overlay)** | `GeminiRepositoryImpl.kt:280, 287` (streaming, latente) | **SANEAR (M3)** |
| **Al wire del puente → TTS humano + broadcast** | `TaskerMessageHandlerImpl.kt:68`, `SystemCommandBridgeImpl.kt:71`, `TaskerCommandReceiver.kt:77` (`t.message`) | **SANEAR (puente)** |
| **Solo a logcat local (seguro, precedente M7/M10)** | `PuenteConfigStore.kt:72,100` · `EncryptedPrefsStore.kt:34,41,49,55,61,74,101` | **INTACTO** (log local es el destino del detalle) |
| Falsos positivos / comentarios | `AppModule.kt:82` (`messageDao`), `SystemActionHandler.kt:78` (comentario M10), `HttpUrlSender.kt:28` (comentario M7), `SystemActionHandlerTest.kt:92` (comentario) | — |

Los `result.message` de `ActionResult` (parser, bridge, GeminiRepositoryImpl) son mensajes autorados por acciones — no son excepciones crudas.

---

## 2. Diseño del Lote 11

### 2.1 M3 — Streaming: sanitizar el literal (NO la estructura)

**Decisión**: el Lote 8 ya pagó "IOException → emit estructurado" (emit en lugar de propagar + rethrow del resto). El residuo es SOLO el literal. No se introduce sealed class de error: `Flow<String>` sin consumidor + YAGNI; la estructura actual cumple el contrato.

**Cambios en `GeminiRepositoryImpl.kt`** (core:data/remote):
1. Extraer constante privada en el companion de la clase (o top-level privado del archivo): `FALLO_TECNICO_GENERICO = "He tenido un problema técnico momentáneo. Inténtalo de nuevo en un segundo."` — el MISMO texto que el catch de `sendMessage` (L259). Esto elimina la deriva entre los dos caminos del repo.
2. L280: `emit(FALLO_TECNICO_GENERICO)` + `Log.w("GeminiRepository", "Error en streaming", e)` (detalle a logcat, precedente M10; core:data ya loguea — EncryptedPrefsStore).
3. L287: `flowOf(FALLO_TECNICO_GENERICO)` + `Log.w` idéntico.
4. L259 (sendMessage): usar la misma constante (1 línea, elimina el duplicado).

**Tests (`GeminiRepositoryTest.kt`)**:
- EDITAR L581: `assertEquals(listOf("He tenido un problema técnico momentáneo. Inténtalo de nuevo en un segundo."), values)` — el leak "red caída" desaparece del assert.
- +1 NUEVO: catch de CONSTRUCCIÓN (hoy sin cobertura): `coEvery { chatMock.sendMessageStream(any()) } throws IOException` → `repo.streamMessage("hola").toList()` == lista de 1 con el genérico (el try exterior cae al flowOf).

### 2.2 Puente Tasker: SANEAR (decisión con justificación)

**Decisión**: SANEAR los 3 call sites. Justificación:
1. **El wire tiene voz**: `textoDe` + `tts.speak` con `hablar = !silencioso` (por defecto) → el `mensaje` se OYE. ADR-009 (canal humano) aplica; "el emisor es el dueño del teléfono" NO blinda: el TTS es voz en alto, no lectura privada del desarrollador.
2. **Casi código muerto**: M10 ya saneó la fuente (`SystemActionHandler`); los catches son red de defensa del invariante ADR-013 — su salida debe ser tan limpia como la del canal de voz.
3. **Cero pérdida de diagnóstico**: el detalle va a Log.w local (precedente M10/M7) y el código máquina `fallo_ejecucion` permanece en el wire (los guiones Tasker ramifican por `%error`, no por `%mensaje`).
4. **Coherencia de ADR-B7**: el "verbatim" declarado cubre el reason de ActionResult.Error (que ya es un mensaje saneado tras M10) y `fallo_accion` (mensajes autorados). Los catches con e.message NUNCA estuvieron cubiertos por esa decisión — son el mismo residuo que M10 cerró en el canal de voz.

**Cambios** (todo en `service:system/bridge`):
1. `TaskerBridgeContract.kt`: nueva constante `MSG_FALLO_EJECUCION = "Error: No pudo completarse la acción."` junto a `MSG_TOKEN_INVALIDO` (mismo texto humano que el canal de voz M10 + prefijo ADR-009; KDoc con la decisión Lote 11). Fuente única para los 3 call sites.
2. `TaskerMessageHandlerImpl.kt:62-69`: catch → `Log.w("TaskerMessageHandler", "Fallo inesperado del puente", e)` + `MSG_FALLO_EJECUCION`. El `?: "Error desconocido"` desaparece (el genérico cubre null y no-null).
3. `SystemCommandBridgeImpl.kt:69-73`: **añadir ANTES** `catch (e: CancellationException) { throw e }` (EXTRA-1, contrato B5 — el mismo patrón que el handler L58-61) + catch Exception → `Log.w("SystemCommandBridge", "Fallo inesperado ejecutando ${command.javaClass.simpleName}", e)` + `MSG_FALLO_EJECUCION`.
4. `TaskerCommandReceiver.kt:69-80` (H9): catch Throwable → `Log.w("TaskerCommandReceiver", "Violación del contrato del handler", t)` + `MSG_FALLO_EJECUCION`. Cáscara Android sin tests (política "Notas de proceso": validación manual) — el KDoc H9 ya lo declara; actualizar el comentario con la referencia al mensaje saneado.

**Tests**:
- `TaskerBridgeTest.kt:291-299` (`excepcion en execute devuelve fallo_ejecucion con el mensaje`): EDITAR assert L298 `"Error: boom"` → `"Error: No pudo completarse la acción."`.
- `TaskerMessageHandlerTest.kt:111-122`: EDITAR L119 `"Error: boom"` → genérico.
- `TaskerMessageHandlerTest.kt:135-142` (`mensaje nulo → generico`): EDITAR L141 `"Error: Error desconocido"` → genérico (ahora null y no-null convergen al mismo texto; el test conserva su valor: cubre el caso null).
- `TaskerMessageHandlerTest.kt:354-374` (cancelación del scope): INTACTO — verifica que el handler propaga.
- +1 NUEVO en `TaskerBridgeTest.kt`: `CancellationException en execute se propaga sin fallo_ejecucion` — `coEvery { systemAction.execute(any()) } throws CancellationException("cancel")` → `assertFailsWith<CancellationException> { handle(...) }` (EXTRA-1).

### 2.3 Constante M1 — diseño de 1 línea

`SystemCommand.kt` companion (junto a `TIMER_MIN_MINUTOS`/`TIMER_MAX_MINUTOS`, que es el KDoc del invariante):

```kotlin
// M1 (Lote 11): mensaje del invariante del temporizador — fuente única compartida
// por el parser (rama 3, guard de rango) y TimerAction (guard defensivo). El wire
// NO lo usa: su formato es "Error: Valor invalido: minutos (X)." (diagnóstico de
// protocolo con el valor recibido, canal distinto).
const val TIMER_ERROR_MENSAJE: String = "Error: La duración debe estar entre 1 minuto y 24 horas."
```

- `SystemCommandParser.kt:233` → `return SystemCommand.TIMER_ERROR_MENSAJE`
- `TimerAction.kt:19` → `return SystemCommand.TIMER_ERROR_MENSAJE`
- Tests: 0 cambios (los 5 asserts fijan el literal — el texto visible es el requerimiento; la constante es la fuente de producción).

### 2.4 Fuera de alcance (declarado, para QA)

- Logs locales (`EncryptedPrefsStore`, `PuenteConfigStore`): INTACTOS — el log local es el destino del detalle (precedente M7/M10).
- `fallo_accion` verbatim y `ActionResult.Error` verbatim del bridge: INTACTOS (ADR-B7).
- Mensajes estáticos del codec: INTACTOS.
- No se toca el wire de respuesta (6 claves, códigos aditivos) — solo el TEXTO del mensaje en el camino `fallo_ejecucion` excepcional.

---

## 3. Archivos exactos, orden de ejecución y gate

### 3.1 Archivos tocados

| Módulo | Archivo | Cambio |
|---|---|---|
| core:domain | `model/SystemCommand.kt` | +1 constante (M1) |
| core:domain | `usecase/SystemCommandParser.kt` | L233 → constante |
| service:system | `action/TimerAction.kt` | L19 → constante |
| core:data | `remote/GeminiRepositoryImpl.kt` | constante privada + L259/280/287 + Log.w |
| core:data (test) | `remote/GeminiRepositoryTest.kt` | 1 editado + 1 nuevo |
| service:system | `bridge/TaskerBridgeContract.kt` | +1 constante MSG_FALLO_EJECUCION |
| service:system | `bridge/TaskerMessageHandlerImpl.kt` | L68 → constante + Log.w |
| service:system | `bridge/SystemCommandBridgeImpl.kt` | +rethrow CancellationException + L71 → constante + Log.w |
| service:system | `bridge/TaskerCommandReceiver.kt` | L77 → constante + Log.w (sin test) |
| service:system (test) | `bridge/TaskerBridgeTest.kt` | 1 editado + 1 nuevo |
| service:system (test) | `bridge/TaskerMessageHandlerTest.kt` | 2 editados |
| docs | `BACKLOG.md` + este documento | cierre del lote |

### 3.2 Orden de ejecución

1. **M1 (constante)** ← independiente, 3 archivos de producción, 0 tests. PRIMERO por ser el más barato.
2. **M3 (streaming)** ← core:data + 1 editado + 1 nuevo. Independiente de 1 y 3.
3. **Puente** ← service:system/bridge (3 call sites + EXTRA-1) + 3 editados + 1 nuevo. ÚLTIMO por tocar el wire (si algo se tuerce, M1/M3 ya están cerrados).
4. **Gate + cierre** (BACKLOG: estado Lote 11 + nota en ADR-B7/KDoc sobre el mensaje saneado del camino excepcional).

### 3.3 Delta de tests y gate

- Base: 791. Editados (no cambian el conteo): GeminiRepositoryTest 1, TaskerBridgeTest 1, TaskerMessageHandlerTest 2.
- Nuevos: +1 (streaming construcción), +1 (CancellationException bridge) → **gate objetivo 793 / 0 fallos**.

### 3.4 Riesgo

| Ítem | Riesgo | Mitigación |
|---|---|---|
| Puente (mensaje observable del wire) | BAJO-MEDIO: un guion Tasker que parsea `%mensaje` literal de `fallo_ejecucion` vería texto distinto (el código `%error` no cambia; `fallo_accion` intacto). | Cambio documentado en BACKLOG + KDoc de la constante; el camino excepcional es casi código muerto (M10 lo saneó aguas arriba). |
| EXTRA-1 (rethrow B5) | BAJO: comportamiento correcto de corrutinas; el handler ya lo hace. | Test nuevo que lo fija. |
| M3 | NULO observable (sin consumidor); contrato para el futuro. | Test editado + test nuevo de construcción. |
| M1 | NULO (misma string). | Tests existentes fijan el literal. |

### 3.5 Verificaciones de cierre (QA shift-left)

- grep `e\.message` en `src/main` → **0 resultados** (los 9 matches restantes son `Log.w(…, e)` locales de EncryptedPrefsStore/PuenteConfigStore, comentarios y falsos positivos `actionResult.message`/`result.message`).
- grep `t\.message` → **0 resultados**.
- grep `"Error en streaming"` → **0 resultados** en caminos al usuario (solo Log.w con detalle); `"Error desconocido"` → **0 en src/main** (se sanó `SpeechToTextManager.kt:43` → "Error desconocido al escuchar." — hallazgo del Supervisor en el cierre, fuera del inventario §1.4).
- `TaskerBridgeTest` / `TaskerMessageHandlerTest` / `GeminiRepositoryTest` verdes.
- Smoke en dispositivo: comando Tasker con acción que falle en ejecución (p.ej. vía `adb shell am broadcast` con payload) → el TTS dice "Error: No pudo completarse la acción." y logcat `TaskerMessageHandler`/`SystemCommandBridge` muestra el detalle. Streaming: sin consumidor → cobertura por tests (documentado).

### 3.6 Desviaciones del diseño (registro)

- **Decisión A (B5, instrucción del Orquestador tras QA P2-2)**: `sendMessage` y el catch de CONSTRUCCIÓN de `streamMessage` en `GeminiRepositoryImpl` tragaban `CancellationException` (solo la recolección re-lanzaba vía `else throw e`). Añadidos 2 `catch (e: CancellationException) { throw e }` + 2 tests nuevos (CE propagada en sendMessage y en construcción). El EXTRA-1 del puente demostró que B5 es in-scope cuando se detecta; la regla del repo exige re-lanzar siempre.
- **Gate 793 → 795**: `GeminiRepositoryTest` tenía 1 test editado + **3 nuevos** (construcción, CE sendMessage, CE construcción) en vez de +1; `TaskerBridgeTest` +1 (CE bridge). Total 791 + 4 = **795** (core:data 46→49, service:system 231→232). El "793" de §3.3 no contaba los 2 tests de la decisión A.
- **`TAG` const** usado en los `Log.w` nuevos de GeminiRepositoryImpl (el diseño no lo especificaba).
- **Saneo extra**: `SpeechToTextManager.kt:43` interpolaba un código Int crudo del SpeechRecognizer al canal visual del overlay ("Error desconocido: $error") → "Error desconocido al escuchar." (1 línea; no pined por ningún test).
