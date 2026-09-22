# ADR-MATH — "Libre albedrío" matemático (2 niveles: local + tool LLM)

> Estado: PROPUESTO (diseño, sin código). Fecha: 2026-09-17. Autor: Arquitecto.
> Alcance: fraseo libre matemático en español. NO gradle. NO implementación.

## 1. Contexto verificado en disco

- `core/domain/.../model/SystemCommand.kt` — `data class Calculate(operand1: Double, operator: CalculatorOperator, operand2: Double)` + `enum CalculatorOperator { ADD, SUBTRACT, MULTIPLY, DIVIDE }`. Congelado por tests.
- `service/system/.../action/CalculatorAction.kt` — `fun calculate(command: Calculate): String` binario + formateo ("El resultado es X." / 2 decimales / "Error: No se puede dividir por cero.").
- `service/system/.../action/CalculatorActionTest.kt` — 7 tests binarios (fijan formato y división por cero).
- `core/domain/.../usecase/SystemCommandParser.kt` rama 18 (`parseCalculator`, L767-815): triggers `cuanto es/que es/cuanto son/suma/sumar/resta/...` + regex `(\d+(\.\d+)?)\s*([+\-*/])\s*(\d+(\.\d+)?)` — 1 sola operación binaria, solo dígitos, sin palabras numéricas, sin `% ^ sqrt ()`, sin precedencia.
- `core/domain/.../usecase/SystemCommandParserTest.kt` — ~12 tests calculadora (`cuanto es 5 por 7 → Calculate(5,MULTIPLY,7)`, `suma 3 y 4`, `resta 5 de 10` invertido, `cuanto es mas` → null, precedencia DeviceInfo antes que calculadora).
- `core/domain/.../usecase/SpokenNumber.kt` — vocabulario 0-59 (`SIMPLE` 0-29 + `TENS` 20/30/40/50 + compuesto "X y Y" 1-9). Reutilizable, insuficiente para 60-100.
- `core/data/.../openrouter/OpenRouterToolCatalog.kt` — 8 tools (`open_alarms, set_alarm, search_google, open_youtube, open_whatsapp, play_music, save_memory, open_app`). Ninguna matemática.
- `core/data/.../remote/GeminiRepositoryImpl.kt` — `executeToolCalls` (L286-356, `when(functionName)`, `else → "Función ejecutada."`), bucle `MAX_FUNCTION_CALL_ITERATIONS=3`.
- `core/data/.../CorrespondenciaGeminiWireTest.kt` — guardián de paridad: `nombres == wirePorNombre.keys`, `AccionRegistry.esRegistrado(wire)`, `assertEquals(8, nombres.size)`. Añadir 9ª tool lo pone en ROJO por diseño.
- `service/system/.../SystemActionHandler.kt` L114 — `is Calculate → calculatorAction.calculate(command)`.

## 2. Decisión principal

**Nivel 1: `MathEvaluator` puro en `core:domain` (recursive descent, sin eval) + nuevo subtipo `CalculateExpression(expression)`; `Calculate` binario CONGELADO. Nivel 2: 9ª tool `calculate(expression)` en catálogo + rama en `executeToolCalls` que evalúa en local. Flujo: local primero, tool-call como fallback.**

## 3. Diagrama de capas (texto)

```
presentation (OverlayViewModel / voz)
  │ parse(text) / sendMessage(text)
  ▼
domain — core:domain (PURO, testeable, sin Android)
  ├─ SystemCommandParser rama 18 (extendida: triggers + normalización ES → canónica)
  ├─ math/MathEvaluator.evaluate(expr): MathResult        ← NUEVO, recursive descent
  ├─ math/SpanishNumberWords / MathExpressionNormalizer   ← NUEVO, palabras 0-100 + % ^ sqrt ()
  ├─ model/SystemCommand.Calculate (CONGELADO) + CalculateExpression (NUEVO)
  └─ model/MathResult (sealed Success/Error)
  │ SystemAction.execute(cmd) / inyección por constructor
  ▼
data — core:data (canal LLM, requiere clave)
  ├─ openrouter/OpenRouterToolCatalog.tools (8 → 9, +calculate)
  └─ remote/GeminiRepositoryImpl.executeToolCalls (+rama "calculate" → MathEvaluator)
  ▼
service:system (efectos Android)
  └─ action/CalculatorAction.calculate() (intacto) + calculateExpression() (NUEVO)
```

