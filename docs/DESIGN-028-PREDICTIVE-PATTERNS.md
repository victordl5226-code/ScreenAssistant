# Diseno Arquitectonico: Sistema Predictivo de Patrones (Revision v2)

**Proyecto:** ScreenAssistant
**Fecha:** 2026-09-20
**ADR asociado:** ADR-028-predictive-pattern-engine.md
**Revision:** v2 (correccion de 5 problemas criticos de QA)

---

## 1. Resumen de Cambios v1 -> v2

| Problema QA | v1 (rechazado) | v2 (corregido) |
|-------------|----------------|----------------|
| CRITICO 1 | PredictiveEngine = interfaz + impl | PredictiveEngine = clase directa |
| CRITICO 2 | UserActionTracker = interfaz + impl separado | trackAction() directo en UserPatternRepository |
| CRITICO 3 | hourStart = hourEnd = currentHour (ambiguo) | DayPeriod.hourRange.first/last (bucketing definido) |
| CRITICO 4 | trackAction() dentro de try-catch principal | Fire-and-forget, try-catch aislado |
| CRITICO 5 | SELECT * + busqueda lineal O(n) | Query SQL especifico O(1) |

---

## 2. Componentes a Crear/Modificar con Ubicacion Exacta

### 2.1 NUEVOS -- Domain Layer (core/domain)

| # | Archivo | Tipo | Descripcion |
|---|---------|------|-------------|
| D1 | `core/domain/src/main/kotlin/com/screenassistant/core/domain/engine/PredictiveEngine.kt` | Clase | Motor predictivo puro (sin interfaz) |

### 2.2 MODIFICADOS -- Domain Layer

| # | Archivo | Cambio | Descripcion |
|---|---------|--------|-------------|
| MD1 | `core/domain/src/main/kotlin/com/screenassistant/core/domain/repository/UserPatternRepository.kt` | +1 metodo | Anadir `trackAction()` a la interfaz |

### 2.3 MODIFICADOS -- Data Layer

| # | Archivo | Cambio | Descripcion |
|---|---------|--------|-------------|
| DA1 | `core/data/src/main/kotlin/com/screenassistant/core/data/local/UserPatternDao.kt` | +1 query | Anadir `findByActionAndContext()` |
| DA2 | `core/data/src/main/kotlin/com/screenassistant/core/data/repository/UserPatternRepositoryImpl.kt` | +2 metodos, -1 privado | Implementar `trackAction()`, mejorar `findExistingPattern()`, inyectar ContextAggregatorRepository |

### 2.4 MODIFICADOS -- Service Layer

| # | Archivo | Cambio | Descripcion |
|---|---------|--------|-------------|
| S1 | `service/system/src/main/kotlin/com/screenassistant/service/system/SystemActionHandler.kt` | +1 inyeccion, +5 lineas | Anadir UserPatternRepository, fire-and-forget trackAction() |

### 2.5 MODIFICADOS -- Data Layer (Worker)

| # | Archivo | Cambio | Descripcion |
|---|---------|--------|-------------|
| W1 | `core/data/src/main/kotlin/com/screenassistant/core/data/proactive/ProactiveCheckWorker.kt` | +2 inyecciones, +10 lineas | Anadir PredictiveEngine y UserPatternRepository, evaluacion de patrones |

### 2.6 ARCHIVOS ELIMINADOS (respecto a v1)

| # | Archivo | Razon |
|---|---------|-------|
| E1 | `core/domain/src/main/kotlin/com/screenassistant/core/domain/tracking/UserActionTracker.kt` | Duplica responsabilidad (CRITICO 2) |
| E2 | `core/data/src/main/kotlin/com/screenassistant/core/data/tracking/UserActionTrackerImpl.kt` | Duplica responsabilidad (CRITICO 2) |

---

## 3. Contratos/Interficies de Cada Componente

### 3.1 UserPatternRepository -- trackAction() [NUEVO]

