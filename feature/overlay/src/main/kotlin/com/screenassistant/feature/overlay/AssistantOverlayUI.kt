package com.screenassistant.feature.overlay

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.screenassistant.core.domain.usecase.SystemCommandParser
import com.screenassistant.core.domain.repository.GeminiRepository
import com.screenassistant.feature.overlay.R
import kotlinx.coroutines.delay

@Composable
fun AssistantOverlayUI(
    onDrag: (Float, Float) -> Unit,
    onFocusChange: (Boolean) -> Unit = {},
    viewModel: OverlayViewModel,
    geminiRepository: GeminiRepository,
    commandParser: SystemCommandParser
) {
    val uiState by viewModel.uiState.collectAsState()
    val characterState = viewModel.characterState
    val context = LocalContext.current

    // Crear TTS y STT y asignarlos al ViewModel
    val ttsManager = remember {
        TextToSpeechManager(context,
            onStart = { characterState.animationState = AnimationState.SPEAKING },
            onDone = { characterState.animationState = AnimationState.IDLE }
        ).also { viewModel.textToSpeech = it }
    }

    val sttManager = remember {
        SpeechToTextManager(context,
            onResult = { text -> viewModel.onVoiceResult(text) },
            onPartialResult = { partial -> viewModel.onVoicePartialResult(partial) },
            onError = { error -> viewModel.onVoiceError(error) }
        ).also { viewModel.speechToText = it }
    }

    DisposableEffect(Unit) {
        characterState.animationState = AnimationState.GREETING
        onDispose {
            ttsManager.destroy()
            sttManager.destroy()
        }
    }

    // Inicio automático de conversación (Saludo/Bautizo)
    LaunchedEffect(Unit) {
        delay(1000)
        viewModel.sendMessage("ACTION_INIT_CONVERSATION")
    }

    // Temporizador de inactividad para dar vida al personaje
    LaunchedEffect(uiState.animationState, uiState.inputText) {
        if (uiState.animationState == AnimationState.IDLE) {
            delay(30000) // 30 segundos
            viewModel.onIdleTimeout()
            delay(30000) // Otros 30 segundos (Total 1 min)
            viewModel.onIdleTimeout()
        }
    }

    // Auto-ocultar burbuja de texto tras 10 segundos de inactividad
    LaunchedEffect(uiState.assistantText, uiState.animationState) {
        if (uiState.assistantText.isNotEmpty() &&
            (uiState.animationState == AnimationState.IDLE ||
             uiState.animationState == AnimationState.SQUATTING ||
             uiState.animationState == AnimationState.MEDITATING)) {
            delay(10000)
            viewModel.dismissBubble()
        }
    }

    // Auto-ocultar tarjeta de ayuda tras 15 segundos
    LaunchedEffect(uiState.showHelpCard) {
        if (uiState.showHelpCard) {
            delay(15000)
            viewModel.dismissBubble()
        }
    }

    // EL CONTENEDOR SE AJUSTA ESTRICTAMENTE AL CONTENIDO PARA NO BLOQUEAR LA PANTALLA
    Box(
        modifier = Modifier
            .wrapContentSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.wrapContentWidth()
        ) {
            // Burbuja de texto (Optimizada: Scrollable, Limitada y Descartable)
            if (uiState.assistantText.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp).copy(alpha = 0.92f),
                    shape = RoundedCornerShape(14.dp),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .widthIn(max = 150.dp)
                        .heightIn(max = 80.dp)
                        .animateContentSize()
                        .clickable { viewModel.dismissBubble() }
                ) {
                    val scrollState = rememberScrollState()
                    Text(
                        text = uiState.assistantText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(8.dp)
                            .verticalScroll(scrollState)
                    )
                }
            }

            // Tarjeta de ayuda (comandos disponibles)
            if (uiState.showHelpCard) {
                HelpCard(onDismiss = viewModel::dismissBubble)
            }

            // EL PERSONAJE (Carga desde recursos locales)
            val currentAsset = characterState.currentAssetRes

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(currentAsset)
                    .decoderFactory(if (Build.VERSION.SDK_INT >= 28) ImageDecoderDecoder.Factory() else GifDecoder.Factory())
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.overlay_assistant_character),
                modifier = Modifier
                    .height(260.dp)
                    .width(200.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomCenter,
                error = painterResource(id = R.drawable.ic_assistant_placeholder)
            )

            // Controles flotantes mínimos (Pastilla inferior ultra-estilizada)
            Surface(
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp).copy(alpha = 0.85f),
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .width(190.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    // Botón micrófono
                    IconButton(
                        onClick = {
                            if (uiState.isListening) {
                                viewModel.stopListening()
                            } else {
                                viewModel.startListening()
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = stringResource(R.string.overlay_voice),
                            tint = if (uiState.isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Input de texto
                    BasicTextField(
                        value = uiState.inputText,
                        onValueChange = viewModel::onInputChanged,
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focusState ->
                                onFocusChange(focusState.isFocused)
                            },
                        textStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurfaceVariant),
                        decorationBox = { innerTextField ->
                            if (uiState.inputText.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.overlay_voice_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            innerTextField()
                        }
                    )

                    // Botón enviar
                    IconButton(
                        onClick = {
                            viewModel.sendMessage(uiState.inputText)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.overlay_send),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Botón cambiar atuendo
                    IconButton(
                        onClick = { viewModel.changeOutfit() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Checkroom,
                            contentDescription = stringResource(R.string.overlay_change_outfit),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Comandos disponibles que la asistente entiende por voz (contenido de la tarjeta de ayuda). */
object HelpContent {
    // M12: IDs de recurso (no literales) — la tarjeta resuelve el texto con stringResource.
    val COMMANDS: List<Int> = listOf(
        R.string.overlay_command_call_ana,
        R.string.overlay_command_call_number,
        R.string.overlay_command_search,
        R.string.overlay_command_open_whatsapp,
        R.string.overlay_command_open_settings,
        R.string.overlay_command_alarm_exact,
        R.string.overlay_command_alarm_words,
        R.string.overlay_command_timer,
        R.string.overlay_command_volume_up,
        R.string.overlay_command_volume_down,
        R.string.overlay_command_mute,
        R.string.overlay_command_navigate,
        R.string.overlay_command_language,
        R.string.overlay_command_remember,
        R.string.overlay_command_repeat
    )
}

@Composable
private fun HelpCard(onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp).copy(alpha = 0.95f),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 4.dp,
        modifier = Modifier
            .padding(bottom = 4.dp)
            .widthIn(max = 180.dp)
            .heightIn(max = 160.dp)
            .animateContentSize()
            .clickable { onDismiss() }
    ) {
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.padding(8.dp).verticalScroll(scrollState)) {
            Text(
                text = stringResource(R.string.overlay_help_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            HelpContent.COMMANDS.forEach { command ->
                Text(
                    // M12: el bullet y el texto se resuelven con stringResource — antes
                    // "• $command" imprimía el ID numérico del recurso en la tarjeta.
                    text = stringResource(R.string.overlay_help_command_bullet, stringResource(command)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
