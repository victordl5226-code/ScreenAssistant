package com.screenassistant.core.data.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Base de prefs cifradas con fallback (D4, Lote 9): EncryptedSharedPreferences
 * AES256_GCM + degradación a SharedPreferences normales si Keystore/Tink no está
 * disponible. Fuente ÚNICA del patrón que duplicaban [ApiKeyProvider] y
 * [PuenteConfigStore] (~1:1 antes de este lote); un tercer store ya no duplicaría.
 *
 * Comportamiento (idéntico al patrón anterior, para que los consumidores no cambien):
 * - [create] intenta la crypto; en el catch marca `isUsingFallback = true` y usa
 *   `context.getSharedPreferences(fallbackName, MODE_PRIVATE)`. NO es lazy: la
 *   creación explícita se delega al lazy de cada store consumidor.
 * - Todos los métodos degradan con Log.w + valor fail-soft (null / default /
 *   no-op) — nunca propagan excepciones de prefs.
 * - [edit] permite transacciones MULTI-clave con UN ÚNICO `apply()` (requisito
 *   P0-2: `PuenteConfigStore.guardar()` verifica `editor.apply()` exactamente 1).
 */
class EncryptedPrefsStore private constructor(
    private val prefs: SharedPreferences,
    private val tag: String,
    /** true si la construcción cayó al fallback de SharedPreferences. */
    val isUsingFallback: Boolean
) {

    fun getString(key: String): String? = try {
        prefs.getString(key, null)
    } catch (e: Exception) {
        Log.w(tag, "Error leyendo $key: ${e.message}")
        null
    }

    fun getBoolean(key: String, default: Boolean): Boolean = try {
        prefs.getBoolean(key, default)
    } catch (e: Exception) {
        Log.w(tag, "Error leyendo $key: ${e.message}")
        default
    }

    /** null = remove (semántica SharedPreferences). */
    fun putString(key: String, value: String?) = try {
        prefs.edit().putString(key, value).apply()
    } catch (e: Exception) {
        Log.w(tag, "Error guardando $key: ${e.message}")
    }

    fun putBoolean(key: String, value: Boolean) = try {
        prefs.edit().putBoolean(key, value).apply()
    } catch (e: Exception) {
        Log.w(tag, "Error guardando $key: ${e.message}")
    }

    fun remove(key: String) = try {
        prefs.edit().remove(key).apply()
    } catch (e: Exception) {
        Log.w(tag, "Error eliminando $key: ${e.message}")
    }

    /**
     * Obtiene todas las claves que coinciden con el prefijo dado.
     */
    fun getKeysWithPrefix(prefix: String): List<String> = try {
        prefs.all.keys.filter { it.startsWith(prefix) }
    } catch (e: Exception) {
        Log.w(tag, "Error obteniendo claves con prefijo $prefix: ${e.message}")
        emptyList()
    }

    /**
     * Transacción multi-clave con UN SOLO `apply()` (sin estados intermedios
     * observables). Uso: el sembrado B4 de [ApiKeyProvider] y `guardar()` de
     * [PuenteConfigStore] (P0-2).
     */
    fun edit(block: (SharedPreferences.Editor) -> Unit) = try {
        val editor = prefs.edit()
        block(editor)
        editor.apply()
    } catch (e: Exception) {
        Log.w(tag, "Error en transacción de prefs: ${e.message}")
    }

    companion object {
        fun create(
            context: Context,
            prefsName: String,     // "secure_api_prefs" | "secure_puente_prefs"
            fallbackName: String,  // "..._fallback"
            tag: String            // para Log.w
        ): EncryptedPrefsStore {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedPrefsStore(
                    prefs = EncryptedSharedPreferences.create(
                        context,
                        prefsName,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    ),
                    tag = tag,
                    isUsingFallback = false
                )
            } catch (e: Exception) {
                Log.w(tag, "EncryptedSharedPreferences no disponible, usando fallback: ${e.message}")
                EncryptedPrefsStore(
                    prefs = context.getSharedPreferences(fallbackName, Context.MODE_PRIVATE),
                    tag = tag,
                    isUsingFallback = true
                )
            }
        }
    }
}