```kotlin
// Ruta: core/domain/src/main/kotlin/com/screenassistant/core/domain/repository/UserPatternRepository.kt
// Se anyade este metodo a la interfaz existente:

/**
 * Registra una accion del usuario con su contexto actual.
 *
 * Construye un [PatternContext] a partir del contexto del dispositivo
 * (hora via DayPeriod, dia, ubicacion) y delega el registro a
 * [recordOccurrence].
 *
 * Si la accion ya fue registrada en el mismo contexto, se incrementa
 * la frecuencia y se recalcula la confianza. Si no, se crea un patron nuevo.
 *
 * @param actionId Identificador de la accion ejecutada.
 *   Convencion: "{category}_{verb}" -- ej: "wifi_toggle", "alarm_set"
 * @param screenApp Paquete de la app que estaba en primer plano.
 *   Null si la accion no ocurrio dentro de una app especifica.
 */
suspend fun trackAction(actionId: String, screenApp: String? = null)
```

### 3.2 UserPatternRepositoryImpl -- implementaciones [MODIFICADAS]

```kotlin
// Ruta: core/data/src/main/kotlin/com/screenassistant/core/data/repository/UserPatternRepositoryImpl.kt

@Singleton
class UserPatternRepositoryImpl @Inject constructor(
    private val dao: UserPatternDao,
    private val contextAggregator: ContextAggregatorRepository  // [NUEVO]
) : UserPatternRepository {

    // [NUEVO] Tracking de acciones con contexto
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

    // [MEJORADO] Busqueda O(1) via query SQL
    private suspend fun findExistingPattern(
        action: String,
        pattern: PatternContext
    ): UserPattern? {
        return dao.findByActionAndContext(
            action = action,
            dayOfWeek = pattern.dayOfWeek.name,
            hourStart = pattern.hourStart,
            hourEnd = pattern.hourEnd,
            location = pattern.location.name
        )?.toDomain()
    }

    // recordOccurrence, getFrequentPatterns, clearOldPatterns, deletePattern -- SIN CAMBIOS
}
```

### 3.3 UserPatternDao -- findByActionAndContext() [NUEVO]

```kotlin
// Ruta: core/data/src/main/kotlin/com/screenassistant/core/data/local/UserPatternDao.kt
// Se anyade este metodo:

/**
 * Busca un patron que coincida con la accion y contexto dados.
 * Busqueda O(1) via indice en 'action'.
 *
 * @param action Identificador de la accion
 * @param dayOfWeek Nombre del enum DayOfWeek (ej: "MONDAY")
 * @param hourStart Hora de inicio del rango (0-23)
 * @param hourEnd Hora de fin del rango (0-23)
 * @param location Nombre del enum LocationType (ej: "HOME")
 * @return El patron si existe, null de lo contrario
 */
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

### 3.4 PredictiveEngine [NUEVO -- Clase Directa]

```kotlin
// Ruta: core/domain/src/main/kotlin/com/screenassistant/core/domain/engine/PredictiveEngine.kt
package com.screenassistant.core.domain.engine

import com.screenassistant.core.domain.model.proactive.AggregatedContext
import com.screenassistant.core.domain.model.proactive.ProactiveAction
import com.screenassistant.core.domain.model.proactive.ProactivePriority
import com.screenassistant.core.domain.model.proactive.ProactiveSuggestion
import com.screenassistant.core.domain.model.proactive.UserPattern
import java.time.Clock
import javax.inject.Inject

/**
 * Motor predictivo que genera sugerencias basadas en patrones de comportamiento
 * establecidos del usuario. Funcion pura: no tiene side effects.
 *
 * Evalua los patrones contra el contexto temporal actual (via DayPeriod)
 * y genera [ProactiveSuggestion]s cuando detecta coincidencias.
 *
 * Misma estructura que [ProactiveEngine]: clase directa, inyectable por Hilt,
 * sin interfaz innecesaria.
 *
 * @constructor Crea el motor con un [Clock] inyectable para testing.
 * @param clock Reloj inyectable (patron identico a ProactiveEngine)
 */
