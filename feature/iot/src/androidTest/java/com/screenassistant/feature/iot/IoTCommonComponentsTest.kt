package com.screenassistant.feature.iot

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.screenassistant.core.ui.theme.ScreenAssistantTheme
import com.screenassistant.feature.iot.ui.common.IoTErrorCard
import com.screenassistant.feature.iot.ui.common.IoTLoadingIndicator
import com.screenassistant.feature.iot.ui.settings.CarConnectionCard
import com.screenassistant.feature.iot.ui.settings.DeviceStatusCard
import com.screenassistant.feature.iot.ui.settings.HealthMetricsSummary
import com.screenassistant.feature.iot.ui.settings.IoTSectionHeader
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests de composables públicos comunes del módulo IoT.
 *
 * createComposeRule sin activity propia (ui-test-manifest registra
 * ComponentActivity en debug). SIEMPRE envuelto en ScreenAssistantTheme.
 */
@RunWith(AndroidJUnit4::class)
class IoTCommonComponentsTest {

    @get:Rule
    val compose = createComposeRule()

    // ── IoTLoadingIndicator ──────────────────────────────────────────

    @Test
    fun loadingIndicatorMuestraCirculoYTexto() {
        compose.setContent {
            ScreenAssistantTheme {
                IoTLoadingIndicator()
            }
        }

        compose.onNodeWithText("Cargando...").assertIsDisplayed()
    }

    // ── IoTErrorCard ─────────────────────────────────────────────────

    @Test
    fun errorCardMuestraIconoTituloYMensaje() {
        compose.setContent {
            ScreenAssistantTheme {
                IoTErrorCard(
                    message = "No se pudo conectar al servidor",
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Error").assertIsDisplayed()
        compose.onNodeWithText("Error").assertIsDisplayed()
        compose.onNodeWithText("No se pudo conectar al servidor").assertIsDisplayed()
    }

    @Test
    fun errorCardBotonReintentarDisparaCallback() {
        var retried = false
        compose.setContent {
            ScreenAssistantTheme {
                IoTErrorCard(
                    message = "Timeout",
                    onRetry = { retried = true },
                )
            }
        }

        compose.onNodeWithText("Reintentar").performClick()
        assertTrue(retried)
    }

    // ── IoTSectionHeader ─────────────────────────────────────────────

    @Test
    fun sectionHeaderConCountMuestraContador() {
        compose.setContent {
            ScreenAssistantTheme {
                IoTSectionHeader(
                    title = "Dispositivos Matter",
                    icon = androidx.compose.material.icons.Icons.Default.Sensors,
                    count = "3/5 en línea",
                )
            }
        }

        compose.onNodeWithText("Dispositivos Matter").assertIsDisplayed()
        compose.onNodeWithText("3/5 en línea").assertIsDisplayed()
    }

    // ── DeviceStatusCard ─────────────────────────────────────────────

    @Test
    fun deviceStatusCardOnlineMuestraIconoCheck() {
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
        compose.onNodeWithContentDescription("Estado: en línea").assertIsDisplayed()
    }

    @Test
    fun deviceStatusCardEmptyMuestraIconoSensors() {
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

        compose.onNodeWithContentDescription("Sin dispositivos").assertIsDisplayed()
    }

    // ── HealthMetricsSummary ──────────────────────────────────────────

    @Test
    fun healthMetricsConDatosMuestraResumen() {
        compose.setContent {
            ScreenAssistantTheme {
                HealthMetricsSummary(
                    summary = "Pasos: 8,420 | FC: 72 bpm | Sueño: 7h 30m",
                )
            }
        }

        compose.onNodeWithText("Resumen de Salud").assertIsDisplayed()
    }

    @Test
    fun healthMetricsSinDatosMuestraEmptyState() {
        compose.setContent {
            ScreenAssistantTheme {
                HealthMetricsSummary(summary = "")
            }
        }

        compose.onNodeWithText("Sin datos de salud disponibles").assertIsDisplayed()
    }

    // ── CarConnectionCard ─────────────────────────────────────────────

    @Test
    fun carConnectionCardConectadoMuestraTextoConectado() {
        compose.setContent {
            ScreenAssistantTheme {
                CarConnectionCard(isConnected = true)
            }
        }

        compose.onNodeWithText("Conectado").assertIsDisplayed()
        compose.onNodeWithContentDescription("Coche conectado").assertIsDisplayed()
    }

    @Test
    fun carConnectionCardDesconectadoMuestraTextoDesconectado() {
        compose.setContent {
            ScreenAssistantTheme {
                CarConnectionCard(isConnected = false)
            }
        }

        compose.onNodeWithText("Desconectado").assertIsDisplayed()
        compose.onNodeWithContentDescription("Coche desconectado").assertIsDisplayed()
    }
}
