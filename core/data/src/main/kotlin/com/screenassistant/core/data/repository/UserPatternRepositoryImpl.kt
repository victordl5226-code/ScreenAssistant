package com.screenassistant.core.data.repository

import com.screenassistant.core.data.local.UserPatternDao
import com.screenassistant.core.data.local.UserPatternEntity
import com.screenassistant.core.domain.model.DayPeriod
import com.screenassistant.core.domain.model.proactive.LocationType
import com.screenassistant.core.domain.model.proactive.PatternContext
import com.screenassistant.core.domain.model.proactive.UserPattern
import com.screenassistant.core.domain.repository.ContextAggregatorRepository
import com.screenassistant.core.domain.repository.UserPatternRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [UserPatternRepository] que persiste los patrones de
 * comportamiento del usuario en Room vía [UserPatternDao].
 *
 * Los patrones representan acciones recurrentes observadas en contextos
 * específicos (día de la semana, rango horario, ubicación, app en primer plano).
 * Se usan para generar sugerencias proactivas personalizadas.
 *
 * La conversión Entity ↔ Domain se realiza en esta clase. La entity
 * [UserPatternEntity] ya proporciona [UserPatternEntity.toDomain], pero el
 * mapeo inverso se ejecuta aquí usando la factory [UserPatternEntity.fromDomain].
 *
 * @property dao DAO de acceso a la tabla `user_patterns`
 * @property clock Reloj inyectable para timestamps deterministas
 */
@Singleton
class UserPatternRepositoryImpl @Inject constructor(
    private val dao: UserPatternDao,
    private val contextAggregator: ContextAggregatorRepository,
    private val clock: Clock
) : UserPatternRepository {

    override fun getAllPatterns(): Flow<List<UserPattern>> {
        return dao.getAllPatterns().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getPattern(id: String): UserPattern? {
        return dao.getPattern(id)?.toDomain()
    }

    override suspend fun recordOccurrence(action: String, pattern: PatternContext) {
        // Buscar si ya existe un patrón con la misma acción y contexto
        val existing = findExistingPattern(action, pattern)
        if (existing != null) {
            // Incrementar frecuencia y actualizar confianza
            val updated = existing.copy(
                frequency = existing.frequency + 1,
                lastSeen = Instant.now(),
                confidence = calculateConfidence(existing.frequency + 1)
            )
            dao.insertPattern(UserPatternEntity.fromDomain(updated))
        } else {
            // Crear patrón nuevo
            val newPattern = UserPattern.initial(
                action = action,
                context = pattern,
                timestamp = Instant.now()
            )
            dao.insertPattern(UserPatternEntity.fromDomain(newPattern))
        }
    }

    override suspend fun getFrequentPatterns(minFrequency: Int): List<UserPattern> {
        return dao.getFrequentPatterns(minFrequency).map { it.toDomain() }
    }

    override suspend fun clearOldPatterns(olderThanDays: Int) {
        val cutoff = clock.millis() - (olderThanDays.toLong() * DAY_MILLIS)
        dao.deleteOldPatterns(cutoff)
    }

    override suspend fun deletePattern(id: String) {
        dao.deletePattern(id)
    }

    override suspend fun trackAction(actionId: String, screenApp: String?) {
        val context = contextAggregator.getAggregatedContext()
        val period = DayPeriod.fromHour(context.temporal.dateTime.hour)
        val patternContext = PatternContext(
            dayOfWeek = context.temporal.dayOfWeek,
            hourStart = period.hourRange.first,
            hourEnd = period.hourRange.last,
            location = context.location,
            screenApp = screenApp
        )
        recordOccurrence(actionId, patternContext)
    }

    /**
     * Busca un patrón existente que coincida con la acción y contexto dados.
     * Usa el query optimizado [UserPatternDao.findByActionAndContext] en
     * lugar de cargar todos los patrones de la tabla.
     */
    private suspend fun findExistingPattern(
        action: String,
        pattern: PatternContext
    ): UserPattern? {
        val entity = dao.findByActionAndContext(
            action = action,
            dayOfWeek = pattern.dayOfWeek.name,
            hourStart = pattern.hourStart,
            hourEnd = pattern.hourEnd,
            location = pattern.location.name
        )
        return entity?.toDomain()
    }

    /**
     * Calcula la confianza de un patrón basándose en su frecuencia.
     * Usa una función logarítmica para que la confianza crezca rápidamente
     * al principio y se estabilice después de muchas observaciones.
     *
     * @param frequency Número de observaciones del patrón
     * @return Confianza entre 0.1 y 1.0
     */
    private fun calculateConfidence(frequency: Int): Float {
        // f(x) = 0.1 + 0.9 * (1 - e^(-x/5))
        // En x=1 → ~0.25, x=3 → ~0.56, x=5 → ~0.74, x=10 → ~0.93
        val raw = 0.1f + 0.9f * (1f - Math.exp(-frequency / 5.0)).toFloat()
        return raw.coerceIn(0.1f, 1f)
    }

    companion object {
        private const val DAY_MILLIS = 86_400_000L
    }
}
