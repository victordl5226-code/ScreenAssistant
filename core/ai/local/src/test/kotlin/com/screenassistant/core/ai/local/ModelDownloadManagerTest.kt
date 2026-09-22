package com.screenassistant.core.ai.local

import android.content.Context
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ModelDownloadManagerTest {

    private lateinit var context: Context
    private lateinit var manager: ModelDownloadManager
    private lateinit var tempDir: File

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        tempDir = File(System.getProperty("java.io.tmpdir"), "mlc_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        context = mockk(relaxed = true)
        every { context.filesDir } returns tempDir

        manager = ModelDownloadManager(context)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        unmockkStatic(Log::class)
    }

    @Test
    fun `getLocalModelPath returns null when model not downloaded`() {
        assertNull(manager.getLocalModelPath("nonexistent-model"))
    }

    @Test
    fun `isModelDownloaded returns false when not downloaded`() {
        assertFalse(manager.isModelDownloaded("nonexistent-model"))
    }

    @Test
    fun `deleteModel returns true when model does not exist`() {
        assertTrue(manager.deleteModel("nonexistent-model"))
    }

    @Test
    fun `deleteModel returns true after successful deletion`() {
        val modelId = "test-model"
        val modelDir = File(tempDir, "mlc_models/$modelId")
        modelDir.mkdirs()
        File(modelDir, "model.bin").writeBytes(byteArrayOf(1, 2, 3))

        assertTrue(manager.deleteModel(modelId))
        assertFalse(modelDir.exists())
    }

    @Test
    fun `formatBytes formats B correctly`() {
        assertEquals("500 B", manager.formatBytes(500))
    }

    @Test
    fun `formatBytes formats KB correctly`() {
        assertEquals("1 KB", manager.formatBytes(1024))
    }

    @Test
    fun `formatBytes formats MB correctly`() {
        val result = manager.formatBytes(1024 * 1024)
        assertTrue(result.endsWith("MB"))
    }

    @Test
    fun `formatBytes formats GB correctly`() {
        val result = manager.formatBytes(1024L * 1024 * 1024)
        assertTrue(result.endsWith("GB"))
    }

    @Test
    fun `getModelState returns NOT_DOWNLOADED when no local path`() {
        val modelInfo = com.screenassistant.core.domain.model.MlcModelInfo.DEFAULT
        val state = manager.getModelState(modelInfo)
        assertEquals(com.screenassistant.core.domain.model.ModelState.NOT_DOWNLOADED, state)
    }

    @Test
    fun `getModelState preserves DOWNLOADING state`() {
        val modelInfo = com.screenassistant.core.domain.model.MlcModelInfo.DEFAULT.copy(
            state = com.screenassistant.core.domain.model.ModelState.DOWNLOADING,
        )
        val state = manager.getModelState(modelInfo)
        assertEquals(com.screenassistant.core.domain.model.ModelState.DOWNLOADING, state)
    }

    @Test
    fun `getModelState preserves LOADING state`() {
        val modelInfo = com.screenassistant.core.domain.model.MlcModelInfo.DEFAULT.copy(
            state = com.screenassistant.core.domain.model.ModelState.LOADING,
        )
        val state = manager.getModelState(modelInfo)
        assertEquals(com.screenassistant.core.domain.model.ModelState.LOADING, state)
    }

    @Test
    fun `getModelState preserves READY state`() {
        val modelInfo = com.screenassistant.core.domain.model.MlcModelInfo.DEFAULT.copy(
            state = com.screenassistant.core.domain.model.ModelState.READY,
        )
        val state = manager.getModelState(modelInfo)
        assertEquals(com.screenassistant.core.domain.model.ModelState.READY, state)
    }
}
