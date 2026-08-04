package com.screenassistant.service.system

import android.content.Context
import com.screenassistant.core.domain.action.SystemAction
import com.screenassistant.core.domain.model.ActionResult
import com.screenassistant.core.domain.model.SystemCommand
import com.screenassistant.service.system.action.AlarmAction
import com.screenassistant.service.system.action.AppLauncherAction
import com.screenassistant.service.system.action.CallAction
import com.screenassistant.service.system.action.LanguageAction
import com.screenassistant.service.system.action.MapsAction
import com.screenassistant.service.system.action.MediaAction
import com.screenassistant.service.system.action.MemoryAction
import com.screenassistant.service.system.action.MessagingAction
import com.screenassistant.service.system.action.NoteAction
import com.screenassistant.service.system.action.SearchAction
import com.screenassistant.service.system.action.SettingsAction
import com.screenassistant.service.system.action.SystemVolumeAction
import com.screenassistant.service.system.action.TimerAction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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
    @ApplicationContext private val context: Context
) : SystemAction {

    override suspend fun execute(command: SystemCommand): ActionResult {
        return try {
            val message = when (command) {
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
                // N1: "lee mis notas" → resumen; "lee la nota de X" → búsqueda por contenido.
                is SystemCommand.ReadNotes -> noteAction.readNotesSummary()
                is SystemCommand.ReadNote -> noteAction.readNote(command.query)
            }
            ActionResult.Success(message)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            // B5: la cancelación de la corrutina (p.ej. al cerrar el overlay mientras
            // se ejecuta una acción) se RE-LANZA siempre — tragar la cancelación
            // dejaría la corrutina viva. Contrato del repo.
            throw e
        } catch (e: Exception) {
            ActionResult.Error(e.message ?: "Error desconocido")
        }
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
