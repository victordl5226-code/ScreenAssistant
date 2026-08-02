package com.screenassistant.service.system.action

import android.content.Context
import android.content.Intent
import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaAction @Inject constructor(
    private val context: Context
) {
    fun searchGoogle(query: String): String {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Éxito: Buscando '$query' en Google."
        } catch (e: Exception) {
            "Error: No se pudo abrir el buscador."
        }
    }

    fun openYouTube(query: String?): String {
        return try {
            val uri = if (query != null) {
                Uri.parse("vnd.youtube:results?search_query=$query")
            } else {
                Uri.parse("vnd.youtube://")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Éxito: YouTube abierto."
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(webIntent)
            "Éxito: YouTube abierto en el navegador."
        }
    }

    fun playMusic(query: String?): String {
        return try {
            val intent = if (query != null) {
                Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=$query")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } else {
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MUSIC)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            context.startActivity(intent)
            "Éxito: Reproductor de música abierto."
        } catch (e: Exception) {
            "Error: No se pudo abrir la aplicación de música."
        }
    }
}
