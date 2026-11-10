package com.school.servicemonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ScheduleCalculatorTest {

    private val days = ScheduleCalculator.DEFAULT_SERVICE_DAYS

    @Test
    fun weekdays_areServiceDays() {
        // Nov 10, 2026 is a Tuesday
        (10..13).forEach { assertTrue(ScheduleCalculator.isServiceDay(LocalDate.of(2026, 11, it), days)) }
        assertTrue(ScheduleCalculator.isServiceDay(LocalDate.of(2026, 11, 9), days))   // Monday
    }

    @Test
    fun weekends_areNotServiceDays() {
        assertFalse(ScheduleCalculator.isServiceDay(LocalDate.of(2026, 11, 14), days)) // Saturday
        assertFalse(ScheduleCalculator.isServiceDay(LocalDate.of(2026, 11, 15), days)) // Sunday
    }

    @Test
    fun parse_defaultSetting() {
        assertEquals(days, ScheduleCalculator.parseServiceDays("MON,TUE,WED,THU,FRI"))
    }

    @Test
    fun parse_customDays() {
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            ScheduleCalculator.parseServiceDays(" mon , WED ")
        )
    }

    @Test
    fun parse_invalidOrEmpty_fallsBackToWeekdays() {
        assertEquals(days, ScheduleCalculator.parseServiceDays(""))
        assertEquals(days, ScheduleCalculator.parseServiceDays(null))
        assertEquals(days, ScheduleCalculator.parseServiceDays("xyz,123"))
    }
}