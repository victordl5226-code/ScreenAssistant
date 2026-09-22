# ADR-029: Descarga Bajo Demanda de Modelos ML (Fase 2 de ADR-027)

## Estado
**Revisado (v2)** — 2026-09-21 — Arquitecto
> Corregido tras observaciones QA: O1 (OkHttpClient inyectado), O2 (sin Flow
> como side-effect), O3 (métodos de consulta añadidos al contrato).

## Contexto

ScreenAssistant empaqueta 3 modelos ML pesados (~139MB) en el APK que deben
extraerse a descarga bajo demanda desde GitHub Releases para reducir el APK
de ~250MB a <150MB por ABI.

| Modelo | Tamaño | Assets | Motor | Manager actual |
|--------|--------|--------|-------|----------------|
| PiperTTS | 60 MB | `piper/voice.onnx` + `.json` | ONNX Runtime | `PiperTtsManager` (feature:overlay) |
| Vosk STT | 57 MB | `model-es/` (graph+am+resto) | Vosk | `VoskSpeechToTextManager` (feature:overlay) |
| MiniLM Embeddings | 22 MB | `all-minilm-l6-v2/model.onnx` + `tokenizer.json` | ONNX Runtime | `OnDeviceEmbeddingGenerator` (core:ai:memory) |

**Restricciones clave:**
- Hosting: GitHub Releases (`https://github.com/{owner}/{repo}/releases/download/{tag}/{file}`)
- Los modelos NO deben romper funcionalidad existente
- El usuario debe poder usar la app SIN modelos descargados (degradado graceful)
- Descarga transparente con progreso, verificación SHA-256
- Modelos descargados persisten entre sesiones
- No hay backend propio; hosting estático gratuito

**Patrón existente reutilizable:** `ModelDownloader` en `core:ai:local` ya implementa
descarga HTTP con reintentos y progreso (HttpURLConnection, stdlib Android).

## Decisión

Crear un módulo `core:model` con la infraestructura de descarga/verificación de modelos
y modificar los 3 managers existentes para consumirlo con fallback graceful.

### Arquitectura de Capas

```
+-------------------------------------------------------------------+
| PRESENTATION (feature:overlay)                                     |
| OverlayViewModel, DownloadBanner (Compose), ModelDownloadUiState  |
| (sealed: NotAsked / Downloading(pct) / Ready / Failed)            |
+-------------------------------------------------------------------+
| DOMAIN (core:domain)                                               |
| ModelAsset (descriptor enum), ModelAssetRepository (interfaz),    |
| EnsureModelUseCase, ObserveModelStatusUseCase                     |
+-------------------------------------------------------------------+
| DATA (core:model — NUEVO módulo)                                   |
| ModelAssetRepositoryImpl (Hilt @Singleton)                        |
|   → GitHubModelDownloader (OkHttpClient + SHA-256 + reintentos)   |
|   → ModelFileStore (filesDir, verificación, cleanup)              |
|   → ModelAssetRegistry (catálogo estático de 3 modelos)          |
+-------------------------------------------------------------------+
| CONSUMERS (modificados, NO nuevos)                                 |
| PiperTtsManager ← consume ModelAssetRepository (piper)            |
| VoskSpeechToTextManager ← consume ModelAssetRepository (vosk)     |
| OnDeviceEmbeddingGenerator ← consume ModelAssetRepository (minilm)|
+-------------------------------------------------------------------+
| HOSTING (GitHub Releases)                                          |
| https://github.com/{owner}/{repo}/releases/download/{tag}/{file}  |
+-------------------------------------------------------------------+

Dependencias apuntan hacia adentro: presentation -> domain <- data.
Domain no conoce rutas, GitHub, ni OkHttp.
```

### Por qué módulo nuevo `core:model` y no reutilizar `core:ai:local`

1. `core:ai:local` es específico de llama.cpp (JNI, CMake, GGUF). Mezclar descarga de
   modelos ONNX/Vosk ahí acoplaría módulos que deben ser independientes.
2. `core:model` es genérico: sirve para CUALQUIER modelo futuro (no solo AI).
3. `core:domain` no puede depender de `core:ai:local` (regla de Clean Architecture).
4. El `ModelDownloader` de `core:ai:local` se mantiene para GGUF/llama; el nuevo
   `GitHubModelDownloader` es más rico (checksum, progress callback, verificación de integridad).

### Flujo de Datos Completo

```
1. APP ARRANCA
   └─> ModelAssetRepositoryImpl.init()
       ├─ Lee estado de cada modelo desde filesDir (¿existe + archivos OK?)
       └─ Emite Flow<ModelAssetStatus> por cada modelo

2. USUARIO INTENTA USAR FEATURE (ej: hablar)
   └─> PiperTtsManager.speak(text)
       ├─ Verifica ModelAssetRepository.isReady("piper")
       │   ├─ YES → carga modelo y habla
       │   └─ NO → inicia descarga en background
       │       ├─ Emite progress → UI banner/notificación
       │       ├─ Descarga OK + SHA-256 OK → carga modelo y habla
       │       ├─ Descarga falla → FALLBACK a TextToSpeech del sistema
       │       └─ Sin red → FALLBACK inmediato + "Descarga cuando haya WiFi"
       └─ Flujo async (nunca bloquea UI)

3. DESCARGA (común para los 3 modelos)
   └─> GitHubModelDownloader.download(model, targetFile, onProgress)
       ├─ OkHttpClient inyectado desde NetworkModule (singleton, testable)
       ├─ onProgress callback → Repository mapea a ModelAssetStatus.Downloading
       ├─ Descarga a archivo temporal (.tmp)
       ├─ Al completar: calcula SHA-256 del archivo temporal
       │   ├─ SHA-256 OK → renombra a archivo final
       │   └─ SHA-256 FAIL → elimina temporal, lanza error
       └─ Repository emite ModelAssetStatus.Ready

4. VERIFICACIÓN DE INTEGRIDAD
   ├─ Cada modelo tiene un checksum SHA-256 hardcodeado en ModelAssetRegistry
   ├─ Se verifica SOLO al completar descarga (no en cada inicio)
   └─ Si checksum no coincide → descartar + reintentar o notificar error
```