class PredictiveEngine @Inject constructor(
    private val clock: Clock
) {

    /**
     * Genera sugerencias predictivas a partir de patrones establecidos.
     *
     * Algoritmo:
     * 1. Filtra patrones que coinciden temporalmente (mismo dayOfWeek + hour en rango)
     * 2. Ordena por confidence descendente
     * 3. Toma los top [MAX_SUGGESTIONS]
     * 4. Genera [ProactiveSuggestion] para cada patron
     *
     * @param currentContext Contexto agregado del dispositivo en el momento actual
     * @param establishedPatterns Patrones con confidence >= ESTABLISHED_THRESHOLD
     * @return Lista ordenada por confianza (max [MAX_SUGGESTIONS] elementos)
     */
    fun generateSuggestions(
        currentContext: AggregatedContext,
        establishedPatterns: List<UserPattern>
    ): List<ProactiveSuggestion> {
        if (establishedPatterns.isEmpty()) return emptyList()

        val now = currentContext.temporal
        return establishedPatterns
            .filter { matchesCurrentContext(it, now) }
            .sortedByDescending { it.confidence }
            .take(MAX_SUGGESTIONS)
            .map { pattern -> buildSuggestion(pattern) }
    }

    /**
     * Verifica si un patron coincide con el contexto temporal actual.
     *
     * Coincidencia: mismo dia de la semana Y hora actual dentro del rango
     * del patron (hourStart..hourEnd definido por DayPeriod).
     *
     * La ubicacion se omite intencionalmente (el LocationType del device puede ser
     * UNKNOWN y filtraria todo).
     */
    private fun matchesCurrentContext(
        pattern: UserPattern,
        temporal: com.screenassistant.core.domain.model.TemporalContext
    ): Boolean {
        val currentHour = temporal.dateTime.hour
        return pattern.context.dayOfWeek == temporal.dayOfWeek &&
               currentHour in pattern.context.hourStart..pattern.context.hourEnd
    }

    /**
     * Construye una [ProactiveSuggestion] a partir de un [UserPattern].
     *
     * El source usa el prefijo "pattern:" para que ProactiveSuggestionManager
     * aplique cooldown independiente de las reglas.
     */
    private fun buildSuggestion(pattern: UserPattern): ProactiveSuggestion {
        val message = buildString {
            append("Detecte que sueles usar esta accion ")
            append("los ${pattern.context.dayOfWeek.name.lowercase()} ")
            append("entre las ${pattern.context.hourStart}:00 y ${pattern.context.hourEnd}:00. ")
            append("Quieres que la ejecute ahora?")
        }
        return ProactiveSuggestion(
            title = "Accion recurrente detectada",
            message = message,
            action = ProactiveAction.SuggestSystemAction(
                actionId = pattern.action
            ),
            priority = ProactivePriority.MEDIUM,
            source = "pattern:${pattern.id}"
        )
    }

    companion object {
        /** Maximo de sugerencias predictivas por ciclo de evaluacion. */
        const val MAX_SUGGESTIONS = 3
    }
}
```

### 3.5 SystemActionHandler -- Fire-and-Forget [MODIFICADO]

```kotlin
// Ruta: service/system/src/main/kotlin/com/screenassistant/service/system/SystemActionHandler.kt
// Cambios:

