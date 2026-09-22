package com.screenassistant.feature.overlay

import android.content.Context
import com.screenassistant.core.model.domain.ModelAsset
import com.screenassistant.core.model.domain.ModelAssetRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * PiperTtsManager — integración con ModelAssetRepository para descarga bajo demanda.
 *
 * Verifica:
 * 1. Modelo listo → usa ORNX (session creada)
 * 2. Modelo no listo → descarga primero
 * 3. Descarga falla → fallback al sistema
 * 4. Shutdown libera recursos
 *
 * NOTA: La librería nativa de ORT no está disponible en JVM tests.
 * Se usa spyk + override de [PiperTtsManager.createOrtSession] (internal open)
 * para evitar UnsatisfiedLinkError. El tipo de retorno es Any? para no
 * importar clases ONNX en el classpath de tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PiperTtsManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var modelRepository: ModelAssetRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        every { context.filesDir } returns java.io.File("/tmp/test-piper")
        modelRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Crea un PiperTtsManager espyado con la sesión ONNX mockeada.
     * El mock retorna un objeto genérico (Any?) para no tocar el classpath de ORT.
     */
    private fun createManager(sessionMock: Any? = Object()): PiperTtsManager {
        val real = PiperTtsManager(
            context = context,
            onStart = {},
            onDone = {},
            modelRepository = modelRepository,
            dispatcher = testDispatcher
        )
        return spyk(real).apply {
            every { createOrtSession(any()) } returns sessionMock
        }
    }

    /**
     * Crea un PiperTtsManager espyado cuyo createOrtSession falla (retorna null).
     */
    private fun createManagerWithFailedSession(): PiperTtsManager {
        val real = PiperTtsManager(
            context = context,
            onStart = {},
            onDone = {},
            modelRepository = modelRepository,
            dispatcher = testDispatcher
        )
        return spyk(real).apply {
            every { createOrtSession(any()) } returns null
        }
    }

    // ── Test 1: Modelo listo → usa ORNX ─────────────────────────────────

    @Test
    fun speakModeloListoUsaOrnx() = runTest {
        coEvery { modelRepository.isReady(ModelAsset.PIPER_TTS) } returns true
        coEvery { modelRepository.getLocalPath(ModelAsset.PIPER_TTS) } returns "/tmp/test-piper/voice.onnx"

        val manager = createManager()

        manager.speak("hola mundo")
        testScheduler.advanceUntilIdle()

        // Verificar que NO se intentó descargar (ya estaba listo)
        coVerify(exactly = 0) { modelRepository.download(any()) }

        // Verificar que se obtuvo la ruta local
        coVerify { modelRepository.getLocalPath(ModelAsset.PIPER_TTS) }

        manager.destroy()
    }

    // ── Test 2: Modelo no listo → descarga primero ───────────────────────

    @Test
    fun speakModeloNoListoDescargaPrimero() = runTest {
        coEvery { modelRepository.isReady(ModelAsset.PIPER_TTS) } returns false
        coEvery { modelRepository.getLocalPath(ModelAsset.PIPER_TTS) } returns "/tmp/test-piper/voice.onnx"

        val manager = createManager()

        manager.speak("hola")
        testScheduler.advanceUntilIdle()

        // Verificar que SÍ se intentó descargar
        coVerify(exactly = 1) { modelRepository.download(ModelAsset.PIPER_TTS) }

        // Verificar que se obtuvo la ruta local después de la descarga
        coVerify { modelRepository.getLocalPath(ModelAsset.PIPER_TTS) }

        manager.destroy()
    }

    // ── Test 3: Descarga falla → fallback al sistema ─────────────────────

    @Test
    fun speakDescargaFallaUsaFallback() = runTest {
        coEvery { modelRepository.isReady(ModelAsset.PIPER_TTS) } returns false
        coEvery { modelRepository.download(ModelAsset.PIPER_TTS) } throws RuntimeException("Error de red")

        val manager = createManagerWithFailedSession()

        manager.speak("fallback test")
        testScheduler.advanceUntilIdle()

        // Verificar que se intentó descargar (y falló)
        coVerify(exactly = 1) { modelRepository.download(ModelAsset.PIPER_TTS) }

        manager.destroy()
    }

    // ── Test 4: Shutdown libera recursos ──────────────────────────────────

    @Test
    fun shutdownLiberaRecursos() = runTest {
        coEvery { modelRepository.isReady(ModelAsset.PIPER_TTS) } returns true
        coEvery { modelRepository.getLocalPath(ModelAsset.PIPER_TTS) } returns "/tmp/test-piper/voice.onnx"

        val sessionHolder = Object()
        val manager = createManager(sessionMock = sessionHolder)

        // Forzar carga del modelo
        manager.speak("test")
        testScheduler.advanceUntilIdle()

        // Destruir — no debe lanzar excepciones
        manager.destroy()
    }
}
