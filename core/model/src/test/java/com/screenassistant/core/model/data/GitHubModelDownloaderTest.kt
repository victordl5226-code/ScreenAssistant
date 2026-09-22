package com.screenassistant.core.model.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class GitHubModelDownloaderTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var downloader: GitHubModelDownloader

    private val tempDir = File(System.getProperty("java.io.tmpdir"), "downloader_test_${System.nanoTime()}")

    @Before
    fun setup() {
        tempDir.mkdirs()
        mockWebServer = MockWebServer()
        mockWebServer.start()
        downloader = GitHubModelDownloader(okhttp3.OkHttpClient())
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        tempDir.deleteRecursively()
    }

    @Test
    fun `download succeeds with valid SHA-256`() = runTest {
        val content = "test model content".toByteArray()
        val sha256 = sha256(content)
        val targetFile = File(tempDir, "model.bin")

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(content))
                .addHeader("Content-Length", content.size.toString())
        )

        val result = downloader.download(
            url = mockWebServer.url("/model.bin").toString(),
            targetFile = targetFile,
            expectedSha256 = sha256,
            onProgress = {}
        )

        assertTrue(result.isSuccess)
        assertTrue(targetFile.exists())
        assertEquals(String(content), targetFile.readText())
    }

    @Test
    fun `download fails with SHA-256 mismatch`() = runTest {
        val content = "test model content".toByteArray()
        val targetFile = File(tempDir, "model.bin")

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(content))
                .addHeader("Content-Length", content.size.toString())
        )

        val result = downloader.download(
            url = mockWebServer.url("/model.bin").toString(),
            targetFile = targetFile,
            expectedSha256 = "0000000000000000000000000000000000000000000000000000000000000000",
            onProgress = {}
        )

        assertTrue(result.isFailure)
        assertEquals("SHA-256 mismatch", result.exceptionOrNull()?.message)
        assertFalse(targetFile.exists())
    }

    @Test
    fun `download fails with HTTP error`() = runTest {
        val targetFile = File(tempDir, "model.bin")

        mockWebServer.enqueue(
            MockResponse().setResponseCode(404)
        )

        val result = downloader.download(
            url = mockWebServer.url("/model.bin").toString(),
            targetFile = targetFile,
            expectedSha256 = "anything",
            onProgress = {}
        )

        assertTrue(result.isFailure)
        assertEquals("HTTP 404", result.exceptionOrNull()?.message)
    }

    @Test
    fun `download reports progress correctly`() = runTest {
        val content = ByteArray(16384) { it.toByte() } // 16KB
        val sha256 = sha256(content)
        val targetFile = File(tempDir, "model.bin")
        val progressValues = mutableListOf<Float>()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(content))
                .addHeader("Content-Length", content.size.toString())
        )

        val result = downloader.download(
            url = mockWebServer.url("/model.bin").toString(),
            targetFile = targetFile,
            expectedSha256 = sha256,
            onProgress = { progressValues.add(it) }
        )

        assertTrue(result.isSuccess)
        assertTrue(progressValues.isNotEmpty())
        assertTrue(progressValues.last() > 0f)
    }

    @Test
    fun `download cleans up temp file on SHA-256 failure`() = runTest {
        val content = "test".toByteArray()
        val targetFile = File(tempDir, "model.bin")

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(content))
                .addHeader("Content-Length", content.size.toString())
        )

        downloader.download(
            url = mockWebServer.url("/model.bin").toString(),
            targetFile = targetFile,
            expectedSha256 = "wrong_hash",
            onProgress = {}
        )

        // Temp file should be cleaned up
        val tempFile = File(tempDir, "model.bin.tmp")
        assertFalse(tempFile.exists())
    }

    @Test
    fun `download creates parent directories if needed`() = runTest {
        val content = "test".toByteArray()
        val sha256 = sha256(content)
        val targetFile = File(tempDir, "subdir/deep/model.bin")

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(content))
                .addHeader("Content-Length", content.size.toString())
        )

        val result = downloader.download(
            url = mockWebServer.url("/model.bin").toString(),
            targetFile = targetFile,
            expectedSha256 = sha256,
            onProgress = {}
        )

        assertTrue(result.isSuccess)
        assertTrue(targetFile.exists())
    }

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }
}
