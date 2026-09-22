# Sprint 2: Interfaces y Modelos

Referencia para el Desarrollador. Signatures completas.

## 1. LlamaInitParams.kt

    data class LlamaInitParams(
        val nCtx: Int = 2048,
        val nBatch: Int = 512,
        val nThreads: Int = 4,
        val useMmap: Boolean = true,
        val useMlock: Boolean = false,
        val verbose: Boolean = false,
    )

## 2. LlamaModelCatalog.kt

    object LlamaModelCatalog {
        val TINY_LLAMA_Q4: ModelInfo
        val TINY_LLAMA_Q8: ModelInfo
        val ALL: List<ModelInfo>
        val DEFAULT: ModelInfo
    }

Usa ModelInfo existente de core/domain. URLs apuntan a HuggingFace GGUF.

## 3. LlamaCppBridge.kt

internal class con external fun. Sistema.loadLibrary("llama_jni").

    internal class LlamaCppBridge {
        companion object { init { System.loadLibrary("llama_jni") } }
        external fun nativeInit(params: LlamaInitParams): Long
        external fun nativeDestroy(handle: Long)
        external fun nativeLoadModel(handle: Long, modelPath: String, nThreads: Int): Long
        external fun nativeFreeModel(handle: Long, modelHandle: Long)
        external fun nativeGenerate(handle: Long, modelHandle: Long, prompt: String,
            maxTokens: Int, temperature: Float, topP: Float, topK: Int): String
        external fun nativeStreamGenerate(handle: Long, modelHandle: Long, prompt: String,
            maxTokens: Int, temperature: Float, topP: Float, topK: Int,
            onToken: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit)
        external fun nativeIsReady(handle: Long): Boolean
        external fun nativeTokenCount(handle: Long, text: String): Int
    }

## 4. LlamaCppEngine.kt

Implementa LocalInferenceEngine de core/domain.

    class LlamaCppEngine(
        private val bridge: LlamaCppBridge,
        private val config: LlamaCppConfig,
    ) : LocalInferenceEngine {
        @Volatile private var contextHandle: Long = 0L
        @Volatile private var modelHandle: Long = 0L
        @Volatile private var ready = false
        override suspend fun initialize(modelPath: String): Boolean
        override suspend fun inference(prompt: String, maxTokens: Int, temperature: Float): AiResponse
        override fun streamInference(prompt: String, maxTokens: Int, temperature: Float): Flow
        override fun isReady(): Boolean
        override suspend fun release()
    }

### Logica de initialize
1. Si ready, return true
2. LlamaInitParams desde config
3. bridge.nativeInit(params) -> contextHandle
4. bridge.nativeLoadModel(contextHandle, modelPath, config.numThreads) -> modelHandle
5. ready = bridge.nativeIsReady(contextHandle)

### Logica de inference
1. Si !ready -> AiResponse.Error("Modelo no cargado", LOCAL, recoverable=true)
2. start = System.currentTimeMillis()
3. bridge.nativeGenerate(...) -> result
4. return AiResponse.Success(result, LOCAL, latency)
5. catch OOM -> release() + AiResponse.Error

### Logica de streamInference
1. callbackFlow con awaitClose
2. bridge.nativeStreamGenerate(..., onToken, onDone, onError)
3. onToken: trySend(token)
4. onDone: close()
5. onError: trySend error + close()
6. flowOn(Dispatchers.IO)

## 5. LlamaCppConfig.kt

    @Singleton
    class LlamaCppConfig @Inject constructor() {
        var contextSize: Int = 2048
        var batchSize: Int = 512
        var numThreads: Int = availableProcessors.coerceIn(2, 8)
        var maxTokens: Int = 1024
        var temperature: Float = 0.7f
        var topP: Float = 0.9f
        var topK: Int = 40
        var repeatPenalty: Float = 1.1f
        var systemPrompt: String = "Eres J.A.R.V.I.S..."
    }

## 6. LlamaModelManager.kt

Implementa ModelManager de core/domain.

    @Singleton
    class LlamaModelManager @Inject constructor(
        @ApplicationContext private val context: Context,
        private val engine: LlamaCppEngine,
        private val downloader: ModelDownloader,
    ) : ModelManager {
        override val availableModels: List<ModelInfo>
        override fun isModelDownloaded(): Boolean
        override fun getModelSizeBytes(): Long
        override fun getRequiredSpaceBytes(): Long
        override suspend fun downloadModel(progressCallback: (Float) -> Unit): Result
        override suspend fun loadModel(): Boolean
        override suspend fun unloadModel()
        override fun getModelInfo(): ModelInfo?
        fun selectModel(modelId: String)
        fun getModelState(): ModelState
        fun getAvailableSpaceBytes(): Long
        fun deleteModel(modelId: String): Boolean
    }

Directorio: {filesDir}/llama_models/

## 7. ModelDownloader.kt

    @Singleton
    class ModelDownloader @Inject constructor() {
        suspend fun download(url: String, targetFile: File,
            progressCallback: (Float) -> Unit = {}): Unit
    }

HttpURLConnection, max 2 reintentos, backoff exponencial.

## 8. PromptTemplate.kt

    object PromptTemplate {
        fun tinyLlamaChat(systemPrompt: String, userMessage: String,
            conversationHistory: List<Pair<String, String>> = emptyList()): String
        fun chatML(systemPrompt: String, userMessage: String,
            conversationHistory: List<Pair<String, String>> = emptyList()): String
        fun detectTemplate(modelId: String): Function3
    }

## 9. DI: LlamaModule.kt

    @Module
    @InstallIn(SingletonComponent::class)
    object LlamaModule {
        @Provides @Singleton
        fun provideLlamaCppBridge(): LlamaCppBridge
        @Provides @Singleton
        fun provideLlamaCppConfig(): LlamaCppConfig
        @Provides @Singleton
        fun provideModelDownloader(): ModelDownloader
        @Provides @Singleton
        fun provideLlamaCppEngine(bridge, config): LlamaCppEngine
        @Provides @Singleton
        fun provideLocalInferenceEngine(engine: LlamaCppEngine): LocalInferenceEngine
        @Provides @Singleton
        fun provideModelManager(context, engine, downloader): LlamaModelManager
    }
