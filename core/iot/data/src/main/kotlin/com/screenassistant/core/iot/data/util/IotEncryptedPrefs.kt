package com.screenassistant.core.iot.data.util

import android.content.Context
import com.screenassistant.core.data.util.EncryptedPrefsStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferencias cifradas para datos sensibles de IoT.
 *
 * Reutiliza el patrón [EncryptedPrefsStore] de core:data para almacenar:
 * - Tokens de Home Assistant (Long-Lived Access Tokens)
 * - Credenciales de Matter/Thread
 * - Configuración de puentes IoT
 * - Claves de API de servicios cloud
 *
 * Cada tipo de dato sensible tiene su store aislado con fallback seguro.
 */
@Singleton
class IotEncryptedPrefs @Inject constructor(
    private val context: Context,
) {

    /** Store para tokens de Home Assistant. */
    private val haTokenStore = EncryptedPrefsStore.create(
        context,
        prefsName = "iot_ha_tokens",
        fallbackName = "iot_ha_tokens_fallback",
        tag = "IoTHATokens",
    )

    /** Store para credenciales Matter. */
    private val matterCredsStore = EncryptedPrefsStore.create(
        context,
        prefsName = "iot_matter_creds",
        fallbackName = "iot_matter_creds_fallback",
        tag = "IoTMatterCreds",
    )

    /** Store para configuración de puentes IoT. */
    private val bridgeConfigStore = EncryptedPrefsStore.create(
        context,
        prefsName = "iot_bridge_config",
        fallbackName = "iot_bridge_config_fallback",
        tag = "IoTBridgeConfig",
    )

    /** Store genérico para otras credenciales IoT. */
    private val genericStore = EncryptedPrefsStore.create(
        context,
        prefsName = "iot_generic_secrets",
        fallbackName = "iot_generic_secrets_fallback",
        tag = "IoTGenericSecrets",
    )

    // --- Home Assistant Tokens ---

    /**
     * Guarda el token de acceso de larga duración de Home Assistant.
     */
    fun saveHaToken(token: String) {
        haTokenStore.putString("access_token", token)
    }

    /**
     * Obtiene el token de Home Assistant.
     */
    fun getHaToken(): String? = haTokenStore.getString("access_token")

    /**
     * Guarda la URL base de Home Assistant.
     */
    fun saveHaBaseUrl(url: String) {
        haTokenStore.putString("base_url", url)
    }

    /**
     * Obtiene la URL base de Home Assistant.
     */
    fun getHaBaseUrl(): String? = haTokenStore.getString("base_url")

    /**
     * Elimina credenciales de Home Assistant (logout).
     */
    fun clearHaCredentials() {
        haTokenStore.remove("access_token")
        haTokenStore.remove("base_url")
    }

    // --- Matter Credentials ---

    /**
     * Guarda credenciales de comisión Matter (PIN, discriminator, etc.).
     */
    fun saveMatterCommissioningData(
        pinCode: String,
        discriminator: Int,
        vendorId: Int,
        productId: Int,
    ) {
        matterCredsStore.edit { editor ->
            editor.putString("pin_code", pinCode)
            editor.putInt("discriminator", discriminator)
            editor.putInt("vendor_id", vendorId)
            editor.putInt("product_id", productId)
        }
    }

    /**
     * Obtiene el PIN de comisión Matter.
     */
    fun getMatterPinCode(): String? = matterCredsStore.getString("pin_code")

    /**
     * Obtiene el discriminator Matter.
     */
    fun getMatterDiscriminator(): Int? = matterCredsStore.getString("discriminator")?.toIntOrNull()

    // --- Bridge Configuration ---

    /**
     * Guarda configuración de un puente IoT (ej: MQTT broker, Matter bridge).
     */
    fun saveBridgeConfig(bridgeId: String, configJson: String) {
        bridgeConfigStore.putString("bridge_$bridgeId", configJson)
    }

    /**
     * Obtiene configuración de un puente IoT.
     */
    fun getBridgeConfig(bridgeId: String): String? = bridgeConfigStore.getString("bridge_$bridgeId")

    /**
     * Lista todos los puentes configurados.
     */
    fun getConfiguredBridgeIds(): List<String> {
        return bridgeConfigStore.getKeysWithPrefix("bridge_")
            .map { it.substringAfter("bridge_") }
    }

    // --- Generic Secrets ---

    /**
     * Guarda un secreto genérico (API key, certificate, etc.).
     */
    fun saveSecret(key: String, value: String) {
        genericStore.putString(key, value)
    }

    /**
     * Obtiene un secreto genérico.
     */
    fun getSecret(key: String): String? = genericStore.getString(key)

    /**
     * Elimina un secreto genérico.
     */
    fun deleteSecret(key: String) {
        genericStore.remove(key)
    }

    /**
     * Verifica si se está usando fallback (SharedPreferences sin cifrar).
     * Útil para logging/telemetría de seguridad.
     */
    val isUsingFallback: Boolean
        get() = haTokenStore.isUsingFallback || matterCredsStore.isUsingFallback ||
                bridgeConfigStore.isUsingFallback || genericStore.isUsingFallback
}