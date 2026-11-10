package com.school.servicemonitor.domain

import com.school.servicemonitor.data.entity.AttemptResultType
import com.school.servicemonitor.data.entity.AttemptState
import com.school.servicemonitor.data.entity.DailyRecordStatus
import com.school.servicemonitor.data.entity.ServiceAttempt

object DailyRecordResolver {

    data class Resolved(val status: DailyRecordStatus, val creditedMinutes: Int)

    /** Voided attempts are ignored. Any PASS wins, then any FAIL, otherwise the day is pending. */
    fun resolve(attempts: List<ServiceAttempt>): Resolved {
        val active = attempts.filter { it.recordState == AttemptState.RECORDED }
        val passes = active.filter { it.result == AttemptResultType.PASS }
        return when {
            passes.isNotEmpty() ->
                Resolved(DailyRecordStatus.PASSED, passes.sumOf { it.creditedMinutes })
            active.any { it.result == AttemptResultType.FAIL } ->
                Resolved(DailyRecordStatus.FAILED_REPEAT, 0)
            else -> Resolved(DailyRecordStatus.PENDING, 0)
        }
    }
}