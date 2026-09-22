package com.screenassistant.core.ai.memory.semantic

import android.content.Context
import android.util.Log
import com.screenassistant.core.model.domain.ModelAsset
import com.screenassistant.core.model.domain.ModelAssetRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generación de embeddings en-device usando ONNX Runtime + all-MiniLM-L6-v2.
 *
 * ## Modelo
 * - Nombre: all-MiniLM-L6-v2 (INT8 quantized para ARM64)
 * - Dimensiones: 384
 * - Tamaño: ~22MB
 * - Latencia: ~50-100ms por inferencia
 * - Licencia: Apache 2.0
 *
 * ## Descarga bajo demanda
 * El modelo se descarga desde GitHub Releases a través de [ModelAssetRepository].
 * La carga es **lazy**: se realiza solo en la primera llamada a [embed].
 * Si la descarga o carga falla, se retornan listas vacías de embeddings (fallback seguro).
 *
 * ## Arquitectura
 * 1. Descarga modelo MINILM_EMBEDDINGS vía [modelRepository]
 * 2. Extrae model.onnx + tokenizer.json del .tar.gz
 * 3. Carga modelo ONNX + vocabulario del tokenizer
 * 4. Tokeniza input → input_ids + attention_mask
 * 5. Inferencia ONNX → sentence_embedding (384 dims)
 * 6. Normaliza vector para cosine similarity
 *
 * Los tipos de ONNX Runtime se manejan como [Any?] internamente para
 * evitar que la librería nativa se cargue en tests JVM unitarios.
 * El cast se realiza solo en la ruta de inferencia.
 *
 * @param context Contexto de aplicación.
 * @param modelRepository Repositorio de modelos descargables (inyectado).
 * @param dispatcher Dispatcher para operaciones de IO (default: Dispatchers.IO).
 */
@Singleton
open class OnDeviceEmbeddingGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepository: ModelAssetRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : EmbeddingGenerator {

    companion object {
        private const val TAG = "OnDeviceEmbeddingGenerator"
        private const val MODEL_DIR = "all-minilm-l6-v2"
        private const val MODEL_FILE = "model.onnx"
        private const val TOKENIZER_FILE = "tokenizer.json"
        private const val EMBEDDING_DIM = 384
        private const val MAX_SEQ_LENGTH = 256
        private const val CLS_TOKEN_ID = 101
        private const val SEP_TOKEN_ID = 102
        private const val PAD_TOKEN_ID = 0

        /** Tamaño de bloque del formato TAR (512 bytes). */
        private const val TAR_BLOCK_SIZE = 512
    }

    @Volatile
    private var ready = false

    /** Entorno ONNX (Any? para evitar carga de la librería nativa en tests). */
    private var ortEnv: Any? = null

    /** Sesión ONNX cargada (Any? para evitar carga de la librería nativa en tests). */
    private var ortSession: Any? = null
    private var vocab: Map<String, Int> = emptyMap()

    /** Mutex para serializar la inicialización del modelo (evita descargas duplicadas). */
    private val initMutex = Mutex()

