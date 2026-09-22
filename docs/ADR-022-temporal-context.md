# ADR-022: Contexto Temporal

## Estado: BORRADOR (pendiente de revisión QA + Desarrollador)

## Fecha: 2026-08-29

## Contexto

ScreenAssistant necesita saber **cuándo** ocurre cada interacción para habilitar comportamientos contextualizados (Fase 2 de J.A.R.V.I.S. — Consciencia Contextual). Actualmente el asistente no tiene noción de hora, día de la semana, período del día, ni festivos. Esto bloquea:

1. Respuestas adaptativas al contexto temporal ("Buenos días" vs "Buenas noches")
2. Comandos que dependen del tiempo ("¿qué voy a hacer hoy?", "¿es festivo?")
3. Sugerencias proactivas futuras ("Son las 8am, ¿quieres tu rutina matutina?")
4. Decisiones de negocio con discriminación temporal (horarios laborales vs ocio)

### Requisitos
- Hora y fecha actual
- Día de la semana (laborable vs festivo)
- Período del día (madrugada, mañana, mediodía, tarde, noche)
- ¿Es fin de semana?
- ¿Es festivo? (configurable por el usuario)
- Disponible para ViewModel y sistema de comandos
- Testeable en JVM puro (core/domain)
- Extensible (fácil de añadir más contexto temporal)
- Datos de fuentes del sistema (java.time)

### Requisitos no funcionales
- JVM puro en core/domain (sin dependencias Android)
- Implementación en core/data con primitivas de Android
- Tests unitarios completos con cobertura mínima 80%
- Sin dependencias nuevas externas

## Decisiones

### Decisión 1: `data class` inmutable en core/domain (NO `StateFlow` reactivo)

El modelo `TemporalContext` será un `data class` inmutable generado bajo demanda. Razones:

- **No es estado que cambie con el tiempo** como el monitoreo de pantalla — es un **snapshot** del momento actual. Un StateFlow implicaría un timer/poller que actualiza periódicamente, desperdiciando recursos (el contexto temporal se consulta, no se observa).
- **Consistencia**: Un solo point-in-time evita carreras donde dos lecturas obtienen minutos distintos.
- **Simplicidad**: `TemporalContext.now()` es más simple de testear que un StateFlow con ticker.
- **Consumo típico**: El ViewModel lo consulta al inicio de `sendMessage()` o al construir el prompt para Gemini, no necesita updates reactivos.

### Decisión 2: Lógica de "¿es festivo?" en core/domain (sin dependencia Android)

La lógica de festivos se divide en dos capas:

- **core/domain** (`TemporalContext.companion`): contiene la regla pura "dayOfWeek in SATURDAY/SUNDAY → esFinDeSemana". La lógica de festivos configurables (lista de fechas) se resuelve con un parámetro `Set<LocalDate>` que el caller provee.
- **core/data** (`TemporalRepositoryImpl`): carga la lista de festivos del usuario desde un almacenamiento persistente (DataStore/SharedPreferences) y la pasa al domain como `Set<LocalDate>`. La lógica de persistencia NO pertenece a domain.

Razón: `core/domain` es JVM puro (usa `java.time`). `java.time` ya es parte del standard library de Java 8+ (y kotlin-stdlib lo incluye en JVM). **NO se necesitan dependencias Android**.

### Decisión 3: `TemporalRepository` como interfaz en core/domain

Siguiendo el patrón establecido (ScreenContextRepository, MemoryRepository):

```
interface TemporalRepository {
    fun getCurrentContext(): TemporalContext
    fun getHolidayDates(): Set<LocalDate>
    suspend fun saveHolidayDate(date: LocalDate)
    suspend fun removeHolidayDate(date: LocalDate)
}
```

- `getCurrentContext()` → opera bajo demanda (no reactivo)
- `getHolidayDates()` → retorna las fechas festivas configuradas por el usuario
- `saveHolidayDate/removeHolidayDate` → CRUD de festivos personalizados

### Decisión 4: Calculado bajo demanda (NO caché)

`getCurrentContext()` genera un nuevo `TemporalContext` en cada llamada. Razones:

- La información temporal cambia cada minuto → cachear es contraproducente
- El costo de `LocalDateTime.now()` es despreciable (~nanosegundos)
- Evita invalidación de caché y bugs de stale data
- Consistencia con el patrón de `CaptureScreenContextUseCase.getScreenText()` que lee `screenText.value` directamente

