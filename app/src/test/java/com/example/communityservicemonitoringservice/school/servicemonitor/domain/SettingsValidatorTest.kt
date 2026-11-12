package com.school.servicemonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class SettingsValidatorTest {

    private val weekdays = ScheduleCalculator.DEFAULT_SERVICE_DAYS

    private fun check(credit: String, multiplier: String, days: Set<DayOfWeek> = weekdays) =
        SettingsValidator.validate(SettingsInput(credit, multiplier, days))

    @Test
    fun defaults_areValid_andEqualTheSpecification() {
        val r = check("60", "2") as SettingsCheck.Valid
        assertEquals(SettingsValidator.defaults(), r.values)
        assertEquals(60, r.values.dailyCreditMinutes)
        assertEquals(2, r.values.ssgMultiplier)
    }

    @Test
    fun creditBelowMinimumAboveMaximumBlankOrText_isRejected() {
        listOf("14", "241", "", "abc", "0").forEach {
            val r = check(it, "2") as SettingsCheck.Invalid
            assertNotNull(r.errors.credit)
            assertNull(r.errors.multiplier)
        }
    }

    @Test
    fun creditBoundaries_areAccepted() {
        assertTrue(check("15", "2") is SettingsCheck.Valid)
        assertTrue(check("240", "2") is SettingsCheck.Valid)
    }

    @Test
    fun multiplierOutsideOneToFive_isRejected() {
        listOf("0", "6", "", "x").forEach {
            assertNotNull((check("60", it) as SettingsCheck.Invalid).errors.multiplier)
        }
        assertTrue(check("60", "1") is SettingsCheck.Valid)
        assertTrue(check("60", "5") is SettingsCheck.Valid)
    }

    @Test
    fun noServiceDays_isRejected() =
        assertNotNull((check("60", "2", emptySet()) as SettingsCheck.Invalid).errors.days)

    @Test
    fun weekendDays_areIgnored_soWeekendOnlyIsRejected() {
        val r = check("60", "2", setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) as SettingsCheck.Invalid
        assertNotNull(r.errors.days)
    }

    @Test
    fun weekendDaysMixedWithWeekdays_areDropped() {
        val r = check("60", "2", setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)) as SettingsCheck.Valid
        assertEquals(setOf(DayOfWeek.MONDAY), r.values.serviceDays)
    }

    @Test
    fun whitespaceAroundNumbers_isTolerated() =
        assertTrue(check(" 90 ", " 3 ") is SettingsCheck.Valid)

    @Test
    fun encodeDays_isInWeekOrder() =
        assertEquals("MON,FRI", SettingsValidator.encodeDays(setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY)))

    @Test
    fun encodedDays_canBeReadBackByTheSchedule() {
        val days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)
        assertEquals(days, ScheduleCalculator.parseServiceDays(SettingsValidator.encodeDays(days)))
    }

    @Test
    fun fromStored_missingOrGarbage_fallsBackToDefaults() {
        assertEquals(SettingsValidator.defaults(), SettingsValidator.fromStored(null, null, null))
        assertEquals(SettingsValidator.defaults(), SettingsValidator.fromStored("abc", "99", "xyz"))
        assertEquals(60, SettingsValidator.fromStored("5", "2", "MON").dailyCreditMinutes)
    }

    @Test
    fun fromStored_validValues_areKept() {
        val v = SettingsValidator.fromStored("90", "3", "MON,WED")
        assertEquals(90, v.dailyCreditMinutes)
        assertEquals(3, v.ssgMultiplier)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), v.serviceDays)
    }
}