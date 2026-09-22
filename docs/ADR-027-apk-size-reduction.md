# ADR-027: Reducción APK 471MB → objetivo ≤150MB instalado (plan por fases)

## Estado
Propuesto — 2026-09-17 — Arquitecto

## Contexto
APK `Asistente.apk` (debug, instalación directa por APK, sin App Bundle) mide 471MB.
Análisis zip real aportado por Orquestador:

- Nativas `.so` ~316MB totales en 4 ABIs: `arm64-v8a` 93MB (20 libs), `x86_64` 100MB (20), `x86` 75MB (12), `armeabi-v7a` 48MB (12). CMake propio (`core:ai:local`) solo compila `arm64-v8a` + `x86_64`; `x86` / `armeabi-v7a` vienen de dependencias prebuilt (onnxruntime, `translate_jni`, Vosk, Porcupine, etc.).
- Assets ML ~120MB+: `piper/voice.onnx` 60MB, `all-minilm-l6-v2/model.onnx` 22MB, Vosk `model-es` (~40MB+: graph 20.8MB + am 15.4MB + resto).
- `classes.dex` 40MB con `isMinifyEnabled = false`, sin splits, sin shrink, sin Bundle.
- Uso real: emulador `x86_64` + físico `arm64` (Pixel). Debug y release.
- Verificado en disco: `app/build.gradle.kts` tiene `isMinifyEnabled = false` solo en `release`, sin bloque `splits` ni `ndk.abiFilters` a nivel `app`; `app/proguard-rules.pro` solo cubre Gemini/Coil/Hilt/Compose/Media3/service; `core/ai/local/build.gradle.kts` ya fija `abiFilters arm64-v8a + x86_64` y NDK 27 solo para su `.so` propio (no filtra las deps prebuilt a nivel app); `feature/overlay/build.gradle.kts` confirma las fuentes de peso (`vosk-android:0.3.47`, `onnxruntime-android:1.19.0`, `porcupine-android:3.0.1`) y `PiperTtsManager.kt` / `VoskSpeechToTextManager.kt` confirman carga por ruta desde assets (`piper/voice.onnx`, `model-es`).

Restricciones: instalación directa por APK (no Play, no AAB hoy). Sin backend propio. NO ejecutar Gradle ni escribir código en esta fase (solo diseño).

## Decisión
Plan en 2 fases. Fase 1 build-only inmediata. Fase 2 con código solo viable completa con hosting externo gratuito o backend futuro; sin eso el objetivo ≤150MB NO se alcanza manteniendo modelos empaquetados. Veredicto honesto abajo.

### Fase 1 — build-only, sin cambios de código (hacer ya)
1. Splits por ABI en `app`: un APK por ABI (`arm64-v8a` para físico, `x86_64` para emulador). Es la medida de mayor impacto y es reversible.
2. Filtro de ABIs a nivel `app` (`arm64-v8a` + `x86_64`): poda `x86` + `armeabi-v7a` heredados de deps prebuilt que hoy se empaquetan en el APK universal sin aportar nada (dispositivos objetivo no los usan; `armeabi-v7a` además está roto en llama.cpp upstream por intrinsics FP16 — ver PROGRESO de `core:ai:local`).
3. R8 (`isMinifyEnabled = true`) + `shrinkResources = true` en `release` (y evaluar `debug` con bandera separada solo para medición, no por defecto para no ralentizar iteración).
4. Completar reglas keep en `app/proguard-rules.pro` antes de activar R8 (lista abajo). Sin esto R8 romperá runtime.

Estimación de ahorro Fase 1 (sobre base 471MB universal debug):

| Medida | Ahorro estimado | Base de cálculo |
|---|---|---|
| Split por ABI (aislar 1 ABI por APK) | −195 a −225MB por APK | Universal 316MB .so → 93MB (arm64) o 100MB (x86_64). APK arm64 ≈ 93+120+40+resto ≈ 260–270MB; x86_64 ≈ 270–280MB |
| `abiFilters` arm64+x86_64 a nivel app (podar x86 75MB + armeabi 48MB del universal) | −115 a −125MB en universal (redundante con splits, pero necesario para que los splits salgan limpios) | Suma directa de carpetas lib/x86 + lib/armeabi-v7a |
| R8 sobre `classes.dex` 40MB | −12 a −16MB | Reducción típica 30–40% en app Compose+Hilt+Kotlin pesada |
| `shrinkResources` + `resConfigs` (solo idiomas/densidades usadas) | −5 a −10MB | Res duplicados, iconos, strings de libs (MLKit, Media3, Compose) |
| **Total Fase 1 por APK ABI** | **APK arm64 ≈ 245–260MB; x86_64 ≈ 255–270MB** | Sigue >150MB. Fase 1 sola NO cumple objetivo. Reduce ~45% vs universal. |

