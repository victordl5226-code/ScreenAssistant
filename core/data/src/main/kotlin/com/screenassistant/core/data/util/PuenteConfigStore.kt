package com.screenassistant.core.data.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistencia cifrada de la config del puente (Lote 7, ADR-015): patrón EXACTO
 * de [ApiKeyProvider] (MISMO paquete, MISMA estructura): EncryptedSharedPreferences
 * con MasterKey AES256_GCM + fallback a prefs normales con [isUsingFallback].
 *
 * H1: [cargar] materializa el default de F2 — clave "packageRespuesta" ausente o
 * blank → [PuenteConfig.PACKAGE_RESPUESTA_DEFAULT]. El `null` de la data class es
 * solo el valor en crudo de guardado ([guardar] persiste lo que recibe, tal cual).
 *
 * v1.2 (rediseño F1/F3): 4 claves — packageRespuesta, autoRemoteKey,
 * tokenCompartido (F1: extra "token"; blank = canal abierto) y enviarRespuestaURL
 * (F3: flag explícito, default false). ELIMINADAS: "allowlistActivada" y
 * "paquetesPermitidos" (y la re-normalización de paquetes al leer — muertos con
 * el veto B1/H1, ADR-015 §2.6). El token se persiste TAL CUAL; el trim lo hace
 * la UI/VM (guardar recibe lo que el emisor ya normalizó).
 *
 * Lectura síncrona (SharedPreferences mmap, microsegundos) desde el handler/emitter
 * en el hilo IO del receiver — sin coste medible (precedente: AppModule lee
 * ApiKeyProvider síncrono).
 *
 * O2: NO lleva @Provides — creación ÚNICA vía @Inject constructor + @Singleton
 * (Hilt resuelve; unificación de vías, precedente TaskerMessageHandlerImpl).
 */
@Singleton
class PuenteConfigStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "PuenteConfigStore"

    // true si la construcción de prefs cayó al fallback de SharedPreferences
    // (Keystore/Tink no disponible en el dispositivo). Solo se marca dentro
    // del catch del lazy: el happy path deja el valor en false.
    var isUsingFallback: Boolean = false
        private set

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "secure_puente_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            isUsingFallback = true
            Log.w(tag, "EncryptedSharedPreferences no disponible, usando fallback: ${e.message}")
            context.getSharedPreferences("secure_puente_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    /**
     * Lee la config completa. H1: packageRespuesta ausente/blank → default
     * [PuenteConfig.PACKAGE_RESPUESTA_DEFAULT] (el consumidor NUNCA ve null/blank
     * en producción). v1.2: tokenCompartido ausente → "" (canal abierto);
     * enviarRespuestaURL ausente → false (flag OFF por defecto).
     */
    fun cargar(): PuenteConfig {
        return try {
            val packageRespuesta = prefs.getString("packageRespuesta", null)
            PuenteConfig(
                packageRespuesta = packageRespuesta?.takeIf { it.isNotBlank() }
                    ?: PuenteConfig.PACKAGE_RESPUESTA_DEFAULT,
                autoRemoteKey = prefs.getString("autoRemoteKey", "") ?: "",
                tokenCompartido = prefs.getString("tokenCompartido", "") ?: "",
                enviarRespuestaURL = prefs.getBoolean("enviarRespuestaURL", false),
            )
        } catch (e: Exception) {
            Log.w(tag, "Error leyendo config del puente: ${e.message}")
            // Mismo invariante H1 en el camino defensivo: nunca null/blank.
            PuenteConfig(packageRespuesta = PuenteConfig.PACKAGE_RESPUESTA_DEFAULT)
        }
    }

    /**
     * Guarda la config tal cual (crudo): un packageRespuesta blank/null se persiste
     * como clave ausente (putString null la elimina en Android real) y [cargar]
     * materializa el default — el modo "global (sin setPackage)" no es alcanzable
     * desde la UI (H1). v1.2: 4 setters con apply() — packageRespuesta (crudo),
     * autoRemoteKey, tokenCompartido (tal cual; el trim lo hace la UI/VM),
     * enviarRespuestaURL.
     */
    fun guardar(config: PuenteConfig) {
        try {
            prefs.edit()
                .putString("packageRespuesta", config.packageRespuesta)
                .putString("autoRemoteKey", config.autoRemoteKey)
                .putString("tokenCompartido", config.tokenCompartido)
                .putBoolean("enviarRespuestaURL", config.enviarRespuestaURL)
                .apply()
        } catch (e: Exception) {
            Log.w(tag, "Error guardando config del puente: ${e.message}")
        }
    }
}
