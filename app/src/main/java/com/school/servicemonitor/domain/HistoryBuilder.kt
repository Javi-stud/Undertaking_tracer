package com.school.servicemonitor.domain

import com.school.servicemonitor.data.entity.AttemptHistoryRow
import com.school.servicemonitor.data.entity.AttemptResultType
import com.school.servicemonitor.data.entity.AttemptState
import com.school.servicemonitor.data.entity.DailyRecordStatus
import com.school.servicemonitor.data.entity.ServiceAttempt
import java.time.LocalDate

enum class DayOutcome { PASSED, FAILED, ALL_VOIDED }

/** One student's attempts on one date. */
data class StudentDayHistory(
    val studentId: Long,
    val studentNumber: String,
    val displayName: String,
    val date: LocalDate,
    val attempts: List<ServiceAttempt>,   // sorted by attempt number, voided ones included
    val outcome: DayOutcome,
    val creditedMinutes: Int
)

data class AttendanceSummary(
    val daysPassed: Int,
    val totalAttempts: Int,
    val failedAttempts: Int,     // recorded (not voided) FAIL attempts
    val voidedAttempts: Int,
    val creditedMinutes: Int
)

object HistoryBuilder {

    /** "Dela Cruz, Juan A." */
    fun displayName(last: String, first: String, middle: String?): String {
        val initial = middle
            ?.takeIf { it.isNotBlank() }
            ?.let { " ${it.trim().first().uppercaseChar()}." }
            ?: ""
        return "$last, $first$initial"
    }

    /**
     * Groups rows by student and date. The order of the input is kept, so the
     * caller decides whether the list is sorted by name or by newest date.
     */
    fun group(rows: List<AttemptHistoryRow>): List<StudentDayHistory> =
        rows.groupBy { it.attempt.studentId to it.serviceDate }
            .map { (key, groupRows) ->
                val first = groupRows.first()
                val attempts = groupRows.map { it.attempt }.sortedBy { it.attemptNo }
                val resolved = DailyRecordResolver.resolve(attempts)
                StudentDayHistory(
                    studentId = key.first,
                    studentNumber = first.studentNumber,
                    displayName = displayName(first.lastName, first.firstName, first.middleName),
                    date = key.second,
                    attempts = attempts,
                    outcome = when (resolved.status) {
                        DailyRecordStatus.PASSED -> DayOutcome.PASSED
                        DailyRecordStatus.FAILED_REPEAT -> DayOutcome.FAILED
                        else -> DayOutcome.ALL_VOIDED
                    },
                    creditedMinutes = resolved.creditedMinutes
                )
            }

    fun summarize(days: List<StudentDayHistory>): AttendanceSummary {
        val attempts = days.flatMap { it.attempts }
        return AttendanceSummary(
            daysPassed = days.count { it.outcome == DayOutcome.PASSED },
            totalAttempts = attempts.size,
            failedAttempts = attempts.count {
                it.recordState == AttemptState.RECORDED && it.result == AttemptResultType.FAIL
            },
            voidedAttempts = attempts.count { it.recordState == AttemptState.VOIDED },
            creditedMinutes = days.sumOf { it.creditedMinutes }
        )
    }
}