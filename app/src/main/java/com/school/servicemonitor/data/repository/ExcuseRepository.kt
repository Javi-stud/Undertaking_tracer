package com.school.servicemonitor.data.repository

import androidx.room.withTransaction
import com.school.servicemonitor.data.AppDatabase
import com.school.servicemonitor.data.dao.AttemptDao
import com.school.servicemonitor.data.dao.DailyRecordDao
import com.school.servicemonitor.data.dao.ExcuseDao
import com.school.servicemonitor.data.dao.SanctionDao
import com.school.servicemonitor.data.dao.StudentDao
import com.school.servicemonitor.data.entity.AttemptResultType
import com.school.servicemonitor.data.entity.AttemptState
import com.school.servicemonitor.data.entity.Excuse
import com.school.servicemonitor.data.entity.ExcuseHistoryRow
import com.school.servicemonitor.data.entity.StudentStatus
import com.school.servicemonitor.domain.AllocationEngine
import com.school.servicemonitor.domain.ScheduleCalculator
import com.school.servicemonitor.domain.toBalance
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

sealed interface ExcuseOutcome {
    data object Saved : ExcuseOutcome
    data class Rejected(val message: String) : ExcuseOutcome
}

data class ExcuseItemResult(val studentId: Long, val outcome: ExcuseOutcome)

class ExcuseRepository(
    private val db: AppDatabase,
    private val excuseDao: ExcuseDao,
    private val attemptDao: AttemptDao,
    private val recordDao: DailyRecordDao,
    private val studentDao: StudentDao,
    private val sanctionDao: SanctionDao,
    private val sync: DailyRecordSync,
    private val settings: SettingsRepository
) {

    fun observeActiveForDate(date: LocalDate): Flow<List<Excuse>> =
        excuseDao.observeActiveForDate(date)

    fun observeForStudent(studentId: Long): Flow<List<Excuse>> =
        excuseDao.observeForStudent(studentId)

    fun observeHistoryForDate(date: LocalDate): Flow<List<ExcuseHistoryRow>> =
        excuseDao.observeHistoryForDate(date)

    /**
     * Daily excuse for one or many students (one student = a single excuse).
     * A separate excuse row is saved per student. No hours are deducted and no sanction is cancelled.
     * A student who cannot be excused is reported and nothing is written for them.
     * If something unexpected throws, the whole batch is rolled back.
     */
    suspend fun excuseStudents(
        studentIds: List<Long>,
        date: LocalDate,
        reason: String
    ): List<ExcuseItemResult> = db.withTransaction {
        val clean = reason.trim()
        if (clean.isEmpty()) {
            return@withTransaction studentIds.map {
                ExcuseItemResult(it, ExcuseOutcome.Rejected("A reason is required."))
            }
        }
        val dayError = dayError(date)
        val batchId = if (studentIds.size > 1) UUID.randomUUID().toString() else null

        studentIds.map { id ->
            ExcuseItemResult(
                id,
                if (dayError != null) ExcuseOutcome.Rejected(dayError)
                else excuseOne(id, date, clean, batchId)
            )
        }
    }

    /** Revokes an excuse (soft). The row stays, stamped with the time and the reason. */
    suspend fun revoke(excuseId: Long, reason: String): ExcuseOutcome = db.withTransaction {
        val clean = reason.trim()
        if (clean.isEmpty()) {
            return@withTransaction ExcuseOutcome.Rejected("A reason is required to revoke an excuse.")
        }
        val excuse = excuseDao.getById(excuseId)
            ?: return@withTransaction ExcuseOutcome.Rejected("Excuse not found.")
        if (excuse.revokedAt != null) {
            return@withTransaction ExcuseOutcome.Rejected("This excuse was already revoked.")
        }

        excuseDao.revoke(excuseId, System.currentTimeMillis(), clean)
        sync.refresh(sync.ensureRecord(excuse.studentId, excuse.excuseDate))
        ExcuseOutcome.Saved
    }

    private suspend fun dayError(date: LocalDate): String? {
        if (date.isAfter(LocalDate.now())) return "Future dates cannot be excused yet."
        val days = ScheduleCalculator.parseServiceDays(
            settings.getString(SettingsRepository.KEY_SERVICE_DAYS)
        )
        if (!ScheduleCalculator.isServiceDay(date, days)) {
            return "This is not a service day, so no excuse is needed."
        }
        return null
    }

    private suspend fun excuseOne(
        studentId: Long,
        date: LocalDate,
        reason: String,
        batchId: String?
    ): ExcuseOutcome {
        val student = studentDao.getById(studentId)
            ?: return ExcuseOutcome.Rejected("Student not found.")
        if (student.status != StudentStatus.ACTIVE) {
            return ExcuseOutcome.Rejected("Archived students cannot be excused.")
        }

        val existing = recordDao.find(studentId, date)
        if (existing != null) {
            val passed = attemptDao.getForRecord(existing.id).any {
                it.result == AttemptResultType.PASS && it.recordState == AttemptState.RECORDED
            }
            if (passed) {
                return ExcuseOutcome.Rejected("Already passed on this date. Void that attempt first if it was a mistake.")
            }
        }
        if (excuseDao.findActive(studentId, date) != null) {
            return ExcuseOutcome.Rejected("Already excused on this date.")
        }

        val balances = sanctionDao.getRows(studentId).map { it.toBalance() }
        if (AllocationEngine.allocate(balances, date, 1).isEmpty()) {
            return ExcuseOutcome.Rejected("Not required to clean on this date, so there is nothing to excuse.")
        }

        excuseDao.insert(
            Excuse(
                studentId = studentId,
                excuseDate = date,
                reason = reason,
                bulkBatchId = batchId,
                createdAt = System.currentTimeMillis()
            )
        )
        sync.refresh(sync.ensureRecord(studentId, date))
        return ExcuseOutcome.Saved
    }
}