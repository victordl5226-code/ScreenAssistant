package com.screenassistant.service.system.action

import android.content.Context
import android.provider.MediaStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchAction @Inject constructor(
    private val context: Context
) {
    fun searchFile(fileName: String): String {
        return try {
            val projection = arrayOf(MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.DISPLAY_NAME)
            val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$fileName%")

            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME))
                    "Éxito: Encontré: $name en $path"
                } else {
                    "Error: No encontré ningún archivo con ese nombre."
                }
            } ?: "Error: No se pudo acceder a los archivos."
        } catch (e: Exception) {
            // No exponer la excepción cruda al usuario (O7)
            "Error: No se pudo buscar archivos."
        }
    }
}