### Componentes a Crear

#### 1. `core:model` — Nuevo módulo Gradle

```
core/model/
├── build.gradle.kts
└── src/main/kotlin/com/screenassistant/core/model/
    ├── di/
    │   └── ModelModule.kt              # Hilt DI
    ├── domain/
    │   ├── ModelAsset.kt               # Enum descriptor de modelos
    │   ├── ModelAssetStatus.kt         # Estado del modelo (sealed class)
    │   └── ModelAssetRepository.kt     # Interfaz (contract)
    └── data/
        ├── ModelAssetRepositoryImpl.kt  # Implementación
        ├── GitHubModelDownloader.kt     # Descarga OkHttp + checksum
        ├── ModelFileStore.kt            # Gestión archivos en disco
        └── ModelAssetRegistry.kt        # Catálogo estático de modelos
```

#### 2. Contratos/Interfaces

**`ModelAsset.kt`** (domain):
```kotlin
package com.screenassistant.core.model.domain

/**
 * Catálogo de modelos descargables.
 * Cada modelo tiene metadata estática (URL, checksum, tamaño).
 */
enum class ModelAsset(
    val id: String,
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val downloadUrl: String,
) {
    PIPER_TTS(
        id = "piper-tts",
        displayName = "Piper TTS (Voz)",
        fileName = "voice.onnx",
        sizeBytes = 60_000_000L,
        sha256 = "SHA256_DEL_VOICE_ONNX",  // Se calcula al publicar en GitHub Releases
        downloadUrl = "https://github.com/{owner}/{repo}/releases/download/v1.0/piper-voice.onnx"
    ),
    VOSK_STT(
        id = "vosk-stt",
        displayName = "Vosk STT (Reconocimiento de voz)",
        fileName = "model-es-vosk.tar.gz",  // empaquetado como .tar.gz
        sizeBytes = 57_000_000L,
        sha256 = "SHA256_DEL_MODEL_ES",
        downloadUrl = "https://github.com/{owner}/{repo}/releases/download/v1.0/vosk-model-es.tar.gz"
    ),
    MINILM_EMBEDDINGS(
        id = "minilm-embeddings",
        displayName = "MiniLM Embeddings (Semántica)",
        fileName = "all-minilm-l6-v2.tar.gz",
        sizeBytes = 22_000_000L,
        sha256 = "SHA256_DEL_MINILM",
        downloadUrl = "https://github.com/{owner}/{repo}/releases/download/v1.0/minilm-embeddings.tar.gz"
    );

    /** Directorio donde se descomprimen los archivos del modelo */
    val extractDir: String get() = id

    /** Lista de archivos esperados tras descompresión */
    val expectedFiles: List<String> get() = when (this) {
        PIPER_TTS -> listOf("voice.onnx", "voice.onnx.json")
        VOSK_STT -> listOf("graph", "am/final.mdl", "conf/mfcc.conf", "ivector/final.ie", "ivector/online_cmvn", "ivector/spk01.md5")
        MINILM_EMBEDDINGS -> listOf("model.onnx", "tokenizer.json")
    }
}
```

**`ModelAssetStatus.kt`** (domain):
```kotlin
package com.screenassistant.core.model.domain

/**
 * Estado del modelo — sealed class para UI reactiva.
 */
sealed class ModelAssetStatus {
    /** Modelo no descargado */
    data object NotDownloaded : ModelAssetStatus()

    /** Descargando con progreso 0.0..1.0 */
    data class Downloading(
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : ModelAssetStatus()

    /** Verificando integridad SHA-256 */
    data object Verifying : ModelAssetStatus()

    /** Modelo listo para usar */
    data class Ready(
        val localPath: String,
    ) : ModelAssetStatus()

    /** Error en descarga o verificación */
    data class Failed(
        val error: String,
        val isRetryable: Boolean = true,
    ) : ModelAssetStatus()
}
```

**`ModelAssetRepository.kt`** (domain) — **ACTUALIZADO (O3)**:
```kotlin
package com.screenassistant.core.model.domain

import kotlinx.coroutines.flow.Flow

/**
 * Contrato para acceso a modelos descargables.
 * Domain no conoce implementación (GitHub, filesystem, checksum).
 */
interface ModelAssetRepository {

    // ── Observación ──────────────────────────────────────────────

    /** Observa el estado de un modelo específico */
    fun observeStatus(model: ModelAsset): Flow<ModelAssetStatus>

    /** Observa el estado de todos los modelos */
    fun observeAllStatus(): Flow<Map<ModelAsset, ModelAssetStatus>>

    // ── Consulta (O3: métodos de consulta añadidos) ─────────────

    /** Verifica si un modelo está listo (síncrono, para checks rápidos) */
    fun isReady(model: ModelAsset): Boolean

    /** Obtiene la ruta local de un modelo listo, o null */
    fun getLocalPath(model: ModelAsset): String?

    /** Retorna el catálogo completo de modelos disponibles */
    fun availableModels(): List<ModelAsset>

    /**
     * Retorna el tamaño en bytes de un modelo ya descargado.
     * Retorna 0 si no está descargado.
     */
    suspend fun getModelSizeBytes(model: ModelAsset): Long

    /**
     * Retorna el espacio necesario en bytes para descargar un modelo
     * (incluye el archivo comprimido + espacio estimado de extracción).
     * Retorna 0 si ya está descargado.
     */
    suspend fun getRequiredSpaceBytes(model: ModelAsset): Long

    // ── Mutación ────────────────────────────────────────────────

    /**
     * Descarga un modelo. Emite progreso a través del Flow de status
     * (observeStatus). NO retorna Flow — progreso es observable via
     * observeStatus(), evitando side-effects en el dominio.
     */
    suspend fun download(model: ModelAsset)

    /** Elimina un modelo descargado */
    suspend fun delete(model: ModelAsset)

    /** Elimina todos los modelos descargados */
    suspend fun deleteAll()
}
```

