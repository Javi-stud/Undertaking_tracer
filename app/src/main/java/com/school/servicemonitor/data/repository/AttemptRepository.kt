package com.school.servicemonitor.data.repository

import androidx.room.withTransaction
import com.school.servicemonitor.data.AppDatabase
import com.school.servicemonitor.data.dao.AttemptDao
import com.school.servicemonitor.data.dao.DailyRecordDao
import com.school.servicemonitor.data.dao.ExcuseDao
import com.school.servicemonitor.data.dao.SanctionDao
import com.school.servicemonitor.data.dao.StudentDao
import com.school.servicemonitor.data.entity.AttemptHistoryRow
import com.school.servicemonitor.data.entity.AttemptResultType
import com.school.servicemonitor.data.entity.AttemptState
import com.school.servicemonitor.data.entity.DailyRecordRow
import com.school.servicemonitor.data.entity.ServiceAttempt
import com.school.servicemonitor.data.entity.StudentStatus
import com.school.servicemonitor.domain.AllocationEngine
import com.school.servicemonitor.domain.BulkPassItem
import com.school.servicemonitor.domain.PassCheck
import com.school.servicemonitor.domain.ScheduleCalculator
import com.school.servicemonitor.domain.TimeRangeCalculator
import com.school.servicemonitor.domain.TimeRangeCheck
import com.school.servicemonitor.domain.toBalance
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

sealed interface AttemptOutcome {
    data class Saved(val attemptId: Long, val creditedMinutes: Int) : AttemptOutcome
    data class Rejected(val message: String) : AttemptOutcome
}

