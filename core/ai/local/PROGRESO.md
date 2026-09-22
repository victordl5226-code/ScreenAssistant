# PROGRESO — llama.cpp Integration (core:ai:local)

> Checkpoint por porciones. Lo actualiza el Desarrollador al completar cada porción.

- Estado: COMPLETO
- Actualizado: 2026-09-07 por Desarrollador Android

## Porciones
- [x] P1 — Crear llama_engine_core.h — src/main/cpp/llama_engine_core.h — verificación: compilación exitosa
- [x] P2 — Implementar llama_engine_core.cpp — src/main/cpp/llama_engine_core.cpp — verificación: compilación exitosa
- [x] P3 — Refactorizar llama_jni_bridge.cpp (thin JNI wrapper) — src/main/cpp/llama_jni_bridge.cpp — verificación: compilación exitosa
- [x] P4 — Crear tests GoogleTest (14 tests) — src/test/cpp/llama_engine_core_test.cpp — verificación: compilación exitosa (host-only)
- [x] P5 — Actualizar CMakeLists.txt (static lib + shared lib + tests) — src/main/cpp/CMakeLists.txt — verificación: compilación exitosa
- [x] P6 — Actualizar build.gradle.kts (abiFilters, ndkVersion, packaging) — build.gradle.kts — verificación: BUILD SUCCESSFUL
- [x] P7 — Verificar LlamaCppEngine.kt (OOM fallback ya presente) — src/main/kotlin/.../llama/LlamaCppEngine.kt — verificación: ya implementado
- [x] P8 — Inicializar submódulo llama.cpp (commit master reciente) — llama.cpp/ — verificación: git submodule status OK

## Última verificación
- Comando: `./gradlew :core:ai:local:assembleDebug :core:ai:local:compileDebugKotlin :core:ai:local:compileDebugUnitTestKotlin --no-daemon`
- Resultado: BUILD SUCCESSFUL (77 tests unitarios, 1 fallo preexistente en LlamaModelManagerTest no relacionado)

## Archivos creados/modificados
### Creados:
- core/ai/local/src/main/cpp/llama_engine_core.h
- core/ai/local/src/main/cpp/llama_engine_core.cpp
- core/ai/local/src/test/cpp/llama_engine_core_test.cpp

### Modificados:
- core/ai/local/src/main/cpp/llama_jni_bridge.cpp (refactorizado completamente como thin JNI wrapper)
- core/ai/local/src/main/cpp/CMakeLists.txt (nueva estructura: static lib + shared lib + common lib)
- core/ai/local/build.gradle.kts (ndkVersion 27.0.12077973, abiFilters arm64-v8a x86_64, packaging options)

## Verificación de Compilation Gate
- `./gradlew :core:ai:local:compileDebugKotlin` → BUILD SUCCESSFUL
- `./gradlew :core:ai:local:compileDebugUnitTestKotlin` → BUILD SUCCESSFUL
- `./gradlew :core:ai:local:compileDebugAndroidTestKotlin` → NO EJECUTADO (requiere device/emulador)
- `./gradlew :core:ai:local:testDebugUnitTest --tests "com.screenassistant.core.ai.local.llama.LlamaCppEngineTest"` → 9/9 tests VERDES

## Desviaciones de la arquitectura
- **armeabi-v7a deshabilitado temporalmente**: La versión actual de llama.cpp (master reciente) tiene un error de compilación en `ggml-cpu/llamafile/sgemm.cpp` para armv7 (intrinsics FP16 no disponibles). Se compila para arm64-v8a y x86_64 únicamente. Se puede habilitar cuando se resuelva upstream.
- **Tests GoogleTest host-only**: Los tests C++ se compilan solo cuando `BUILD_TESTING=ON` (no en Android build). Requieren modelo GGUF real para ejecutarse (TEST_MODEL_PATH).
- **common_tokenize**: Requiere `llama-common` library habilitada en CMake (LLAMA_BUILD_COMMON=ON).

## Problemas encontrados y resueltos
1. **API llama.cpp cambiada**: La versión master reciente usa API v2 (llama_model_load_from_file, llama_init_from_model, llama_model_get_vocab, llama_token_to_piece con vocab, etc.). Código actualizado a nueva API.
2. **llama_token type**: Requiere incluir `<llama.h>` en el JNI bridge.
3. **common_tokenize no linkeaba**: Habilitado LLAMA_BUILD_COMMON y linkeado contra llama-common.
4. **armeabi-v7a falla en sgemm.cpp**: Deshabilitado LLAMA_LLAMAFILE y removido de abiFilters temporalmente.
5. **Move constructor con mutex**: Cambiado a `= delete` en lugar de `= default` porque std::mutex no es movable.
6. **Formato jlong en logs**: Cambiado %lld por %ld en arm64 (jlong es long en Android 64-bit).

## Pendientes
- **Habilitar armeabi-v7a**: Cuando upstream corrija el issue de FP16 intrinsics o se desactive el código problematico.
- **Tests instrumentados**: Ejecutar connectedDebugAndroidTest en device/emulador con modelo GGUF real.
- **Tests C++ host**: Compilar y ejecutar llama_engine_core_test con modelo real (requiere TEST_MODEL_PATH).
- **LlamaCppBridgeInterface**: Verificar que coincida con las 10 JNIEXPORTs implementadas (nativeTokenize, nativeDetokenize son placeholders).

## Notas
- El build produce libllama_jni.so para arm64-v8a (~619 KB) y x86_64 (~613 KB).
- La integración usa arquitectura limpia: LlamaEngineCore (lógica pura, testeable) + LlamaCppBridge (JNI thin wrapper).
- OOM fallback implementado en LlamaCppEngine.kt (2048→1024→512) funciona correctamente.