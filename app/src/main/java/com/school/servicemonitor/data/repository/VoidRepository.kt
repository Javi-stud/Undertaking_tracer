package com.school.servicemonitor.data.repository

import androidx.room.withTransaction
import com.school.servicemonitor.data.AppDatabase
import com.school.servicemonitor.data.dao.AllocationDao
import com.school.servicemonitor.data.dao.AttemptDao
import com.school.servicemonitor.data.dao.DailyRecordDao
import com.school.servicemonitor.data.dao.DeadlineEventDao
import com.school.servicemonitor.data.entity.AttemptState
import com.school.servicemonitor.ui.components.formatMinutes

sealed interface VoidOutcome {
    data class Voided(val message: String) : VoidOutcome
    data class Rejected(val message: String) : VoidOutcome
}

class VoidRepository(
    private val db: AppDatabase,
    private val attemptDao: AttemptDao,
    private val recordDao: DailyRecordDao,
    private val allocationDao: AllocationDao,
    private val allocationRepository: AllocationRepository,
    private val eventDao: DeadlineEventDao,
    private val sync: DailyRecordSync,
    private val logger: ActivityLogger
) {

    /**
     * Voids one attempt. The attempt row is kept: its times, result and credited minutes
     * are untouched. Only the void fields are set. Its credits are reversed (stamped, not
     * deleted), sanctions reopen if needed, and the daily status is recalculated.
     * One transaction: all of it is saved, or none of it.
     */
    suspend fun voidAttempt(attemptId: Long, reason: String): VoidOutcome = db.withTransaction {
        val clean = reason.trim()
        if (clean.isEmpty()) {
            return@withTransaction VoidOutcome.Rejected("A reason is required to void an attempt.")
        }
        val attempt = attemptDao.getById(attemptId)
            ?: return@withTransaction VoidOutcome.Rejected("Attempt not found.")
        if (attempt.recordState == AttemptState.VOIDED) {
            return@withTransaction VoidOutcome.Rejected("This attempt was already voided.")
        }
        val record = recordDao.getById(attempt.dailyRecordId)
            ?: return@withTransaction VoidOutcome.Rejected("The daily record was not found.")

        // Remember which sanctions this attempt paid, before the credits are reversed.
        val paidSanctions = allocationDao.activeForAttempt(attemptId).map { it.sanctionId }.distinct()

        val now = System.currentTimeMillis()
        // If someone voided it a moment ago, this throws and everything rolls back.
        check(attemptDao.markVoided(attemptId, clean, now) == 1) { "The attempt was already voided." }

        val reversedRows = allocationRepository.reverseAttempt(attemptId, "Attempt voided: $clean")
        sync.refresh(record)

        // A confirmed repeat is never removed automatically. Tell the operator instead.
        val repeatKept = paidSanctions.any { eventDao.countConfirmed(it) > 0 }

        logger.log(
            entityType = "ATTEMPT",
            entityId = attemptId,
            action = "ATTEMPT_VOIDED",
            studentId = attempt.studentId,
            details = "Attempt #${attempt.attemptNo} on ${record.serviceDate} " +
                    "(${attempt.result}, ${formatMinutes(attempt.creditedMinutes)} credited) voided. Reason: $clean"
        )

        val message = buildString {
            append("Attempt #${attempt.attemptNo} voided.")
            if (reversedRows > 0) {
                append(" ${formatMinutes(attempt.creditedMinutes)} returned to the student's sanctions.")
            }
            if (repeatKept) {
                append(" Note: a sanction this attempt paid already has a confirmed repeat. ")
                append("The repeat was kept, so check the student's sanctions.")
            }
        }
        VoidOutcome.Voided(message)
    }
}