Dependencias apuntan hacia adentro: `service:system → domain`, `core:data → domain`. `domain` no conoce `data` ni Android.

## 4. Estructura de paquetes / módulos

**CREAR (solo domain puro + docs):**

- `core/domain/.../usecase/math/MathEvaluator.kt` — objeto/función pura `evaluate`.
- `core/domain/.../usecase/math/MathExpressionNormalizer.kt` — ES libre → canónica.
- `core/domain/.../usecase/math/SpanishNumberWords.kt` — extiende `SpokenNumber` a 0-100 (+ `sesenta/setenta/ochenta/noventa/cien/ciento`; `veinti*` con tilde normalizada).
- `core/domain/.../model/MathResult.kt` — `sealed interface MathResult { data class Success(value: Double); data class Error(reason: String) }`.
- `docs/ADR-MATH-libre-albedrio.md` — este archivo.
- Tests (los crea Desarrollador, los diseña QA): `MathEvaluatorTest`, `MathExpressionNormalizerTest`, extensión `SystemCommandParserTest`, extensión `CalculatorActionTest`, `OpenRouterToolCatalogTest` (+ actualizar `CorrespondenciaGeminiWireTest`).

**TOCAR (aditivo, sin romper):**

1. `core/domain/.../model/SystemCommand.kt` — AÑADIR `data class CalculateExpression(val expression: String)` (nuevo subtipo; `Calculate` intacto).
2. `core/domain/.../usecase/SystemCommandParser.kt` — rama 18: normalizar → si binaria simple emite `Calculate` legacy; si compleja emite `CalculateExpression`; si inválida → `null` (fall-through a Gemini/Nivel 2).
3. `service/system/.../action/CalculatorAction.kt` — AÑADIR `fun calculateExpression(cmd: CalculateExpression): String` (delega a `MathEvaluator`; reutiliza formateo y mensaje división-cero).
4. `service/system/.../SystemActionHandler.kt` — AÑADIR rama `is CalculateExpression → calculatorAction.calculateExpression(command)`.
5. `core/data/.../openrouter/OpenRouterToolCatalog.kt` — AÑADIR 9ª tool `calculate` + entrada `wirePorNombre` con exclusión documentada (ver §7).
6. `core/data/.../remote/GeminiRepositoryImpl.kt` — AÑADIR rama `"calculate"` en `executeToolCalls`.
7. `PROGRESO.md` (raíz) — sección `MATH-diseno`.
8. Tests guardián: `CorrespondenciaGeminiWireTest` (8→9 con excepción) + `SystemCommandJsonCodec`/`AccionRegistry` SOLO si se decide wire (por defecto NO).

**NO TOCAR:** `Calculate(op1,operator,op2)`, `CalculatorOperator`, triggers DeviceInfo (precedencia), `normalize()`, bucle `MAX_FUNCTION_CALL_ITERATIONS`, modelo `google/gemini-flash-1.5-8b`, ningún `build.gradle.kts`.

## 5. Contratos clave (firmas, sin implementación)

