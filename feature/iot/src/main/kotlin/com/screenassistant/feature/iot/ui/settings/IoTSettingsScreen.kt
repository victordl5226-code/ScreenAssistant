package com.screenassistant.feature.iot.ui.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.screenassistant.core.iot.domain.usecase.IotSyncState
import com.screenassistant.feature.iot.ui.common.IoTErrorCard
import com.screenassistant.feature.iot.ui.common.IoTLoadingIndicator

/**
 * Pantalla principal de configuración IoT.
 *
 * Muestra un dashboard con el estado de dispositivos Matter,
 * métricas de salud y conexión del vehículo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IoTSettingsScreen(
    viewModel: IoTSettingsViewModel = hiltViewModel(),
    onNavigateToPermissions: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración IoT") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.syncNow() }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sincronizar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
    ) { padding ->
        when (val state = uiState) {
            is IoTSettingsUiState.Loading -> {
                IoTLoadingIndicator(modifier = Modifier.padding(padding))
            }
            is IoTSettingsUiState.Content -> {
                IoTSettingsContent(
                    state = state,
                    onNavigateToPermissions = onNavigateToPermissions,
                    modifier = Modifier.padding(padding),
                )
            }
            is IoTSettingsUiState.Error -> {
                IoTErrorCard(
                    message = state.message,
                    onRetry = { viewModel.syncNow() },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun IoTSettingsContent(
    state: IoTSettingsUiState.Content,
    onNavigateToPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Matter Devices Section
        item {
            IoTSectionHeader(
                title = "Dispositivos Matter",
                icon = Icons.Default.Sensors,
                count = "${state.matterDevicesOnline}/${state.matterDevicesTotal} en línea",
            )
        }

        item {
            DeviceStatusCard(
                title = "Dispositivos conectados",
                subtitle = if (state.matterDevicesTotal == 0) {
                    "Sin dispositivos configurados"
                } else {
                    "${state.matterDevicesOnline} de ${state.matterDevicesTotal} en línea"
                },
                isOnline = state.matterDevicesOnline > 0,
                isEmpty = state.matterDevicesTotal == 0,
            )
        }

        // Health Metrics Section
        item {
            IoTSectionHeader(
                title = "Datos de Salud",
                icon = Icons.Default.HealthAndSafety,
            )
        }

        item {
            HealthMetricsSummary(
                summary = state.healthSummary,
            )
        }

        // Car Connection Section
        item {
            IoTSectionHeader(
                title = "Conexión del Vehículo",
                icon = Icons.Default.DirectionsCar,
            )
        }

        item {
            CarConnectionCard(
                isConnected = state.carConnected,
            )
        }

        // Permissions Button
        item {
            Card(
                onClick = onNavigateToPermissions,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Permisos",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Gestionar permisos de Health Connect y Coche",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        // Last Sync
        item {
            Text(
                text = "Última sincronización: ${state.lastSyncTime}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun IoTSectionHeader(
    title: String,
    icon: ImageVector,
    count: String? = null,
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
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (count != null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = count,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun DeviceStatusCard(
    title: String,
    subtitle: String,
    isOnline: Boolean,
    isEmpty: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isEmpty -> MaterialTheme.colorScheme.surfaceVariant
                isOnline -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when {
                    isEmpty -> Icons.Default.Sensors
                    isOnline -> Icons.Default.CheckCircle
                    else -> Icons.Default.Error
                },
                contentDescription = when {
                    isEmpty -> "Sin dispositivos"
                    isOnline -> "Estado: en línea"
                    else -> "Estado: sin conexión"
                },
                tint = when {
                    isEmpty -> MaterialTheme.colorScheme.onSurfaceVariant
                    isOnline -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                },
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun HealthMetricsSummary(
    summary: String,
    modifier: Modifier = Modifier,
) {
    val isEmpty = summary.isEmpty() || summary == "Sin datos recientes"
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEmpty) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.HealthAndSafety,
                contentDescription = null,
                tint = if (isEmpty) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isEmpty) "Sin datos de salud disponibles" else "Resumen de Salud",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isEmpty) {
                    "Conecta un dispositivo wearable para comenzar"
                } else {
                    summary
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun CarConnectionCard(
    isConnected: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = if (isConnected) "Coche conectado" else "Coche desconectado",
                tint = if (isConnected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Estado del Coche",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (isConnected) "Conectado" else "Desconectado",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
