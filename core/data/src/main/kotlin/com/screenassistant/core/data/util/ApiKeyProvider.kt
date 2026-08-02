package com.screenassistant.core.data.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "ApiKeyProvider"

    // true si la construcción de prefs cayó al fallback de SharedPreferences
    // (Keystore/Tink no disponible en el dispositivo). Solo se marca dentro
    // del catch del lazy: el happy path deja el valor en false.
    var isUsingFallback: Boolean = false
        private set

    // Blinda contra fallos del Keystore/Tink en dispositivos sin soporte.
    // Si EncryptedSharedPreferences falla, degrada a SharedPreferences normales.
    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "secure_api_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            isUsingFallback = true
            Log.w(tag, "EncryptedSharedPreferences no disponible, usando fallback: ${e.message}")
            context.getSharedPreferences("secure_api_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    fun getApiKey(): String {
        return try {
            val storedKey = prefs.getString("gemini_api_key", null)
            if (!storedKey.isNullOrBlank()) storedKey else ""
        } catch (e: Exception) {
            Log.w(tag, "Error leyendo API key: ${e.message}")
            ""
        }
    }

    fun storeApiKey(key: String) {
        try {
            prefs.edit().putString("gemini_api_key", key).apply()
        } catch (e: Exception) {
            Log.w(tag, "Error guardando API key: ${e.message}")
        }
    }

    fun clearApiKey() {
        try {
            prefs.edit().remove("gemini_api_key").apply()
        } catch (e: Exception) {
            Log.w(tag, "Error eliminando API key: ${e.message}")
        }
    }
}
