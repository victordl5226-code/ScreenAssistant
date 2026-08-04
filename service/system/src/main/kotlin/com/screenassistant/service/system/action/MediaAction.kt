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
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e  // B5: la cancelación nunca se traga
        } catch (e: Exception) {
            "Error: No se pudo abrir el buscador."
        }
    }

    fun openYouTube(query: String?): String {
        return try {
            val uri = if (query != null) {
                // M9: la query se codifica (espacios/`&`/`?` → URIs malformados sin
                // Uri.encode; p. ej. "gato y perro" partía la query en el intent).
                Uri.parse("vnd.youtube:results?search_query=${Uri.encode(query)}")
            } else {
                Uri.parse("vnd.youtube://")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Éxito: YouTube abierto."
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e  // B5: la cancelación nunca se traga (tampoco en el fallback)
        } catch (e: Exception) {
            // M9: el fallback no puede relanzar startActivity fuera de ningún try
            // (excepción cruda al usuario); se protege con su propio try/catch y
            // devuelve un error ADR-009 si el navegador tampoco existe.
            try {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query ?: "")}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
                "Éxito: YouTube abierto en el navegador."
            } catch (e2: kotlin.coroutines.cancellation.CancellationException) {
                throw e2  // B5: la cancelación nunca se traga
            } catch (e2: Exception) {
                "Error: No se pudo abrir YouTube."
            }
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
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e  // B5: la cancelación nunca se traga
        } catch (e: Exception) {
            "Error: No se pudo abrir la aplicación de música."
        }
    }
}
