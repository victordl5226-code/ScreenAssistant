package com.screenassistant.core.nlp.parser

import com.screenassistant.core.domain.action.SystemAction
import com.screenassistant.core.domain.model.*
import com.screenassistant.core.domain.nlp.LocalNlpEngine
import com.screenassistant.core.domain.nlp.model.NlpEntity
import com.screenassistant.core.domain.nlp.model.NlpIntent
import com.screenassistant.core.domain.nlp.model.NlpResult
import com.screenassistant.core.domain.repository.MemoryRepository
import com.screenassistant.core.domain.util.JarvisResponseFormatter
import kotlinx.coroutines.flow.first

/**
 * Adaptador que convierte NlpResult → SystemCommand → ejecución.
 *
 * Permite usar LocalNlpEngine como PRIMER PASO antes del parser legacy.
 * Si NlpResult.esReconocido es true, ejecuta el SystemCommand correspondiente.
 * Si no, retorna null (se delega al parser legacy).
 *
 * CONTRATO: parse(texto) retorna String? (resultado de ejecución)
 * - "Éxito: ..." si el comando se ejecutó correctamente
 * - "Error: ..." si hubo un error
 * - null si el NLP no pudo resolver (fallback al parser legacy)
 */
class NlpCommandParser(
    private val nlpEngine: LocalNlpEngine,
    private val systemAction: SystemAction,
    private val memoryRepository: MemoryRepository
) {

    /** Procesa el texto y retorna el resultado de ejecución. */
    suspend fun parse(texto: String): String? {
        val resultado = nlpEngine.procesar(texto)

        if (!resultado.esReconocido || resultado.necesitaParserLegacy) {
            return null
        }

        val comando = mapearASystemCommand(resultado) ?: return null
        
        val memories = memoryRepository.getAllMemories().first()
        val userName = memoryRepository.getUserName(memories) ?: "Señor"
        val mode = systemAction.assistantMode.value

        val rawResponse = ejecutar(comando)
        
        return if (rawResponse.startsWith("Error:")) {
            JarvisResponseFormatter.formatError(rawResponse.removePrefix("Error:").trim(), userName, mode)
        } else {
            JarvisResponseFormatter.formatSuccess(rawResponse.removePrefix("Éxito:").trim(), userName, mode)
        }
    }

    /**
     * Mapea un NlpResult a un SystemCommand ejecutable.
     * Retorna null si la intención no se puede mapear (markers UI, etc.).
     */
    private fun mapearASystemCommand(resultado: NlpResult): SystemCommand? {
        val entidades = resultado.entidades
        return when (resultado.intent) {
            // === Notas ===
            NlpIntent.CREATE_NOTE -> {
                val texto = entidades.buscarTexto() ?: return null
                SystemCommand.CreateNote(texto)
            }
            NlpIntent.READ_NOTE -> {
                val query = entidades.buscarTexto() ?: return null
                SystemCommand.ReadNote(query)
            }
            NlpIntent.READ_ALL_NOTES -> SystemCommand.ReadNotes

            // === Llamadas ===
            NlpIntent.CALL_CONTACT -> {
                val nombre = entidades.buscarContacto() ?: return null
                SystemCommand.Call(nombre)
            }
            NlpIntent.CALL_NUMBER -> {
                val numero = entidades.buscarTelefono() ?: return null
                SystemCommand.CallNumber(numero)
            }
            NlpIntent.CALL_HISTORY -> SystemCommand.CallHistory
            NlpIntent.LIST_CONTACTS -> SystemCommand.ListContacts

            // === Alarmas ===
            NlpIntent.SET_ALARM -> {
                val hora = entidades.buscarHora() ?: return null
                val label = entidades.buscarTexto()?.takeIf { it.isNotBlank() }
                SystemCommand.SetAlarm(hora.hora, hora.minuto, label)
            }
            NlpIntent.CANCEL_ALARM -> {
                val hora = entidades.buscarHora()
                SystemCommand.CancelAlarm(hora?.hora, hora?.minuto)
            }
            NlpIntent.OPEN_ALARMS -> SystemCommand.OpenAlarms

            // === Temporizador ===
            NlpIntent.SET_TIMER -> {
                val duracion = entidades.buscarDuracion() ?: return null
                val minutos = duracion.minutos
                if (minutos !in SystemCommand.TIMER_MIN_MINUTOS..SystemCommand.TIMER_MAX_MINUTOS) {
                    return null
                }
                SystemCommand.SetTimer(minutos)
            }

            // === Apps ===
            NlpIntent.OPEN_APP -> {
                val query = entidades.buscarAplicacion() ?: entidades.buscarTexto() ?: return null
                SystemCommand.OpenApp(query)
            }
            NlpIntent.OPEN_SETTINGS -> SystemCommand.OpenSettings
            NlpIntent.SEARCH_GOOGLE -> {
                val query = entidades.buscarTexto() ?: return null
                SystemCommand.SearchGoogle(query)
            }

            // === Sistema ===
            NlpIntent.PLAY_MUSIC -> {
                val query = entidades.buscarTexto()
                SystemCommand.PlayMusic(query)
            }
            NlpIntent.SET_VOLUME -> {
                val accion = entidades.buscarAccionVolumen() ?: return null
                SystemCommand.SetVolume(accion)
            }
            NlpIntent.SET_LANGUAGE -> {
                val idioma = entidades.buscarIdioma() ?: return null
                SystemCommand.SetLanguage(idioma)
            }
            NlpIntent.NAVIGATE -> {
                val destino = entidades.buscarDestino() ?: return null
                SystemCommand.Navigate(destino)
            }
            NlpIntent.SAVE_MEMORY -> {
                val texto = entidades.buscarTexto() ?: return null
                SystemCommand.SaveMemory(texto)
            }
            NlpIntent.OPEN_FILE -> {
                val query = entidades.buscarTexto() ?: return null
                SystemCommand.OpenFile(query)
            }

            // === Hardware ===
            NlpIntent.SET_BLUETOOTH -> {
                val on = entidades.buscarOnOff() ?: return null
                SystemCommand.SetBluetooth(on)
            }
            NlpIntent.SET_BRIGHTNESS -> {
                val nivel = entidades.buscarNumero() ?: return null
                SystemCommand.SetBrightness(nivel)
            }
            NlpIntent.SET_FLASHLIGHT -> {
                val on = entidades.buscarOnOff() ?: return null
                SystemCommand.SetFlashlight(on)
            }
            NlpIntent.SET_AIRPLANE_MODE -> {
                val on = entidades.buscarOnOff() ?: return null
                SystemCommand.SetAirplaneMode(on)
            }
            NlpIntent.SET_MOBILE_DATA -> {
                val on = entidades.buscarOnOff() ?: return null
                SystemCommand.SetMobileData(on)
            }
            NlpIntent.SET_WIFI -> {
                val on = entidades.buscarOnOff() ?: return null
                SystemCommand.SetWifi(on)
            }
            NlpIntent.VIBRATE -> {
                val ms = entidades.buscarVibracion()?.duracionMs ?: 500L
                SystemCommand.Vibrate(ms)
            }
            NlpIntent.GET_LOCATION -> SystemCommand.GetLocation
            NlpIntent.TAKE_PHOTO -> {
                val frontal = entidades.buscarFoto()?.frontal ?: false
                SystemCommand.TakePhoto(frontal)
            }

            // === Dispositivo ===
            NlpIntent.DEVICE_INFO_MODEL -> SystemCommand.DeviceInfo(DeviceInfoType.MODEL)
            NlpIntent.DEVICE_INFO_BATTERY -> SystemCommand.DeviceInfo(DeviceInfoType.BATTERY)
            NlpIntent.DEVICE_INFO_STORAGE -> SystemCommand.DeviceInfo(DeviceInfoType.STORAGE)
            NlpIntent.GET_WIFI_INFO -> SystemCommand.GetWifiInfo
            NlpIntent.CLIPBOARD_COPY -> {
                val texto = entidades.buscarTexto() ?: return null
                SystemCommand.Clipboard(ClipboardOperation.COPY, texto)
            }
            NlpIntent.CLIPBOARD_PASTE -> SystemCommand.Clipboard(ClipboardOperation.PASTE)
            NlpIntent.CLIPBOARD_SHOW -> SystemCommand.Clipboard(ClipboardOperation.SHOW)
            NlpIntent.CALCULATOR -> {
                val expr = entidades.buscarExpresionCalculadora() ?: return null
                SystemCommand.Calculate(expr.operando1, expr.operador, expr.operando2)
            }
            NlpIntent.STOPWATCH_START -> SystemCommand.Stopwatch(StopwatchAction.START)
            NlpIntent.STOPWATCH_STOP -> SystemCommand.Stopwatch(StopwatchAction.STOP)
            NlpIntent.STOPWATCH_GET_TIME -> SystemCommand.Stopwatch(StopwatchAction.GET_TIME)

            // === ML Kit ===
            NlpIntent.SCAN_QR -> SystemCommand.ScanQr
            NlpIntent.OCR_SCAN -> SystemCommand.OcrScan
            NlpIntent.TRANSLATE_TEXT -> {
                val texto = entidades.buscarTexto() ?: return null
                SystemCommand.TranslateText(texto, TranslateLanguage.ENGLISH)
            }
            NlpIntent.DETECT_FACE -> SystemCommand.DetectFace

            // === SMS ===
            NlpIntent.SEND_SMS -> {
                val contacto = entidades.buscarContacto() ?: return null
                val mensaje = entidades.buscarTexto() ?: return null
                SystemCommand.SendSms(contacto, mensaje)
            }

            // === UI Markers → null (el OverlayViewModel los maneja directamente) ===
            NlpIntent.HELP, NlpIntent.REPEAT,
            NlpIntent.START_MONITORING, NlpIntent.STOP_MONITORING,
            NlpIntent.ANALYZE_SCREEN,
            NlpIntent.UNKNOWN -> null
        }
    }

    /** Ejecuta un SystemCommand y retorna el resultado como String. */
    private suspend fun ejecutar(comando: SystemCommand): String {
        return when (val resultado = systemAction.execute(comando)) {
            is ActionResult.Success -> resultado.message
            is ActionResult.Error -> "Error: ${resultado.reason}"
        }
    }

    // ── Extensiones helper para buscar entidades ──

    private fun List<NlpEntity>.buscarTexto(): String? =
        (find { it is NlpEntity.Texto } as? NlpEntity.Texto)?.contenido

    private fun List<NlpEntity>.buscarContacto(): String? =
        (find { it is NlpEntity.Contacto } as? NlpEntity.Contacto)?.nombre

    private fun List<NlpEntity>.buscarTelefono(): String? =
        (find { it is NlpEntity.Telefono } as? NlpEntity.Telefono)?.numero

    private fun List<NlpEntity>.buscarHora(): NlpEntity.Hora? =
        find { it is NlpEntity.Hora } as? NlpEntity.Hora

    private fun List<NlpEntity>.buscarAplicacion(): String? =
        (find { it is NlpEntity.Aplicacion } as? NlpEntity.Aplicacion)?.consulta

    private fun List<NlpEntity>.buscarDestino(): String? =
        (find { it is NlpEntity.Destino } as? NlpEntity.Destino)?.destino

    private fun List<NlpEntity>.buscarNumero(): Int? =
        (find { it is NlpEntity.Numero } as? NlpEntity.Numero)?.valor

    private fun List<NlpEntity>.buscarDuracion(): NlpEntity.Duracion? =
        find { it is NlpEntity.Duracion } as? NlpEntity.Duracion

    private fun List<NlpEntity>.buscarAccionVolumen(): VolumeAction? =
        (find { it is NlpEntity.AccionVolumen } as? NlpEntity.AccionVolumen)?.accion

    private fun List<NlpEntity>.buscarOnOff(): Boolean? =
        (find { it is NlpEntity.OnOff } as? NlpEntity.OnOff)?.activado

    private fun List<NlpEntity>.buscarIdioma(): AssistantLanguage? =
        (find { it is NlpEntity.Idioma } as? NlpEntity.Idioma)?.idioma

    private fun List<NlpEntity>.buscarExpresionCalculadora(): NlpEntity.ExpresionCalculadora? =
        find { it is NlpEntity.ExpresionCalculadora } as? NlpEntity.ExpresionCalculadora

    private fun List<NlpEntity>.buscarVibracion(): NlpEntity.Vibracion? =
        find { it is NlpEntity.Vibracion } as? NlpEntity.Vibracion

    private fun List<NlpEntity>.buscarFoto(): NlpEntity.Foto? =
        find { it is NlpEntity.Foto } as? NlpEntity.Foto
}
