package com.screenassistant.feature.iot.ui.permissions

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.screenassistant.core.iot.domain.repository.CarPermission
import com.screenassistant.core.iot.domain.repository.HealthRecordType
import com.screenassistant.core.iot.domain.usecase.PermissionsReport
import com.screenassistant.feature.iot.ui.common.IoTErrorCard
import com.screenassistant.feature.iot.ui.common.IoTLoadingIndicator

/**
 * Pantalla de gestión de permisos IoT.
 *
 * Muestra el estado de permisos de Health Connect y Car App,
 * y permite solicitar los permisos faltantes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IoTPermissionsScreen(
    viewModel: IoTPermissionsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permisos IoT") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
    ) { padding ->
        when (val state = uiState) {
            is IoTPermissionsUiState.Loading -> {
                IoTLoadingIndicator(modifier = Modifier.padding(padding))
            }
            is IoTPermissionsUiState.Content -> {
                IoTPermissionsContent(
                    report = state.report,
                    isRequesting = state.isRequesting,
                    onRequestAll = { viewModel.requestAllEssential() },
                    modifier = Modifier.padding(padding),
                )
            }
            is IoTPermissionsUiState.Error -> {
                IoTErrorCard(
                    message = state.message,
                    onRetry = { viewModel.requestAllEssential() },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun IoTPermissionsContent(
    report: PermissionsReport,
    isRequesting: Boolean,
    onRequestAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Health Connect Section
        item {
            PermissionSectionHeader(
                title = "Health Connect",
                icon = Icons.Default.HealthAndSafety,
                granted = report.healthPermissions.grantedTypes.size,
                total = (report.healthPermissions.requiredTypes + report.healthPermissions.optionalTypes).size,
            )
        }

        items(report.healthPermissions.requiredTypes.toList()) { type ->
            PermissionItem(
                name = formatHealthRecordType(type),
                isRequired = true,
                isGranted = type in report.healthPermissions.grantedTypes,
            )
        }

        items(report.healthPermissions.optionalTypes.toList()) { type ->
            PermissionItem(
                name = formatHealthRecordType(type),
                isRequired = false,
                isGranted = type in report.healthPermissions.grantedTypes,
            )
        }

        // Car App Section
        item {
            PermissionSectionHeader(
                title = "Android Auto / AAOS",
                icon = Icons.Default.Security,
                granted = report.carPermissions.grantedPermissions.size,
                total = (report.carPermissions.requiredPermissions + report.carPermissions.optionalPermissions).size,
            )
        }

        items(report.carPermissions.requiredPermissions.toList()) { perm ->
            PermissionItem(
                name = formatCarPermission(perm),
                isRequired = true,
                isGranted = perm in report.carPermissions.grantedPermissions,
            )
        }

        items(report.carPermissions.optionalPermissions.toList()) { perm ->
            PermissionItem(
                name = formatCarPermission(perm),
                isRequired = false,
                isGranted = perm in report.carPermissions.grantedPermissions,
            )
        }

        // Request Button
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRequestAll,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRequesting && !report.hasAllEssentialPermissions,
            ) {
                Text(
                    text = if (report.hasAllEssentialPermissions) {
                        "Todos los permisos concedidos"
                    } else {
                        "Solicitar permisos faltantes"
                    },
                )
            }
        }

        // Summary
        item {
            Text(
                text = report.summary(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionSectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    granted: Int,
    total: Int,
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
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "$granted/$total",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionItem(
    name: String,
    isRequired: Boolean,
    isGranted: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Close,
                contentDescription = if (isGranted) "Permiso concedido" else "Permiso no concedido",
                tint = if (isGranted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = if (isRequired) "Requerido" else "Opcional",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatHealthRecordType(type: HealthRecordType): String = when (type) {
    // Actividad
    HealthRecordType.STEPS -> "Pasos"
    HealthRecordType.ACTIVE_CALORIES_BURNED -> "Calorías Activas"
    HealthRecordType.BASAL_METABOLIC_RATE -> "Metabolismo Basal"
    HealthRecordType.DISTANCE -> "Distancia"
    HealthRecordType.EXERCISE_SESSION -> "Sesión de Ejercicio"
    HealthRecordType.HEART_RATE -> "Frecuencia Cardíaca"
    HealthRecordType.HEART_RATE_VARIABILITY -> "Variabilidad Cardíaca"
    HealthRecordType.RESTING_HEART_RATE -> "FC en Reposo"
    HealthRecordType.VO2_MAX -> "VO2 Máximo"
    HealthRecordType.SPEED -> "Velocidad"
    HealthRecordType.POWER -> "Potencia"
    HealthRecordType.CADENCE -> "Cadencia"
    // Cuerpo
    HealthRecordType.WEIGHT -> "Peso"
    HealthRecordType.BODY_FAT -> "Grasa Corporal"
    HealthRecordType.HEIGHT -> "Altura"
    HealthRecordType.LEAN_BODY_MASS -> "Masa Muscular Libre"
    HealthRecordType.BODY_WATER_MASS -> "Masa de Agua"
    HealthRecordType.BONE_MASS -> "Masa Ósea"
    HealthRecordType.MUSCLE_MASS -> "Masa Muscular"
    HealthRecordType.WAIST_CIRCUMFERENCE -> "Cintura"
    // Sueño
    HealthRecordType.SLEEP_SESSION -> "Sesión de Sueño"
    HealthRecordType.SLEEP_STAGE -> "Etapas de Sueño"
    // Signos vitales
    HealthRecordType.BLOOD_OXYGEN -> "Saturación de Oxígeno"
    HealthRecordType.BLOOD_PRESSURE -> "Presión Arterial"
    HealthRecordType.BODY_TEMPERATURE -> "Temperatura Corporal"
    HealthRecordType.RESPIRATORY_RATE -> "Frecuencia Respiratoria"
    HealthRecordType.SKIN_TEMPERATURE -> "Temperatura de Piel"
    // Ciclo menstrual
    HealthRecordType.MENSTRUATION_FLOW -> "Flujo Menstrual"
    HealthRecordType.OVULATION_TEST -> "Test de Ovulación"
    HealthRecordType.CERVICAL_MUCUS -> "Moco Cervical"
    HealthRecordType.BASAL_BODY_TEMPERATURE -> "Temperatura Basal"
    // Nutrición
    HealthRecordType.NUTRITION -> "Nutrición"
    HealthRecordType.HYDRATION -> "Hidratación"
    // Médicos
    HealthRecordType.MEDICATION -> "Medicamentos"
    HealthRecordType.ALLERGY -> "Alergias"
    HealthRecordType.CONDITION -> "Condiciones"
    HealthRecordType.IMMUNIZATION -> "Inmunizaciones"
    HealthRecordType.LAB_RESULT -> "Resultados de Laboratorio"
    HealthRecordType.PROCEDURE -> "Procedimientos"
    HealthRecordType.VITAL_SIGNS -> "Signos Vitales"
}

private fun formatCarPermission(permission: CarPermission): String = when (permission) {
    CarPermission.MICROPHONE -> "Micrófono"
    CarPermission.LOCATION -> "Ubicación"
    CarPermission.CONTACTS -> "Contactos"
    CarPermission.SMS -> "Mensajes SMS"
    CarPermission.CALL_LOGS -> "Registro de Llamadas"
    CarPermission.PHONE_STATE -> "Estado del Teléfono"
    CarPermission.VEHICLE_DATA -> "Datos del Vehículo"
    CarPermission.PASSENGER_DETECTION -> "Detección de Pasajeros"
}
