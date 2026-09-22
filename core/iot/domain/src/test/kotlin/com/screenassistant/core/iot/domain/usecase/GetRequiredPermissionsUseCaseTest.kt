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
class GetRequiredPermissionsUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var healthRepository: HealthConnectRepository
    private lateinit var carRepository: CarAppRepository
    private lateinit var useCase: GetRequiredPermissionsUseCase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        healthRepository = mockk()
        carRepository = mockk()
        useCase = GetRequiredPermissionsUseCase(healthRepository, carRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `invoke returns PermissionsReport`() = runTest {
        coEvery { healthRepository.getGrantedPermissions() } returns HealthRecordType.essentialTypes
        coEvery { carRepository.getCarPermissions() } returns CarPermissions(
            microphone = true,
            location = true,
        )

        val report = useCase()
        assertTrue(report is PermissionsReport)
    }

    @Test
    fun `hasAllEssentialPermissions true when all granted`() = runTest {
        coEvery { healthRepository.getGrantedPermissions() } returns HealthRecordType.essentialTypes
        coEvery { carRepository.getCarPermissions() } returns CarPermissions(
            microphone = true,
            location = true,
        )

        val report = useCase()
        assertTrue(report.hasAllEssentialPermissions)
    }

    @Test
    fun `hasAllEssentialPermissions false when health missing`() = runTest {
        coEvery { healthRepository.getGrantedPermissions() } returns emptySet()
        coEvery { carRepository.getCarPermissions() } returns CarPermissions(
            microphone = true,
            location = true,
        )

        val report = useCase()
        assertFalse(report.hasAllEssentialPermissions)
    }

    @Test
    fun `hasAllEssentialPermissions false when car missing`() = runTest {
        coEvery { healthRepository.getGrantedPermissions() } returns HealthRecordType.essentialTypes
        coEvery { carRepository.getCarPermissions() } returns CarPermissions()

        val report = useCase()
        assertFalse(report.hasAllEssentialPermissions)
    }

    @Test
    fun `health grantPercentage edge case all granted`() = runTest {
        coEvery { healthRepository.getGrantedPermissions() } returns HealthRecordType.all
        coEvery { carRepository.getCarPermissions() } returns CarPermissions()

        val report = useCase()
        assertEquals(100, report.healthPermissions.grantPercentage)
    }

    @Test
    fun `health grantPercentage edge case none granted`() = runTest {
        coEvery { healthRepository.getGrantedPermissions() } returns emptySet()
        coEvery { carRepository.getCarPermissions() } returns CarPermissions()

        val report = useCase()
        assertEquals(0, report.healthPermissions.grantPercentage)
    }

    @Test
    fun `health grantPercentage edge case partial granted`() = runTest {
        coEvery { healthRepository.getGrantedPermissions() } returns setOf(
            HealthRecordType.STEPS,
            HealthRecordType.HEART_RATE,
            HealthRecordType.SLEEP_SESSION,
        )
        coEvery { carRepository.getCarPermissions() } returns CarPermissions()

        val report = useCase()
        val totalTypes = (report.healthPermissions.requiredTypes + report.healthPermissions.optionalTypes).size
        val expected = (3 * 100) / totalTypes
        assertEquals(expected, report.healthPermissions.grantPercentage)
    }

    @Test
    fun `car grantPercentage edge case all granted`() = runTest {
        coEvery { healthRepository.getGrantedPermissions() } returns HealthRecordType.essentialTypes
        coEvery { carRepository.getCarPermissions() } returns CarPermissions(
            microphone = true,
            location = true,
            contacts = true,
            sms = true,
            callLogs = true,
            phoneState = true,
            vehicleData = true,
            isPassenger = true,
        )

        val report = useCase()
        assertEquals(100, report.carPermissions.grantPercentage)
    }

    @Test
    fun `car grantPercentage edge case none granted`() = runTest {
        coEvery { healthRepository.getGrantedPermissions() } returns HealthRecordType.essentialTypes
        coEvery { carRepository.getCarPermissions() } returns CarPermissions()

        val report = useCase()
        assertEquals(0, report.carPermissions.grantPercentage)
    }

    @Test
    fun `missingEssential contains health and car missing items`() = runTest {
        coEvery { healthRepository.getGrantedPermissions() } returns setOf(HealthRecordType.STEPS)
        coEvery { carRepository.getCarPermissions() } returns CarPermissions(microphone = true)

        val report = useCase()
        val missing = report.missingEssential
        assertTrue(missing.any { it.startsWith("Health:") })
        assertTrue(missing.any { it.startsWith("Car:") })
    }

    @Test
    fun `summary shows OK when all essential granted`() = runTest {
        coEvery { healthRepository.getGrantedPermissions() } returns HealthRecordType.essentialTypes
        coEvery { carRepository.getCarPermissions() } returns CarPermissions(
            microphone = true,
            location = true,
        )

        val report = useCase()
        val summary = report.summary()
        assertTrue(summary.contains("Health: OK"))
        assertTrue(summary.contains("Car: OK"))
    }

    @Test
    fun `summary shows FALTAN when permissions missing`() = runTest {
        coEvery { healthRepository.getGrantedPermissions() } returns emptySet()
        coEvery { carRepository.getCarPermissions() } returns CarPermissions()

        val report = useCase()
        val summary = report.summary()
        assertTrue(summary.contains("Health: FALTAN"))
        assertTrue(summary.contains("Car: FALTAN"))
    }

    @Test
    fun `carPermissionsReport missingRequired contains only ungranted required`() = runTest {
        coEvery { healthRepository.getGrantedPermissions() } returns HealthRecordType.essentialTypes
        coEvery { carRepository.getCarPermissions() } returns CarPermissions(microphone = true)

        val report = useCase()
        assertTrue(report.carPermissions.missingRequired.contains(CarPermission.LOCATION))
        assertFalse(report.carPermissions.missingRequired.contains(CarPermission.MICROPHONE))
    }

    @Test
    fun `healthPermissionsReport hasAllRequired true when essential granted`() = runTest {
        coEvery { healthRepository.getGrantedPermissions() } returns HealthRecordType.essentialTypes
        coEvery { carRepository.getCarPermissions() } returns CarPermissions()

        val report = useCase()
        assertTrue(report.healthPermissions.hasAllRequired)
    }

    @Test
    fun `healthPermissionsReport hasAllRequired false when essential missing`() = runTest {
        coEvery { healthRepository.getGrantedPermissions() } returns setOf(HealthRecordType.STEPS)
        coEvery { carRepository.getCarPermissions() } returns CarPermissions()

        val report = useCase()
        assertFalse(report.healthPermissions.hasAllRequired)
    }
}