@Singleton
class SystemActionHandler @Inject constructor(
    // ... inyecciones existentes ...
    private val userPatternRepository: UserPatternRepository,  // [NUEVO]
    // ...
) : SystemAction {

    override suspend fun execute(command: SystemCommand): ActionResult {
        val result = try {
            // ... toda la logica existente SIN CAMBIOS ...
            ActionResult.Success(formatted)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("SystemActionHandler", "Protocolo fallido: ${command.javaClass.simpleName}", e)
            ActionResult.Error("No pude procesar la solicitud de sistema, Senor.")
        }

        // [NUEVO] Fire-and-forget: tracking no bloquea ni contamina el resultado
        try {
            val actionId = command.toActionId()
            if (actionId != null) {
                userPatternRepository.trackAction(actionId)
            }
        } catch (e: Exception) {
            android.util.Log.w("SystemActionHandler", "Tracking fallido para ${command.javaClass.simpleName}", e)
        }

        return result
    }
}
```

**NOTA:** Se necesita un metodo `toActionId()` en `SystemCommand` o un mapping en `SystemActionHandler`. El mapping mas limpio es un `when` en el handler:

```kotlin
private fun SystemCommand.toActionId(): String? {
    return when (this) {
        is SystemCommand.Call -> "call"
        is SystemCommand.SendSms -> "sms_send"
        is SystemCommand.SetAlarm -> "alarm_set"
        is SystemCommand.OpenApp -> "app_open"
        is SystemCommand.SearchFile -> "file_search"
        is SystemCommand.SetVolume -> "volume_set"
        is SystemCommand.SetTimer -> "timer_set"
        is SystemCommand.Navigate -> "navigate"
        is SystemCommand.OpenSettings -> "settings_open"
        is SystemCommand.SaveMemory -> "memory_save"
        is SystemCommand.CreateNote -> "note_create"
        is SystemCommand.SetBluetooth -> "bluetooth_toggle"
        is SystemCommand.SetBrightness -> "brightness_set"
        is SystemCommand.SetFlashlight -> "flashlight_toggle"
        is SystemCommand.SetAirplaneMode -> "airplane_toggle"
        is SystemCommand.SetMobileData -> "mobile_data_toggle"
        is SystemCommand.SetWifi -> "wifi_toggle"
        is SystemCommand.TakePhoto -> "photo_take"
        is SystemCommand.Vibrate -> "vibrate"
        is SystemCommand.SearchGoogle -> "google_search"
        is SystemCommand.OpenYouTube -> "youtube_open"
        is SystemCommand.PlayMusic -> "music_play"
        // ... resto de comandos ...
        else -> null  // Comandos sin tracking (SetAssistantMode, etc.)
    }
}
```

---

## 4. Flujo de Datos Completo (v2)

```
======================================================================
 FLUJO 1: REGISTRO DE ACCION (tiempo real, fire-and-forget)
======================================================================

 Usuario ejecuta "toggle WiFi"
          |
          v
 SystemActionHandler.execute(command)
          |
          v (ejecutar accion existente)
          rawMessage = "WiFi activado"
          |
          v (formatear respuesta)
          result = ActionResult.Success("WiFi activado, Senor.")
          |
          v (fire-and-forget, DESPUES del try-catch)
          userPatternRepository.trackAction("wifi_toggle")
          |
          +--> ContextAggregatorRepository.getAggregatedContext()
          |     +--> TemporalContext { dayOfWeek=MONDAY, hour=14 }
          |     +--> DayPeriod.fromHour(14) = TARDE
          |     |     hourRange = 14..19
          |     +--> LocationType.HOME
          |
          +--> PatternContext(MONDAY, 14, 19, HOME, null)
          |
          +--> recordOccurrence("wifi_toggle", patternContext)
                |
                +--> [Si existe] findByActionAndContext() -> O(1)
                |     +--> frequency: 3 -> 4
                |     +--> confidence: 0.56 -> 0.67 (establecido!)
                |     +--> insertPattern(updated)
                |
                +--> [Si no existe]
                      +--> frequency: 1
                      +--> confidence: 0.1
                      +--> insertPattern(new)

          |
          v
 return result (el usuario YA tiene su respuesta)


======================================================================
 FLUJO 2: EVALUACION PREDICTIVA (cada 15 min, periodico)
======================================================================

 ProactiveCheckWorker.doWork()
          |
          +--> [A] Evaluacion de reglas (existente, sin cambios)
          |     rules = ruleRepository.getEnabledRules()
          |     context = contextAggregator.getAggregatedContext()
          |     ruleSuggestions = proactiveEngine.evaluate(rules, context)
          |
          +--> [B] Evaluacion de patrones (NUEVO)
          |     patterns = patternRepository.getFrequentPatterns(3)
          |         +--> SELECT * FROM user_patterns WHERE frequency >= 3
          |     established = patterns.filter { it.isEstablished }
          |         +--> confidence >= 0.6
          |     patternSuggestions = predictiveEngine.generateSuggestions(
          |         context, established
          |     )
          |         +--> Filtra: same dayOfWeek AND hour in range (DayPeriod)
          |         +--> Ordena: confidence DESC
          |         +--> Take: top 3
          |         +--> Map: to ProactiveSuggestion(source="pattern:{id}")
          |
          +--> [C] Entrega unificada
                allSuggestions = ruleSuggestions + patternSuggestions
                suggestionManager.submitSuggestions(allSuggestions)
                    |
                    +--> Filtro 1: isSystemProactiveEnabled
                    +--> Filtro 2: quietHours
                    +--> Filtro 3: cooldown (por source, independiente)
                    +--> Filtro 4: expiracion (60 min)
                    |
                    +--> _activeSuggestions.value = filtered
                          +--> UI consume StateFlow


