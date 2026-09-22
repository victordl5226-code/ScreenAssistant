package com.screenassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.compose.rememberNavController
import com.screenassistant.core.ui.theme.ScreenAssistantTheme
import com.screenassistant.feature.overlay.AssistantOverlayService
import com.screenassistant.feature.overlay.onboarding.OnboardingScreen
import com.screenassistant.feature.overlay.onboarding.OnboardingViewModel
import com.screenassistant.feature.overlay.ui.settings.LocalLlmSettingsScreen
import com.screenassistant.feature.iot.ui.settings.IoTSettingsScreen
import com.screenassistant.feature.iot.ui.permissions.IoTPermissionsScreen
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
    ) { }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        requestOverlayOrContinue()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScreenAssistantTheme {
                val onboardingViewModel: OnboardingViewModel = hiltViewModel()
                val onboardingState by onboardingViewModel.uiState.collectAsState()

                if (onboardingState.isOnboardingCompleted) {
                    val apiKeyViewModel: ApiKeyViewModel = hiltViewModel()
                    val apiKeyUiState by apiKeyViewModel.uiState.collectAsState()

                    val puenteViewModel: PuenteSettingsViewModel = hiltViewModel()
                    val puenteUiState by puenteViewModel.uiState.collectAsState()

                    var showLocalLlmSettings by remember { mutableStateOf(false) }
                    var showIotSettings by remember { mutableStateOf(false) }
                    var showIotPermissions by remember { mutableStateOf(false) }

                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        when {
                            showLocalLlmSettings -> {
                                LocalLlmSettingsScreen(
                                    onBack = { showLocalLlmSettings = false }
                                )
                            }
                            showIotPermissions -> {
                                IoTPermissionsScreen(
                                    onBack = { showIotPermissions = false }
                                )
                            }
                            showIotSettings -> {
                                IoTSettingsScreen(
                                    onNavigateToPermissions = { showIotPermissions = true },
                                    onBack = { showIotSettings = false }
                                )
                            }
                            else -> {
                                MainScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    onStartService = { checkAndStartService() },
                                    onStopService = { stopAssistantService() },
                                    onRequestExtraPermissions = { requestExtraPermissions() },
                                    onNavigateToLocalLlmSettings = { 
                                        Log.d("MainActivity", "Navigating to Local LLM")
                                        showLocalLlmSettings = true 
                                    },
                                    onNavigateToIotSettings = { 
                                        Log.d("MainActivity", "Navigating to IoT Settings")
                                        showIotSettings = true 
                                    },
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
                } else {
                    OnboardingScreen(
                        viewModel = onboardingViewModel,
                        onOnboardingComplete = { }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
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

    private fun checkAndStartService() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        requestOverlayOrContinue()
    }

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
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopAssistantService() {
        val intent = Intent(this, AssistantOverlayService::class.java)
        stopService(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onRequestExtraPermissions: () -> Unit,
    onNavigateToLocalLlmSettings: () -> Unit,
    onNavigateToIotSettings: () -> Unit,
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
    val context = LocalContext.current
    var isAccessibilityEnabled by remember {
        mutableStateOf(isAccessibilityServiceEnabled(context, ScreenContextService::class.java))
    }
    var isOverlayEnabled by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    LifecycleResumeEffect(Unit) {
        isAccessibilityEnabled = isAccessibilityServiceEnabled(context, ScreenContextService::class.java)
        isOverlayEnabled = Settings.canDrawOverlays(context)
        onPauseOrDispose { }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("J.A.R.V.I.S.") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "\uD83E\uDD16",
                        style = MaterialTheme.typography.displayMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Un asistente tipo J.A.R.V.I.S.",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Estado",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.enable_accessibility),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Surface(
                                color = if (isAccessibilityEnabled)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = if (isAccessibilityEnabled) "Activo" else "Inactivo",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isAccessibilityEnabled)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Superposición",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Surface(
                                color = if (isOverlayEnabled)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = if (isOverlayEnabled) "Activo" else "Inactivo",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOverlayEnabled)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.api_key_title),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Surface(
                                color = when {
                                    apiKeyUiState.isConfigured -> MaterialTheme.colorScheme.primaryContainer
                                    apiKeyUiState.isDegraded -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.errorContainer
                                },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = when {
                                        apiKeyUiState.isConfigured -> "Configurada"
                                        apiKeyUiState.isDegraded -> "Degradada"
                                        else -> "Sin clave"
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        apiKeyUiState.isConfigured -> MaterialTheme.colorScheme.onPrimaryContainer
                                        apiKeyUiState.isDegraded -> MaterialTheme.colorScheme.onTertiaryContainer
                                        else -> MaterialTheme.colorScheme.onErrorContainer
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onStartService,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.start_assistant))
                    }

                    OutlinedButton(
                        onClick = onStopService,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.stop_assistant))
                    }

                    OutlinedButton(
                        onClick = onRequestExtraPermissions,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.setup_grant_offline_permissions))
                    }
                }
            }

            item {
                ApiKeySection(
                    uiState = apiKeyUiState,
                    onInputChange = onApiKeyInputChange,
                    onSaveKey = onApiKeySave,
                    onClearKey = onApiKeyClear,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            item {
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
            }

            item {
                Card(
                    onClick = onNavigateToLocalLlmSettings,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "IA Local", style = MaterialTheme.typography.titleMedium)
                            Text(text = "Inferencia sin conexión", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Card(
                    onClick = onNavigateToIotSettings,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Configuración IoT", style = MaterialTheme.typography.titleMedium)
                            Text(text = "Domótica, salud y vehículo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
    val expectedComponentName = ComponentName(context, service)
    val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    return enabledServices?.contains(expectedComponentName.flattenToString()) == true
}
