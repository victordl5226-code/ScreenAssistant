package com.screenassistant.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProactiveRuleDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ProactiveRuleDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = db.proactiveRuleDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createRule(
        id: String = "rule-1",
        name: String = "Test Rule",
        conditionsJson: String = "[]",
        actionType: String = "Speak",
        actionData: String = "{}",
        priority: String = "MEDIUM",
        cooldownMinutes: Int = 30,
        enabled: Boolean = true
    ) = ProactiveRuleEntity(
        id = id,
        name = name,
        conditionsJson = conditionsJson,
        actionType = actionType,
        actionData = actionData,
        priority = priority,
        cooldownMinutes = cooldownMinutes,
        enabled = enabled
    )

    @Test
    fun `insertRule and getRule retrieves it`() = runTest {
        val rule = createRule()

        dao.insertRule(rule)

        val result = dao.getRule("rule-1")
        assertEquals(rule, result)
    }

    @Test
    fun `getRule returns null for non-existent id`() = runTest {
        val result = dao.getRule("nonexistent")

        assertNull(result)
    }

    @Test
    fun `getAllRules returns rules ordered by priority descending`() = runTest {
        // ORDER BY priority DESC es alfabético: MEDIUM > LOW > HIGH
        dao.insertRule(createRule(id = "low", name = "Low", priority = "LOW"))
        dao.insertRule(createRule(id = "high", name = "High", priority = "HIGH"))
        dao.insertRule(createRule(id = "medium", name = "Medium", priority = "MEDIUM"))

        val rules = dao.getAllRules().first()

        assertEquals(3, rules.size)
        assertEquals("medium", rules[0].id)
        assertEquals("low", rules[1].id)
        assertEquals("high", rules[2].id)
    }

    @Test
    fun `getAllRules returns empty when no rules exist`() = runTest {
        val rules = dao.getAllRules().first()

        assertTrue(rules.isEmpty())
    }

    @Test
    fun `getEnabledRules returns only enabled rules ordered by priority`() = runTest {
        // ORDER BY priority DESC es alfabético: MEDIUM > LOW > HIGH
        dao.insertRule(createRule(id = "enabled-high", priority = "HIGH", enabled = true))
        dao.insertRule(createRule(id = "disabled", priority = "HIGH", enabled = false))
        dao.insertRule(createRule(id = "enabled-low", priority = "LOW", enabled = true))

        val results = dao.getEnabledRules()

        assertEquals(2, results.size)
        assertEquals("enabled-low", results[0].id)
        assertEquals("enabled-high", results[1].id)
    }

    @Test
    fun `getEnabledRules returns empty when all rules are disabled`() = runTest {
        dao.insertRule(createRule(enabled = false))

        val results = dao.getEnabledRules()

        assertTrue(results.isEmpty())
    }

    @Test
    fun `deleteRule removes specific rule`() = runTest {
        dao.insertRule(createRule(id = "to-delete"))
        dao.insertRule(createRule(id = "to-keep"))

        dao.deleteRule("to-delete")

        assertNull(dao.getRule("to-delete"))
        assertEquals("to-keep", dao.getRule("to-keep")?.id)
    }

    @Test
    fun `deleteRule with non-existent id does nothing`() = runTest {
        dao.insertRule(createRule(id = "existing"))

        dao.deleteRule("nonexistent")

        assertEquals(1, dao.getAllRules().first().size)
    }

    @Test
    fun `toggleRule disables an enabled rule`() = runTest {
        dao.insertRule(createRule(id = "rule-1", enabled = true))

        dao.toggleRule("rule-1", false)

        val result = dao.getRule("rule-1")
        assertFalse(result?.enabled ?: true)
    }

    @Test
    fun `toggleRule enables a disabled rule`() = runTest {
        dao.insertRule(createRule(id = "rule-1", enabled = false))

        dao.toggleRule("rule-1", true)

        val result = dao.getRule("rule-1")
        assertTrue(result?.enabled ?: false)
    }

    @Test
    fun `toggleRule with non-existent id does nothing`() = runTest {
        dao.toggleRule("nonexistent", true)

        assertTrue(dao.getAllRules().first().isEmpty())
    }

    @Test
    fun `insertRule with replace strategy updates existing rule`() = runTest {
        dao.insertRule(createRule(id = "rule-1", name = "Old Name"))
        dao.insertRule(createRule(id = "rule-1", name = "New Name"))

        val result = dao.getRule("rule-1")

        assertEquals("New Name", result?.name)
    }

    @Test
    fun `getAllRules emits reactively on insert`() = runTest {
        val initialRules = dao.getAllRules().first()
        assertTrue(initialRules.isEmpty())

        dao.insertRule(createRule(id = "rule-1", name = "First"))

        val afterInsert = dao.getAllRules().first()
        assertEquals(1, afterInsert.size)
        assertEquals("First", afterInsert[0].name)
    }

    @Test
    fun `cooldownMinutes is preserved through round-trip`() = runTest {
        dao.insertRule(createRule(id = "rule-1", cooldownMinutes = 60))

        val result = dao.getRule("rule-1")

        assertEquals(60, result?.cooldownMinutes)
    }
}
