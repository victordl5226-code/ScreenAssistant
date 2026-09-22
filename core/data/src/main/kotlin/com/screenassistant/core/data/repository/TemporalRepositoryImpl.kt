package com.screenassistant.core.data.repository

import com.screenassistant.core.data.util.HolidayStore
import com.screenassistant.core.domain.model.TemporalContext
import com.screenassistant.core.domain.repository.TemporalRepository
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemporalRepositoryImpl @Inject constructor(
    private val holidayStore: HolidayStore
) : TemporalRepository {

    override fun getCurrentContext(clock: Clock): TemporalContext {
        val now = LocalDateTime.now(clock)
        val holidays = holidayStore.getHolidayDatesSync()
        return TemporalContext.now(now, holidays)
    }

    override suspend fun getHolidayDates(): Set<LocalDate> {
        return holidayStore.getHolidayDates()
    }

    override suspend fun saveHolidayDate(date: LocalDate) {
        holidayStore.saveHolidayDate(date)
    }

    override suspend fun removeHolidayDate(date: LocalDate) {
        holidayStore.removeHolidayDate(date)
    }
}
