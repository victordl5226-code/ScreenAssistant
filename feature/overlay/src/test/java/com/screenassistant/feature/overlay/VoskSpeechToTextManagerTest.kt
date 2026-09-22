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
 * VoskSpeechToTextManager — integración con ModelAssetRepository para descarga bajo demanda.
 *
 * Verifica:
 * 1. Modelo listo → inicia recognizer directamente
 * 2. Modelo no listo → descarga primero
 * 3. Descarga falla → STT deshabilitado
 * 4. Shutdown libera recursos
 *
 * NOTA: La librería nativa de Vosk no está disponible en JVM tests.
 * Se usa spyk + override de [VoskSpeechToTextManager.createVoskModel],
 * [VoskSpeechToTextManager.createRecognizer] y
 * [VoskSpeechToTextManager.createSpeechService] (internal open)
 * para evitar UnsatisfiedLinkError.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoskSpeechToTextManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var modelRepository: ModelAssetRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        modelRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Crea un VoskSpeechToTextManager espyado con los componentes nativos mockeados.
     */
    private fun createManager(): VoskSpeechToTextManager {
        val real = VoskSpeechToTextManager(
            context = context,
            onResultCallback = {},
            onPartialResultCallback = {},
            onErrorCallback = {},
            modelRepository = modelRepository,
            dispatcher = testDispatcher
        )
        return spyk(real).apply {
            every { createVoskModel(any()) } returns mockk(relaxed = true)
            every { createRecognizer(any()) } returns mockk(relaxed = true)
            every { createSpeechService(any()) } returns mockk(relaxed = true)
        }
    }

    // ── Test 1: Modelo listo → inicia recognizer ─────────────────────────

    @Test
    fun startListeningModeloListoIniciaRecognizer() = runTest {
        coEvery { modelRepository.isReady(ModelAsset.VOSK_STT) } returns true
        coEvery { modelRepository.getLocalPath(ModelAsset.VOSK_STT) } returns "/tmp/test-vosk/model"

        val manager = createManager()

        manager.startListening()
        testScheduler.advanceUntilIdle()

        // Verificar que NO se intentó descargar (ya estaba listo)
        coVerify(exactly = 0) { modelRepository.download(any()) }

        // Verificar que se obtuvo la ruta local
        coVerify { modelRepository.getLocalPath(ModelAsset.VOSK_STT) }

        // Verificar que se creó el SpeechService (reconocedor listo)
        verify { manager.createSpeechService(any()) }

        manager.destroy()
    }

    // ── Test 2: Modelo no listo → descarga primero ───────────────────────

    @Test
    fun startListeningModeloNoListoDescargaPrimero() = runTest {
        coEvery { modelRepository.isReady(ModelAsset.VOSK_STT) } returns false
        coEvery { modelRepository.getLocalPath(ModelAsset.VOSK_STT) } returns "/tmp/test-vosk/model"

        val manager = createManager()

        manager.startListening()
        testScheduler.advanceUntilIdle()

        // Verificar que SÍ se intentó descargar
        coVerify(exactly = 1) { modelRepository.download(ModelAsset.VOSK_STT) }

        // Verificar que se obtuvo la ruta local después de la descarga
        coVerify { modelRepository.getLocalPath(ModelAsset.VOSK_STT) }

        manager.destroy()
    }

    // ── Test 3: Descarga falla → STT deshabilitado ──────────────────────

    @Test
    fun startListeningDescargaFallaRetornaFalse() = runTest {
        coEvery { modelRepository.isReady(ModelAsset.VOSK_STT) } returns false
        coEvery { modelRepository.download(ModelAsset.VOSK_STT) } throws RuntimeException("Error de red")

        var errorReceived: String? = null
        val manager = VoskSpeechToTextManager(
            context = context,
            onResultCallback = {},
            onPartialResultCallback = {},
            onErrorCallback = { errorReceived = it },
            modelRepository = modelRepository,
            dispatcher = testDispatcher
        )

        manager.startListening()
        testScheduler.advanceUntilIdle()

        // Verificar que se intentó descargar (y falló)
        coVerify(exactly = 1) { modelRepository.download(ModelAsset.VOSK_STT) }

        // Verificar que se notificó el error
        assert(errorReceived != null) { "Se esperaba un error de callback" }

        manager.destroy()
    }

    // ── Test 4: Shutdown libera recursos ──────────────────────────────────

    @Test
    fun shutdownLiberaRecursos() = runTest {
        coEvery { modelRepository.isReady(ModelAsset.VOSK_STT) } returns true
        coEvery { modelRepository.getLocalPath(ModelAsset.VOSK_STT) } returns "/tmp/test-vosk/model"

        val manager = createManager()

        // Forzar carga del modelo
        manager.startListening()
        testScheduler.advanceUntilIdle()

        // Destruir — no debe lanzar excepciones
        manager.destroy()
    }
}
