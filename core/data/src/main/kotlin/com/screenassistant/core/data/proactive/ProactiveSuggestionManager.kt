package com.screenassistant.core.data.proactive

import android.util.Log
import com.screenassistant.core.data.util.ProactivePreferences
import com.screenassistant.core.domain.model.proactive.ProactiveSuggestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestor centralizado de sugerencias proactivas visibles para el usuario.
 *
 * Actúa como puente entre el [ProactiveCheckWorker] (productor) y la UI del
 * overlay (consumidor). Recibe las sugerencias evaluadas por el worker,
 * aplica filtros de negocio (cooldown, expiración) y expone un [StateFlow]
 * reactivo que la capa de presentación consume.
 *
 * ## Responsabilidades
 * - Cooldown in-memory: evita repetir la misma regla antes de que pase
 *   el intervalo mínimo entre activaciones ([DEFAULT_COOLDOWN_MINUTES]).
 * - Expiración: descarta sugerencias con antigüedad superior a
 *   [ProactiveSuggestion.MAX_LIFETIME_MINUTES].
 * - Quiet hours: suprime sugerencias si la hora actual está dentro del
 *   horario de silencio configurado por el usuario.
 *
 * ## Lo que NO hace
 * - No conoce la UI (ni Compose, ni ViewModels).
 * - No conoce WorkManager (el worker lo llama, no al revés).
 * - No persiste cooldown entre reinicios de la app (aceptable para
 *   sugerencias transitorias de bajo impacto).
 *
 * @constructor Crea el manager con las dependencias inyectadas por Hilt.
 * @param proactivePreferences Preferencias de configuración del sistema proactivo
 * @param clock Reloj inyectable para testing determinístico
 */
