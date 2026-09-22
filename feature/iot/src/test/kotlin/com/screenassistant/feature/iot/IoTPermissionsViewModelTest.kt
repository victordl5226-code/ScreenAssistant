package com.screenassistant.feature.iot.ui.permissions

import com.screenassistant.core.iot.domain.usecase.GetRequiredPermissionsUseCase
import com.screenassistant.core.iot.domain.usecase.PermissionsReport
import com.screenassistant.core.iot.domain.usecase.HealthPermissionsReport
import com.screenassistant.core.iot.domain.usecase.CarPermissionsReport
import com.screenassistant.core.iot.domain.repository.HealthRecordType
import com.screenassistant.core.iot.domain.repository.CarPermission
import com.screenassistant.core.iot.domain.usecase.RequestPermissionsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class IoTPermissionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var getPermissionsUseCase: GetRequiredPermissionsUseCase
    private lateinit var requestPermissionsUseCase: RequestPermissionsUseCase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        getPermissionsUseCase = mockk()
        requestPermissionsUseCase = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakePermissionsReport(
        grantedHealth: Set<HealthRecordType> = emptySet(),
        grantedCar: Set<CarPermission> = emptySet(),
    ) = PermissionsReport(
        healthPermissions = HealthPermissionsReport(
            requiredTypes = HealthRecordType.essentialTypes,
            optionalTypes = HealthRecordType.all - HealthRecordType.essentialTypes,
            grantedTypes = grantedHealth,
        ),
        carPermissions = CarPermissionsReport(
            requiredPermissions = setOf(CarPermission.MICROPHONE, CarPermission.LOCATION),
            optionalPermissions = setOf(CarPermission.CONTACTS, CarPermission.SMS),
            grantedPermissions = grantedCar,
        ),
    )

    @Test
    fun `initial state is Loading`() = runTest {
        coEvery { getPermissionsUseCase() } returns fakePermissionsReport()
        val vm = IoTPermissionsViewModel(getPermissionsUseCase, requestPermissionsUseCase)
        assertTrue(vm.uiState.value is IoTPermissionsUiState.Loading)
    }

    @Test
    fun `emits Content after loading permissions`() = runTest {
        val report = fakePermissionsReport(grantedHealth = setOf(HealthRecordType.STEPS))
        coEvery { getPermissionsUseCase() } returns report
        val vm = IoTPermissionsViewModel(getPermissionsUseCase, requestPermissionsUseCase)
        advanceUntilIdle()

        val content = vm.uiState.value as IoTPermissionsUiState.Content
        assertTrue(HealthRecordType.STEPS in content.report.healthPermissions.grantedTypes)
    }

    @Test
    fun `emits Error when loading fails`() = runTest {
        coEvery { getPermissionsUseCase() } throws RuntimeException("load error")
        val vm = IoTPermissionsViewModel(getPermissionsUseCase, requestPermissionsUseCase)
        advanceUntilIdle()

        val error = vm.uiState.value as IoTPermissionsUiState.Error
        assertEquals("load error", error.message)
    }

    @Test
    fun `requestAllEssential reloads permissions after request`() = runTest {
        coEvery { getPermissionsUseCase() } returns fakePermissionsReport()
        coEvery { requestPermissionsUseCase.requestAllEssential() } returns mockk()
        val vm = IoTPermissionsViewModel(getPermissionsUseCase, requestPermissionsUseCase)
        advanceUntilIdle()

        vm.requestAllEssential()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is IoTPermissionsUiState.Content)
    }

    @Test
    fun `hasAllEssentialPermissions is false when missing`() = runTest {
        coEvery { getPermissionsUseCase() } returns fakePermissionsReport()
        val vm = IoTPermissionsViewModel(getPermissionsUseCase, requestPermissionsUseCase)
        advanceUntilIdle()

        val content = vm.uiState.value as IoTPermissionsUiState.Content
        assertFalse(content.report.hasAllEssentialPermissions)
    }

    @Test
    fun `hasAllEssentialPermissions is true when all granted`() = runTest {
        val report = fakePermissionsReport(
            grantedHealth = HealthRecordType.essentialTypes,
            grantedCar = setOf(CarPermission.MICROPHONE, CarPermission.LOCATION),
        )
        coEvery { getPermissionsUseCase() } returns report
        val vm = IoTPermissionsViewModel(getPermissionsUseCase, requestPermissionsUseCase)
        advanceUntilIdle()

        val content = vm.uiState.value as IoTPermissionsUiState.Content
        assertTrue(content.report.hasAllEssentialPermissions)
    }

    @Test
    fun `requestAllEssential sets isRequesting true during execution`() = runTest {
        coEvery { getPermissionsUseCase() } returns fakePermissionsReport()
        coEvery { requestPermissionsUseCase.requestAllEssential() } coAnswers {
            kotlinx.coroutines.delay(100)
            mockk()
        }
        val vm = IoTPermissionsViewModel(getPermissionsUseCase, requestPermissionsUseCase)
        advanceUntilIdle()

        vm.requestAllEssential()
        // Advance just enough to enter the launch block and set isRequesting
        testDispatcher.scheduler.advanceTimeBy(10)
        val content = vm.uiState.value as IoTPermissionsUiState.Content
        assertTrue(content.isRequesting)

        advanceUntilIdle()
        val finalContent = vm.uiState.value as IoTPermissionsUiState.Content
        assertFalse(finalContent.isRequesting)
    }

    @Test
    fun `requestAllEssential calls requestPermissionsUseCase`() = runTest {
        coEvery { getPermissionsUseCase() } returns fakePermissionsReport()
        coEvery { requestPermissionsUseCase.requestAllEssential() } returns mockk()
        val vm = IoTPermissionsViewModel(getPermissionsUseCase, requestPermissionsUseCase)
        advanceUntilIdle()

        vm.requestAllEssential()
        advanceUntilIdle()

        coVerify(exactly = 1) { requestPermissionsUseCase.requestAllEssential() }
    }

    @Test
    fun `requestAllEssential catches exceptions gracefully`() = runTest {
        coEvery { getPermissionsUseCase() } returns fakePermissionsReport()
        coEvery { requestPermissionsUseCase.requestAllEssential() } throws RuntimeException("permission dialog unavailable")
        val vm = IoTPermissionsViewModel(getPermissionsUseCase, requestPermissionsUseCase)
        advanceUntilIdle()

        vm.requestAllEssential()
        advanceUntilIdle()

        // Should still reload permissions after exception
        assertTrue(vm.uiState.value is IoTPermissionsUiState.Content)
    }

    @Test
    fun `requestAllEssential reloads permissions even after exception`() = runTest {
        coEvery { getPermissionsUseCase() } returns fakePermissionsReport()
        coEvery { requestPermissionsUseCase.requestAllEssential() } throws RuntimeException("no UI")
        val vm = IoTPermissionsViewModel(getPermissionsUseCase, requestPermissionsUseCase)
        advanceUntilIdle()

        vm.requestAllEssential()
        advanceUntilIdle()

        // getPermissionsUseCase should have been called twice: once in init, once after request
        coVerify(exactly = 2) { getPermissionsUseCase() }
    }
}
