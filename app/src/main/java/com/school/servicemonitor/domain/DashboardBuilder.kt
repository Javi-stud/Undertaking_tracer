package com.school.servicemonitor.domain

import com.school.servicemonitor.data.entity.DailyRecordStatus
import com.school.servicemonitor.data.entity.SanctionRow
import com.school.servicemonitor.data.entity.Student
import java.time.LocalDate

enum class DailyStatus { PENDING, PASSED, FAILED_REPEAT, EXCUSED }

/** What the daily record says about one student on the selected date. */
data class DayRecordInfo(
    val status: DailyRecordStatus,
    val creditedMinutes: Int,
    val attemptCount: Int
)

data class DashboardEntry(
    val student: Student,
    val status: DailyStatus,
    val outstandingMinutes: Int,      // the combined total
    val activeSanctionCount: Int,
    val creditedMinutes: Int,         // credited on the selected date
    val attemptCount: Int,            // non-voided attempts on the selected date
    val nextDeadline: LocalDate?      // earliest deadline among sanctions that still owe hours
)

object DashboardBuilder {

    /**
     * Decides who appears on the dashboard for [date].
     * [students] must already be active students, sorted the way they should be shown.
     */
    fun build(
        students: List<Student>,
        sanctionRows: List<SanctionRow>,
        dayRecords: Map<Long, DayRecordInfo>,
        date: LocalDate,
        isServiceDay: Boolean
    ): List<DashboardEntry> {
        val rowsByStudent = sanctionRows.groupBy { it.sanction.studentId }

        return students.mapNotNull { student ->
            val rows = rowsByStudent[student.id].orEmpty()
            val info = dayRecords[student.id]
            val summary = SanctionCalculator.summarize(rows)
            val owing = rows.filter { SanctionCalculator.remainingMinutes(it) > 0 }

            // Required = a service day, and at least one started sanction still owes hours.
            val required = isServiceDay && owing.any { !it.sanction.startDate.isAfter(date) }

            val status = when (info?.status) {
                DailyRecordStatus.PASSED -> DailyStatus.PASSED
                DailyRecordStatus.EXCUSED -> DailyStatus.EXCUSED
                DailyRecordStatus.FAILED_REPEAT -> DailyStatus.FAILED_REPEAT
                else -> if (required) DailyStatus.PENDING else return@mapNotNull null
            }

            DashboardEntry(
                student = student,
                status = status,
                outstandingMinutes = summary.outstandingMinutes,
                activeSanctionCount = summary.activeSanctionCount,
                creditedMinutes = info?.creditedMinutes ?: 0,
                attemptCount = info?.attemptCount ?: 0,
                nextDeadline = owing.minOfOrNull { it.sanction.endDate }
            )
        }
    }
}