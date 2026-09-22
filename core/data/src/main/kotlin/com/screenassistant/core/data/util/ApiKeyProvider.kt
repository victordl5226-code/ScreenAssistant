package com.screenassistant.core.data.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API key de Gemini cifrada (D4, Lote 9): cáscara fina sobre [EncryptedPrefsStore]
 * (fuente única del patrón cifrado+fallback; ver KDoc de la base). API pública
 * INTACTA — los consumidores (AppModule, VMs) no cambian.
 */
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
    // El lazy queda en el CONSUMIDOR (comportamiento idéntico al previo):
    // la crypto real se toca en el primer acceso a prefs, no en el arranque.
    private val store: EncryptedPrefsStore by lazy {
        EncryptedPrefsStore.create(
            context,
            "secure_api_prefs",
            "secure_api_prefs_fallback",
            tag
        ).also { created ->
            isUsingFallback = created.isUsingFallback
        }
    }

    fun getApiKey(): String {
        // getString de la base ya degrada con null en fallo (Log.w interno).
        return store.getString("openrouter_api_key")?.takeIf { it.isNotBlank() } ?: ""
    }

    /**
     * Persiste la key de Gemini.
     *
     * M18 (Lote 10) — CONTRATO fail-soft, aceptado por diseño (v1.2a): si la
     * persistencia falla (Keystore/Tink/IO), la excepción se traga con Log.w en la
     * base (EncryptedPrefsStore) y la VM de API key marca "guardado" sin saber —
     * un reinicio posterior puede mostrar la key previa. El contrato del store es
     * degradar, nunca lanzar; los consumidores NO deben asumir persistencia real
     * tras storeApiKey (validar con [getApiKey] si el dato es crítico).
     */
    fun storeApiKey(key: String) {
        store.putString("openrouter_api_key", key)
    }

    // B4 (Lote 8): siembra inicial desde BuildConfig en el arranque de la app.
    // El flag api_key_seeded evita re-sembrar si el usuario la BORRÓ a propósito
    // (clearApiKey no borra el flag: una vez sembrada, el control es del usuario).
    // Un solo edit() con ambas claves: sin estados intermedios observables (edit
    // de la base = UN ÚNICO apply(), requisito P0-2).
    fun sembrarDesdeBuildConfig(buildConfigKey: String) {
        if (buildConfigKey.isBlank()) return
        if (store.getBoolean("api_key_seeded", false)) return
        store.edit { editor ->
            editor.putBoolean("api_key_seeded", true)
                .putString("openrouter_api_key", buildConfigKey)
        }
    }

    fun clearApiKey() {
        // B4: NO se borra api_key_seeded (el usuario la borró → no re-sembrar).
        store.remove("openrouter_api_key")
    }
}
