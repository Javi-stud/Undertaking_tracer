package com.school.servicemonitor.report

import com.school.servicemonitor.data.entity.*
import com.school.servicemonitor.domain.DailyStatus
import com.school.servicemonitor.domain.DashboardEntry
import com.school.servicemonitor.domain.HistoryBuilder
import com.school.servicemonitor.domain.SanctionCalculator
import com.school.servicemonitor.domain.formatClock
import com.school.servicemonitor.ui.components.display
import com.school.servicemonitor.ui.components.formatMinutes
import com.school.servicemonitor.ui.components.formatTimestamp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

object ReportContent {

    // ---------- small helpers ----------

    private fun none(text: String = "None recorded.") = ReportBlock.Paragraph(text, muted = true)

    /** An empty table is replaced by a short "None recorded." line. */
    private fun table(
        headers: List<String>,
        rows: List<List<String>>,
        weights: List<Float> = emptyList()
    ): ReportBlock = if (rows.isEmpty()) none() else ReportBlock.Table(headers, rows, weights)

    private fun yesNo(value: Boolean) = if (value) "Yes" else "No"

    private fun statusLabel(s: SanctionStatus) = when (s) {
        SanctionStatus.ACTIVE -> "Active"
        SanctionStatus.COMPLETED -> "Completed"
        SanctionStatus.OVERDUE -> "Overdue"
        SanctionStatus.EXEMPTED -> "Exempted"
    }

    private fun assignedText(s: Sanction): String =
        formatMinutes(s.assignedMinutes) +
                if (s.multiplierApplied > 1) " (${formatMinutes(s.baseHours * 60)} x${s.multiplierApplied} SSG)" else ""

    private fun attemptLabel(a: ServiceAttempt) =
        if (a.recordState == AttemptState.VOIDED) "VOIDED" else a.result.name

    private fun attemptNote(a: ServiceAttempt): String {
        val parts = mutableListOf<String>()
        a.failReason?.let { parts += "Reason: $it" }
        a.voidReason?.let { parts += "Voided: $it" }
        return parts.joinToString(". ").ifEmpty { "-" }
    }

    private fun dailyLabel(s: DailyStatus) = when (s) {
        DailyStatus.PENDING -> "Pending"
        DailyStatus.PASSED -> "Passed"
        DailyStatus.FAILED_REPEAT -> "Repeat needed"
        DailyStatus.EXCUSED -> "Excused"
    }

    private fun attemptRow(date: LocalDate, a: ServiceAttempt) = listOf(
        date.display(),
        "#${a.attemptNo}",
        a.startTime.formatClock(),
        a.endTime.formatClock(),
        formatMinutes(a.durationMinutes),
        attemptLabel(a),
        formatMinutes(a.creditedMinutes),
        attemptNote(a)
    )

    // ---------- individual student report ----------

