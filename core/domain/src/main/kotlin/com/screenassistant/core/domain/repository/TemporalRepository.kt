package com.screenassistant.core.domain.repository

import com.screenassistant.core.domain.model.TemporalContext
import java.time.Clock
import java.time.LocalDate

/**
 * Repositorio para obtener y gestionar el contexto temporal.
 *
 * La lógica de "¿es festivo?" se resuelve internamente usando la lista
 * de fechas festivas persistidas. El dominio no conoce la persistencia.
 */
interface TemporalRepository {
    /**
     * Obtiene el contexto temporal actual.
     *
     * @param clock Reloj inyectable para testing determinístico
     * @return TemporalContext con la información temporal del momento
     */
    fun getCurrentContext(clock: Clock): TemporalContext

    /**
     * Obtiene las fechas festivas configuradas por el usuario.
     *
     * @return Conjunto de fechas festivas
     */
    suspend fun getHolidayDates(): Set<LocalDate>

    /**
     * Guarda una fecha como festiva.
     *
     * @param date Fecha a marcar como festiva
     */
    suspend fun saveHolidayDate(date: LocalDate)

    /**
     * Elimina una fecha de la lista de festivos.
     *
     * @param date Fecha a eliminar
     */
    suspend fun removeHolidayDate(date: LocalDate)
}
