package com.screenassistant

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.screenassistant.core.ui.theme.ScreenAssistantTheme
import com.screenassistant.ui.apikey.UiState
import com.screenassistant.ui.puente.PuenteUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lote 12 (M13): UI tests de MainScreen — composable PURO (sin Hilt; los VMs
 * viven en MainActivity, fuera de alcance). createAndroidComposeRule<ComponentActivity>
 * (QA-5: usa LocalContext/LifecycleResumeEffect → necesita actividad real) y
 * SIEMPRE envuelto en ScreenAssistantTheme (QA-4).
 *
 * RESTRICCIÓN del diseño (R9): NO se asserta el badge de accesibilidad (lee
 * Settings.Secure del dispositivo real, estado no determinista) — solo elementos
 * estables de la pantalla.
 */
@RunWith(AndroidJUnit4::class)
class MainScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun setContent(
        onStartService: () -> Unit = {},
        onStopService: () -> Unit = {}
    ) {
        compose.setContent {
            ScreenAssistantTheme {
                MainScreen(
                    onStartService = onStartService,
                    onStopService = onStopService,
                    onRequestExtraPermissions = {},
                    apiKeyUiState = UiState(),
                    onApiKeyInputChange = {},
                    onApiKeySave = {},
                    onApiKeyClear = {},
                    puenteUiState = PuenteUiState(),
                    onPuenteTokenChange = {},
                    onPuenteUrlFlagChange = {},
                    onPuentePackageRespuestaChange = {},
                    onPuenteAutoRemoteKeyChange = {},
                    onPuenteSave = {},
                    onPuenteClearKey = {}
                )
            }
        }
    }

    @Test
    fun `render muestra titulo secciones y botones de servicio`() {
        setContent()

        compose.onNodeWithText("Configuración de Screen Assistant").assertIsDisplayed()
        compose.onNodeWithText("API Key (Gemini)").assertIsDisplayed()
        compose.onNodeWithText("Puente Tasker (configuración)").assertIsDisplayed()
        // Botones bajo el scroll vertical: se scrollean al viewport y se verifican visibles.
        compose.onNodeWithText("Iniciar Asistente Flotante").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Detener Asistente Flotante").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `click en iniciar asistente invoca onStartService`() {
        var started = false
        setContent(onStartService = { started = true })

        compose.onNodeWithText("Iniciar Asistente Flotante").performScrollTo().performClick()

        assertTrue(started)
    }

    @Test
    fun `click en detener asistente invoca onStopService`() {
        var stopped = false
        setContent(onStopService = { stopped = true })

        compose.onNodeWithText("Detener Asistente Flotante").performScrollTo().performClick()

        assertTrue(stopped)
    }
}