> **Nota O3**: Los 3 métodos nuevos (`availableModels`, `getModelSizeBytes`,
> `getRequiredSpaceBytes`) son necesarios para: (a) UI de gestión de modelos,
> (b) verificación de espacio en disco antes de descargar, y (c) listado
> para configuración del usuario.

#### 3. Implementaciones Data

**`ModelAssetRegistry.kt`** (data):
```kotlin
package com.screenassistant.core.model.data

import com.screenassistant.core.model.domain.ModelAsset

/**
 * Catálogo estático de modelos disponibles.
 * En futuras versiones podría cargarse desde un JSON remoto para
 * actualizar URLs/checksums sin rebuild.
 */
object ModelAssetRegistry {
    fun getAll(): Array<ModelAsset> = ModelAsset.entries.toTypedArray()
    fun getById(id: String): ModelAsset? = ModelAsset.entries.find { it.id == id }
}
```

**`GitHubModelDownloader.kt`** (data) — **ACTUALIZADO (O1 + O2)**:
```kotlin
package com.screenassistant.core.model.data

import android.util.Log
import com.screenassistant.core.model.domain.ModelAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Descargador de modelos desde GitHub Releases con verificación SHA-256.
 *
 * Usa OkHttpClient inyectado (O1: testable, reutiliza configuración de red
 * del proyecto). PROGRESO via callback (O2: sin StateFlow como side-effect).
 *
 * Patrón extendido de ModelDownloader de core:ai:local con:
 * - OkHttpClient inyectado en vez de HttpURLConnection
 * - Cálculo SHA-256 streaming (sin cargar archivo completo en memoria)
 * - Descarga a archivo temporal (.tmp) → rename atómico
 * - Progress callback (lambda) — NO StateFlow
 * - Reintentos con backoff exponencial
 *
 * @param client OkHttpClient inyectado desde NetworkModule
 * @param maxRetries Número máximo de reintentos
 * @param initialBackoffMs Backoff inicial en milisegundos
 */
@Singleton
class GitHubModelDownloader @Inject constructor(
    private val client: OkHttpClient,
    private val maxRetries: Int = 3,
    private val initialBackoffMs: Long = 2000L,
) {

    /**
     * Descarga un modelo desde GitHub Releases.
     *
     * @param model Descriptor del modelo
     * @param targetFile Archivo destino final
     * @param onProgress Callback de progreso (0.0 a 1.0) — el Repository
     *   lo mapea a ModelAssetStatus.Downloading, manteniendo el patrón
     *   reactivo sin side-effects en el dominio.
     * @return File descargado y verificado
     * @throws IntegrityException si SHA-256 no coincide
     * @throws Exception si la descarga falla tras reintentos
     */
    suspend fun download(
        model: ModelAsset,
        targetFile: File,
        onProgress: (Float) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")

        var lastException: Exception? = null
        for (attempt in 0..maxRetries) {
            try {
                if (attempt > 0) {
                    val backoff = initialBackoffMs * (1 shl (attempt - 1))
                    Log.i(TAG, "Reintento $attempt/$maxRetries tras ${backoff}ms")
                    Thread.sleep(backoff)
                }

                downloadInternal(model, tempFile, onProgress)
                verifyAndMove(tempFile, targetFile, model.sha256)
                onProgress(1f)
                return@withContext targetFile
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Error descargando ${model.id} (intento ${attempt + 1}): ${e.message}")
                tempFile.delete()
            }
        }

        throw lastException ?: Exception("Descarga fallida tras $maxRetries reintentos")
    }

    /**
     * Descarga interna con OkHttpClient (O1).
     * Usa Request/Response de OkHttp en vez de HttpURLConnection.
     * Progress se emite via callback (O2), NO via StateFlow.
     */
    private fun downloadInternal(
        model: ModelAsset,
        tempFile: File,
        onProgress: (Float) -> Unit,
    ) {
        val request = Request.Builder()
            .url(model.downloadUrl)
            .header("User-Agent", "ScreenAssistant/1.0")
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: ${response.message}")
        }

        val body = response.body
            ?: throw Exception("Response body vacío para ${model.id}")

        val contentLength = body.contentLength()
        val source = body.source()
        val outputStream = FileOutputStream(tempFile)

        val buffer = okio.Buffer()
        var totalRead = 0L

        try {
            while (true) {
                val read = source.read(buffer, 8192L)
                if (read == -1L) break

                outputStream.write(buffer.readByteArray())
                totalRead += read

                if (contentLength > 0) {
                    val progress = (totalRead.toFloat() / contentLength).coerceIn(0f, 1f)
                    onProgress(progress)
                }
            }

            outputStream.flush()
            Log.i(TAG, "Descarga completada: ${tempFile.absolutePath} ($totalRead bytes)")
        } finally {
            outputStream.close()
            source.close()
            response.close()
        }
    }

    private fun verifyAndMove(tempFile: File, targetFile: File, expectedSha256: String) {
        // Calcular SHA-256 del archivo descargado
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(tempFile).use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }

        if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
            tempFile.delete()
            throw IntegrityException(
                "SHA-256 mismatch: esperado=$expectedSha256, actual=$actualSha256"
            )
        }

        // Rename atómico
        if (targetFile.exists()) targetFile.delete()
        val renamed = tempFile.renameTo(targetFile)
        if (!renamed) {
            throw Exception("No se pudo renombrar ${tempFile.name} → ${targetFile.name}")
        }

        Log.i(TAG, "Integridad verificada: ${targetFile.name} SHA-256 OK")
    }

    class IntegrityException(message: String) : Exception(message)

    companion object {
        private const val TAG = "GitHubModelDownloader"
    }
}
```

