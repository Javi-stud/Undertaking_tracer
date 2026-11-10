package com.school.servicemonitor.domain

import java.time.DayOfWeek
import java.time.LocalDate

object ScheduleCalculator {

    val DEFAULT_SERVICE_DAYS: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    )

    /** Turns the setting "MON,TUE,WED,THU,FRI" into days. Bad or empty input falls back to Mon-Fri. */
    fun parseServiceDays(value: String?): Set<DayOfWeek> {
        val parsed = value.orEmpty()
            .split(",")
            .map { it.trim().uppercase() }
            .mapNotNull { token -> DayOfWeek.values().firstOrNull { it.name.take(3) == token } }
            .toSet()
        return parsed.ifEmpty { DEFAULT_SERVICE_DAYS }
    }

    fun isServiceDay(date: LocalDate, serviceDays: Set<DayOfWeek>): Boolean =
        date.dayOfWeek in serviceDays
}