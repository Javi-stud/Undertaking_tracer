package com.school.servicemonitor.domain

import com.school.servicemonitor.data.entity.Sanction
import com.school.servicemonitor.data.entity.SanctionRow
import com.school.servicemonitor.data.entity.SanctionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DeadlineMonitorTest {

    private val today = LocalDate.of(2026, 11, 12)
    private val deadline = LocalDate.of(2026, 11, 9)   // 3 days ago

    private fun row(
        assigned: Int = 300,
        allocated: Int = 0,
        exempted: Int = 0,
        end: LocalDate = deadline,
        status: SanctionStatus = SanctionStatus.ACTIVE,
        id: Long = 1
    ) = SanctionRow(
        sanction = Sanction(
            id = id, studentId = 1, violationId = 1,
            baseHours = assigned / 60, multiplierApplied = 1, assignedMinutes = assigned,
            assignedDate = LocalDate.of(2026, 11, 2), startDate = LocalDate.of(2026, 11, 2),
            endDate = end, status = status, createdAt = 0L
        ),
        violationName = "Test",
        allocatedMinutes = allocated,
        exemptedMinutes = exempted
    )

    @Test
    fun deadlineYesterday_withHoursLeft_isOverdue() =
        assertTrue(DeadlineMonitor.isOverdue(row(end = today.minusDays(1)), today))

    @Test
    fun deadlineToday_isNotOverdueYet() =
        assertFalse(DeadlineMonitor.isOverdue(row(end = today), today))

    @Test
    fun deadlineInTheFuture_isNotOverdue() =
        assertFalse(DeadlineMonitor.isOverdue(row(end = today.plusDays(5)), today))

    @Test
    fun alreadyOverdueStatus_stillCounts() =
        assertTrue(DeadlineMonitor.isOverdue(row(status = SanctionStatus.OVERDUE), today))

    @Test
    fun completedSanction_isNotOverdue() =
        assertFalse(
            DeadlineMonitor.isOverdue(
                row(allocated = 300, status = SanctionStatus.COMPLETED), today
            )
        )

    @Test
    fun exemptedSanction_isNotOverdue() =
        assertFalse(
            DeadlineMonitor.isOverdue(row(exempted = 300, status = SanctionStatus.EXEMPTED), today)
        )

    @Test
    fun nothingLeftToDo_isNotOverdue_evenIfStatusIsStale() =
        assertFalse(DeadlineMonitor.isOverdue(row(allocated = 300), today))

    @Test
    fun overdue_filtersAList() {
        val rows = listOf(
            row(id = 1),
            row(id = 2, end = today.plusDays(3)),
            row(id = 3, allocated = 300, status = SanctionStatus.COMPLETED)
        )
        assertEquals(listOf(1L), DeadlineMonitor.overdue(rows, today).map { it.sanction.id })
    }

    @Test
    fun daysOverdue_countsFullDays() {
        assertEquals(3, DeadlineMonitor.daysOverdue(deadline, today))
        assertEquals(0, DeadlineMonitor.daysOverdue(today, today))
        assertEquals(0, DeadlineMonitor.daysOverdue(today.plusDays(2), today))
    }

    @Test
    fun proposedRepeat_usesTheFullAssignedHours_notTheRemaining() {
        // 5 h assigned, 3 h done, 2 h left: the repeat is still 5 h.
        val original = row(assigned = 300, allocated = 180)
        assertEquals(120, SanctionCalculator.remainingMinutes(original))
        assertEquals(300, DeadlineMonitor.proposedMinutes(original.sanction))
    }

    @Test
    fun ssgOriginal_repeatKeepsItsOwnSnapshot() {
        // SSG snapshot: 5 h x2 = 10 h assigned. The repeat is the same 10 h.
        val original = row(assigned = 600)
        assertEquals(600, DeadlineMonitor.proposedMinutes(original.sanction))
    }

    @Test
    fun beforeConfirmation_nothingIsAdded_afterConfirmation_twoPlusFiveIsSeven() {
        val original = row(assigned = 300, allocated = 180, status = SanctionStatus.OVERDUE)
        // Proposed only: the combined total is still just the 2 remaining hours.
        assertEquals(120, SanctionCalculator.summarize(listOf(original)).outstandingMinutes)

        val repeat = row(id = 2, assigned = DeadlineMonitor.proposedMinutes(original.sanction),
            end = today.plusDays(8))
        assertEquals(420, SanctionCalculator.summarize(listOf(original, repeat)).outstandingMinutes)
    }

    @Test
    fun repeatDates_validWhenEndIsTodayOrLater() {
        assertNull(DeadlineMonitor.validateRepeatDates(today, today, today))
        assertNull(DeadlineMonitor.validateRepeatDates(today, today.plusDays(8), today))
    }

    @Test
    fun repeatDates_endBeforeStart_isRejected() =
        assertNotNull(DeadlineMonitor.validateRepeatDates(today.plusDays(5), today.plusDays(2), today))

    @Test
    fun repeatDates_endInThePast_isRejected() =
        assertNotNull(DeadlineMonitor.validateRepeatDates(today.minusDays(10), today.minusDays(2), today))
}