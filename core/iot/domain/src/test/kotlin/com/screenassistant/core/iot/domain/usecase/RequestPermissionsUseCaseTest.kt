package com.screenassistant.core.iot.domain.usecase

import com.screenassistant.core.iot.domain.model.auto.CarPermissions
import com.screenassistant.core.iot.domain.repository.CarAppRepository
import com.screenassistant.core.iot.domain.repository.CarPermission
import com.screenassistant.core.iot.domain.repository.HealthConnectRepository
import com.screenassistant.core.iot.domain.repository.HealthRecordType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
class RequestPermissionsUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var healthRepository: HealthConnectRepository
    private lateinit var carRepository: CarAppRepository
    private lateinit var useCase: RequestPermissionsUseCase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        healthRepository = mockk()
        carRepository = mockk()
        useCase = RequestPermissionsUseCase(healthRepository, carRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `requestAllEssential returns CombinedPermissionsResult`() = runTest {
        coEvery { healthRepository.requestPermissions(any()) } returns HealthRecordType.essentialTypes
        coEvery { carRepository.requestCarPermissions(any()) } returns CarPermissions(
            microphone = true,
            location = true,
        )

        val result = useCase.requestAllEssential()
        assertTrue(result is CombinedPermissionsResult)
    }

    @Test
    fun `allEssentialGranted is true when all permissions granted`() = runTest {
        coEvery { healthRepository.requestPermissions(any()) } returns HealthRecordType.essentialTypes
        coEvery { carRepository.requestCarPermissions(any()) } returns CarPermissions(
            microphone = true,
            location = true,
        )

        val result = useCase.requestAllEssential()
        assertTrue(result.allEssentialGranted)
    }

    @Test
    fun `allEssentialGranted is false when health permissions missing`() = runTest {
        coEvery { healthRepository.requestPermissions(any()) } returns emptySet()
        coEvery { carRepository.requestCarPermissions(any()) } returns CarPermissions(
            microphone = true,
            location = true,
        )

        val result = useCase.requestAllEssential()
        assertFalse(result.allEssentialGranted)
    }

    @Test
    fun `allEssentialGranted is false when car permissions missing`() = runTest {
        coEvery { healthRepository.requestPermissions(any()) } returns HealthRecordType.essentialTypes
        coEvery { carRepository.requestCarPermissions(any()) } returns CarPermissions()

        val result = useCase.requestAllEssential()
        assertFalse(result.allEssentialGranted)
    }

    @Test
    fun `missingHealth contains only missing health types`() = runTest {
        val granted = setOf(HealthRecordType.STEPS, HealthRecordType.HEART_RATE)
        coEvery { healthRepository.requestPermissions(any()) } returns granted
        coEvery { carRepository.requestCarPermissions(any()) } returns CarPermissions(
            microphone = true,
            location = true,
        )

        val result = useCase.requestAllEssential()
        val missing = result.missingHealth
        assertFalse(missing.contains(HealthRecordType.STEPS))
        assertFalse(missing.contains(HealthRecordType.HEART_RATE))
        assertTrue(missing.contains(HealthRecordType.SLEEP_SESSION))
        assertTrue(missing.contains(HealthRecordType.BLOOD_OXYGEN))
        assertTrue(missing.contains(HealthRecordType.WEIGHT))
    }

    @Test
    fun `missingCar contains only missing car permissions`() = runTest {
        coEvery { healthRepository.requestPermissions(any()) } returns HealthRecordType.essentialTypes
        coEvery { carRepository.requestCarPermissions(any()) } returns CarPermissions(microphone = true)

        val result = useCase.requestAllEssential()
        assertTrue(result.missingCar.contains(CarPermission.LOCATION))
        assertFalse(result.missingCar.contains(CarPermission.MICROPHONE))
    }

    @Test
    fun `summary shows OK when all granted`() = runTest {
        coEvery { healthRepository.requestPermissions(any()) } returns HealthRecordType.essentialTypes
        coEvery { carRepository.requestCarPermissions(any()) } returns CarPermissions(
            microphone = true,
            location = true,
        )

        val result = useCase.requestAllEssential()
        val summary = result.summary()
        assertTrue(summary.contains("Health: OK"))
        assertTrue(summary.contains("Car: OK"))
    }

    @Test
    fun `summary shows FALTAN when permissions missing`() = runTest {
        coEvery { healthRepository.requestPermissions(any()) } returns emptySet()
        coEvery { carRepository.requestCarPermissions(any()) } returns CarPermissions()

        val result = useCase.requestAllEssential()
        val summary = result.summary()
        assertTrue(summary.contains("Health: FALTAN"))
        assertTrue(summary.contains("Car: FALTAN"))
    }

    @Test
    fun `invoke delegates to health repository with essential types`() = runTest {
        coEvery { healthRepository.requestPermissions(any()) } returns HealthRecordType.essentialTypes

        val result = useCase()
        assertEquals(HealthRecordType.essentialTypes, result)
    }

    @Test
    fun `requestCarPermissions returns granted set from CarPermissions data class`() = runTest {
        coEvery { carRepository.requestCarPermissions(any()) } returns CarPermissions(
            microphone = true,
            location = false,
            contacts = true,
        )

        val result = useCase.requestCarPermissions()
        assertTrue(result.contains(CarPermission.MICROPHONE))
        assertFalse(result.contains(CarPermission.LOCATION))
        assertTrue(result.contains(CarPermission.CONTACTS))
    }
}
