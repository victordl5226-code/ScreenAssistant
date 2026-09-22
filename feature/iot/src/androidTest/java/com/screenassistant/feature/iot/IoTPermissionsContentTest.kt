package com.screenassistant.feature.iot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.screenassistant.core.iot.domain.repository.CarPermission
import com.screenassistant.core.iot.domain.repository.HealthRecordType
import com.screenassistant.core.iot.domain.usecase.CarPermissionsReport
import com.screenassistant.core.iot.domain.usecase.HealthPermissionsReport
import com.screenassistant.core.iot.domain.usecase.PermissionsReport
import com.screenassistant.core.ui.theme.ScreenAssistantTheme
import com.screenassistant.feature.iot.ui.common.IoTLoadingIndicator
import com.screenassistant.feature.iot.ui.common.IoTErrorCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests de los componentes de la pantalla de permisos IoT.
 *
 * PermissionItem y PermissionSectionHeader son privados; se testean
 * los estados de carga, error, y el modelo PermissionsReport.
 */
@RunWith(AndroidJUnit4::class)
class IoTPermissionsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun createReport(
        healthRequired: Set<HealthRecordType> = setOf(HealthRecordType.STEPS),
        healthOptional: Set<HealthRecordType> = setOf(HealthRecordType.HEART_RATE),
        healthGranted: Set<HealthRecordType> = setOf(HealthRecordType.STEPS),
        carRequired: Set<CarPermission> = setOf(CarPermission.LOCATION),
        carOptional: Set<CarPermission> = setOf(CarPermission.MICROPHONE),
        carGranted: Set<CarPermission> = setOf(CarPermission.LOCATION),
    ) = PermissionsReport(
        healthPermissions = HealthPermissionsReport(
            requiredTypes = healthRequired,
            optionalTypes = healthOptional,
            grantedTypes = healthGranted,
        ),
        carPermissions = CarPermissionsReport(
            requiredPermissions = carRequired,
            optionalPermissions = carOptional,
            grantedPermissions = carGranted,
        ),
    )

    // ── Loading State ────────────────────────────────────────────────

    @Test
    fun loadingStateMuestraLoadingIndicator() {
        compose.setContent {
            ScreenAssistantTheme {
                IoTLoadingIndicator()
            }
        }

        compose.onNodeWithText("Cargando...").assertIsDisplayed()
    }

    // ── Error State ──────────────────────────────────────────────────

    @Test
    fun errorStateMuestraErrorCardConMensaje() {
        compose.setContent {
            ScreenAssistantTheme {
                IoTErrorCard(
                    message = "Error al cargar permisos",
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("Error").assertIsDisplayed()
        compose.onNodeWithText("Error al cargar permisos").assertIsDisplayed()
    }

    // ── PermissionsReport Model ──────────────────────────────────────

    @Test
    fun reportTodosLosPermisosConcedidosHasAllTrue() {
        val report = createReport(
            healthGranted = setOf(HealthRecordType.STEPS, HealthRecordType.HEART_RATE),
            carGranted = setOf(CarPermission.LOCATION, CarPermission.MICROPHONE),
        )

        assertTrue(report.hasAllEssentialPermissions)
        assertTrue(report.healthPermissions.hasAllRequired)
        assertTrue(report.carPermissions.hasAllRequired)
    }

    @Test
    fun reportFaltanPermisosSaludHasAllFalse() {
        val report = createReport(
            healthGranted = emptySet(),
            carGranted = setOf(CarPermission.LOCATION, CarPermission.MICROPHONE),
        )

        assertFalse(report.hasAllEssentialPermissions)
        assertFalse(report.healthPermissions.hasAllRequired)
    }

    @Test
    fun reportFaltanPermisosCarHasAllFalse() {
        val report = createReport(
            healthGranted = setOf(HealthRecordType.STEPS, HealthRecordType.HEART_RATE),
            carGranted = emptySet(),
        )

        assertFalse(report.hasAllEssentialPermissions)
        assertFalse(report.carPermissions.hasAllRequired)
    }

    @Test
    fun reportMissingRequiredHealth() {
        val report = createReport(
            healthRequired = setOf(HealthRecordType.STEPS, HealthRecordType.HEART_RATE),
            healthGranted = setOf(HealthRecordType.STEPS),
        )

        assertEquals(1, report.healthPermissions.missingRequired.size)
        assertTrue(HealthRecordType.HEART_RATE in report.healthPermissions.missingRequired)
    }

    @Test
    fun reportMissingRequiredCar() {
        val report = createReport(
            carRequired = setOf(CarPermission.LOCATION, CarPermission.VEHICLE_DATA),
            carGranted = setOf(CarPermission.LOCATION),
        )

        assertEquals(1, report.carPermissions.missingRequired.size)
        assertTrue(CarPermission.VEHICLE_DATA in report.carPermissions.missingRequired)
    }

    @Test
    fun reportSummaryMuestraFormatoCorrecto() {
        val report = createReport(
            healthRequired = setOf(HealthRecordType.STEPS),
            healthGranted = setOf(HealthRecordType.STEPS),
            carRequired = setOf(CarPermission.LOCATION),
            carGranted = emptySet(),
        )

        val summary = report.summary()
        assertTrue(summary.contains("Health: OK"))
        assertTrue(summary.contains("Car: FALTAN"))
    }

    @Test
    fun reportMissingEssentialContieneAmbasSecciones() {
        val report = createReport(
            healthRequired = setOf(HealthRecordType.STEPS),
            healthGranted = emptySet(),
            carRequired = setOf(CarPermission.LOCATION),
            carGranted = emptySet(),
        )

        val missing = report.missingEssential
        assertTrue(missing.any { it.startsWith("Health:") })
        assertTrue(missing.any { it.startsWith("Car:") })
    }

    @Test
    fun reportGrantPercentageCalculado() {
        val report = createReport(
            healthRequired = setOf(HealthRecordType.STEPS, HealthRecordType.HEART_RATE),
            healthOptional = setOf(HealthRecordType.DISTANCE),
            healthGranted = setOf(HealthRecordType.STEPS),
        )

        assertEquals(33, report.healthPermissions.grantPercentage)
    }
}