> **Correcciones O1/O2 documentadas:**
> - **O1**: `OkHttpClient` inyectado por constructor. En producción llega desde
>   `NetworkModule` (singleton con timeouts configurados). En tests se puede
>   inyectar `OkHttpClient` mockeado o `MockWebServer`.
> - **O2**: Sin `StateFlow` interno. El `onProgress` callback es mapeado por
>   `ModelAssetRepositoryImpl` al `MutableStateFlow<ModelAssetStatus>` del
>   repositorio. El flujo reactivo se mantiene en la capa correcta.

**`ModelFileStore.kt`** (data):
```kotlin
package com.screenassistant.core.model.data

import android.content.Context
import android.util.Log
import com.screenassistant.core.model.domain.ModelAsset
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Almacenamiento local de modelos descargados.
 *
 * Estructura en filesDir:
 *   models/
 *   ├── piper-tts/
 *   │   ├── voice.onnx
 *   │   └── voice.onnx.json
 *   ├── vosk-stt/
 *   │   ├── graph
 *   │   ├── am/final.mdl
 *   │   └── ...
 *   └── minilm-embeddings/
 *       ├── model.onnx
 *       └── tokenizer.json
 *
 * Verificación de integridad:
 * - Al descarga: SHA-256 contra catálogo (ya hecho por GitHubModelDownloader)
 * - Al inicio: solo verifica existencia de archivos esperados (rápido)
 */
class ModelFileStore(private val context: Context) {

    private val modelsDir: File by lazy {
        File(context.filesDir, "models").also { it.mkdirs() }
    }

    /**
     * Obtiene el directorio de un modelo.
     */
    fun getModelDir(model: ModelAsset): File {
        return File(modelsDir, model.id).also { it.mkdirs() }
    }

    /**
     * Verifica si un modelo está completamente descargado.
     * Solo verifica existencia de archivos (no checksum — eso es costoso).
     */
    fun isModelComplete(model: ModelAsset): Boolean {
        val dir = getModelDir(model)
        return model.expectedFiles.all { file ->
            File(dir, file).exists()
        }
    }

    /**
     * Obtiene la ruta local del modelo (directorio raíz del modelo).
     * Retorna null si no está completo.
     */
    fun getModelPath(model: ModelAsset): String? {
        return if (isModelComplete(model)) {
            getModelDir(model).absolutePath
        } else {
            null
        }
    }

    /**
     * Obtiene la ruta de un archivo específico del modelo.
     */
    fun getFilePath(model: ModelAsset, fileName: String): File {
        return File(getModelDir(model), fileName)
    }

    /**
     * Elimina un modelo descargado.
     */
    fun deleteModel(model: ModelAsset): Boolean {
        val dir = getModelDir(model)
        return if (dir.exists()) {
            dir.deleteRecursively().also { success ->
                if (success) Log.d(TAG, "Modelo ${model.id} eliminado")
                else Log.w(TAG, "Error eliminando modelo ${model.id}")
            }
        } else true
    }

    /**
     * Elimina todos los modelos descargados.
     */
    fun deleteAll(): Boolean {
        return modelsDir.deleteRecursively().also { success ->
            if (success) Log.d(TAG, "Todos los modelos eliminados")
            else Log.w(TAG, "Error eliminando directorio de modelos")
        }
    }

    /**
     * Obtiene el espacio total ocupado por modelos descargados.
     */
    fun getModelsSizeBytes(): Long {
        return modelsDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    /**
     * Obtiene el tamaño en bytes de un modelo específico (solo archivos descargados).
     * Retorna 0 si el modelo no está completo.
     */
    fun getModelSizeBytes(model: ModelAsset): Long {
        if (!isModelComplete(model)) return 0L
        return getModelDir(model).walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    companion object {
        private const val TAG = "ModelFileStore"
    }
}
```