    /**
     * Garantiza que el modelo MiniLM esté descargado, extraído y cargado.
     *
     * - Si ya está cargado, retorna `true` inmediatamente.
     * - Si no está descargado, lo descarga a través de [modelRepository].
     * - Si la descarga o carga falla, retorna `false` (fallback seguro).
     *
     * THREAD-SAFE: el Mutex serializa concurrentes que llamen a [embed] simultáneamente.
     */
    internal suspend fun ensureModelLoaded(): Boolean {
        if (ready) return true

        initMutex.withLock {
            // Doble chequeo tras adquirir el mutex
            if (ready) return true

            try {
                Log.i(TAG, "Checking MiniLM embedding model...")

                // 1. Verificar si el modelo ya está descargado
                if (!modelRepository.isReady(ModelAsset.MINILM_EMBEDDINGS)) {
                    Log.d(TAG, "Modelo MiniLM no descargado, iniciando descarga...")
                    modelRepository.download(ModelAsset.MINILM_EMBEDDINGS)
                }

                // 2. Obtener ruta del modelo descargado
                val tarPath = modelRepository.getLocalPath(ModelAsset.MINILM_EMBEDDINGS)
                if (tarPath == null) {
                    Log.w(TAG, "Ruta del modelo MiniLM no disponible tras descarga")
                    return false
                }

                // 3. Extraer model.onnx y tokenizer.json del .tar.gz
                val modelDir = extractModel(tarPath)
                if (modelDir == null) {
                    Log.e(TAG, "No se pudo extraer el modelo MiniLM desde: $tarPath")
                    return false
                }

                // 4. Cargar vocabulario del tokenizer
                val tokenizerFile = File(modelDir, TOKENIZER_FILE)
                if (!tokenizerFile.exists()) {
                    Log.e(TAG, "Tokenizer file not found at: ${tokenizerFile.absolutePath}")
                    return false
                }
                vocab = loadVocab(tokenizerFile)
                if (vocab.isEmpty()) {
                    Log.e(TAG, "Failed to load vocabulary from tokenizer")
                    return false
                }
                Log.i(TAG, "Vocabulary loaded: ${vocab.size} tokens")

                // 5. Crear ONNX Runtime session
                val modelFile = File(modelDir, MODEL_FILE)
                if (!modelFile.exists()) {
                    Log.e(TAG, "Model file not found at: ${modelFile.absolutePath}")
                    return false
                }

                val env = createOrtEnvironment()
                if (env == null) {
                    Log.e(TAG, "Failed to create ONNX Runtime environment")
                    return false
                }
                ortEnv = env

                val session = createOrtSession(modelFile.absolutePath)
                if (session == null) {
                    Log.e(TAG, "Failed to create ONNX session from: ${modelFile.absolutePath}")
                    releaseResources()
                    return false
                }
                ortSession = session

                // 6. Verificar inputs/outputs esperados
                val inputNames = getInputNames(session)
                val outputNames = getOutputNames(session)
                Log.i(TAG, "ONNX inputs: $inputNames, outputs: $outputNames")

                // 7. Test rápido para verificar dimensionalidad
                val testEmbed = runInference("test")
                if (testEmbed.size != EMBEDDING_DIM) {
                    Log.e(TAG, "Unexpected embedding dim: ${testEmbed.size}, expected $EMBEDDING_DIM")
                    releaseResources()
                    return false
                }

                ready = true
                Log.i(TAG, "ONNX embedding model initialized successfully (dim=$EMBEDDING_DIM)")
                return true
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OOM initializing ONNX model", e)
                releaseResources()
                return false
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando modelo MiniLM, fallback seguro", e)
                releaseResources()
                return false
            }
        }
    }

    override suspend fun embed(text: String): FloatArray {
        if (!ensureModelLoaded()) {
            Log.w(TAG, "Model not ready, returning empty embedding")
            return FloatArray(EMBEDDING_DIM)
        }

        return try {
            runInference(text)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM during embedding", e)
            releaseResources()
            FloatArray(EMBEDDING_DIM)
        } catch (e: Exception) {
            Log.e(TAG, "Error during embedding", e)
            FloatArray(EMBEDDING_DIM)
        }
    }

