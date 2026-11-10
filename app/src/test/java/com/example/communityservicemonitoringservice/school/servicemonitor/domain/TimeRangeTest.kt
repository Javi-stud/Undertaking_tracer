package com.school.servicemonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class TimeRangeTest {

    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    // ---- ClockTime conversions ----

    @Test
    fun twelveAm_isMidnight() =
        assertEquals(t(0, 0), ClockTime(12, 0, isPm = false).toLocalTime())

    @Test
    fun twelvePm_isNoon() =
        assertEquals(t(12, 0), ClockTime(12, 0, isPm = true).toLocalTime())

    @Test
    fun fourPm_is1600() =
        assertEquals(t(16, 0), ClockTime(4, 0, isPm = true).toLocalTime())

    @Test
    fun elevenFiftyNineAm() =
        assertEquals(t(11, 59), ClockTime(11, 59, isPm = false).toLocalTime())

    @Test
    fun roundTrip_everyMinuteOfTheDay() {
        for (h in 0..23) for (m in 0..59) {
            val time = t(h, m)
            assertEquals(time, ClockTime.from(time).toLocalTime())
        }
    }

    @Test
    fun from_midnightAndNoon() {
        assertEquals(ClockTime(12, 0, false), ClockTime.from(t(0, 0)))
        assertEquals(ClockTime(12, 30, true), ClockTime.from(t(12, 30)))
    }

    @Test
    fun format_isReadable() {
        assertEquals("4:00 PM", t(16, 0).formatClock())
        assertEquals("12:05 AM", t(0, 5).formatClock())
    }

    // ---- Duration and validation ----

    @Test
    fun duration_oneHour() =
        assertEquals(TimeRangeCheck.Valid(60), TimeRangeCalculator.check(t(16), t(17)))

    @Test
    fun duration_isCalculatedFromActualTimes() =
        assertEquals(TimeRangeCheck.Valid(95), TimeRangeCalculator.check(t(16, 10), t(17, 45)))

    @Test
    fun endEqualToStart_isInvalid() =
        assertEquals(TimeRangeCheck.EndNotAfterStart, TimeRangeCalculator.check(t(16), t(16)))

    @Test
    fun endBeforeStart_isInvalid() =
        assertEquals(TimeRangeCheck.EndNotAfterStart, TimeRangeCalculator.check(t(17), t(16)))

    @Test
    fun overnight_isNotSupported() =
        assertEquals(TimeRangeCheck.EndNotAfterStart, TimeRangeCalculator.check(t(23, 30), t(0, 30)))

    // ---- Default end time ----

    @Test
    fun defaultEnd_isOneHourLater() =
        assertEquals(t(17, 0), TimeRangeCalculator.defaultEnd(t(16, 0)))

    @Test
    fun defaultEnd_keepsMinutes() =
        assertEquals(t(17, 25), TimeRangeCalculator.defaultEnd(t(16, 25)))

    @Test
    fun defaultEnd_stopsAtElevenFiftyNine_insteadOfWrappingPastMidnight() {
        assertEquals(t(23, 59), TimeRangeCalculator.defaultEnd(t(23, 0)))
        assertEquals(t(23, 59), TimeRangeCalculator.defaultEnd(t(23, 45)))
    }

    @Test
    fun defaultEnd_lastStartThatStillGetsAFullHour() =
        assertEquals(t(23, 59), TimeRangeCalculator.defaultEnd(t(22, 59)))

    // ---- PASS check ----

    @Test
    fun pass_59Minutes_isBlocked() {
        val r = TimeRangeCalculator.passCheck(t(16), t(16, 59), 60)
        assertEquals(PassCheck.TooShort(59, 60), r)
    }

    @Test
    fun pass_exactly60_isAllowed() =
        assertEquals(PassCheck.Allowed, TimeRangeCalculator.passCheck(t(16), t(17), 60))

    @Test
    fun pass_90and120_areAllowed() {
        assertEquals(PassCheck.Allowed, TimeRangeCalculator.passCheck(t(16), t(17, 30), 60))
        assertEquals(PassCheck.Allowed, TimeRangeCalculator.passCheck(t(15), t(17), 60))
    }

    @Test
    fun pass_45Minutes_isBlocked() =
        assertEquals(PassCheck.TooShort(45, 60), TimeRangeCalculator.passCheck(t(16), t(16, 45), 60))

    @Test
    fun pass_invalidRange_isBlocked() =
        assertEquals(PassCheck.InvalidRange, TimeRangeCalculator.passCheck(t(17), t(16), 60))

    @Test
    fun pass_usesTheConfiguredMinimum() {
        assertEquals(PassCheck.TooShort(60, 90), TimeRangeCalculator.passCheck(t(16), t(17), 90))
        assertEquals(PassCheck.Allowed, TimeRangeCalculator.passCheck(t(16), t(17, 30), 90))
    }

    // ---- Input behaviour: the end follows the start until edited ----

    @Test
    fun default_is4pmTo5pm() {
        val d = TimeRangeInput.default()
        assertEquals(t(16), d.start)
        assertEquals(t(17), d.end)
        assertFalse(d.endManuallyEdited)
    }

    @Test
    fun changingStart_movesTheEnd_whenNotEdited() {
        val r = TimeRangeInput.default().withStart(t(15, 30))
        assertEquals(t(15, 30), r.start)
        assertEquals(t(16, 30), r.end)
    }

    @Test
    fun editingEnd_setsTheFlag() {
        val r = TimeRangeInput.default().withEnd(t(17, 30))
        assertEquals(t(17, 30), r.end)
        assertTrue(r.endManuallyEdited)
    }

    @Test
    fun changingStart_keepsTheEnd_afterManualEdit() {
        val r = TimeRangeInput.default().withEnd(t(17, 30)).withStart(t(15, 0))
        assertEquals(t(15, 0), r.start)
        assertEquals(t(17, 30), r.end)
    }
}