### Decisión 5: Primitivas `java.time` (NO `Calendar`)

Uso exclusivo de `java.time` (LocalDateTime, DayOfWeek, LocalDate):

- API moderna, inmutable, thread-safe
- Disponible en JVM puro (core/domain)
- `Calendar` es legacy, mutable, y propenso a bugs de timezone
- `java.time` ya está en kotlin-stdlib para JVM — 0 dependencias nuevas

### Decisión 6: Sealed class para `DayPeriod`

```kotlin
sealed class DayPeriod {
    data object Madrugada : DayPeriod()  // 00:00 - 05:59
    data object Manana : DayPeriod()     // 06:00 - 11:59
    data object Mediodia : DayPeriod()   // 12:00 - 13:59
    data object Tarde : DayPeriod()      // 14:00 - 19:59
    data object Noche : DayPeriod()      // 20:00 - 23:59
}
```

Razón: Los períodos son enumeración cerrada (nunca habrá un "mega-período"). Sealed class permite `when` exhaustivo sin `else`. Consistente con `ScreenMonitoringState` (sealed class).

### Decisión 7: Integración con sistema de comandos vía prompt enrichment

El contexto temporal NO genera comandos nuevos. Se integra como **enriquecimiento del contexto** en el pipeline existente:

1. **PromptBuilder**: `ScreenAnalysisPromptBuilder` recibe `TemporalContext` y lo inyecta en el prompt para Gemini
2. **ViewModel**: Consulta `getTemporalContextUseCase()` antes de enviar a Gemini, añade contexto al prompt
3. **Comandos directos**: Futuros comandos como "¿qué hora es?" pueden usarlo

No se modifica `SystemCommandParser` — el contexto temporal es read-only background info, no un comando accionable.

### Decisión 8: `GetTemporalContextUseCase` como caso de uso puro

```kotlin
class GetTemporalContextUseCase @Inject constructor(
    private val temporalRepository: TemporalRepository
) {
    operator fun invoke(): TemporalContext =
        temporalRepository.getCurrentContext()
}
```

Siguiendo el patrón de `AnalyzeScreenUseCase` (constructor con inyección, `operator fun invoke()`).

## Diagrama de Capas

```
┌─────────────────────────────────────────────────────────────┐
│                   FEATURE: OVERLAY                           │
│                                                             │
│  OverlayViewModel                                           │
│    ├── inyecta GetTemporalContextUseCase                    │
│    ├── en sendMessage(): temporalContext = useCase()         │
│    └── pasa temporalContext al PromptBuilder                 │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                   CORE:DOMAIN (JVM puro)                     │
│                                                             │
│  TemporalContext (data class inmutable)                     │
│    ├── dateTime: LocalDateTime                              │
│    ├── dayOfWeek: DayOfWeek                                 │
│    ├── dayPeriod: DayPeriod (sealed)                        │
│    ├── esFinDeSemana: Boolean                               │
│    ├── esFestivo: Boolean                                   │
│    └── companion object { fun now(...): TemporalContext }   │
│                                                             │
│  TemporalRepository (interfaz)                              │
│    ├── getCurrentContext(): TemporalContext                  │
│    ├── getHolidayDates(): Set<LocalDate>                    │
│    ├── saveHolidayDate(LocalDate)                           │
│    └── removeHolidayDate(LocalDate)                         │
│                                                             │
│  GetTemporalContextUseCase                                   │
│    └── operator fun invoke(): TemporalContext               │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                   CORE:DATA                                  │
│                                                             │
│  TemporalRepositoryImpl                                     │
│    ├── getCurrentContext(): TemporalContext.now(holidays)    │
│    ├── getHolidayDates(): Set<LocalDate> (DataStore)        │
│    ├── saveHolidayDate/removeHolidayDate                    │
│    └── inyecta holidayStore                                 │
│                                                             │
│  HolidayStore (DataStore wrapper)                           │
│    ├── holidaysFlow: Flow<Set<LocalDate>>                   │
│    ├── getHolidays(): Set<LocalDate>                        │
│    └── save/remove LocalDate                                │
│                                                             │
│  DataModule                                                 │
│    └── @Binds TemporalRepositoryImpl → TemporalRepository   │
└─────────────────────────────────────────────────────────────┘
```

