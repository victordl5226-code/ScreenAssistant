# ADR-003: Integración real de llama.cpp en core:ai:local

## Estado
Aceptado

## Contexto
El módulo `core:ai:local` tiene toda la infraestructura Kotlin/JNI/Hilt lista, pero **falta la integración real de llama.cpp** (fuentes, CMake, flags ARM, ABIs). El modelo TinyLlama Q4_K_M (637 MB) ya está descargado y verificado.

## Decisión
**Estrategia: Submódulo git (Opción A)** — Clonar `ggml-org/llama.cpp` en `core/ai/local/llama.cpp` fijando el commit `b4270` (release estable con soporte Android mejorado).

## Justificación
| Criterio | Opción A (Submódulo) | Opción B (Descarga build) | Opción C (Prefab) | Opción D (Copiar fuentes) |
|----------|---------------------|--------------------------|-------------------|--------------------------|
| Versionado exacto | ✅ | ❌ | ✅ (pero limitado) | ❌ manual |
| Build offline | ✅ | ❌ | ✅ | ✅ |
| Control flags CMake | ✅ total | ✅ total | ❌ limitado | ✅ total |
| Tamaño repo | ~200 MB | ✅ ligero | ✅ ligero | ~50 MB (parcial) |
| Actualizaciones | `git submodule update` | Script custom | Esperar release AAR | Manual |

**Ganador: Opción A** — Estándar en proyectos Android NDK, permite fijar commit exacto, control total de flags, build offline.

## Commit llama.cpp
- **Commit**: `b4270` (tag `b4270` = release `b4270` del 2024-08-XX)
- **Por qué**: Incluye fixes Android (JNI, threads, memoria), cuantización Q4_K_M, backends CPU/Metal/CUDA configurables. Evita `master` (breaking changes frecuentes).

## ABIs objetivo
| ABI | Prioridad | Flags CMake |
|-----|-----------|-------------|
| `arm64-v8a` | ALTA (dispositivos reales) | `-march=armv8-a+simd -mfpu=neon -O3 -DGGML_USE_NEON=1` |
| `armeabi-v7a` | MEDIA (compatibilidad) | `-march=armv7-a -mfpu=neon -O3 -DGGML_USE_NEON=1` |
| `x86_64` | BAJA (emulador) | `-O3 -DGGML_USE_AVX=1 -DGGML_USE_AVX2=1` |

## Threading Model
- **llama.cpp no es thread-safe** para el mismo contexto
- **Patrón**: Un contexto por sesión, mutex en Kotlin (`LlamaCppEngine`) — ya implementado con `@Volatile handles`
- **Android**: `llama_set_num_threads(n)` con `n = CPU cores - 1` (mín 2, máx 8)

## Memoria
- Contexto ~200-400 MB RAM (modelo 1.1B Q4_K_M + KV cache 2048 ctx)
- **OOM risk** en dispositivos <4 GB RAM
- **Mitigación**: `n_ctx=512` (o 1024), `n_batch=256`, `n_ubatch=256`, `use_mmap=true`, `use_mlock=false`

## Licencia
- llama.cpp: **MIT** — compatible, sin obligación de open-sourcing app
- TinyLlama: **Apache-2.0** — compatible
- **Acción**: Incluir `NOTICE` / `LICENSE` en `core/ai/local/llama.cpp/`

## Consecuencias
- Build time aumenta ~2-3 min (compilación C++ llama.cpp)
- APK size +~15-20 MB (libllama_jni.so + libggml.so embebidos)
- Requiere NDK r26+ en CI/local (ya instalado)

## Implementación
Ver diseño completo en entrega del Arquitecto adjunta.