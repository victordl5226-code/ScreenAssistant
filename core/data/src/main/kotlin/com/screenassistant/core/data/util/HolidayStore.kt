package com.screenassistant.core.data.util

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Almacén de festivos vía DataStore Preferences.
 *
 * El delegate [holidayDataStore] DEBE estar en el nivel superior del archivo
 * (recomendación oficial de DataStore). No puede estar dentro de un object/class.
 *
 * Patrón idéntico a [MonitoringPreferences] pero como clase con @Inject
 * (condición QA: no usar object singleton).
 */
private val Context.holidayDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "holiday_dates"
)

@Singleton
class HolidayStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val holidayKey = stringPreferencesKey("holiday_dates")

    /**
     * Flow reactivo de las fechas festivas almacenadas.
     */
    fun getHolidayDatesFlow(): Flow<Set<LocalDate>> {
        return context.holidayDataStore.data.map { prefs ->
            parseDates(prefs[holidayKey])
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Obtiene las fechas festivas de forma suspendida (corre en IO internamente).
     */
    suspend fun getHolidayDates(): Set<LocalDate> {
        return context.holidayDataStore.data.first().let { prefs ->
            parseDates(prefs[holidayKey])
        }
    }

    /**
     * Obtiene las fechas festivas de forma síncrona (usado por TemporalRepositoryImpl).
     * Aceptable porque DataStore lee de disco de forma asíncrona pero el resultado
     * es cacheado en memoria.
     */
    fun getHolidayDatesSync(): Set<LocalDate> {
        return runBlocking {
            context.holidayDataStore.data.first().let { prefs ->
                parseDates(prefs[holidayKey])
            }
        }
    }

    /**
     * Añade una fecha festiva.
     */
    suspend fun saveHolidayDate(date: LocalDate) {
        context.holidayDataStore.edit { prefs ->
            val current = parseDates(prefs[holidayKey]).toMutableSet()
            current.add(date)
            prefs[holidayKey] = current.joinToString(",") { it.toString() }
        }
    }

    /**
     * Elimina una fecha festiva.
     */
    suspend fun removeHolidayDate(date: LocalDate) {
        context.holidayDataStore.edit { prefs ->
            val current = parseDates(prefs[holidayKey]).toMutableSet()
            current.remove(date)
            prefs[holidayKey] = current.joinToString(",") { it.toString() }
        }
    }

    @VisibleForTesting
    internal fun parseDates(raw: String?): Set<LocalDate> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()
    }
}
