package com.screenassistant.feature.iot.ui.settings

import com.screenassistant.core.iot.domain.model.auto.CarAppState
import com.screenassistant.core.iot.domain.model.wearables.HealthMetrics
import com.screenassistant.core.iot.domain.usecase.IotSyncState
import com.screenassistant.core.iot.domain.usecase.SyncIotDevicesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IoTSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var syncUseCase: SyncIotDevicesUseCase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        syncUseCase = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeSyncState(
        matterDeviceCount: Int = 0,
        onlineCount: Int = 0,
        healthMetrics: HealthMetrics = HealthMetrics(),
        carConnected: Boolean = false,
        timestamp: Long = System.currentTimeMillis(),
    ) = IotSyncState(
        matterDevices = (1..matterDeviceCount).map { mockk(relaxed = true) { every { isOnline } returns (it <= onlineCount) } },
        homeAssistantEntities = emptyList(),
        healthMetrics = healthMetrics,
        carAppState = CarAppState(isConnected = carConnected),
        scenes = emptyList(),
        automations = emptyList(),
        lastSyncTimestamp = timestamp,
    )

    @Test
    fun `initial state is Loading`() = runTest {
        coEvery { syncUseCase() } returns flowOf(fakeSyncState())
        val vm = IoTSettingsViewModel(syncUseCase)
        assertTrue(vm.uiState.value is IoTSettingsUiState.Loading)
    }

    @Test
    fun `emits Content after sync flow emits`() = runTest {
        val state = fakeSyncState(matterDeviceCount = 3, onlineCount = 2, carConnected = true)
        coEvery { syncUseCase() } returns flowOf(state)
        val vm = IoTSettingsViewModel(syncUseCase)
        advanceUntilIdle()

        val content = vm.uiState.value as IoTSettingsUiState.Content
        assertEquals(3, content.matterDevicesTotal)
        assertTrue(content.carConnected)
    }

    @Test
    fun `Content maps matterDevicesOnline from syncState`() = runTest {
        val state = fakeSyncState(matterDeviceCount = 5, onlineCount = 3)
        coEvery { syncUseCase() } returns flowOf(state)
        val vm = IoTSettingsViewModel(syncUseCase)
        advanceUntilIdle()

        val content = vm.uiState.value as IoTSettingsUiState.Content
        assertEquals(3, content.matterDevicesOnline)
    }

    @Test
    fun `emits Error when flow throws`() = runTest {
        coEvery { syncUseCase() } returns flow { throw RuntimeException("network error") }
        val vm = IoTSettingsViewModel(syncUseCase)
        advanceUntilIdle()

        val error = vm.uiState.value as IoTSettingsUiState.Error
        assertEquals("network error", error.message)
    }

    @Test
    fun `syncNow transitions Loading then Content`() = runTest {
        coEvery { syncUseCase() } returns flowOf(fakeSyncState())
        coEvery { syncUseCase.syncNow() } returns fakeSyncState(matterDeviceCount = 5, onlineCount = 5, carConnected = true)
        val vm = IoTSettingsViewModel(syncUseCase)
        advanceUntilIdle()

        vm.syncNow()
        advanceUntilIdle()
        val content = vm.uiState.value as IoTSettingsUiState.Content
        assertEquals(5, content.matterDevicesTotal)
        assertTrue(content.carConnected)
    }

    @Test
    fun `syncNow sets Loading before Content`() = runTest {
        coEvery { syncUseCase() } returns flowOf(fakeSyncState())
        coEvery { syncUseCase.syncNow() } coAnswers {
            kotlinx.coroutines.delay(100)
            fakeSyncState(matterDeviceCount = 2, onlineCount = 2)
        }
        val vm = IoTSettingsViewModel(syncUseCase)
        advanceUntilIdle()

        vm.syncNow()
        // Advance just enough to enter the launch block and set Loading
        testDispatcher.scheduler.advanceTimeBy(10)
        assertTrue(vm.uiState.value is IoTSettingsUiState.Loading)

        advanceUntilIdle()
        assertTrue(vm.uiState.value is IoTSettingsUiState.Content)
    }

    @Test
    fun `syncNow emits Error on failure`() = runTest {
        coEvery { syncUseCase() } returns flowOf(fakeSyncState())
        coEvery { syncUseCase.syncNow() } throws RuntimeException("sync failed")
        val vm = IoTSettingsViewModel(syncUseCase)
        advanceUntilIdle()

        vm.syncNow()
        advanceUntilIdle()
        val error = vm.uiState.value as IoTSettingsUiState.Error
        assertEquals("sync failed", error.message)
    }

    @Test
    fun `health summary reflects health metrics`() = runTest {
        val metrics = HealthMetrics()
        coEvery { syncUseCase() } returns flowOf(fakeSyncState(healthMetrics = metrics))
        val vm = IoTSettingsViewModel(syncUseCase)
        advanceUntilIdle()

        val content = vm.uiState.value as IoTSettingsUiState.Content
        assertEquals("Sin datos recientes", content.healthSummary)
    }

    @Test
    fun `lastSyncTime shows less than 1 min for recent timestamp`() = runTest {
        val now = System.currentTimeMillis()
        val state = fakeSyncState(timestamp = now - 30_000) // 30 seconds ago
        coEvery { syncUseCase() } returns flowOf(state)
        val vm = IoTSettingsViewModel(syncUseCase)
        advanceUntilIdle()

        val content = vm.uiState.value as IoTSettingsUiState.Content
        assertEquals("Hace menos de 1 min", content.lastSyncTime)
    }

    @Test
    fun `lastSyncTime shows minutes for timestamp within hour`() = runTest {
        val now = System.currentTimeMillis()
        val state = fakeSyncState(timestamp = now - 150_000) // 2.5 minutes ago
        coEvery { syncUseCase() } returns flowOf(state)
        val vm = IoTSettingsViewModel(syncUseCase)
        advanceUntilIdle()

        val content = vm.uiState.value as IoTSettingsUiState.Content
        assertEquals("Hace 2 min", content.lastSyncTime)
    }

    @Test
    fun `lastSyncTime shows hours for older timestamp`() = runTest {
        val now = System.currentTimeMillis()
        val state = fakeSyncState(timestamp = now - 7_200_000) // 2 hours ago
        coEvery { syncUseCase() } returns flowOf(state)
        val vm = IoTSettingsViewModel(syncUseCase)
        advanceUntilIdle()

        val content = vm.uiState.value as IoTSettingsUiState.Content
        assertEquals("Hace 2 h", content.lastSyncTime)
    }

    @Test
    fun `syncNow calls use case syncNow method`() = runTest {
        coEvery { syncUseCase() } returns flowOf(fakeSyncState())
        coEvery { syncUseCase.syncNow() } returns fakeSyncState()
        val vm = IoTSettingsViewModel(syncUseCase)
        advanceUntilIdle()

        vm.syncNow()
        advanceUntilIdle()
        coVerify(exactly = 1) { syncUseCase.syncNow() }
    }
}
