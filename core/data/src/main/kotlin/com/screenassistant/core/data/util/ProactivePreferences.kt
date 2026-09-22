package com.screenassistant.core.data.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore Preferences para configuración del sistema proactivo.
 *
 * Almacena las preferencias del motor proactivo: estado habilitado,
 * intervalo de comprobación y horario de silencio (quiet hours).
 *
 * El delegate [proactiveDataStore] DEBE estar en el nivel superior del archivo
 * (recomendación oficial de DataStore). No puede estar dentro de un object/class.
 *
 * Patrón idéntico a [HolidayStore]: clase inyectable con `@Inject constructor`,
 * NO object singleton (requisito QA).
 *
 * @property context Contexto de aplicación inyectado por Hilt
 */
private val Context.proactiveDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "proactive_prefs"
)

@Singleton
class ProactivePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val enabledKey = booleanPreferencesKey("proactive_enabled")
    private val checkIntervalKey = longPreferencesKey("check_interval_minutes")
    private val quietHoursStartKey = intPreferencesKey("quiet_hours_start")
    private val quietHoursEndKey = intPreferencesKey("quiet_hours_end")

    /** Flow reactivo del estado habilitado del sistema proactivo. Default: `true`. */
    val isEnabled: Flow<Boolean> = context.proactiveDataStore.data.map { prefs ->
        prefs[enabledKey] ?: true
    }

    /** Flow reactivo del intervalo de comprobación en minutos. Default: 15. */
    val checkIntervalMinutes: Flow<Long> = context.proactiveDataStore.data.map { prefs ->
        prefs[checkIntervalKey] ?: 15L
    }

    /** Flow reactivo de la hora de inicio de quiet hours (0-23). Default: 22. */
    val quietHoursStart: Flow<Int> = context.proactiveDataStore.data.map { prefs ->
        prefs[quietHoursStartKey] ?: 22
    }

    /** Flow reactivo de la hora de fin de quiet hours (0-23). Default: 7. */
    val quietHoursEnd: Flow<Int> = context.proactiveDataStore.data.map { prefs ->
        prefs[quietHoursEndKey] ?: 7
    }

    /**
     * Habilita o deshabilita el sistema proactivo.
     *
     * @param enabled `true` para activar, `false` para desactivar
     */
    suspend fun setEnabled(enabled: Boolean) {
        context.proactiveDataStore.edit { prefs ->
            prefs[enabledKey] = enabled
        }
    }

    /**
     * Establece el intervalo de comprobación de reglas proactivas.
     *
     * @param minutes Intervalo en minutos (mínimo 1, por defecto 15)
     */
    suspend fun setCheckInterval(minutes: Long) {
        context.proactiveDataStore.edit { prefs ->
            prefs[checkIntervalKey] = minutes.coerceAtLeast(1L)
        }
    }

    /**
     * Obtiene el horario de silencio (quiet hours) como par de horas.
     *
     * @return Pair<horaInicio, horaFin> en formato 0-23.
     *         Default: 22 (10 PM) — 7 (7 AM)
     */
    suspend fun getQuietHours(): Pair<Int, Int> {
        val prefs = context.proactiveDataStore.data.first()
        return Pair(
            prefs[quietHoursStartKey] ?: 22,
            prefs[quietHoursEndKey] ?: 7
        )
    }

    /**
     * Establece el horario de silencio (quiet hours).
     *
     * @param startHour Hora de inicio (0-23)
     * @param endHour Hora de fin (0-23)
     */
    suspend fun setQuietHours(startHour: Int, endHour: Int) {
        require(startHour in 0..23) { "startHour debe estar entre 0 y 23, recibido: $startHour" }
        require(endHour in 0..23) { "endHour debe estar entre 0 y 23, recibido: $endHour" }
        context.proactiveDataStore.edit { prefs ->
            prefs[quietHoursStartKey] = startHour
            prefs[quietHoursEndKey] = endHour
        }
    }
}
