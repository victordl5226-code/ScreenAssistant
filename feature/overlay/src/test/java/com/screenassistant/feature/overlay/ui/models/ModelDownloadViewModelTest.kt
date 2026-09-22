package com.screenassistant.feature.overlay.ui.models

import com.screenassistant.core.model.domain.ModelAsset
import com.screenassistant.core.model.domain.ModelAssetRepository
import com.screenassistant.core.model.domain.ModelAssetStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ModelDownloadViewModel — tests de la pantalla de descarga de modelos.
 *
 * Verifica:
 * 1. Estado inicial carga los modelos
 * 2. Descarga de modelo exitosa
 * 3. Descarga de modelo con error
 * 4. Eliminación de modelo
 * 5. Refresh status actualiza el estado
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelDownloadViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var modelRepository: ModelAssetRepository

    private val piperStatus = MutableStateFlow<ModelAssetStatus>(ModelAssetStatus.NotDownloaded)
    private val voskStatus = MutableStateFlow<ModelAssetStatus>(ModelAssetStatus.NotDownloaded)
    private val minilmStatus = MutableStateFlow<ModelAssetStatus>(ModelAssetStatus.NotDownloaded)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        modelRepository = mockk(relaxed = true)

        every { modelRepository.availableModels() } returns listOf(
            ModelAsset.PIPER_TTS,
            ModelAsset.VOSK_STT,
            ModelAsset.MINILM_EMBEDDINGS,
        )
        every { modelRepository.observeStatus(ModelAsset.PIPER_TTS) } returns piperStatus
        every { modelRepository.observeStatus(ModelAsset.VOSK_STT) } returns voskStatus
        every { modelRepository.observeStatus(ModelAsset.MINILM_EMBEDDINGS) } returns minilmStatus

        coEvery { modelRepository.isReady(any()) } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ModelDownloadViewModel {
        return ModelDownloadViewModel(
            modelRepository = modelRepository,
            ioDispatcher = testDispatcher,
        )
    }

    // ── Test 1: Estado inicial carga los modelos ─────────────────────────

    @Test
    fun initialStateLoadsModels() = runTest {
        val viewModel = createViewModel()

        val state = viewModel.uiState.value

        assertEquals("Should have 3 models", 3, state.models.size)
        assertFalse("Should not be loading", state.isLoading)
        assertNull("Should have no error", state.errorMessage)
    }

    // ── Test 2: Descarga de modelo exitosa ───────────────────────────────

    @Test
    fun downloadModelSuccess() = runTest {
        coEvery { modelRepository.download(ModelAsset.PIPER_TTS) } returns Unit
        coEvery { modelRepository.isReady(ModelAsset.PIPER_TTS) } returns true

        val viewModel = createViewModel()

        viewModel.downloadModel(ModelAsset.PIPER_TTS)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { modelRepository.download(ModelAsset.PIPER_TTS) }

        val state = viewModel.uiState.value
        assertNull("Should have no error", state.errorMessage)
    }

    // ── Test 3: Descarga de modelo con error ─────────────────────────────

    @Test
    fun downloadModelError() = runTest {
        coEvery { modelRepository.download(ModelAsset.PIPER_TTS) } throws RuntimeException("Error de red")

        val viewModel = createViewModel()

        viewModel.downloadModel(ModelAsset.PIPER_TTS)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Should have error message", state.errorMessage?.contains("Error de red") == true)
    }

    // ── Test 4: Eliminación de modelo ────────────────────────────────────

    @Test
    fun deleteModelSuccess() = runTest {
        coEvery { modelRepository.delete(ModelAsset.PIPER_TTS) } returns Result.success(Unit)

        val viewModel = createViewModel()

        viewModel.deleteModel(ModelAsset.PIPER_TTS)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { modelRepository.delete(ModelAsset.PIPER_TTS) }
    }

    // ── Test 5: Eliminación de modelo con error ──────────────────────────

    @Test
    fun deleteModelError() = runTest {
        coEvery { modelRepository.delete(ModelAsset.PIPER_TTS) } returns Result.failure(Exception("Error"))

        val viewModel = createViewModel()

        viewModel.deleteModel(ModelAsset.PIPER_TTS)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Should have error message", state.errorMessage?.contains("eliminar") == true)
    }

    // ── Test 6: Refresh status actualiza el estado ───────────────────────

    @Test
    fun refreshStatusUpdatesModels() = runTest {
        piperStatus.value = ModelAssetStatus.Ready("/path/to/voice.onnx")

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        val piperModel = state.models.find { it.asset == ModelAsset.PIPER_TTS }

        assertTrue("Piper should be ready", piperModel?.isReady == true)
    }

    // ── Test 7: clearError limpia el error ───────────────────────────────

    @Test
    fun clearErrorRemovesErrorMessage() = runTest {
        coEvery { modelRepository.download(ModelAsset.PIPER_TTS) } throws RuntimeException("Error")

        val viewModel = createViewModel()

        viewModel.downloadModel(ModelAsset.PIPER_TTS)
        testScheduler.advanceUntilIdle()

        assertTrue("Should have error", viewModel.uiState.value.errorMessage != null)

        viewModel.clearError()

        assertNull("Error should be cleared", viewModel.uiState.value.errorMessage)
    }
}
