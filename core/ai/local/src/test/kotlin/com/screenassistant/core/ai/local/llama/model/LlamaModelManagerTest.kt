package com.screenassistant.core.ai.local.llama.model

import android.content.Context
import android.util.Log
import com.screenassistant.core.ai.local.llama.LlamaCppEngine
import com.screenassistant.core.domain.model.ModelState
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests unitarios para LlamaModelManager.
 */
class LlamaModelManagerTest {

    private lateinit var context: Context
    private lateinit var engine: LlamaCppEngine
    private lateinit var downloader: ModelDownloader
    private lateinit var manager: LlamaModelManager
    private lateinit var modelsDir: File

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0

        context = mockk(relaxed = true)
        engine = mockk(relaxed = true)
        downloader = mockk(relaxed = true)

        modelsDir = File(System.getProperty("java.io.tmpdir"), "llama_models_test")
        modelsDir.mkdirs()

        every { context.filesDir } returns modelsDir.parentFile

        manager = LlamaModelManager(context, engine, downloader)
    }

    @Test
    fun `availableModels retorna catalogo completo`() {
        assertEquals(2, manager.availableModels.size)
    }

    @Test
    fun `isModelDownloaded retorna false cuando no existe`() {
        assertFalse(manager.isModelDownloaded())
    }

    @Test
    fun `getModelSizeBytes retorna tamanho del modelo`() {
        assertTrue(manager.getModelSizeBytes() > 0)
    }

    @Test
    fun `getRequiredSpaceBytes es mayor que el tamanho`() {
        assertTrue(manager.getRequiredSpaceBytes() > manager.getModelSizeBytes())
    }

    @Test
    fun `selectModel cambia el modelo seleccionado`() {
        manager.selectModel("tinyllama-1.1b-chat-v1.0-q8_0")
        assertEquals("tinyllama-1.1b-chat-v1.0-q8_0", manager.getModelInfo()?.id)
    }

    @Test
    fun `selectModel con ID invalido no cambia`() {
        val originalId = manager.getModelInfo()?.id
        manager.selectModel("id-invalido")
        assertEquals(originalId, manager.getModelInfo()?.id)
    }

    @Test
    fun `getModelState retorna estado inicial`() {
        assertEquals(ModelState.NOT_DOWNLOADED, manager.getModelState())
    }

    @Test
    fun `getModelInfo retorna modelo por defecto`() {
        assertNotNull(manager.getModelInfo())
        assertEquals(LlamaModelCatalog.DEFAULT.id, manager.getModelInfo()?.id)
    }

    @Test
    fun `getLocalModelPath retorna null cuando no existe`() {
        assertNull(manager.getLocalModelPath("modelo-fantasma"))
    }

    @Test
    fun `deleteModel retorna false cuando no existe`() {
        assertFalse(manager.deleteModel("modelo-fantasma"))
    }
}
