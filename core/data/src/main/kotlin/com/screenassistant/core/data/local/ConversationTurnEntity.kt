package com.screenassistant.core.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.screenassistant.core.domain.model.personality.ConversationTurn
import com.screenassistant.core.domain.model.personality.ConversationTurn.Sentiment
import com.screenassistant.core.domain.model.proactive.ScreenInfo
import java.time.Instant

/**
 * Entity Room para persistir turnos de conversación entre el usuario y el asistente.
 *
 * Almacena cada intercambio completo (pregunta + respuesta) junto con su contexto
 * de pantalla, tema detectado y sentimiento. Se usa para:
 * - Recuperar conversaciones recientes para el motor de personalidad
 * - Buscar por contenido o tema
 * - Calcular estadísticas de uso y tendencias de sentimiento
 *
 * Mapeo de campos:
 * - [timestamp] se almacena como `Long` (epoch millis) y se convierte a [Instant] en [toDomain]
 * - [screenContextJson] serializa [ScreenInfo] como JSON; la serialización
 *   se realiza en el repositorio porque [ScreenInfo] no está anotado con @Serializable en domain
 * - [sentiment] se almacena como el nombre del enum [Sentiment]
 *
 * @property id Identificador único del turno (UUID)
 * @property userMessage Mensaje enviado por el usuario
 * @property assistantResponse Respuesta generada por el asistente
 * @property timestamp Timestamp del turno en milisegundos desde epoch
 * @property screenContextJson JSON serializado de [ScreenInfo] o null si no hay contexto
 * @property topic Tema principal de la conversación (puede ser null)
 * @property sentiment Nombre del enum [Sentiment] o null si no se detectó sentimiento
 */
@Entity(
    tableName = "conversation_turns",
    indices = [Index("timestamp")]
)
data class ConversationTurnEntity(
    @PrimaryKey val id: String,
    val userMessage: String,
    val assistantResponse: String,
    val timestamp: Long,
    val screenContextJson: String? = null,
    val topic: String? = null,
    val sentiment: String? = null
) {
    /**
     * Convierte esta entity a su equivalente en el dominio.
     *
     * El [screenContextJson] se deserializa a [ScreenInfo] usando un helper de parsing
     * simple que no depende de kotlinx.serialization.
     *
     * @return ConversationTurn con los campos convertidos a tipos de dominio
     */
    fun toDomain() = ConversationTurn(
        id = id,
        userMessage = userMessage,
        assistantResponse = assistantResponse,
        timestamp = Instant.ofEpochMilli(timestamp),
        screenContext = parseScreenInfo(screenContextJson),
        topic = topic,
        sentiment = sentiment?.let { runCatching { Sentiment.valueOf(it) }.getOrNull() }
    )

    companion object {
        private const val PACKAGE_NAME_KEY = "packageName"
        private const val TEXT_KEY = "text"

        /**
         * Crea una [ConversationTurnEntity] a partir de un [ConversationTurn] de dominio.
         *
         * @param turn Turno de conversación en formato de dominio
         * @return Entity lista para persistir en Room
         */
        fun fromDomain(turn: ConversationTurn) = ConversationTurnEntity(
            id = turn.id,
            userMessage = turn.userMessage,
            assistantResponse = turn.assistantResponse,
            timestamp = turn.timestamp.toEpochMilli(),
            screenContextJson = serializeScreenInfo(turn.screenContext),
            topic = turn.topic,
            sentiment = turn.sentiment?.name
        )

        /**
         * Parsea un JSON de [ScreenInfo] de forma simple sin kotlinx.serialization.
         * Asume formato: `{"packageName":"...","text":"..."}`.
         * Si el JSON es null o inválido, retorna null.
         */
        private fun parseScreenInfo(json: String?): ScreenInfo? {
            if (json.isNullOrBlank()) return null
            return try {
                val pkgRegex = Regex("\"$PACKAGE_NAME_KEY\"\\s*:\\s*\"([^\"]*)\"")
                val txtRegex = Regex("\"$TEXT_KEY\"\\s*:\\s*\"([^\"]*)\"")
                val packageName = pkgRegex.find(json)?.groupValues?.get(1)
                val text = txtRegex.find(json)?.groupValues?.get(1) ?: ""
                ScreenInfo(packageName = packageName, text = text)
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Serializa un [ScreenInfo] a JSON simple sin kotlinx.serialization.
         * Si [info] es null, retorna null.
         */
        private fun serializeScreenInfo(info: ScreenInfo?): String? {
            if (info == null) return null
            val pkg = info.packageName?.replace("\\", "\\\\")
                ?.replace("\"", "\\\"")
            val txt = info.text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
            val pkgJson = if (pkg != null) "\"$pkg\"" else "null"
            return "{\"$PACKAGE_NAME_KEY\":$pkgJson,\"$TEXT_KEY\":\"$txt\"}"
        }
    }
}
