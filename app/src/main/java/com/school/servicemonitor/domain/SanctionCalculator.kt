package com.school.servicemonitor.domain

import com.school.servicemonitor.data.entity.SanctionRow
import com.school.servicemonitor.data.entity.SanctionStatus

data class HourSummary(
    val totalAssignedMinutes: Int,
    val completedMinutes: Int,
    val outstandingMinutes: Int,
    val activeSanctionCount: Int
)

object SanctionCalculator {

    /** Minutes still owed on one sanction. Completed and exempted sanctions owe nothing. */
    fun remainingMinutes(row: SanctionRow): Int {
        val counts = row.sanction.status == SanctionStatus.ACTIVE ||
                row.sanction.status == SanctionStatus.OVERDUE
        if (!counts) return 0
        return (row.sanction.assignedMinutes - row.allocatedMinutes).coerceAtLeast(0)
    }

    /** One combined summary for a student. The individual rows are untouched. */
    fun summarize(rows: List<SanctionRow>): HourSummary = HourSummary(
        totalAssignedMinutes = rows.sumOf { it.sanction.assignedMinutes },
        completedMinutes = rows.sumOf { it.allocatedMinutes },
        outstandingMinutes = rows.sumOf { remainingMinutes(it) },
        activeSanctionCount = rows.count { remainingMinutes(it) > 0 }
    )
}