```kotlin
// domain — model/MathResult.kt (NUEVO)
sealed interface MathResult {
  data class Success(val value: Double) : MathResult
  data class Error(val reason: String) : MathResult  // reason ∈ {"division por cero","expresion invalida","numero fuera de rango"}
}

// domain — usecase/math/MathEvaluator.kt (NUEVO, puro, sin Android)
object MathEvaluator {
  fun evaluate(canonical: String): MathResult
  // Precondición: canonical solo charset [0-9.+\-*/%^() ] + token "sqrt".
  // Límites: canonical.length ≤ 200, profundidad paréntesis ≤ 10.
  // Gramática: expr := term (('+'|'-') term)* ; term := factor (('*'|'/'|'%') factor)*
  //            factor := unary ('^' factor)? (right-assoc) ; unary := '-' unary | primary
  //            primary := número | 'sqrt' '(' expr ')' | '(' expr ')' | porcentaje-postfijo
}

// domain — usecase/math/MathExpressionNormalizer.kt (NUEVO, puro)
object MathExpressionNormalizer {
  fun toCanonical(trimmedYaNormalizado: String, original: String): String?
  // null = no es matemática / inválida → fall-through (null → Gemini/Nivel 2).
}

// domain — model/SystemCommand.kt (ADITIVO)
data class CalculateExpression(val expression: String) : SystemCommand()
// Invariante: expression es la forma CANÓNICA (no el texto libre); longitud ≤ 200.

// service:system — action/CalculatorAction.kt (ADITIVO)
fun calculate(command: SystemCommand.Calculate): String              // CONGELADO, intacto
fun calculateExpression(command: SystemCommand.CalculateExpression): String  // NUEVO

// core:data — GeminiRepositoryImpl.kt (ADITIVO)
"calculate" -> MathEvaluator.evaluate(sanitizar(args["expression"] ?: "")) // → "El resultado es X." | "Error: ..."
```

Formato de salida único (reutilizar el de `CalculatorAction`): entero sin decimales, si no `String.format("%.2f")` + `trimEnd('0')` + `trimEnd('.')`; división por cero → `"Error: No se puede dividir por cero."`.

## 6. Nivel 1 — local offline (detalle)

**Normalización ES → canónica** (sobre `trimmed`, que ya es lowercase/sin tildes/`n`→`n`; extraer magnitud sobre el original solo si hace falta preservar — aquí no: todo es ASCII):

| Entrada (voz) | Canónica |
|---|---|
| `dos más dos`, `dos mas dos`, `2 y 2` (solo entre números) | `2+2` |
| `veinte por tres`, `5 por 7` | `20*3`, `5*7` |
| `10 entre 2`, `10 dividido por 2`, `10 dividido entre 2` | `10/2` |
| `15% de 200`, `el quince por ciento de doscientos` | `15/100*200` |
| `50%` suelto | `50/100` |
| `2 al cuadrado`, `3 al cubo`, `2^3`, `2 elevado a 3` | `2^2`, `3^3`, `2^3` |
| `raíz de 81` (ya normalizada `raiz de 81`), `raiz cuadrada de 81` | `sqrt(81)` |
| `abre paréntesis 2 más 3 cierra paréntesis por 4` / `(2+3)*4` | `(2+3)*4` |
| `2 + 3 * 4` | `2+3*4` → 14 (precedencia) |
| `1,5 más 2,3` (coma decimal) | `1.5+2.3` |

Precedencia del evaluador: `()` > `sqrt` / unario > `^` (right-assoc) > `* / %` (left) > `+ -` (left). `%` es binario resto (`10%3=1`) EXCEPTO el patrón `X% de Y` que el normalizador reescribe a `X/100*Y` antes de evaluar.

**Regla de emisión del parser (compatibilidad):** si la canónica matchea `^num op num$` binario simple → emitir `Calculate(op1,operator,op2)` legacy (los 12 tests viejos siguen verdes sin tocarse). En cualquier otro caso válido → `CalculateExpression(canonical)`. Si `toCanonical` devuelve `null` → `null` (Gemini/Nivel 2).

**Palabras numéricas 0-100:** `SpanishNumberWords` reutiliza `SpokenNumber.SIMPLE/TENS` y AÑADE `sesenta/setenta/ochenta/noventa (=60/70/80/90)`, `cien/ciento (=100)`, compuestos `X y Y` hasta 99 (`noventa y nueve`), `veintiuno…veintinueve` (ya existen), tolerancia `un/una`. Límite 0-100 por requisito; fuera de rango → `null` (Nivel 2 o error, nunca parcial).

