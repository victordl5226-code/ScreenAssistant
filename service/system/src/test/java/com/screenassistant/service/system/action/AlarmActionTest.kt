package com.screenassistant.service.system.action

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.screenassistant.core.data.local.ActiveAlarmStore
import com.screenassistant.core.data.local.AlarmEntity
import io.mockk.EqMatcher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import io.mockk.verify
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * AlarmAction O4-P2 con MockK puro (sin Robolectric):
 *  - FakeAlarmScheduler (implementa AlarmScheduler, registra calls, flag canSchedule)
 *  - store mockeado (MockK) y notifier mockeado
 *  - mockkStatic(PendingIntent::class) + mockkConstructor(Intent::class) para
 *    PendingIntent.getBroadcast: el verify EXPLÍCITO de getBroadcast con
 *    requestCode/action evita verdes falsos (isReturnDefaultValues devolvería
 *    null y el fake no registraría nada verificable)
 *  - reloj inyectable (now = { nowCal }) para decidir hoy/mañana
 */
class AlarmActionTest {

    private class FakeAlarmScheduler(
        var canSchedule: Boolean = true,
        // M5: simula el rechazo del sistema en la PRIMERA reprogramación.
        private val throwOnFirstSetExact: Boolean = false
    ) : AlarmScheduler {
        data class SetExactCall(val triggerAtMillis: Long, val pendingIntent: PendingIntent)

        val setExactCalls = mutableListOf<SetExactCall>()
        val cancelCalls = mutableListOf<PendingIntent>()
        private var setExactAttempts = 0

        override fun canScheduleExactAlarms(): Boolean = canSchedule

        override fun setExactAndAllowWhileIdle(triggerAtMillis: Long, pendingIntent: PendingIntent) {
            // Contador de INTENTOS (no setExactCalls: la llamada que lanza no registra).
            setExactAttempts++
            if (throwOnFirstSetExact && setExactAttempts == 1) throw RuntimeException("boom")
            setExactCalls.add(SetExactCall(triggerAtMillis, pendingIntent))
        }

        override fun cancel(pendingIntent: PendingIntent) {
            cancelCalls.add(pendingIntent)
        }
    }

    private lateinit var context: Context
    private lateinit var store: ActiveAlarmStore
    private lateinit var notifier: AlarmNotificationHelper
    private lateinit var scheduler: FakeAlarmScheduler
    private lateinit var piMock: PendingIntent
    private lateinit var alarmAction: AlarmAction
    private val broadcastIntentSlot = io.mockk.slot<Intent>()
    private val extraKeySlot = io.mockk.slot<String>()
    private val extraValueSlot = io.mockk.slot<Int>()
    private var originalTimeZone: TimeZone? = null

    private var nowCal: Calendar = Calendar.getInstance()

