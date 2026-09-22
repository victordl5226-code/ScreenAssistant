package com.screenassistant.core.model.data

import com.screenassistant.core.model.domain.ModelAsset
import com.screenassistant.core.model.domain.ModelAssetStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ModelAssetRepositoryImplTest {

    private lateinit var fileStore: ModelFileStore
    private lateinit var downloader: GitHubModelDownloader
    private lateinit var repository: ModelAssetRepositoryImpl

    private val tempDir = File(System.getProperty("java.io.tmpdir"), "model_test_${System.nanoTime()}")

    @Before
    fun setup() {
        tempDir.mkdirs()
        fileStore = mockk(relaxed = true)
        downloader = mockk(relaxed = true)
        repository = ModelAssetRepositoryImpl(fileStore, downloader)
    }

    @Test
    fun `observeStatus returns NotDownloaded when model not ready`() {
        every { fileStore.isModelReady(ModelAsset.PIPER_TTS) } returns false

        val status = repository.observeStatus(ModelAsset.PIPER_TTS)

        assertTrue(status.value is ModelAssetStatus.NotDownloaded)
    }

    @Test
    fun `observeStatus returns Ready when model is ready`() {
        val modelFile = File(tempDir, "voice.onnx")
        every { fileStore.isModelReady(ModelAsset.PIPER_TTS) } returns true
        every { fileStore.getModelFile(ModelAsset.PIPER_TTS) } returns modelFile

        val status = repository.observeStatus(ModelAsset.PIPER_TTS)

        assertTrue(status.value is ModelAssetStatus.Ready)
        assertEquals(modelFile.absolutePath, (status.value as ModelAssetStatus.Ready).localPath)
    }

    @Test
    fun `isReady delegates to fileStore`() = runTest {
        every { fileStore.isModelReady(ModelAsset.VOSK_STT) } returns true
        assertTrue(repository.isReady(ModelAsset.VOSK_STT))

        every { fileStore.isModelReady(ModelAsset.VOSK_STT) } returns false
        assertFalse(repository.isReady(ModelAsset.VOSK_STT))
    }

    @Test
    fun `download succeeds and updates status to Ready`() = runTest {
        val targetFile = File(tempDir, "voice.onnx")
        every { fileStore.getModelFile(ModelAsset.PIPER_TTS) } returns targetFile
        coEvery {
            downloader.download(
                url = any(),
                targetFile = any(),
                expectedSha256 = any(),
                onProgress = any()
            )
        } returns Result.success(Unit)

        repository.download(ModelAsset.PIPER_TTS)

        val status = repository.observeStatus(ModelAsset.PIPER_TTS)
        assertTrue(status.value is ModelAssetStatus.Ready)
    }

    @Test
    fun `download fails and updates status to Failed`() = runTest {
        val targetFile = File(tempDir, "voice.onnx")
        val expectedError = Exception("Network error")
        every { fileStore.getModelFile(ModelAsset.PIPER_TTS) } returns targetFile
        coEvery {
            downloader.download(
                url = any(),
                targetFile = any(),
                expectedSha256 = any(),
                onProgress = any()
            )
        } returns Result.failure(expectedError)

        repository.download(ModelAsset.PIPER_TTS)

        val status = repository.observeStatus(ModelAsset.PIPER_TTS)
        assertTrue(status.value is ModelAssetStatus.Failed)
        assertEquals(expectedError, (status.value as ModelAssetStatus.Failed).error)
    }

    @Test
    fun `download skips when already Ready`() = runTest {
        val modelFile = File(tempDir, "voice.onnx")
        every { fileStore.isModelReady(ModelAsset.PIPER_TTS) } returns true
        every { fileStore.getModelFile(ModelAsset.PIPER_TTS) } returns modelFile

        // First call initializes status as Ready
        repository.observeStatus(ModelAsset.PIPER_TTS)
        repository.download(ModelAsset.PIPER_TTS)

        coVerify(exactly = 0) {
            downloader.download(any(), any(), any(), any())
        }
    }

    @Test
    fun `download reports progress via onProgress callback`() = runTest {
        val targetFile = File(tempDir, "voice.onnx")
        val progressSlot = slot<(Float) -> Unit>()
        every { fileStore.getModelFile(ModelAsset.PIPER_TTS) } returns targetFile
        coEvery {
            downloader.download(
                url = any(),
                targetFile = any(),
                expectedSha256 = any(),
                onProgress = capture(progressSlot)
            )
        } answers {
            progressSlot.captured(0.5f)
            Result.success(Unit)
        }

        repository.download(ModelAsset.PIPER_TTS)

        coVerify {
            downloader.download(
                url = ModelAsset.PIPER_TTS.downloadUrl,
                targetFile = targetFile,
                expectedSha256 = ModelAsset.PIPER_TTS.sha256,
                onProgress = any()
            )
        }
    }

    @Test
    fun `delete removes model and resets status`() = runTest {
        every { fileStore.deleteModel(ModelAsset.PIPER_TTS) } returns true

        // Initialize status first
        every { fileStore.isModelReady(ModelAsset.PIPER_TTS) } returns false
        repository.observeStatus(ModelAsset.PIPER_TTS)

        val result = repository.delete(ModelAsset.PIPER_TTS)

        assertTrue(result.isSuccess)
        assertEquals(ModelAssetStatus.NotDownloaded, repository.observeStatus(ModelAsset.PIPER_TTS).value)
    }

    @Test
    fun `delete returns failure when fileStore fails`() = runTest {
        every { fileStore.deleteModel(ModelAsset.PIPER_TTS) } returns false

        val result = repository.delete(ModelAsset.PIPER_TTS)

        assertTrue(result.isFailure)
    }

    @Test
    fun `getLocalPath returns path when ready`() = runTest {
        val modelFile = File(tempDir, "voice.onnx")
        every { fileStore.isModelReady(ModelAsset.PIPER_TTS) } returns true
        every { fileStore.getModelFile(ModelAsset.PIPER_TTS) } returns modelFile

        val path = repository.getLocalPath(ModelAsset.PIPER_TTS)

        assertEquals(modelFile.absolutePath, path)
    }

    @Test
    fun `getLocalPath returns null when not ready`() = runTest {
        every { fileStore.isModelReady(ModelAsset.PIPER_TTS) } returns false

        val path = repository.getLocalPath(ModelAsset.PIPER_TTS)

        assertEquals(null, path)
    }

    @Test
    fun `availableModels returns all enum entries`() {
        val models = repository.availableModels()

        assertEquals(3, models.size)
        assertTrue(models.contains(ModelAsset.PIPER_TTS))
        assertTrue(models.contains(ModelAsset.VOSK_STT))
        assertTrue(models.contains(ModelAsset.MINILM_EMBEDDINGS))
    }

    @Test
    fun `getModelSizeBytes returns manifest size`() = runTest {
        val size = repository.getModelSizeBytes(ModelAsset.PIPER_TTS)
        assertEquals(60_270_000L, size)
    }

    @Test
    fun `getRequiredSpaceBytes returns 1_2x manifest size`() = runTest {
        val required = repository.getRequiredSpaceBytes(ModelAsset.PIPER_TTS)
        assertEquals((60_270_000L * 1.2).toLong(), required)
    }
}
