package com.screenassistant.core.iot.data.repository

import android.util.Log
import com.screenassistant.core.iot.data.util.FakeIotClock
import com.screenassistant.core.iot.domain.model.smartHome.MatterAttribute
import com.screenassistant.core.iot.domain.model.smartHome.MatterCommand
import com.screenassistant.core.iot.domain.model.smartHome.MatterDevice
import com.screenassistant.core.iot.domain.model.smartHome.MatterDeviceType
import com.screenassistant.core.iot.domain.model.smartHome.MatterScene
import com.screenassistant.core.iot.domain.model.smartHome.SceneDeviceState
import com.screenassistant.core.iot.domain.repository.MatterDeviceRepository
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para [MatterDeviceRepositoryImpl].
 *
 * Suite completa: dispositivos (observe, get, send, rediscover),
 * escenas (observe, get, create, update, delete, activate) y cache.
 * Usa [FakeIotClock] para timestamps deterministas.
 */
class MatterDeviceRepositoryImplTest {

    private lateinit var clock: FakeIotClock
    private lateinit var repository: MatterDeviceRepositoryImpl

    private val fixedTime = Instant.parse("2026-01-15T12:00:00Z")

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun createDevice(
        deviceId: String = "device_1",
        name: String = "Living Room Light",
        type: MatterDeviceType = MatterDeviceType.ON_OFF_LIGHT,
        attributes: Map<String, MatterAttribute> = mapOf(
            "onOff" to MatterAttribute(value = true, lastUpdated = fixedTime),
        ),
    ) = MatterDevice(
        deviceId = deviceId,
        name = name,
        type = type,
        room = "Living Room",
        isOnline = true,
        attributes = attributes,
        lastSeen = fixedTime,
        firmwareVersion = "1.0.0",
        manufacturer = "Philips",
        model = "Hue Bulb",
        serialNumber = "SN001",
    )

    /** Helper para crear MatterScene directamente (usa el SceneDeviceState del dominio). */
    private fun createMatterScene(
        sceneId: String = "scene_1",
        name: String = "Movie Night",
        devices: List<SceneDeviceState> = emptyList(),
    ) = MatterScene(
        sceneId = sceneId,
        name = name,
        icon = "movie",
        devices = devices,
        createdAt = fixedTime,
        updatedAt = fixedTime,
    )

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        clock = FakeIotClock(fixedTime)
        val mockIotDao = io.mockk.mockk<com.screenassistant.core.data.local.iot.IotDao>(relaxed = true)
        repository = MatterDeviceRepositoryImpl(clock, mockIotDao)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DISPOSITIVOS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `observeDevices returns flow with empty list initially`() = runTest {
        val devices = repository.observeDevices().first()
        assertTrue(devices.isEmpty())
    }

    @Test
    fun `observeDevices returns flow with loaded devices`() = runTest {
        val device = createDevice()
        repository.loadDevices(listOf(device))

        val devices = repository.observeDevices().first()
        assertEquals(1, devices.size)
        assertEquals("device_1", devices[0].deviceId)
    }

    @Test
    fun `getDevice returns device when exists`() = runTest {
        val device = createDevice(deviceId = "light_1")
        repository.loadDevices(listOf(device))

        val result = repository.getDevice("light_1")
        assertNotNull(result)
        assertEquals("light_1", result?.deviceId)
        assertEquals("Living Room Light", result?.name)
    }

    @Test
    fun `getDevice returns null when not exists`() = runTest {
        val result = repository.getDevice("nonexistent")
        assertNull(result)
    }

    @Test
    fun `sendCommand updates device attribute optimistically`() = runTest {
        val device = createDevice(
            attributes = mapOf(
                "onOff" to MatterAttribute(value = false, lastUpdated = fixedTime),
            ),
        )
        repository.loadDevices(listOf(device))

        val command = MatterCommand.onOff(deviceId = "device_1", on = true)
        val result = repository.sendCommand(command)

        assertTrue(result)
        val updated = repository.getDevice("device_1")
        assertNotNull(updated)
        val attr = updated?.attributes?.get("onOff")
        assertNotNull(attr)
        assertEquals(true, attr?.value)
        assertEquals(fixedTime, attr?.lastUpdated)
    }

    @Test
    fun `sendCommand returns false for nonexistent device`() = runTest {
        val command = MatterCommand.onOff(deviceId = "ghost", on = true)
        val result = repository.sendCommand(command)
        assertFalse(result)
    }

    @Test
    fun `rediscoverDevices returns current devices`() = runTest {
        val device = createDevice()
        repository.loadDevices(listOf(device))

        val discovered = repository.rediscoverDevices()
        assertEquals(1, discovered.size)
        assertEquals("device_1", discovered[0].deviceId)
    }

