package com.screenassistant.feature.overlay.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.screenassistant.core.domain.repository.ai.ModelInfo

/**
 * Pantalla de configuración de IA Local (llama.cpp).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalLlmSettingsScreen(
    viewModel: LocalLlmSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IA Local") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
    ) { padding ->
        LocalLlmSettingsContent(
            uiState = uiState,
            onDownloadModel = { viewModel.downloadModel() },
            onLoadModel = { viewModel.loadModel() },
            onUnloadModel = { viewModel.unloadModel() },
            onDeleteModel = { viewModel.deleteModel() },
            onClearError = { viewModel.clearError() },
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun LocalLlmSettingsContent(
    uiState: LocalLlmUiState,
    onDownloadModel: () -> Unit,
    onLoadModel: () -> Unit,
    onUnloadModel: () -> Unit,
    onDeleteModel: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            LocalLlmSectionHeader(
                title = "IA Local",
                subtitle = "Inferencia sin conexión",
                icon = Icons.Default.Psychology,
            )
        }

        item {
            uiState.modelInfo?.let {
                ModelInfoCard(
                    info = it,
                    formattedSize = uiState.formattedModelSize,
                    isReady = uiState.isModelReady,
                    isDownloaded = uiState.isModelDownloaded
                )
            }
        }

        item {
            NativeAvailabilityCard(isAvailable = uiState.isNativeAvailable)
        }

        item {
            SpaceInfoCard(
                required = uiState.formattedModelSize,
                available = uiState.formattedAvailableSpace,
            )
        }

        item {
            ModelActionsSection(
                isDownloaded = uiState.isModelDownloaded,
                isReady = uiState.isModelReady,
                isDownloading = uiState.isLoading && !uiState.isModelReady,
                downloadProgress = uiState.downloadProgress,
                onDownload = onDownloadModel,
                onLoad = onLoadModel,
                onUnload = onUnloadModel,
                onDelete = onDeleteModel,
                onErrorDismiss = onClearError,
                errorMessage = uiState.errorMessage,
            )
        }

        item {
            InfoCard(
                title = "¿Qué es llama.cpp?",
                message = "Es un motor de alto rendimiento que permite ejecutar " +
                    "modelos de lenguaje directamente en tu dispositivo. " +
                    "Utiliza modelos GGUF optimizados.",
            )
        }
    }
}

@Composable
private fun LocalLlmSectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModelInfoCard(
    info: ModelInfo,
    formattedSize: String,
    isReady: Boolean,
    isDownloaded: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = info.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            ModelDetailRow(label = "Tamaño", value = formattedSize)
            ModelDetailRow(label = "Versión", value = info.version)
            ModelDetailRow(label = "Cuantización", value = info.quantization)

            Spacer(modifier = Modifier.height(12.dp))

            ModelStatusBadge(isReady = isReady, isDownloaded = isDownloaded)
        }
    }
}

@Composable
private fun ModelDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ModelStatusBadge(isReady: Boolean, isDownloaded: Boolean) {
    val (label, color) = when {
        isReady -> "Listo" to MaterialTheme.colorScheme.tertiary
        isDownloaded -> "Descargado" to MaterialTheme.colorScheme.secondary
        else -> "No descargado" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(modifier = Modifier.size(8.dp)) { drawCircle(color = color) }
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun NativeAvailabilityCard(isAvailable: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isAvailable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isAvailable) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = "Motor llama.cpp", style = MaterialTheme.typography.titleMedium)
                Text(text = if (isAvailable) "✓ Librería nativa cargada" else "✗ No disponible", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SpaceInfoCard(required: String, available: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = "Espacio en disco", style = MaterialTheme.typography.titleSmall)
                Text(text = "Necesario: $required · Disponible: $available", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ModelActionsSection(
    isDownloaded: Boolean,
    isReady: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    onDownload: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit,
    onErrorDismiss: () -> Unit,
    errorMessage: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Acciones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            if (errorMessage != null) {
                ErrorIndicator(message = errorMessage, onDismiss = onErrorDismiss)
            }

            when {
                isReady -> {
                    ActionButton(text = "Descargar de memoria", icon = Icons.Default.HourglassEmpty, onClick = onUnload, enabled = true)
                }
                isDownloaded -> {
                    ActionButton(text = "Cargar en memoria", icon = Icons.Default.Memory, onClick = onLoad, enabled = true)
                    OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Eliminar modelo")
                    }
                }
                isDownloading -> {
                    DownloadProgressCard(progress = downloadProgress)
                }
                else -> {
                    ActionButton(text = "Descargar modelo", icon = Icons.Default.CloudDownload, onClick = onDownload, enabled = true)
                }
            }
        }
    }
}

@Composable
private fun ErrorIndicator(message: String, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

@Composable
private fun DownloadProgressCard(progress: Float) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Descargando...", style = MaterialTheme.typography.bodySmall)
            Text(text = "${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp), strokeCap = StrokeCap.Round)
    }
}

@Composable
private fun ActionButton(text: String, icon: ImageVector, onClick: () -> Unit, enabled: Boolean) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), enabled = enabled) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text)
    }
}

@Composable
private fun InfoCard(title: String, message: String) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = message, style = MaterialTheme.typography.bodySmall)
        }
    }
}
