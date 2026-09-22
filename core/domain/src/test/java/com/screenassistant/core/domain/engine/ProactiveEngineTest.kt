package com.screenassistant.core.domain.engine

import com.screenassistant.core.domain.model.TemporalContext
import com.screenassistant.core.domain.model.DayPeriod
import com.screenassistant.core.domain.model.proactive.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.*

class ProactiveEngineTest {

    private lateinit var engine: ProactiveEngine
    private lateinit var fixedClock: Clock

    @Before
    fun setup() {
        fixedClock = Clock.fixed(Instant.parse("2026-01-15T14:30:00Z"), ZoneOffset.UTC)
        engine = ProactiveEngine(fixedClock)
    }

    private fun createContext(
        temporal: TemporalContext? = null,
        battery: BatteryInfo = BatteryInfo.from(100, false),
        upcomingEvents: List<CalendarEvent> = emptyList(),
        screen: ScreenInfo = ScreenInfo(null, ""),
        connectivity: ConnectivityInfo = ConnectivityInfo.from(true, false),
        location: LocationType = LocationType.UNKNOWN
    ): AggregatedContext {
        return AggregatedContext(
            temporal = temporal ?: TemporalContext.now(
                now = LocalDateTime.of(2026, 1, 15, 14, 30),
                holidayDates = emptySet()
            ),
            battery = battery,
            upcomingEvents = upcomingEvents,
            screen = screen,
            connectivity = connectivity,
            location = location
        )
    }

    private fun temporal(
        dateTime: LocalDateTime,
        holidayDates: Set<LocalDate> = emptySet()
    ): TemporalContext {
        return TemporalContext.now(now = dateTime, holidayDates = holidayDates)
    }

    // ── evaluate (lista de reglas) ──

    @Test
    fun `evaluate filtra reglas deshabilitadas`() {
        val rules = listOf(
            ProactiveRule(
                name = "Disabled",
                conditions = listOf(RuleCondition.IsConnected),
                action = ProactiveAction.Speak(message = "disabled"),
                enabled = false
            ),
            ProactiveRule(
                name = "Enabled",
                conditions = listOf(RuleCondition.IsConnected),
                action = ProactiveAction.Speak(message = "enabled"),
                enabled = true
            )
        )
        val context = createContext()

        val suggestions = engine.evaluate(rules, context)

        assertEquals(1, suggestions.size)
        assertEquals("Enabled", suggestions.first().title)
    }

    @Test
    fun `evaluate retorna lista vacía si no hay reglas activas`() {
        val rules = listOf(
            ProactiveRule(
                name = "Disabled 1",
                conditions = listOf(RuleCondition.IsConnected),
                action = ProactiveAction.Speak(message = "disabled"),
                enabled = false
            ),
            ProactiveRule(
                name = "Disabled 2",
                conditions = listOf(RuleCondition.IsConnected),
                action = ProactiveAction.Speak(message = "disabled"),
                enabled = false
            )
        )
        val context = createContext()

        val suggestions = engine.evaluate(rules, context)

        assertTrue(suggestions.isEmpty())
    }

    // ── evaluateCondition: TimeRange ──