    @Test
    fun `rediscoverDevices returns empty list when no devices`() = runTest {
        val discovered = repository.rediscoverDevices()
        assertTrue(discovered.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ESCENAS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `observeScenes returns flow with empty list initially`() = runTest {
        val scenes = repository.observeScenes().first()
        assertTrue(scenes.isEmpty())
    }

    @Test
    fun `observeScenes returns flow with loaded scenes`() = runTest {
        val scene = createMatterScene()
        repository.loadScenes(listOf(scene))

        val scenes = repository.observeScenes().first()
        assertEquals(1, scenes.size)
        assertEquals("scene_1", scenes[0].sceneId)
    }

    @Test
    fun `getScene returns scene when exists`() = runTest {
        val scene = createMatterScene(sceneId = "scene_42")
        repository.loadScenes(listOf(scene))

        val result = repository.getScene("scene_42")
        assertNotNull(result)
        assertEquals("scene_42", result?.sceneId)
    }

    @Test
    fun `getScene returns null when not exists`() = runTest {
        val result = repository.getScene("nonexistent_scene")
        assertNull(result)
    }

    @Test
    fun `createScene creates and adds to list`() = runTest {
        val deviceState = MatterDeviceRepository.SceneDeviceState(
            deviceId = "device_1",
            targetAttributes = mapOf("onOff" to "true"),
        )

        val scene = repository.createScene(
            name = "Good Morning",
            icon = "sunrise",
            deviceStates = listOf(deviceState),
        )

        assertNotNull(scene.sceneId)
        assertTrue(scene.sceneId.startsWith("scene_"))
        assertEquals("Good Morning", scene.name)
        assertEquals("sunrise", scene.icon)
        assertEquals(1, scene.devices.size)
        assertEquals("device_1", scene.devices[0].deviceId)
        assertEquals(fixedTime, scene.createdAt)
        assertEquals(fixedTime, scene.updatedAt)

        // Verificar que aparece en el flow
        val scenes = repository.observeScenes().first()
        assertEquals(1, scenes.size)
        assertEquals("Good Morning", scenes[0].name)
    }

    @Test
    fun `updateScene updates existing scene`() = runTest {
        val scene = createMatterScene(name = "Old Name")
        repository.loadScenes(listOf(scene))

        val updated = scene.copy(name = "New Name")
        val result = repository.updateScene(updated)

        assertTrue(result)
        val scenes = repository.observeScenes().first()
        assertEquals(1, scenes.size)
        assertEquals("New Name", scenes[0].name)
        // updatedAt should be refreshed by the clock
        assertEquals(fixedTime, scenes[0].updatedAt)
    }

    @Test
    fun `updateScene returns false for nonexistent scene`() = runTest {
        val ghost = createMatterScene(sceneId = "ghost_scene")
        val result = repository.updateScene(ghost)
        assertFalse(result)
    }

    @Test
    fun `deleteScene deletes existing scene`() = runTest {
        val scene = createMatterScene()
        repository.loadScenes(listOf(scene))

        val result = repository.deleteScene("scene_1")
        assertTrue(result)

        val scenes = repository.observeScenes().first()
        assertTrue(scenes.isEmpty())
    }

    @Test
    fun `deleteScene returns false for nonexistent scene`() = runTest {
        val result = repository.deleteScene("ghost_scene")
        assertFalse(result)
    }

    @Test
    fun `deleteScene does not affect other scenes`() = runTest {
        val scene1 = createMatterScene(sceneId = "s1", name = "Scene 1")
        val scene2 = createMatterScene(sceneId = "s2", name = "Scene 2")
        repository.loadScenes(listOf(scene1, scene2))

        repository.deleteScene("s1")

        val scenes = repository.observeScenes().first()
        assertEquals(1, scenes.size)
        assertEquals("s2", scenes[0].sceneId)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ACTIVATE SCENE
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `activateScene returns error for nonexistent scene`() = runTest {
        val result = repository.activateScene("ghost_scene")
        assertFalse(result.success)
        assertEquals("ghost_scene", result.sceneId)
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("not found"))
    }

    @Test
    fun `activateScene with all devices successful returns success`() = runTest {
        // Setup: crear dispositivo y escena que lo referencie
        val device = createDevice(
            attributes = mapOf(
                "onOff" to MatterAttribute(value = false, lastUpdated = fixedTime),
            ),
        )
        repository.loadDevices(listOf(device))

        // Crear escena usando SceneDeviceState del dominio (targetAttributes: Map<String, String>)
        val sceneDeviceState = SceneDeviceState(
            deviceId = "device_1",
            targetAttributes = mapOf("onOff" to "true"),
        )
        val scene = createMatterScene(devices = listOf(sceneDeviceState))
        repository.loadScenes(listOf(scene))

        val result = repository.activateScene("scene_1")

        assertTrue(result.success)
        assertEquals("scene_1", result.sceneId)
        assertEquals(1, result.deviceResults.size)
        assertTrue(result.deviceResults[0].success)
        assertEquals("device_1", result.deviceResults[0].deviceId)
    }

    @Test
    fun `activateScene with missing device returns partial error`() = runTest {
        // Escena referencia dispositivo que no existe
        val sceneDeviceState = SceneDeviceState(
            deviceId = "missing_device",
            targetAttributes = mapOf("onOff" to "true"),
        )
        val scene = createMatterScene(devices = listOf(sceneDeviceState))
        repository.loadScenes(listOf(scene))

        val result = repository.activateScene("scene_1")

        assertFalse(result.success)
        assertEquals(1, result.deviceResults.size)
        assertFalse(result.deviceResults[0].success)
        assertEquals("missing_device", result.deviceResults[0].deviceId)
        assertNotNull(result.deviceResults[0].error)
    }

    @Test
    fun `activateScene with mixed results returns partial success`() = runTest {
        // Un dispositivo existe, otro no
        val device = createDevice(
            deviceId = "light_1",
            attributes = mapOf(
                "onOff" to MatterAttribute(value = false, lastUpdated = fixedTime),
            ),
        )
        repository.loadDevices(listOf(device))

        val sceneDeviceStates = listOf(
            SceneDeviceState(
                deviceId = "light_1",
                targetAttributes = mapOf("onOff" to "true"),
            ),
            SceneDeviceState(
                deviceId = "missing_light",
                targetAttributes = mapOf("onOff" to "true"),
            ),
        )
        val scene = createMatterScene(devices = sceneDeviceStates)
        repository.loadScenes(listOf(scene))

        val result = repository.activateScene("scene_1")

        assertFalse(result.success)
        assertEquals(2, result.deviceResults.size)
        assertTrue(result.deviceResults[0].success)
        assertFalse(result.deviceResults[1].success)
    }

    @Test
    fun `activateScene with unknown capability returns error`() = runTest {
        val device = createDevice()
        repository.loadDevices(listOf(device))

        val sceneDeviceState = SceneDeviceState(
            deviceId = "device_1",
            targetAttributes = mapOf("unknownCapability" to "value"),
        )
        val scene = createMatterScene(devices = listOf(sceneDeviceState))
        repository.loadScenes(listOf(scene))

        val result = repository.activateScene("scene_1")

        assertFalse(result.success)
        assertEquals(1, result.deviceResults.size)
        assertFalse(result.deviceResults[0].success)
        assertNotNull(result.deviceResults[0].error)
    }

    @Test
    fun `activateScene with no target attributes returns error`() = runTest {
        val device = createDevice()
        repository.loadDevices(listOf(device))

        val sceneDeviceState = SceneDeviceState(
            deviceId = "device_1",
            targetAttributes = emptyMap(),
        )
        val scene = createMatterScene(devices = listOf(sceneDeviceState))
        repository.loadScenes(listOf(scene))

        val result = repository.activateScene("scene_1")

        assertFalse(result.success)
        assertEquals(1, result.deviceResults.size)
        assertFalse(result.deviceResults[0].success)
        assertNotNull(result.deviceResults[0].error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CACHE
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `loadDevices updates cache completely`() = runTest {
        val device1 = createDevice(deviceId = "d1", name = "Light 1")
        val device2 = createDevice(deviceId = "d2", name = "Light 2")
        repository.loadDevices(listOf(device1, device2))

        val devices = repository.observeDevices().first()
        assertEquals(2, devices.size)

        // Load again with different list (replaces, not appends)
        val device3 = createDevice(deviceId = "d3", name = "Light 3")
        repository.loadDevices(listOf(device3))

        val devices2 = repository.observeDevices().first()
        assertEquals(1, devices2.size)
        assertEquals("d3", devices2[0].deviceId)
    }

    @Test
    fun `loadScenes updates cache completely`() = runTest {
        val scene1 = createMatterScene(sceneId = "s1", name = "Scene 1")
        repository.loadScenes(listOf(scene1))

        assertEquals(1, repository.observeScenes().first().size)

        val scene2 = createMatterScene(sceneId = "s2", name = "Scene 2")
        val scene3 = createMatterScene(sceneId = "s3", name = "Scene 3")
        repository.loadScenes(listOf(scene2, scene3))

        val scenes = repository.observeScenes().first()
        assertEquals(2, scenes.size)
    }

    @Test
    fun `loadDevices with empty list clears cache`() = runTest {
        repository.loadDevices(listOf(createDevice()))
        assertEquals(1, repository.observeDevices().first().size)

        repository.loadDevices(emptyList())
        assertTrue(repository.observeDevices().first().isEmpty())
    }

    @Test
    fun `sendCommand updates attribute timestamp from clock`() = runTest {
        val device = createDevice(
            attributes = mapOf(
                "onOff" to MatterAttribute(value = false, lastUpdated = fixedTime),
            ),
        )
        repository.loadDevices(listOf(device))

        // Advance clock
        clock.advanceMinutes(30)
        val laterTime = clock.now()

        val command = MatterCommand.onOff(deviceId = "device_1", on = true)
        repository.sendCommand(command)

        val updated = repository.getDevice("device_1")
        val attr = updated?.attributes?.get("onOff")
        assertEquals(laterTime, attr?.lastUpdated)
    }
}
