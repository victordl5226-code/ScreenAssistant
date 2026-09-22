package com.screenassistant.service.system.action

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenFileAction @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun openFile(query: String): String {
        if (query.isBlank()) {
            return "Error: ¿Qué archivo quieres abrir?"
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        val cursor = context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val id = it.getLong(0)
                val name = it.getString(1)
                val mimeType = it.getString(2) ?: "*/*"

                val uri = Uri.withAppendedPath(
                    MediaStore.Files.getContentUri("external"),
                    id.toString()
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return "Abriendo archivo: $name"
            }
        }

        return "Error: No encontré ningún archivo que coincida con '$query'."
    }
}
