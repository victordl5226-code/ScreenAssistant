package com.screenassistant.service.system.action

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.screenassistant.core.domain.model.ClipboardOperation
import com.screenassistant.core.domain.model.SystemCommand
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardAction @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun execute(command: SystemCommand.Clipboard): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        return when (command.operation) {
            ClipboardOperation.COPY -> {
                val text = command.text
                if (text.isNullOrBlank()) {
                    return "Error: ¿Qué quieres que copie?"
                }
                val clip = ClipData.newPlainText("ScreenAssistant", text)
                clipboard.setPrimaryClip(clip)
                "Texto copiado al portapapeles."
            }
            ClipboardOperation.PASTE -> {
                val clip = clipboard.primaryClip
                if (clip == null || clip.itemCount == 0) {
                    "No hay nada en el portapapeles."
                } else {
                    val content = clip.getItemAt(0).text?.toString()
                    if (content.isNullOrBlank()) {
                        "El portapapeles está vacío."
                    } else {
                        "Tengo copiado: ${content.take(200)}"
                    }
                }
            }
            ClipboardOperation.SHOW -> {
                val clip = clipboard.primaryClip
                if (clip == null || clip.itemCount == 0) {
                    "No hay nada en el portapapeles."
                } else {
                    val content = clip.getItemAt(0).text?.toString()
                    if (content.isNullOrBlank()) {
                        "El portapapeles está vacío."
                    } else {
                        "En el portapapeles hay: ${content.take(200)}"
                    }
                }
            }
        }
    }
}
