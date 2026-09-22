package com.screenassistant.service.system.action

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallHistoryAction @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MAX_CALLS_DISPLAYED = 10
    }

    fun getCallHistory(): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return "Error: No tengo permiso para leer el historial de llamadas."
        }

        val calls = mutableListOf<String>()
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE
            ),
            null, null,
            "${CallLog.Calls.DATE} DESC"
        )

        cursor?.use {
            while (it.moveToNext() && calls.size < MAX_CALLS_DISPLAYED) {
                val name = it.getString(0) ?: it.getString(1) ?: "Desconocido"
                val type = when (it.getInt(2)) {
                    CallLog.Calls.INCOMING_TYPE -> "Recibida"
                    CallLog.Calls.OUTGOING_TYPE -> "Saliente"
                    CallLog.Calls.MISSED_TYPE -> "Perdida"
                    CallLog.Calls.VOICEMAIL_TYPE -> "Voz"
                    else -> "Otra"
                }
                calls.add("$type: $name")
            }
        }

        if (calls.isEmpty()) {
            return "No hay llamadas recientes."
        }

        val list = calls.joinToString("\n")
        return "Últimas llamadas:\n$list"
    }
}
