# ADR: Integración de Memoria en el Flujo Conversacional

## Estado
Aprobado

## Contexto
La app tiene componentes de memoria (ShortTermMemory, FactExtractor, MemoryStore, DAOs) que existen pero NO están conectados al flujo conversacional principal (`OverlayViewModel.sendMessage()`). Se necesita integrar memoria de corto y largo plazo para que el asistente:

1. Recuerde turnos previos en la sesión (ShortTermMemory)
2. Extraiga hechos del usuario de cada mensaje (FactExtractor)
3. Recupere hechos relevantes para enriquecer prompts (MemoryStore)

## Decisión
Pipeline de memoria de tres etapas en `OverlayViewModel.sendMessage()`:

### Etapa 1: Recuperación (antes del prompt)
- `memoryStore.search(userMessage)` busca entradas relevantes (hechos + conversaciones previas)
- Resultado se inyecta como `memoryContext` en el prompt enviado a Gemini

### Etapa 2: Guardado de turno (después de respuesta exitosa)
- `shortTermMemory.addTurn(MemoryTurn)` — buffer in-memory, síncrono, no persiste

### Etapa 3: Extracción de hechos (async post-respuesta)
- `factExtractor.extractFromMessage(userMessage)` — regex patterns, retorna `List<KnowledgeFact>`
- Cada hecho se guarda como `MemoryEntry(type=FACT)` en `MemoryStore`
- El turno completo también se guarda en `MemoryStore` para historial persistente

### MemoryStoreImpl
- Enrutamiento por `MemoryEntry.type`:
  - CONVERSATION/SCREEN_CONTEXT → `LongTermMemoryDao`
  - FACT/PREFERENCE → `LongTermMemoryDao` + `KnowledgeFactDao` (dual write)
- Búsqueda unificada: consulta ambas tablas y mergea por timestamp
- Mappers internos de dominio ↔ Room entities

## Consecuencias

### Positivas
- El asistente conoce hechos del usuario ("me gusta el café") y los usa en respuestas
- La memoria persiste entre sesiones (MemoryStore con Room)
- Degradación suave: errores de memoria no rompen la conversación
- ShortTermMemory rápida (in-memory) para contexto de sesión

### Negativas
- Duplicación de almacenamiento de FACT en dos tablas (temporal hasta v2)
- `ConversationMemoryRepository` coexistirá con `MemoryStore` (compatibilidad)
- Tests existentes necesitan actualización (3 nuevos parámetros en constructor)

### Riesgos mitigados
- Fallo en `memoryStore.search()` → try-catch silencioso, prompt sin contexto previo
- Fallo en `factExtractor` → hechos no guardados, conversación funciona igual
- Fallo en `memoryStore.save()` post-respuesta → turnos perdidos en persistencia, ShortTermMemory preserva el turno actual

## Fechas
- Creado: 2026-08-31
- Implementación estimada: 1 lote
