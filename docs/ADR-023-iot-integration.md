# ADR-023: Integración IoT — Smart Home, Wearables y Android Auto

## Estado: APROBADO

## Fecha: 2026-08-31

## Contexto

ScreenAssistant necesita integrar dispositivos IoT para habilitar la Fase 5 del roadmap J.A.R.V.I.S. — Integración IoT. El usuario debe poder controlar dispositivos Matter, ver datos de salud de wearables, y controlar funciones del vehículo desde el asistente.

### Dominios IoT soportados
1. **Smart Home (Matter + Home Assistant)**: Dispositivos conectados, escenas, automatizaciones
2. **Wearables (Health Connect)**: Métricas de salud (pasos, FC, SpO2, sueño)
3. **Android Auto / AAOS**: Navegación, música, llamadas, clima

### Requisitos
- Arquitectura extensible para nuevos dominios IoT
- Sincronización periódica en background (WorkManager)
- UI de configuración y permisos (Jetpack Compose)
- Tests unitarios con cobertura mínima 80%
- Módulos separados (core:iot:domain JVM, core:iot:data Android)

## Decisiones

### Decisión 1: Módulo dual `core:iot:domain` (JVM) + `core:iot:data` (Android)

**Elección**: Separar modelos y repositorios en dos módulos.

**Justificación**:
- `core:iot:domain` es JVM puro (sin dependencias Android) → testeable en JVM estándar
- `core:iot:data` tiene dependencias Android (Room, Health Connect SDK, Car App Library)
- Permite testing rápido en CI sin emulador
- Patrón consistente con `core:domain` + `core:data` existentes

**Alternativas descartadas**:
- Módulo único Android: imposible testear en JVM puro, lento en CI
- Tres módulos (domain/data/android): complejidad innecesaria para 3 dominios

### Decisión 2: `IotRepository` como fachada + repositorios especializados

**Elección**: Un repositorio fachada (`IotRepository`) que delega a repositorios específicos (`MatterDeviceRepository`, `HealthConnectRepository`, `CarAppRepository`).

**Justificación**:
- **Separación de responsabilidades**: Cada dominio tiene su propio repositorio con API específica
- **Fachada para simplificar**: `IotRepository` orquesta la sincronización combinada para UI
- **Testeabilidad**: Cada repositorio se puede mockear independientemente
- **Extensibilidad**: Agregar un nuevo dominio (ej: Cámaras) solo requiere un nuevo repositorio

**Patrón**: Facade + Strategy (cada repositorio implementa su estrategia de sincronización)

### Decisión 3: Health Connect SDK con reflexión (reflection)

**Elección**: Acceder a Health Connect SDK v1.1.0-alpha02 usando `KClass` y reflexión.

**Justificación**:
- El SDK es **alpha02** — API inestable que puede cambiar sin previo aviso
- La reflexión permite resistir cambios de API sin recompilar
- Si la API cambia, solo falla la reflexión (manejable con try-catch), no la compilación
- Alternativa: directly types → rompería en cada actualización del SDK

**Riesgo**: Performance marginalmente menor (aceptable para operaciones de salud que no son en tiempo real)

### Decisión 4: `SyncIotDevicesUseCase` como orquestador de sincronización

**Elección**: Un UseCase que combina datos de todas las fuentes en un `Flow<IotSyncState>`.

**Justificación**:
- **Single source of truth**: La UI observa un solo Flow combinado
- **Separación de concerns**: El UseCase orquesta, los repositorios proveen datos
- **Reactividad**: `combine()` de 6 Flows emite cuando cualquiera cambia
- **Testeable**: Se mockea `IotRepository` y se verifican las combinaciones

### Decisión 5: Trabajo local sin SDK real (Matter alpha)

**Elección**: `MatterDeviceRepositoryImpl` usa StateFlow cache local en lugar del SDK Matter real.

**Justificación**:
- El SDK Matter (`com.google.home:matter-android-sdk`) está en alpha
- No se puede depender de una API que puede cambiar fundamentalmente
- La implementación local permite:
  - Desarrollo y testing sin hardware real
  - swapping futuro por la implementación real cuando el SDK madure
  - La interfaz `MatterDeviceRepository` se mantiene estable

### Decisión 6: ViewModel compartido `feature:overlay` (no duplicar)

**Elección**: Reutilizar `ProactiveViewModel` y `PersonalityViewModel` existentes en `feature:iot`.

**Justificación**:
- **DRY**: No duplicar ViewModels que ya existen
- **Consistencia**: Misma UI overlay para IoT que para otras features
- **Simplicidad**: `feature:iot` solo agrega pantallas de configuración/permisos
- El ViewModel de overlay ya maneja el patrón de UI reactivo

### Decisión 7: `IotSyncWorker` con WorkManager para sincronización background

**Elección**: WorkManager con `PeriodicWorkRequest` para sincronización periódica.

**Justificación**:
- **Garantía de ejecución**: WorkManager maneja Doze, batería, reinicios
- **Periodicidad configurable**: El intervalo se define en el worker
- **Constraint integration**: Solo ejecuta con batería y red
- **Hilt integration**: `@HiltWorker` + `@AssistedInject` para testing

## Consecuencias

### Positivas
- Arquitectura extensible para nuevos dominios IoT
- Testing rápido en CI (core:iot:domain es JVM puro)
- Separación clara de responsabilidades
- UI reutilizable con patrón existente

### Negativas
- Complejidad inicial: 2 módulos + fachada + repositorios
- SDK Matter placeholder: requiere implementación futura
- Health Connect reflexión: performance marginal, código menos legible
- 8 módulos totales en el proyecto: overhead de Gradle

### Riesgos
- SDK Health Connect alpha puede cambiar API → mitigado con reflexión
- SDK Matter alpha puede no madurar → mitigado con implementación local
- WorkManager mínimo 15min → aceptable para sync IoT (no es crítico en tiempo real)

## Archivos clave

| Archivo | Responsabilidad |
|---------|----------------|
| `core/iot/domain/` | Modelos, interfaces, use cases (JVM puro) |
| `core/iot/data/` | Repositorios, Worker, DI (Android) |
| `feature/iot/` | UI Compose (Settings, Permisos, Dashboard) |
| `IotRepository` | Fachada que orquesta sincronización |
| `SyncIotDevicesUseCase` | Combina datos de todas las fuentes |
| `IotSyncWorker` | Sincronización periódica en background |

## Fecha de decisión: 2026-08-31
## Decisiones previas relacionadas: ADR-020 (Monitoreo Continuo), ADR-022 (Contexto Temporal)
