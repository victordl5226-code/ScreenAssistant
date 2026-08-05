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
 * Lote 12 (M13): UI tests de ApiKeySection — composable PURO (estado+callbacks
 * por parámetro, sin Hilt). createComposeRule sin activity propia (QA-5:
 * ui-test-manifest registra ComponentActivity en debug) y SIEMPRE envuelto en
 * ScreenAssistantTheme (QA-4: stringResource + MaterialTheme lo requieren).
 */
@RunWith(AndroidJUnit4::class)
class ApiKeySectionTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `estado no configurado muestra badge y boton guardar deshabilitado`() {
        compose.setContent {
            ScreenAssistantTheme {
                ApiKeySection(uiState = UiState(), onInputChange = {}, onSaveKey = {}, onClearKey = {})
            }
        }

        compose.onNodeWithText("No configurada").assertIsDisplayed()
        compose.onNodeWithText("Guardar").assertIsNotEnabled()
    }

    @Test
    fun `escribir en el campo notifica onInputChange`() {
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
    fun `input no vacio habilita guardar y el click envia onSaveKey con el texto`() {
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
    fun `estado configurado muestra key enmascarada y boton quitar`() {
        compose.setContent {
            ScreenAssistantTheme {
                ApiKeySection(
                    uiState = UiState(isConfigured = true, maskedKey = "•••• 1234"),
                    onInputChange = {},
                    onSaveKey = {},
                    onClearKey = {}
                )
            }
        }

        compose.onNodeWithText("Configurada: •••• 1234").assertIsDisplayed()
        compose.onNodeWithText("Quitar").assertIsDisplayed()
    }

    @Test
    fun `el dialogo de quitar confirma con onClearKey y cancelar no notifica`() {
        var cleared = false
        compose.setContent {
            ScreenAssistantTheme {
                ApiKeySection(
                    uiState = UiState(isConfigured = true, maskedKey = "•••• 1234"),
                    onInputChange = {},
                    onSaveKey = {},
                    onClearKey = { cleared = true }
                )
            }
        }

        // Confirmar → onClearKey
        compose.onNodeWithText("Quitar").performClick()
        compose.onNodeWithText("¿Quitar la API key?").assertIsDisplayed()
        compose.onNodeWithText("Sí, quitar").performClick()
        assertTrue(cleared)

        // Cancelar → sin callback
        cleared = false
        compose.onNodeWithText("Quitar").performClick()
        compose.onNodeWithText("Cancelar").performClick()
        assertFalse(cleared)
    }
}
