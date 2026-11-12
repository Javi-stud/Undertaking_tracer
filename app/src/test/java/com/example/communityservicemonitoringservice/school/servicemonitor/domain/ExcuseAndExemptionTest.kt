package com.school.servicemonitor.domain

import com.school.servicemonitor.data.entity.AttemptResultType
import com.school.servicemonitor.data.entity.DailyRecordStatus
import com.school.servicemonitor.data.entity.Sanction
import com.school.servicemonitor.data.entity.SanctionRow
import com.school.servicemonitor.data.entity.SanctionStatus
import com.school.servicemonitor.data.entity.ServiceAttempt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ExcuseAndExemptionTest {

    private val day = LocalDate.of(2026, 11, 10)
    private val future = LocalDate.of(2026, 11, 20)

    private fun row(
        assigned: Int,
        allocated: Int = 0,
        exempted: Int = 0,
        status: SanctionStatus = SanctionStatus.ACTIVE
    ) = SanctionRow(
        sanction = Sanction(
            id = 1, studentId = 1, violationId = 1,
            baseHours = assigned / 60, multiplierApplied = 1, assignedMinutes = assigned,
            assignedDate = LocalDate.of(2026, 11, 1), startDate = LocalDate.of(2026, 11, 1),
            endDate = future, status = status, createdAt = 0L
        ),
        violationName = "Test",
        allocatedMinutes = allocated,
        exemptedMinutes = exempted
    )

    private fun attempt(result: AttemptResultType, credited: Int = 0) = ServiceAttempt(
        id = 1, dailyRecordId = 1, studentId = 1, attemptNo = 1,
        startTime = LocalTime.of(16, 0), endTime = LocalTime.of(17, 0), durationMinutes = 60,
        result = result, creditedMinutes = credited, createdAt = 0L
    )

    // ---- Daily excuse ----

    @Test
    fun excused_withNoAttempts_isExcused_withZeroCredit() {
        val r = DailyRecordResolver.resolve(emptyList(), excused = true)
        assertEquals(DailyRecordStatus.EXCUSED, r.status)
        assertEquals(0, r.creditedMinutes)
    }

    @Test
    fun excuse_beatsAFailedAttempt() {
        val r = DailyRecordResolver.resolve(listOf(attempt(AttemptResultType.FAIL)), excused = true)
        assertEquals(DailyRecordStatus.EXCUSED, r.status)
    }

    @Test
    fun pass_beatsAnExcuse() {
        val r = DailyRecordResolver.resolve(listOf(attempt(AttemptResultType.PASS, 60)), excused = true)
        assertEquals(DailyRecordStatus.PASSED, r.status)
        assertEquals(60, r.creditedMinutes)
    }

    @Test
    fun revokedExcuse_returnsToFailedOrPending() {
        assertEquals(
            DailyRecordStatus.FAILED_REPEAT,
            DailyRecordResolver.resolve(listOf(attempt(AttemptResultType.FAIL)), excused = false).status
        )
        assertEquals(
            DailyRecordStatus.PENDING,
            DailyRecordResolver.resolve(emptyList(), excused = false).status
        )
    }

    @Test
    fun dailyExcuse_changesNoHours() {
        // An excuse never touches sanction rows, so the totals are identical.
        val rows = listOf(row(600), row(300))
        assertEquals(900, SanctionCalculator.summarize(rows).outstandingMinutes)
        assertEquals(0, SanctionCalculator.summarize(rows).exemptedMinutes)
    }

    // ---- Permanent exemption ----

    @Test
    fun partialExemption_reducesOutstanding() {
        val s = SanctionCalculator.summarize(listOf(row(300, exempted = 120)))
        assertEquals(180, s.outstandingMinutes)
        assertEquals(120, s.exemptedMinutes)
        assertEquals(1, s.activeSanctionCount)
    }

    @Test
    fun fullExemption_owesNothing() {
        val s = SanctionCalculator.summarize(listOf(row(300, exempted = 300)))
        assertEquals(0, s.outstandingMinutes)
        assertEquals(0, s.activeSanctionCount)
    }

    @Test
    fun exemptionAndCredit_areBothSubtracted() {
        assertEquals(420, SanctionCalculator.remainingMinutes(row(600, allocated = 60, exempted = 120)))
    }

    @Test
    fun exemptionStacksWithOtherSanctions() {
        val s = SanctionCalculator.summarize(listOf(row(600, exempted = 600), row(300)))
        assertEquals(300, s.outstandingMinutes)
    }

    @Test
    fun toBalance_subtractsExemption() {
        assertEquals(180, row(300, exempted = 120).toBalance().remainingMinutes)
    }

    @Test
    fun fullyExemptedSanction_receivesNoCredit() {
        val result = AllocationEngine.allocate(listOf(row(300, exempted = 300).toBalance()), day, 60)
        assertTrue(result.isEmpty())
    }

    // ---- Status after exemption ----

    @Test
    fun status_fullExemption_becomesExempted() {
        val s = AllocationEngine.resolveStatus(SanctionStatus.ACTIVE, 300, 0, future, day, 300)
        assertEquals(SanctionStatus.EXEMPTED, s)
    }

    @Test
    fun status_partialExemption_staysActive() {
        val s = AllocationEngine.resolveStatus(SanctionStatus.ACTIVE, 300, 0, future, day, 120)
        assertEquals(SanctionStatus.ACTIVE, s)
    }

    @Test
    fun status_exemptingTheRestAfterPartialCredit_becomesExempted() {
        val s = AllocationEngine.resolveStatus(SanctionStatus.ACTIVE, 300, 180, future, day, 120)
        assertEquals(SanctionStatus.EXEMPTED, s)
    }

    @Test
    fun status_reopens_ifACreditIsReversedAfterExemption() {
        // Exempted 180 when 120 had been credited; the 120 is later voided, so 120 is owed again.
        val s = AllocationEngine.resolveStatus(SanctionStatus.EXEMPTED, 300, 0, future, day, 180)
        assertEquals(SanctionStatus.ACTIVE, s)
    }
}