**`ModelAssetRepositoryImpl.kt`** (data) — **ACTUALIZADO (O2 + O3)**:
```kotlin
package com.screenassistant.core.model.data

import android.util.Log
import com.screenassistant.core.model.domain.ModelAsset
import com.screenassistant.core.model.domain.ModelAssetRepository
import com.screenassistant.core.model.domain.ModelAssetStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación del repositorio de modelos.
 *
 * Orquesta: ModelFileStore (verificación) + GitHubModelDownloader (descarga)
 * Emite estados reactivos por modelo para UI.
 *
 * O2: El progreso de descarga se mapea desde callback → MutableStateFlow,
 *     eliminando cualquier side-effect de Flow en la capa de dominio.
 * O3: Implementa availableModels(), getModelSizeBytes(), getRequiredSpaceBytes().
 */
@Singleton
class ModelAssetRepositoryImpl @Inject constructor(
    private val fileStore: ModelFileStore,
    private val downloader: GitHubModelDownloader,
) : ModelAssetRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Estado por modelo */
    private val modelStatus = mutableMapOf<ModelAsset, MutableStateFlow<ModelAssetStatus>>()

    init {
        // Inicializar estados desde disco
        ModelAsset.entries.forEach { model ->
            val initial = if (fileStore.isModelComplete(model)) {
                ModelAssetStatus.Ready(fileStore.getModelPath(model)!!)
            } else {
                ModelAssetStatus.NotDownloaded
            }
            modelStatus[model] = MutableStateFlow(initial)
        }
    }

    override fun observeStatus(model: ModelAsset): Flow<ModelAssetStatus> {
        return getOrCreateFlow(model).asStateFlow()
    }

    override fun observeAllStatus(): Flow<Map<ModelAsset, ModelAssetStatus>> {
        // Combinar todos los flows en uno solo
        if (modelStatus.isEmpty()) return flowOf(emptyMap())

        val flows = ModelAsset.entries.map { model ->
            getOrCreateFlow(model).asStateFlow().combine(flowOf(model)) { status, asset ->
                asset to status
            }
        }

        return combine(flows) { results ->
            results.toMap()
        }
    }

    override fun isReady(model: ModelAsset): Boolean {
        return fileStore.isModelComplete(model)
    }

    override fun getLocalPath(model: ModelAsset): String? {
        return fileStore.getModelPath(model)
    }

    // ── O3: Métodos de consulta ─────────────────────────────────

    override fun availableModels(): List<ModelAsset> {
        return ModelAsset.entries.toList()
    }

    override suspend fun getModelSizeBytes(model: ModelAsset): Long {
        return fileStore.getModelSizeBytes(model)
    }

    override suspend fun getRequiredSpaceBytes(model: ModelAsset): Long {
        if (fileStore.isModelComplete(model)) return 0L
        // Tamaño del archivo comprimido + ~20% de margen para extracción
        return (model.sizeBytes * 1.2).toLong()
    }

    // ── Mutación ────────────────────────────────────────────────

    override suspend fun download(model: ModelAsset) {
        val flow = getOrCreateFlow(model)

        // Si ya está descargado, no hacer nada
        if (fileStore.isModelComplete(model)) {
            flow.value = ModelAssetStatus.Ready(fileStore.getModelPath(model)!!)
            return
        }

        // Iniciar descarga
        flow.value = ModelAssetStatus.Downloading(0f, 0, model.sizeBytes)

        try {
            val targetFile = fileStore.getFilePath(model, model.fileName)

            // O2: Progress callback mapeado a ModelAssetStatus.Downloading
            // Sin StateFlow interno en el downloader — el progreso se emite
            // a través del repositorio (observeStatus), NO como side-effect.
            downloader.download(model, targetFile) { progress ->
                flow.value = ModelAssetStatus.Downloading(
                    progress = progress,
                    bytesDownloaded = (model.sizeBytes * progress).toLong(),
                    totalBytes = model.sizeBytes,
                )
            }

            // Si es un archivo comprimido, descomprimir
            if (model.fileName.endsWith(".tar.gz")) {
                flow.value = ModelAssetStatus.Verifying
                extractModel(model, targetFile)
            }

            // Verificar que todos los archivos esperados existen
            if (!fileStore.isModelComplete(model)) {
                throw Exception("Archivos incompletos tras extracción: ${model.id}")
            }

            flow.value = ModelAssetStatus.Ready(fileStore.getModelPath(model)!!)
            Log.i(TAG, "Modelo ${model.id} listo")
        } catch (e: Exception) {
            Log.e(TAG, "Error descargando ${model.id}", e)
            flow.value = ModelAssetStatus.Failed(
                error = e.message ?: "Error desconocido",
                isRetryable = e !is GitHubModelDownloader.IntegrityException
            )
        }
    }

    private fun extractModel(model: ModelAsset, archiveFile: File) {
        // TODO: Implementar extracción .tar.gz
        // Usar org.apache.commons.compress o implementación manual
        // Por ahora, si el modelo viene como .onnx directo, no necesita extracción
        Log.d(TAG, "Extracción de modelo ${model.id} desde ${archiveFile.name}")
    }

    override suspend fun delete(model: ModelAsset) {
        fileStore.deleteModel(model)
        getOrCreateFlow(model).value = ModelAssetStatus.NotDownloaded
    }

    override suspend fun deleteAll() {
        fileStore.deleteAll()
        ModelAsset.entries.forEach { model ->
            getOrCreateFlow(model).value = ModelAssetStatus.NotDownloaded
        }
    }

    private fun getOrCreateFlow(model: ModelAsset): MutableStateFlow<ModelAssetStatus> {
        return modelStatus.getOrPut(model) {
            MutableStateFlow(
                if (fileStore.isModelComplete(model)) {
                    ModelAssetStatus.Ready(fileStore.getModelPath(model)!!)
                } else {
                    ModelAssetStatus.NotDownloaded
                }
            )
        }
    }

    companion object {
        private const val TAG = "ModelAssetRepository"
    }
}
```

#### 4. Inyección de Dependencias

**`ModelModule.kt`** (di) — **ACTUALIZADO (O1)**:
```kotlin
package com.screenassistant.core.model.di

import android.content.Context
import com.screenassistant.core.model.data.GitHubModelDownloader
import com.screenassistant.core.model.data.ModelAssetRepositoryImpl
import com.screenassistant.core.model.data.ModelFileStore
import com.screenassistant.core.model.domain.ModelAssetRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ModelModule {

    @Provides
    @Singleton
    fun provideModelFileStore(
        @ApplicationContext context: Context,
    ): ModelFileStore {
        return ModelFileStore(context)
    }

    /**
     * O1: GitHubModelDownloader recibe OkHttpClient inyectado.
     * Reutiliza el singleton de NetworkModule — misma instancia,
     * mismos timeouts, interceptors, etc.
     *
     * En tests: inyectar OkHttpClient mockeado o MockWebServer.
     */
    @Provides
    @Singleton
    fun provideGitHubModelDownloader(
        client: OkHttpClient,
    ): GitHubModelDownloader {
        return GitHubModelDownloader(client = client)
    }

    @Provides
    @Singleton
    fun provideModelAssetRepository(
        fileStore: ModelFileStore,
        downloader: GitHubModelDownloader,
    ): ModelAssetRepository {
        return ModelAssetRepositoryImpl(fileStore, downloader)
    }
}
```

### Componentes a Modificar

#### 5. `PiperTtsManager.kt` (feature:overlay)

**Cambios:**
- Eliminar init block que copia desde assets
- Agregar dependencia a `ModelAssetRepository`
- Antes de cargar modelo, verificar `isReady("piper-tts")`
- Si no está listo, intentar descargar
- Fallback: `TextToSpeechManager` (ya existe como `systemTts`)

