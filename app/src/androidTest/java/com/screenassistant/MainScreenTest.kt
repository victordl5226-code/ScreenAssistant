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
 * UI tests de MainScreen — composable PURO (sin Hilt).
 * createAndroidComposeRule<ComponentActivity> y SIEMPRE envuelto en ScreenAssistantTheme.
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
                    onNavigateToLocalLlmSettings = {},
                    onNavigateToIotSettings = {},
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
    fun renderMuestraTituloYBotones() {
        setContent()

        compose.onNodeWithText("J.A.R.V.I.S.").assertIsDisplayed()
        compose.onNodeWithText("Iniciar Asistente Flotante").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Detener Asistente Flotante").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun clickIniciarAsistenteInvocaOnStartService() {
        var started = false
        setContent(onStartService = { started = true })

        compose.onNodeWithText("Iniciar Asistente Flotante").performScrollTo().performClick()

        assertTrue(started)
    }

    @Test
    fun clickDetenerAsistenteInvocaOnStopService() {
        var stopped = false
        setContent(onStopService = { stopped = true })

        compose.onNodeWithText("Detener Asistente Flotante").performScrollTo().performClick()

        assertTrue(stopped)
    }
}
