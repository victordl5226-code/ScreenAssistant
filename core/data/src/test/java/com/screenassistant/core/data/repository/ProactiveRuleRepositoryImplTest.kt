package com.screenassistant.core.data.repository

import com.screenassistant.core.data.local.ProactiveRuleDao
import com.screenassistant.core.data.local.ProactiveRuleEntity
import com.screenassistant.core.domain.model.proactive.ProactiveAction
import com.screenassistant.core.domain.model.proactive.ProactivePriority
import com.screenassistant.core.domain.model.proactive.ProactiveRule
import com.screenassistant.core.domain.model.proactive.RuleCondition
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class ProactiveRuleRepositoryImplTest {

    private lateinit var dao: ProactiveRuleDao
    private lateinit var json: Json
    private lateinit var repository: ProactiveRuleRepositoryImpl

    private fun testRuleEntity(
        id: String = "rule-1",
        name: String = "Test Rule",
        conditionsJson: String = """[{"type":"IsCharging"}]""",
        actionType: String = "Speak",
        actionData: String = """{"message":"Hello"}""",
        priority: String = "MEDIUM",
        cooldownMinutes: Int = 30,
        enabled: Boolean = true
    ) = ProactiveRuleEntity(
        id = id, name = name, conditionsJson = conditionsJson, actionType = actionType,
        actionData = actionData, priority = priority, cooldownMinutes = cooldownMinutes,
        enabled = enabled
    )

    private fun testRule(
        id: String = "rule-1",
        name: String = "Test Rule",
        conditions: List<RuleCondition> = listOf(RuleCondition.IsCharging),
        action: ProactiveAction = ProactiveAction.Speak("Hello"),
        priority: ProactivePriority = ProactivePriority.MEDIUM,
        enabled: Boolean = true,
        cooldownMinutes: Int = 30
    ) = ProactiveRule(
        id = id, name = name, conditions = conditions, action = action,
        priority = priority, enabled = enabled, cooldownMinutes = cooldownMinutes
    )

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        json = Json { ignoreUnknownKeys = true }
        repository = ProactiveRuleRepositoryImpl(dao, json)
    }

    @Test
    fun `getAllRules mapea entities a dominio`() = runTest {
        val entities = listOf(testRuleEntity())
        every { dao.getAllRules() } returns flowOf(entities)

        repository.getAllRules().collect { rules ->
            assertEquals(1, rules.size)
            assertEquals("rule-1", rules[0].id)
            assertEquals("Test Rule", rules[0].name)
            assertTrue(rules[0].action is ProactiveAction.Speak)
        }
    }

    @Test
    fun `getAllRules retorna lista vacia`() = runTest {
        every { dao.getAllRules() } returns flowOf(emptyList())

        repository.getAllRules().collect { rules ->
            assertTrue(rules.isEmpty())
        }
    }

    @Test
    fun `getEnabledRules retorna solo reglas habilitadas`() = runTest {
        val entities = listOf(testRuleEntity(enabled = true))
        coEvery { dao.getEnabledRules() } returns entities

        val result = repository.getEnabledRules()

        assertEquals(1, result.size)
        assertTrue(result[0].enabled)
    }

    @Test
    fun `getRule retorna regla existente`() = runTest {
        coEvery { dao.getRule("rule-1") } returns testRuleEntity()

        val result = repository.getRule("rule-1")

        assertEquals("rule-1", result?.id)
    }

    @Test
    fun `getRule retorna null cuando no existe`() = runTest {
        coEvery { dao.getRule("no-exist") } returns null

        val result = repository.getRule("no-exist")

        assertNull(result)
    }

    @Test
    fun `saveRule persiste regla correctamente`() = runTest {
        coEvery { dao.insertRule(any()) } returns Unit
        val rule = testRule()

        repository.saveRule(rule)

        coVerify(exactly = 1) {
            dao.insertRule(withArg { entity ->
                assertEquals("rule-1", entity.id)
                assertEquals("Test Rule", entity.name)
                assertEquals("Speak", entity.actionType)
            })
        }
    }

    @Test
    fun `deleteRule delega en dao`() = runTest {
        coEvery { dao.deleteRule("rule-1") } returns Unit

        repository.deleteRule("rule-1")

        coVerify(exactly = 1) { dao.deleteRule("rule-1") }
    }

    @Test
    fun `toggleRule cambia estado de regla existente`() = runTest {
        coEvery { dao.getRule("rule-1") } returns testRuleEntity(enabled = false)
        coEvery { dao.toggleRule("rule-1", true) } returns Unit

        repository.toggleRule("rule-1", true)

        coVerify(exactly = 1) { dao.toggleRule("rule-1", true) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `toggleRule lanza excepcion si regla no existe`() = runTest {
        coEvery { dao.getRule("no-exist") } returns null

        repository.toggleRule("no-exist", true)
    }

    @Test
    fun `serializa y deserializa condicion TimeRange`() = runTest {
        val conditions = listOf(RuleCondition.TimeRange(start = LocalTime.of(9, 0), end = LocalTime.of(17, 0)))
        val rule = testRule(conditions = conditions, action = ProactiveAction.Speak("test"))
        coEvery { dao.insertRule(any()) } returns Unit
        coEvery { dao.getRule("rule-1") } returns null

        repository.saveRule(rule)
        coEvery { dao.getRule("rule-1") } returns ProactiveRuleEntity(
            id = "rule-1", name = "Test Rule",
            conditionsJson = """[{"type":"TimeRange","start":32400,"end":61200}]""",
            actionType = "Speak", actionData = """{"message":"test"}""",
            priority = "MEDIUM", cooldownMinutes = 30, enabled = true
        )

        val loaded = repository.getRule("rule-1")
        assertTrue(loaded?.conditions?.first() is RuleCondition.TimeRange)
        val tr = loaded?.conditions?.first() as RuleCondition.TimeRange
        assertEquals(LocalTime.of(9, 0), tr.start)
        assertEquals(LocalTime.of(17, 0), tr.end)
    }

    @Test
    fun `serializa y deserializa condicion DayOfWeek`() = runTest {
        coEvery { dao.getRule("rule-1") } returns ProactiveRuleEntity(
            id = "rule-1", name = "Test Rule",
            conditionsJson = """[{"type":"DayOfWeek","days":["MONDAY","FRIDAY"]}]""",
            actionType = "Speak", actionData = """{"message":"hi"}""",
            priority = "HIGH", cooldownMinutes = 15, enabled = true
        )

        val loaded = repository.getRule("rule-1")
        assertTrue(loaded?.conditions?.first() is RuleCondition.DayOfWeek)
        val dow = loaded?.conditions?.first() as RuleCondition.DayOfWeek
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), dow.days)
    }

    @Test
    fun `serializa y deserializa BatteryBelow y BatteryAbove`() = runTest {
        coEvery { dao.getRule("rule-below") } returns ProactiveRuleEntity(
            id = "rule-below", name = "Low Battery",
            conditionsJson = """[{"type":"BatteryBelow","threshold":20}]""",
            actionType = "Speak", actionData = """{"message":"low"}""",
            priority = "LOW", cooldownMinutes = 30, enabled = true
        )
        coEvery { dao.getRule("rule-above") } returns ProactiveRuleEntity(
            id = "rule-above", name = "High Battery",
            conditionsJson = """[{"type":"BatteryAbove","threshold":80}]""",
            actionType = "Speak", actionData = """{"message":"high"}""",
            priority = "LOW", cooldownMinutes = 30, enabled = true
        )

        val below = repository.getRule("rule-below")
        assertTrue(below?.conditions?.first() is RuleCondition.BatteryBelow)
        assertEquals(20, (below?.conditions?.first() as RuleCondition.BatteryBelow).threshold)

        val above = repository.getRule("rule-above")
        assertTrue(above?.conditions?.first() is RuleCondition.BatteryAbove)
        assertEquals(80, (above?.conditions?.first() as RuleCondition.BatteryAbove).threshold)
    }

    @Test
    fun `serializa y deserializa condicion And`() = runTest {
        coEvery { dao.getRule("rule-and") } returns ProactiveRuleEntity(
            id = "rule-and", name = "And Rule",
            conditionsJson = """[{"type":"And","left":{"type":"IsCharging"},"right":{"type":"IsWeekend"}}]""",
            actionType = "ShowNotification",
            actionData = """{"title":"Hi","body":"Weekend charging"}""",
            priority = "MEDIUM", cooldownMinutes = 30, enabled = true
        )

        val loaded = repository.getRule("rule-and")
        assertTrue(loaded?.conditions?.first() is RuleCondition.And)
        val andCond = loaded?.conditions?.first() as RuleCondition.And
        assertTrue(andCond.left is RuleCondition.IsCharging)
        assertTrue(andCond.right is RuleCondition.IsWeekend)
    }

    @Test
    fun `serializa y deserializa accion ShowNotification`() = runTest {
        coEvery { dao.getRule("rule-notif") } returns ProactiveRuleEntity(
            id = "rule-notif", name = "Notification Rule",
            conditionsJson = """[{"type":"IsConnected"}]""",
            actionType = "ShowNotification",
            actionData = """{"title":"Title","body":"Body text"}""",
            priority = "LOW", cooldownMinutes = 30, enabled = true
        )

        val loaded = repository.getRule("rule-notif")
        assertTrue(loaded?.action is ProactiveAction.ShowNotification)
        val notif = loaded?.action as ProactiveAction.ShowNotification
        assertEquals("Title", notif.title)
        assertEquals("Body text", notif.body)
    }

    @Test
    fun `serializa y deserializa accion SuggestSystemAction con params`() = runTest {
        coEvery { dao.getRule("rule-sys") } returns ProactiveRuleEntity(
            id = "rule-sys", name = "System Rule",
            conditionsJson = """[{"type":"IsCharging"}]""",
            actionType = "SuggestSystemAction",
            actionData = """{"actionId":"brightness_up","params":{"level":"200"}}""",
            priority = "MEDIUM", cooldownMinutes = 30, enabled = true
        )

        val loaded = repository.getRule("rule-sys")
        assertTrue(loaded?.action is ProactiveAction.SuggestSystemAction)
        val sys = loaded?.action as ProactiveAction.SuggestSystemAction
        assertEquals("brightness_up", sys.actionId)
        assertEquals("200", sys.params["level"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `deserializacion con JSON invalido lanza excepcion`() = runTest {
        coEvery { dao.getRule("rule-bad") } returns ProactiveRuleEntity(
            id = "rule-bad", name = "Bad Rule",
            conditionsJson = "not-json",
            actionType = "Speak",
            actionData = "not-json-either",
            priority = "MEDIUM", cooldownMinutes = 30, enabled = true
        )

        repository.getRule("rule-bad")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `condiciones vacias en JSON lanza excepcion`() = runTest {
        coEvery { dao.getRule("rule-empty") } returns ProactiveRuleEntity(
            id = "rule-empty", name = "Empty",
            conditionsJson = "",
            actionType = "Speak", actionData = """{"message":"hi"}""",
            priority = "LOW", cooldownMinutes = 30, enabled = true
        )

        repository.getRule("rule-empty")
    }
}