class AttemptRepository(
    private val db: AppDatabase,
    private val recordDao: DailyRecordDao,
    private val attemptDao: AttemptDao,
    private val studentDao: StudentDao,
    private val sanctionDao: SanctionDao,
    private val excuseDao: ExcuseDao,
    private val allocationRepository: AllocationRepository,
    private val sync: DailyRecordSync,
    private val settings: SettingsRepository
) {

    fun observeDayRecords(date: LocalDate): Flow<List<DailyRecordRow>> =
        recordDao.observeForDate(date)

    fun observeAttempts(studentId: Long, date: LocalDate): Flow<List<ServiceAttempt>> =
        attemptDao.observeForStudentDate(studentId, date)

    fun observeHistoryForDate(date: LocalDate): Flow<List<AttemptHistoryRow>> =
        attemptDao.observeHistoryForDate(date)

    fun observeHistoryForStudent(studentId: Long): Flow<List<AttemptHistoryRow>> =
        attemptDao.observeHistoryForStudent(studentId)

    /**
     * PASS: needs a valid range of at least the daily credit (default 60 minutes).
     * Credits at most the daily limit, however long the session was.
     */
    suspend fun recordPass(
        studentId: Long,
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
        bulkBatchId: String? = null
    ): AttemptOutcome = db.withTransaction {
        val limit = settings.getInt(SettingsRepository.KEY_DAILY_CREDIT_MINUTES, 60)

        checkCommon(studentId, date)?.let { return@withTransaction AttemptOutcome.Rejected(it) }

        when (val pass = TimeRangeCalculator.passCheck(start, end, limit)) {
            PassCheck.InvalidRange ->
                return@withTransaction AttemptOutcome.Rejected("The end time must be later than the start time.")
            is PassCheck.TooShort ->
                return@withTransaction AttemptOutcome.Rejected(
                    "PASS is not allowed: at least ${pass.requiredMinutes} minutes must be recorded " +
                            "(currently ${pass.recordedMinutes})."
                )
            PassCheck.Allowed -> Unit
        }
        val duration = (TimeRangeCalculator.check(start, end) as TimeRangeCheck.Valid).durationMinutes

        // What will actually be credited (may be less than the limit if little is owed).
        val balances = sanctionDao.getRows(studentId).map { it.toBalance() }
        val planned = AllocationEngine.allocate(balances, date, limit).sumOf { it.minutes }

        val record = sync.ensureRecord(studentId, date)
        val now = System.currentTimeMillis()
        val attemptId = attemptDao.insert(
            ServiceAttempt(
                dailyRecordId = record.id,
                studentId = studentId,
                attemptNo = attemptDao.nextAttemptNo(record.id),
                startTime = start,
                endTime = end,
                durationMinutes = duration,
                result = AttemptResultType.PASS,
                creditedMinutes = planned,
                bulkBatchId = bulkBatchId,
                createdAt = now
            )
        )

        val allocations = allocationRepository.creditMinutes(studentId, attemptId, date, limit)
        // If the two calculations ever disagree, throwing here rolls everything back.
        check(allocations.sumOf { it.minutes } == planned) { "Credit mismatch. Nothing was saved." }

        sync.refresh(record)
        AttemptOutcome.Saved(attemptId, planned)
    }

    /** FAIL: needs a valid range and a reason. Credits nothing. */
    suspend fun recordFail(
        studentId: Long,
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
        reason: String
    ): AttemptOutcome = db.withTransaction {
        val cleanReason = reason.trim()
        if (cleanReason.isEmpty()) {
            return@withTransaction AttemptOutcome.Rejected("A reason is required when the cleaning fails.")
        }
        checkCommon(studentId, date)?.let { return@withTransaction AttemptOutcome.Rejected(it) }

        val range = TimeRangeCalculator.check(start, end)
        if (range !is TimeRangeCheck.Valid) {
            return@withTransaction AttemptOutcome.Rejected("The end time must be later than the start time.")
        }

        val record = sync.ensureRecord(studentId, date)
        val attemptId = attemptDao.insert(
            ServiceAttempt(
                dailyRecordId = record.id,
                studentId = studentId,
                attemptNo = attemptDao.nextAttemptNo(record.id),
                startTime = start,
                endTime = end,
                durationMinutes = range.durationMinutes,
                result = AttemptResultType.FAIL,
                creditedMinutes = 0,
                failReason = cleanReason,
                createdAt = System.currentTimeMillis()
            )
        )
        sync.refresh(record)
        AttemptOutcome.Saved(attemptId, 0)
    }

    /**
     * Bulk PASS: one separate attempt per student, all sharing one batch id.
     * Every student goes through the same checks as a single PASS. A student who is rejected
     * writes nothing and is reported in the result. If something unexpected throws,
     * the whole batch is rolled back.
     */
    suspend fun recordBulkPass(items: List<BulkPassItem>, date: LocalDate): List<BulkItemResult> =
        db.withTransaction {
            val batchId = UUID.randomUUID().toString()
            items.map { item ->
                BulkItemResult(
                    studentId = item.studentId,
                    outcome = recordPass(item.studentId, date, item.start, item.end, batchId)
                )
            }
        }

    /** Rules shared by PASS and FAIL. Returns an error message, or null when everything is fine. */
    private suspend fun checkCommon(studentId: Long, date: LocalDate): String? {
        val student = studentDao.getById(studentId) ?: return "Student not found."
        if (student.status != StudentStatus.ACTIVE) {
            return "Archived students cannot be recorded. Restore the student first."
        }
        if (date.isAfter(LocalDate.now())) {
            return "Future dates are view-only. Attempts cannot be recorded yet."
        }
        val days = ScheduleCalculator.parseServiceDays(
            settings.getString(SettingsRepository.KEY_SERVICE_DAYS)
        )
        if (!ScheduleCalculator.isServiceDay(date, days)) {
            return "This is not a service day. Use bulk Excused for holidays."
        }
        if (excuseDao.findActive(studentId, date) != null) {
            return "This student is excused on this date. Revoke the excuse first to record cleaning."
        }

        val existing = recordDao.find(studentId, date)
        if (existing != null) {
            val alreadyPassed = attemptDao.getForRecord(existing.id).any {
                it.result == AttemptResultType.PASS && it.recordState == AttemptState.RECORDED
            }
            if (alreadyPassed) {
                return "This student already passed on this date. Void that attempt first to correct it."
            }
        }

        val balances = sanctionDao.getRows(studentId).map { it.toBalance() }
        if (AllocationEngine.allocate(balances, date, 1).isEmpty()) {
            return "This student has no outstanding hours that are required on this date."
        }
        return null
    }
}

data class BulkItemResult(val studentId: Long, val outcome: AttemptOutcome)