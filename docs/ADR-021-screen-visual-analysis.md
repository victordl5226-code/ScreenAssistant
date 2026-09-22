# ADR-021: Análisis Visual de Pantalla en Comandos Directos

**Estado**: Propuesto  
**Fecha**: 2026-08-24  
**Decisor**: Arquitecto  
**Contexto**: O8 — Captura de screenshot en comandos directos

---

## Contexto

El usuario quiere decir "¿qué hay en mi pantalla?" y obtener un análisis **visual** de la captura, no solo el texto de accesibilidad que el `ScreenContextService` extrae continuamente.

### Estado previo (Lotes 8–11)

- **B3 (Lote 8)**: `ScreenCaptureProvider` (interfaz domain) + `ScreenContextService.captureScreenshot()` (service:system) implementados con `suspendCancellableCoroutine`, fail-soft null, API30+ guard, anti-reentrada, JPEG q80 scale 1024.
- **D7 (Lote 9)**: Auto-registro del servicio como proveedor en `ScreenContextRepositoryImpl` vía `setScreenCaptureProvider(this)` en `onServiceConnected`. Eliminación del companion estático.
- **M21 (Lote 8)**: `OverlayViewModel.sendMessage()` ya captura screenshot para el path genérico de Gemini (solo si el parser devuelve null — es decir, no es comando directo).

### Problema

La infraestructura de captura **ya existe y funciona**, pero:

1. No hay marker explícito para "analiza mi pantalla" → cae al path genérico de Gemini sin UX diferenciada.
2. El prompt enviado a Gemini es genérico ("Analiza mi pantalla y mi petición") — no instruye para análisis visual detallado.
3. No hay feedback visual de que se está capturando screenshot ("Analizando..." genérico).
4. La lógica de composición (screenshot + prompt + Gemini) está inline en el ViewModel — no testeable independientemente.

## Decisión

### 1. Nuevo marker `ANALYZE_SCREEN` en `CommandMarkers`

Añadir `const val ANALYZE_SCREEN = "__SCREEN_ASSISTANT_ANALYZE_SCREEN__"` al objeto `CommandMarkers` (core:domain).

**Posible en**: `SystemCommandParser`, rama 2c (después de monitoreo, antes de temporizador).

**Frases detectadas** (sobre trimmed normalizado):
- "analiza mi pantalla", "analiza la pantalla"
- "que hay en mi pantalla", "que hay en la pantalla"
- "que se ve en mi pantalla", "que se ve en la pantalla"
- "describe mi pantalla", "describe la pantalla"
- "que ves en pantalla"

Variantes: con/sin "mi"/"la", con `:` separador (H5/ADR-011).

### 2. Nuevo `AnalyzeScreenUseCase` (core:domain)

Caso de uso dedicado que encapsula:
1. Obtener texto de pantalla (`CaptureScreenContextUseCase.getScreenText()`)
2. Capturar screenshot (`CaptureScreenContextUseCase.captureScreenshot()`)
3. Construir prompt especializado (`ScreenAnalysisPromptBuilder`)
4. Enviar a Gemini (`GeminiRepository.sendMessage(prompt, image)`)

```kotlin
class AnalyzeScreenUseCase(
    private val captureScreenContext: CaptureScreenContextUseCase,
    private val geminiRepository: GeminiRepository
) {
    suspend operator fun invoke(userQuery: String): String? {
        val screenText = captureScreenContext.getScreenText()
        val imageData = captureScreenContext.captureScreenshot()
        val prompt = ScreenAnalysisPromptBuilder.build(screenText, userQuery, imageData != null)
        return geminiRepository.sendMessage(prompt, imageData)
    }
}
```

### 3. Nuevo `ScreenAnalysisPromptBuilder` (core:domain)

Objeto con función pura `build(screenText, userQuery, hasScreenshot) → String`:
- **Con screenshot**: prompt para análisis visual completo (qué app, qué elementos, qué contenido visual).
- **Sin screenshot** (fail-soft B3): prompt de fallback con solo el texto de accesibilidad, indicando que la imagen no está disponible.

### 4. Modificación de `OverlayViewModel`

- Añadir `analyzeScreenUseCase: AnalyzeScreenUseCase` al constructor.
- Nuevo branch en `sendMessage()`: `CommandMarkers.ANALYZE_SCREEN → analyzeScreen(text)`.
- Método `analyzeScreen()`: feedback "Capturando pantalla...", delegación al use case, manejo de error explícito.

### 5. No se tocan