    fun studentReport(
        student: Student,
        sanctionRows: List<SanctionRow>,
        attempts: List<AttemptHistoryRow>,
        excuses: List<Excuse>,
        exemptions: List<ExemptionRow>,
        events: List<DeadlineEventRow>,
        generatedAt: String
    ): ReportDocument {
        val summary = SanctionCalculator.summarize(sanctionRows)
        val sortedAttempts = attempts.sortedWith(
            compareByDescending<AttemptHistoryRow> { it.serviceDate }.thenBy { it.attempt.attemptNo }
        )
        val attendance = HistoryBuilder.summarize(HistoryBuilder.group(sortedAttempts))
        val blocks = mutableListOf<ReportBlock>()

        blocks += ReportBlock.Heading("Student information")
        blocks += ReportBlock.KeyValues(
            listOf(
                "Name" to student.fullName,
                "Student number" to student.studentNumber,
                "Grade level" to student.gradeLevel,
                "Section" to student.section,
                "SSG member" to yesNo(student.isSsg),
                "Record status" to if (student.status == StudentStatus.ARCHIVED) "Archived" else "Active",
                "Registered" to student.registrationDate.display()
            )
        )

        blocks += ReportBlock.Heading("Service hours")
        blocks += ReportBlock.KeyValues(
            listOf(
                "Combined outstanding (remaining)" to formatMinutes(summary.outstandingMinutes),
                "Completed" to formatMinutes(summary.completedMinutes),
                "Permanently exempted" to formatMinutes(summary.exemptedMinutes),
                "Total assigned" to formatMinutes(summary.totalAssignedMinutes),
                "Sanctions still owing hours" to summary.activeSanctionCount.toString()
            )
        )

        blocks += ReportBlock.Heading("Violations and sanctions")
        val byViolation = sanctionRows.groupingBy { it.violationName }.eachCount()
        blocks += if (byViolation.isEmpty()) none() else ReportBlock.Paragraph(
            "Violations on record: " + byViolation.entries.joinToString(", ") { "${it.key} (${it.value})" }
        )
        blocks += table(
            listOf("#", "Violation", "Assigned", "Completed", "Exempted", "Remaining", "Start", "Deadline", "Status", "Repeat of"),
            sanctionRows.map { row ->
                val s = row.sanction
                listOf(
                    "#${s.id}",
                    row.violationName,
                    assignedText(s),
                    formatMinutes(row.allocatedMinutes),
                    formatMinutes(row.exemptedMinutes),
                    formatMinutes(SanctionCalculator.remainingMinutes(row)),
                    s.startDate.display(),
                    s.endDate.display(),
                    statusLabel(s.status),
                    s.parentSanctionId?.let { "#$it" } ?: "-"
                )
            },
            listOf(0.5f, 2f, 1.7f, 1.1f, 1.1f, 1.1f, 1.2f, 1.2f, 1f, 0.9f)
        )

        blocks += ReportBlock.Heading("Repeated sanctions")
        blocks += table(
            listOf("Violation", "Deadline passed", "Original", "Decision", "Details"),
            events.map { row ->
                val e = row.event
                listOf(
                    row.violationName,
                    e.triggeringDeadline.display(),
                    "${formatMinutes(row.assignedMinutes)} assigned, ${formatMinutes(row.allocatedMinutes)} completed",
                    when (e.status) {
                        RepeatEventStatus.DETECTED -> "Awaiting decision"
                        RepeatEventStatus.CONFIRMED -> "Confirmed"
                        RepeatEventStatus.DISMISSED -> "Dismissed"
                    },
                    when (e.status) {
                        RepeatEventStatus.DETECTED ->
                            "Proposed repeat of the full ${formatMinutes(row.assignedMinutes)} (nothing added yet)"
                        RepeatEventStatus.CONFIRMED ->
                            "Repeat of the full ${formatMinutes(row.assignedMinutes)} created as #${e.proposedSanctionId}"
                        RepeatEventStatus.DISMISSED -> "Reason: ${e.dismissReason.orEmpty()}"
                    }
                )
            },
            listOf(2f, 1.3f, 2.4f, 1.3f, 3.2f)
        )

        blocks += ReportBlock.Heading("Attendance history")
        blocks += ReportBlock.KeyValues(
            listOf(
                "Days passed" to attendance.daysPassed.toString(),
                "Attempts recorded (including voided)" to attendance.totalAttempts.toString(),
                "Failed attempts" to attendance.failedAttempts.toString(),
                "Voided attempts" to attendance.voidedAttempts.toString(),
                "Total credited" to formatMinutes(attendance.creditedMinutes)
            )
        )
        blocks += table(
            listOf("Date", "Attempt", "Start", "End", "Duration", "Result", "Credited", "Note"),
            sortedAttempts.map { attemptRow(it.serviceDate, it.attempt) },
            listOf(1.3f, 0.8f, 1f, 1f, 1f, 0.9f, 1f, 3.5f)
        )

        blocks += ReportBlock.Heading("Failed attempts")
        blocks += table(
            listOf("Date", "Attempt", "Start", "End", "Reason"),
            sortedAttempts
                .filter { it.attempt.recordState == AttemptState.RECORDED && it.attempt.result == AttemptResultType.FAIL }
                .map {
                    listOf(
                        it.serviceDate.display(),
                        "#${it.attempt.attemptNo}",
                        it.attempt.startTime.formatClock(),
                        it.attempt.endTime.formatClock(),
                        it.attempt.failReason.orEmpty().ifEmpty { "-" }
                    )
                },
            listOf(1.3f, 0.8f, 1f, 1f, 4f)
        )

        blocks += ReportBlock.Heading("Voided attempts")
        blocks += table(
            listOf("Date", "Attempt", "Original result", "Credit kept in record", "Void reason", "Voided at"),
            sortedAttempts
                .filter { it.attempt.recordState == AttemptState.VOIDED }
                .map {
                    listOf(
                        it.serviceDate.display(),
                        "#${it.attempt.attemptNo}",
                        it.attempt.result.name,
                        formatMinutes(it.attempt.creditedMinutes),
                        it.attempt.voidReason.orEmpty().ifEmpty { "-" },
                        it.attempt.voidedAt?.let(::formatTimestamp) ?: "-"
                    )
                },
            listOf(1.3f, 0.8f, 1.2f, 1.4f, 3f, 1.8f)
        )

        blocks += ReportBlock.Heading("Daily excuses")
        blocks += table(
            listOf("Date", "Reason", "Status", "Revoke reason"),
            excuses.map {
                listOf(
                    it.excuseDate.display(),
                    it.reason,
                    if (it.revokedAt == null) "Excused" else "Revoked",
                    it.revokeReason ?: "-"
                )
            },
            listOf(1.3f, 3.5f, 1f, 3f)
        )

        blocks += ReportBlock.Heading("Permanent exemptions")
        blocks += table(
            listOf("Violation", "Exempted", "Approved by", "Approval date", "Reason"),
            exemptions.map {
                val x = it.exemption
                listOf(
                    it.violationName,
                    formatMinutes(x.exemptedMinutes),
                    x.approvedBy,
                    x.approvalDate.display(),
                    x.reason
                )
            },
            listOf(2f, 1.1f, 1.6f, 1.4f, 3.5f)
        )

        return ReportDocument(
            title = "Student Community Service Report",
            subtitle = "${student.fullName}  |  No. ${student.studentNumber}  |  ${student.gradeLevel} - ${student.section}",
            generatedAt = generatedAt,
            blocks = blocks
        )
    }

