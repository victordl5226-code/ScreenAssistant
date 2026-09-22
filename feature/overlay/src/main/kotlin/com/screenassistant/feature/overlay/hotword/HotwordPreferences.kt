package com.screenassistant.feature.overlay.hotword

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.hotwordDataStore by preferencesDataStore(name = "hotword_prefs")

/**
 * Preferencias para el hotword (on/off).
 */
@Singleton
class HotwordPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val enabledKey = booleanPreferencesKey("hotword_enabled")

    /** Flow del estado habilitado/deshabilitado. Default: false */
    val isEnabled: Flow<Boolean> = context.hotwordDataStore.data.map { prefs ->
        prefs[enabledKey] ?: false
    }

    /**
     * Establece si el hotword está habilitado.
     */
    suspend fun setEnabled(enabled: Boolean) {
        context.hotwordDataStore.edit { prefs ->
            prefs[enabledKey] = enabled
        }
    }
}
