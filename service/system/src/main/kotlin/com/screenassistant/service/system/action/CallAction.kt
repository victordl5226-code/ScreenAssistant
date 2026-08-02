package com.screenassistant.service.system.action

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallAction @Inject constructor(
    private val context: Context
) {
    fun makeCall(contactName: String): String {
        return try {
            val number = findContactNumber(contactName)
            if (number != null) {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                "Éxito: Llamando a $contactName..."
            } else {
                "Error: No encontré el número de $contactName en tus contactos."
            }
        } catch (e: Exception) {
            "Error: No tengo permiso para llamar o hubo un error."
        }
    }

    /** Llamada a número directo (sin consultar contactos). Para "llama al 600 123 456". */
    fun makeCallToNumber(phoneNumber: String): String {
        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Éxito: Llamando al $phoneNumber..."
        } catch (e: Exception) {
            "Error: No tengo permiso para llamar o hubo un error."
        }
    }

    fun sendSms(contactName: String, message: String): String {
        return try {
            val number = findContactNumber(contactName) ?: contactName
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$number")
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Éxito: Preparando SMS para $contactName."
        } catch (e: Exception) {
            "Error: No se pudo enviar el SMS."
        }
    }

    fun findContactNumber(name: String): String? {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )
        return cursor?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }
}