## 7. Nivel 2 — tool `calculate` (requiere clave)

**JSON de la tool (formato OpenAI-compatible, mismo estilo que las 8 existentes):**

```json
{
  "type": "function",
  "function": {
    "name": "calculate",
    "description": "Resuelve una expresión aritmética en español libre. Devuelve la expresión canónica; el cálculo lo hace el evaluador local exacto, no el LLM.",
    "parameters": {
      "type": "object",
      "properties": {
        "expression": {
          "type": "string",
          "description": "Expresión aritmética canónica o en español (p. ej. '15% de 200', 'raiz de 81', '(2+3)*4'). Solo aritmética."
        }
      },
      "required": ["expression"]
    }
  }
}
```

**Rama en `executeToolCalls` (junto a las 8 existentes, antes del `else`):**

- `when (functionName) { "calculate" -> { val raw = args["expression"] ?: ""; val can = MathExpressionNormalizer.toCanonical(normalizar(raw), raw) ?: raw; when (val r = MathEvaluator.evaluate(sanitizar(can))) { Success → "El resultado es ${formatear(r.value)}."; Error → "Error: ${r.reason}." } } }`
- Sanitizar: `take(200)`, rechazar charset fuera de `[0-9.+\-*/%^() ]+sqrt` → `"Error: Expresión inválida."`. Capturar toda excepción → `null`-safe (igual que las ramas vecinas).
- El LLM **estructura**, el evaluador **calcula**: el prompt del sistema NO cambia salvo añadir "para matemáticas usa siempre la herramienta calculate, nunca calcules mentalmente".

**Convivencia N1/N2:**

```
voz/texto → SystemCommandParser.parse()
  ├─ Math OK (N1) → respuesta inmediata offline, fin (cero red, cero clave).
  └─ null → sendMessage() → LLM (+9 tools incl. calculate)
       └─ tool-call calculate → MathEvaluator local → tool-result → LLM verbaliza
```

N1 nunca llama red; N2 nunca calcula con el LLM. Sin doble ejecución: si N1 emitió `Calculate/CalculateExpression`, `OverlayViewModel` NO reenvía a `sendMessage`.

**Paridad Tasker (decisión explícita):** `calculate` es cálculo puro, NO acción de dispositivo → **NO nuevo wire** en `AccionRegistry` (precedente `save_memory → MemoryRepository` directa). `CorrespondenciaGeminiWireTest` debe actualizarse: `8→9` + aserción de exclusión documentada (`calculate` sin wire por ser puro). Alternativa descartada: crear wire `calcular` — obligaría a `SystemCommandJsonCodec` + Tasker + 22→23 wires para un cálculo que no necesita puente.

## 8. Alcance explícito

**ENTRA:** operadores `+ - * / % ^`, `sqrt`/`raíz`, paréntesis `()` (símbolo y verbal), precedencia estándar, `^` right-assoc, unario `-`, decimales punto y coma (`1,5→1.5`), palabras numéricas 0-100 ES, sinónimos `por/entre/dividido por/más/menos/y (entre números)/por ciento/de/%/al cuadrado/al cubo/elevado a/raíz`, triggers legacy intactos.
**NO ENTRA:** trigonometría (`sin/cos/tan`), logaritmos, matrices, ecuaciones, constantes (`pi/e`), factoriales, binario/hex, unidades físicas, gráficos. (Barato ≠ gratis: cada familia nueva multiplica la superficie del normalizador y los tests; se deja para un MATH-2 si hay demanda.)

## 9. Tests por nivel + aceptación

**Nivel 1 (offline, `MathEvaluatorTest` + `MathExpressionNormalizerTest` + `SystemCommandParserTest` + `CalculatorActionTest`):**

