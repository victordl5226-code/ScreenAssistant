# 📦 Entrega del Arquitecto: Integración REAL llama.cpp en core:ai:local

---

## Decisión principal
**Submódulo git `ggml-org/llama.cpp@b4270` en `core/ai/local/llama.cpp` + CMakeLists.txt que compila llama.cpp como librería estática y linkea el bridge JNI contra ella, con ABIs arm64-v8a/armeabi-v7a/x86_64 y flags NEON/SIMD.**

---

## Estructura

### 1. Estructura de archivos nueva
```
core/ai/local/
├── .gitmodules                          # ← NUEVO: registra submódulo
├── llama.cpp/                           # ← NUEVO: submódulo git (commit b4270)
│   ├── CMakeLists.txt                   # (propio de llama.cpp)
│   ├── llama.cpp
│   ├── ggml.c
│   ├── ggml-alloc.c
│   ├── ggml-backend.c
│   ├── common.cpp
│   ├── sampling.cpp
│   └── include/
│       ├── llama.h
│       └── ggml.h
├── src/main/cpp/
│   ├── CMakeLists.txt                   # ← ACTUALIZADO: integra llama.cpp
│   └── llama_jni_bridge.cpp             # ← ACTUALIZADO: implementación real
├── build.gradle.kts                     # ← ACTUALIZADO: abiFilters + CMake args
└── models/
    └── tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf  (637 MB, YA EXISTE)
```

### 2. Módulos Gradle afectados
- Solo `core:ai:local` — ningún otro módulo cambia.

---

## Justificación

### Por qué Submódulo git (Opción A)
| Factor | Decisión |
|--------|----------|
| **Versionado reproducible** | Commit `b4270` fijado en `.gitmodules` — builds idénticos en CI y local |
| **Build offline** | Sin red en tiempo de build — crítico para CI aislado |
| **Control total de flags** | CMake expone `GGML_USE_NEON`, `GGML_USE_CPU`, `LLAMA_CURL=OFF`, etc. |
| **Actualizaciones controladas** | `git submodule update --remote` cuando queramos, revisando diff |
| **Tamaño aceptable** | ~200 MB en repo (solo sources, no build artifacts) |

### Por qué commit `b4270`
- Release estable agosto 2024 con: soporte Android NDK maduro, cuantización Q4_K_M nativa, backend CPU optimizado, fixes de memoria/JNI
- `master` tiene breaking changes semanales (API `llama_model_loader`, `llama_batch`, etc.)

### Por qué estos ABIs
- `arm64-v8a`: 95%+ dispositivos Android activos (requerido)
- `armeabi-v7a`: ~3% dispositivos legacy, bajo costo incluirlo
- `x86_64`: Solo emuladores, útil para CI/tests

---

## Alternativas descartadas

| Opción | Por qué NO |
|--------|------------|
| **B) Descarga en build (script Gradle)** | Complejidad innecesaria, red requerida en CI, builds no reproducibles sin cache |
| **C) Prefab / AAR precompilado** | Versiones fijas (suele ir por detrás), menos control sobre flags NEON/SIMD, dependencia externa |
| **D) Copiar fuentes manuales** | Mantenimiento manual propenso a errores, sin versionado, difícil actualizar |

---

## Testabilidad (para QA shift-left)

| Componente | Inyectable | Mockeable | Contrato |
|------------|------------|-----------|----------|
| `LlamaCppBridgeInterface` | ✅ Hilt | ✅ MockK | Ya existe, tests pasan |
| `LlamaCppEngine` | ✅ Hilt | ✅ MockK | Ya existe |
| `LlamaModelManager` | ✅ Hilt | ✅ MockK | Ya existe |
| **Nativo (.so)** | ❌ No inyectable | ❌ No mockeable | **Nuevo**: tests de integración reales en `connectedAndroidTest` |

**Estrategia QA**:
1. **Unit tests** (JVM): Usan `LlamaCppBridgeInterface` mockeado — **ya funcionan, no cambian**
2. **Instrumented tests** (device): Cargan `.so` real, testean inferencia end-to-end — **NUEVO: `LlamaCppEngineIntegrationTest.kt`**
3. **Contract test**: Verificar que `LlamaCppBridge` implementa `LlamaCppBridgeInterface` — ya cubierto por compilación

---

## Contratos clave

### CMakeLists.txt (core/ai/local/src/main/cpp/CMakeLists.txt)
**API pública del bridge** (firmas JNI que Kotlin espera):

