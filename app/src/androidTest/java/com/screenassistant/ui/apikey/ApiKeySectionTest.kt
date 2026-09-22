package com.screenassistant.ui.apikey

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.screenassistant.core.ui.theme.ScreenAssistantTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests de ApiKeySection — composable PURO (estado+callbacks
 * por parametro, sin Hilt). createComposeRule sin activity propia y
 * SIEMPRE envuelto en ScreenAssistantTheme.
 */
@RunWith(AndroidJUnit4::class)
class ApiKeySectionTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun estadoNoConfiguradoMuestraBadgeYBotonDeshabilitado() {
        compose.setContent {
            ScreenAssistantTheme {
                ApiKeySection(uiState = UiState(), onInputChange = {}, onSaveKey = {}, onClearKey = {})
            }
        }

        compose.onNodeWithText("No configurada").assertIsDisplayed()
        compose.onNodeWithText("Guardar").assertIsNotEnabled()
    }

    @Test
    fun escribirEnCampoNotificaOnInputChange() {
        var input: String? = null
        compose.setContent {
            ScreenAssistantTheme {
                ApiKeySection(uiState = UiState(), onInputChange = { input = it }, onSaveKey = {}, onClearKey = {})
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("AIza123")

        assertEquals("AIza123", input)
    }

    @Test
    fun inputNoVacioHabilitaGuardarYClickEnviaSaveKey() {
        var uiState by mutableStateOf(UiState())
        var saved: String? = null
        compose.setContent {
            ScreenAssistantTheme {
                ApiKeySection(
                    uiState = uiState,
                    onInputChange = { uiState = uiState.copy(input = it) },
                    onSaveKey = { saved = it },
                    onClearKey = {}
                )
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("ABC123")
        compose.onNodeWithText("Guardar").assertIsEnabled()
        compose.onNodeWithText("Guardar").performClick()

        assertEquals("ABC123", saved)
    }

    @Test
    fun estadoConfiguradoMuestraKeyEnmascaradaYBotonQuitar() {
        compose.setContent {
            ScreenAssistantTheme {
                ApiKeySection(
                    uiState = UiState(isConfigured = true, maskedKey = "\u2022\u2022\u2022\u2022 1234"),
                    onInputChange = {},
                    onSaveKey = {},
                    onClearKey = {}
                )
            }
        }

        compose.onNodeWithText("Configurada: \u2022\u2022\u2022\u2022 1234").assertIsDisplayed()
        compose.onNodeWithText("Quitar").assertIsDisplayed()
    }

    @Test
    fun dialogoQuitarConfirmaConOnClearKeyYCancelarNoNotifica() {
        var cleared = false
        compose.setContent {
            ScreenAssistantTheme {
                ApiKeySection(
                    uiState = UiState(isConfigured = true, maskedKey = "\u2022\u2022\u2022\u2022 1234"),
                    onInputChange = {},
                    onSaveKey = {},
                    onClearKey = { cleared = true }
                )
            }
        }

        // Confirmar -> onClearKey
        compose.onNodeWithText("Quitar").performClick()
        compose.onNodeWithText("\u00BFQuitar la API key?").assertIsDisplayed()
        compose.onNodeWithText("S\u00ED, quitar").performClick()
        assertTrue(cleared)

        // Cancelar -> sin callback
        cleared = false
        compose.onNodeWithText("Quitar").performClick()
        compose.onNodeWithText("Cancelar").performClick()
        assertFalse(cleared)
    }
}