| # | Frase | Resultado esperado |
|---|---|---|
| 1 | `cuanto es dos más dos` | `El resultado es 4.` (vía `Calculate` o `CalculateExpression`, mismo texto) |
| 2 | `cuanto es veinte por tres` | `El resultado es 60.` |
| 3 | `cuanto es 15% de 200` | `El resultado es 30.` |
| 4 | `cuanto es el quince por ciento de doscientos` | `El resultado es 30.` |
| 5 | `cuanto es 2 + 3 * 4` | `El resultado es 14.` (precedencia, no 20) |
| 6 | `cuanto es (2 + 3) * 4` | `El resultado es 20.` |
| 7 | `cuanto es 2 al cuadrado` / `cuanto es 2^3` | `El resultado es 4.` / `El resultado es 8.` |
| 8 | `cuanto es la raiz de 81` | `El resultado es 9.` |
| 9 | `cuanto es 10 entre 0` | `Error: No se puede dividir por cero.` |
| 10 | `cuanto es mas` / `cuanto es` | `null` (fall-through, tests existentes intactos) |
| 11 | `cuanto es 5 por 7` (regresión) | `Calculate(5,MULTIPLY,7)` legacy, `El resultado es 35.` |
| 12 | `cuanto es 1,5 mas 2,3` | `El resultado es 3.8.` (coma decimal) |

Criterio N1: 7 tests `CalculatorActionTest` + 12 tests calculadora `SystemCommandParserTest` verdes SIN modificación; nuevos tests verdes; `parse("cuanto es mas") → null` inalterado; sin permisos/red.

**Nivel 2 (`OpenRouterToolCatalogTest` + `CorrespondenciaGeminiWireTest` actualizado + test de rama `executeToolCalls` con fake `MathEvaluator`):**

| # | Frase libre | Tool-call esperado → resultado |
|---|---|---|
| 13 | `¿cuánto es el quince por ciento de doscientos?` (si N1 no la cubre) | `calculate(expression="15% de 200"‖"15/100*200")` → `El resultado es 30.` |
| 14 | `¿cuánto es doce al cuadrado más la raíz de dieciséis?` | `calculate(...)` → `El resultado es 148.` (el LLM no devuelve 150 alucinado) |
| 15 | `calcula (8 + 2) * (7 - 3) / 5` | `calculate("(8+2)*(7-3)/5")` → `El resultado es 8.` |
| 16 | `expression="__import__('os')..."` (inyección) | `Error: Expresión inválida.` (nunca ejecuta) |

Criterio N2: el LLM devuelve tool-call con `expression` saneable; el valor numérico final lo produce `MathEvaluator`; `CorrespondenciaGeminiWireTest` verde con 9 + exclusión documentada.

## 10. Orden de implementación (para Desarrollador)

1. P1 — `MathResult` + `MathEvaluator` puro + `MathEvaluatorTest` (tabla 5,6,7,8,9).
2. P2 — `SpanishNumberWords` + `MathExpressionNormalizer` + tests (tabla 1-4,12).
3. P3 — `SystemCommand.CalculateExpression` + rama `SystemActionHandler` + `CalculatorAction.calculateExpression` + tests (regresión §9).
4. P4 — rama 18 del parser (binario→legacy, complejo→nuevo, inválido→null) + extensión `SystemCommandParserTest`.
5. P5 — tool `calculate` + rama `executeToolCalls` + actualización guardián paridad (tabla 13-16).
6. P6 — QA shift-left: matrices de locale/inyección/profundidad + verificación offline (modo avión N1 responde, N2 pide clave).

## 11. Riesgos y mitigaciones

