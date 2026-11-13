package com.school.servicemonitor.domain

import com.school.servicemonitor.data.entity.Sanction
import com.school.servicemonitor.data.entity.SanctionRow
import com.school.servicemonitor.data.entity.SanctionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExtensionPlannerTest {

    private val days = ScheduleCalculator.DEFAULT_SERVICE_DAYS
    private val thu = LocalDate.of(2026, 11, 12)
    private val fri = LocalDate.of(2026, 11, 13)
    private val sat = LocalDate.of(2026, 11, 14)
    private val mon = LocalDate.of(2026, 11, 16)
    private val tue = LocalDate.of(2026, 11, 17)

    private fun row(
        end: LocalDate,
        start: LocalDate = LocalDate.of(2026, 11, 2),
        status: SanctionStatus = SanctionStatus.ACTIVE,
        assigned: Int = 300,
        allocated: Int = 0,
        locked: Boolean = false,
        id: Long = 1
    ) = SanctionRow(
        sanction = Sanction(
            id = id, studentId = 1, violationId = 1, baseHours = 5, multiplierApplied = 1,
            assignedMinutes = assigned, assignedDate = start, startDate = start, endDate = end,
            deadlineLocked = locked,
            status = status, createdAt = 0L
        ),
        violationName = "Test",
        allocatedMinutes = allocated
    )

    // ---- Calendar ----

    @Test fun nextServiceDay_midweek() = assertEquals(fri, ScheduleCalculator.nextServiceDay(thu, days))
    @Test fun nextServiceDay_fridayGoesToMonday() = assertEquals(mon, ScheduleCalculator.nextServiceDay(fri, days))
    @Test fun nextServiceDay_saturdayGoesToMonday() = assertEquals(mon, ScheduleCalculator.nextServiceDay(sat, days))
    @Test fun previousServiceDay_mondayGoesToFriday() = assertEquals(fri, ScheduleCalculator.previousServiceDay(mon, days))

    // ---- Planning ----

    @Test
    fun activeSanction_movesOneSchoolDay() {
        val shifts = ExtensionPlanner.plan(listOf(row(end = thu)), thu, thu, days)
        assertEquals(listOf(DeadlineShift(1, thu, fri)), shifts)
    }

    @Test
    fun fridayDeadline_skipsTheWeekend() {
        val shifts = ExtensionPlanner.plan(listOf(row(end = fri)), thu, thu, days)
        assertEquals(mon, shifts[0].newEnd)
    }

    @Test
    fun everyQualifyingSanction_isExtended() {
        val shifts = ExtensionPlanner.plan(listOf(row(end = fri, id = 1), row(end = mon, id = 2)), thu, thu, days)
        assertEquals(listOf(mon, tue), shifts.map { it.newEnd })
    }

    @Test
    fun expiredSanction_isNotExtended() =
        assertTrue(ExtensionPlanner.plan(listOf(row(end = thu.minusDays(2))), thu, thu, days).isEmpty())

    @Test
    fun notStartedYet_isNotExtended() =
        assertTrue(ExtensionPlanner.plan(listOf(row(end = mon, start = fri)), thu, thu, days).isEmpty())

    @Test
    fun completedOrNothingOwed_isNotExtended() {
        val done = row(end = fri, status = SanctionStatus.COMPLETED, allocated = 300)
        val nothingLeft = row(end = fri, allocated = 300, id = 2)
        assertTrue(ExtensionPlanner.plan(listOf(done, nothingLeft), thu, thu, days).isEmpty())
    }

    @Test
    fun hoursOwedAreNeverChanged() {
        val before = SanctionCalculator.summarize(listOf(row(end = fri)))
        // The planner only moves dates; it has no way to change assigned or credited minutes.
        ExtensionPlanner.plan(listOf(row(end = fri)), thu, thu, days)
        assertEquals(300, before.outstandingMinutes)
    }

    // ---- Locked Deadlines ----

    @Test
    fun lockedSanction_isNotExtended() {
        val lockedRow = row(end = thu, locked = true)
        val shifts = ExtensionPlanner.plan(listOf(lockedRow), thu, thu, days)
        assertTrue(shifts.isEmpty())

        val lockedPlanned = ExtensionPlanner.plannedLocked(listOf(lockedRow), thu, thu)
        assertEquals(1, lockedPlanned.size)
        assertEquals(thu, lockedPlanned[0].sanction.endDate)
    }

    @Test
    fun specifiedExample_lockedSanctionUnchangedOnExcuse() {
        // Locked sanction 2026-11-05 to 2026-11-10, student excused on 2026-11-07, end_date stays 2026-11-10.
        val start = LocalDate.of(2026, 11, 5)
        val end = LocalDate.of(2026, 11, 10)
        val excuseDate = LocalDate.of(2026, 11, 7)
        val today = LocalDate.of(2026, 11, 6)

        val r = row(end = end, start = start, locked = true)
        val shifts = ExtensionPlanner.plan(listOf(r), excuseDate, today, days)
        assertTrue(shifts.isEmpty())

        val locked = ExtensionPlanner.plannedLocked(listOf(r), excuseDate, today)
        assertEquals(1, locked.size)
        assertEquals(end, locked[0].sanction.endDate)
    }

    @Test
    fun mixedCase_normalSanctionExtended_lockedSanctionUnchanged() {
        val normal = row(end = thu, locked = false, id = 1)
        val locked = row(end = thu, locked = true, id = 2)

        val shifts = ExtensionPlanner.plan(listOf(normal, locked), thu, thu, days)
        assertEquals(1, shifts.size)
        assertEquals(1L, shifts[0].sanctionId)
        assertEquals(fri, shifts[0].newEnd)

        val lockedPlanned = ExtensionPlanner.plannedLocked(listOf(normal, locked), thu, thu)
        assertEquals(1, lockedPlanned.size)
        assertEquals(2L, lockedPlanned[0].sanction.id)
        assertEquals(thu, lockedPlanned[0].sanction.endDate)
    }

    // ---- Undoing ----

    @Test
    fun revert_restoresTheExactOldDate_whenNothingElseMovedIt() =
        assertEquals(sat, ExtensionPlanner.revertedEnd(mon, sat, mon, thu, days))

    @Test
    fun revert_stepsBackOneSchoolDay_whenAnotherExcuseAlsoMovedIt() {
        // Excuse A moved Fri -> Mon, excuse B moved Mon -> Tue. Revoking A leaves Tue, so go back to Mon.
        assertEquals(mon, ExtensionPlanner.revertedEnd(tue, fri, mon, thu, days))
    }

    @Test
    fun revert_neverGoesBeforeTheStartDate() {
        assertEquals(fri, ExtensionPlanner.revertedEnd(mon, thu, tue, fri, days))
    }
}
