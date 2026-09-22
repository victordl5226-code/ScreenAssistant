package com.screenassistant.core.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.screenassistant.core.domain.model.proactive.LocationType
import com.screenassistant.core.domain.model.proactive.PatternContext
import com.screenassistant.core.domain.model.proactive.UserPattern
import java.time.DayOfWeek
import java.time.Instant

/**
 * Entity Room para persistir patrones de comportamiento del usuario detectados
 * por el sistema de aprendizaje.
 *
 * Cada patrón representa una acción recurrente observada en un contexto específico
 * (día de la semana, rango horario, ubicación, app en primer plano). Se usa para:
 * - Generar sugerencias proactivas personalizadas
 * - Identificar hábitos del usuario
 * - Calcular la confianza de las sugerencias
 *
 * Mapeo de campos:
 * - [dayOfWeek] se almacena como el nombre del enum [DayOfWeek]
 * - [hourStart], [hourEnd] se almacenan como [Int] (0-23)
 * - [locationType] se almacena como el nombre del enum [LocationType]
 * - [lastSeen] se almacena como `Long` (epoch millis) y se convierte a [Instant]
 *
 * @property id Identificador único del patrón (UUID)
 * @property action Identificador de la acción asociada al patrón
 * @property dayOfWeek Nombre del enum [DayOfWeek] (e.g., "MONDAY")
 * @property hourStart Hora de inicio del rango horario (0-23)
 * @property hourEnd Hora de fin del rango horario (0-23)
 * @property locationType Nombre del enum [LocationType] (e.g., "HOME")
 * @property screenApp Paquete de la app que estaba en primer plano (null si no aplica)
 * @property frequency Número de veces que se ha observado este patrón
 * @property lastSeen Timestamp del último avistamiento en milisegundos desde epoch
 * @property confidence Nivel de confianza en el patrón (0.0 a 1.0)
 */
@Entity(
    tableName = "user_patterns",
    indices = [Index("action")]
)
data class UserPatternEntity(
    @PrimaryKey val id: String,
    val action: String,
    val dayOfWeek: String,
    val hourStart: Int,
    val hourEnd: Int,
    val locationType: String,
    val screenApp: String? = null,
    val frequency: Int = 1,
    val lastSeen: Long = System.currentTimeMillis(),
    val confidence: Float = 0.5f
) {
    /**
     * Convierte esta entity a su equivalente en el dominio.
     *
     * Los campos enum se convierten usando [DayOfWeek.valueOf] y [LocationType.valueOf].
     * Si el nombre no coincide con un valor válido del enum, se usa un valor por defecto
     * ([DayOfWeek.MONDAY] y [LocationType.UNKNOWN] respectivamente).
     *
     * @return UserPattern con los campos convertidos a tipos de dominio
     */
    fun toDomain() = UserPattern(
        id = id,
        action = action,
        context = PatternContext(
            dayOfWeek = runCatching { DayOfWeek.valueOf(dayOfWeek) }.getOrDefault(DayOfWeek.MONDAY),
            hourStart = hourStart,
            hourEnd = hourEnd,
            location = runCatching { LocationType.valueOf(locationType) }.getOrDefault(LocationType.UNKNOWN),
            screenApp = screenApp
        ),
        frequency = frequency,
        lastSeen = Instant.ofEpochMilli(lastSeen),
        confidence = confidence
    )

    companion object {
        /**
         * Crea una [UserPatternEntity] a partir de un [UserPattern] de dominio.
         *
         * @param pattern Patrón de usuario en formato de dominio
         * @return Entity lista para persistir en Room
         */
        fun fromDomain(pattern: UserPattern) = UserPatternEntity(
            id = pattern.id,
            action = pattern.action,
            dayOfWeek = pattern.context.dayOfWeek.name,
            hourStart = pattern.context.hourStart,
            hourEnd = pattern.context.hourEnd,
            locationType = pattern.context.location.name,
            screenApp = pattern.context.screenApp,
            frequency = pattern.frequency,
            lastSeen = pattern.lastSeen.toEpochMilli(),
            confidence = pattern.confidence
        )
    }
}