    // ---------- daily attendance report ----------

    fun dailyReport(
        date: LocalDate,
        isServiceDay: Boolean,
        isPastDate: Boolean,
        entries: List<DashboardEntry>,
        attempts: List<AttemptHistoryRow>,
        excuses: List<ExcuseHistoryRow>,
        generatedAt: String
    ): ReportDocument {
        val blocks = mutableListOf<ReportBlock>()
        val failedAttempts = attempts.count {
            it.attempt.recordState == AttemptState.RECORDED && it.attempt.result == AttemptResultType.FAIL
        }
        val voidedAttempts = attempts.count { it.attempt.recordState == AttemptState.VOIDED }

        blocks += ReportBlock.Heading("Summary")
        blocks += ReportBlock.KeyValues(
            listOf(
                "Date" to date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US) + ", " + date.display(),
                "Service day" to yesNo(isServiceDay),
                "Students scheduled" to entries.size.toString(),
                "Passed" to entries.count { it.status == DailyStatus.PASSED }.toString(),
                "Repeat needed (failed, not yet passed)" to entries.count { it.status == DailyStatus.FAILED_REPEAT }.toString(),
                "Excused" to entries.count { it.status == DailyStatus.EXCUSED }.toString(),
                "Still pending" to entries.count { it.status == DailyStatus.PENDING }.toString(),
                "Failed attempts recorded" to failedAttempts.toString(),
                "Voided attempts" to voidedAttempts.toString(),
                "Total credited" to formatMinutes(entries.sumOf { it.creditedMinutes })
            )
        )
        if (isPastDate) {
            blocks += ReportBlock.Paragraph(
                "This is a past date. The student list uses each student's current outstanding hours, " +
                        "so students who have since finished may not appear. The attempts and excuses below are exact.",
                muted = true
            )
        }

        blocks += ReportBlock.Heading("Students")
        blocks += table(
            listOf("No.", "Student", "Grade - Section", "Status", "Attempts", "Credited", "Outstanding"),
            entries.map {
                listOf(
                    it.student.studentNumber,
                    it.student.fullName + if (it.student.isSsg) " (SSG)" else "",
                    "${it.student.gradeLevel} - ${it.student.section}",
                    dailyLabel(it.status),
                    it.attemptCount.toString(),
                    formatMinutes(it.creditedMinutes),
                    formatMinutes(it.outstandingMinutes)
                )
            },
            listOf(1f, 2.6f, 1.6f, 1.3f, 0.9f, 1.1f, 1.3f)
        )

