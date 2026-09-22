package com.screenassistant.core.data.repository

import com.screenassistant.core.data.local.ConversationTurnDao
import com.screenassistant.core.data.local.ConversationTurnEntity
import com.screenassistant.core.domain.model.personality.ConversationTurn
import com.screenassistant.core.domain.repository.ConversationMemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [ConversationMemoryRepository] que persiste los turnos
 * de conversación en Room vía [ConversationTurnDao].
 *
 * Cada turno almacena el intercambio completo (mensaje del usuario + respuesta
 * del asistente) junto con su contexto de pantalla, tema y sentimiento detectado.
 *
 * La conversión Entity ↔ Domain se realiza en esta clase usando los mappers
 * privados [toDomain] y [fromDomain]. La entity [ConversationTurnEntity] ya
 * proporciona [ConversationTurnEntity.toDomain], pero el mapeo inverso se
 * ejecuta aquí para mantener la lógica de serialización centralizada.
 *
 * @property dao DAO de acceso a la tabla `conversation_turns`
 */
@Singleton
class ConversationMemoryRepositoryImpl @Inject constructor(
    private val dao: ConversationTurnDao
) : ConversationMemoryRepository {

    override fun getRecentTurns(limit: Int): Flow<List<ConversationTurn>> {
        return dao.getRecentTurns(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveTurn(turn: ConversationTurn) {
        dao.insertTurn(fromDomain(turn))
    }

    override suspend fun searchByContent(query: String): List<ConversationTurn> {
        return dao.searchByContent(query).map { it.toDomain() }
    }

    override suspend fun searchByTopic(topic: String): List<ConversationTurn> {
        return dao.searchByTopic(topic).map { it.toDomain() }
    }

    override suspend fun getTurnsBetween(startMillis: Long, endMillis: Long): List<ConversationTurn> {
        return dao.getTurnsBetween(startMillis, endMillis).map { it.toDomain() }
    }

    override suspend fun clearOldTurns(olderThanDays: Int) {
        val cutoff = System.currentTimeMillis() - (olderThanDays.toLong() * DAY_MILLIS)
        dao.deleteOldTurns(cutoff)
    }

    /**
     * Convierte un [ConversationTurnEntity] a [ConversationTurn] de dominio.
     */
    private fun ConversationTurnEntity.toDomain(): ConversationTurn = toDomain()

    /**
     * Convierte un [ConversationTurn] de dominio a [ConversationTurnEntity] para persistencia.
     */
    private fun fromDomain(turn: ConversationTurn): ConversationTurnEntity {
        return ConversationTurnEntity(
            id = turn.id.ifBlank { UUID.randomUUID().toString() },
            userMessage = turn.userMessage,
            assistantResponse = turn.assistantResponse,
            timestamp = turn.timestamp.toEpochMilli(),
            screenContextJson = serializeScreenInfo(turn.screenContext),
            topic = turn.topic,
            sentiment = turn.sentiment?.name
        )
    }

    /**
     * Serializa [ScreenInfo] a JSON simple sin kotlinx.serialization.
     * Centralizado aquí para mantener la lógica de serialización en la capa data.
     */
    private fun serializeScreenInfo(info: com.screenassistant.core.domain.model.proactive.ScreenInfo?): String? {
        if (info == null) return null
        val pkg = info.packageName?.replace("\\", "\\\\")
            ?.replace("\"", "\\\"")
        val txt = info.text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
        val pkgJson = if (pkg != null) "\"$pkg\"" else "null"
        return """{"packageName":$pkgJson,"text":"$txt"}"""
    }

    companion object {
        private const val DAY_MILLIS = 86_400_000L
    }
}
