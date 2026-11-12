package com.school.servicemonitor.domain

import com.school.servicemonitor.data.entity.AttemptResultType
import com.school.servicemonitor.data.entity.AttemptState
import com.school.servicemonitor.data.entity.DailyRecordStatus
import com.school.servicemonitor.data.entity.ServiceAttempt

object DailyRecordResolver {

    data class Resolved(val status: DailyRecordStatus, val creditedMinutes: Int)

    /** Voided attempts are ignored. PASS wins, then an active excuse, then FAIL, otherwise pending. */
    fun resolve(attempts: List<ServiceAttempt>, excused: Boolean = false): Resolved {
        val active = attempts.filter { it.recordState == AttemptState.RECORDED }
        val passes = active.filter { it.result == AttemptResultType.PASS }
        return when {
            passes.isNotEmpty() ->
                Resolved(DailyRecordStatus.PASSED, passes.sumOf { it.creditedMinutes })
            excused -> Resolved(DailyRecordStatus.EXCUSED, 0)
            active.any { it.result == AttemptResultType.FAIL } ->
                Resolved(DailyRecordStatus.FAILED_REPEAT, 0)
            else -> Resolved(DailyRecordStatus.PENDING, 0)
        }
    }
}