======================================================================
 FLUJO 3: LIMPIEZA DIARIA (existente, sin cambios)
======================================================================

 PatternLearnerWorker.doWork() (1 vez al dia)
          |
          +--> patternRepository.clearOldPatterns(30)
                +--> DELETE FROM user_patterns WHERE lastSeen < (now - 30 dias)
```

---

## 5. DI Module (ProactiveModule -- sin cambios)

```kotlin
// core/data/src/main/kotlin/com/screenassistant/core/data/di/ProactiveModule.kt
// NO SE MODIFICA -- PredictiveEngine se resuelve automaticamente via @Inject constructor

@Module
@InstallIn(SingletonComponent::class)
abstract class ProactiveModule {

    // @Binds existentes (sin cambios)
    @Binds @Singleton
    abstract fun bindProactiveRuleRepository(impl: ProactiveRuleRepositoryImpl): ProactiveRuleRepository

    @Binds @Singleton
    abstract fun bindUserPatternRepository(impl: UserPatternRepositoryImpl): UserPatternRepository

    @Binds @Singleton
    abstract fun bindContextAggregatorRepository(impl: ContextAggregatorRepositoryImpl): ContextAggregatorRepository

    // companion object: @Provides Clock (sin cambios)
}
```

**Por que no hay cambios en DI:**
- `PredictiveEngine` es una clase con `@Inject constructor` en `core:domain` (modulo JVM puro). Hilt lo resuelve automaticamente.
- `UserPatternRepositoryImpl` ya esta bindeado. Su nueva dependencia `ContextAggregatorRepository` ya esta bindeada en este mismo modulo.
- `SystemActionHandler` ya es `@Singleton` con `@Inject constructor`. Anadir `UserPatternRepository` es transparente para Hilt.

---

## 6. Testabilidad

### Test: UserPatternRepositoryImpl.trackAction()

```kotlin
// core/data/src/test/java/com/screenassistant/core/data/repository/UserPatternRepositoryImplTest.kt
class UserPatternRepositoryImplTest {

    private val dao = mockk<UserPatternDao>(relaxed = true)
    private val contextAggregator = mockk<ContextAggregatorRepository>()
    private val repository = UserPatternRepositoryImpl(dao, contextAggregator)

    @Test
    fun `trackAction builds PatternContext using DayPeriod`() = runTest {
        // Arrange
        val now = LocalDateTime.of(2026, 9, 20, 14, 30)  // TARDE (14..19)
        val context = AggregatedContext.withDefaults(
            TemporalContext.now(now)
        ).copy(location = LocationType.HOME)
        coEvery { contextAggregator.getAggregatedContext() } returns context
        coEvery { dao.findByActionAndContext(any(), any(), any(), any(), any()) } returns null

        // Act
        repository.trackAction("wifi_toggle", "com.android.settings")

        // Assert: PatternContext usa DayPeriod.hourRange
        coVerify {
            dao.insertPattern(withArg { entity ->
                assertEquals("MONDAY", entity.dayOfWeek)
                assertEquals(14, entity.hourStart)   // TARDE.hourRange.first
                assertEquals(19, entity.hourEnd)     // TARDE.hourRange.last
                assertEquals("HOME", entity.locationType)
                assertEquals("com.android.settings", entity.screenApp)
            })
        }
    }

