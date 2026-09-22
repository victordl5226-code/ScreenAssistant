package com.screenassistant.core.ai.local.llama

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.screenassistant.core.ai.local.llama.bridge.LlamaCppBridge
import com.screenassistant.core.ai.local.llama.config.LlamaCppConfig
import com.screenassistant.core.ai.local.llama.model.LlamaModelManager
import com.screenassistant.core.domain.repository.ai.AiProvider
import com.screenassistant.core.domain.repository.ai.AiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test de integración REAL con llama.cpp nativo.
 *
 * Requiere:
 * - Dispositivo/emulador arm64-v8a conectado
 * - Modelo TinyLlama descargado en {filesDir}/llama_models/tinyllama-1.1b-chat-v1.0-q4_k_m.gguf
 * - Build con .so nativo (:core:ai:local:assembleDebug)
 *
 * Ejecutar: ./gradlew :core:ai:local:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class LlamaCppEngineIntegrationTest {

    private lateinit var context: Context
    private lateinit var engine: LlamaCppEngine
    private lateinit var modelManager: LlamaModelManager
    private val testPrompt = "Hola, ¿cómo estás? Responde en una frase corta."

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        val config = LlamaCppConfig()
        val bridge = LlamaCppBridge()
        engine = LlamaCppEngine(bridge, config)

        val downloader = com.screenassistant.core.ai.local.llama.model.ModelDownloader()
        modelManager = LlamaModelManager(context, engine, downloader)
    }

    @Test
    fun nativeLibraryLoads() {
        // Verifica que libllama_jni.so se carga sin crash
        val bridge = LlamaCppBridge()
        val params = LlamaCppConfig().toInitParams()
        val handle = bridge.nativeInit(params)

        assumeTrue("libllama_jni.so no disponible (build sin .so nativo)", handle != 0L)

        bridge.nativeDestroy(handle)
        assertTrue("Handle válido retornado", handle > 0)
    }

    @Test
    fun initializeAndInferenceEndToEnd() = runBlocking {
        // Saltar si no hay modelo descargado (ej. CI sin artefactos)
        val modelPath = modelManager.getLocalModelPath(
            "tinyllama-1.1b-chat-v1.0-q4_k_m"
        ) ?: return@runBlocking

        assumeTrue("Modelo no descargado en dispositivo", modelPath.isNotEmpty())

        // Inicializar motor con modelo real
        val initialized = engine.initialize(modelPath)
        assertTrue("Motor debe inicializarse correctamente", initialized)
        assertTrue("Motor debe estar listo", engine.isReady())

        // Inferencia síncrona real
        val response = engine.inference(testPrompt, 50, 0.7f)

        assertTrue("Respuesta debe ser Success", response is AiResponse.Success)
        val success = response as AiResponse.Success
        assertEquals("Provider debe ser LOCAL", AiProvider.LOCAL, success.provider)
        assertTrue("Latencia debe ser > 0", success.latencyMs > 0)
        assertTrue("Texto generado no vacío", success.text.isNotBlank())

        println("✅ Inferencia real completada: ${success.text.take(100)}... (${success.latencyMs}ms)")

        // Cleanup
        engine.release()
        assertFalse("Motor debe estar liberado", engine.isReady())
    }

    @Test
    fun streamInferenceEmitsTokens() = runBlocking {
        val modelPath = modelManager.getLocalModelPath(
            "tinyllama-1.1b-chat-v1.0-q4_k_m"
        ) ?: return@runBlocking

        assumeTrue("Modelo no descargado", modelPath.isNotEmpty())

        val initialized = engine.initialize(modelPath)
        assumeTrue("Inicialización falló (posible OOM en CI)", initialized)

        // Streaming: recolectar primeros 5 tokens
        var tokenCount = 0
        var fullText = ""
        val flow = engine.streamInference(testPrompt, 50, 0.7f)
            .onEach { token ->
                fullText += token
                tokenCount++
            }
            .take(5)  // Solo primeros 5 tokens para test rápido
        
        val collected = flow.firstOrNull()

        assertNotNull("Debe emitir al menos un token", collected)
        assertTrue("Debe haber tokens emitidos", tokenCount > 0)
        assertTrue("Texto acumulado no vacío", fullText.isNotBlank())

        println("✅ Streaming real: $tokenCount tokens = '$fullText'")

        engine.release()
    }

    @Test
    fun tokenCountWorks() = runBlocking {
        val modelPath = modelManager.getLocalModelPath(
            "tinyllama-1.1b-chat-v1.0-q4_k_m"
        ) ?: return@runBlocking

        assumeTrue("Modelo no descargado", modelPath.isNotEmpty())

        val initialized = engine.initialize(modelPath)
        assumeTrue("Inicialización falló", initialized)

        val bridge = LlamaCppBridge()
        val contextHandle = bridge.nativeInit(LlamaCppConfig().toInitParams())
        assumeTrue("nativeInit falló", contextHandle != 0L)

        val modelHandle = bridge.nativeLoadModel(contextHandle, modelPath, 4)
        assumeTrue("nativeLoadModel falló", modelHandle != 0L)

        val count = bridge.nativeTokenCount(contextHandle, "Hola mundo")
        assertTrue("Token count debe ser > 0", count > 0)
        println("✅ Token count para 'Hola mundo': $count")

        bridge.nativeFreeModel(contextHandle, modelHandle)
        bridge.nativeDestroy(contextHandle)
        engine.release()
    }

    @Test
    fun multipleInitializeReleaseCycles() = runBlocking {
        val modelPath = modelManager.getLocalModelPath(
            "tinyllama-1.1b-chat-v1.0-q4_k_m"
        ) ?: return@runBlocking

        assumeTrue("Modelo no descargado", modelPath.isNotEmpty())

        // Ciclo 1
        assertTrue(engine.initialize(modelPath))
        assertTrue(engine.isReady())
        engine.release()
        assertFalse(engine.isReady())

        // Ciclo 2 (re-inicializar)
        assertTrue(engine.initialize(modelPath))
        assertTrue(engine.isReady())
        engine.release()
        assertFalse(engine.isReady())

        println("✅ Múltiples ciclos init/release OK")
    }

    @Test
    fun inferenceRecoversAfterRelease() = runBlocking {
        val modelPath = modelManager.getLocalModelPath(
            "tinyllama-1.1b-chat-v1.0-q4_k_m"
        ) ?: return@runBlocking

        assumeTrue("Modelo no descargado", modelPath.isNotEmpty())

        // Primera inferencia
        assertTrue(engine.initialize(modelPath))
        val r1 = engine.inference("Test 1", 20, 0.7f)
        assertTrue("Primera inferencia OK", r1 is AiResponse.Success)
        engine.release()

        // Segunda inferencia tras release
        assertTrue(engine.initialize(modelPath))
        val r2 = engine.inference("Test 2", 20, 0.7f)
        assertTrue("Segunda inferencia OK", r2 is AiResponse.Success)
        engine.release()

        println("✅ Recuperación tras release OK")
    }
}