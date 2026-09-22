# ADR-028: Motor Predictivo de Patrones de Usuario (Revision v2)

**Estado:** Aceptado (revision tras feedback QA)
**Fecha:** 2026-09-20
**Decisor:** Arquitecto
**Contexto:** Sistema proactivo existente con infraestructura de patrones incompleta
**Revisiones:** v1 rechazada por QA (5 problemas criticos), v2 corrige todos

---

## 1. Contexto

El proyecto ScreenAssistant tiene un sistema proactivo que evalua reglas hardcodeadas (`ProactiveRule`) contra el contexto del dispositivo (`AggregatedContext`). Existe infraestructura completa para patrones de comportamiento:

- **Entity Room** `UserPatternEntity` con campos: action, dayOfWeek, hourStart, hourEnd, locationType, screenApp, frequency, lastSeen, confidence
- **Modelo de dominio** `UserPattern` con `isEstablished` (confidence >= 0.6)
- **Formula de confianza**: `0.1 + 0.9 * (1 - e^(-frequency/5))`
- **DAO** `UserPatternDao` con operaciones CRUD completas
- **Repositorio** `UserPatternRepository` con `recordOccurrence()` y `getFrequentPatterns()`
- `PatternLearnerWorker` que SOLO limpia patrones antiguos (30 dias)

**Faltan 2 piezas criticas:**
1. Un **metodo de tracking** en el repositorio que detecte y registre que hace el usuario con contexto
2. Un **motor predictivo** que genere sugerencias basadas en patrones frecuentes

---

## 2. Problemas de la v1 y Correcciones

### 2.1 CRITICO 1: `PredictiveEngine` -- Interfaz innecesaria

**Problema v1:** Se diseno `PredictiveEngine` como interfaz con `PredictiveEngineImpl`. Los motores existentes (`ProactiveEngine`, `PersonalityEngine`) son clases directas sin interfaz. La interfaz agrega complejidad sin beneficio.

**Correccion v2:** Clase directa, misma estructura que `ProactiveEngine`:

```kotlin
class PredictiveEngine @Inject constructor(private val clock: Clock) {
    fun generateSuggestions(...): List<ProactiveSuggestion> { ... }
}
```

**Justificacion:** Consistencia con patrones existentes. No hay multiples implementaciones ni necesidad de abstraccion.

### 2.2 CRITICO 2: `UserActionTracker` -- Duplica responsabilidad

**Problema v1:** Se creo `UserActionTracker` (interfaz) + `UserActionTrackerImpl` que simplemente llamaba a `UserPatternRepository.recordOccurrence()`. `recordOccurrence()` ya existe en el repositorio -- el tracker era un pasamanos innecesario.

**Correccion v2:** Anadir metodo `trackAction()` directamente a `UserPatternRepository`:

```kotlin
// UserPatternRepository.kt -- anadir:
suspend fun trackAction(actionId: String, screenApp: String?)
```

```kotlin
// UserPatternRepositoryImpl.kt -- implementar:
override suspend fun trackAction(actionId: String, screenApp: String?) {
    val context = contextAggregator.getAggregatedContext()
    val period = DayPeriod.fromHour(context.temporal.dateTime.hour)
    val patternContext = PatternContext(
        dayOfWeek = context.temporal.dayOfWeek,
        hourStart = period.hourRange.first,
        hourEnd = period.hourRange.last,
        location = context.location,
        screenApp = screenApp
    )
    recordOccurrence(actionId, patternContext)
}
```

**Justificacion:** Elimina una capa de abstraccion innecesaria. La responsabilidad de "construir contexto y registrar" encaja naturalmente en el repositorio que ya conoce `PatternContext` y `recordOccurrence()`.

### 2.3 CRITICO 3: Mapeo temporal no definido

**Problema v1:** `PatternContext` requiere `hourStart` y `hourEnd`, pero `TemporalContext` solo tiene un `LocalTime` individual. El diseño original usaba `hourStart = hourEnd = currentHour` (ventana de 1 hora), lo cual era ambiguo y poco documentado.

**Correccion v2:** Usar `DayPeriod` existente para bucketing:

```kotlin
val period = DayPeriod.fromHour(context.temporal.dateTime.hour)
val hourStart = period.hourRange.first   // ej: 6 para MANANA
val hourEnd = period.hourRange.last      // ej: 11 para MANANA
```

**Mapping DayPeriod -> PatternContext:**

| DayPeriod  | hourStart | hourEnd |
|------------|-----------|---------|
| MADRUGADA  | 0         | 5       |
| MANANA     | 6         | 11      |
| MEDIODIA   | 12        | 13      |
| TARDE      | 14        | 19      |
| NOCHE      | 20        | 23      |

**Justificacion:** Reutiliza la abstraccion existente `DayPeriod` con su `hourRange` definido. Los buckets son amplios pero consistentes con la granularidad del modelo actual. Testing deterministico (DayPeriod es un enum cerrado).

### 2.4 CRITICO 4: Try-catch convierte tracking-fallido en error

**Problema v1:** En `SystemActionHandler.execute()`, si `trackAction()` falla dentro del try-catch, se convierte en error para el usuario: "No pude procesar la solicitud de sistema".

**Correccion v2:** Fire-and-forget -- tracking no bloquea ni contamina el resultado:

```kotlin
// En SystemActionHandler.execute(), DESPUES del try-catch principal:
val result = try { /* logica existente */ } catch { ... }

// Fire-and-forget: tracking no bloquea ni contamina el resultado
try {
    userPatternRepository.trackAction(command.toActionId(), currentPackageName)
} catch (_: Exception) {
    // Logging silencioso -- tracking fallido no afecta al usuario
    android.util.Log.w("SystemActionHandler", "Tracking fallido para ${command.javaClass.simpleName}")
}

return result
```

**Justificacion:** El tracking es best-effort. Si falla, el usuario no debe enterarse. La accion principal ya se ejecuto correctamente.

### 2.5 CRITICO 5: `findExistingPattern()` es O(n)

**Problema v1:** `UserPatternRepositoryImpl.findExistingPattern()` cargaba TODOS los patrones con `SELECT *` y hacie busqueda lineal en memoria.

**Correccion v2:** Query especifico en DAO:

```kotlin
@Query("""
    SELECT * FROM user_patterns
    WHERE action = :action
    AND dayOfWeek = :dayOfWeek
    AND hourStart = :hourStart
    AND hourEnd = :hourEnd
    AND locationType = :location
    LIMIT 1
""")
suspend fun findByActionAndContext(
    action: String,
    dayOfWeek: String,
    hourStart: Int,
    hourEnd: Int,
    location: String
): UserPatternEntity?
```

**Justificacion:** Busqueda O(1) con indice. Room optimiza la query automaticamente. La entity ya tiene indice en `action`.

---

## 3. Diagrama de Capas (v2)

```
+-------------------------------------------------------------------+
|                   PRESENTATION (feature:overlay)                    |
|  ProactiveViewModel --> ProactiveSuggestionManager (StateFlow)     |
+----------------------------+--------------------------------------+
                             | consume StateFlow
+----------------------------v--------------------------------------+
|                       DOMAIN (core:domain)                          |
|  +---------------+  +----------------------+                      |
|  | Predictive    |  | ProactiveEngine      |                      |
|  | Engine        |  | (existente)          |                      |
|  | (clase pura)  |  |                      |                      |
|  +-------+-------+  +----------+-----------+                      |
|          |                     |                                   |
|  +-------v---------------------v-------------------------------+  |
|  |              UserPatternRepository (interfaz)                |  |
|  |              + trackAction() [NUEVO]                         |  |
|  |              ContextAggregatorRepository (interfaz)          |  |
|  +-----------------------------+-------------------------------+  |
+--------------------------------+----------------------------------+
                                 | implementa
+--------------------------------v----------------------------------+
|                        DATA (core:data)                             |
|  +------------------------------------------------------------+  |
|  | UserPatternRepositoryImpl                                    |  |
|  |  + trackAction() [NUEVO: construye contexto, delega]       |  |
|  |  + findExistingPattern() [MEJORADO: query O(1)]            |  |
|  +------------------------------------------------------------+  |
|  +------------------------------------------------------------+  |
|  | ProactiveCheckWorker (modificado: +patrones)                |  |
|  | PatternLearnerWorker (existente: limpieza diaria)           |  |
|  +------------------------------------------------------------+  |
|  +------------------------------------------------------------+  |
|  | UserPatternDao (MODIFICADO: +findByActionAndContext)        |  |
|  | UserPatternEntity (sin cambios)                              |  |
|  +------------------------------------------------------------+  |
|                                                                   |
|  SERVICE (service:system)                                          |
|  +------------------------------------------------------------+  |
|  | SystemActionHandler (MODIFICADO: +fire-and-forget track)   |  |
|  +------------------------------------------------------------+  |
+-------------------------------------------------------------------+
```