    @Test
    fun `evaluateCondition TimeRange dentro del rango retorna true`() {
        val condition = RuleCondition.TimeRange(
            start = LocalTime.of(14, 0),
            end = LocalTime.of(16, 0)
        )
        val context = createContext(temporal = temporal(LocalDateTime.of(2026, 1, 15, 14, 30)))

        assertTrue(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition TimeRange fuera del rango retorna false`() {
        val condition = RuleCondition.TimeRange(
            start = LocalTime.of(14, 0),
            end = LocalTime.of(16, 0)
        )
        val context = createContext(temporal = temporal(LocalDateTime.of(2026, 1, 15, 10, 30)))

        assertFalse(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition TimeRange en límite inclusivo retorna true`() {
        val condition = RuleCondition.TimeRange(
            start = LocalTime.of(14, 0),
            end = LocalTime.of(16, 0)
        )
        val context = createContext(temporal = temporal(LocalDateTime.of(2026, 1, 15, 16, 0)))

        assertTrue(engine.evaluateCondition(condition, context))
    }

    // ── evaluateCondition: DayOfWeek ──

    @Test
    fun `evaluateCondition DayOfWeek día correcto retorna true`() {
        val condition = RuleCondition.DayOfWeek(days = setOf(DayOfWeek.THURSDAY))
        val context = createContext(temporal = temporal(LocalDateTime.of(2026, 1, 15, 14, 30)))

        assertTrue(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition DayOfWeek día incorrecto retorna false`() {
        val condition = RuleCondition.DayOfWeek(days = setOf(DayOfWeek.MONDAY))
        val context = createContext(temporal = temporal(LocalDateTime.of(2026, 1, 15, 14, 30)))

        assertFalse(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition DayOfWeek múltiples días`() {
        val condition = RuleCondition.DayOfWeek(days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        val context = createContext(temporal = temporal(LocalDateTime.of(2026, 1, 17, 14, 30)))

        assertTrue(engine.evaluateCondition(condition, context))
    }

    // ── evaluateCondition: BatteryBelow ──

    @Test
    fun `evaluateCondition BatteryBelow nivel bajo retorna true`() {
        val condition = RuleCondition.BatteryBelow(threshold = 20)
        val context = createContext(battery = BatteryInfo.from(15, false))

        assertTrue(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition BatteryBelow nivel alto retorna false`() {
        val condition = RuleCondition.BatteryBelow(threshold = 20)
        val context = createContext(battery = BatteryInfo.from(50, false))

        assertFalse(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition BatteryBelow en límite retorna true`() {
        val condition = RuleCondition.BatteryBelow(threshold = 20)
        val context = createContext(battery = BatteryInfo.from(20, false))

        assertTrue(engine.evaluateCondition(condition, context))
    }

    // ── evaluateCondition: BatteryAbove ──

    @Test
    fun `evaluateCondition BatteryAbove nivel alto retorna true`() {
        val condition = RuleCondition.BatteryAbove(threshold = 80)
        val context = createContext(battery = BatteryInfo.from(90, false))

        assertTrue(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition BatteryAbove nivel bajo retorna false`() {
        val condition = RuleCondition.BatteryAbove(threshold = 80)
        val context = createContext(battery = BatteryInfo.from(50, false))

        assertFalse(engine.evaluateCondition(condition, context))
    }

    // ── evaluateCondition: IsCharging ──

    @Test
    fun `evaluateCondition IsCharging cargando retorna true`() {
        val condition = RuleCondition.IsCharging
        val context = createContext(battery = BatteryInfo.from(50, true))

        assertTrue(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition IsCharging no cargando retorna false`() {
        val condition = RuleCondition.IsCharging
        val context = createContext(battery = BatteryInfo.from(50, false))

        assertFalse(engine.evaluateCondition(condition, context))
    }

    // ── evaluateCondition: HasUpcomingEvent (BUG-001 fix) ──

    @Test
    fun `evaluateCondition HasUpcomingEvent dentro de ventana retorna true`() {
        val condition = RuleCondition.HasUpcomingEvent(withinMinutes = 5)
        val now = Instant.now(fixedClock).toEpochMilli()
        val event = CalendarEvent(
            title = "Meeting",
            startMillis = now + 2 * 60 * 1000, // 2 minutes from now
            endMillis = now + 32 * 60 * 1000
        )
        val context = createContext(upcomingEvents = listOf(event))

        assertTrue(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition HasUpcomingEvent fuera de ventana retorna false`() {
        val condition = RuleCondition.HasUpcomingEvent(withinMinutes = 5)
        val now = Instant.now(fixedClock).toEpochMilli()
        val event = CalendarEvent(
            title = "Meeting",
            startMillis = now + 10 * 60 * 1000, // 10 minutes from now
            endMillis = now + 40 * 60 * 1000
        )
        val context = createContext(upcomingEvents = listOf(event))

        assertFalse(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition HasUpcomingEvent sin eventos retorna false`() {
        val condition = RuleCondition.HasUpcomingEvent(withinMinutes = 5)
        val context = createContext(upcomingEvents = emptyList())

        assertFalse(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition HasUpcomingEvent múltiples eventos uno en ventana`() {
        val condition = RuleCondition.HasUpcomingEvent(withinMinutes = 5)
        val now = Instant.now(fixedClock).toEpochMilli()
        val events = listOf(
            CalendarEvent(title = "Later", startMillis = now + 10 * 60 * 1000, endMillis = now + 40 * 60 * 1000),
            CalendarEvent(title = "Soon", startMillis = now + 2 * 60 * 1000, endMillis = now + 32 * 60 * 1000)
        )
        val context = createContext(upcomingEvents = events)

        assertTrue(engine.evaluateCondition(condition, context))
    }

    // ── evaluateCondition: ScreenContains ──

    @Test
    fun `evaluateCondition ScreenContains texto encontrado retorna true`() {
        val condition = RuleCondition.ScreenContains(text = "batería")
        val context = createContext(screen = ScreenInfo("com.android.settings", "Batería al 15%"))

        assertTrue(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition ScreenContains texto no encontrado retorna false`() {
        val condition = RuleCondition.ScreenContains(text = "wifi")
        val context = createContext(screen = ScreenInfo("com.android.settings", "Batería al 15%"))

        assertFalse(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition ScreenContains case insensitive`() {
        val condition = RuleCondition.ScreenContains(text = "BATERÍA")
        val context = createContext(screen = ScreenInfo("com.android.settings", "batería baja"))

        assertTrue(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition ScreenContains texto vacío retorna false`() {
        val condition = RuleCondition.ScreenContains(text = "algo")
        val context = createContext(screen = ScreenInfo(null, ""))

        assertFalse(engine.evaluateCondition(condition, context))
    }

    // ── evaluateCondition: AppInForeground ──

    @Test
    fun `evaluateCondition AppInForeground app correcta retorna true`() {
        val condition = RuleCondition.AppInForeground(packageName = "com.android.chrome")
        val context = createContext(screen = ScreenInfo("com.android.chrome", ""))

        assertTrue(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition AppInForeground app incorrecta retorna false`() {
        val condition = RuleCondition.AppInForeground(packageName = "com.android.chrome")
        val context = createContext(screen = ScreenInfo("com.android.settings", ""))

        assertFalse(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition AppInForeground sin packageName retorna false`() {
        val condition = RuleCondition.AppInForeground(packageName = "com.android.chrome")
        val context = createContext(screen = ScreenInfo(null, ""))

        assertFalse(engine.evaluateCondition(condition, context))
    }

    // ── evaluateCondition: AtLocation ──

    @Test
    fun `evaluateCondition AtLocation ubicación correcta retorna true`() {
        val condition = RuleCondition.AtLocation(location = LocationType.HOME)
        val context = createContext(location = LocationType.HOME)

        assertTrue(engine.evaluateCondition(condition, context))
    }

    // ── evaluateCondition: IsWeekend ──

    @Test
    fun `evaluateCondition IsWeekend sábado retorna true`() {
        val condition = RuleCondition.IsWeekend
        val context = createContext(temporal = temporal(LocalDateTime.of(2026, 1, 17, 14, 30)))

        assertTrue(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition IsWeekend domingo retorna true`() {
        val condition = RuleCondition.IsWeekend
        val context = createContext(temporal = temporal(LocalDateTime.of(2026, 1, 18, 14, 30)))

        assertTrue(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition IsWeekend día laboral retorna false`() {
        val condition = RuleCondition.IsWeekend
        val context = createContext(temporal = temporal(LocalDateTime.of(2026, 1, 15, 14, 30)))

        assertFalse(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition IsWeekend día festivo laboral retorna false`() {
        // IsWeekend solo evalúa esFinDeSemana (sábado/domingo), NO festivos
        val condition = RuleCondition.IsWeekend
        val context = createContext(temporal = temporal(
            LocalDateTime.of(2026, 12, 25, 14, 30),
            setOf(LocalDate.of(2026, 12, 25))
        ))

        assertFalse(engine.evaluateCondition(condition, context))
    }

    // ── evaluateCondition: IsConnected ──

    @Test
    fun `evaluateCondition IsConnected wifi retorna true`() {
        val condition = RuleCondition.IsConnected
        val context = createContext(connectivity = ConnectivityInfo.from(true, false))

        assertTrue(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition IsConnected mobile retorna true`() {
        val condition = RuleCondition.IsConnected
        val context = createContext(connectivity = ConnectivityInfo.from(false, true))

        assertTrue(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition IsConnected desconectado retorna false`() {
        val condition = RuleCondition.IsConnected
        val context = createContext(connectivity = ConnectivityInfo.from(false, false))

        assertFalse(engine.evaluateCondition(condition, context))
    }

    // ── evaluateCondition: Combinadores lógicos ──

    @Test
    fun `evaluateCondition And todas verdaderas retorna true`() {
        val condition = RuleCondition.And(
            left = RuleCondition.IsConnected,
            right = RuleCondition.IsCharging
        )
        val context = createContext(
            battery = BatteryInfo.from(50, true),
            connectivity = ConnectivityInfo.from(true, false)
        )

        assertTrue(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition And una falsa retorna false`() {
        val condition = RuleCondition.And(
            left = RuleCondition.IsConnected,
            right = RuleCondition.IsCharging
        )
        val context = createContext(
            battery = BatteryInfo.from(50, false),
            connectivity = ConnectivityInfo.from(true, false)
        )

        assertFalse(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition Or una verdadera retorna true`() {
        val condition = RuleCondition.Or(
            left = RuleCondition.IsConnected,
            right = RuleCondition.IsCharging
        )
        val context = createContext(
            battery = BatteryInfo.from(50, false),
            connectivity = ConnectivityInfo.from(true, false)
        )

        assertTrue(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition Or ambas falsas retorna false`() {
        val condition = RuleCondition.Or(
            left = RuleCondition.IsConnected,
            right = RuleCondition.IsCharging
        )
        val context = createContext(
            battery = BatteryInfo.from(50, false),
            connectivity = ConnectivityInfo.from(false, false)
        )

        assertFalse(engine.evaluateCondition(condition, context))
    }

    @Test
    fun `evaluateCondition Not invierte resultado`() {
        val condition = RuleCondition.Not(condition = RuleCondition.IsConnected)
        val contextConnected = createContext(connectivity = ConnectivityInfo.from(true, false))
        val contextDisconnected = createContext(connectivity = ConnectivityInfo.from(false, false))

        assertFalse(engine.evaluateCondition(condition, contextConnected))
        assertTrue(engine.evaluateCondition(condition, contextDisconnected))
    }

    @Test
    fun `evaluateCondition combinación compleja And Or Not`() {
        val condition = RuleCondition.Or(
            left = RuleCondition.And(
                left = RuleCondition.IsConnected,
                right = RuleCondition.IsCharging
            ),
            right = RuleCondition.Not(condition = RuleCondition.IsWeekend)
        )

        val context1 = createContext(
            battery = BatteryInfo.from(50, true),
            connectivity = ConnectivityInfo.from(true, false),
            temporal = temporal(LocalDateTime.of(2026, 1, 15, 14, 30))
        )
        assertTrue(engine.evaluateCondition(condition, context1))

        val context2 = createContext(
            battery = BatteryInfo.from(50, false),
            connectivity = ConnectivityInfo.from(false, false),
            temporal = temporal(LocalDateTime.of(2026, 1, 15, 14, 30))
        )
        assertTrue(engine.evaluateCondition(condition, context2))

        val context3 = createContext(
            battery = BatteryInfo.from(50, false),
            connectivity = ConnectivityInfo.from(false, false),
            temporal = temporal(LocalDateTime.of(2026, 1, 17, 14, 30))
        )
        assertFalse(engine.evaluateCondition(condition, context3))
    }

    // ── buildSuggestion ──

    @Test
    fun `buildSuggestion genera ID único y mensaje correcto para ShowNotification`() {
        val rule = ProactiveRule(
            name = "Batería baja",
            conditions = listOf(RuleCondition.BatteryBelow(threshold = 15)),
            action = ProactiveAction.ShowNotification(
                title = "Batería",
                body = "Tu batería está baja"
            ),
            enabled = true
        )
        val context = createContext(battery = BatteryInfo.from(10, false))

        val suggestion = engine.buildSuggestion(rule, context)

        assertEquals("Batería baja", suggestion.title)
        assertEquals("Tu batería está baja", suggestion.message)
        assertEquals(ProactivePriority.MEDIUM, suggestion.priority)
        assertTrue(suggestion.id.startsWith("rule_"))
        assertEquals("rule:${rule.id}", suggestion.source)
    }

    @Test
    fun `buildSuggestion con SuggestSystemAction`() {
        val rule = ProactiveRule(
            name = "Abrir ajustes",
            conditions = listOf(RuleCondition.BatteryBelow(threshold = 15)),
            action = ProactiveAction.SuggestSystemAction(actionId = "open_settings", params = mapOf("screen" to "battery")),
            enabled = true
        )
        val context = createContext(battery = BatteryInfo.from(10, false))

        val suggestion = engine.buildSuggestion(rule, context)

        assertEquals("Acción disponible: open_settings", suggestion.message)
    }

    @Test
    fun `buildSuggestion con Speak`() {
        val rule = ProactiveRule(
            name = "Hablar",
            conditions = listOf(RuleCondition.BatteryBelow(threshold = 15)),
            action = ProactiveAction.Speak(message = "Tu batería está baja"),
            enabled = true
        )
        val context = createContext(battery = BatteryInfo.from(10, false))

        val suggestion = engine.buildSuggestion(rule, context)

        assertEquals("Tu batería está baja", suggestion.message)
    }

    @Test
    fun `buildSuggestion con SuggestAutomation`() {
        val rule = ProactiveRule(
            name = "Automatizar",
            conditions = listOf(RuleCondition.BatteryBelow(threshold = 15)),
            action = ProactiveAction.SuggestAutomation(
                name = "Ahorro de batería",
                description = "Activar modo ahorro cuando batería < 15%"
            ),
            enabled = true
        )
        val context = createContext(battery = BatteryInfo.from(10, false))

        val suggestion = engine.buildSuggestion(rule, context)

        assertEquals("Detecté un patrón recurrente. ¿Quieres automatizarlo?", suggestion.message)
    }

    // ── evaluate (regla individual) ──

    @Test
    fun `evaluate regla individual deshabilitada retorna false`() {
        val rule = ProactiveRule(
            name = "Test",
            conditions = listOf(RuleCondition.IsConnected),
            action = ProactiveAction.Speak(message = "test"),
            enabled = false
        )
        val context = createContext()

        assertFalse(engine.evaluate(rule, context))
    }

    @Test
    fun `evaluate regla individual con condición simple`() {
        val rule = ProactiveRule(
            name = "Test",
            conditions = listOf(RuleCondition.IsConnected),
            action = ProactiveAction.Speak(message = "test"),
            enabled = true
        )
        val context = createContext()

        assertTrue(engine.evaluate(rule, context))
    }

    @Test
    fun `evaluate regla individual habilitada y activa retorna true`() {
        val rule = ProactiveRule(
            name = "Test",
            conditions = listOf(RuleCondition.IsConnected),
            action = ProactiveAction.Speak(message = "test"),
            enabled = true
        )
        val context = createContext()

        assertTrue(engine.evaluate(rule, context))
    }
}