```kotlin
class PiperTtsManager(
    private val context: Context,
    private val modelRepository: ModelAssetRepository,  // NUEVO
    private val onStart: () -> Unit = {},
    private val onDone: () -> Unit = {}
) : TextToSpeech {

    private val systemTts = TextToSpeechManager(context, onStart, onDone)
    private val ortEnv = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var isPiperReady = false

    // SIN init block — carga lazy cuando se necesita

    override fun speak(text: String) {
        if (text.isBlank()) return

        if (isPiperReady) {
            // Usar Piper
            Log.d(TAG, "[PIPER] JARVIS habla: $text")
            // ... lógica Piper existente ...
        } else {
            // Fallback: sistema
            Log.d(TAG, "[SISTEMA-FALLBACK] JARVIS habla: $text")
            systemTts.speak(text)
        }
    }

    /**
     * Carga el modelo Piper de forma lazy.
     * Llamar antes del primer speak() o en background.
     */
    suspend fun ensureModelReady(): Boolean {
        if (isPiperReady) return true

        return try {
            // Verificar si ya está descargado
            if (!modelRepository.isReady(ModelAsset.PIPER_TTS)) {
                Log.d(TAG, "Piper no disponible, descargando...")
                modelRepository.download(ModelAsset.PIPER_TTS)
            }

            // Cargar modelo desde disco
            val modelPath = modelRepository.getLocalPath(ModelAsset.PIPER_TTS)
            if (modelPath != null) {
                val modelFile = File(modelPath, "voice.onnx")
                if (modelFile.exists()) {
                    ortSession = ortEnv.createSession(modelFile.absolutePath)
                    isPiperReady = true
                    Log.i(TAG, "Piper TTS listo (ONNX)")
                    true
                } else {
                    Log.w(TAG, "Modelo Piper descargado pero archivo no encontrado")
                    false
                }
            } else {
                Log.w(TAG, "Piper no disponible")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando Piper, fallback a sistema", e)
            isPiperReady = false
            false
        }
    }

    // ... resto igual ...
}
```

#### 6. `VoskSpeechToTextManager.kt` (feature:overlay)

**Cambios:**
- Eliminar init block que descomprime desde assets
- Agregar dependencia a `ModelAssetRepository`
- Antes de initModel, verificar `isReady("vosk-stt")`
- Si no está listo, intentar descargar
- Fallback: `SpeechToTextManager` (SpeechRecognizer del sistema)

```kotlin
class VoskSpeechToTextManager(
    private val context: Context,
    private val modelRepository: ModelAssetRepository,  // NUEVO
    private val onResultCallback: (String) -> Unit,
    private val onPartialResultCallback: (String) -> Unit = {},
    private val onErrorCallback: (String) -> Unit = {}
) : SpeechToText, RecognitionListener {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // SIN init block — carga lazy

    private suspend fun initModel() {
        Log.d(TAG, "Initializing Vosk model...")

        try {
            // Verificar si ya está descargado
            if (!modelRepository.isReady(ModelAsset.VOSK_STT)) {
                Log.d(TAG, "Vosk no disponible, descargando...")
                modelRepository.download(ModelAsset.VOSK_STT)
            }

            // Cargar modelo desde disco
            val modelPath = modelRepository.getLocalPath(ModelAsset.VOSK_STT)
            if (modelPath != null) {
                val modelDir = File(modelPath)
                model = Model(modelDir.absolutePath)
                setupRecognizer()
                Log.d(TAG, "Model loaded successfully from $modelPath")
            } else {
                onErrorCallback("Modelo Vosk no disponible")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Vosk model", e)
            onErrorCallback("Voz Offline: ${e.message}")
        }
    }

    override fun startListening() {
        if (model == null) {
            // Iniciar carga en background
            scope.launch { initModel() }
            onErrorCallback("Modelo cargando, intenta de nuevo en unos segundos...")
            return
        }
        // ... resto igual ...
    }
}
```

#### 7. `OnDeviceEmbeddingGenerator.kt` (core:ai:memory)

**Cambios:**
- Eliminar copia desde assets
- Agregar dependencia a `ModelAssetRepository`
- En `ensureInitialized()`, verificar disponibilidad
- Si no está listo, intentar descargar
- Fallback: deshabilitar embeddings (isReady = false, usar LIKE en memoria)

```kotlin
@Singleton
class OnDeviceEmbeddingGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepository: ModelAssetRepository,  // NUEVO
) : EmbeddingGenerator {

    // ...existing fields...

    private fun ensureInitialized() {
        if (ready) return

        try {
            Log.i(TAG, "Initializing ONNX embedding model...")

            // Verificar si ya está descargado
            if (!modelRepository.isReady(ModelAsset.MINILM_EMBEDDINGS)) {
                Log.d(TAG, "MiniLM no disponible, descargando...")
                // Descarga en bloqueo (ensureInitialized se llama lazy)
                runBlocking { modelRepository.download(ModelAsset.MINILM_EMBEDDINGS) }
            }

            val modelPath = modelRepository.getLocalPath(ModelAsset.MINILM_EMBEDDINGS)
            if (modelPath == null) {
                Log.w(TAG, "MiniLM embeddings no disponible")
                return
            }

            val modelFile = File(modelPath, MODEL_FILE)
            val tokenizerFile = File(modelPath, TOKENIZER_FILE)

            if (!modelFile.exists() || !tokenizerFile.exists()) {
                Log.e(TAG, "Model or tokenizer file not found")
                return
            }

            // ...resto de inicialización ONNX igual...
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ONNX model", e)
            releaseResources()
        }
    }
}
```

### Modificaciones Gradle

**`settings.gradle.kts`** — agregar módulo:
```kotlin
include(":core:model")
```

**`core/model/build.gradle.kts`**:
```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.screenassistant.core.model"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:domain"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // O1: OkHttp para descarga (ya en el proyecto via core:data)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Okio para streaming de progreso (dependencia transitiva de OkHttp)
    // implementation("com.squareup.okio:okio:3.9.0")

    implementation("com.google.dagger:hilt-android:2.53.1")
    ksp("com.google.dagger:hilt-android-compiler:2.53.1")

    // Para extracción .tar.gz (si se usa)
    // implementation("org.apache.commons:commons-compress:1.26.0")

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)

    // O1: Para tests de red
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
```

**`feature/overlay/build.gradle.kts`** — agregar dependencia:
```kotlin
dependencies {
    // ...existing...
    implementation(project(":core:model"))  // NUEVO
}
```

**`core/ai/memory/build.gradle.kts`** — agregar dependencia:
```kotlin
dependencies {
    // ...existing...
    implementation(project(":core:model"))  // NUEVO
}
```

