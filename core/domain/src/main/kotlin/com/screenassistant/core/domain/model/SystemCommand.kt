package com.screenassistant.core.domain.model

sealed class SystemCommand {
    data class Call(val contactName: String) : SystemCommand()
    data class SendSms(val contact: String, val message: String) : SystemCommand()
    data class SetAlarm(val hour: Int, val minute: Int, val label: String?) : SystemCommand()
    // O4: con hora → cancela la alarma de esa hora; sin hora (null/null) → todas (guía general).
    data class CancelAlarm(val hour: Int?, val minute: Int?) : SystemCommand()
    data class OpenApp(val appQuery: String) : SystemCommand()
    data class SearchFile(val query: String) : SystemCommand()
    data class QueueMessage(val platform: String, val contact: String, val message: String) : SystemCommand()
    data object OpenAlarms : SystemCommand()
    data class SearchGoogle(val query: String) : SystemCommand()
    data class OpenYouTube(val query: String?) : SystemCommand()
    data object OpenWhatsApp : SystemCommand()
    data class PlayMusic(val query: String?) : SystemCommand()
    data class SetVolume(val action: VolumeAction) : SystemCommand()
    data class SetLanguage(val language: AssistantLanguage) : SystemCommand()
    data class SetTimer(val minutes: Int) : SystemCommand()
    data class Navigate(val destination: String) : SystemCommand()
    data object OpenSettings : SystemCommand()
    data class CallNumber(val phoneNumber: String) : SystemCommand()
    data class SaveMemory(val fact: String) : SystemCommand()
    data class CreateNote(val text: String) : SystemCommand()
    // N1: "lee mis notas" → resumen de todas; "lee la nota de X" → búsqueda por contenido.
    data object ReadNotes : SystemCommand()
    data class ReadNote(val query: String) : SystemCommand()

    companion object {
        // Fuente única de verdad del límite de caracteres de una nota por voz
        // (el parser NO trunca: el límite lo aplica la acción al guardar).
        const val MAX_NOTE_CHARS: Int = 1000
    }
}

enum class VolumeAction { UP, DOWN, MAX, MIN, MUTE }

enum class AssistantLanguage { SPANISH, ENGLISH }