---

## 4. Dependencias entre Componentes (v2)

```
SystemActionHandler (service:system)
  +-- inyecta: UserPatternRepository (domain) [NUEVO]

UserPatternRepository (domain) [MODIFICADO]
  +-- recordOccurrence(action, patternContext)  [existente]
  +-- trackAction(actionId, screenApp)          [NUEVO]
  +-- getFrequentPatterns(minFrequency)         [existente]
  +-- findByActionAndContext(...)               [NUEVO en DAO]

UserPatternRepositoryImpl (data) [MODIFICADO]
  +-- inyecta: UserPatternDao
  +-- inyecta: ContextAggregatorRepository [NUEVO]
  +-- implementa: UserPatternRepository

UserPatternDao (data) [MODIFICADO]
  +-- findByActionAndContext(action, dayOfWeek, hourStart, hourEnd, location) [NUEVO]

PredictiveEngine (domain) [NUEVO, clase directa]
  +-- inyecta: Clock

ProactiveCheckWorker (data) [MODIFICADO]
  +-- inyecta: PredictiveEngine [NUEVO]
  +-- inyecta: UserPatternRepository [NUEVO]
  +-- inyecta: ProactiveEngine [existente]
```

---

## 5. Orden de Implementacion

### Fase 1: DAO Optimization (CRITICO 5)

| Paso | Archivo | Accion | Dependencias |
|------|---------|--------|--------------|
| 1.1 | `core/data/.../local/UserPatternDao.kt` | Anadir `findByActionAndContext()` | Ninguna |
| 1.2 | `core/data/src/test/.../local/UserPatternDaoTest.kt` | Test de la nueva query | Paso 1.1 |

### Fase 2: Repository Enhancement (CRITICOS 2, 3, 5)

| Paso | Archivo | Accion | Dependencias |
|------|---------|--------|--------------|
| 2.1 | `core/domain/.../repository/UserPatternRepository.kt` | Anadir `trackAction()` a la interfaz | Ninguna |
| 2.2 | `core/data/.../repository/UserPatternRepositoryImpl.kt` | Implementar `trackAction()` con DayPeriod + mejorar `findExistingPattern()` | Paso 1.1, 2.1 |
| 2.3 | `core/data/src/test/.../repository/UserPatternRepositoryImplTest.kt` | Tests de `trackAction()` y `findExistingPattern()` mejorado | Paso 2.2 |

### Fase 3: Motor Predictivo (CRITICO 1)

| Paso | Archivo | Accion | Dependencias |
|------|---------|--------|--------------|
| 3.1 | `core/domain/.../engine/PredictiveEngine.kt` | Crear clase directa (sin interfaz) | Ninguna |
| 3.2 | `core/domain/src/test/.../engine/PredictiveEngineTest.kt` | Tests unitarios del motor | Paso 3.1 |

### Fase 4: Integration (CRITICO 4 + Worker)

| Paso | Archivo | Accion | Dependencias |
|------|---------|--------|--------------|
| 4.1 | `service/system/.../SystemActionHandler.kt` | Anadir `trackAction()` fire-and-forget post-ejecucion | Paso 2.1 |
| 4.2 | `core/data/.../proactive/ProactiveCheckWorker.kt` | Anadir evaluacion de patrones | Paso 3.1 |
| 4.3 | `core/data/src/test/.../proactive/ProactiveCheckWorkerTest.kt` | Tests de integracion | Paso 4.2 |

---

## 6. Criterios de Aceptacion

