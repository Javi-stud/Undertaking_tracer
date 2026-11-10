package com.school.servicemonitor.domain

import com.school.servicemonitor.data.entity.Sanction
import com.school.servicemonitor.data.entity.SanctionRow
import com.school.servicemonitor.data.entity.SanctionStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SanctionCalculatorTest {

    private fun row(
        assignedMinutes: Int,
        allocated: Int = 0,
        status: SanctionStatus = SanctionStatus.ACTIVE
    ) = SanctionRow(
        sanction = Sanction(
            studentId = 1,
            violationId = 1,
            baseHours = assignedMinutes / 60,
            multiplierApplied = 1,
            assignedMinutes = assignedMinutes,
            assignedDate = LocalDate.of(2026, 11, 1),
            startDate = LocalDate.of(2026, 11, 1),
            endDate = LocalDate.of(2026, 11, 20),
            status = status,
            createdAt = 0L
        ),
        violationName = "Test",
        allocatedMinutes = allocated
    )

    @Test
    fun singleSanction_outstandingEqualsAssigned() {
        val s = SanctionCalculator.summarize(listOf(row(300)))
        assertEquals(300, s.outstandingMinutes)
        assertEquals(0, s.completedMinutes)
        assertEquals(1, s.activeSanctionCount)
    }

    @Test
    fun twoSanctions_areCombinedIntoOneTotal() {
        // Alcohol 10 h + Uniform 5 h = 15 h
        val s = SanctionCalculator.summarize(listOf(row(600), row(300)))
        assertEquals(900, s.outstandingMinutes)
        assertEquals(2, s.activeSanctionCount)
    }

    @Test
    fun partialCredit_reducesRemaining() {
        val s = SanctionCalculator.summarize(listOf(row(300, allocated = 180)))
        assertEquals(120, s.outstandingMinutes)
        assertEquals(180, s.completedMinutes)
    }

    @Test
    fun oneHourCredit_comesOffTheCombinedTotal() {
        // After one successful day: 14 h left (840 minutes)
        val s = SanctionCalculator.summarize(listOf(row(600, allocated = 60), row(300)))
        assertEquals(840, s.outstandingMinutes)
    }

    @Test
    fun completedSanction_owesNothingAndIsNotCounted() {
        val s = SanctionCalculator.summarize(
            listOf(row(300, allocated = 300, status = SanctionStatus.COMPLETED), row(120))
        )
        assertEquals(120, s.outstandingMinutes)
        assertEquals(1, s.activeSanctionCount)
        assertEquals(300, s.completedMinutes)
    }

    @Test
    fun exemptedSanction_owesNothing() {
        val s = SanctionCalculator.summarize(listOf(row(300, status = SanctionStatus.EXEMPTED)))
        assertEquals(0, s.outstandingMinutes)
        assertEquals(0, s.activeSanctionCount)
    }

    @Test
    fun remaining_neverGoesNegative() {
        assertEquals(0, SanctionCalculator.remainingMinutes(row(300, allocated = 400)))
    }

    @Test
    fun repeatedSanctionExample_twoPlusFiveIsSeven() {
        // Original: 5 h assigned, 3 h done, 2 h left, overdue. Repeat: full 5 h.
        val original = row(300, allocated = 180, status = SanctionStatus.OVERDUE)
        val repeat = row(300)
        val s = SanctionCalculator.summarize(listOf(original, repeat))
        assertEquals(420, s.outstandingMinutes)   // 7 hours
    }
}