Conclusión Fase 1: imprescindible, bajo riesgo si se hace en orden, pero insuficiente por sí sola porque el 45% del peso por APK son assets ML (~120MB) que ni splits ni R8 tocan.

### Fase 2 — arquitectura, con cambios de código (modelos bajo demanda)
Opciones evaluadas:

A. Play Asset Delivery (install-time / fast-follow / on-demand). Requiere migrar a App Bundle + publicación en Play. Hoy instalación directa por APK → NO viable hoy. Queda como deuda/visión si se publica en Play. Ahorro potencial: −120MB del APK base (modelos fuera), instalado final según pack descargado.

B. Descarga propia al primer uso (conserva APK directo). Requiere: hosting HTTPS de los 3 blobs + `ModelAssetRepository` + `DownloadManager/WorkManager` + verificación checksum + UX de primer uso + fallback sin red. Hoy NO hay backend → NO viable sin más, SALVO usar hosting estático gratuito existente como sustituto (GitHub Releases del propio repo o Hugging Face dataset/modelo público). Esa vía es técnicamente viable con coste cero y reutiliza el patrón ya diseñado en ADR-025 (`ModelDownloader` + `ModelFileStore` del módulo `core:ai:local`), pero exige cambios de código + decisión de producto (¿la app funciona degradada sin modelos? ¿qué pasa sin internet en primer arranque?) + política de versiones y checksums. Sin decisión de hosting, Fase 2 queda en deuda.

C. Mantener empaquetados. Cero cambios, cero riesgo, pero condena a ~250MB por ABI para siempre. Se descarta como estado final si el objetivo ≤150MB es firme.

Veredicto honesto con lo que hay HOY (sin backend, sin Play):
- Lo que SÍ se puede hacer ya sin backend ni código: Fase 1 completa + variante de build de desarrollo sin assets (flavor `lite` que excluya `piper/voice.onnx` + `model-es` + `minilm` solo para iterar en emulador/físico cuando no se prueba voz). Eso da un APK dev de ~130–145MB útil para iteración, pero NO es un producto shippable con voz offline.
- Lo que NO se puede hacer sin backend/Play: un APK producto ≤150MB CON voz offline completa. Matemática: APK por ABI post-Fase 1 ~250MB − 120MB modelos = ~130MB + overhead downloader (~2–3MB) = ~132–145MB → cumple; sin quitar modelos es imposible (dex+res+nativas de una sola ABI ya suman ~130MB antes de modelos).
- Deuda registrada: elegir hosting (recomendación: GitHub Releases del repo para `voice.onnx` + `model-es` + `minilm`, con SHA-256 en `BuildConfig`/json versionado) o aceptar publicar por Play con PAD/AAB. Hasta entonces, objetivo ≤150MB instalado queda condicionado a variante sin modelos.

## Capas (texto)
```
+-------------------------------------------------------------+
| PRESENTATION (app, feature:overlay)                         |
| OverlayViewModel, TTS/STT controllers, ModelDownloadUiState |
| (sealed: NotAsked / Downloading(pct) / Ready / Failed)      |
+-------------------------------------------------------------+
| DOMAIN (core:domain) — JVM puro, sin Android                |
| ModelAsset (descriptor), ModelAssetRepository (interfaz),   |
| GetModelStatusUseCase, EnsureModelUseCase, ObserveDownload  |
+-------------------------------------------------------------+
| DATA (core:data + core:ai:local ModelFileStore)             |
| ModelAssetRepositoryImpl, ModelDownloader (HTTP+checksum),  |
| ModelFileStore (filesDir/piper, filesDir/vosk...), Room     |
| flag de estado si se quiere persistir                       |
+-------------------------------------------------------------+
| NATIVE / ASSETS (fuera del APK en Fase 2)                   |
| voice.onnx, model-es/*, minilm/model.onnx en hosting;       |
| .so por ABI vía splits (Fase 1)                             |
+-------------------------------------------------------------+
Dependencias apuntan hacia adentro: presentation -> domain <- data.
Domain no conoce data, ni rutas de assets, ni WorkManager.
```

## Estructura de paquetes / módulos (propuesta, sin crear nada aún)
- Sin módulo nuevo. Reutilizar: `core:domain` (contratos + UseCases), `core:data` (impl repo + downloader + store), `feature:overlay` (UI primer uso + estados sellados), `core:ai:local` (patrón `ModelDownloader/ModelFileStore` ya diseñado en ADR-025 como referencia).
- Fase 1 no toca paquetes; solo `app/build.gradle.kts`, `app/proguard-rules.pro` (y verificación de consumer-rules de libs), `gradle.properties` si se quiere universal APK además de splits durante transición.

