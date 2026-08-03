package com.screenassistant.ui.puente

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.screenassistant.R

/**
 * Sección de configuración del puente Tasker (P4, ADR-015 v1.2): patrón
 * ApiKeySection 1:1 — recibe el estado y callbacks desde el MainScreen (el VM
 * vive en MainActivity). Card independiente, sin refactor de pantalla.
 *
 * 1. Campo "Token compartido" (F1 v1.2) — enmascarado con toggle de
 *    visibilidad; vacío = canal abierto (documentado "no protegido").
 * 2. Campo "Paquete del receptor de la respuesta" (F2) — vacío = default taskerm
 *    al cargar (H1); el modo global (sin setPackage) no es alcanzable.
 * 3. Switch "Responder por URL (AutoRemote)" (F3 v1.2, default OFF) + campo
 *    "Key de AutoRemote" (enmascarado).
 * 4. Botones Guardar/Borrar key.
 *
 * v1.2a (H1): ELIMINADO el manejo de error (PuenteError muerto con la allowlist).
 */
@Composable
fun PuenteSettingsSection(
    uiState: PuenteUiState,
    onTokenChange: (String) -> Unit,
    onUrlFlagChange: (Boolean) -> Unit,
    onPackageRespuestaChange: (String) -> Unit,
    onAutoRemoteKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onClearKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showToken by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    var showClearKeyDialog by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.puente_title),
                style = MaterialTheme.typography.titleMedium
            )

            // Badge de estado del canal (patrón ApiKeySection)
            Text(
                text = if (uiState.tokenCompartido.isNotBlank()) {
                    stringResource(R.string.puente_channel_protected)
                } else {
                    stringResource(R.string.puente_channel_open)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiState.tokenCompartido.isNotBlank()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            // Banner de modo degradado (prefs de fallback, patrón ApiKeySection)
            if (uiState.isDegraded) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.puente_degraded_banner),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // === F1 v1.2: token compartido del canal (enmascarado con toggle) ===
            OutlinedTextField(
                value = uiState.tokenCompartido,
                onValueChange = onTokenChange,
                label = { Text(stringResource(R.string.puente_token_label)) },
                singleLine = true,
                visualTransformation = if (showToken) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(
                            imageVector = if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(
                                if (showToken) R.string.puente_token_hide else R.string.puente_token_show
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text(stringResource(R.string.puente_token_notice))
                }
            )

            // === F2: paquete del receptor de la respuesta (vacío = default taskerm) ===
            OutlinedTextField(
                value = uiState.packageRespuesta,
                onValueChange = onPackageRespuestaChange,
                label = { Text(stringResource(R.string.puente_package_respuesta_label)) },
                placeholder = { Text(stringResource(R.string.puente_package_respuesta_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text(stringResource(R.string.puente_privacy_notice))
                }
            )

            // === F3 v1.2: switch de URL callback (default OFF) ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = uiState.enviarRespuestaURL,
                        role = Role.Switch,
                        onValueChange = onUrlFlagChange
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.puente_url_switch),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.puente_url_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.enviarRespuestaURL,
                    onCheckedChange = null
                )
            }

            // === F3: key de AutoRemote (enmascarada con toggle) ===
            OutlinedTextField(
                value = uiState.autoRemoteKey,
                onValueChange = onAutoRemoteKeyChange,
                label = { Text(stringResource(R.string.puente_key_label)) },
                singleLine = true,
                visualTransformation = if (showKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            imageVector = if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(
                                if (showKey) R.string.puente_key_hide else R.string.puente_key_show
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave) {
                    Text(stringResource(R.string.puente_save))
                }

                if (uiState.autoRemoteKey.isNotBlank()) {
                    OutlinedButton(onClick = { showClearKeyDialog = true }) {
                        Text(stringResource(R.string.puente_clear_key))
                    }
                }
            }
        }
    }

    // Confirmación antes de borrar la key (patrón ApiKeySection M7)
    if (showClearKeyDialog) {
        AlertDialog(
            onDismissRequest = { showClearKeyDialog = false },
            title = { Text(stringResource(R.string.puente_key_clear_dialog_title)) },
            text = { Text(stringResource(R.string.puente_key_clear_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearKeyDialog = false
                    onClearKey()
                }) {
                    Text(stringResource(R.string.puente_key_clear_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearKeyDialog = false }) {
                    Text(stringResource(R.string.puente_key_clear_dialog_cancel))
                }
            }
        )
    }
}