## Estructura de Archivos

### Crear (core/domain)
| Archivo | Descripción |
|---------|-------------|
| `core/domain/model/TemporalContext.kt` | Data class + sealed class DayPeriod + companion `now()` |
| `core/domain/repository/TemporalRepository.kt` | Interfaz del repositorio |
| `core/domain/usecase/GetTemporalContextUseCase.kt` | Caso de uso |

### Crear (core/data)
| Archivo | Descripción |
|---------|-------------|
| `core/data/repository/TemporalRepositoryImpl.kt` | Implementación |
| `core/data/util/HolidayStore.kt` | DataStore para festivos personalizados |

### Crear (tests)
| Archivo | Descripción |
|---------|-------------|
| `core/domain/src/test/.../TemporalContextTest.kt` | Tests del modelo + lógica pura |
| `core/domain/src/test/.../GetTemporalContextUseCaseTest.kt` | Tests del caso de uso |
| `core/data/src/test/.../TemporalRepositoryImplTest.kt` | Tests de la implementación |

### Modificar (con justificación)
| Archivo | Cambios | Justificación |
|---------|---------|---------------|
| `core/data/src/main/.../DataModule.kt` | +@Binds TemporalRepository | Binding de DI, sigue patrón existente |
| `core/domain/src/main/.../ScreenAnalysisPromptBuilder.kt` | +TemporalContext param | Enriquecimiento de prompt (Fase 2 JARVIS) |
| `feature/overlay/OverlayViewModel.kt` | +inyectar useCase, pasar contexto | Integración con pipeline existente |

### NO modificar
| Archivo | Razón |
|---------|-------|
| `SystemCommandParser.kt` | Contexto temporal es background info, no genera comandos |
| `CommandMarkers.kt` | No hay marcadores nuevos |
| `OverlayUiState.kt` | Contexto temporal no se muestra directamente en UI |

## Contratos de Interfaces Clave

### TemporalContext (nuevo)
```kotlin
/**
 * Snapshot inmutable del contexto temporal en un punto dado.
 * Generado bajo demanda vía [TemporalContext.now].
 *
 * @property dateTime Fecha y hora del snapshot
 * @property dayOfWeek Día de la semana (java.time.DayOfWeek)
 * @property dayPeriod Período del día (madrugada, mañana, etc.)
 * @property esFinDeSemana true si es sábado o domingo
 * @property esFestivo true si la fecha está en la lista de festivos del usuario
 */
data class TemporalContext(
    val dateTime: LocalDateTime,
    val dayOfWeek: DayOfWeek,
    val dayPeriod: DayPeriod,
    val esFinDeSemana: Boolean,
    val esFestivo: Boolean
) {
    companion object {
        /**
         * Crea un TemporalContext con la fecha/hora actual del sistema.
         * @param holidayDates Fechas festivas configuradas por el usuario
         */
        fun now(holidayDates: Set<LocalDate> = emptySet()): TemporalContext {
            val now = LocalDateTime.now()
            val date = now.toLocalDate()
            return TemporalContext(
                dateTime = now,
                dayOfWeek = now.dayOfWeek,
                dayPeriod = DayPeriod.fromHour(now.hour),
                esFinDeSemana = now.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                esFestivo = date in holidayDates
            )
        }
    }
}

sealed class DayPeriod {
    data object Madrugada : DayPeriod()
    data object Manana : DayPeriod()
    data object Mediodia : DayPeriod()
    data object Tarde : DayPeriod()
    data object Noche : DayPeriod()

    companion object {
        fun fromHour(hour: Int): DayPeriod = when (hour) {
            in 0..5 -> Madrugada
            in 6..11 -> Manana
            in 12..13 -> Mediodia
            in 14..19 -> Tarde
            else -> Noche // 20..23
        }
    }
}
```

### TemporalRepository (nuevo)
```kotlin
interface TemporalRepository {
    /** Genera un snapshot del contexto temporal actual. */
    fun getCurrentContext(): TemporalContext

    /** Retorna las fechas festivas configuradas por el usuario. */
    fun getHolidayDates(): Set<LocalDate>

    /** Añade una fecha festiva personalizada. */
    suspend fun saveHolidayDate(date: LocalDate)

    /** Elimina una fecha festiva personalizada. */
    suspend fun removeHolidayDate(date: LocalDate)
}
```