## Reglas keep necesarias (qué + dónde) — Fase 1 requisito previo a R8
Todo en `app/proguard-rules.pro` (centralizar; no dispersar por módulo salvo consumer-rules que ya traigan las libs). Verificar además que las consumer-rules de cada lib no contradigan:

1. Hilt/Dagger: mantener anotaciones e injects, componentes generados y EntryPoints. Lo actual cubre parcial; ampliar a rules oficiales Hilt (keep `dagger.hilt.internal.**`, EntryPoint, `*Hilt*`).
2. Room (`core:data` tiene `@Entity/@Dao/@Database` en `AppDatabase`, `ConversationTurnEntity`, `MemoryEntity`, etc.): keep entidades, DAOs y `RoomDatabase` + `keepattributes Signature, *Annotation*`. Sin esto R8 renombra columnas/constructores y rompe en runtime con `IllegalArgumentException`.
3. kotlinx-serialization (`core:iot:domain` usa `@Serializable` masivo, `SystemCommandJsonCodec` con constructor con defaults — ver comentario D5 en `app/build.gradle.kts`): keep `serializable` + `Companion`, `serializer()`, `keepattributes RuntimeVisibleAnnotations`, y no ofuscar `serialName`. Riesgo alto: rutas JSON persistidas.
4. Vosk (`org.vosk.**`, `com.alphacephei.**`): keep clases `Model/Recognizer/SpeechService/StorageService` + métodos nativos + `keepclasseswithmembernames` JNI. Vosk carga `libvosk_jni.so` y `StorageService.unpack(context,"model-es",...)` usa ruta literal de assets → esa cadena no debe ofuscarse y la carpeta assets no debe renombrarse por `shrinkResources` (assets no se shrinking, pero validar con `aaptOptions/noCompress` si se añade).
5. JNI propio llama (`core:ai:local` `LlamaCppBridge`, 10 `native*` en `llama_jni_bridge.cpp`): keep clase bridge + `native <methods>` + `keepclasseswithmembernames class * { native <methods>; }`. Cualquier renombre rompe `UnsatisfiedLinkError`.
6. ONNX Runtime (`ai.onnxruntime.**`): keep API OrtEnvironment/OrtSession + nativos. Doble versión en grafo (`1.19.0` en overlay + `1.21.0` en ai:memory) → alinear a una sola versión en Fase 1 para evitar duplicar nativas/clases (también ahorra MB y evita `pickFirsts` frágiles).
7. Porcupine (`ai.picovoice.**`) + MLKit Translate (`com.google.mlkit:translate`, `translate_jni` prebuilt): keep modelos/clases de API + nativos. Translate trae `x86/armeabi` prebuilt que es justo lo que poda `abiFilters`.
8. Compose Navigation / Hilt-navigation-compose rutas por string, DataStore serializers, ExoPlayer/Media3 renderers cargados por reflexión, Coil decoders: keeps ya parciales en `proguard-rules.pro` para Compose/Media3/Coil/service — mantener y no reducir; añadir `dontwarn` solo donde el warning sea falso positivo verificado, no global.
9. `BuildConfig` campos (`GEMINI_API_KEY`) y `R` — no ofuscar referencias usadas por Dagger/Manifest.

## Consecuencias
- Positivas: APK por ABI −45% inmediato (471→~250MB), iteración más rápida, base para llegar a ≤150MB cuando se externalicen modelos; R8 además endurece contra ingeniería inversa básica.
- Negativas/riesgos: R8 puede romper runtime por reflexión/JNI/rutas literales (Hilt, Room, serialization, Vosk `model-es`, Piper `piper/voice.onnx`, llama JNI). Mitigación: activar por pasos con rollback por paso (ver plan),auss `mapping.txt` guardado, matriz de smoke tests en arm64 físico + x86_64 emulador, `minify` primero sin `shrinkResources`, luego con.
- Si se decide hosting gratuito (GitHub Releases/HF) hay que asumir: versionado de modelos, checksums, UX offline-degradado, permisos `POST_NOTIFICATIONS`/foreground para descarga, y test de primera ejecución sin red.

## Plan ejecutable por fases (sin código, solo build + decisiones)

### Fase 1 — orden estricto, un paso = una medición
Archivos a tocar (solo estos): `app/build.gradle.kts`, `app/proguard-rules.pro`, opcional `gradle.properties` (universal APK transitorio). Verificar alineación de `core/ai/local/build.gradle.kts` (ya tiene filtros) y `feature/overlay/build.gradle.kts` (alinear onnxruntime a una versión). No tocar código Kotlin/C++.

