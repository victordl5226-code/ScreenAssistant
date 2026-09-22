# ADR-025: Integracion de llama.cpp para LLM Local

## Estado: Propuesto (Sprint 2)

---

## Contexto

ScreenAssistant necesita un motor de inferencia LLM local funcional. El modulo `core:ai:local` fue disenado originalmente para MLC LLM, pero esta libreria **no tiene release estable para Android** (verificado septiembre 2026). El `MlcLlmEngine` actual es un placeholder que no ejecuta inferencia real.

**llama.cpp** es el candidato mas fuerte:
- Comunidad activa (40k+ stars, 6k+ forks)
- Formato GGUF nativo (estandar de facto para modelos quantizados)
- JNI bindings para Android ya disponibles
- TinyLlama 1.1B en Q4 = ~700MB (factible para descarga)
- Streaming nativo callback a Kotlin Flow
- Sin dependencias de JVM/Android pesadas en el core

## Decision

### 1. Arquitectura de Capas

```
+-------------------------------------------------------------------+
|  PRESENTATION                                                     |
|  OverlayViewModel <- AiOrchestrator.streamMessage()               |
+-------------------------------------------------------------------+
|  DOMAIN (JVM puro)                                                |
|  interfaces: LocalInferenceEngine, ModelManager, AiOrchestrator   |
|  models: AiResponse, AiProvider, LlamaModelInfo, ModelState      |
+-------------------------------------------------------------------+
|  DATA / AI LOCAL (Android Library)                                |
|  LlamaCppEngine : LocalInferenceEngine                            |
|    -> LlamaCppBridge (JNI thin wrapper)                           |
|    -> LlamaCppConfig (parametros de inferencia)                   |
|  LlamaModelManager : ModelManager                                 |
|    -> ModelDownloader (descarga HTTP con progress)                |
|    -> ModelFileStore (gestion de archivos GGUF en disco)          |
|  PromptTemplate (formateo de prompts por modelo)                  |
|  di/LlamaModule.kt                                                |
+-------------------------------------------------------------------+
|  NATIVE (JNI / .so)                                               |
|  llama.cpp compiled for Android (ARM64-v8a, armeabi-v7a)          |
|  llama-jni.c: bridge JNI -> llama.cpp API                         |
+-------------------------------------------------------------------+
```

### 2. Modulo JNI: Separacion de Responsabilidades

**Regla de oro**: El codigo nativo (.c/.cpp) es una cascaja delgada. Toda logica de negocio (gestion de modelo, retry, fallback, threading) vive en Kotlin.

```
core/ai/local/src/main/
+-- jni/                           # Codigo fuente nativo
|   +-- llama_jni.c               # Bridge JNI (extern C)
|   +-- llama_jni.h               # Headers
|   +-- CMakeLists.txt            # Build nativo (NDK)
+-- cpp/                           # llama.cpp source (vendored o submodule)
+-- kotlin/.../
|   +-- LlamaCppEngine.kt         # Implementa LocalInferenceEngine
|   +-- LlamaCppBridge.kt         # Wrapper Kotlin sobre JNI
|   +-- LlamaCppConfig.kt         # Config de inferencia
|   +-- LlamaModelManager.kt      # Implementa ModelManager
|   +-- ModelDownloader.kt        # Descarga HTTP con progress
|   +-- PromptTemplate.kt         # Formateo de prompts
|   +-- di/
|       +-- LlamaModule.kt        # Hilt DI
```

**Nota sobre compilacion nativa**: El `.so` pre-compilado se puede incluir en `jniLibs/` para no requerir NDK en el build de CI. El `CMakeLists.txt` es para desarrollo local.

### 3. Estrategia de Fallback

```
AiOrchestrator.resolveProvider(preferLocal=true)
  |
  +-- 1. Si LOCAL esta listo (modelo cargado) -> LOCAL
  |     |
  |     +-- Si LOCAL falla -> Intentar GEMINI si hay internet
  |     +-- Si GEMINI falla -> AiResponse.FallbackFailed
  |
  +-- 2. Si LOCAL no esta listo y hay internet -> GEMINI
  |
  +-- 3. Si ninguno disponible -> AiResponse.Error (recoverable=false)
```

El flujo de streaming es similar pero mas delicado: si el stream local falla mid-way, no se puede hacer fallback transparente (el usuario ya ve tokens parciales). En ese caso, se emite un error al final del flow.

### 4. Testabilidad (para QA shift-left)

| Componente | Testeable sin JNI? | Estrategia |
|---|---|---|
| LlamaCppEngine | SI (mock bridge) | MockK mockea LlamaCppBridge, verifica llamadas |
| LlamaModelManager | SI (mock engine) | MockK mockea LlamaCppEngine, testea logica |
| ModelDownloader | SI (mock URL) | WireMock o mock de HttpURLConnection |
| PromptTemplate | SI | Tests unitarios puros |
| LlamaCppConfig | SI | Tests de valores por defecto |

### 5. Nota JNI: Carga Nativa en Android

El `.so` se carga via `System.loadLibrary("llama_jni")` en el companion object de `LlamaCppBridge`. Esto se ejecuta una vez cuando se accede a la clase por primera vez. El `.so` debe compilarse para:

- `arm64-v8a` (dispositivos modernos, prioridad)
- `armeabi-v7a` (dispositivos antiguos, opcional)

El build nativo usa CMake via NDK:
```gradle
android {
    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
        }
    }
}
```

Alternativa: incluir `.so` pre-compilado en `jniLibs/` para evitar NDK en CI.