### UI de Progreso

**`ModelDownloadBanner.kt`** (feature:overlay/ui):

```kotlin
package com.screenassistant.feature.overlay.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.screenassistant.core.model.domain.ModelAsset
import com.screenassistant.core.model.domain.ModelAssetStatus

/**
 * Banner de descarga de modelo.
 * Se muestra cuando un modelo está descargando.
 */
@Composable
fun ModelDownloadBanner(
    modelStatus: ModelAssetStatus?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = modelStatus is ModelAssetStatus.Downloading,
        modifier = modifier,
    ) {
        if (modelStatus is ModelAssetStatus.Downloading) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = "Descargando modelo de voz...",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { modelStatus.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(modelStatus.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
```

### Flujo de Integración Completo

```
1. App arranca → ModelModule inicializa ModelAssetRepositoryImpl
   └─> Lee disco: ¿qué modelos hay en filesDir/models/?
   └─> Emite estados iniciales (Ready/NotDownloaded)

2. OverlayViewModel crea PiperTtsManager y VoskSpeechToTextManager
   └─> Reciben ModelAssetRepository via DI

3. Usuario habla al asistente
   └─> PiperTtsManager.speak("Hola")
       ├─ isPiperReady? → NO
       ├─ Lanza ensureModelReady() en coroutine
       │   ├─ isReady("piper-tts")? → NO
       │   ├─ download("piper-tts")
       │   │   ├─ onProgress(0.3) → Flow emite Downloading(0.3) → UI banner aparece
       │   │   ├─ Descarga completada → Verificación SHA-256
       │   │   ├─ SHA-256 OK → Ready
       │   │   └─ Carga ONNX session
       │   └─ isPiperReady = true
       └─ speak(text) con Piper

4. Si descarga falla (sin red):
   └─> catch → isPiperReady = false
   └─> speak() usa fallback: TextToSpeechManager (sistema)

5. Si usuario quiere cancelar descarga:
   └─> ModelAssetRepository.delete(model)
   └─> Estado vuelve a NotDownloaded

6. UI de gestión (futuro): usuario consulta espacio
   └─> modelRepository.getRequiredSpaceBytes(model)
   └─> modelRepository.getModelSizeBytes(model)
   └─> Muestra en Settings: "Piper: 60MB (descargado)"
```

### Orden de Implementación

| Paso | Componente | Esfuerzo | Dependencias |
|------|-----------|----------|--------------|
| 1 | `core:model` módulo Gradle + build.gradle.kts | S | Ninguna |
| 2 | `ModelAsset.kt` + `ModelAssetStatus.kt` (domain) | S | Paso 1 |
| 3 | `ModelAssetRepository.kt` (interfaz) | S | Paso 2 |
| 4 | `ModelFileStore.kt` | M | Paso 1 |
| 5 | `GitHubModelDownloader.kt` (con OkHttpClient) | M | Paso 1 |
| 6 | `ModelAssetRepositoryImpl.kt` | M | Pasos 3-5 |
| 7 | `ModelModule.kt` (DI) | S | Paso 6 |
| 8 | `PiperTtsManager` modificación | M | Paso 7 |
| 9 | `VoskSpeechToTextManager` modificación | M | Paso 7 |
| 10 | `OnDeviceEmbeddingGenerator` modificación | M | Paso 7 |
| 11 | `ModelDownloadBanner.kt` (UI) | S | Paso 7 |
| 12 | Integración UI en OverlayViewModel | S | Pasos 8-11 |
| 13 | Publicar blobs en GitHub Releases + checksums | S | Externo |
| 14 | Tests unitarios (Repository, Downloader, FileStore) | M | Pasos 4-6 |
| 15 | Tests de integración (descarga real, fallback) | L | Todos |

**Leyenda:** S=Small (0.5-1 día), M=Medium (1-2 días), L=Large (2-3 días)

### Tests Unitarios Clave

```kotlin
// ModelFileStoreTest
@Test fun `isModelComplete returns true when all expected files exist`() { ... }
@Test fun `isModelComplete returns false when any file is missing`() { ... }
@Test fun `deleteModel removes directory recursively`() { ... }
@Test fun `getModelSizeBytes returns 0 when model not downloaded`() { ... }

// GitHubModelDownloaderTest (O1: OkHttpClient mockeado via MockWebServer)
@Test fun `download succeeds with valid SHA-256`() { ... }
@Test fun `download throws IntegrityException on SHA-256 mismatch`() { ... }
@Test fun `download retries on network error`() { ... }
@Test fun `download reports progress via onProgress callback`() { ... }

// ModelAssetRepositoryImplTest
@Test fun `isReady returns true when model is downloaded`() { ... }
@Test fun `download emits Downloading then Ready states`() { ... }
@Test fun `download emits Failed on error`() { ... }
@Test fun `availableModels returns all 3 models`() { ... }  // O3
@Test fun `getModelSizeBytes returns 0 when not downloaded`() { ... }  // O3
@Test fun `getRequiredSpaceBytes returns 0 when already downloaded`() { ... }  // O3
```

## Alternativas Descartadas

| Alternativa | Razón de descarte |
|------------|-------------------|
| WorkManager para descarga | Complejidad innecesaria; descargas <100MB no requieren foreground service ni constraints de red. Coroutines simples bastan. |
| Descarga por archivos individuales (no empaquetado) | Vosk tiene 6 archivos; empaquetar como .tar.gz simplifica a 1 descarga + 1 checksum. Piper/MiniLM ya son 1-2 archivos. |
| Almacenamiento en cacheDir | cacheDir puede ser purgado por el sistema. filesDir es persistente entre sesiones (requisito). |
| Descarga bajo demanda por feature | Agrega complejidad de tracking por modelo. Descarga al primer uso es más simple y cubre todos los casos. |
| Hosting en Hugging Face | GitHub Releases es más estable y el repo ya existe. HF puede cambiar políticas de hotlinking. |
| Backend propio | Restricción: "no hay backend propio". GitHub Releases es gratuito y suficiente. |
| Descarga en background al primer start | El usuario debería ver progreso. Descarga reactiva (al intentar usar) es más UX-friendly. |
| HttpURLConnection (sin OkHttpClient) | O1: OkHttpClient es mockeable, reutiliza configuración del proyecto (NetworkModule), soporta interceptors y es la práctica estándar con Hilt DI. |