    @Test
    fun `trackAction with null screenApp works correctly`() = runTest {
        // Arrange
        val context = AggregatedContext.withDefaults(
            TemporalContext.now(LocalDateTime.of(2026, 9, 20, 8, 0))  // MANANA (6..11)
        ).copy(location = LocationType.WORK)
        coEvery { contextAggregator.getAggregatedContext() } returns context
        coEvery { dao.findByActionAndContext(any(), any(), any(), any(), any()) } returns null

        // Act
        repository.trackAction("alarm_set")

        // Assert
        coVerify {
            dao.insertPattern(withArg { entity ->
                assertEquals(6, entity.hourStart)    // MANANA.hourRange.first
                assertEquals(11, entity.hourEnd)     // MANANA.hourRange.last
                assertEquals("WORK", entity.locationType)
                assertNull(entity.screenApp)
            })
        }
    }
}
```

### Test: UserPatternRepositoryImpl.findExistingPattern (mejorado)

```kotlin
@Test
fun `findExistingPattern uses DAO query O(1) instead of SELECT ALL`() = runTest {
    // Arrange: patron existente en DB
    val existingEntity = UserPatternEntity(
        id = "test-id",
        action = "wifi_toggle",
        dayOfWeek = "MONDAY",
        hourStart = 14,
        hourEnd = 19,
        locationType = "HOME",
        frequency = 3,
        confidence = 0.56f
    )
    coEvery {
        dao.findByActionAndContext("wifi_toggle", "MONDAY", 14, 19, "HOME")
    } returns existingEntity

    val pattern = PatternContext(
        dayOfWeek = DayOfWeek.MONDAY,
        hourStart = 14,
        hourEnd = 19,
        location = LocationType.HOME
    )

    // Act
    repository.recordOccurrence("wifi_toggle", pattern)

    // Assert: usa findByActionAndContext, NO getFrequentPatterns(0)
    coVerify(exactly = 1) {
        dao.findByActionAndContext("wifi_toggle", "MONDAY", 14, 19, "HOME")
    }
    coVerify(exactly = 0) { dao.getFrequentPatterns(0) }
}
```

### Test: PredictiveEngine (clase directa)

```kotlin
// core/domain/src/test/java/com/screenassistant/core/domain/engine/PredictiveEngineTest.kt
class PredictiveEngineTest {

    private val clock = Clock.fixed(
        Instant.parse("2026-09-20T14:30:00Z"),
        ZoneId.of("America/Argentina/Buenos_Aires")
    )
    private val engine = PredictiveEngine(clock)

    @Test
    fun `generateSuggestions returns matching patterns`() {
        val context = AggregatedContext.withDefaults(
            TemporalContext.now(LocalDateTime.of(2026, 9, 20, 14, 30))
        )
        val patterns = listOf(
            UserPattern(
                action = "wifi_toggle",
                context = PatternContext(DayOfWeek.SATURDAY, 14, 19, LocationType.HOME),
                frequency = 5,
                lastSeen = Instant.now(),
                confidence = 0.74f
            )
        )

        val suggestions = engine.generateSuggestions(context, patterns)

        assertEquals(1, suggestions.size)
        assertEquals("pattern:${patterns[0].id}", suggestions[0].source)
    }

