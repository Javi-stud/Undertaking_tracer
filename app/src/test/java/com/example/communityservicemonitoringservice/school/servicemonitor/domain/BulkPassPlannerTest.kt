package com.school.servicemonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class BulkPassPlannerTest {

    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private fun item(id: Long, start: LocalTime, end: LocalTime) = BulkPassItem(id, start, end)

    @Test
    fun everyoneValid_nothingSkipped() {
        val plan = BulkPassPlanner.plan(
            listOf(item(1, t(16), t(17)), item(2, t(16), t(17, 30))), 60
        )
        assertEquals(listOf(1L, 2L), plan.valid.map { it.studentId })
        assertTrue(plan.problems.isEmpty())
    }

    @Test
    fun fiftyNineMinutes_isSkipped_othersStillValid() {
        val plan = BulkPassPlanner.plan(
            listOf(item(1, t(16), t(17)), item(2, t(16), t(16, 59))), 60
        )
        assertEquals(listOf(1L), plan.valid.map { it.studentId })
        assertEquals(setOf(2L), plan.problems.keys)
        assertTrue(plan.problems.getValue(2L).contains("59"))
    }

    @Test
    fun exactlySixtyMinutes_isValid() {
        val plan = BulkPassPlanner.plan(listOf(item(1, t(16), t(17))), 60)
        assertEquals(1, plan.valid.size)
    }

    @Test
    fun endBeforeStart_isAProblem() {
        val plan = BulkPassPlanner.plan(listOf(item(1, t(17), t(16))), 60)
        assertTrue(plan.valid.isEmpty())
        assertEquals(setOf(1L), plan.problems.keys)
    }

    @Test
    fun individualTimes_areJudgedSeparately() {
        // Common time 4-5 PM is fine, but student 2 was adjusted to only 30 minutes.
        val plan = BulkPassPlanner.plan(
            listOf(item(1, t(16), t(17)), item(2, t(16), t(16, 30)), item(3, t(15), t(17))), 60
        )
        assertEquals(listOf(1L, 3L), plan.valid.map { it.studentId })
        assertEquals(setOf(2L), plan.problems.keys)
    }

    @Test
    fun allInvalid_givesNoValidItems() {
        val plan = BulkPassPlanner.plan(
            listOf(item(1, t(16), t(16, 10)), item(2, t(16), t(16, 20))), 60
        )
        assertTrue(plan.valid.isEmpty())
        assertEquals(2, plan.problems.size)
    }

    @Test
    fun emptySelection_givesEmptyPlan() {
        val plan = BulkPassPlanner.plan(emptyList(), 60)
        assertTrue(plan.valid.isEmpty())
        assertTrue(plan.problems.isEmpty())
    }

    @Test
    fun configuredMinimum_isUsed() {
        val plan = BulkPassPlanner.plan(listOf(item(1, t(16), t(17))), 90)
        assertTrue(plan.valid.isEmpty())
    }
}