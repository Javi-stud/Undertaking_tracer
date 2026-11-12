package com.school.servicemonitor.domain

import com.school.servicemonitor.data.entity.AttemptResultType
import com.school.servicemonitor.data.entity.AttemptState
import com.school.servicemonitor.data.entity.DailyRecordStatus
import com.school.servicemonitor.data.entity.Sanction
import com.school.servicemonitor.data.entity.SanctionRow
import com.school.servicemonitor.data.entity.SanctionStatus
import com.school.servicemonitor.data.entity.ServiceAttempt
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class VoidScenarioTest {

    private val today = LocalDate.of(2026, 11, 12)

    private fun attempt(
        no: Int,
        result: AttemptResultType,
        credited: Int = 0,
        state: AttemptState = AttemptState.RECORDED
    ) = ServiceAttempt(
        id = no.toLong(), dailyRecordId = 1, studentId = 1, attemptNo = no,
        startTime = LocalTime.of(16, 0), endTime = LocalTime.of(17, 0), durationMinutes = 60,
        result = result, creditedMinutes = credited, recordState = state, createdAt = 0L
    )

    private fun row(assigned: Int, allocated: Int, status: SanctionStatus, end: LocalDate) = SanctionRow(
        sanction = Sanction(
            id = 1, studentId = 1, violationId = 1, baseHours = assigned / 60, multiplierApplied = 1,
            assignedMinutes = assigned, assignedDate = today.minusDays(10), startDate = today.minusDays(10),
            endDate = end, status = status, createdAt = 0L
        ),
        violationName = "Test",
        allocatedMinutes = allocated
    )

    @Test
    fun voidedPass_returnsTheDayToPending() {
        val r = DailyRecordResolver.resolve(listOf(attempt(1, AttemptResultType.PASS, 60, AttemptState.VOIDED)))
        assertEquals(DailyRecordStatus.PENDING, r.status)
        assertEquals(0, r.creditedMinutes)
    }

    @Test
    fun voidedPass_afterAFail_returnsToRepeat() {
        val r = DailyRecordResolver.resolve(
            listOf(
                attempt(1, AttemptResultType.FAIL),
                attempt(2, AttemptResultType.PASS, 60, AttemptState.VOIDED)
            )
        )
        assertEquals(DailyRecordStatus.FAILED_REPEAT, r.status)
    }

    @Test
    fun voidedAttempt_keepsItsOriginalData() {
        val original = attempt(1, AttemptResultType.PASS, 60)
        val voided = original.copy(recordState = AttemptState.VOIDED, voidReason = "Wrong student", voidedAt = 5L)
        assertEquals(original.startTime, voided.startTime)
        assertEquals(original.endTime, voided.endTime)
        assertEquals(original.result, voided.result)
        assertEquals(original.creditedMinutes, voided.creditedMinutes)
    }

    @Test
    fun reversingTheCredit_restoresTheOutstandingHours() {
        val end = today.plusDays(5)
        assertEquals(240, SanctionCalculator.summarize(listOf(row(300, 60, SanctionStatus.ACTIVE, end))).outstandingMinutes)
        assertEquals(300, SanctionCalculator.summarize(listOf(row(300, 0, SanctionStatus.ACTIVE, end))).outstandingMinutes)
    }

    @Test
    fun completedSanction_reopensAsActive_whenTheDeadlineIsStillAhead() {
        val s = AllocationEngine.resolveStatus(SanctionStatus.COMPLETED, 300, 240, today.plusDays(5), today)
        assertEquals(SanctionStatus.ACTIVE, s)
    }

    @Test
    fun completedSanction_reopensAsOverdue_whenTheDeadlinePassed() {
        val s = AllocationEngine.resolveStatus(SanctionStatus.COMPLETED, 300, 240, today.minusDays(2), today)
        assertEquals(SanctionStatus.OVERDUE, s)
    }

    @Test
    fun voidThenNewPass_creditsOnlyOneHour() {
        val r = DailyRecordResolver.resolve(
            listOf(
                attempt(1, AttemptResultType.PASS, 60, AttemptState.VOIDED),
                attempt(2, AttemptResultType.PASS, 60)
            )
        )
        assertEquals(DailyRecordStatus.PASSED, r.status)
        assertEquals(60, r.creditedMinutes)
    }

    @Test
    fun keptRepeat_meansRestoredHoursPlusRepeat() {
        // Original 5 h: 3 h credited, then one of those credits is voided -> 3 h owed again.
        // The confirmed repeat (5 h) is kept, so the student owes 3 + 5 = 8 hours.
        val original = row(300, 120, SanctionStatus.OVERDUE, today.minusDays(3))
        val repeat = row(300, 0, SanctionStatus.ACTIVE, today.plusDays(8))
        assertEquals(180, SanctionCalculator.remainingMinutes(original))
        assertEquals(480, SanctionCalculator.summarize(listOf(original, repeat)).outstandingMinutes)
    }
}