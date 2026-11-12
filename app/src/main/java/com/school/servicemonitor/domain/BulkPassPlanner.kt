package com.school.servicemonitor.domain

import java.time.LocalTime

/** One student's PASS request inside a bulk operation. */
data class BulkPassItem(val studentId: Long, val start: LocalTime, val end: LocalTime)

sealed interface BulkRowCheck {
    data object Ok : BulkRowCheck
    data class Problem(val message: String) : BulkRowCheck
}

object BulkPassPlanner {

    /** The same PASS rule as a single attempt, applied to one student's own times. */
    fun check(item: BulkPassItem, requiredMinutes: Int): BulkRowCheck =
        when (val p = TimeRangeCalculator.passCheck(item.start, item.end, requiredMinutes)) {
            PassCheck.Allowed -> BulkRowCheck.Ok
            PassCheck.InvalidRange -> BulkRowCheck.Problem("The end time must be later than the start time.")
            is PassCheck.TooShort ->
                BulkRowCheck.Problem("Only ${p.recordedMinutes} min recorded. At least ${p.requiredMinutes} needed.")
        }

    data class Plan(val valid: List<BulkPassItem>, val problems: Map<Long, String>)

    /** Splits the selection into students that can be saved and students with a problem. */
    fun plan(items: List<BulkPassItem>, requiredMinutes: Int): Plan {
        val valid = mutableListOf<BulkPassItem>()
        val problems = linkedMapOf<Long, String>()
        for (item in items) {
            when (val c = check(item, requiredMinutes)) {
                BulkRowCheck.Ok -> valid += item
                is BulkRowCheck.Problem -> problems[item.studentId] = c.message
            }
        }
        return Plan(valid, problems)
    }
}