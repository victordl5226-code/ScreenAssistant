package com.screenassistant.core.domain.model

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Contexto temporal del dispositivo en el momento de la consulta.
 * Snapshot inmutable calculado bajo demanda.
 *
 * @param dateTime Fecha y hora actual
 * @param dayOfWeek Día de la semana
 * @param dayPeriod Período del día (madrugada, mañana, etc.)
 * @param esFinDeSemana true si es sábado o domingo
 * @param esFestivo true si la fecha está en la lista de festivos del usuario
 */
data class TemporalContext(
    val dateTime: LocalDateTime,
    val dayOfWeek: DayOfWeek,
    val dayPeriod: DayPeriod,
    val esFinDeSemana: Boolean,
    val esFestivo: Boolean
) {
    companion object {
        /**
         * Calcula el contexto temporal para una fecha/hora dada.
         *
         * @param now Fecha y hora a evaluar
         * @param holidayDates Conjunto de fechas festivas configuradas por el usuario
         * @return TemporalContext con toda la información temporal
         */
        fun now(
            now: LocalDateTime = LocalDateTime.now(),
            holidayDates: Set<java.time.LocalDate> = emptySet()
        ): TemporalContext {
            return TemporalContext(
                dateTime = now,
                dayOfWeek = now.dayOfWeek,
                dayPeriod = DayPeriod.fromHour(now.hour),
                esFinDeSemana = now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY,
                esFestivo = now.toLocalDate() in holidayDates
            )
        }
    }
}

/**
 * Período del día basado en la hora.
 * Enumeración cerrada con valores predefinidos para testing determinístico.
 */
enum class DayPeriod(val hourRange: IntRange) {
    MADRUGADA(0..5),    // 00:00 - 05:59
    MANANA(6..11),      // 06:00 - 11:59
    MEDIODIA(12..13),   // 12:00 - 13:59
    TARDE(14..19),      // 14:00 - 19:59
    NOCHE(20..23);      // 20:00 - 23:59

    companion object {
        /**
         * Determina el período del día a partir de la hora.
         * Función pura, testeable sin dependencias Android.
         *
         * @param hour Hora (0-23)
         * @return DayPeriod correspondiente
         */
        fun fromHour(hour: Int): DayPeriod {
            return when (hour) {
                in 0..5 -> MADRUGADA
                in 6..11 -> MANANA
                in 12..13 -> MEDIODIA
                in 14..19 -> TARDE
                in 20..23 -> NOCHE
                else -> throw IllegalArgumentException("Hora fuera de rango: $hour (debe ser 0-23)")
            }
        }
    }
}
