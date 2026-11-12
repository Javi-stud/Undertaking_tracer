package com.school.servicemonitor.domain

import com.school.servicemonitor.data.entity.SanctionRow
import com.school.servicemonitor.data.entity.SanctionStatus
import java.time.DayOfWeek
import java.time.LocalDate

data class DeadlineShift(val sanctionId: Long, val oldEnd: LocalDate, val newEnd: LocalDate)

object ExtensionPlanner {

    /**
     * Which sanctions get a one-school-day extension because of an excuse on [excuseDate].
     * Only ACTIVE sanctions that had started, still owe hours, and have not expired.
     */
    fun plan(
        rows: List<SanctionRow>,
        excuseDate: LocalDate,
        today: LocalDate,
        serviceDays: Set<DayOfWeek>
    ): List<DeadlineShift> =
        rows.filter {
            it.sanction.status == SanctionStatus.ACTIVE &&
                    !it.sanction.startDate.isAfter(excuseDate) &&
                    !it.sanction.endDate.isBefore(today) &&
                    SanctionCalculator.remainingMinutes(it) > 0
        }.map {
            DeadlineShift(
                sanctionId = it.sanction.id,
                oldEnd = it.sanction.endDate,
                newEnd = ScheduleCalculator.nextServiceDay(it.sanction.endDate, serviceDays)
            )
        }

    /**
     * The deadline after an extension is undone. If nothing else moved it, restore the exact
     * old date. Otherwise step back one school day. Never earlier than the start date.
     */
    fun revertedEnd(
        current: LocalDate,
        oldEnd: LocalDate,
        newEnd: LocalDate,
        start: LocalDate,
        serviceDays: Set<DayOfWeek>
    ): LocalDate {
        val target = if (current == newEnd) oldEnd
        else ScheduleCalculator.previousServiceDay(current, serviceDays)
        return if (target.isBefore(start)) start else target
    }
}