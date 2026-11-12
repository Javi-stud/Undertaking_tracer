package com.school.servicemonitor.domain

import com.school.servicemonitor.data.entity.SanctionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AllocationEngineTest {

    private val day = LocalDate.of(2026, 11, 10)

    private fun b(
        id: Long,
        remaining: Int,
        end: Int,
        assignedDay: Int = 1,
        start: Int = 1,
        status: SanctionStatus = SanctionStatus.ACTIVE
    ) = SanctionBalance(
        sanctionId = id,
        status = status,
        startDate = LocalDate.of(2026, 11, start),
        endDate = LocalDate.of(2026, 11, end),
        assignedDate = LocalDate.of(2026, 11, assignedDay),
        remainingMinutes = remaining
    )

    @Test
    fun earliestDeadline_getsTheHour() {
        // Listed in the "wrong" order on purpose, to prove sorting works.
        val r = AllocationEngine.allocate(listOf(b(2, 300, end = 20), b(1, 600, end = 12)), day, 60)
        assertEquals(listOf(Allocation(1, 60)), r)
    }

    @Test
    fun nearlyFinishedSanction_splitsAcrossTwo() {
        val r = AllocationEngine.allocate(listOf(b(1, 30, end = 12), b(2, 300, end = 20)), day, 60)
        assertEquals(listOf(Allocation(1, 30), Allocation(2, 30)), r)
    }

    @Test
    fun sameDeadline_oldestAssignedFirst() {
        val r = AllocationEngine.allocate(
            listOf(b(1, 300, end = 15, assignedDay = 5), b(2, 300, end = 15, assignedDay = 2)),
            day, 60
        )
        assertEquals(listOf(Allocation(2, 60)), r)
    }

    @Test
    fun sameDeadlineAndAssignedDate_lowestIdFirst() {
        val r = AllocationEngine.allocate(
            listOf(b(7, 300, end = 15, assignedDay = 2), b(3, 300, end = 15, assignedDay = 2)),
            day, 60
        )
        assertEquals(listOf(Allocation(3, 60)), r)
    }

    @Test
    fun overdueSanction_isPaidFirstBecauseItsDeadlineIsOldest() {
        val r = AllocationEngine.allocate(
            listOf(b(1, 120, end = 5, status = SanctionStatus.OVERDUE), b(2, 300, end = 12)),
            day, 60
        )
        assertEquals(listOf(Allocation(1, 60)), r)
    }

    @Test
    fun sanctionNotStartedYet_isSkipped() {
        val r = AllocationEngine.allocate(
            listOf(b(1, 300, end = 12, start = 15), b(2, 300, end = 20)),
            day, 60
        )
        assertEquals(listOf(Allocation(2, 60)), r)
    }

    @Test
    fun completedAndExemptedSanctions_areSkipped() {
        val r = AllocationEngine.allocate(
            listOf(
                b(1, 300, end = 12, status = SanctionStatus.COMPLETED),
                b(2, 300, end = 13, status = SanctionStatus.EXEMPTED),
                b(3, 300, end = 20)
            ),
            day, 60
        )
        assertEquals(listOf(Allocation(3, 60)), r)
    }

    @Test
    fun lessRemainingThanCredit_allocatesOnlyWhatIsOwed() {
        val r = AllocationEngine.allocate(listOf(b(1, 20, end = 12)), day, 60)
        assertEquals(listOf(Allocation(1, 20)), r)
    }

    @Test
    fun zeroCredit_orNoSanctions_givesNothing() {
        assertTrue(AllocationEngine.allocate(listOf(b(1, 300, end = 12)), day, 0).isEmpty())
        assertTrue(AllocationEngine.allocate(emptyList(), day, 60).isEmpty())
    }

    @Test
    fun elevenSuccessfulDays_alcoholFinishesFirstThenUniform() {
        var alcohol = 600   // 10 h, due Nov 12
        var uniform = 300   // 5 h, due Nov 20
        repeat(11) {
            val r = AllocationEngine.allocate(listOf(b(1, alcohol, end = 12), b(2, uniform, end = 20)), day, 60)
            r.forEach { if (it.sanctionId == 1L) alcohol -= it.minutes else uniform -= it.minutes }
        }
        assertEquals(0, alcohol)
        assertEquals(240, uniform)   // the 11th day went to Uniform
    }

    @Test
    fun dailyCap_neverExceedsTheLimit() {
        assertEquals(60, AllocationEngine.allowedToday(60, 60, 0))
        assertEquals(30, AllocationEngine.allowedToday(60, 60, 30))
        assertEquals(0, AllocationEngine.allowedToday(60, 60, 60))
        assertEquals(0, AllocationEngine.allowedToday(60, 60, 90))
    }

    @Test
    fun status_completedWhenNothingRemains() {
        val s = AllocationEngine.resolveStatus(SanctionStatus.ACTIVE, 300, 300, day, day)
        assertEquals(SanctionStatus.COMPLETED, s)
    }

    @Test
    fun status_reopensAfterAReversal() {
        val future = LocalDate.of(2026, 11, 20)
        val past = LocalDate.of(2026, 11, 5)
        assertEquals(
            SanctionStatus.ACTIVE,
            AllocationEngine.resolveStatus(SanctionStatus.COMPLETED, 300, 240, future, day)
        )
        assertEquals(
            SanctionStatus.OVERDUE,
            AllocationEngine.resolveStatus(SanctionStatus.COMPLETED, 300, 240, past, day)
        )
    }

    @Test
    fun status_exemptedStaysExempted() {
        val s = AllocationEngine.resolveStatus(
            SanctionStatus.EXEMPTED, 300, 0, day, day, exemptedMinutes = 300
        )
        assertEquals(SanctionStatus.EXEMPTED, s)
    }
}