    /** Reloj fijo determinista (evita DST/zonas horarias del entorno). */
    private fun fixedCalendar(y: Int, mo: Int, d: Int, h: Int, mi: Int): Calendar =
        Calendar.getInstance().apply {
            set(y, mo, d, h, mi, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun alarmEntity(
        requestCode: Int,
        hour: Int,
        minute: Int,
        trigger: Long = 0L,
        label: String? = null
    ): AlarmEntity {
        val pretty = "$hour:${minute.toString().padStart(2, '0')}"
        return AlarmEntity(
            requestCode = requestCode,
            hour = hour,
            minute = minute,
            label = label ?: "Alarma de las $pretty",
            triggerAtMillis = trigger,
            createdAt = trigger
        )
    }

    @Before
    fun setup() {
        // El test de DST cambia la zona horaria por defecto; se guarda aquí para
        // restaurarla SIEMPRE en tearDown (aunque un test falle a mitad).
        originalTimeZone = TimeZone.getDefault()
        context = mockk(relaxed = true)
        store = mockk()
        notifier = mockk(relaxed = true)
        scheduler = FakeAlarmScheduler()
        piMock = mockk()

        nowCal = fixedCalendar(2026, Calendar.JANUARY, 15, 6, 0)

        // PendingIntent.getBroadcast estático: todas las llamadas devuelven piMock
        // (el fake registrará esa misma instancia → assertSame en los tests).
        // El Intent se captura en un slot para verificar action/package FUERA del
        // verify (limite de MockK: llamar getters de un mock de constructor dentro
        // de un match {} lanza stdObjectAnswer).
        mockkStatic(PendingIntent::class)
        every { PendingIntent.getBroadcast(any(), any(), capture(broadcastIntentSlot), any()) } returns piMock

        // Prototipos del constructor Intent(ACTION_ALARM_FIRED): getters
        // reflejados para poder verificar action/package sobre el intent capturado.
        mockkConstructor(Intent::class)
        every {
            constructedWith<Intent>(EqMatcher(AlarmAction.ACTION_ALARM_FIRED)).action
        } returns AlarmAction.ACTION_ALARM_FIRED
        every {
            constructedWith<Intent>(EqMatcher(AlarmAction.ACTION_ALARM_FIRED)).getPackage()
        } returns context.packageName
        every {
            constructedWith<Intent>(EqMatcher(AlarmAction.ACTION_ALARM_FIRED)).setPackage(any())
        } returns mockk()
        every {
            constructedWith<Intent>(EqMatcher(AlarmAction.ACTION_ALARM_FIRED)).putExtra(
                capture(extraKeySlot), capture(extraValueSlot)
            )
        } returns mockk()

        alarmAction = AlarmAction(
            context = context,
            store = store,
            notifier = notifier,
            alarmSchedulerProvider = { scheduler },
            now = { nowCal }
        )
    }

    @After
    fun tearDown() {
        originalTimeZone?.let { TimeZone.setDefault(it) }
        unmockkConstructor(Intent::class)
        unmockkStatic(PendingIntent::class)
    }

    // ===== setAlarm =====

    @Test
    fun `setAlarm con hora futura hoy programa el trigger de hoy y guarda`() = runTest {
        coEvery { store.upsertAlarm(any()) } returns Unit

        val result = alarmAction.setAlarm(7, 30, null)

        assertEquals("Éxito: Alarma configurada para las 7:30.", result)
        val expectedTrigger = fixedCalendar(2026, Calendar.JANUARY, 15, 7, 30).timeInMillis
        assertEquals(listOf(expectedTrigger), scheduler.setExactCalls.map { it.triggerAtMillis })
        coVerify(exactly = 1) {
            store.upsertAlarm(
                match {
                    it.requestCode == 450 && it.hour == 7 && it.minute == 30 &&
                        it.triggerAtMillis == expectedTrigger
                }
            )
        }
    }

    @Test
    fun `setAlarm con hora ya pasada programa para manana`() = runTest {
        coEvery { store.upsertAlarm(any()) } returns Unit
        nowCal = fixedCalendar(2026, Calendar.JANUARY, 15, 8, 0)

        val result = alarmAction.setAlarm(7, 30, null)

        assertEquals("Éxito: Alarma configurada para las 7:30.", result)
        val expectedTrigger = fixedCalendar(2026, Calendar.JANUARY, 16, 7, 30).timeInMillis
        assertEquals(listOf(expectedTrigger), scheduler.setExactCalls.map { it.triggerAtMillis })
    }

    @Test
    fun `setAlarm sin permiso de alarmas exactas devuelve error y no programa`() = runTest {
        scheduler.canSchedule = false

        val result = alarmAction.setAlarm(7, 30, null)

        assertEquals(
            "Error: La app no puede programar alarmas exactas. Activa 'Alarmas y recordatorios' en los ajustes del sistema.",
            result
        )
        assertTrue(scheduler.setExactCalls.isEmpty())
        coVerify(exactly = 0) { store.upsertAlarm(any()) }
    }

    @Test
    fun `setAlarm formatea los minutos con padStart`() = runTest {
        coEvery { store.upsertAlarm(any()) } returns Unit

        val result = alarmAction.setAlarm(19, 5, null)

        assertEquals("Éxito: Alarma configurada para las 19:05.", result)
        coVerify(exactly = 1) { store.upsertAlarm(match { it.requestCode == 19 * 60 + 5 }) }
    }

    // ===== B2: guard de rango (último filtro antes de programar) =====

    @Test
    fun `setAlarm con hora fuera de rango devuelve error y no programa`() = runTest {
        val result = alarmAction.setAlarm(24, 0, null)

        assertEquals("Error: Hora (0-23) o minuto (0-59) inválidos.", result)
        assertTrue(scheduler.setExactCalls.isEmpty())
        coVerify(exactly = 0) { store.upsertAlarm(any()) }
    }

    @Test
    fun `setAlarm con minuto fuera de rango devuelve error y no programa`() = runTest {
        val result = alarmAction.setAlarm(7, 60, null)

        assertEquals("Error: Hora (0-23) o minuto (0-59) inválidos.", result)
        assertTrue(scheduler.setExactCalls.isEmpty())
        coVerify(exactly = 0) { store.upsertAlarm(any()) }
    }

    // ===== cancelAlarm =====

    @Test
    fun `cancelAlarm con alarma existente la cancela y la elimina`() = runTest {
        coEvery { store.findAlarm(450) } returns alarmEntity(450, 7, 30)
        coEvery { store.removeAlarm(any()) } returns Unit

        val result = alarmAction.cancelAlarm(7, 30)

        assertEquals("Éxito: Alarma de las 7:30 cancelada.", result)
        assertSame(piMock, scheduler.cancelCalls.single())
        coVerify(exactly = 1) { store.removeAlarm(450) }
    }

    @Test
    fun `cancelAlarm sin alarma existente devuelve error y no cancela nada`() = runTest {
        coEvery { store.findAlarm(450) } returns null

        val result = alarmAction.cancelAlarm(7, 30)

        assertEquals("Error: No encontré ninguna alarma a las 7:30.", result)
        assertTrue(scheduler.cancelCalls.isEmpty())
        coVerify(exactly = 0) { store.removeAlarm(any()) }
    }

    @Test
    fun `cancelAlarm sin hora cancela todas las alarmas y vacia el store`() = runTest {
        coEvery { store.allAlarms() } returns listOf(
            alarmEntity(450, 7, 30),
            alarmEntity(1200, 20, 0)
        )
        coEvery { store.clearAll() } returns Unit

        val result = alarmAction.cancelAlarm(null, null)

        assertEquals("Éxito: He cancelado todas las alarmas.", result)
        assertEquals(2, scheduler.cancelCalls.size)
        assertTrue(scheduler.cancelCalls.all { it === piMock })
        coVerify(exactly = 1) { store.clearAll() }
        coVerify(exactly = 0) { store.removeAlarm(any()) }
    }

    @Test
    fun `cancelAlarm sin hora y sin alarmas devuelve error`() = runTest {
        coEvery { store.allAlarms() } returns emptyList()

        val result = alarmAction.cancelAlarm(null, null)

        assertEquals("Error: No hay alarmas que cancelar.", result)
        assertTrue(scheduler.cancelCalls.isEmpty())
    }

    // ===== onAlarmFired =====

    @Test
    fun `onAlarmFired con alarma muestra la notificacion y la elimina`() = runTest {
        coEvery { store.findAlarm(450) } returns alarmEntity(450, 7, 30)
        coEvery { store.removeAlarm(any()) } returns Unit

        alarmAction.onAlarmFired(450)

        coVerify(exactly = 1) { notifier.showAlarm("Alarma de las 7:30", 450) }
        coVerify(exactly = 1) { store.removeAlarm(450) }
    }

    @Test
    fun `onAlarmFired sin alarma no hace nada y no lanza`() = runTest {
        coEvery { store.findAlarm(450) } returns null

        alarmAction.onAlarmFired(450)

        coVerify(exactly = 0) { notifier.showAlarm(any(), any()) }
        coVerify(exactly = 0) { store.removeAlarm(any()) }
    }

    // ===== restoreActiveAlarms =====

    @Test
    fun `restoreActiveAlarms reprograma las futuras con nextTrigger y purga las pasadas`() = runTest {
        val future = alarmEntity(
            450, 7, 30,
            trigger = fixedCalendar(2026, Calendar.JANUARY, 15, 7, 30).timeInMillis
        )
        val past = alarmEntity(
            300, 5, 0,
            trigger = fixedCalendar(2026, Calendar.JANUARY, 15, 5, 0).timeInMillis
        )
        coEvery { store.allAlarms() } returns listOf(future, past)
        coEvery { store.upsertAlarm(any()) } returns Unit
        coEvery { store.removeAlarm(any()) } returns Unit

        alarmAction.restoreActiveAlarms()

        // Recomputed, NUNCA el triggerAtMillis almacenado
        val expectedTrigger = fixedCalendar(2026, Calendar.JANUARY, 15, 7, 30).timeInMillis
        assertEquals(listOf(expectedTrigger), scheduler.setExactCalls.map { it.triggerAtMillis })
        coVerify(exactly = 1) { store.upsertAlarm(match { it.requestCode == 450 && it.triggerAtMillis == expectedTrigger }) }
        coVerify(exactly = 1) { store.removeAlarm(300) }
    }

    @Test
    fun `restoreActiveAlarms usa la fabrica unica de PendingIntent`() = runTest {
        val future = alarmEntity(
            450, 7, 30,
            trigger = fixedCalendar(2026, Calendar.JANUARY, 15, 7, 30).timeInMillis
        )
        coEvery { store.allAlarms() } returns listOf(future)
        coEvery { store.upsertAlarm(any()) } returns Unit

        alarmAction.restoreActiveAlarms()

        // verify EXPLÍCITO de getBroadcast (evita verde falso por isReturnDefaultValues):
        // mismo requestCode y action de la fábrica única.
        verify(exactly = 1) {
            PendingIntent.getBroadcast(eq(context), eq(450), any(), any())
        }
        assertEquals(AlarmAction.ACTION_ALARM_FIRED, broadcastIntentSlot.captured.action)
        assertSame(piMock, scheduler.setExactCalls.single().pendingIntent)
    }

    @Test
    fun `restoreActiveAlarms aísla por fila si una reprogramacion falla`() = runTest {
        // M5: si el sistema rechaza UNA fila (SecurityException/IAE), el resto se
        // sigue reprogramando — antes la excepción abortaba el forEach completo.
        val futureA = alarmEntity(
            450, 7, 30,
            trigger = fixedCalendar(2026, Calendar.JANUARY, 15, 7, 30).timeInMillis
        )
        val futureB = alarmEntity(
            300, 8, 0,
            trigger = fixedCalendar(2026, Calendar.JANUARY, 15, 8, 0).timeInMillis
        )
        coEvery { store.allAlarms() } returns listOf(futureA, futureB)
        coEvery { store.upsertAlarm(any()) } returns Unit
        coEvery { store.removeAlarm(any()) } returns Unit

        // Scheduler que falla SOLO en la primera reprogramación (fila A).
        val flaky = FakeAlarmScheduler(throwOnFirstSetExact = true)
        val flakyAlarmAction = AlarmAction(
            context = context,
            store = store,
            notifier = notifier,
            alarmSchedulerProvider = { flaky },
            now = { nowCal }
        )

        flakyAlarmAction.restoreActiveAlarms()

        // La fila A NO se persiste (TOCTOU: setExact falló → sin upsert)...
        coVerify(exactly = 0) { store.upsertAlarm(match { it.requestCode == 450 }) }
        // ...pero la fila B SÍ se reprograma y persiste (aislamiento M5).
        coVerify(exactly = 1) { store.upsertAlarm(match { it.requestCode == 300 }) }
        val expectedB = fixedCalendar(2026, Calendar.JANUARY, 15, 8, 0).timeInMillis
        assertEquals(listOf(expectedB), flaky.setExactCalls.map { it.triggerAtMillis })
    }

    // ===== Fábrica única de PendingIntent (QA #1) =====

    @Test
    fun `setAlarm construye el PI con action ALARM_FIRED package de la app y el extra del requestCode`() = runTest {
        coEvery { store.upsertAlarm(any()) } returns Unit

        alarmAction.setAlarm(7, 30, null)

        verify(exactly = 1) {
            PendingIntent.getBroadcast(eq(context), eq(450), any(), any())
        }
        assertEquals(AlarmAction.ACTION_ALARM_FIRED, broadcastIntentSlot.captured.action)
        assertEquals(context.packageName, broadcastIntentSlot.captured.getPackage())
        // Extra EXTRA_REQUEST_CODE: lo que lee AlarmReceiver al dispararse.
        assertEquals(AlarmAction.EXTRA_REQUEST_CODE, extraKeySlot.captured)
        assertEquals(450, extraValueSlot.captured)
        assertSame(piMock, scheduler.setExactCalls.single().pendingIntent)
    }

    @Test
    fun `cancelAlarm usa el mismo PI de la fabrica que setAlarm`() = runTest {
        coEvery { store.findAlarm(450) } returns alarmEntity(450, 7, 30)
        coEvery { store.removeAlarm(any()) } returns Unit
        coEvery { store.upsertAlarm(any()) } returns Unit

        alarmAction.setAlarm(7, 30, null)
        alarmAction.cancelAlarm(7, 30)

        // Mismo requestCode → mismo PendingIntent (filterEquals): setExact y cancel
        // reciben la MISMA instancia de la fábrica única.
        verify(exactly = 2) {
            PendingIntent.getBroadcast(eq(context), eq(450), any(), any())
        }
        assertSame(scheduler.setExactCalls.single().pendingIntent, scheduler.cancelCalls.single())
    }

    // ===== Gaps de cobertura (QA/Supervisor) =====

    @Test
    fun `setAlarm con label la persiste en la entidad y la notificacion la muestra`() = runTest {
        coEvery { store.upsertAlarm(any()) } returns Unit
        coEvery { store.findAlarm(450) } returns alarmEntity(
            450, 7, 30,
            label = "Alarma de las 7:30 para despertarme",
            trigger = fixedCalendar(2026, Calendar.JANUARY, 15, 7, 30).timeInMillis
        )
        coEvery { store.removeAlarm(any()) } returns Unit

        alarmAction.setAlarm(7, 30, "despertarme")
        alarmAction.onAlarmFired(450)

        coVerify(exactly = 1) {
            store.upsertAlarm(
                match {
                    it.requestCode == 450 &&
                        it.label == "Alarma de las 7:30 para despertarme"
                }
            )
        }
        coVerify(exactly = 1) { notifier.showAlarm("Alarma de las 7:30 para despertarme", 450) }
    }

    @Test
    fun `setAlarm a la hora exacta actual pasa a manana`() = runTest {
        coEvery { store.upsertAlarm(any()) } returns Unit
        nowCal = fixedCalendar(2026, Calendar.JANUARY, 15, 7, 30)

        val result = alarmAction.setAlarm(7, 30, null)

        assertEquals("Éxito: Alarma configurada para las 7:30.", result)
        // trigger == now → !after → mañana
        val expectedTrigger = fixedCalendar(2026, Calendar.JANUARY, 16, 7, 30).timeInMillis
        assertEquals(listOf(expectedTrigger), scheduler.setExactCalls.map { it.triggerAtMillis })
    }

    @Test
    fun `setAlarm a las 23_59 usa el requestCode 1439 y las 0_00 el 0 sin colision`() = runTest {
        coEvery { store.upsertAlarm(any()) } returns Unit

        alarmAction.setAlarm(23, 59, null)

        verify(exactly = 1) {
            PendingIntent.getBroadcast(eq(context), eq(1439), any(), any())
        }
        coVerify(exactly = 1) { store.upsertAlarm(match { it.requestCode == 1439 }) }
        // 0:00 → requestCode 0 (distinto del 1439: franja horaria propia)
        alarmAction.setAlarm(0, 0, null)
        verify(exactly = 1) {
            PendingIntent.getBroadcast(eq(context), eq(0), any(), any())
        }
    }

    @Test
    fun `restoreActiveAlarms con store vacio no programa nada`() = runTest {
        coEvery { store.allAlarms() } returns emptyList()

        alarmAction.restoreActiveAlarms()

        assertTrue(scheduler.setExactCalls.isEmpty())
        coVerify(exactly = 1) { store.allAlarms() }
    }

    @Test
    fun `restoreActiveAlarms sin permiso de alarmas exactas no reprograma ni lanza`() = runTest {
        scheduler.canSchedule = false

        alarmAction.restoreActiveAlarms()

        // Guard: return inmediato antes incluso de tocar el store.
        coVerify(exactly = 0) { store.allAlarms() }
        assertTrue(scheduler.setExactCalls.isEmpty())
    }

    @Test
    fun `setAlarm en dia de DST spring-forward normaliza la hora inexistente`() = runTest {
        coEvery { store.upsertAlarm(any()) } returns Unit
        // 2026-03-08 en America/New_York: 02:00 → 03:00 (spring forward).
        // 02:30 no existe; Calendar la normaliza al mismo instante (02:30 EST == 03:30 EDT
        // == 07:30 UTC) → la alarma dispara ese instante, no lanza.
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        nowCal = fixedCalendar(2026, Calendar.MARCH, 8, 0, 0)

        alarmAction.setAlarm(2, 30, null)

        val expected = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.MARCH, 8, 7, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, scheduler.setExactCalls.single().triggerAtMillis)
    }

    @Test
    fun `cancelAlarm con hora y sin minutos documenta que cancela la hora en punto`() = runTest {
        // Decisión documentada: minute == null → 0 → cancela la hora en punto (7:00).
        val sevenOClock = alarmEntity(420, 7, 0)
        coEvery { store.findAlarm(420) } returns sevenOClock
        coEvery { store.removeAlarm(any()) } returns Unit

        val result = alarmAction.cancelAlarm(7, null)

        assertEquals("Éxito: Alarma de las 7:00 cancelada.", result)
        coVerify(exactly = 1) { store.findAlarm(420) }
        coVerify(exactly = 1) { store.removeAlarm(420) }
        assertSame(piMock, scheduler.cancelCalls.single())
    }
}
