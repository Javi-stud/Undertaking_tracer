package com.school.servicemonitor.domain

import com.school.servicemonitor.data.entity.AttemptResultType
import com.school.servicemonitor.data.entity.AttemptState
import com.school.servicemonitor.data.entity.DailyRecordStatus
import com.school.servicemonitor.data.entity.ServiceAttempt
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class DailyRecordResolverTest {

    private fun attempt(
        no: Int,
        result: AttemptResultType,
        credited: Int = 0,
        state: AttemptState = AttemptState.RECORDED
    ) = ServiceAttempt(
        id = no.toLong(),
        dailyRecordId = 1,
        studentId = 1,
        attemptNo = no,
        startTime = LocalTime.of(16, 0),
        endTime = LocalTime.of(17, 0),
        durationMinutes = 60,
        result = result,
        creditedMinutes = credited,
        recordState = state,
        createdAt = 0L
    )

    @Test
    fun noAttempts_isPending() =
        assertEquals(DailyRecordStatus.PENDING, DailyRecordResolver.resolve(emptyList()).status)

    @Test
    fun failOnly_isFailedRepeat_withZeroCredit() {
        val r = DailyRecordResolver.resolve(listOf(attempt(1, AttemptResultType.FAIL)))
        assertEquals(DailyRecordStatus.FAILED_REPEAT, r.status)
        assertEquals(0, r.creditedMinutes)
    }

    @Test
    fun failThenPass_isPassed_with60() {
        val r = DailyRecordResolver.resolve(
            listOf(attempt(1, AttemptResultType.FAIL), attempt(2, AttemptResultType.PASS, 60))
        )
        assertEquals(DailyRecordStatus.PASSED, r.status)
        assertEquals(60, r.creditedMinutes)
    }

    @Test
    fun voidedPass_isIgnored_backToFailed() {
        val r = DailyRecordResolver.resolve(
            listOf(
                attempt(1, AttemptResultType.FAIL),
                attempt(2, AttemptResultType.PASS, 60, AttemptState.VOIDED)
            )
        )
        assertEquals(DailyRecordStatus.FAILED_REPEAT, r.status)
        assertEquals(0, r.creditedMinutes)
    }

    @Test
    fun everyAttemptVoided_isPending() {
        val r = DailyRecordResolver.resolve(
            listOf(attempt(1, AttemptResultType.PASS, 60, AttemptState.VOIDED))
        )
        assertEquals(DailyRecordStatus.PENDING, r.status)
    }
}