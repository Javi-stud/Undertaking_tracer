package com.school.servicemonitor.data.repository

import androidx.room.withTransaction
import com.school.servicemonitor.data.AppDatabase
import com.school.servicemonitor.data.dao.AttemptDao
import com.school.servicemonitor.data.dao.DailyRecordDao
import com.school.servicemonitor.data.dao.ExcuseDao
import com.school.servicemonitor.data.dao.ExcuseExtensionDao
import com.school.servicemonitor.data.dao.SanctionDao
import com.school.servicemonitor.data.dao.SanctionHistoryDao
import com.school.servicemonitor.data.dao.StudentDao
import com.school.servicemonitor.data.entity.AttemptResultType
import com.school.servicemonitor.data.entity.AttemptState
import com.school.servicemonitor.data.entity.Excuse
import com.school.servicemonitor.data.entity.ExcuseExtension
import com.school.servicemonitor.data.entity.ExcuseHistoryRow
import com.school.servicemonitor.data.entity.SanctionHistory
import com.school.servicemonitor.data.entity.SanctionHistoryType
import com.school.servicemonitor.data.entity.SanctionStatus
import com.school.servicemonitor.data.entity.StudentStatus
import com.school.servicemonitor.domain.AllocationEngine
import com.school.servicemonitor.domain.ExtensionPlanner
import com.school.servicemonitor.domain.ScheduleCalculator
import com.school.servicemonitor.domain.toBalance
import com.school.servicemonitor.ui.components.display
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

sealed interface ExcuseOutcome {
    data object Saved : ExcuseOutcome
    data class Rejected(val message: String) : ExcuseOutcome
}

/** [extendedCount] = how many sanctions had their deadline moved by this excuse. */
data class ExcuseItemResult(
    val studentId: Long,
    val outcome: ExcuseOutcome,
    val extendedCount: Int = 0
)

class ExcuseRepository(
    private val db: AppDatabase,
    private val excuseDao: ExcuseDao,
    private val extensionDao: ExcuseExtensionDao,
    private val historyDao: SanctionHistoryDao,
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
     * Daily excuse for one or many students. No hours are deducted and no sanction is cancelled,
     * but each qualifying sanction's deadline moves one school day later (never a weekend day).
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
            if (dayError != null) ExcuseItemResult(id, ExcuseOutcome.Rejected(dayError))
            else excuseOne(id, date, clean, batchId)
        }
    }

    /** Revokes an excuse (soft) and undoes the deadline extensions it caused. */
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

        val now = System.currentTimeMillis()
        excuseDao.revoke(excuseId, now, clean)
        revertExtensions(excuseId, excuse.excuseDate, now)
        sync.refresh(sync.ensureRecord(excuse.studentId, excuse.excuseDate))
        ExcuseOutcome.Saved
    }

    private suspend fun serviceDays(): Set<DayOfWeek> =
        ScheduleCalculator.parseServiceDays(settings.getString(SettingsRepository.KEY_SERVICE_DAYS))

    private suspend fun dayError(date: LocalDate): String? {
        if (date.isAfter(LocalDate.now())) return "Future dates cannot be excused yet."
        if (!ScheduleCalculator.isServiceDay(date, serviceDays())) {
            return "This is not a service day, so no excuse is needed."
        }
        return null
    }

    private suspend fun excuseOne(
        studentId: Long,
        date: LocalDate,
        reason: String,
        batchId: String?
    ): ExcuseItemResult {
        fun reject(message: String) = ExcuseItemResult(studentId, ExcuseOutcome.Rejected(message))

        val student = studentDao.getById(studentId) ?: return reject("Student not found.")
        if (student.status != StudentStatus.ACTIVE) return reject("Archived students cannot be excused.")

        val existing = recordDao.find(studentId, date)
        if (existing != null) {
            val passed = attemptDao.getForRecord(existing.id).any {
                it.result == AttemptResultType.PASS && it.recordState == AttemptState.RECORDED
            }
            if (passed) {
                return reject("Already passed on this date. Void that attempt first if it was a mistake.")
            }
        }
        if (excuseDao.findActive(studentId, date) != null) return reject("Already excused on this date.")

        val balances = sanctionDao.getRows(studentId).map { it.toBalance() }
        if (AllocationEngine.allocate(balances, date, 1).isEmpty()) {
            return reject("Not required to clean on this date, so there is nothing to excuse.")
        }

        val now = System.currentTimeMillis()
        val excuseId = excuseDao.insert(
            Excuse(
                studentId = studentId,
                excuseDate = date,
                reason = reason,
                bulkBatchId = batchId,
                createdAt = now
            )
        )
        val extended = extendDeadlines(studentId, excuseId, date, now)
        sync.refresh(sync.ensureRecord(studentId, date))
        return ExcuseItemResult(studentId, ExcuseOutcome.Saved, extended)
    }

    /** Moves each qualifying sanction's deadline one school day later and records it. */
    private suspend fun extendDeadlines(studentId: Long, excuseId: Long, date: LocalDate, now: Long): Int {
        val shifts = ExtensionPlanner.plan(
            rows = sanctionDao.getRows(studentId),
            excuseDate = date,
            today = LocalDate.now(),
            serviceDays = serviceDays()
        )
        for (shift in shifts) {
            sanctionDao.updateEndDate(shift.sanctionId, shift.newEnd)
            extensionDao.insert(
                ExcuseExtension(
                    excuseId = excuseId,
                    sanctionId = shift.sanctionId,
                    oldEndDate = shift.oldEnd,
                    newEndDate = shift.newEnd,
                    createdAt = now
                )
            )
            historyDao.insert(
                SanctionHistory(
                    sanctionId = shift.sanctionId,
                    eventType = SanctionHistoryType.DEADLINE_EXTENDED,
                    details = "Deadline moved from ${shift.oldEnd.display()} to ${shift.newEnd.display()} " +
                            "because of an excuse on ${date.display()}.",
                    createdAt = now
                )
            )
        }
        return shifts.size
    }

    /** Undoes the extensions of a revoked excuse. Sanctions that are no longer ACTIVE are left alone. */
    private suspend fun revertExtensions(excuseId: Long, date: LocalDate, now: Long) {
        val days = serviceDays()
        for (ext in extensionDao.activeForExcuse(excuseId)) {
            extensionDao.markReverted(ext.id, now)
            val sanction = sanctionDao.getById(ext.sanctionId) ?: continue
            if (sanction.status != SanctionStatus.ACTIVE) continue

            val restored = ExtensionPlanner.revertedEnd(
                current = sanction.endDate,
                oldEnd = ext.oldEndDate,
                newEnd = ext.newEndDate,
                start = sanction.startDate,
                serviceDays = days
            )
            sanctionDao.updateEndDate(sanction.id, restored)
            historyDao.insert(
                SanctionHistory(
                    sanctionId = sanction.id,
                    eventType = SanctionHistoryType.DEADLINE_EXTENSION_REVERTED,
                    details = "Excuse of ${date.display()} was revoked. " +
                            "Deadline moved from ${sanction.endDate.display()} back to ${restored.display()}.",
                    createdAt = now
                )
            )
        }
    }
}