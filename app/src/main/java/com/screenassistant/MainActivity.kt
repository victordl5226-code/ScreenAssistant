package com.screenassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.screenassistant.core.ui.theme.ScreenAssistantTheme
import com.screenassistant.feature.overlay.AssistantOverlayService
import com.screenassistant.service.system.ScreenContextService
import com.screenassistant.ui.apikey.ApiKeySection
import com.screenassistant.ui.apikey.ApiKeyViewModel
import com.screenassistant.ui.apikey.UiState
import com.screenassistant.ui.puente.PuenteSettingsSection
import com.screenassistant.ui.puente.PuenteSettingsViewModel
import com.screenassistant.ui.puente.PuenteUiState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // M14: cadena de permisos — launchers a nivel de Activity (registerForActivityResult)
    // para poder CONTINUAR la cadena desde onResume al volver de Settings de superposición
    // sin depender del estado de composición.
    private var startPending = false

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startAssistantService()
        }
    }

    private val multiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Just log or update UI if needed
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // M14: tras el diálogo de notificaciones (concedido O denegado — en 13+ el FGS
        // arranca aunque la notificación no sea visible) la cadena CONTINÚA: se pasa a
        // la siguiente etapa (superposición) en vez de abortar el arranque.
        requestOverlayOrContinue()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScreenAssistantTheme {
                val apiKeyViewModel: ApiKeyViewModel = hiltViewModel()
                val apiKeyUiState by apiKeyViewModel.uiState.collectAsState()

                val puenteViewModel: PuenteSettingsViewModel = hiltViewModel()
                val puenteUiState by puenteViewModel.uiState.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        onStartService = { checkAndStartService() },
                        onStopService = { stopAssistantService() },
                        onRequestExtraPermissions = { requestExtraPermissions() },
                        apiKeyUiState = apiKeyUiState,
                        onApiKeyInputChange = apiKeyViewModel::onInputChange,
                        onApiKeySave = apiKeyViewModel::saveKey,
                        onApiKeyClear = apiKeyViewModel::clearKey,
                        puenteUiState = puenteUiState,
                        onPuenteTokenChange = puenteViewModel::onTokenChange,
                        onPuenteUrlFlagChange = puenteViewModel::onUrlFlagChange,
                        onPuentePackageRespuestaChange = puenteViewModel::onPackageRespuestaChange,
                        onPuenteAutoRemoteKeyChange = puenteViewModel::onAutoRemoteKeyChange,
                        onPuenteSave = puenteViewModel::save,
                        onPuenteClearKey = puenteViewModel::clearKey
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // M14: al volver de Settings de superposición con el permiso ya concedido, la
        // cadena continúa AUTOMÁTICAMENTE (sin pedir otra pulsación de "Start").
        if (startPending && Settings.canDrawOverlays(this)) {
            requestMicOrStart()
        }
    }

    private fun requestExtraPermissions() {
        val perms = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        multiplePermissionsLauncher.launch(perms.toTypedArray())
    }

    /** Punto de entrada de la cadena de permisos (1 pulsación de "Start"). */
    private fun checkAndStartService() {
        // 1. Notificaciones (Android 13+)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        requestOverlayOrContinue()
    }

    /** M14 etapa 2: superposición — si falta, marca startPending y abre Settings;
     *  onResume continúa la cadena al volver con canDrawOverlays=true. */
    private fun requestOverlayOrContinue() {
        if (!Settings.canDrawOverlays(this)) {
            startPending = true
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }
        requestMicOrStart()
    }

    /** M14 etapa 3: micrófono — si ya está concedido, arranca el servicio directo. */
    private fun requestMicOrStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startPending = false
            startAssistantService()
        }
    }

    private fun startAssistantService() {
        val intent = Intent(this, AssistantOverlayService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(this, intent)
    }

    private fun stopAssistantService() {
        val intent = Intent(this, AssistantOverlayService::class.java)
        stopService(intent)
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onRequestExtraPermissions: () -> Unit,
    apiKeyUiState: UiState,
    onApiKeyInputChange: (String) -> Unit,
    onApiKeySave: (String) -> Unit,
    onApiKeyClear: () -> Unit,
    puenteUiState: PuenteUiState,
    onPuenteTokenChange: (String) -> Unit,
    onPuenteUrlFlagChange: (Boolean) -> Unit,
    onPuentePackageRespuestaChange: (String) -> Unit,
    onPuenteAutoRemoteKeyChange: (String) -> Unit,
    onPuenteSave: () -> Unit,
    onPuenteClearKey: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isAccessibilityEnabled by remember {
        mutableStateOf(isAccessibilityServiceEnabled(context, ScreenContextService::class.java))
    }

    // Refresh status when returning to app
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        isAccessibilityEnabled = isAccessibilityServiceEnabled(context, ScreenContextService::class.java)
        onPauseOrDispose { }
    }

    // M1 (ADR-015): las Cards apiladas quedan cortas en pantallas pequeñas →
    // el Column de MainScreen gana verticalScroll.
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Estado del Servicio de Accesibilidad
        Surface(
            color = if (isAccessibilityEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = if (isAccessibilityEnabled) {
                    stringResource(R.string.setup_status_active)
                } else {
                    stringResource(R.string.setup_status_inactive)
                },
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Configuración de la API key de Gemini
        ApiKeySection(
            uiState = apiKeyUiState,
            onInputChange = onApiKeyInputChange,
            onSaveKey = onApiKeySave,
            onClearKey = onApiKeyClear,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Configuración del puente Tasker (Lote 7 / ADR-015 v1.2): token
        // compartido F1, privacidad de respuesta F2 y URL callback F3 (flag + key).
        PuenteSettingsSection(
            uiState = puenteUiState,
            onTokenChange = onPuenteTokenChange,
            onUrlFlagChange = onPuenteUrlFlagChange,
            onPackageRespuestaChange = onPuentePackageRespuestaChange,
            onAutoRemoteKeyChange = onPuenteAutoRemoteKeyChange,
            onSave = onPuenteSave,
            onClearKey = onPuenteClearKey,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = onStartService) {
            Text(stringResource(R.string.start_assistant))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            context.startActivity(intent)
        }) {
            Text(stringResource(R.string.enable_accessibility))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onRequestExtraPermissions) {
            Text(stringResource(R.string.setup_grant_offline_permissions))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onStopService) {
            Text(stringResource(R.string.stop_assistant))
        }
    }
}

fun isAccessibilityServiceEnabled(context: android.content.Context, service: Class<*>): Boolean {
    val expectedComponentName = android.content.ComponentName(context, service)
    val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    return enabledServices?.contains(expectedComponentName.flattenToString()) == true
}