        blocks += ReportBlock.Heading("Recorded cleaning times")
        blocks += table(
            listOf("Student", "Attempt", "Start", "End", "Duration", "Result", "Credited", "Note"),
            attempts.map {
                val a = it.attempt
                listOf(
                    HistoryBuilder.displayName(it.lastName, it.firstName, it.middleName),
                    "#${a.attemptNo}",
                    a.startTime.formatClock(),
                    a.endTime.formatClock(),
                    formatMinutes(a.durationMinutes),
                    attemptLabel(a),
                    formatMinutes(a.creditedMinutes),
                    attemptNote(a)
                )
            },
            listOf(2.4f, 0.8f, 1f, 1f, 1f, 0.9f, 1f, 3f)
        )

        blocks += ReportBlock.Heading("Excused students")
        blocks += table(
            listOf("Student", "Reason", "Status", "Revoke reason"),
            excuses.map {
                listOf(
                    HistoryBuilder.displayName(it.lastName, it.firstName, it.middleName),
                    it.excuse.reason,
                    if (it.excuse.revokedAt == null) "Excused" else "Revoked",
                    it.excuse.revokeReason ?: "-"
                )
            },
            listOf(2.4f, 3.5f, 1f, 3f)
        )

        return ReportDocument(
            title = "Daily Attendance Report",
            subtitle = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US) + ", " + date.display(),
            generatedAt = generatedAt,
            blocks = blocks
        )
    }

    // ---------- sanction report ----------

    fun sanctionReport(
        rows: List<SanctionRow>,
        students: Map<Long, Student>,
        onlyOutstanding: Boolean,
        generatedAt: String
    ): ReportDocument {
        // Repeat links are worked out from ALL rows, even if the list below is filtered.
        val children: Map<Long, List<Long>> = rows
            .mapNotNull { r -> r.sanction.parentSanctionId?.let { parent -> parent to r.sanction.id } }
            .groupBy({ it.first }, { it.second })

        val listed = rows
            .filter { !onlyOutstanding || SanctionCalculator.remainingMinutes(it) > 0 }
            .sortedWith(
                compareBy<SanctionRow>(
                    { students[it.sanction.studentId]?.fullName.orEmpty() },
                    { it.sanction.endDate },
                    { it.sanction.id }
                )
            )

        fun chain(row: SanctionRow): String {
            val parts = mutableListOf<String>()
            row.sanction.parentSanctionId?.let { parts += "Repeat of #$it" }
            children[row.sanction.id]?.let { ids -> parts += "Repeated as " + ids.joinToString(", ") { "#$it" } }
            return parts.joinToString("; ").ifEmpty { "-" }
        }

        val blocks = mutableListOf<ReportBlock>()
        blocks += ReportBlock.Heading("Summary")
        blocks += ReportBlock.KeyValues(
            listOf(
                "Included" to if (onlyOutstanding) "Only sanctions that still owe hours" else "All sanctions",
                "Sanctions listed" to listed.size.toString(),
                "Total assigned" to formatMinutes(listed.sumOf { it.sanction.assignedMinutes }),
                "Completed" to formatMinutes(listed.sumOf { it.allocatedMinutes }),
                "Permanently exempted" to formatMinutes(listed.sumOf { it.exemptedMinutes }),
                "Remaining" to formatMinutes(listed.sumOf { SanctionCalculator.remainingMinutes(it) })
            )
        )
        blocks += ReportBlock.Paragraph("Covers active students only.", muted = true)

        blocks += ReportBlock.Heading("Sanctions")
        blocks += table(
            listOf("#", "Student", "Violation", "Assigned", "Completed", "Exempted", "Remaining", "Start", "Deadline", "Status", "Repeat history"),
            listed.map { row ->
                val s = row.sanction
                listOf(
                    "#${s.id}",
                    students[s.studentId]?.fullName ?: "Unknown",
                    row.violationName,
                    assignedText(s),
                    formatMinutes(row.allocatedMinutes),
                    formatMinutes(row.exemptedMinutes),
                    formatMinutes(SanctionCalculator.remainingMinutes(row)),
                    s.startDate.display(),
                    s.endDate.display(),
                    statusLabel(s.status),
                    chain(row)
                )
            },
            listOf(0.5f, 2f, 1.8f, 1.6f, 1.1f, 1f, 1.1f, 1.2f, 1.2f, 1f, 1.6f)
        )

        return ReportDocument(
            title = "Sanction Report",
            subtitle = if (onlyOutstanding) "Sanctions that still owe hours" else "All sanctions",
            generatedAt = generatedAt,
            blocks = blocks
        )
    }
}