- `ScreenContextService` — la infraestructura de captura (D7) funciona correctamente.
- `ScreenContextRepositoryImpl` — la delegación al provider funciona.
- `CaptureScreenContextUseCase` — se reutiliza tal cual.
- Monitoreo continuo — los flows son independientes.

## Consecuencias

### Positivas

- **UX mejorada**: feedback "Capturando pantalla..." vs "Analizando..." genérico.
- **Prompt especializado**: Gemini recibe instrucciones claras de análisis visual, no un prompt genérico.
- **Testabilidad**: `AnalyzeScreenUseCase` testeable con mocks de `CaptureScreenContextUseCase` y `GeminiRepository`. `ScreenAnalysisPromptBuilder` testeable con asserts directos (función pura).
- **Fail-soft intacto**: si `captureScreenshot()` devuelve null (API < 30, servicio desconectado, reentrada), el prompt de fallback usa solo texto de accesibilidad.
- **Consistencia**: sigue el patrón de `CaptureScreenContextUseCase` (composición en domain, delegación a repos).

### Negativas

- **Complejidad marginal**: 3 archivos nuevos + 3 modificaciones. Aceptable por la ganancia en testabilidad y UX.
- **Prompt acoplado al dominio**: los strings del prompt están en `ScreenAnalysisPromptBuilder`. Si el prompt necesita internacionalización futura, habrá que refactorizar (pero hoy el app es monolingüe).

### Riesgos

- **Overlay superpuesta**: la screenshot captura TODO el display, incluyendo la burbuja del overlay. El contenido detrás puede quedar parcialmente obstruido. Limitación conocida de la API de AccessibilityService — no bloqueante.

## Alternativas descartadas

| Alternativa | Por qué se descartó |
|---|---|
| **No marker, path genérico** | Ya funciona (M21), pero sin UX diferenciada ni prompt especializado. El usuario ve "Analizando..." y no sabe que se capturó screenshot. |
| **Marker + inline en ViewModel** | Sin use case: la lógica de composición no es testeable independientemente. Viola SRP. |
| **Dejar que Gemini maneje** | Latencia de red innecesaria para un comando conocido. El marker da respuesta instantánea (UX) y permite prompt local. |
| **Screenshot asíncrono en monitoreo continuo** | El monitoreo solo extrae texto por diseño (barato, cada 5s). Capturar screenshot cada 5s sería costoso en batería y memoria. |
| **MediaProjection API** | Requiere permiso explícito del usuario + un Activity de consentimiento. La AccessibilityService ya tiene permisos de captura en API30+. Más complejo sin beneficio. |

---

## Notas de implementación

### ordering de ramas en SystemCommandParser

```
rama 0  → notas crear (precedencia máxima)
rama 0b → notas leer
rama 1  → ayuda
rama 2  → repite
rama 2b → monitoreo continuo (start/stop)
rama 2c → análisis visual (NUEVA — ANALYZE_SCREEN)
rama 3  → temporizador
rama 4  → llamadas
rama 5  → volumen
rama 6  → idioma
rama 7  → ajustes
rama 8  → abrir app
rama 9  → navegación
rama 10 → búsqueda
rama 11 → memoria
rama 11b→ cancelar alarma
rama 12 → alarma
else    → null (Gemini genérico)
```

La rama 2c va DESPUÉS de 2b (monitoreo) porque:
- "activar monitoreo" matchea primero por precedencia de orden
- "analiza mi pantalla" NO contiene "monitoreo" → no hay conflicto
- "analiza mi pantalla" NO contiene "temporizador" → no hay conflicto con rama 3

### Prompt con imagen vs sin imagen

```
CON imagen:
"El usuario quiere que analices visualmente su pantalla.
 Te envío una captura de pantalla junto con el texto extraído.
 [TEXTO EXTRAÍDO]: ...
 [PETICIÓN]: ...
 Analiza la imagen considerando: app, elementos UI, contenido visual."

SIN imagen (fallback):
"El usuario quiere analizar su pantalla pero no hay captura visual.
 Aquí está el texto extraído: ...
 Indica que no se pudo capturar la imagen."
```

### Inyección de dependencias

```kotlin
// AppModule.kt — nuevo provider
@Provides @Singleton
fun provideAnalyzeScreenUseCase(
    captureScreenContext: CaptureScreenContextUseCase,
    geminiRepository: GeminiRepository
): AnalyzeScreenUseCase = AnalyzeScreenUseCase(captureScreenContext, geminiRepository)
```

`ScreenAnalysisPromptBuilder` es un `object` (singleton de Kotlin) — no necesita inyección.
