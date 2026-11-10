package com.school.servicemonitor.domain

import com.school.servicemonitor.data.entity.DailyRecordStatus
import com.school.servicemonitor.data.entity.Sanction
import com.school.servicemonitor.data.entity.SanctionRow
import com.school.servicemonitor.data.entity.SanctionStatus
import com.school.servicemonitor.data.entity.Student
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DashboardBuilderTest {

    private val tuesday = LocalDate.of(2026, 11, 10)

    private fun student(id: Long) = Student(
        id = id,
        studentNumber = "S$id",
        firstName = "First$id",
        lastName = "Last$id",
        gradeLevel = "Grade 10",
        section = "A",
        registrationDate = tuesday,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun row(
        studentId: Long,
        id: Long = 1,
        assigned: Int = 300,
        allocated: Int = 0,
        start: LocalDate = LocalDate.of(2026, 11, 1),
        end: LocalDate = LocalDate.of(2026, 11, 20),
        status: SanctionStatus = SanctionStatus.ACTIVE
    ) = SanctionRow(
        sanction = Sanction(
            id = id,
            studentId = studentId,
            violationId = 1,
            baseHours = assigned / 60,
            multiplierApplied = 1,
            assignedMinutes = assigned,
            assignedDate = start,
            startDate = start,
            endDate = end,
            status = status,
            createdAt = 0L
        ),
        violationName = "Test",
        allocatedMinutes = allocated
    )

    private fun build(
        rows: List<SanctionRow>,
        records: Map<Long, DayRecordInfo> = emptyMap(),
        date: LocalDate = tuesday,
        serviceDay: Boolean = true,
        ids: List<Long> = listOf(1)
    ) = DashboardBuilder.build(ids.map { student(it) }, rows, records, date, serviceDay)

    private val passed = DayRecordInfo(DailyRecordStatus.PASSED, 60, 1)

    @Test
    fun studentWithOutstandingHours_isPending() {
        val result = build(listOf(row(1)))
        assertEquals(1, result.size)
        assertEquals(DailyStatus.PENDING, result[0].status)
    }

    @Test
    fun studentWithNoSanctions_doesNotAppear() {
        assertTrue(build(emptyList()).isEmpty())
    }

    @Test
    fun studentWithNothingLeftToDo_doesNotAppear() {
        val done = row(1, assigned = 300, allocated = 300, status = SanctionStatus.COMPLETED)
        assertTrue(build(listOf(done)).isEmpty())
    }

    @Test
    fun weekend_noOnePending() {
        assertTrue(build(listOf(row(1)), date = LocalDate.of(2026, 11, 14), serviceDay = false).isEmpty())
    }

    @Test
    fun sanctionNotStartedYet_isNotRequired() {
        assertTrue(build(listOf(row(1, start = LocalDate.of(2026, 11, 15)))).isEmpty())
    }

    @Test
    fun passedRecord_isPassed_evenWithNothingLeft() {
        val done = row(1, assigned = 300, allocated = 300, status = SanctionStatus.COMPLETED)
        val result = build(listOf(done), records = mapOf(1L to passed))
        assertEquals(DailyStatus.PASSED, result[0].status)
        assertEquals(60, result[0].creditedMinutes)
    }

    @Test
    fun failedRecord_isFailedRepeat_andShowsAttemptCount() {
        val failed = DayRecordInfo(DailyRecordStatus.FAILED_REPEAT, 0, 2)
        val result = build(listOf(row(1)), records = mapOf(1L to failed))
        assertEquals(DailyStatus.FAILED_REPEAT, result[0].status)
        assertEquals(2, result[0].attemptCount)
    }

    @Test
    fun pendingRecord_afterEveryAttemptWasVoided_isPendingAgain() {
        val voided = DayRecordInfo(DailyRecordStatus.PENDING, 0, 0)
        val result = build(listOf(row(1)), records = mapOf(1L to voided))
        assertEquals(DailyStatus.PENDING, result[0].status)
    }

    @Test
    fun combinedTotal_countAndNextDeadline() {
        val alcohol = row(1, id = 1, assigned = 600, end = LocalDate.of(2026, 11, 12))
        val uniform = row(1, id = 2, assigned = 300, end = LocalDate.of(2026, 11, 20))
        val entry = build(listOf(uniform, alcohol))[0]
        assertEquals(900, entry.outstandingMinutes)
        assertEquals(2, entry.activeSanctionCount)
        assertEquals(LocalDate.of(2026, 11, 12), entry.nextDeadline)
    }

    @Test
    fun passedStudent_isShownEvenOnAWeekend() {
        val result = build(
            listOf(row(1)),
            records = mapOf(1L to passed),
            date = LocalDate.of(2026, 11, 14),
            serviceDay = false
        )
        assertEquals(DailyStatus.PASSED, result[0].status)
    }

    @Test
    fun onlyStudentsWhoOweHours_areListed() {
        val result = build(listOf(row(1), row(3, id = 3)), ids = listOf(1, 2, 3))
        assertEquals(listOf(1L, 3L), result.map { it.student.id })
    }
}