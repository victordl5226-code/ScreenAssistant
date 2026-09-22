package com.screenassistant.feature.overlay

import android.os.Build
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.screenassistant.core.domain.usecase.SystemCommandParser
import com.screenassistant.core.domain.repository.GeminiRepository
import com.screenassistant.core.ui.theme.JarvisCyan
import com.screenassistant.core.ui.theme.JarvisBlue
import com.screenassistant.core.ui.theme.JarvisHologram
import com.screenassistant.core.ui.theme.JarvisGlow
import com.screenassistant.core.domain.repository.ai.MemoryTurn
import com.screenassistant.feature.overlay.R
import kotlinx.coroutines.delay

// ── Constantes UI/UX (tokens de diseño) ───────────────────────────────
private const val ALPHA_BUBBLE_TEXT = 0.92f
private const val ALPHA_CONTROL_BAR = 0.85f
private const val CHAR_HEIGHT_RATIO = 0.35f
private const val CHAR_MIN_HEIGHT_DP = 180
private const val CHAR_MAX_HEIGHT_DP = 320
private const val CHAR_ASPECT_RATIO = 0.77f // ~200/260
private const val BUBBLE_WIDTH_RATIO = 0.4f
private const val BUBBLE_MIN_WIDTH_DP = 160
private const val BUBBLE_MAX_WIDTH_DP = 260

@Composable
fun AssistantOverlayUI(
    onDrag: (Float, Float) -> Unit,
    onFocusChange: (Boolean) -> Unit = {},
    viewModel: OverlayViewModel,
    geminiRepository: GeminiRepository,
    commandParser: SystemCommandParser,
    modelAssetRepository: com.screenassistant.core.model.domain.ModelAssetRepository
) {
    val uiState by viewModel.uiState.collectAsState()
    val characterState = viewModel.characterState
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val maxBubbleWidth = (configuration.screenWidthDp * BUBBLE_WIDTH_RATIO).dp
        .coerceIn(BUBBLE_MIN_WIDTH_DP.dp, BUBBLE_MAX_WIDTH_DP.dp)
    val characterHeight = (configuration.screenHeightDp * CHAR_HEIGHT_RATIO).dp
        .coerceIn(CHAR_MIN_HEIGHT_DP.dp, CHAR_MAX_HEIGHT_DP.dp)
    val characterWidth = characterHeight * CHAR_ASPECT_RATIO

    // TTS y STT
    val ttsManager = remember {
        PiperTtsManager(context,
            onStart = { 
                characterState.animationState = AnimationState.SPEAKING
                viewModel.onAnimationStateChanged(AnimationState.SPEAKING)
            },
            onDone = { 
                characterState.animationState = AnimationState.IDLE
                viewModel.onAnimationStateChanged(AnimationState.IDLE)
            },
            modelRepository = modelAssetRepository
        ).also {
            Log.d("AssistantOverlayUI", "tts asignado a OverlayViewModel")
            viewModel.textToSpeech = it
        }
    }

    val sttManager = remember {
        VoskSpeechToTextManager(context,
            onResultCallback = { text -> viewModel.onVoiceResult(text) },
            onPartialResultCallback = { partial -> viewModel.onVoicePartialResult(partial) },
            onErrorCallback = { error -> viewModel.onVoiceError(error) },
            modelRepository = modelAssetRepository
        ).also { viewModel.speechToText = it }
    }

    DisposableEffect(Unit) {
        characterState.animationState = AnimationState.GREETING
        onDispose {
            ttsManager.destroy()
            sttManager.destroy()
        }
    }

    LaunchedEffect(Unit) {
        delay(1000)
        viewModel.sendMessage("ACTION_INIT_CONVERSATION")
    }

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
            modifier = Modifier.widthIn(min = 280.dp)
        ) {
            // ── Burbuja de respuesta (Estilo Holograma J.A.R.V.I.S.) ──
            AnimatedVisibility(
                visible = uiState.assistantText.isNotEmpty(),
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                AssistantResponseBubble(
                    text = uiState.assistantText,
                    maxWidth = maxBubbleWidth,
                    isMultiStep = uiState.isMultiStep,
                    onDismiss = { viewModel.dismissBubble() }
                )
            }

            // ── EL PERSONAJE ──────────────────────────────────────────
            val currentAsset = characterState.currentAssetRes
            Box(contentAlignment = Alignment.BottomCenter) {
                // Barra de estado de hardware (Estilo J.A.R.V.I.S.)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(bottom = characterHeight + 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "PWR:${uiState.batteryLevel}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = if (uiState.isCharging) Color.Green else if (uiState.batteryLevel <= 15) Color.Red else JarvisCyan.copy(alpha = 0.7f)
                        )
                        if (uiState.isCharging) {
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(8.dp), tint = Color.Green)
                        }
                        
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MODO:${uiState.assistantMode.name}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = JarvisCyan.copy(alpha = 0.5f)
                        )
                    }
                }

                // Efecto de escaneo J.A.R.V.I.S. (Círculo de pulso)
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val scanScale by infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )
                val scanAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.1f,
                    targetValue = 0.4f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )

                androidx.compose.foundation.Canvas(modifier = Modifier.size(characterWidth * 1.5f)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(JarvisCyan.copy(alpha = scanAlpha), Color.Transparent)
                        ),
                        radius = (size.minDimension * 0.5f) * scanScale
                    )
                }

                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(currentAsset)
                        .decoderFactory(if (Build.VERSION.SDK_INT >= 28) ImageDecoderDecoder.Factory() else GifDecoder.Factory())
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.height(characterHeight).width(characterWidth),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomCenter
                )
                
                // Indicador visual de escucha (Estilo J.A.R.V.I.S.)
                if (uiState.isListening) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .size(characterWidth, 4.dp)
                            .padding(horizontal = 20.dp)
                    ) {
                        drawRoundRect(
                            color = JarvisCyan.copy(alpha = 0.6f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                        )
                    }
                }
            }

            // ── Barra de control (Estilo Dark Tech) ──────────────────
            AssistantInputBar(
                assistantName = uiState.assistantName,
                inputText = uiState.inputText,
                isListening = uiState.isListening,
                onInputChanged = viewModel::onInputChanged,
                onFocusChanged = { focused ->
                    onFocusChange(focused)
                },
                onMicClick = {
                    if (uiState.isListening) viewModel.stopListening() else viewModel.startListening()
                },
                onSendClick = { viewModel.sendMessage(uiState.inputText) },
                onHistoryClick = { viewModel.toggleHistory() }
            )

            // ── Historial (Estilo Holograma) ─────────────────────────
            AnimatedVisibility(
                visible = uiState.showHistory,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                HistoryCard(
                    turns = uiState.historyTurns,
                    onDismiss = { viewModel.toggleHistory() },
                    maxWidth = maxBubbleWidth
                )
            }
        }
    }
}

