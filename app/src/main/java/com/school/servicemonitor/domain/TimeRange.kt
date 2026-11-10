package com.school.servicemonitor.domain

import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A time as the operator sees it: 1-12 hours, minutes, AM or PM. */
data class ClockTime(val hour12: Int, val minute: Int, val isPm: Boolean) {

    init {
        require(hour12 in 1..12) { "hour must be 1..12" }
        require(minute in 0..59) { "minute must be 0..59" }
    }

    fun toLocalTime(): LocalTime {
        // 12 AM is 00:00 and 12 PM is 12:00, so 12 counts as 0 before adding the PM offset.
        val hour24 = (hour12 % 12) + if (isPm) 12 else 0
        return LocalTime.of(hour24, minute)
    }

    companion object {
        fun from(time: LocalTime): ClockTime {
            val h = time.hour
            val hour12 = if (h % 12 == 0) 12 else h % 12
            return ClockTime(hour12, time.minute, isPm = h >= 12)
        }
    }
}

private val clockFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

/** 16:00 -> "4:00 PM" */
fun LocalTime.formatClock(): String = format(clockFormat)

sealed interface TimeRangeCheck {
    data class Valid(val durationMinutes: Int) : TimeRangeCheck
    data object EndNotAfterStart : TimeRangeCheck
}

sealed interface PassCheck {
    data object Allowed : PassCheck
    data object InvalidRange : PassCheck
    data class TooShort(val recordedMinutes: Int, val requiredMinutes: Int) : PassCheck
}

object TimeRangeCalculator {

    /** The end time must be later than the start time (same day only). */
    fun check(start: LocalTime, end: LocalTime): TimeRangeCheck =
        if (end.isAfter(start)) {
            TimeRangeCheck.Valid(Duration.between(start, end).toMinutes().toInt())
        } else {
            TimeRangeCheck.EndNotAfterStart
        }

    /**
     * The suggested end time: one hour after the start.
     * If that would pass midnight, it stops at 11:59 PM (overnight sessions are not supported).
     */
    fun defaultEnd(start: LocalTime): LocalTime {
        val end = start.plusMinutes(60)
        return if (end.isAfter(start)) end else LocalTime.of(23, 59)
    }

    /** PASS needs a valid range that lasts at least [requiredMinutes] (default 60). */
    fun passCheck(start: LocalTime, end: LocalTime, requiredMinutes: Int): PassCheck =
        when (val c = check(start, end)) {
            TimeRangeCheck.EndNotAfterStart -> PassCheck.InvalidRange
            is TimeRangeCheck.Valid ->
                if (c.durationMinutes >= requiredMinutes) PassCheck.Allowed
                else PassCheck.TooShort(c.durationMinutes, requiredMinutes)
        }
}

/**
 * The two time fields and the "has the end been edited by hand?" flag.
 * Immutable: every change returns a new copy, which keeps the rules easy to test.
 */
data class TimeRangeInput(
    val start: LocalTime,
    val end: LocalTime,
    val endManuallyEdited: Boolean = false
) {
    /** Changing the start moves the end along, until the operator edits the end themselves. */
    fun withStart(newStart: LocalTime): TimeRangeInput =
        if (endManuallyEdited) copy(start = newStart)
        else copy(start = newStart, end = TimeRangeCalculator.defaultEnd(newStart))

    fun withEnd(newEnd: LocalTime): TimeRangeInput =
        copy(end = newEnd, endManuallyEdited = true)

    companion object {
        /** Starts at 4:00 PM, ends at 5:00 PM (the example in the specification). */
        fun default(): TimeRangeInput {
            val start = LocalTime.of(16, 0)
            return TimeRangeInput(start, TimeRangeCalculator.defaultEnd(start))
        }
    }
}