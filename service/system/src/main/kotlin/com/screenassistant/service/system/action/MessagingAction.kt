package com.screenassistant.service.system.action

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.screenassistant.core.data.local.MessageQueueManager
import com.screenassistant.core.domain.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessagingAction @Inject constructor(
    private val context: Context,
    private val messageQueue: MessageQueueManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    fun openWhatsApp(): String {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                "Éxito: WhatsApp abierto."
            } else {
                "Error: WhatsApp no parece estar instalado."
            }
        } catch (e: Exception) {
            "Error: No se pudo abrir WhatsApp."
        }
    }

    suspend fun queueWhatsApp(contactName: String, message: String, hasNetwork: Boolean, callAction: CallAction): String {
        if (hasNetwork) {
            return openWhatsAppWithText(contactName, message, callAction)
        } else {
            messageQueue.addPendingMessage("WhatsApp", contactName, callAction.findContactNumber(contactName), message)
            return "Error: No tienes internet. He guardado el mensaje para enviarlo a $contactName en cuanto recuperes la conexión."
        }
    }

    private fun openWhatsAppWithText(contactName: String, message: String, callAction: CallAction): String {
        return try {
            val number = callAction.findContactNumber(contactName)
            val uri = if (number != null) {
                Uri.parse("https://api.whatsapp.com/send?phone=$number&text=${Uri.encode(message)}")
            } else {
                Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(message)}")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Éxito: Abriendo WhatsApp para enviar mensaje a $contactName."
        } catch (e: Exception) {
            "Error: No pude abrir WhatsApp."
        }
    }
}