@Composable
private fun AssistantResponseBubble(text: String, maxWidth: Dp, isMultiStep: Boolean, onDismiss: () -> Unit) {
    var displayedText by remember(text) { mutableStateOf("") }
    
    LaunchedEffect(text) {
        displayedText = ""
        text.forEach { char ->
            displayedText += char
            delay(12) 
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF001219).copy(alpha = 0.75f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, JarvisCyan.copy(alpha = 0.4f)),
        shadowElevation = 6.dp,
        modifier = Modifier
            .padding(bottom = 2.dp, end = 12.dp)
            .widthIn(max = maxWidth * 0.85f)
            .animateContentSize()
            .clickable { onDismiss() }
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(2.dp).background(JarvisCyan, RoundedCornerShape(50)))
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "STARK_OS v3.1",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 6.sp,
                        color = JarvisCyan.copy(alpha = 0.4f),
                        letterSpacing = 0.3.sp
                    )
                )
            }
            Text(
                text = displayedText,
                style = MaterialTheme.typography.bodySmall.copy(
                    lineHeight = 13.sp,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.1.sp,
                    color = JarvisCyan
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AssistantInputBar(
    assistantName: String,
    inputText: String, isListening: Boolean,
    onInputChanged: (String) -> Unit, onFocusChanged: (Boolean) -> Unit,
    onMicClick: () -> Unit, onSendClick: () -> Unit, onHistoryClick: () -> Unit
) {
    Surface(
        color = Color(0xFF001219).copy(alpha = 0.9f),
        shape = RoundedCornerShape(28.dp), 
        border = androidx.compose.foundation.BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.2f)),
        shadowElevation = 4.dp,
        modifier = Modifier.padding(bottom = 8.dp).widthIn(min = 180.dp, max = 280.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            Text(
                text = assistantName.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Black),
                color = JarvisCyan.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 10.dp)
            )
            IconButton(onClick = onMicClick, modifier = Modifier.size(44.dp)) {
                Icon(imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null, tint = if (isListening) Color.Red else JarvisCyan)
            }
            BasicTextField(
                value = inputText, onValueChange = onInputChanged,
                modifier = Modifier.weight(1f).onFocusChanged { onFocusChanged(it.isFocused) }.semantics { contentDescription = "Campo de texto" },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSendClick() }),
                cursorBrush = SolidColor(JarvisCyan),
                decorationBox = { inner ->
                    Box(modifier = Modifier.background(JarvisCyan.copy(alpha = 0.05f), RoundedCornerShape(12.dp)).padding(8.dp)) {
                        if (inputText.isEmpty()) Text(if (isListening) "Escuchando..." else "Protocolo...", style = MaterialTheme.typography.bodySmall, color = JarvisCyan.copy(alpha = 0.3f))
                        inner()
                    }
                }
            )
            IconButton(onClick = onHistoryClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.History, contentDescription = "Historial", tint = JarvisCyan.copy(alpha = 0.8f))
            }
            IconButton(onClick = onSendClick, enabled = inputText.isNotEmpty(), modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", tint = if (inputText.isNotEmpty()) JarvisCyan else Color.DarkGray)
            }
        }
    }
}

@Composable
private fun HistoryCard(turns: List<MemoryTurn>, onDismiss: () -> Unit, maxWidth: Dp) {
    Surface(
        shape = RoundedCornerShape(16.dp), 
        color = Color(0xFF001219).copy(alpha = 0.95f),
        border = androidx.compose.foundation.BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.3f)),
        shadowElevation = 16.dp, 
        modifier = Modifier.padding(bottom = 8.dp).widthIn(max = maxWidth).heightIn(max = 240.dp)
    ) {
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.padding(12.dp).verticalScroll(scrollState)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LOGS DE MEMORIA", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp), fontWeight = FontWeight.Bold, color = JarvisCyan, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, contentDescription = null, tint = JarvisCyan) }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = JarvisCyan.copy(alpha = 0.2f))
            turns.forEach { turn ->
                if (turn.userMessage.isNotBlank()) {
                    Text("USER > ${turn.userMessage}", style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), color = Color.Gray)
                }
                Text("JARVIS > ${turn.assistantResponse}", style = MaterialTheme.typography.bodySmall, color = JarvisCyan.copy(alpha = 0.9f), modifier = Modifier.padding(bottom = 8.dp))
            }
            if (turns.isEmpty()) Text("SIN REGISTROS.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}
