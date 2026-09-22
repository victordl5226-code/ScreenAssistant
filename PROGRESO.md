# PROGRESO — ScreenAssistant

## MINILM-INTEG-004 — Integración MiniLM + UI de descarga (2026-09-22, Desarrollador)

- Estado: COMPLETO
- Actualizado: 2026-09-22 por Desarrollador

### Porciones
- [x] P1 — OnDeviceEmbeddingGenerator.kt: ModelAssetRepository + ensureModelLoaded() + fallback — `core/ai/memory/src/main/kotlin/.../semantic/OnDeviceEmbeddingGenerator.kt` + `core/ai/memory/build.gradle.kts` + `core/ai/memory/src/main/kotlin/.../di/SemanticMemoryModule.kt`
- [x] P2 — OnDeviceEmbeddingGeneratorTest.kt (6 tests) — `core/ai/memory/src/test/kotlin/.../semantic/OnDeviceEmbeddingGeneratorTest.kt`
- [x] P3 — Verificación core:ai:memory compila + tests verdes
- [x] P4 — ModelDownloadViewModel.kt — `feature/overlay/src/main/kotlin/.../ui/models/ModelDownloadViewModel.kt`
- [x] P5 — ModelDownloadScreen.kt — `feature/overlay/src/main/kotlin/.../ui/models/ModelDownloadScreen.kt`
- [x] P6 — ModelDownloadViewModelTest.kt (7 tests) — `feature/overlay/src/test/java/.../ui/models/ModelDownloadViewModelTest.kt`
- [x] P7 — Compilation gate + regresión

### Última verificación
- `./gradlew :core:ai:memory:compileDebugKotlin :feature:overlay:compileDebugKotlin` → BUILD SUCCESSFUL
- `./gradlew :feature:overlay:testDebugUnitTest :core:ai:memory:testDebugUnitTest` → BUILD SUCCESSFUL, todos los tests verdes
- `./gradlew :core:ai:memory:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL
- `./gradlew :feature:overlay:compileDebugAndroidTestKotlin` → FALLA PRE-EXISTENTE (OverlaySendMessageTest — no relacionado)

### Notas
- open class OnDeviceEmbeddingGenerator para permitir spyk en tests
- extractModel(), createOrtEnvironment(), createOrtSession(), runInference() son `internal open` para mocking en JVM tests
- Extracción de tar.gz implementada sin dependencias externas (formato TAR manual con GZIPInputStream)
- org.json:json añadido como testImplementation para que loadVocab funcione en JVM tests
- testOptions.unitTests.isReturnDefaultValues = true añadido para android.util.Log stub
- ModelDownloadViewModel sigue patrón OnboardingViewModel (MutableStateFlow + viewModelScope)
- ModelDownloadScreen usa Material Design 3 con animaciones y cards

---

> Checkpoint vivo del Orquestador. Tras cada entrega: se mueve a HISTORIAL.md y se borra.

## VOSK-STT-INTEG-003 — Integración Vosk STT con core:model (2026-09-22, Desarrollador)

- Estado: COMPLETO
- Actualizado: 2026-09-22 por Desarrollador

### Porciones
- [x] P1 — VoskSpeechToTextManager.kt: ModelAssetRepository + ensureModelLoaded() + fallback — `feature/overlay/src/main/kotlin/.../VoskSpeechToTextManager.kt`
- [x] P2 — AssistantOverlayUI.kt: pasar modelAssetRepository al constructor — `feature/overlay/src/main/kotlin/.../AssistantOverlayUI.kt`
- [x] P3 — Tests: VoskSpeechToTextManagerTest (4 tests) — `feature/overlay/src/test/java/.../VoskSpeechToTextManagerTest.kt`
- [x] P4 — Compilation gate + regresión

### Última verificación
- `./gradlew :feature:overlay:compileDebugKotlin` → BUILD SUCCESSFUL
- `./gradlew :feature:overlay:compileDebugUnitTestKotlin` → BUILD SUCCESSFUL
- `./gradlew :feature:overlay:testDebugUnitTest` → BUILD SUCCESSFUL, 116/116 verdes (0 fallos)
- `./gradlew :feature:overlay:compileDebugAndroidTestKotlin` → FALLA pre-existente (OverlaySendMessageTest imports no resueltos — no relacionado)

## PIPER-TTS-INTEG-002 — Integración PiperTTS con core:model (2026-09-22, Desarrollador)

- Estado: COMPLETO
- Actualizado: 2026-09-22 por Desarrollador

### Porciones
- [x] P1 — build.gradle.kts: dependencia core:model — `feature/overlay/build.gradle.kts`
- [x] P2 — PiperTtsManager.kt: ModelAssetRepository + carga lazy + fallback — `feature/overlay/src/main/kotlin/.../PiperTtsManager.kt`
- [x] P3 — Propagación ModelAssetRepository Service→UI→Manager — `AssistantOverlayService.kt` + `AssistantOverlayUI.kt`
- [x] P4 — Tests: PiperTtsManagerTest (4 tests) — `feature/overlay/src/test/java/.../PiperTtsManagerTest.kt`
- [x] P5 — Compilation gate + regresión

### Última verificación
- `./gradlew :feature:overlay:compileDebugKotlin` → BUILD SUCCESSFUL
- `./gradlew :feature:overlay:compileDebugUnitTestKotlin` → BUILD SUCCESSFUL
- `./gradlew :feature:overlay:testDebugUnitTest` → BUILD SUCCESSFUL, 112/112 verdes (0 fallos)
- `./gradlew :feature:overlay:compileDebugAndroidTestKotlin` → FALLA pre-existente (imports compose test/mockk no resueltos — no relacionado con esta porción)

## CORE-MODEL-001 — Infraestructura Base core:model (2026-09-21, Desarrollador)

- Estado: COMPLETO
- Actualizado: 2026-09-21 por Desarrollador

### Porciones
- [x] P1 — build.gradle.kts + settings.gradle.kts — `core/model/build.gradle.kts` + `settings.gradle.kts`
- [x] P2 — Domain layer: ModelAsset, ModelAssetStatus, ModelAssetRepository — `core/model/src/main/kotlin/.../domain/`
- [x] P3 — Data layer: ModelFileStore, GitHubModelDownloader, ModelAssetRepositoryImpl — `core/model/src/main/kotlin/.../data/`
- [x] P4 — DI: ModelModule (Hilt) — `core/model/src/main/kotlin/.../di/ModelModule.kt`
- [x] P5 — Tests: ModelAssetRepositoryImplTest + GitHubModelDownloaderTest — `core/model/src/test/java/.../data/`

## PREDICTIVE-028 — Sistema Predictivo de Patrones (2026-09-20, Desarrollador)

- Estado: COMPLETADO
- Actualizado: 2026-09-20 por Orquestador

## ACTION-ID-001 — Mapeo explícito SystemCommand→actionId (2026-09-21, Desarrollador)

- Estado: COMPLETO
- Actualizado: 2026-09-21 por Desarrollador