```cpp
// Inicialización
JNIEXPORT jlong JNICALL Java_com_screenassistant_core_ai_local_llama_bridge_LlamaCppBridge_nativeInit(
    JNIEnv*, jobject, jobject /* LlamaInitParams */);

// Destrucción
JNIEXPORT void JNICALL Java_com_screenassistant_core_ai_local_llama_bridge_LlamaCppBridge_nativeDestroy(
    JNIEnv*, jobject, jlong /* contextHandle */);

// Carga modelo
JNIEXPORT jlong JNICALL Java_com_screenassistant_core_ai_local_llama_bridge_LlamaCppBridge_nativeLoadModel(
    JNIEnv*, jobject, jlong /* contextHandle */, jstring /* modelPath */, jint /* nThreads */);

// Libera modelo
JNIEXPORT void JNICALL Java_com_screenassistant_core_ai_local_llama_bridge_LlamaCppBridge_nativeFreeModel(
    JNIEnv*, jobject, jlong /* contextHandle */, jlong /* modelHandle */);

// Generación síncrona
JNIEXPORT jstring JNICALL Java_com_screenassistant_core_ai_local_llama_bridge_LlamaCppBridge_nativeGenerate(
    JNIEnv*, jobject, jlong /* contextHandle */, jlong /* modelHandle */,
    jstring /* prompt */, jint /* maxTokens */, jfloat /* temperature */,
    jfloat /* topP */, jint /* topK */);

// Streaming token-a-token
JNIEXPORT void JNICALL Java_com_screenassistant_core_ai_local_llama_bridge_LlamaCppBridge_nativeStreamGenerate(
    JNIEnv*, jobject, jlong /* contextHandle */, jlong /* modelHandle */,
    jstring /* prompt */, jint /* maxTokens */, jfloat /* temperature */,
    jfloat /* topP */, jint /* topK */,
    jobject /* onToken: (String)->Unit */, jobject /* onDone: ()->Unit */, jobject /* onError: (String)->Unit */);

// Health check
JNIEXPORT jboolean JNICALL Java_com_screenassistant_core_ai_local_llama_bridge_LlamaCppBridge_nativeIsReady(
    JNIEnv*, jobject, jlong /* contextHandle */);

// Token count
JNIEXPORT jint JNICALL Java_com_screenassistant_core_ai_local_llama_bridge_LlamaCppBridge_nativeTokenCount(
    JNIEnv*, jobject, jlong /* contextHandle */, jstring /* text */);
```

### LlamaInitParams (Kotlin → C++)
```kotlin
data class LlamaInitParams(
    val nCtx: Int = 2048,
    val nBatch: Int = 512,
    val nThreads: Int = 4,
    val useMmap: Boolean = true,
    val useMlock: Boolean = false,
    val verbose: Boolean = false,
)
```
Se serializa a C++ via JNI (ver implementación bridge).

---

## ⚠️ Ambigüedades detectadas

1. **`n_ctx` óptimo para TinyLlama 1.1B en móvil**: Configurado a 2048 en `LlamaCppConfig`, pero para dispositivos <4GB RAM podría necesitar 512/1024. **Interpretación por defecto**: mantener 2048 y dejar que `LlamaCppEngine` haga fallback a 1024 si `nativeLoadModel` retorna 0 (OOM).

2. **Threading en streaming**: `nativeStreamGenerate` usa callbacks Kotlin desde hilo nativo. **Interpretación**: el bridge C++ corre en hilo IO (Dispatchers.IO), callbacks se ejecutan en ese mismo hilo — el `callbackFlow` en Kotlin ya hace `flowOn(Dispatchers.IO)`. **Riesgo bajo**.

3. **Licencia NOTICE en APK**: Requiere empaquetar `llama.cpp/LICENSE` y `TinyLlama/LICENSE` en `assets/licenses/`. **Pendiente**: confirmar con Legal si aplica.

---

## 🔴 Bloqueos

**Ninguno** — toda la información necesaria está disponible. El diseño es accionable inmediatamente.

---

## Archivos a crear/modificar (resumen ejecutivo)

| Archivo | Acción | Descripción |
|---------|--------|-------------|
| `.gitmodules` | **CREAR** | Registra submódulo llama.cpp@b4270 |
| `llama.cpp/` | **CLONAR** | `git submodule add -b master --depth 1 https://github.com/ggml-org/llama.cpp.git llama.cpp && cd llama.cpp && git checkout b4270` |
| `src/main/cpp/CMakeLists.txt` | **REEMPLAZAR** | Integra llama.cpp como subdirectorio, compila static lib, linkea bridge |
| `src/main/cpp/llama_jni_bridge.cpp` | **REEMPLAZAR** | Implementación real usando API llama.cpp |
| `build.gradle.kts` | **MODIFICAR** | Añade `armeabi-v7a`, flags CMake por ABI, NDK config |
| `LlamaCppEngine.kt` | **AJUSTAR** | Manejo de fallback `n_ctx` si OOM, logging mejorado |
| `LlamaCppBridge.kt` | **NINGUNA** | Interfaz JNI no cambia (compatibilidad binaria) |

---

## Próximos pasos (para Desarrollador)

1. `git submodule add https://github.com/ggml-org/llama.cpp.git core/ai/local/llama.cpp`
2. `cd core/ai/local/llama.cpp && git checkout b4270`
3. Reemplazar `src/main/cpp/CMakeLists.txt` con versión nueva
4. Reemplazar `src/main/cpp/llama_jni_bridge.cpp` con implementación real
5. Actualizar `build.gradle.kts` con abiFilters + CMake args
6. `./gradlew :core:ai:local:assembleDebug` → verificar `.so` generados
7. Escribir `LlamaCppEngineIntegrationTest.kt` (connectedAndroidTest)
8. Probar en dispositivo real arm64-v8a