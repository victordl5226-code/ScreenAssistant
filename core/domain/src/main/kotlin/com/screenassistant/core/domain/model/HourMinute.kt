package com.screenassistant.core.domain.model

/** Hora del día con invariantes: hour in 0..23, minute in 0..59. */
data class HourMinute(val hour: Int, val minute: Int)
