package com.school.servicemonitor.domain

import com.school.servicemonitor.data.entity.AttemptHistoryRow
import com.school.servicemonitor.data.entity.AttemptResultType
import com.school.servicemonitor.data.entity.AttemptState
import com.school.servicemonitor.data.entity.ServiceAttempt
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class HistoryBuilderTest {

    private val nov10 = LocalDate.of(2026, 11, 10)
    private val nov11 = LocalDate.of(2026, 11, 11)

    private fun row(
        studentId: Long,
        no: Int,
        result: AttemptResultType,
        credited: Int = 0,
        state: AttemptState = AttemptState.RECORDED,
        date: LocalDate = nov10,
        last: String = "Last$studentId"
    ) = AttemptHistoryRow(
        attempt = ServiceAttempt(
            id = studentId * 100 + no,
            dailyRecordId = studentId,
            studentId = studentId,
            attemptNo = no,
            startTime = LocalTime.of(16, 0),
            endTime = LocalTime.of(17, 0),
            durationMinutes = 60,
            result = result,
            creditedMinutes = credited,
            failReason = if (result == AttemptResultType.FAIL) "Not clean" else null,
            recordState = state,
            createdAt = 0L
        ),
        serviceDate = date,
        studentNumber = "S$studentId",
        firstName = "First",
        middleName = null,
        lastName = last
    )

    @Test
    fun failFailPass_isOneDay_withThreeAttempts_andOneHourCredited() {
        val days = HistoryBuilder.group(
            listOf(
                row(1, 1, AttemptResultType.FAIL),
                row(1, 2, AttemptResultType.FAIL),
                row(1, 3, AttemptResultType.PASS, credited = 60)
            )
        )
        assertEquals(1, days.size)
        assertEquals(3, days[0].attempts.size)
        assertEquals(DayOutcome.PASSED, days[0].outcome)
        assertEquals(60, days[0].creditedMinutes)
    }

    @Test
    fun failOnly_isFailed_withNoCredit() {
        val d = HistoryBuilder.group(listOf(row(1, 1, AttemptResultType.FAIL)))[0]
        assertEquals(DayOutcome.FAILED, d.outcome)
        assertEquals(0, d.creditedMinutes)
    }

    @Test
    fun voidedPass_afterAFail_returnsToFailed_andStaysVisible() {
        val d = HistoryBuilder.group(
            listOf(
                row(1, 1, AttemptResultType.FAIL),
                row(1, 2, AttemptResultType.PASS, 60, AttemptState.VOIDED)
            )
        )[0]
        assertEquals(DayOutcome.FAILED, d.outcome)
        assertEquals(0, d.creditedMinutes)
        assertEquals(2, d.attempts.size)   // the voided attempt is still listed
    }

    @Test
    fun voidedPassThenNewPass_creditsOnlyOneHour() {
        val d = HistoryBuilder.group(
            listOf(
                row(1, 1, AttemptResultType.PASS, 60, AttemptState.VOIDED),
                row(1, 2, AttemptResultType.PASS, 60)
            )
        )[0]
        assertEquals(DayOutcome.PASSED, d.outcome)
        assertEquals(60, d.creditedMinutes)
    }

    @Test
    fun everyAttemptVoided_isAllVoided() {
        val d = HistoryBuilder.group(
            listOf(row(1, 1, AttemptResultType.PASS, 60, AttemptState.VOIDED))
        )[0]
        assertEquals(DayOutcome.ALL_VOIDED, d.outcome)
    }

    @Test
    fun attemptsAreSortedByNumber_evenIfInputIsNot() {
        val d = HistoryBuilder.group(
            listOf(
                row(1, 3, AttemptResultType.PASS, 60),
                row(1, 1, AttemptResultType.FAIL),
                row(1, 2, AttemptResultType.FAIL)
            )
        )[0]
        assertEquals(listOf(1, 2, 3), d.attempts.map { it.attemptNo })
    }

    @Test
    fun twoStudentsSameDate_areSeparateGroups_inInputOrder() {
        val days = HistoryBuilder.group(
            listOf(
                row(2, 1, AttemptResultType.PASS, 60),
                row(1, 1, AttemptResultType.FAIL)
            )
        )
        assertEquals(listOf(2L, 1L), days.map { it.studentId })
    }

    @Test
    fun sameStudentTwoDates_areTwoGroups() {
        val days = HistoryBuilder.group(
            listOf(
                row(1, 1, AttemptResultType.PASS, 60, date = nov11),
                row(1, 1, AttemptResultType.FAIL, date = nov10)
            )
        )
        assertEquals(listOf(nov11, nov10), days.map { it.date })
    }

    @Test
    fun displayName_usesMiddleInitial() {
        assertEquals("Cruz, Juan A.", HistoryBuilder.displayName("Cruz", "Juan", "andres"))
        assertEquals("Cruz, Juan", HistoryBuilder.displayName("Cruz", "Juan", null))
        assertEquals("Cruz, Juan", HistoryBuilder.displayName("Cruz", "Juan", "  "))
    }

    @Test
    fun summary_countsPassedDaysFailedAndVoided() {
        val days = HistoryBuilder.group(
            listOf(
                row(1, 1, AttemptResultType.FAIL, date = nov10),
                row(1, 2, AttemptResultType.PASS, 60, date = nov10),
                row(1, 1, AttemptResultType.PASS, 60, AttemptState.VOIDED, date = nov11)
            )
        )
        val s = HistoryBuilder.summarize(days)
        assertEquals(1, s.daysPassed)
        assertEquals(3, s.totalAttempts)
        assertEquals(1, s.failedAttempts)
        assertEquals(1, s.voidedAttempts)
        assertEquals(60, s.creditedMinutes)
    }

    @Test
    fun summary_ofNothing_isAllZero() {
        val s = HistoryBuilder.summarize(emptyList())
        assertEquals(AttendanceSummary(0, 0, 0, 0, 0), s)
    }
}