    override suspend fun embedBatch(texts: List<String>): List<FloatArray> {
        if (!ensureModelLoaded()) {
            Log.w(TAG, "Model not ready, returning empty embeddings")
            return texts.map { FloatArray(EMBEDDING_DIM) }
        }

        // Batch inference: tokenizar todos los textos y correr en una sola pasada
        return try {
            val tokenized = texts.map { tokenize(it) }
            val maxLen = tokenized.maxOfOrNull { it.first.size }
                ?: return texts.map { FloatArray(EMBEDDING_DIM) }

            val inputIdsArray = Array(tokenized.size) { i ->
                IntArray(maxLen) { j ->
                    val ids = tokenized[i].first
                    if (j < ids.size) ids[j] else PAD_TOKEN_ID
                }
            }
            val attentionMaskArray = Array(tokenized.size) { i ->
                IntArray(maxLen) { j ->
                    val mask = tokenized[i].second
                    if (j < mask.size) mask[j] else 0
                }
            }

            val env = ortEnv ?: return texts.map { FloatArray(EMBEDDING_DIM) }
            val session = ortSession ?: return texts.map { FloatArray(EMBEDDING_DIM) }

            val inputIdsTensor = createOnnxTensor(env, inputIdsArray)
            val attentionMaskTensor = createOnnxTensor(env, attentionMaskArray)

            if (inputIdsTensor == null || attentionMaskTensor == null) {
                Log.e(TAG, "Failed to create ONNX tensors for batch")
                return texts.map { FloatArray(EMBEDDING_DIM) }
            }

            val results = runOnnxSession(
                session,
                mapOf("input_ids" to inputIdsTensor, "attention_mask" to attentionMaskTensor)
            )

            if (results == null) {
                Log.e(TAG, "ONNX batch run failed")
                closeOnnxTensor(inputIdsTensor)
                closeOnnxTensor(attentionMaskTensor)
                return texts.map { FloatArray(EMBEDDING_DIM) }
            }

            @Suppress("UNCHECKED_CAST")
            val output = extractOutputArray(results)
            closeOnnxTensor(inputIdsTensor)
            closeOnnxTensor(attentionMaskTensor)

            if (output != null) {
                output.map { normalize(it) }
            } else {
                texts.map { embed(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Batch embedding failed, falling back to sequential", e)
            texts.map { embed(it) }
        }
    }

    override fun isReady(): Boolean = ready

    // ── ONNX Runtime wrappers (reflection-based for test compatibility) ────

    /**
     * Crea el entorno ONNX Runtime.
     * Extraído como `internal open` para permitir mocking en tests JVM.
     */
    internal open fun createOrtEnvironment(): Any? {
        return try {
            val ortEnvClass = Class.forName("ai.onnxruntime.OrtEnvironment")
            val getEnvMethod = ortEnvClass.getMethod("getEnvironment")
            getEnvMethod.invoke(null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ONNX Runtime environment", e)
            null
        }
    }

    /**
     * Crea una sesión ONNX Runtime desde la ruta del modelo.
     * Extraído como `internal open` para permitir mocking en tests JVM
     * (la librería nativa de ORT no está disponible en el classpath de tests).
     *
     * Retorna `Any?` para no exponer tipos de ORT en la interfaz pública.
     */
    internal open fun createOrtSession(path: String): Any? {
        return try {
            val env = ortEnv ?: return null
            val ortEnvClass = Class.forName("ai.onnxruntime.OrtEnvironment")
            val createSessionMethod = ortEnvClass.getMethod("createSession", String::class.java)
            createSessionMethod.invoke(env, path)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ONNX session from: $path", e)
            null
        }
    }

    private fun getInputNames(session: Any): Set<String> {
        return try {
            val method = session.javaClass.getMethod("getInputNames")
            @Suppress("UNCHECKED_CAST")
            method.invoke(session) as? Set<String> ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun getOutputNames(session: Any): Set<String> {
        return try {
            val method = session.javaClass.getMethod("getOutputNames")
            @Suppress("UNCHECKED_CAST")
            method.invoke(session) as? Set<String> ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createOnnxTensor(env: Any, data: Array<IntArray>): Any? {
        return try {
            val tensorClass = Class.forName("ai.onnxruntime.OnnxTensor")
            val createMethod = tensorClass.getMethod("createTensor", env.javaClass, Array<IntArray>::class.java)
            createMethod.invoke(null, env, data)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create OnnxTensor", e)
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createOnnxTensor(env: Any, data: Array<FloatArray>): Any? {
        return try {
            val tensorClass = Class.forName("ai.onnxruntime.OnnxTensor")
            val createMethod = tensorClass.getMethod("createTensor", env.javaClass, Array<FloatArray>::class.java)
            createMethod.invoke(null, env, data)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create OnnxTensor (float)", e)
            null
        }
    }

    private fun closeOnnxTensor(tensor: Any) {
        try {
            val closeMethod = tensor.javaClass.getMethod("close")
            closeMethod.invoke(tensor)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close OnnxTensor", e)
        }
    }

    private fun runOnnxSession(session: Any, inputs: Map<String, Any>): Any? {
        return try {
            val method = session.javaClass.getMethod("run", Map::class.java)
            method.invoke(session, inputs)
        } catch (e: Exception) {
            Log.e(TAG, "ONNX session run failed", e)
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractOutputArray(results: Any): Array<FloatArray>? {
        return try {
            // results[0].value as Array<FloatArray>
            val getMethod = results.javaClass.getMethod("get", Int::class.javaPrimitiveType)
            val firstResult = getMethod.invoke(results, 0)
            val valueMethod = firstResult.javaClass.getMethod("getValue")
            valueMethod.invoke(firstResult) as? Array<FloatArray>
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract output array", e)
            null
        }
    }

    /**
     * Ejecuta inferencia ONNX para un solo texto.
     * Extraído como `internal open` para permitir mocking en tests JVM.
     */
    internal open fun runInference(text: String): FloatArray {
        val (inputIds, attentionMask) = tokenize(text)

        val env = ortEnv ?: throw IllegalStateException("ORT environment not initialized")
        val session = ortSession ?: throw IllegalStateException("ORT session not initialized")

        val inputIdsTensor = createOnnxTensor(env, arrayOf(inputIds))
            ?: throw IllegalStateException("Failed to create input_ids tensor")
        val attentionMaskTensor = createOnnxTensor(env, arrayOf(attentionMask))
            ?: throw IllegalStateException("Failed to create attention_mask tensor")

        val results = runOnnxSession(
            session,
            mapOf("input_ids" to inputIdsTensor, "attention_mask" to attentionMaskTensor)
        ) ?: throw IllegalStateException("ONNX inference failed")

        @Suppress("UNCHECKED_CAST")
        val embedding = extractOutputArray(results)?.get(0)
            ?: throw IllegalStateException("Failed to extract embedding from results")

        closeOnnxTensor(inputIdsTensor)
        closeOnnxTensor(attentionMaskTensor)

        return normalize(embedding)
    }

    // ── Model extraction ────────────────────────────────────────────────────

    /**
     * Extrae el modelo MiniLM desde un archivo .tar.gz.
     * Retorna el directorio donde se extrajo, o null si falló.
     *
     * El .tar.gz contiene al menos:
     * - model.onnx
     * - tokenizer.json
     *
     * Extraído como `internal open` para permitir mocking en tests JVM.
     */
    internal open fun extractModel(tarGzPath: String): File? {
        val extractDir = File(context.filesDir, MODEL_DIR)
        extractDir.mkdirs()

        // Si ya fue extraído previamente, retornar directamente
        val modelFile = File(extractDir, MODEL_FILE)
        val tokenizerFile = File(extractDir, TOKENIZER_FILE)
        if (modelFile.exists() && tokenizerFile.exists()) {
            Log.i(TAG, "Model already extracted at: ${extractDir.absolutePath}")
            return extractDir
        }

        return try {
            Log.i(TAG, "Extracting model from: $tarGzPath")
            FileInputStream(tarGzPath).use { fis ->
                GZIPInputStream(fis).use { gis ->
                    extractTarEntries(gis, extractDir)
                }
            }

            // Verificar que ambos archivos se extrajeron
            if (modelFile.exists() && tokenizerFile.exists()) {
                Log.i(TAG, "Model extracted successfully to: ${extractDir.absolutePath}")
                extractDir
            } else {
                Log.e(
                    TAG,
                    "Extraction incomplete: model.onnx=${modelFile.exists()}, " +
                        "tokenizer.json=${tokenizerFile.exists()}"
                )
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract model from: $tarGzPath", e)
            null
        }
    }

    /**
     * Extrae entradas de un stream TAR hacia [targetDir].
     *
     * Formato TAR:
     * - Cada entrada tiene un header de 512 bytes seguido de datos en bloques de 512 bytes.
     * - Nombre del archivo: offset 0, 100 bytes.
     * - Tamaño del archivo: offset 124, 12 bytes (octal string).
     * - Tipo de archivo: offset 156, 1 byte ('0' = archivo regular).
     */
    private fun extractTarEntries(
        input: java.io.InputStream,
        targetDir: File
    ) {
        val header = ByteArray(TAR_BLOCK_SIZE)

        while (true) {
            // Leer header
            val bytesRead = input.read(header)
            if (bytesRead < TAR_BLOCK_SIZE) break // Fin del stream

            // Header de fin: todos ceros
            if (header.all { it == 0.toByte() }) break

            // Nombre del archivo (offset 0, 100 bytes)
            val nameBytes = header.copyOfRange(0, 100)
            val name = String(nameBytes).trimTrimNulls()

            // Tipo de archivo (offset 156, 1 byte)
            val typeFlag = header[156].toInt().toChar()
            if (typeFlag != '0' && typeFlag != '\u0000') {
                // No es archivo regular — skip
                skipTarBlocks(input, header)
                continue
            }

            // Tamaño del archivo (offset 124, 12 bytes — octal)
            val sizeBytes = header.copyOfRange(124, 136)
            val sizeStr = String(sizeBytes).trimTrimNulls()
            val size = sizeStr.toLongOrNull(8) ?: 0L

            if (name.isNotEmpty() && size > 0) {
                // Extraer solo los archivos que nos interesan
                val fileName = File(name).name
                if (fileName == MODEL_FILE || fileName == TOKENIZER_FILE) {
                    val outFile = File(targetDir, fileName)
                    val data = ByteArray(size.toInt())
                    readExact(input, data)
                    outFile.writeBytes(data)
                    Log.i(TAG, "Extracted: $fileName (${size} bytes) → ${outFile.absolutePath}")

                    // Pad a siguiente bloque de 512 bytes
                    val remainder = (TAR_BLOCK_SIZE - (size % TAR_BLOCK_SIZE).toInt()) % TAR_BLOCK_SIZE
                    if (remainder > 0) input.read(ByteArray(remainder))
                } else {
                    // Skip data blocks
                    skipDataBlocks(input, size)
                }
            } else {
                skipTarBlocks(input, header)
            }
        }
    }

    private fun readExact(input: java.io.InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read == -1) break
            offset += read
        }
    }

    private fun skipDataBlocks(input: java.io.InputStream, size: Long) {
        val blocks = ((size + TAR_BLOCK_SIZE - 1) / TAR_BLOCK_SIZE).toInt()
        val skipBuffer = ByteArray(TAR_BLOCK_SIZE)
        repeat(blocks) {
            readExact(input, skipBuffer)
        }
    }

    private fun skipTarBlocks(input: java.io.InputStream, header: ByteArray) {
        // Re-leer el header como datos (ya fue leído antes)
        val sizeBytes = header.copyOfRange(124, 136)
        val sizeStr = String(sizeBytes).trimTrimNulls()
        val size = sizeStr.toLongOrNull(8) ?: 0L
        skipDataBlocks(input, size)
    }

    private fun String.trimTrimNulls(): String = this.trim().trim('\u0000')

    // ── Tokenizer & Inference ──────────────────────────────────────────────

    /**
     * Tokeniza texto usando word-piece simple con el vocabulario cargado.
     * Retorna Pair<IntArray, IntArray> = (input_ids, attention_mask)
     */
    private fun tokenize(text: String): Pair<IntArray, IntArray> {
        val tokens = mutableListOf<Int>()
        tokens.add(CLS_TOKEN_ID)

        // Tokenización simple: split por whitespace + lowercase + lookup en vocabulario
        val words = text.lowercase().trim().split(Regex("\\s+"))
        for (word in words) {
            if (tokens.size >= MAX_SEQ_LENGTH - 1) break // Reservar espacio para SEP

            // Intentar tokenizar como palabra completa
            val wordId = vocab[word]
            if (wordId != null) {
                tokens.add(wordId)
            } else {
                // Subword tokenization (word-piece simple)
                tokenizeWordPiece(word, tokens)
            }
        }

        tokens.add(SEP_TOKEN_ID)

        // Crear attention mask (1 para tokens reales, 0 para padding)
        val mask = IntArray(tokens.size) { 1 }

        // Pad a longitud fija si es necesario
        val paddedIds = IntArray(MAX_SEQ_LENGTH) { i ->
            if (i < tokens.size) tokens[i] else PAD_TOKEN_ID
        }
        val paddedMask = IntArray(MAX_SEQ_LENGTH) { i ->
            if (i < mask.size) mask[i] else 0
        }

        return Pair(paddedIds, paddedMask)
    }

    /**
     * Tokenización word-piece para una palabra.
     * Divide la palabra en subwords usando el vocabulario.
     */
    private fun tokenizeWordPiece(word: String, tokens: MutableList<Int>) {
        if (word.isEmpty()) return

        var start = 0
        while (start < word.length && tokens.size < MAX_SEQ_LENGTH - 1) {
            var end = word.length
            var found = false

            while (start < end) {
                var sub = word.substring(start, end)
                if (start > 0) sub = "##$sub"

                val id = vocab[sub]
                if (id != null) {
                    tokens.add(id)
                    found = true
                    start = end
                    break
                }
                end--
            }

            if (!found) {
                // Token desconocido — usar [UNK]
                val unkId = vocab["[UNK]"] ?: 100
                tokens.add(unkId)
                start++
            }
        }
    }

    /**
     * Carga el vocabulario del tokenizer.json usando org.json (Android built-in).
     * El archivo tiene la estructura: { "model": { "vocab": { "token": id, ... } } }
     */
    private fun loadVocab(tokenizerFile: File): Map<String, Int> {
        return try {
            val jsonStr = tokenizerFile.readText()
            val json = JSONObject(jsonStr)
            val model = json.getJSONObject("model")
            val vocabObj = model.getJSONObject("vocab")

            val result = mutableMapOf<String, Int>()
            val keys = vocabObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = vocabObj.getInt(key)
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing tokenizer vocab", e)
            emptyMap()
        }
    }

    /**
     * Normaliza un vector para que tenga magnitud unitaria.
     * Necesario para que cosine similarity funcione correctamente.
     */
    private fun normalize(vector: FloatArray): FloatArray {
        var norm = 0f
        for (value in vector) {
            norm += value * value
        }
        norm = kotlin.math.sqrt(norm)

        if (norm == 0f) return vector

        return FloatArray(vector.size) { vector[it] / norm }
    }

    /**
     * Cierra la sesión ONNX de forma segura.
     * Usa reflexión para evitar dependencia directa de la clase OrtSession
     * en el classpath (la librería nativa no está disponible en JVM tests).
     */
    private fun closeOrtSession() {
        val session = ortSession ?: return
        try {
            val closeMethod = session.javaClass.getMethod("close")
            closeMethod.invoke(session)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close ONNX session", e)
        }
    }

    /**
     * Libera todos los recursos ONNX Runtime.
     */
    private fun releaseResources() {
        try {
            closeOrtSession()
            ortSession = null
            ortEnv = null
            ready = false
            Log.i(TAG, "ONNX resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing ONNX resources", e)
            ready = false
        }
    }
}
