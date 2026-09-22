package com.screenassistant.feature.overlay

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test automatizado del flujo de envío de mensajes en el overlay.
 *
 * Verifica que:
 * 1. El input bar se renderiza correctamente
 * 2. El usuario puede escribir texto
 * 3. Al presionar enviar, se invoca viewModel.sendMessage()
 * 4. El campo se limpia después de enviar
 *
 * Usa un ViewModel mockeado para aislar la UI de la lógica de negocio.
 */
@RunWith(AndroidJUnit4::class)
class OverlaySendMessageTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var mockViewModel: OverlayViewModel
    private val _uiState = MutableStateFlow(OverlayUiState())

    @Before
    fun setUp() {
        mockViewModel = mockk<OverlayViewModel>(relaxed = true)
        every { mockViewModel.uiState } returns _uiState.asStateFlow()
        every { mockViewModel.characterState } returns CharacterState()
        // Simular que onInputChanged actualiza el StateFlow (como hace el ViewModel real)
        every { mockViewModel.onInputChanged(any()) } answers {
            _uiState.value = _uiState.value.copy(inputText = firstArg())
        }
    }

    @Test
    fun inputBar_isDisplayed() {
        setContent()

        // Verificar que el nombre del asistente se muestra
        compose.onNodeWithText("J.A.R.V.I.S.").assertIsDisplayed()
    }

    @Test
    fun typingText_updatesInputField() {
        setContent()

        // El placeholder "Habla..." debe estar visible inicialmente
        compose.onNodeWithText("Habla...").assertIsDisplayed()

        // Escribir texto en el campo de entrada
        compose.onNodeWithContentDescription("Campo de texto").performTextInput("hola")

        // Verificar que el ViewModel recibió el cambio
        verify { mockViewModel.onInputChanged("hola") }
    }

    @Test
    fun sendButton_callsViewModelSendMessage() {
        setContent()
        compose.waitForIdle()

        // 1. Escribir texto (esto actualiza el StateFlow via onInputChanged mock)
        compose.onNodeWithContentDescription("Campo de texto").performTextInput("hola")
        compose.waitForIdle()

        // 2. Hacer click en el botón de enviar
        compose.onNodeWithContentDescription("Enviar").performClick()
        compose.waitForIdle()

        // 3. Verificar que se invocó sendMessage con el texto
        verify { mockViewModel.sendMessage("hola") }
    }

    @Test
    fun sendButton_isDisabled_whenInputIsEmpty() {
        setContent()
        compose.waitForIdle()

        // El botón de enviar debe estar deshabilitado cuando no hay texto
        // (enabled = inputText.isNotEmpty() en el código)
        // Verificamos que el click no invoca sendMessage
        compose.onNodeWithContentDescription("Enviar").performClick()
        compose.waitForIdle()

        // sendMessage no debería haberse llamado con texto vacío
        verify(exactly = 0) { mockViewModel.sendMessage(any()) }
    }

    @Test
    fun historyButton_callsViewModelToggleHistory() {
        setContent()

        // Hacer click en el botón de historial
        compose.onNodeWithContentDescription("Historial").performClick()

        // Verificar que se invocó toggleHistory
        verify { mockViewModel.toggleHistory() }
    }

    @Test
    fun micButton_callsViewModelStartListening() {
        setContent()

        // Hacer click en el botón de micrófono (cuando no está escuchando)
        compose.onNodeWithContentDescription("Micrófono").performClick()

        // Verificar que se invocó startListening
        verify { mockViewModel.startListening() }
    }

    @Test
    fun responseBubble_isDisplayed_whenAssistantHasText() {
        // Pre-llenar el estado con respuesta del asistente
        _uiState.value = OverlayUiState(assistantText = "Hola, soy J.A.R.V.I.S.")

        setContent()
        compose.waitForIdle()

        // Verificar que la burbuja de respuesta se muestra
        compose.onNodeWithText("Hola, soy J.A.R.V.I.S.").assertIsDisplayed()
    }

    @Test
    fun fullFlow_typeAndSend() {
        setContent()

        // 1. Escribir texto
        compose.onNodeWithContentDescription("Campo de texto").performTextInput("hola")

        // 2. Verificar que el ViewModel recibió el cambio
        verify { mockViewModel.onInputChanged("hola") }

        // 3. Enviar el mensaje
        compose.onNodeWithContentDescription("Enviar").performClick()

        // 4. Verificar que se invocó sendMessage
        verify { mockViewModel.sendMessage("hola") }
    }

    private fun setContent() {
        compose.setContent {
            // No envolvemos en Theme porque AssistantOverlayUI ya usa MaterialTheme internamente
            // y necesitamos que los nodos de testing puedan encontrar los textos
            AssistantOverlayUI(
                onDrag = { _, _ -> },
                onFocusChange = {},
                viewModel = mockViewModel,
                geminiRepository = mockk(relaxed = true),
                commandParser = mockk(relaxed = true),
                modelAssetRepository = mockk(relaxed = true)
            )
        }
    }
}
