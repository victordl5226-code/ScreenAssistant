package com.screenassistant.core.iot.domain.usecase

import com.screenassistant.core.iot.domain.model.auto.CarAppState
import com.screenassistant.core.iot.domain.model.smartHome.AutomationRule
import com.screenassistant.core.iot.domain.model.smartHome.HomeAssistantEntity
import com.screenassistant.core.iot.domain.model.smartHome.HAContext
import com.screenassistant.core.iot.domain.model.smartHome.MatterDevice
import com.screenassistant.core.iot.domain.model.smartHome.MatterDeviceType
import com.screenassistant.core.iot.domain.model.smartHome.MatterScene
import com.screenassistant.core.iot.domain.model.wearables.HealthMetrics
import com.screenassistant.core.iot.domain.repository.IotRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncIotDevicesUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: IotRepository
    private lateinit var useCase: SyncIotDevicesUseCase

    private val now = Clock.System.now()

    private val matterDeviceOnline = MatterDevice(
        deviceId = "light-1",
        name = "Living Room Light",
        type = MatterDeviceType.ON_OFF_LIGHT,
        room = "Living Room",
        isOnline = true,
        attributes = emptyMap(),
        lastSeen = now,
        firmwareVersion = "1.0.0",
        manufacturer = "Philips",
        model = "Hue",
        serialNumber = "SN001",
    )

    private val matterDeviceOffline = MatterDevice(
        deviceId = "light-2",
        name = "Bedroom Light",
        type = MatterDeviceType.COLOR_TEMPERATURE_LIGHT,
        room = "Bedroom",
        isOnline = false,
        attributes = emptyMap(),
        lastSeen = now,
        firmwareVersion = "1.0.0",
        manufacturer = "Philips",
        model = "Hue",
        serialNumber = "SN002",
    )

    private val haEntityAvailable = HomeAssistantEntity(
        entityId = "light.salon",
        state = "on",
        attributes = mapOf("friendly_name" to "Salón"),
        lastChanged = now,
        lastUpdated = now,
        context = HAContext(id = "ctx1"),
    )

    private val haEntityUnavailable = HomeAssistantEntity(
        entityId = "sensor.temperature",
        state = "unavailable",
        attributes = emptyMap(),
        lastChanged = now,
        lastUpdated = now,
        context = HAContext(id = "ctx2"),
    )

    private val healthMetricsNoRecent = HealthMetrics()

    private val carStateDisconnected = CarAppState()

    private val scenes = listOf(
        MatterScene(
            sceneId = "scene-1",
            name = "Movie Night",
            icon = "movie",
            devices = emptyList(),
        )
    )

    private val automations = listOf(
        AutomationRule(
            ruleId = "rule-1",
            name = "Lights at sunset",
            triggers = emptyList(),
            actions = emptyList(),
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        useCase = SyncIotDevicesUseCase(repository)

        every { repository.getMatterDevices() } returns flowOf(listOf(matterDeviceOnline, matterDeviceOffline))
        every { repository.getHomeAssistantEntities() } returns flowOf(listOf(haEntityAvailable, haEntityUnavailable))
        every { repository.getHealthMetrics() } returns flowOf(healthMetricsNoRecent)
        every { repository.getCarAppState() } returns flowOf(carStateDisconnected)
        every { repository.getMatterScenes() } returns flowOf(scenes)
        every { repository.getAutomationRules() } returns flowOf(automations)
        coEvery { repository.getCurrentHealthMetrics() } returns healthMetricsNoRecent
        coEvery { repository.getCurrentCarAppState() } returns carStateDisconnected
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `invoke combines flows and emits IotSyncState`() = runTest {
        val state = useCase().first()
        assertEquals(listOf(matterDeviceOnline, matterDeviceOffline), state.matterDevices)
        assertEquals(listOf(haEntityAvailable, haEntityUnavailable), state.homeAssistantEntities)
        assertEquals(healthMetricsNoRecent, state.healthMetrics)
        assertEquals(carStateDisconnected, state.carAppState)
        assertEquals(scenes, state.scenes)
        assertEquals(automations, state.automations)
    }

    @Test
    fun `syncNow returns correct data from repository`() = runTest {
        val state = useCase.syncNow()
        assertEquals(listOf(matterDeviceOnline, matterDeviceOffline), state.matterDevices)
        assertEquals(listOf(haEntityAvailable, haEntityUnavailable), state.homeAssistantEntities)
        assertEquals(healthMetricsNoRecent, state.healthMetrics)
        assertEquals(carStateDisconnected, state.carAppState)
    }

    @Test
    fun `matterDevicesOnline counts only online devices`() = runTest {
        val state = useCase().first()
        assertEquals(1, state.matterDevicesOnline)
    }

    @Test
    fun `haEntitiesAvailable counts only available entities`() = runTest {
        val state = useCase().first()
        assertEquals(1, state.haEntitiesAvailable)
    }

    @Test
    fun `hasRecentHealthData is false when no metrics`() = runTest {
        val state = useCase().first()
        assertFalse(state.hasRecentHealthData)
    }

    @Test
    fun `isCarConnected is false when disconnected`() = runTest {
        val state = useCase().first()
        assertFalse(state.isCarConnected)
    }

    @Test
    fun `isCarConnected is true when connected`() = runTest {
        val connectedCar = CarAppState(isConnected = true)
        every { repository.getCarAppState() } returns flowOf(connectedCar)

        val state = useCase().first()
        assertTrue(state.isCarConnected)
    }

    @Test
    fun `summary formats correctly`() = runTest {
        val state = useCase().first()
        val summary = state.summary()
        assertTrue(summary.contains("Matter: 1/2 online"))
        assertTrue(summary.contains("HA: 1/2 available"))
        assertTrue(summary.contains("Car: disconnected"))
    }

    @Test
    fun `summary shows recent health when data is recent`() = runTest {
        val recentMetrics = HealthMetrics(
            heartRate = com.screenassistant.core.iot.domain.model.wearables.HeartRateMetric(
                timestamp = now,
                value = 72,
            )
        )
        every { repository.getHealthMetrics() } returns flowOf(recentMetrics)

        val state = useCase().first()
        val summary = state.summary()
        assertTrue(summary.contains("Health: recent"))
    }

    @Test
    fun `syncNow uses snapshot methods for health and car`() = runTest {
        val specialMetrics = HealthMetrics(
            heartRate = com.screenassistant.core.iot.domain.model.wearables.HeartRateMetric(
                timestamp = now,
                value = 80,
            )
        )
        val connectedCar = CarAppState(isConnected = true)
        coEvery { repository.getCurrentHealthMetrics() } returns specialMetrics
        coEvery { repository.getCurrentCarAppState() } returns connectedCar

        val state = useCase.syncNow()
        assertEquals(specialMetrics, state.healthMetrics)
        assertTrue(state.isCarConnected)
    }
}
