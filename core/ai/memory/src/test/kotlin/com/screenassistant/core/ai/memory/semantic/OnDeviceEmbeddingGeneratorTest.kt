package com.screenassistant.core.ai.memory.semantic

import android.content.Context
import com.screenassistant.core.model.domain.ModelAsset
import com.screenassistant.core.model.domain.ModelAssetRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * OnDeviceEmbeddingGenerator — integración con ModelAssetRepository para descarga bajo demanda.
 *
 * Verifica:
 * 1. Modelo listo → genera embeddings (session creada)
 * 2. Modelo no listo → descarga primero
 * 3. Descarga falla → retorna lista vacía (fallback seguro)
 * 4. isReady refleja estado correctamente
 *
 * NOTA: La librería nativa de ORT no está disponible en JVM tests.
 * Se usa spyk + override de [OnDeviceEmbeddingGenerator.createOrtSession] (internal open)
 * y [OnDeviceEmbeddingGenerator.createOrtEnvironment] para evitar UnsatisfiedLinkError.
 * También se hace override de [OnDeviceEmbeddingGenerator.extractModel] para evitar
 * leer archivos .tar.gz reales (no disponibles en tests JVM).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnDeviceEmbeddingGeneratorTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var modelRepository: ModelAssetRepository
    private lateinit var tempDir: File

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        tempDir = File(System.getProperty("java.io.tmpdir"), "minilm-test-${System.nanoTime()}")
        tempDir.mkdirs()
        context = mockk(relaxed = true)
        every { context.filesDir } returns tempDir
        modelRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempDir.deleteRecursively()
    }

    /**
     * Prepara archivos extraídos de modelo en el directorio temporal.
     * Retorna el directorio del modelo extraído.
     */
    private fun setupExtractedModel(): File {
        val extractDir = File(tempDir, "all-minilm-l6-v2")
        extractDir.mkdirs()
        File(extractDir, "model.onnx").writeBytes(ByteArray(10))
        File(extractDir, "tokenizer.json").writeText(
            """{"model":{"vocab":{"hello":1,"world":2,"[UNK]":100,"[CLS]":101,"[SEP]":102}}}"""
        )
        return extractDir
    }

    /**
     * Crea un OnDeviceEmbeddingGenerator espyado con la sesión ONNX mockeada,
     * el entorno mockeado, la extracción mockeada, y la inferencia mockeada.
     */
    private fun createGenerator(
        sessionMock: Any? = Object(),
        envMock: Any? = Object()
    ): OnDeviceEmbeddingGenerator {
        val real = OnDeviceEmbeddingGenerator(
            context = context,
            modelRepository = modelRepository,
            dispatcher = testDispatcher
        )
        val extractDir = setupExtractedModel()
        return spyk(real).apply {
            every { createOrtEnvironment() } returns envMock
            every { createOrtSession(any()) } returns sessionMock
            every { extractModel(any()) } returns extractDir
            every { runInference(any()) } returns FloatArray(384) { 0.1f }
        }
    }

    /**
     * Crea un OnDeviceEmbeddingGenerator cuyo createOrtSession falla (retorna null).
     */
    private fun createGeneratorWithFailedSession(): OnDeviceEmbeddingGenerator {
        val real = OnDeviceEmbeddingGenerator(
            context = context,
            modelRepository = modelRepository,
            dispatcher = testDispatcher
        )
        return spyk(real).apply {
            every { createOrtEnvironment() } returns Object()
            every { createOrtSession(any()) } returns null
            every { extractModel(any()) } returns setupExtractedModel()
        }
    }

    // ── Test 1: Modelo listo → genera embeddings ──────────────────────────

    @Test
    fun embedModeloListoGeneraEmbeddings() = runTest {
        coEvery { modelRepository.isReady(ModelAsset.MINILM_EMBEDDINGS) } returns true
        coEvery { modelRepository.getLocalPath(ModelAsset.MINILM_EMBEDDINGS) } returns "/test/minilm.tar.gz"

        val generator = createGenerator()

        val result = generator.ensureModelLoaded()

        assertTrue("Model should be loaded", result)
        assertTrue("Generator should be ready", generator.isReady())
        coVerify(exactly = 0) { modelRepository.download(any()) }
    }

    // ── Test 2: Modelo no listo → descarga primero ───────────────────────

    @Test
    fun embedModeloNoListoDescargaPrimero() = runTest {
        coEvery { modelRepository.isReady(ModelAsset.MINILM_EMBEDDINGS) } returns false
        coEvery { modelRepository.getLocalPath(ModelAsset.MINILM_EMBEDDINGS) } returns "/test/minilm.tar.gz"

        val generator = createGenerator()

        val result = generator.ensureModelLoaded()

        assertTrue("Model should be loaded after download", result)
        coVerify(exactly = 1) { modelRepository.download(ModelAsset.MINILM_EMBEDDINGS) }
    }

    // ── Test 3: Descarga falla → retorna lista vacía ─────────────────────

    @Test
    fun embedDescargaFallaRetornaListaVacia() = runTest {
        coEvery { modelRepository.isReady(ModelAsset.MINILM_EMBEDDINGS) } returns false
        coEvery { modelRepository.download(ModelAsset.MINILM_EMBEDDINGS) } throws RuntimeException("Error de red")
        coEvery { modelRepository.getLocalPath(ModelAsset.MINILM_EMBEDDINGS) } returns null

        val generator = createGeneratorWithFailedSession()

        val result = generator.ensureModelLoaded()

        assertFalse("Model should NOT be loaded after failed download", result)
        assertFalse("Generator should NOT be ready", generator.isReady())
        coVerify(exactly = 1) { modelRepository.download(ModelAsset.MINILM_EMBEDDINGS) }
    }

    // ── Test 4: embed retorna empty FloatArray cuando modelo no está listo ──

    @Test
    fun embedModeloNoListoRetornaEmbeddingVacio() = runTest {
        coEvery { modelRepository.isReady(ModelAsset.MINILM_EMBEDDINGS) } returns false
        coEvery { modelRepository.download(ModelAsset.MINILM_EMBEDDINGS) } throws RuntimeException("Error")

        val generator = createGeneratorWithFailedSession()

        val embedding = generator.embed("hola mundo")

        assertEquals("Embedding should have correct dimension", 384, embedding.size)
        assertFalse("Generator should NOT be ready", generator.isReady())
    }

    // ── Test 5: embedBatch retorna empty embeddings cuando modelo no listo ──

    @Test
    fun embedBatchModeloNoListoRetornaEmbeddingsVacios() = runTest {
        coEvery { modelRepository.isReady(ModelAsset.MINILM_EMBEDDINGS) } returns false
        coEvery { modelRepository.download(ModelAsset.MINILM_EMBEDDINGS) } throws RuntimeException("Error")

        val generator = createGeneratorWithFailedSession()

        val embeddings = generator.embedBatch(listOf("hola", "mundo"))

        assertEquals("Should return same number of embeddings", 2, embeddings.size)
        embeddings.forEach { embedding ->
            assertEquals("Each embedding should have correct dimension", 384, embedding.size)
        }
    }

    // ── Test 6: destroy libera recursos ───────────────────────────────────

    @Test
    fun destroyLiberaRecursos() = runTest {
        coEvery { modelRepository.isReady(ModelAsset.MINILM_EMBEDDINGS) } returns true
        coEvery { modelRepository.getLocalPath(ModelAsset.MINILM_EMBEDDINGS) } returns "/test/minilm.tar.gz"

        val generator = createGenerator()

        // Forzar carga del modelo
        val loaded = generator.ensureModelLoaded()

        assertTrue("Model should be loaded", loaded)
        assertTrue("Generator should be ready", generator.isReady())
    }
}