- **Inyección vía `expression` (LLM o voz):** `"; rm -rf"`, `"__import__"`, `"1;2"` → MITIGACIÓN: allowlist de charset + longitud ≤200 + profundidad ≤10 + `MathResult.Error` sin `eval`/ScriptEngine/reflection (prohibidos por contrato). Test 16 obligatorio.
- **Locale coma vs punto:** `1,5` decimal ES vs `1,000` miles vs `normalize()` que convierte `,` en espacio → MITIGACIÓN: el normalizador matemático corre ANTES/DISTINTO de `normalize` general para este tramo: `1,5→1.5`, `1.000,5→1000.5`, miles `1,000→1000`; documentar en KDoc que la rama 18 no reutiliza `normalize` a ciegas.
- **Colisión `y`/`por`/`entre`:** `y` solo es `+` entre dos números (`suma 3 y 4`), no en `¿y qué hora es?`; `por` en `por favor`/`por la mañana` no es `*` → MITIGACIÓN: reemplazos solo dentro del segmento matemático extraído tras el trigger, nunca sobre `trimmed` completo; `cortesiaSufijoRegex` (`por favor`) se aplica antes.
- **Regresión triggers:** DeviceInfo (`cuanto espacio libre/bateria`) precede a calculadora; `normalize` longitud-invariante para `extractAfterPrefix` → MITIGACIÓN: no mover el orden de ramas; P4 con tests de precedencia verdes.
- **Paridad 8→9:** `CorrespondenciaGeminiWireTest` fallará en rojo hasta actualizarse → MITIGACIÓN: P5 incluye la actualización con exclusión documentada; NO crear wire salvo que QA/Orquestador lo exija.
- **`^` right-assoc y `%` dual:** `2^3^2=512` (no 64); `10%3=1` vs `10% de 50=5` → MITIGACIÓN: fijar con tests ambos casos.
- **DoS por paréntesis profundos:** `((((((...` → MITIGACIÓN: límite profundidad 10 + longitud 200 → `Error`.

## 12. Testabilidad (para QA shift-left)

Todo lo aritmético es puro Kotlin sin Android/Hilt/coroutines: `MathEvaluator.evaluate`, `MathExpressionNormalizer.toCanonical`, `SpanishNumberWords.resolve` — testeables con JUnit + `runTest` en parser. Contratos mockeables: `SystemAction` (fake para `parse`), `MemoryRepository`/`OpenRouterApiClient` (fakes para `executeToolCalls`). Casos borde a cubrir: `10/0`, `0/0`, `sqrt(-1)→Error`, `2^3^2`, `(2+3)*4` vs `2+3*4`, `1,5`, `noventa y nueve`, `cien`, `101→null/error`, inyección, longitud 201, profundidad 11.

## 13. Alternativas descartadas

- **Extender `Calculate` con `expression: String` obligatorio:** rompe 7+12 tests + `SystemCommandJsonCodec` (`ignoreUnknownKeys=false`) + puente Tasker. Descartada por costo de migración.
- **`Calculate` con `expression: String? = null` opcional:** abarata firma pero ensucia `equals/hash` del comando más usado y obliga al codec a distinguir `null` vs binario. Descartada frente a subtipo limpio.
- **Unificar N1+N2 en solo-LLM:** viola offline/sin clave y alucina aritmética. Descartada.
- **Solo-N1 sin tool:** deja fuera el fraseo verdaderamente libre (`¿cuánto es el quince por ciento de...?`). Descartada.
- **`ScriptEngine.eval` / exp4j sin allowlist:** riesgo de inyección + dependencia. Prohibido por contrato.

## 14. Ambigüedades detectadas

- `PROGRESO.md` de raíz no existe (solo `core/ai/local/PROGRESO.md`); se crea de novo con la sección `MATH-diseno` — interpretación por defecto del encargo.
- `SystemCommandJsonCodec`/Tasker para `Calculate` no se inspeccionó a fondo (fuera del alcance sin código); se asume `ignoreUnknownKeys=false` por ADR-007 citado — si el Desarrollador encuentra `true`, la opción "campo opcional" se abarata y puede reevaluarse en el feedback loop.
- Rango 0-100 pedido con `?`: se fija 0-100 inclusivo, enteros; negativos vía unario (`menos cinco`), decimales en palabras (`coma cinco`) DIFERIDOS a MATH-2.

## 15. Bloqueos

Ninguno. Diseño entregable por escrito aunque parcial — el feedback loop con QA/Desarrollador ajusta.
