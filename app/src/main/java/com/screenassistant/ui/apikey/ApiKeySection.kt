package com.screenassistant.ui.apikey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.screenassistant.R

/**
 * Sección de configuración de la API key de Gemini.
 * Recibe el estado y callbacks desde el MainScreen (el VM vive en MainActivity),
 * siguiendo el patrón de los composables existentes.
 */
@Composable
fun ApiKeySection(
    uiState: UiState,
    onInputChange: (String) -> Unit,
    onSaveKey: (String) -> Unit,
    onClearKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showKey by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.api_key_title),
                style = MaterialTheme.typography.titleMedium
            )

            // Badge de estado
            Text(
                text = if (uiState.isConfigured) {
                    stringResource(R.string.api_key_configured, uiState.maskedKey)
                } else {
                    stringResource(R.string.api_key_not_configured)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiState.isConfigured) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            // Banner de modo degradado
            if (uiState.isDegraded) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.api_key_degraded_banner),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Campo enmascarado con toggle de visibilidad
            OutlinedTextField(
                value = uiState.input,
                onValueChange = onInputChange,
                label = { Text(stringResource(R.string.api_key_field_label)) },
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
                                if (showKey) R.string.api_key_hide_key else R.string.api_key_show_key
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Error inline bajo el campo (solo si hay error)
            uiState.error?.let { error ->
                Text(
                    text = when (error) {
                        ErrorType.KEY_EMPTY -> stringResource(R.string.api_key_error_empty)
                        ErrorType.KEY_INVALID -> stringResource(R.string.api_key_error_invalid)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSaveKey(uiState.input) },
                    enabled = uiState.input.isNotBlank()
                ) {
                    Text(stringResource(R.string.api_key_save))
                }

                if (uiState.isConfigured) {
                    OutlinedButton(onClick = { showRemoveDialog = true }) {
                        Text(stringResource(R.string.api_key_remove))
                    }
                }
            }
        }
    }

    // Confirmación antes de quitar la key (M7)
    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text(stringResource(R.string.api_key_remove_dialog_title)) },
            text = { Text(stringResource(R.string.api_key_remove_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveDialog = false
                    onClearKey()
                }) {
                    Text(stringResource(R.string.api_key_remove_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text(stringResource(R.string.api_key_remove_dialog_cancel))
                }
            }
        )
    }
}
