package com.school.servicemonitor.report

import com.school.servicemonitor.data.entity.*
import com.school.servicemonitor.domain.DailyStatus
import com.school.servicemonitor.domain.DashboardEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ReportContentTest {

    private val day = LocalDate.of(2026, 11, 12)

    private val student = Student(
        id = 1, studentNumber = "S1", firstName = "Juan", lastName = "Cruz",
        gradeLevel = "Grade 10", section = "A", registrationDate = day, createdAt = 0L, updatedAt = 0L
    )

    private fun row(
        id: Long, assigned: Int, allocated: Int = 0, exempted: Int = 0, parent: Long? = null,
        status: SanctionStatus = SanctionStatus.ACTIVE, name: String = "Uniform"
    ) = SanctionRow(
        sanction = Sanction(
            id = id, studentId = 1, violationId = 1, baseHours = assigned / 60, multiplierApplied = 1,
            assignedMinutes = assigned, assignedDate = day, startDate = day, endDate = day.plusDays(8),
            status = status, parentSanctionId = parent, createdAt = 0L
        ),
        violationName = name, allocatedMinutes = allocated, exemptedMinutes = exempted
    )

    private fun attempt(
        no: Int, result: AttemptResultType, credited: Int = 0, state: AttemptState = AttemptState.RECORDED,
        failReason: String? = null, voidReason: String? = null
    ) = AttemptHistoryRow(
        attempt = ServiceAttempt(
            id = no.toLong(), dailyRecordId = 1, studentId = 1, attemptNo = no,
            startTime = LocalTime.of(16, 0), endTime = LocalTime.of(17, 0), durationMinutes = 60,
            result = result, creditedMinutes = credited, failReason = failReason,
            recordState = state, voidReason = voidReason, createdAt = 0L
        ),
        serviceDate = day, studentNumber = "S1", firstName = "Juan", middleName = null, lastName = "Cruz"
    )

    private fun ReportDocument.text(): String = blocks.joinToString("\n") { b ->
        when (b) {
            is ReportBlock.Heading -> b.text
            is ReportBlock.Paragraph -> b.text
            is ReportBlock.KeyValues -> b.rows.joinToString("\n") { "${it.first}: ${it.second}" }
            is ReportBlock.Table -> (listOf(b.headers) + b.rows).joinToString("\n") { it.joinToString(" | ") }
        }
    }

    private fun student(
        rows: List<SanctionRow> = emptyList(),
        attempts: List<AttemptHistoryRow> = emptyList()
    ) = ReportContent.studentReport(student, rows, attempts, emptyList(), emptyList(), emptyList(), "now")

    @Test
    fun studentReport_showsTheCombinedOutstandingHours() {
        // 10 h + 5 h with one hour credited = 14 h
        val doc = student(rows = listOf(row(1, 600, allocated = 60), row(2, 300)))
        assertTrue(doc.text().contains("Combined outstanding (remaining): 14 hours"))
        assertTrue(doc.text().contains("Completed: 1 hour"))
    }

    @Test
    fun studentReport_withNoData_hasNoEmptyTables() {
        val doc = student()
        assertTrue(doc.blocks.filterIsInstance<ReportBlock.Table>().isEmpty())
        assertTrue(doc.text().contains("None recorded."))
        assertTrue(doc.text().contains("Combined outstanding (remaining): 0 hours"))
    }

    @Test
    fun studentReport_everyTableRowMatchesItsHeaders() {
        val doc = student(
            rows = listOf(row(1, 300)),
            attempts = listOf(attempt(1, AttemptResultType.FAIL, failReason = "Not clean"))
        )
        doc.blocks.filterIsInstance<ReportBlock.Table>().forEach { t ->
            assertTrue(t.rows.all { it.size == t.headers.size })
        }
    }

    @Test
    fun studentReport_listsFailedAndVoidedAttemptsWithTheirReasons() {
        val doc = student(
            attempts = listOf(
                attempt(1, AttemptResultType.FAIL, failReason = "Not clean"),
                attempt(2, AttemptResultType.PASS, 60, AttemptState.VOIDED, voidReason = "Wrong student")
            )
        )
        val text = doc.text()
        assertTrue(text.contains("Not clean"))
        assertTrue(text.contains("Wrong student"))
        assertTrue(text.contains("VOIDED"))
        assertTrue(text.contains("Failed attempts: 1"))
        assertTrue(text.contains("Voided attempts: 1"))
    }

    @Test
    fun studentReport_showsTheLinkOfARepeatedSanction() {
        val doc = student(rows = listOf(row(1, 300, status = SanctionStatus.OVERDUE), row(2, 300, parent = 1)))
        assertTrue(doc.text().contains("#1"))
        assertTrue(doc.blocks.filterIsInstance<ReportBlock.Table>().first().rows.any { it.last() == "#1" })
    }

    @Test
    fun studentReport_explainsADismissedRepeat() {
        val event = DeadlineEventRow(
            event = DeadlineRepeatEvent(
                id = 1, originalSanctionId = 1, triggeringDeadline = day.minusDays(3),
                status = RepeatEventStatus.DISMISSED, detectedAt = 0L, dismissReason = "Family emergency"
            ),
            studentId = 1, studentNumber = "S1", firstName = "Juan", middleName = null, lastName = "Cruz",
            violationName = "Uniform", assignedMinutes = 300, allocatedMinutes = 180, exemptedMinutes = 0
        )
        val doc = ReportContent.studentReport(student, emptyList(), emptyList(), emptyList(), emptyList(), listOf(event), "now")
        assertTrue(doc.text().contains("Family emergency"))
        assertTrue(doc.text().contains("Dismissed"))
    }

    @Test
    fun dailyReport_countsEachStatus() {
        fun entry(id: Long, status: DailyStatus, credited: Int = 0) = DashboardEntry(
            student = student.copy(id = id, studentNumber = "S$id"), status = status, outstandingMinutes = 300,
            activeSanctionCount = 1, creditedMinutes = credited, attemptCount = 1, nextDeadline = null
        )
        val doc = ReportContent.dailyReport(
            date = day, isServiceDay = true, isPastDate = false,
            entries = listOf(
                entry(1, DailyStatus.PASSED, 60), entry(2, DailyStatus.PASSED, 60),
                entry(3, DailyStatus.FAILED_REPEAT), entry(4, DailyStatus.EXCUSED), entry(5, DailyStatus.PENDING)
            ),
            attempts = listOf(attempt(1, AttemptResultType.FAIL, failReason = "x")),
            excuses = emptyList(), generatedAt = "now"
        )
        val text = doc.text()
        assertTrue(text.contains("Students scheduled: 5"))
        assertTrue(text.contains("Passed: 2"))
        assertTrue(text.contains("Excused: 1"))
        assertTrue(text.contains("Still pending: 1"))
        assertTrue(text.contains("Failed attempts recorded: 1"))
        assertTrue(text.contains("Total credited: 2 hours"))
        assertFalse(text.contains("past date"))
    }

    @Test
    fun dailyReport_ofAPastDate_addsTheCaveat() {
        val doc = ReportContent.dailyReport(day, true, true, emptyList(), emptyList(), emptyList(), "now")
        assertTrue(doc.text().contains("past date"))
    }

    @Test
    fun sanctionReport_showsRepeatLinksBothWays() {
        val rows = listOf(row(1, 300, status = SanctionStatus.OVERDUE), row(2, 300, parent = 1))
        val doc = ReportContent.sanctionReport(rows, mapOf(1L to student), false, "now")
        val table = doc.blocks.filterIsInstance<ReportBlock.Table>().first()
        assertEquals("Repeated as #2", table.rows.first { it.first() == "#1" }.last())
        assertEquals("Repeat of #1", table.rows.first { it.first() == "#2" }.last())
    }

    @Test
    fun sanctionReport_onlyOutstanding_hidesFinishedSanctions_butKeepsTheChain() {
        val rows = listOf(
            row(1, 300, allocated = 300, status = SanctionStatus.COMPLETED),
            row(2, 300, parent = 1)
        )
        val doc = ReportContent.sanctionReport(rows, mapOf(1L to student), true, "now")
        val table = doc.blocks.filterIsInstance<ReportBlock.Table>().first()
        assertEquals(1, table.rows.size)
        assertEquals("Repeat of #1", table.rows.first().last())
        assertTrue(doc.text().contains("Sanctions listed: 1"))
    }

    @Test
    fun sanctionReport_totalsAddUp() {
        val rows = listOf(row(1, 600, allocated = 60), row(2, 300))
        val text = ReportContent.sanctionReport(rows, mapOf(1L to student), false, "now").text()
        assertTrue(text.contains("Total assigned: 15 hours"))
        assertTrue(text.contains("Completed: 1 hour"))
        assertTrue(text.contains("Remaining: 14 hours"))
    }
}