### GetTemporalContextUseCase (nuevo)
```kotlin
class GetTemporalContextUseCase @Inject constructor(
    private val temporalRepository: TemporalRepository
) {
    /**
     * Genera un snapshot del contexto temporal actual.
     * Incluye festivos del usuario.
     */
    operator fun invoke(): TemporalContext =
        temporalRepository.getCurrentContext()
}
```

### HolidayStore (nuevo, core/data)
```kotlin
/**
 * DataStore Preferences para persistir festivos personalizados del usuario.
 * Serialización: Set<LocalDate> → Set<String> (ISO-8601 "yyyy-MM-dd").
 */
object HolidayStore {
    suspend fun getHolidays(context: Context): Set<LocalDate>
    suspend fun saveHoliday(context: Context, date: LocalDate)
    suspend fun removeHoliday(context: Context, date: LocalDate)
    fun holidaysFlow(context: Context): Flow<Set<LocalDate>>
}
```

### ScreenAnalysisPromptBuilder (modificado)
```kotlin
// Firma actual:
fun buildPrompt(screenText: String, userQuestion: String = ""): String

// Firma propuesta:
fun buildPrompt(
    screenText: String,
    userQuestion: String = "",
    temporalContext: TemporalContext? = null
): String
```
El parámetro `temporalContext` es nullable y con valor por defecto null para mantener backward compatibility.

## Estrategia de Testing

### core/domain (JVM puro, sin Android)
1. **TemporalContextTest**: Verificar `now()` genera datos correctos, DayPeriod.fromHour cubre todos los rangos, esFinDeSemana con Sábado/Domingo, esFestivo con set de fechas
2. **GetTemporalContextUseCaseTest**: Mock de TemporalRepository, verificar que invoke() delega correctamente

### core/data (Android, con Robolectric)
3. **TemporalRepositoryImplTest**: Verificar que getCurrentContext() genera un TemporalContext válido, que save/removeHolidayDate persisten correctamente
4. **HolidayStoreTest**: Verificar serialización/deserialización de LocalDate en DataStore

### Cobertura estimada
- TemporalContext.kt: ~95% (lógica pura, fácil de testear)
- TemporalRepositoryImpl.kt: ~85% (requiere DataStore mock)
- GetTemporalContextUseCaseTest.kt: ~90% (simple delegación)
- HolidayStoreTest.kt: ~80% (DataStore operations)
- **Promedio general**: ~85% (cumple umbral del 80%)

## Consecuencias

### Positivas
- **Sin dependencias nuevas**: java.time ya está en kotlin-stdlib
- **JVM puro en domain**: testeable sin Robolectric ni Android
- **Extensible**: añadir "¿es horario laboral?" requiere solo un campo más en data class + lógica en companion
- **Backward compatible**: todos los parámetros opcionales, sin cambios en firmas existentes
- **Coherencia arquitectónica**: sigue patrón Repository → UseCase → ViewModel

### Negativas
- **Cálculo bajo demanda**: si muchos consumidores llaman `now()` simultáneamente, cada uno obtiene un timestamp distinto (mínimo, despreciable)
- **Festivos hardcodeados**: la lista inicial de festivos nacionales será vacía (el usuario debe añadirlos); futura mejora: festivos por país automático
- **No reactivo**: si se necesitara "cambia el prompt cuando cambia la hora" (cada minuto), habría que añadir un timer. Por ahora es innecesario

## ⚠️ AMBIGÜEDADES DETECTADAS

1. **Festivos por defecto**: ¿La app debe incluir festivos nacionales del país del usuario? → **Interpretación**: NO en esta fase. El usuario añade festivos manualmente. Futura mejora: detectar país del locale y precargar festivos.

2. **Zona horaria**: `LocalDateTime.now()` usa la zona horaria del dispositivo. Es correcto (el usuario quiere saber "su" hora local). No se necesita configuración explícita.

3. **Modificación de ScreenAnalysisPromptBuilder**: La tarea dice "NO tocar archivos existentes sin justificación". La modificación está justificada: es la integración principal del contexto temporal con el pipeline de IA. El parámetro es nullable con default null → **sin breaking change**.

## 🔴 BLOQUEOS

Ninguno. Diseño completo y accionable.
