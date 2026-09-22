# Comandos exactos para build y verificación de la integración llama.cpp
# ScreenAssistant — core:ai:local

## 1. Inicializar submódulo llama.cpp (una sola vez)

```bash
cd C:\Users\QuintiVG\AndroidStudioProjects\ScreenAssistant\core\ai\local

# Añadir submódulo apuntando al repo oficial
git submodule add https://github.com/ggml-org/llama.cpp.git llama.cpp

# Entrar y fijar commit b4270 (release estable agosto 2024)
cd llama.cpp
git checkout b4270

# Verificar
git log --oneline -1
# Debe mostrar: b4270 ... (mensaje del commit)
```

## 2. Verificar estructura esperada

```bash
ls -la llama.cpp/
# Debe contener: CMakeLists.txt, llama.cpp, ggml.c, include/, etc.

ls -la models/
# Debe contener: tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf (637 MB)
```

## 3. Build completo (primera vez — compila llama.cpp ~2-3 min)

```bash
cd C:\Users\QuintiVG\AndroidStudioProjects\ScreenAssistant

# Clean build
./gradlew :core:ai:local:clean :core:ai:local:assembleDebug

# Verificar que se generaron los .so para las 3 ABIs
ls -la core/ai/local/build/intermediates/cmake/debug/obj/
# Debe mostrar: arm64-v8a/, armeabi-v7a/, x86_64/
# Cada uno con: libllama_jni.so
```

## 4. Verificar símbolos exportados (sanity check)

```bash
# arm64-v8a (dispositivo real)
$ANDROID_NDK/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-nm \
  core/ai/local/build/intermediates/cmake/debug/obj/arm64-v8a/libllama_jni.so \
  | grep "Java_com_screenassistant_core_ai_local_llama_bridge_LlamaCppBridge"

# Debe mostrar 9 símbolos (nativeInit, nativeDestroy, nativeLoadModel, nativeFreeModel,
# nativeGenerate, nativeStreamGenerate, nativeIsReady, nativeTokenCount, + 1 interno)
```

## 5. Test unitario (JVM, sin .so — usa MockK)

```bash
./gradlew :core:ai:local:testDebugUnitTest
# Debe pasar: LlamaCppEngineTest, LlamaModelManagerTest, etc.
```

## 6. Test de integración REAL (requiere dispositivo/emulador conectado)

```bash
# Conectar dispositivo arm64-v8a (físico o emulador)
adb devices

# Instalar test APK
./gradlew :core:ai:local:connectedDebugAndroidTest

# O ejecutar test específico
./gradlew :core:ai:local:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.screenassistant.core.ai.local.llama.LlamaCppEngineIntegrationTest
```

## 7. Verificación manual en dispositivo (adb shell)

```bash
# Verificar que la librería se carga
adb shell run-as com.screenassistant.core.ai.local ls -la /data/app/~~*/com.screenassistant.core.ai.local-*/lib/arm64/
# Debe mostrar: libllama_jni.so

# Ver logs de inicialización
adb logcat -s LlamaCppBridge LlamaCppEngine
```

## 8. Benchmark rápido (opcional)

```bash
# En dispositivo, medir tiempo de primera inferencia
adb shell am start -n com.screenassistant.debug/com.screenassistant.MainActivity
# Luego en logcat buscar: "Motor llama.cpp inicializado" y "Generados X chars"
```

## 9. Troubleshooting común

| Error | Causa | Solución |
|-------|-------|----------|
| `llama.cpp no encontrado` | Submódulo no inicializado | `git submodule update --init --recursive` |
| `undefined reference to llama_*` | CMake no linkeó llama.cpp | Verificar `target_link_libraries(llama_jni PRIVATE llama ggml ...)` en CMakeLists.txt |
| `UnsatisfiedLinkError: dlopen failed: library "libc++_shared.so" not found` | STL mismatch | `packagingOptions.pickFirsts += "lib/**/libc++_shared.so"` en build.gradle.kts |
| `OOM al cargar modelo` | n_ctx muy grande para RAM | Fallback automático en `LlamaCppEngine.initialize()` (512→1024→2048) |
| `nativeLoadModel retorna 0` | Ruta modelo incorrecta | Verificar `LlamaModelManager.getLocalModelPath()` retorna path válido |
| `SIGSEGV en llama_decode` | Threading race | Verificar `mutex` en bridge C++ y `@Volatile` handles en Kotlin |

## 10. Build para release (APK firmado)

```bash
./gradlew :core:ai:local:assembleRelease
# Genera: core/ai/local/build/outputs/aar/core-ai-local-release.aar
# Incluye libllama_jni.so strippeado (~30% menor)
```

## Tiempos esperados

| Paso | Tiempo aprox. |
|------|---------------|
| `git submodule add + checkout` | 30-60 seg (descarga ~200 MB) |
| Primera compilación (`assembleDebug`) | 2-3 min (compila llama.cpp desde cero) |
| Builds incrementales | 10-30 seg |
| Test unitarios | < 30 seg |
| Test instrumentado (device) | 1-2 min (incluye carga modelo 637 MB) |

## Notas importantes

1. **NDK versión**: Requiere NDK r26+ (configurado en build.gradle.kts: `ndkVersion = "26.1.10909125"`)
2. **RAM dispositivo**: Mínimo 4 GB recomendado. En 3 GB usar nCtx=512.
3. **Modelo**: TinyLlama Q4_K_M es el único soportado actualmente. Para cambiar modelo, actualizar `LlamaModelCatalog` y re-descargar.
4. **Licencias**: Copiar `llama.cpp/LICENSE` y `models/*.gguf` license a `assets/licenses/` para cumplimiento legal.