### CA-1: Tracking de Acciones
- [ ] Cuando el usuario ejecuta una accion del sistema, se registra un patrcon con contexto temporal
- [ ] El contexto usa `DayPeriod.hourRange` (no hora individual)
- [ ] Si el patron ya existe, se incrementa la frequency y se recalcula la confidence
- [ ] El registro no bloquea la ejecucion de la accion original (fire-and-forget)
- [ ] Si el tracking falla, el usuario NO ve error (try-catch aislado)
- [ ] Test unitario: `trackAction` llama a `recordOccurrence` con el contexto correcto

### CA-2: Motor Predictivo
- [ ] `PredictiveEngine` es clase directa (sin interfaz)
- [ ] Dados patrones establecidos y contexto actual, genera sugerencias validas
- [ ] Solo sugiere patrones que coinciden temporalmente (mismo dia, hora en rango DayPeriod)
- [ ] Maximo 3 sugerencias por ciclo de evaluacion
- [ ] Las sugerencias tienen source = "pattern:{id}" para tracking
- [ ] Test unitario: `generateSuggestions` retorna sugerencias cuando hay match
- [ ] Test unitario: `generateSuggestions` retorna vacio cuando no hay match

### CA-3: Performance
- [ ] `findExistingPattern()` usa query SQL especifica (no SELECT *)
- [ ] Bussqueda es O(1) con indice en `action`
- [ ] Test: `findByActionAndContext()` retorna el patron correcto

### CA-4: Integracion
- [ ] `ProactiveCheckWorker` evalua reglas Y patrones en el mismo ciclo
- [ ] Las sugerencias de patrones pasan por los mismos filtros (cooldown, quiet hours, expiracion)
- [ ] El sistema de reglas existente no se ve afectado (no hay regression)
- [ ] Test de integracion: worker genera sugerencias de ambos tipos

### CA-5: Testabilidad
- [ ] Todos los componentes usan inyeccion de dependencias por constructor
- [ ] `PredictiveEngine` es testeable con Clock falso (sin dependencias Android)
- [ ] `UserPatternRepositoryImpl` es testeable con mocks de dao y contextAggregator

### CA-6: Compatibilidad
- [ ] La entity `UserPatternEntity` no cambia (sin migracion de DB)
- [ ] El `PatternLearnerWorker` existente sigue funcionando igual

---

## 7. Alternativas Descartadas

### Alternativa A: UserActionTracker como clase independiente
**Descartada:** Duplica la responsabilidad de `recordOccurrence()` que ya existe en el repositorio. Agrega una capa innecesaria.

### Alternativa B: PredictiveEngine como interfaz
**Descartada:** Consistencia con `ProactiveEngine` y `PersonalityEngine` que son clases directas. No hay multiples implementaciones.

### Alternativa C: Mapeo temporal con hora individual (hourStart = hourEnd)
**Descartada:** Bucketing por `DayPeriod` es mas robusto, reutiliza abstraccion existente, y permite matching correcto en evaluacion predictiva.

### Alternativa D: Tracking dentro del try-catch principal
**Descartada:** Convierte tracking-fallido en error de usuario. Fire-and-forget es el patron correcto para operaciones best-effort.

### Alternativa E: Machine Learning en el dispositivo
**Descartada:** Requeriria TensorFlow Lite, modelo entrenado, y complejidad significativa. El enfoque de frecuencia + confianza es mas simple, testeable, y suficiente para la fase actual.

---

## 8. Riesgos y Mitigaciones

| Riesgo | Impacto | Mitigacion |
|--------|---------|------------|
| Performance: getFrequentPatterns() carga todos los patrones | Bajo | Room tiene indice en frequency; getFrequentPatterns(3) retorna pocos registros |
| Ruido: muchas sugerencias de patrones | Medio | MAX_SUGGESTIONS = 3, cooldown del manager, filtro de quiet hours |
| Falsos positivos: patrones que no son relevantes | Medio | ESTABLISHED_THRESHOLD = 0.6, minimo 3 observaciones, coincidencia temporal estricta |
| DayPeriod buckets demasiado amplios | Bajo | Suficiente para fase 1; se puede refinar con DayPeriod custom en futuro |
| DB migration por nuevo query | Nulo | Room genera el query en compile-time, sin cambios al schema |
