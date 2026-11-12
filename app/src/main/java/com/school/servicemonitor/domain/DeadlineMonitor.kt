package com.school.servicemonitor.domain

import com.school.servicemonitor.data.entity.Sanction
import com.school.servicemonitor.data.entity.SanctionRow
import com.school.servicemonitor.data.entity.SanctionStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object DeadlineMonitor {

    /**
     * Overdue = still open, the deadline is BEFORE today (the deadline day itself is still fine),
     * and hours are still owed.
     */
    fun isOverdue(row: SanctionRow, today: LocalDate): Boolean =
        (row.sanction.status == SanctionStatus.ACTIVE || row.sanction.status == SanctionStatus.OVERDUE) &&
                row.sanction.endDate.isBefore(today) &&
                SanctionCalculator.remainingMinutes(row) > 0

    fun overdue(rows: List<SanctionRow>, today: LocalDate): List<SanctionRow> =
        rows.filter { isOverdue(it, today) }

    fun daysOverdue(deadline: LocalDate, today: LocalDate): Int =
        ChronoUnit.DAYS.between(deadline, today).toInt().coerceAtLeast(0)

    /** A repeat always carries the FULL original assigned hours, never just the remaining ones. */
    fun proposedMinutes(original: Sanction): Int = original.assignedMinutes

    /** An error message for the dates the operator picked, or null when they are fine. */
    fun validateRepeatDates(start: LocalDate, end: LocalDate, today: LocalDate): String? = when {
        end.isBefore(start) -> "The new deadline cannot be before the new start date."
        end.isBefore(today) -> "The new deadline cannot be in the past."
        else -> null
    }
}