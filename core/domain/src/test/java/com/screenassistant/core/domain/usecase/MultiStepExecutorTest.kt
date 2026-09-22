package com.screenassistant.core.domain.usecase

import com.screenassistant.core.domain.action.SystemAction
import com.screenassistant.core.domain.model.ActionResult
import com.screenassistant.core.domain.model.CommandMarkers
import com.screenassistant.core.domain.model.CommandSequence
import com.screenassistant.core.domain.model.CommandStep
import com.screenassistant.core.domain.model.ConnectorType
import com.screenassistant.core.domain.model.MultiStepResult
import com.screenassistant.core.domain.model.StepFeedback
import com.screenassistant.core.domain.model.StepStatus
import com.screenassistant.core.domain.model.SystemCommand
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests de MultiStepExecutorImpl contra la implementación real.
 *
 * Patrón de tiempo virtual (mismo que AutoRemoteUrlCallbackTest): el executor
 * recibe un StandardTestDispatcher que COMPARTe el testScheduler del TestScope,
 * de modo que advanceUntilIdle()/los delays internos avanzan con el reloj virtual.
 * runBlockingTest fue eliminado en coroutines-test 1.9.0 → runTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MultiStepExecutorTest {

    private lateinit var systemAction: SystemAction

    @Before
    fun setup() {
        systemAction = mockk()
    }

    /** Executor cuyo dispatcher comparte el scheduler del TestScope actual. */
    private fun TestScope.nuevoExecutor(maxSteps: Int = 10): MultiStepExecutorImpl =
        MultiStepExecutorImpl(systemAction, StandardTestDispatcher(testScheduler), maxSteps)

    private fun secuencia(nPasos: Int): CommandSequence = CommandSequence(
        steps = List(nPasos) { CommandStep.Command(SystemCommand.SetAlarm(it + 1, 0, null)) },
        originalText = "test",
        connectorType = ConnectorType.Y_LUEGO
    )

    @Test
    fun `execute exito 3 pasos devuelve Success con 3 resultados`() = runTest {
        val executor = nuevoExecutor()
        coEvery { systemAction.execute(any()) } returns ActionResult.Success("OK")

        val result = executor.execute(secuencia(3))

        assertTrue(result is MultiStepResult.Success)
        assertEquals(3, (result as MultiStepResult.Success).stepResults.size)
    }

    @Test
    fun `execute error en paso 2 devuelve Error con completed=1`() = runTest {
        val executor = nuevoExecutor()
        coEvery { systemAction.execute(SystemCommand.SetAlarm(1, 0, null)) } returns ActionResult.Success("OK")
        coEvery { systemAction.execute(SystemCommand.SetAlarm(2, 0, null)) } returns ActionResult.Error("falló")

        val sequence = CommandSequence(
            steps = listOf(
                CommandStep.Command(SystemCommand.SetAlarm(1, 0, null)),
                CommandStep.Command(SystemCommand.SetAlarm(2, 0, null)),
                CommandStep.Command(SystemCommand.SetAlarm(3, 0, null))
            ),
            originalText = "test",
            connectorType = ConnectorType.Y_LUEGO
        )

        val result = executor.execute(sequence)

        assertTrue(result is MultiStepResult.Error)
        val error = result as MultiStepResult.Error
        assertEquals(1, error.failedStepIndex)
        // El impl añade el paso fallido a completedSteps antes de retornar:
        // [paso 1 OK, paso 2 ERROR] → size = 2.
        assertEquals(2, error.completedSteps.size)
        assertTrue(error.completedSteps[0].result == "OK")
        assertTrue(error.completedSteps[1].result.startsWith("Error:"))
    }

    @Test
    fun `cancel tras paso 1 devuelve Cancelled con completed=1`() = runTest {
        // El paso 1 queda bloqueado dentro de systemAction.execute hasta que el
        // test lo libere: permite cancelar DURANTE la ejecución de forma determinista
        // (sin depender de los delays internos del executor).
        val gate = CompletableDeferred<ActionResult>()
        coEvery { systemAction.execute(any()) } coAnswers { gate.await() }
        val executor = nuevoExecutor()

        val resultado = CompletableDeferred<MultiStepResult>()
        // UNDISPATCHED: execute arranca en el acto y queda bloqueada en el gate
        // dentro del paso 1, sin depender del drenaje inicial del scheduler.
        launch(start = CoroutineStart.UNDISPATCHED) {
            resultado.complete(executor.execute(
                CommandSequence(
                    steps = List(3) { CommandStep.Command(SystemCommand.SetAlarm(it + 1, 0, null)) },
                    originalText = "test",
                    connectorType = ConnectorType.Y_LUEGO
                )
            ))
        }
        assertTrue(resultado.isActive)

        // Cancelar mientras el paso 1 está en vuelo y después liberarlo:
        // el paso 1 se completa y añade, pero el loop detecta la cancelación
        // antes del paso 2 → Cancelled con exactamente 1 paso completado.
        executor.cancel()
        gate.complete(ActionResult.Success("OK"))
        testScheduler.advanceUntilIdle()

        val result = resultado.await()
        assertTrue("resultado=$result", result is MultiStepResult.Cancelled)
        assertEquals(1, (result as MultiStepResult.Cancelled).completedSteps.size)
    }

    @Test
    fun `limite 10 pasos exito`() = runTest {
        val executor = nuevoExecutor()
        coEvery { systemAction.execute(any()) } returns ActionResult.Success("OK")

        val result = executor.execute(secuencia(10))

        assertTrue(result is MultiStepResult.Success)
        assertEquals(10, (result as MultiStepResult.Success).stepResults.size)
    }

    @Test
    fun `11 pasos devuelve Error inmediato`() = runTest {
        val executor = nuevoExecutor()

        val result = executor.execute(secuencia(11))

        assertTrue(result is MultiStepResult.Error)
        val error = result as MultiStepResult.Error
        assertEquals(0, error.failedStepIndex)
        assertTrue(error.reason.contains("10 pasos"))
    }

    @Test
    fun `CommandMarker en medio se ejecuta sin llamar systemAction`() = runTest {
        val executor = nuevoExecutor()
        coEvery { systemAction.execute(any()) } returns ActionResult.Success("OK")

        val sequence = CommandSequence(
            steps = listOf(
                CommandStep.Command(SystemCommand.SetAlarm(1, 0, null)),
                CommandStep.Marker(CommandMarkers.HELP),
                CommandStep.Command(SystemCommand.SetAlarm(2, 0, null))
            ),
            originalText = "test",
            connectorType = ConnectorType.Y_LUEGO
        )

        val result = executor.execute(sequence)

        assertTrue(result is MultiStepResult.Success)
        assertEquals(3, (result as MultiStepResult.Success).stepResults.size)
        // Los marcadores NO pasan por SystemAction
        coVerify(exactly = 2) { systemAction.execute(any()) }
    }

    @Test
    fun `feedback StateFlow emite STARTING EXECUTING COMPLETED por paso`() = runTest {
        val executor = nuevoExecutor()
        coEvery { systemAction.execute(any()) } returns ActionResult.Success("OK")

        val sequence = CommandSequence(
            steps = listOf(
                CommandStep.Command(SystemCommand.SetAlarm(1, 0, null)),
                CommandStep.Command(SystemCommand.SetAlarm(2, 0, null))
            ),
            originalText = "test",
            connectorType = ConnectorType.Y_LUEGO
        )

        val feedbacks = mutableListOf<StepFeedback>()
        backgroundScope.launch {
            executor.stepFeedback.collect { fb ->
                fb?.let { feedbacks.add(it) }
            }
        }

        executor.execute(sequence)
        testScheduler.advanceUntilIdle()

        // Debe haber al menos STARTING, EXECUTING, COMPLETED para cada paso
        assertTrue(feedbacks.size >= 6) // 3 estados × 2 pasos
        assertTrue(feedbacks.any { it.status == StepStatus.STARTING })
        assertTrue(feedbacks.any { it.status == StepStatus.EXECUTING })
        assertTrue(feedbacks.any { it.status == StepStatus.COMPLETED })
    }

    @Test
    fun `paso devuelve Error string devuelve Error en MultiStepResult`() = runTest {
        val executor = nuevoExecutor()
        coEvery { systemAction.execute(any()) } returns ActionResult.Error("algo falló")

        val result = executor.execute(secuencia(1))

        assertTrue(result is MultiStepResult.Error)
        val error = result as MultiStepResult.Error
        assertTrue(error.reason.startsWith("Error:"))
    }

    @Test
    fun `excepcion en SystemAction devuelve Error`() = runTest {
        val executor = nuevoExecutor()
        coEvery { systemAction.execute(any()) } throws RuntimeException("boom")

        val result = executor.execute(secuencia(1))

        assertTrue(result is MultiStepResult.Error)
        val error = result as MultiStepResult.Error
        assertTrue(error.reason.contains("Error inesperado"))
    }

    @Test
    fun `secuencia vacía devuelve Success vacío`() = runTest {
        val executor = nuevoExecutor()

        val result = executor.execute(
            CommandSequence(
                steps = emptyList(),
                originalText = "",
                connectorType = ConnectorType.Y_LUEGO
            )
        )

        assertTrue(result is MultiStepResult.Success)
        assertEquals(0, (result as MultiStepResult.Success).stepResults.size)
    }

    @Test
    fun `un solo paso devuelve Success compatibilidad legacy`() = runTest {
        val executor = nuevoExecutor()
        coEvery { systemAction.execute(any()) } returns ActionResult.Success("OK")

        val result = executor.execute(secuencia(1))

        assertTrue(result is MultiStepResult.Success)
        assertEquals(1, (result as MultiStepResult.Success).stepResults.size)
    }

    @Test
    fun `solo Markers devuelve Success sin llamar systemAction`() = runTest {
        val executor = nuevoExecutor()

        val result = executor.execute(
            CommandSequence(
                steps = listOf(
                    CommandStep.Marker(CommandMarkers.HELP),
                    CommandStep.Marker(CommandMarkers.REPEAT)
                ),
                originalText = "test",
                connectorType = ConnectorType.Y_LUEGO
            )
        )

        assertTrue(result is MultiStepResult.Success)
        assertEquals(2, (result as MultiStepResult.Success).stepResults.size)
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `maxSteps configurable inyectar 5 probar 6 pasos devuelve Error`() = runTest {
        val limitedExecutor = nuevoExecutor(maxSteps = 5)

        val result = limitedExecutor.execute(secuencia(6))

        assertTrue(result is MultiStepResult.Error)
        val error = result as MultiStepResult.Error
        assertEquals(0, error.failedStepIndex)
        assertTrue(error.reason.contains("5 pasos"))
    }
}