    @Test
    fun `generateSuggestions returns empty when no match`() {
        val context = AggregatedContext.withDefaults(
            TemporalContext.now(LocalDateTime.of(2026, 9, 20, 14, 30))
        )
        val patterns = listOf(
            UserPattern(
                action = "wifi_toggle",
                context = PatternContext(DayOfWeek.MONDAY, 6, 11, LocationType.HOME),
                frequency = 5,
                lastSeen = Instant.now(),
                confidence = 0.74f
            )
        )

        val suggestions = engine.generateSuggestions(context, patterns)

        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `generateSuggestions limits to MAX_SUGGESTIONS`() {
        val context = AggregatedContext.withDefaults(
            TemporalContext.now(LocalDateTime.of(2026, 9, 20, 14, 30))
        )
        val patterns = (1..10).map {
            UserPattern(
                action = "action_$it",
                context = PatternContext(DayOfWeek.SATURDAY, 14, 19, LocationType.HOME),
                frequency = it,
                lastSeen = Instant.now(),
                confidence = 0.1f + 0.9f * (1f - Math.exp(-it / 5.0)).toFloat()
            )
        }

        val suggestions = engine.generateSuggestions(context, patterns)

        assertEquals(PredictiveEngine.MAX_SUGGESTIONS, suggestions.size)
    }
}
```

---

## 7. Criterios de Aceptacion Verificables

| ID | Criterio | Verificacion |
|----|----------|-------------|
| CA-1.1 | trackAction construye PatternContext con DayPeriod | Test: verify recordOccurrence con hourStart/hourEnd de DayPeriod |
| CA-1.2 | trackAction funciona con screenApp null | Test: patternContext.screenApp == null |
| CA-1.3 | trackAction es non-blocking (suspend) | Compilacion: firma suspend fun |
| CA-2.1 | PredictiveEngine es clase directa (no interfaz) | Revision de codigo: sin interface keyword |
| CA-2.2 | generateSuggestions retorna match temporal | Test: mismo dia + hora en rango -> 1 sugerencia |
| CA-2.3 | generateSuggestions retorna vacio sin match | Test: dia diferente -> 0 sugerencias |
| CA-2.4 | generateSuggestions respeta MAX_SUGGESTIONS | Test: 10 patrones -> 3 sugerencias |
| CA-3.1 | findExistingPattern usa query SQL | Test: verify findByActionAndContext, NO getFrequentPatterns(0) |
| CA-3.2 | findExistingPattern es O(1) | Verificacion: query con WHERE + LIMIT 1 |
| CA-4.1 | tracking fire-and-forget no contamina resultado | Test: trackAction falla -> resultado exitoso |
| CA-4.2 | ProactiveCheckWorker evalua patrones | Test de integracion: verify predictiveEngine.generateSuggestions |
| CA-4.3 | Reglas existentes no se rompen | Test de regresion: ProactiveEngineTest pasa sin cambios |
| CA-5.1 | DI por constructor en todos los componentes | Revision de codigo: sin @Inject en campos |
| CA-5.2 | Sin cambios en UserPatternEntity | Diff: 0 cambios |
| CA-5.3 | Sin cambios en ProactiveModule | Diff: 0 cambios |
| CA-5.4 | UserPatternRepository.kt solo agrega 1 metodo | Diff: +1 suspend fun trackAction() |

---

## 8. Archivos a Crear/Modificar (Lista Final)

### Crear (2 archivos)

| # | Ruta | Descripcion |
|---|------|-------------|
| 1 | `core/domain/src/main/kotlin/com/screenassistant/core/domain/engine/PredictiveEngine.kt` | Clase directa del motor predictivo |
| 2 | `core/domain/src/test/java/com/screenassistant/core/domain/engine/PredictiveEngineTest.kt` | Tests unitarios del motor |

### Modificar (5 archivos)

| # | Ruta | Cambio |
|---|------|--------|
| 1 | `core/domain/src/main/kotlin/com/screenassistant/core/domain/repository/UserPatternRepository.kt` | +1 metodo: trackAction() |
| 2 | `core/data/src/main/kotlin/com/screenassistant/core/data/local/UserPatternDao.kt` | +1 query: findByActionAndContext() |
| 3 | `core/data/src/main/kotlin/com/screenassistant/core/data/repository/UserPatternRepositoryImpl.kt` | +2 metodos, +1 inyeccion, -1 privado |
| 4 | `service/system/src/main/kotlin/com/screenassistant/service/system/SystemActionHandler.kt` | +1 inyeccion, +fire-and-forget block |
| 5 | `core/data/src/main/kotlin/com/screenassistant/core/data/proactive/ProactiveCheckWorker.kt` | +2 inyecciones, +bloque patrones |

### Modificar (tests existentes, agregar test cases)

| # | Ruta | Cambio |
|---|------|--------|
| 1 | `core/data/src/test/java/com/screenassistant/core/data/repository/UserPatternRepositoryImplTest.kt` | +tests de trackAction y findOrCreate mejorado |
| 2 | `core/data/src/test/java/com/screenassistant/core/data/local/UserPatternDaoTest.kt` | +test de findByActionAndContext |
| 3 | `service/system/src/test/java/com/screenassistant/service/system/SystemActionHandlerTest.kt` | +test de fire-and-forget tracking |
