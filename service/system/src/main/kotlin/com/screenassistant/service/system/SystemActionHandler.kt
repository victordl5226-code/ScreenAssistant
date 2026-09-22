package com.screenassistant.service.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.screenassistant.core.domain.action.SystemAction
import com.screenassistant.core.domain.model.*
import com.screenassistant.core.domain.model.actionId
import com.screenassistant.core.domain.repository.ScreenContextRepository
import com.screenassistant.core.domain.util.JarvisResponseFormatter
import com.screenassistant.core.domain.repository.MemoryRepository
import com.screenassistant.core.domain.repository.UserPatternRepository
import com.screenassistant.service.system.action.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orquestador central de acciones del sistema.
 * 
 * Implementa [SystemAction] delegando en clases especializadas para cada tipo de comando.
 * Optimizado para J.A.R.V.I.S. con integración de contexto visual real.
 */
@Singleton
class SystemActionHandler @Inject constructor(
    private val callAction: CallAction,
    private val messagingAction: MessagingAction,
    private val mediaAction: MediaAction,
    private val alarmAction: AlarmAction,
    private val searchAction: SearchAction,
    private val appLauncherAction: AppLauncherAction,
    private val systemVolumeAction: SystemVolumeAction,
    private val timerAction: TimerAction,
    private val mapsAction: MapsAction,
    private val settingsAction: SettingsAction,
    private val languageAction: LanguageAction,
    private val memoryAction: MemoryAction,
    private val noteAction: NoteAction,
    private val calculatorAction: CalculatorAction,
    private val stopwatchActionProvider: StopwatchActionProvider,
    private val deviceInfoAction: DeviceInfoAction,
    private val clipboardAction: ClipboardAction,
    private val contactsAction: ContactsAction,
    private val wifiInfoAction: WifiInfoAction,
    private val bluetoothAction: BluetoothAction,
    private val brightnessAction: BrightnessAction,
    private val flashlightAction: FlashlightAction,
    private val airplaneModeAction: AirplaneModeAction,
    private val mobileDataAction: MobileDataAction,
    private val openFileAction: OpenFileAction,
    private val callHistoryAction: CallHistoryAction,
    private val qrScanAction: QrScanAction,
    private val ocrAction: OcrAction,
    private val translateAction: TranslateAction,
    private val faceDetectionAction: FaceDetectionAction,
    private val vibrationAction: VibrationAction,
    private val locationAction: LocationAction,
    private val wifiToggleAction: WifiToggleAction,
    private val cameraAction: CameraAction,
    private val screenContextRepository: ScreenContextRepository,
    private val memoryRepository: MemoryRepository,
    private val userPatternRepository: UserPatternRepository,
    @ApplicationContext private val context: Context
) : SystemAction {

    private val _assistantMode = MutableStateFlow(AssistantMode.CENTINELA)
    override val assistantMode: StateFlow<AssistantMode> = _assistantMode.asStateFlow()

    override suspend fun execute(command: SystemCommand): ActionResult {
        val result = try {
            val memories = memoryRepository.getAllMemories().first()
            val userName = memoryRepository.getUserName(memories) ?: "Señor"
            val mode = _assistantMode.value

            // Confirmación háptica J.A.R.V.I.S. para acciones críticas
            when (command) {
                is SystemCommand.Call, is SystemCommand.SendSms, is SystemCommand.CallNumber -> {
                    vibrationAction.vibrate(150) 
                }
                else -> {}
            }

            // Captura de contexto visual ANTES del when (sin lazy, sin runBlocking)
            val visualBitmap: Bitmap? = when (command) {
                is SystemCommand.ScanQr,
                is SystemCommand.OcrScan,
                is SystemCommand.DetectFace -> {
                    val imageData = screenContextRepository.captureScreenshot()
                    imageData?.let { BitmapFactory.decodeByteArray(it.data, 0, it.data.size) }
                }
                else -> null
            }

            val rawMessage = when (command) {
                is SystemCommand.Call -> callAction.makeCall(command.contactName)
                is SystemCommand.SendSms -> callAction.sendSms(command.contact, command.message)
                is SystemCommand.SetAlarm -> alarmAction.setAlarm(command.hour, command.minute, command.label)
                is SystemCommand.OpenApp -> appLauncherAction.launchApp(command.appQuery)
                is SystemCommand.SearchFile -> searchAction.searchFile(command.query)
                is SystemCommand.QueueMessage -> messagingAction.queueWhatsApp(
                    command.contact, command.message, hasNetwork(), callAction
                )
                is SystemCommand.OpenAlarms -> alarmAction.openAlarms()
                is SystemCommand.CancelAlarm -> alarmAction.cancelAlarm(command.hour, command.minute)
                is SystemCommand.SearchGoogle -> mediaAction.searchGoogle(command.query)
                is SystemCommand.OpenYouTube -> mediaAction.openYouTube(command.query)
                is SystemCommand.OpenWhatsApp -> messagingAction.openWhatsApp()
                is SystemCommand.PlayMusic -> mediaAction.playMusic(command.query)
                is SystemCommand.SetVolume -> systemVolumeAction.setVolume(command.action)
                is SystemCommand.SetLanguage -> languageAction.setLanguage(command.language)
                is SystemCommand.SetTimer -> timerAction.setTimer(command.minutes)
                is SystemCommand.Navigate -> mapsAction.navigateTo(command.destination)
                is SystemCommand.OpenSettings -> settingsAction.openSettings()
                is SystemCommand.CallNumber -> callAction.makeCallToNumber(command.phoneNumber)
                is SystemCommand.SaveMemory -> memoryAction.saveMemory(command.fact)
                is SystemCommand.CreateNote -> noteAction.saveNote(command.text)
                is SystemCommand.ReadNotes -> noteAction.readNotesSummary()
                is SystemCommand.ReadNote -> noteAction.readNote(command.query)
                
                // Capacidades Offline
                is SystemCommand.Calculate -> calculatorAction.calculate(command)
                // MATH (ADR-MATH, P3): expresión libre — Calculate binario intacto.
                is SystemCommand.CalculateExpression -> calculatorAction.calculateExpression(command)
                is SystemCommand.Stopwatch -> stopwatchActionProvider.execute(command.action)
                is SystemCommand.DeviceInfo -> deviceInfoAction.getInfo(command.type)
                is SystemCommand.Clipboard -> clipboardAction.execute(command)
                is SystemCommand.ListContacts -> contactsAction.listContacts()
                is SystemCommand.GetWifiInfo -> wifiInfoAction.getWifiInfo()
                
                // Hardware y Ajustes
                is SystemCommand.SetBluetooth -> bluetoothAction.setBluetooth(command.enabled)
                is SystemCommand.SetBrightness -> brightnessAction.setBrightness(command.level)
                is SystemCommand.SetFlashlight -> flashlightAction.setFlashlight(command.enabled)
                is SystemCommand.SetAirplaneMode -> airplaneModeAction.setAirplaneMode(command.enabled)
                is SystemCommand.SetMobileData -> mobileDataAction.setMobileData(command.enabled)
                is SystemCommand.OpenFile -> openFileAction.openFile(command.query)
                is SystemCommand.CallHistory -> callHistoryAction.getCallHistory()
                
                // Inteligencia Visual (Optimizado con contexto de pantalla real)
                is SystemCommand.ScanQr -> qrScanAction.scanQr(visualBitmap)
                is SystemCommand.OcrScan -> ocrAction.scanText(visualBitmap)
                is SystemCommand.TranslateText -> translateAction.translate(command.text, command.targetLanguage)
                is SystemCommand.DetectFace -> faceDetectionAction.detectFace(visualBitmap)
                
                // Hardware Directo
                is SystemCommand.Vibrate -> vibrationAction.vibrate(command.durationMs)
                is SystemCommand.GetLocation -> locationAction.getCurrentLocation()
                is SystemCommand.SetWifi -> wifiToggleAction.setWifi(command.enabled)
                is SystemCommand.TakePhoto -> cameraAction.takePhoto(command.useFrontCamera)

                // Personalidad J.A.R.V.I.S.
                is SystemCommand.SetAssistantMode -> {
                    _assistantMode.value = command.mode
                    "Protocolo ${command.mode.name} activado."
                }
            }

            val formatted = if (rawMessage.startsWith("Error:")) {
                JarvisResponseFormatter.formatError(rawMessage.removePrefix("Error:").trim(), userName, mode)
            } else {
                JarvisResponseFormatter.formatSuccess(rawMessage.removePrefix("Éxito:").trim(), userName, mode)
            }

            ActionResult.Success(formatted)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("SystemActionHandler", "Protocolo fallido: ${command.actionId}", e)
            ActionResult.Error("No pude procesar la solicitud de sistema, Señor.")
        }

        // Fire-and-forget: tracking de patrones no bloquea ni contamina el resultado
        try {
            userPatternRepository.trackAction(command.actionId)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e  // Respetar structured concurrency
        } catch (e: Exception) {
            android.util.Log.w("SystemActionHandler", "Tracking fallido para ${command.actionId}")
        }

        return result
    }

    private fun hasNetwork(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && (
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
        )
    }
}
