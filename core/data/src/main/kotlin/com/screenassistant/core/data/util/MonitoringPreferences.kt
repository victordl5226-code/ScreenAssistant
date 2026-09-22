package com.screenassistant.core.data.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore Preferences para configuración de monitoreo (Lote 11).
 * Persiste el intervalo de monitoreo entre reinicios de la app.
 *
 * El delegate [monitoringDataStore] DEBE estar en el nivel superior del archivo
 * (recomendación oficial de DataStore). No puede estar dentro de un object/class.
 */
// Singleton DataStore — top-level (patrón oficial Android).
private val Context.monitoringDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "monitoring_prefs"
)

object MonitoringPreferences {

    private val INTERVAL_KEY = longPreferencesKey("monitoring_interval_ms")

    const val DEFAULT_INTERVAL_MS_LONG = 5000L

    fun intervalFlow(context: Context): Flow<Long> =
        context.monitoringDataStore.data
            .map { prefs -> prefs[INTERVAL_KEY] ?: DEFAULT_INTERVAL_MS_LONG }
            .distinctUntilChanged()

    suspend fun getInterval(context: Context): Long =
        context.monitoringDataStore.data
            .map { prefs -> prefs[INTERVAL_KEY] ?: DEFAULT_INTERVAL_MS_LONG }
            .first()

    suspend fun setInterval(context: Context, intervalMs: Long) {
        context.monitoringDataStore.edit { prefs ->
            prefs[INTERVAL_KEY] = intervalMs.coerceAtLeast(1000L)
        }
    }
}