- Paso 1.1 — `abiFilters arm64-v8a + x86_64` a nivel `app` + `splits.abi` (arm64, x86_64, universal=false tras validación). Medir: `app-arm64-debug.apk`, `app-x86_64-debug.apk`. Criterio: cada APK contiene solo su carpeta `lib/<abi>` (verificar con zip listing), tamaño 260–280MB, instala y arranca en Pixel y emulador.
- Paso 1.2 — Alinear `onnxruntime-android` a una sola versión en todo el grafo + `packaging.jniLibs.pickFirsts` para `libc++_shared.so` si hay duplicados. Medir delta MB.
- Paso 1.3 — R8 `isMinifyEnabled=true` en `release` (con keeps completos del § anterior), `shrinkResources=false` aún. Medir `app-arm64-release.apk` vs debug. Criterio: −10 a −16MB dex + smoke test completo (TTS Piper, STT Vosk, traducción offline si se usa, Room, navegación) en ambas ABIs.
- Paso 1.4 — `shrinkResources=true` + `resConfigs` (idiomas/densidades). Medir −5 a −10MB extra. Criterio: sin recursos rotos (iconos, strings MLKit, overlay layouts) + test con `isMinifyEnabled` + `shrink` combinados.
- Rollback por paso: revert del flag del paso en `app/build.gradle.kts` (un commit por paso); conservar `mapping.txt` y APKs de cada paso para bisecar; si R8 rompe runtime y no se aísla en 1 jornada, volver a Paso 1.1 (splits+filters, que ya dan el grueso del ahorro sin riesgo de ofuscación) y reintentar R8 con keeps ampliados + `-printusage/-printmapping` analizados.

### Fase 2 — cuando haya decisión de hosting (diseño listo, código pendiente)
Archivos futuros (no tocar hoy): `core/domain` (interfaz repositorio + 2–3 UseCases + `ModelAsset` descriptor), `core/data` (impl + downloader HTTP + file store + checksum), `feature/overlay` (pantalla primer uso + `sealed UiState`), DI Hilt module, `AndroidManifest` (permiso notificaciones/foreground si se usa WorkManager), `docs/BACKLOG.md` (deuda). Orden futuro: 2.1 publicar blobs + SHA-256 → 2.2 repo+store+UseCases (testeable JVM con mocks) → 2.3 UI primer uso + fallback degradado → 2.4 quitar blobs de `assets/` y medir `app-arm64-release ≈ 130–145MB` → 2.5 AAB/PAD solo si se va a Play.
Criterio aceptación Fase 2: APK base por ABI ≤150MB instalado (medido con `adb install` + `dumpsys`/`du`), primer arranque sin red = degradado explícito (no crash), con red = descarga reanudable con progreso y checksum OK, modelos fuera del APK (zip listing sin `piper/voice.onnx`, `model-es/`, `minilm/`).
Rollback Fase 2: re-empaquetar modelos (revert del commit que los excluye) → vuelve a ~250MB pero funcional offline total.

## Alternativas descartadas
- App Bundle inmediato sin Play: inútil para instalación directa por APK; no reduce nada hoy.
- Podar a una sola ABI (`arm64` únicamente): rompería emulador `x86_64` usado a diario; splits dan lo mismo sin romper nada.
- Comprimir más los `.onnx`/modelos en assets: ahorro marginal (<5%) y riesgo de corromper inferencia; la vía real es sacarlos, no recomprimirlos.
- `armeabi-v7a/x86` como targets soportados: sin usuarios reales + llama.cpp roto en armv7 + peso muerto 123MB; podar es correcto.

## Testabilidad (para QA shift-left)
- Fase 1: cada paso es medible con `zipinfo`/tamaño APK + matriz smoke (arm64 físico, x86_64 emulador) × (debug, release+R8). R8 exige `mapping.txt` archivado por build para desofuscar stacktraces de QA. Dependencias inyectables no cambian; contratos mockeables intactos.
- Fase 2: `ModelAssetRepository` mockeable, `EnsureModelUseCase` testeable JVM sin red (mock downloader + fake store), UI con estados sellados testeable con Turbine/screenshot sin modelo real. QA debe exigir test primer-arranque-sin-red y descarga-interrumpida-reanudada.

## Referencias verificadas en disco
- `app/build.gradle.kts` (minify false, sin splits/filters a nivel app)
- `app/proguard-rules.pro` (keeps actuales incompletos para R8 total)
- `core/ai/local/build.gradle.kts` (filtros arm64+x86_64 ya presentes a nivel módulo)
- `feature/overlay/build.gradle.kts` (vosk/onnxruntime/porcupine)
- `docs/ADR-025-llamacpp-integration.md` (patrón downloader/store a reutilizar en Fase 2)
