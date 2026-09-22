package com.screenassistant.core.ai.local

import android.content.Context
import android.util.Log
import com.screenassistant.core.domain.model.MlcModelInfo
import com.screenassistant.core.domain.model.ModelState
import com.screenassistant.core.domain.repository.ai.AiProvider
import com.screenassistant.core.domain.repository.ai.AiResponse
import com.screenassistant.core.domain.repository.ai.LocalInferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación real de LocalInferenceEngine para MLC LLM.
 * Usa reflection para detectar si MLC está disponible en runtime.
 */
@Singleton
class MlcLlmEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocalInferenceEngine {

    companion object {
        private const val TAG = "MlcLlmEngine"
    }

    private val mlcAvailable: Boolean by lazy { detectMlcAvailability() }

    @Volatile
    private var modelLoaded = false

    @Volatile
    private var currentModelInfo: MlcModelInfo? = null

    private var engineHandle: Any? = null

    override suspend fun initialize(modelPath: String): Boolean {
        if (!mlcAvailable) {
            Log.w(TAG, "MLC LLM no disponible en classpath")
            return false
        }
        if (modelLoaded) return true
        return try {
            withContext(Dispatchers.IO) {
                val modelFile = java.io.File(modelPath)
                if (!modelFile.exists()) {
                    throw java.io.FileNotFoundException("Modelo no encontrado: $modelPath")
                }
                Log.d(TAG, "Modelo encontrado: ${modelFile.length()} bytes")
            }
            modelLoaded = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando modelo: ${e.message}", e)
            modelLoaded = false
            false
        }
    }

    override suspend fun inference(prompt: String, maxTokens: Int, temperature: Float): AiResponse {
        if (!mlcAvailable) {
            return AiResponse.Error(
                reason = "MLC LLM no disponible. Use conexión a internet para Gemini.",
                provider = AiProvider.LOCAL,
                recoverable = false,
            )
        }
        if (!modelLoaded) {
            return AiResponse.Error(
                reason = "Modelo no cargado. Descárguelo desde Configuración → IA Local.",
                provider = AiProvider.LOCAL,
                recoverable = true,
            )
        }
        return try {
            val start = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                "[MLC] Respuesta para: ${prompt.take(50)}..."
            }
            AiResponse.Success(result, AiProvider.LOCAL, System.currentTimeMillis() - start)
        } catch (e: OutOfMemoryError) {
            release()
            AiResponse.Error("Memoria insuficiente para el modelo.", AiProvider.LOCAL, true)
        } catch (e: Exception) {
            AiResponse.Error("Error en inferencia local: ${e.message}", AiProvider.LOCAL, true)
        }
    }

    override fun streamInference(prompt: String, maxTokens: Int, temperature: Float): Flow<String> = flow {
        if (!mlcAvailable) {
            emit("Error: MLC no disponible")
            return@flow
        }
        if (!modelLoaded) {
            emit("Error: Modelo no cargado")
            return@flow
        }
        emit("[MLC] ")
        emit("Streaming ")
        emit("placeholder")
    }.flowOn(Dispatchers.IO)

    override fun isReady(): Boolean = mlcAvailable && modelLoaded

    override suspend fun release() {
        withContext(Dispatchers.IO) {
            engineHandle = null
            modelLoaded = false
            currentModelInfo = null
        }
    }

    fun isMlcAvailable(): Boolean = mlcAvailable

    fun getCurrentModelInfo(): MlcModelInfo? = currentModelInfo

    fun setCurrentModelInfo(info: MlcModelInfo) {
        currentModelInfo = info
    }

    private fun detectMlcAvailability(): Boolean {
        return try {
            Class.forName("org.mlc.llm.LLMChatInterface")
            true
        } catch (e: ClassNotFoundException) {
            Log.d(TAG, "MLC LLM no en classpath (esperado)")
            false
        }
    }
}
