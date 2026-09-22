# Diseno Arquitectonico - Sprint 2: LLM Local con llama.cpp

## 1. Estructura de Paquetes Completa

```
core/ai/local/
+-- build.gradle.kts                                    # MODIFICADO
+-- src/
    +-- main/
    |   +-- AndroidManifest.xml
    |   +-- jni/                                         # NUEVO: Codigo nativo
    |   |   +-- CMakeLists.txt
    |   |   +-- llama_jni.c
    |   |   +-- llama_jni.h
    |   +-- jniLibs/                                     # NUEVO: .so pre-compilado
    |   |   +-- arm64-v8a/
    |   |   |   +-- libllama_jni.so
    |   |   +-- armeabi-v7a/
    |   |       +-- libllama_jni.so
    |   +-- kotlin/com/screenassistant/core/ai/local/
    |       +-- LlamaCppBridge.kt                        # NUEVO
    |       +-- LlamaCppEngine.kt                        # REEMPLAZA MlcLlmEngine
    |       +-- LlamaCppConfig.kt                        # NUEVO
    |       +-- LlamaInitParams.kt                       # NUEVO
    |       +-- LlamaModelManager.kt                     # REEMPLAZA ModelDownloadManager
    |       +-- ModelDownloader.kt                       # NUEVO
    |       +-- PromptTemplate.kt                        # NUEVO
    |       +-- di/
    |           +-- LlamaModule.kt                       # REEMPLAZA LocalAiModule
    +-- test/
        +-- kotlin/com/screenassistant/core/ai/local/
            +-- LlamaCppEngineTest.kt                    # NUEVO
            +-- LlamaModelManagerTest.kt                 # MODIFICADO
            +-- ModelDownloaderTest.kt                   # NUEVO
            +-- PromptTemplateTest.kt                    # NUEVO
            +-- LlamaCppConfigTest.kt                    # NUEVO

core/domain/                                            # SIN CAMBIOS
  repository/ai/LocalInferenceEngine.kt                  # Ya existe
  repository/ai/ModelManager.kt                         # Ya existe
  repository/ai/AiOrchestrator.kt                       # Ya existe
  repository/ai/AiRepository.kt                         # Ya existe
```

## 2. Archivos a Eliminar (Reemplazados)

| Archivo | Reemplazado por | Razon |
|---|---|---|
| `MlcLlmEngine.kt` | `LlamaCppEngine.kt` | MLC sin release estable |
| `StubLocalInferenceEngine.kt` | ELIMINAR | Ya no necesario |
| `ModelDownloadManager.kt` | `LlamaModelManager.kt` | Reemplazo completo |
| `di/LocalAiModule.kt` | `di/LlamaModule.kt` | Nuevo binding |

## 3. Diagrama de Dependencias

```
core:domain (JVM puro)
  ^
  |  implements interfaces
  |
core:ai:local (Android Library)
  |
  +-- LlamaCppBridge ---[JNI]---> libllama_jni.so ---> llama.cpp
  |
  +-- LlamaCppEngine (implements LocalInferenceEngine)
  |     depends on: LlamaCppBridge, LlamaCppConfig
  |
  +-- LlamaModelManager (implements ModelManager)
  |     depends on: LlamaCppEngine, ModelDownloader
  |
  +-- ModelDownloader (sin dependencias externas)
  |     uses: HttpURLConnection (stdlib)
  |
  +-- PromptTemplate (object, sin dependencias)
  |
  +-- LlamaCppConfig (singleton, inyectable)
  |
  +-- di/LlamaModule (Hilt)
        binds: LlamaCppBridge -> LlamaCppEngine -> LocalInferenceEngine
```

## 4. Estrategia de Fallback

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

Streaming: si el stream local falla mid-way (ya vio tokens parciales),
no hay fallback transparente. Se emite error al final del flow.

## 5. Nota JNI: Carga Nativa en Android

El `.so` se carga via `System.loadLibrary("llama_jni")` en companion object
de `LlamaCppBridge`. Se ejecuta una vez al acceder a la clase.

Compilar para:
- `arm64-v8a` (dispositivos modernos, prioridad)
- `armeabi-v7a` (dispositivos antiguos, opcional)

Build nativo via CMake + NDK:
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

## 6. Testabilidad (QA shift-left)

| Componente | Testeable sin JNI? | Estrategia |
|---|---|---|
| LlamaCppEngine | SI (mock bridge) | MockK mockea LlamaCppBridge |
| LlamaModelManager | SI (mock engine) | MockK mockea LlamaCppEngine |
| ModelDownloader | SI (mock URL) | Mock de HttpURLConnection |
| PromptTemplate | SI | Tests unitarios puros |
| LlamaCppConfig | SI | Tests de defaults |
