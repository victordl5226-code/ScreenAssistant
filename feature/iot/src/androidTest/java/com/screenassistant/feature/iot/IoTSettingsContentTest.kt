package com.screenassistant.feature.iot

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.screenassistant.core.ui.theme.ScreenAssistantTheme
import com.screenassistant.feature.iot.ui.common.IoTLoadingIndicator
import com.screenassistant.feature.iot.ui.common.IoTErrorCard
import com.screenassistant.feature.iot.ui.settings.CarConnectionCard
import com.screenassistant.feature.iot.ui.settings.DeviceStatusCard
import com.screenassistant.feature.iot.ui.settings.HealthMetricsSummary
import com.screenassistant.feature.iot.ui.settings.IoTSectionHeader
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests de los componentes públicos que componen IoTSettingsContent.
 *
 * IoTSettingsContent es un composable privado, por lo que se testean
 * los bloques públicos que lo integran: IoTSectionHeader, DeviceStatusCard,
 * HealthMetricsSummary y CarConnectionCard.
 */
@RunWith(AndroidJUnit4::class)
class IoTSettingsContentTest {

    @get:Rule
    val compose = createComposeRule()

    // ── Section Headers ──────────────────────────────────────────────

    @Test
    fun sectionHeadersSeMuestranLasSeccionesDelDashboard() {
        compose.setContent {
            ScreenAssistantTheme {
                IoTSectionHeader(
                    title = "Dispositivos Matter",
                    icon = androidx.compose.material.icons.Icons.Default.Sensors,
                    count = "2/3 en línea",
                )
            }
        }

        compose.onNodeWithText("Dispositivos Matter").assertIsDisplayed()
        compose.onNodeWithText("2/3 en línea").assertIsDisplayed()
    }

    @Test
    fun sectionHeaderDatosDeSaludSeMuestra() {
        compose.setContent {
            ScreenAssistantTheme {
                IoTSectionHeader(
                    title = "Datos de Salud",
                    icon = androidx.compose.material.icons.Icons.Default.Favorite,
                )
            }
        }

        compose.onNodeWithText("Datos de Salud").assertIsDisplayed()
    }

    @Test
    fun sectionHeaderConexionVehiculoSeMuestra() {
        compose.setContent {
            ScreenAssistantTheme {
                IoTSectionHeader(
                    title = "Conexión del Vehículo",
                    icon = androidx.compose.material.icons.Icons.Default.DateRange,
                )
            }
        }

        compose.onNodeWithText("Conexión del Vehículo").assertIsDisplayed()
    }

    // ── Matter Device Cards ──────────────────────────────────────────

    @Test
    fun deviceStatusCardMatterDevicesMuestraEstadoOnline() {
        compose.setContent {
            ScreenAssistantTheme {
                DeviceStatusCard(
                    title = "Dispositivos conectados",
                    subtitle = "2 de 3 en línea",
                    isOnline = true,
                )
            }
        }

        compose.onNodeWithText("Dispositivos conectados").assertIsDisplayed()
        compose.onNodeWithText("2 de 3 en línea").assertIsDisplayed()
        compose.onNodeWithContentDescription("Estado: en línea").assertIsDisplayed()
    }

    @Test
    fun deviceStatusCardMatterDevicesSinDispositivos() {
        compose.setContent {
            ScreenAssistantTheme {
                DeviceStatusCard(
                    title = "Dispositivos conectados",
                    subtitle = "Sin dispositivos configurados",
                    isOnline = false,
                    isEmpty = true,
                )
            }
        }

        compose.onNodeWithText("Sin dispositivos configurados").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sin dispositivos").assertIsDisplayed()
    }

    // ── Health Metrics Section ────────────────────────────────────────

    @Test
    fun healthMetricsConDatosMuestraResumen() {
        compose.setContent {
            ScreenAssistantTheme {
                HealthMetricsSummary(
                    summary = "Pasos: 5000 | Calorías: 320 | Sueño: 7h",
                )
            }
        }

        compose.onNodeWithText("Resumen de Salud").assertIsDisplayed()
        compose.onNodeWithText("Pasos: 5000 | Calorías: 320 | Sueño: 7h").assertIsDisplayed()
    }

    @Test
    fun healthMetricsSinDatosMuestraEmptyState() {
        compose.setContent {
            ScreenAssistantTheme {
                HealthMetricsSummary(summary = "Sin datos recientes")
            }
        }

        compose.onNodeWithText("Sin datos de salud disponibles").assertIsDisplayed()
    }

    // ── Car Connection Status ─────────────────────────────────────────

    @Test
    fun carConnectionConectadoMuestraConectado() {
        compose.setContent {
            ScreenAssistantTheme {
                CarConnectionCard(isConnected = true)
            }
        }

        compose.onNodeWithText("Conectado").assertIsDisplayed()
        compose.onNodeWithContentDescription("Coche conectado").assertIsDisplayed()
    }

    @Test
    fun carConnectionDesconectadoMuestraDesconectado() {
        compose.setContent {
            ScreenAssistantTheme {
                CarConnectionCard(isConnected = false)
            }
        }

        compose.onNodeWithText("Desconectado").assertIsDisplayed()
        compose.onNodeWithContentDescription("Coche desconectado").assertIsDisplayed()
    }

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
                    message = "Error de sincronización",
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("Error").assertIsDisplayed()
        compose.onNodeWithText("Error de sincronización").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").assertIsDisplayed()
    }
}