## Resolución de Observaciones QA

### O1: GitHubModelDownloader debe inyectar OkHttpClient

**Estado:** RESUELTO

**Problema:** Si `GitHubModelDownloader` instancia `HttpURLConnection` internamente,
no es mockeable para tests de red.

**Solución implementada:**
- `GitHubModelDownloader` recibe `OkHttpClient` por constructor via `@Inject`
- `ModelModule.provideGitHubModelDownloader(client: OkHttpClient)` provee la
  instancia desde el singleton de `NetworkModule`
- `downloadInternal()` usa `client.newCall(request).execute()` con API OkHttp
- `build.gradle.kts` de `core:model` incluye dependencia a `okhttp` y
  `mockwebserver` (para tests)

**Testeabilidad:** MockWebServer simula GitHub Releases; OkHttpClient se
inyecta mockeado o real según el tipo de test.

### O2: download retorna Flow<Float> como side effect

**Estado:** RESUELTO

**Problema:** Un `Flow<Float>` que descarga archivos como side effect rompe
principios de Flow.

**Solución implementada:**
- `GitHubModelDownloader.download()` usa un **callback lambda** `onProgress: (Float) -> Unit`
  en vez de `StateFlow` interno
- `ModelAssetRepositoryImpl.download()` mapea el callback al
  `MutableStateFlow<ModelAssetStatus>` del repositorio
- El progreso se observa vía `observeStatus()` — flujo reactivo en la capa correcta
- **O2-Opción A adoptada**: download NO retorna Flow; progreso se observa via observeStatus

### O3: Contrato carece de métodos de consulta

**Estado:** RESUELTO

**Problema:** No hay forma de obtener lista de modelos, tamaño, o verificar espacio.

**Solución implementada:**
- `ModelAssetRepository` añade: `availableModels()`, `getModelSizeBytes()`,
  `getRequiredSpaceBytes()`
- `ModelAssetRepositoryImpl` implementa:
  - `availableModels()` → `ModelAsset.entries.toList()`
  - `getModelSizeBytes()` → delega a `ModelFileStore.getModelSizeBytes()`
  - `getRequiredSpaceBytes()` → retorna `sizeBytes * 1.2` (comprimido + margen)
    o 0 si ya está descargado
- `ModelFileStore` añade `getModelSizeBytes(model)` que recorre archivos del modelo

## Testabilidad (para QA shift-left)

| Componente | Testeable sin red? | Estrategia |
|-----------|-------------------|-----------|
| ModelAsset | SI | Tests de enum values, URLs, checksums |
| ModelFileStore | SI | Mockk: crear archivos temporales, verificar isModelComplete |
| GitHubModelDownloader | SI (O1) | **MockWebServer** simula GitHub; OkHttpClient mockeado via DI |
| ModelAssetRepositoryImpl | SI | Mockk mockea FileStore + Downloader (callback onProgress) |
| PiperTtsManager | SI | Mockk mockea ModelAssetRepository |
| VoskSpeechToTextManager | SI | Mockk mockea ModelAssetRepository |
| OnDeviceEmbeddingGenerator | SI | Mockk mockea ModelAssetRepository |
| UI Banner | SI (preview) | Compose Preview + Screenshot tests |

**QA debe exigir:**
1. Test primer-arranque-sin-red: app no crashea, features degradadas correctamente
2. Test descarga-interrumpida-reanudada: descargar 50%, matar app, reabrir → continuar
3. Test verificación-SHA-256: simular corrupción → modelo no se carga
4. Test fallback: sin modelo → Piper usa sistema, Vosk usa SpeechRecognizer, MiniLM deshabilitado
5. Test espacio: getRequiredSpaceBytes retorna valores correctos para cada modelo

## Criterios de Aceptación

1. **APK reducido:** APK por ABI <150MB tras eliminar assets de modelos
2. **Descarga funcional:** Los 3 modelos se descargan correctamente desde GitHub Releases
3. **Verificación SHA-256:** Modelo corrupto no se carga; se reintenta automáticamente
4. **Degradado graceful:** Sin modelos: Piper→sistema, Vosk→SpeechRecognizer, MiniLM→deshabilitado
5. **Persistencia:** Modelos descargados sobreviven reinicios de app
6. **UI transparente:** Banner de progreso visible durante descarga; error claro si falla
7. **No regresión:** Todas las features existentes siguen funcionando igual
8. **Test coverage:** >80% en ModelFileStore, GitHubModelDownloader, ModelAssetRepositoryImpl
9. **DI consistente:** OkHttpClient inyectado desde NetworkModule (no instanciado internamente)
10. **Sin side-effects:** Progress se observa via observeStatus(), no via Flow retornado por download()

## Referencias Verificadas en Disco
- `feature/overlay/src/main/kotlin/.../PiperTtsManager.kt` — carga actual desde assets
- `feature/overlay/src/main/kotlin/.../VoskSpeechToTextManager.kt` — carga actual desde assets
- `core/ai/memory/src/main/kotlin/.../OnDeviceEmbeddingGenerator.kt` — carga actual desde assets
- `core/ai/local/src/main/kotlin/.../ModelDownloader.kt` — patrón reutilizable
- `core/data/src/main/kotlin/.../di/NetworkModule.kt` — **OkHttpClient singleton existente (O1)**
- `app/src/main/java/.../di/AppModule.kt` — patrón DI Hilt
- `docs/ADR-027-apk-size-reduction.md` — Fase 2 que esta propuesta implementa
- `docs/ADR-025-llamacpp-integration.md` — patrón ModelDownloader/ModelFileStore