@Singleton
class ProactiveSuggestionManager @Inject constructor(
    private val proactivePreferences: ProactivePreferences,
    private val clock: Clock
) {

    private val _activeSuggestions = MutableStateFlow<List<ProactiveSuggestion>>(emptyList())

    /** Flujo reactivo de sugerencias activas para mostrar al usuario. */
    val activeSuggestions: StateFlow<List<ProactiveSuggestion>> = _activeSuggestions.asStateFlow()

    /**
     * Mapa de cooldown por regla. Clave: ID de la regla, valor: timestamp
     * de la última vez que se mostró una sugerencia de esa regla (epoch millis).
     * Se reinicia al reiniciar la app (in-memory).
     */
    private val cooldownMap = mutableMapOf<String, Long>()

    /**
     * Procesa una lista de sugerencias generadas por el [ProactiveCheckWorker].
     *
     * Flujo:
     * 1. Verifica que el sistema proactivo esté habilitado.
     * 2. Filtra sugerencias en horario de silencio (quiet hours).
     * 3. Filtra sugerencias en cooldown (misma regla, intervalo no cumplido).
     * 4. Filtra sugerencias expiradas.
     * 5. Actualiza el cooldown de las sugerencias aceptadas.
     * 6. Emite la lista filtrada en [activeSuggestions].
     *
     * @param suggestions Lista de sugerencias generadas por el motor de reglas
     */
    suspend fun submitSuggestions(suggestions: List<ProactiveSuggestion>) {
        // Si el sistema proactivo está deshabilitado, ignorar
        val isEnabled = proactivePreferences.isEnabled.first()
        if (!isEnabled) {
            Log.d(TAG, "Sistema proactivo deshabilitado, ignorando sugerencias")
            return
        }

        // Obtener quiet hours para verificar
        val (quietStart, quietEnd) = proactivePreferences.getQuietHours()
        val currentHour = LocalTime.now(clock).hour

        val filtered = suggestions.filter { suggestion ->
            // Filtro 1: quiet hours
            if (isInQuietHours(currentHour, quietStart, quietEnd)) {
                Log.d(TAG, "Sugerencia '${suggestion.title}' suprimida por quiet hours")
                return@filter false
            }

            // Filtro 2: cooldown — se actualiza DURANTE el filtrado para que
            // sugerencias de la misma regla en el mismo lote sean filtradas.
            if (isInCooldown(suggestion)) {
                Log.d(TAG, "Sugerencia '${suggestion.title}' suprimida por cooldown")
                return@filter false
            }

            // Filtro 3: expiración
            if (isExpired(suggestion)) {
                Log.d(TAG, "Sugerencia '${suggestion.title}' descartada por expiración")
                return@filter false
            }

            // Registrar cooldown inmediatamente para que la siguiente sugerencia
            // de la misma regla (en el mismo lote) sea filtrada.
            updateCooldown(suggestion)
            true
        }

        // Emitir sugerencias activas
        if (filtered.isNotEmpty()) {
            _activeSuggestions.value = filtered
            filtered.forEach { suggestion ->
                Log.d(TAG, "Sugerencia activa: ${suggestion.title} - ${suggestion.message}")
            }
        }
    }

    /**
     * Descarta una sugerencia específica por su ID.
     *
     * @param id Identificador de la sugerencia a descartar
     */
    fun dismissSuggestion(id: String) {
        _activeSuggestions.value = _activeSuggestions.value.filter { it.id != id }
    }

    /**
     * Descarta todas las sugerencias activas.
     */
    fun dismissAll() {
        _activeSuggestions.value = emptyList()
    }

    /**
     * Verifica si una sugerencia está en período de cooldown.
     *
     * El cooldown se calcula por regla (extraído de [ProactiveSuggestion.source]).
     * Si la regla se mostró hace menos de [DEFAULT_COOLDOWN_MINUTES] minutos,
     * la sugerencia se suprime.
     *
     * @param suggestion Sugerencia a verificar
     * @return true si la sugerencia está en cooldown (debe suprimirse)
     */
    private fun isInCooldown(suggestion: ProactiveSuggestion): Boolean {
        val ruleId = extractRuleId(suggestion.source) ?: return false
        val lastShown = cooldownMap[ruleId] ?: return false
        val cooldownMillis = DEFAULT_COOLDOWN_MINUTES * 60 * 1000L
        return (clock.millis() - lastShown) < cooldownMillis
    }

    /**
     * Verifica si una sugerencia ha expirado.
     *
     * @param suggestion Sugerencia a verificar
     * @return true si la sugerencia ha superado el tiempo máximo de vida
     */
    private fun isExpired(suggestion: ProactiveSuggestion): Boolean {
        val ageMillis = clock.millis() - suggestion.generatedAt.toEpochMilli()
        return ageMillis > ProactiveSuggestion.MAX_LIFETIME_MINUTES * 60 * 1000L
    }

    /**
     * Actualiza el timestamp de la última visualización de una regla.
     *
     * @param suggestion Sugerencia cuya regla se debe actualizar
     */
    private fun updateCooldown(suggestion: ProactiveSuggestion) {
        val ruleId = extractRuleId(suggestion.source) ?: return
        cooldownMap[ruleId] = clock.millis()
    }

    /**
     * Extrae el ID de la regla del campo [ProactiveSuggestion.source].
     *
     * El formato esperado es `"rule:<id>"`.
     *
     * @param source Campo source de la sugerencia
     * @return ID de la regla, o null si el formato no coincide
     */
    private fun extractRuleId(source: String): String? {
        if (source.startsWith(RULE_SOURCE_PREFIX)) {
            return source.removePrefix(RULE_SOURCE_PREFIX)
        }
        return null
    }

    /**
     * Determina si la hora actual está dentro del horario de silencio.
     *
     * Soporta rangos que cruzan medianoche (ej: 22-7 = 10 PM a 7 AM).
     *
     * @param currentHour Hora actual (0-23)
     * @param startHour Hora de inicio de quiet hours (0-23)
     * @param endHour Hora de fin de quiet hours (0-23)
     * @return true si la hora actual está en quiet hours
     */
    private fun isInQuietHours(currentHour: Int, startHour: Int, endHour: Int): Boolean {
        return if (startHour <= endHour) {
            // Rango normal: ej. 9-17 (9 AM a 5 PM)
            currentHour in startHour until endHour
        } else {
            // Rango cruza medianoche: ej. 22-7 (10 PM a 7 AM)
            currentHour >= startHour || currentHour < endHour
        }
    }

    companion object {
        /** Tag para logs del manager. */
        private const val TAG = "ProactiveSuggestionMgr"

        /** Prefijo del campo source para extraer el ID de la regla. */
        private const val RULE_SOURCE_PREFIX = "rule:"

        /**
         * Intervalo mínimo en minutos entre activaciones consecutivas de la
         * misma regla. Equivale al default de [ProactiveRule.cooldownMinutes].
         *
         * Se usa como fallback cuando la sugerencia no transporta el cooldown
         * de la regla original. Un valor de 30 minutos asegura que, con el
         * worker ejecutándose cada 15 minutos, una regla no se repite más de
         * una vez cada dos ciclos.
         */
        const val DEFAULT_COOLDOWN_MINUTES = 30
    }
}
