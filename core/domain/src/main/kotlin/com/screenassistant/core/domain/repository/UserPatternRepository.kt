package com.screenassistant.core.domain.repository

import com.screenassistant.core.domain.model.proactive.PatternContext
import com.screenassistant.core.domain.model.proactive.UserPattern
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio de acceso a los patrones de comportamiento del usuario.
 *
 * Almacena y consulta [UserPattern]s que representan acciones recurrentes
 * observadas en contextos específicos. Estos patrones alimentan el motor
 * de sugerencias proactivas y el aprendizaje de rutinas del usuario.
 */
interface UserPatternRepository {

    /**
     * Flujo reactivo de todos los patrones almacenados.
     * Emite una nueva lista cuando se registra o elimina un patrón.
     *
     * @return [Flow] que emite la lista completa de patrones
     */
    fun getAllPatterns(): Flow<List<UserPattern>>

    /**
     * Busca un patrón por su identificador único.
     *
     * @param id Identificador del patrón a buscar
     * @return El [UserPattern] si existe, o `null` si no se encontró
     */
    suspend fun getPattern(id: String): UserPattern?

    /**
     * Registra una nueva ocurrencia de una acción en un contexto dado.
     *
     * Si ya existe un patrón con la misma acción y contexto, incrementa su
     * frecuencia y actualiza la marca de tiempo. Si no existe, crea uno nuevo
     * con frecuencia inicial de 1.
     *
     * @param action Identificador de la acción observada
     * @param pattern Contexto en el que se observó la acción
     */
    suspend fun recordOccurrence(action: String, pattern: PatternContext)

    /**
     * Obtiene los patrones que han sido observados al menos [minFrequency] veces.
     * Útil para identificar comportamientos establecidos del usuario.
     *
     * @param minFrequency Frecuencia mínima para incluir el patrón (por defecto 3)
     * @return Lista de patrones que cumplen con la frecuencia mínima
     */
    suspend fun getFrequentPatterns(minFrequency: Int = 3): List<UserPattern>

    /**
     * Elimina los patrones cuya última observación sea anterior a [olderThanDays] días.
     * Operación de limpieza para evitar acumulación de patrones obsoletos.
     *
     * @param olderThanDays Antigüedad máxima en días (por defecto 30)
     */
    suspend fun clearOldPatterns(olderThanDays: Int = 30)

    /**
     * Elimina un patrón por su identificador.
     * Si no existe ningún patrón con ese identificador, la operación es un noop.
     *
     * @param id Identificador del patrón a eliminar
     */
    suspend fun deletePattern(id: String)

    /**
     * Registra una acción del sistema como parte de un patrón de comportamiento.
     *
     * Obtiene el contexto agregado actual (día, hora, ubicación) y registra la
     * ocurrencia de la acción en ese contexto. Se usa en fire-and-forget desde
     * [SystemActionHandler] para alimentar el motor predictivo sin bloquear
     * la ejecución principal de la acción.
     *
     * @param actionId Identificador de la acción ejecutada (ej. "SetWifi", "OpenApp")
     * @param screenApp Paquete de la app que estaba en primer plano (opcional)
     */
    suspend fun trackAction(actionId: String, screenApp: String? = null)
}
