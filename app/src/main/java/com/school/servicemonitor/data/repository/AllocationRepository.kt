package com.school.servicemonitor.data.repository

import androidx.room.withTransaction
import com.school.servicemonitor.data.AppDatabase
import com.school.servicemonitor.data.dao.AllocationDao
import com.school.servicemonitor.data.dao.SanctionDao
import com.school.servicemonitor.data.dao.StudentCredit
import com.school.servicemonitor.data.entity.SanctionHourAllocation
import com.school.servicemonitor.data.entity.SanctionStatus
import com.school.servicemonitor.domain.Allocation
import com.school.servicemonitor.domain.AllocationEngine
import com.school.servicemonitor.domain.toBalance
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class AllocationRepository(
    private val db: AppDatabase,
    private val sanctionDao: SanctionDao,
    private val allocationDao: AllocationDao,
    private val settings: SettingsRepository
) {

    fun observeCreditedByStudent(date: LocalDate): Flow<List<StudentCredit>> =
        allocationDao.observeCreditedByStudent(date)

    /**
     * Credits up to [requestedMinutes] to the student's sanctions, earliest deadline first.
     * Never credits more than the daily limit for [serviceDate].
     * Returns the allocations created (empty if nothing could be credited).
     */
    suspend fun creditMinutes(
        studentId: Long,
        attemptId: Long,
        serviceDate: LocalDate,
        requestedMinutes: Int
    ): List<Allocation> = db.withTransaction {
        val limit = settings.getInt(SettingsRepository.KEY_DAILY_CREDIT_MINUTES, 60)
        val already = allocationDao.creditedOn(studentId, serviceDate)
        val allowed = AllocationEngine.allowedToday(requestedMinutes, limit, already)
        if (allowed <= 0) return@withTransaction emptyList()

        val balances = sanctionDao.getRows(studentId).map { it.toBalance() }
        val allocations = AllocationEngine.allocate(balances, serviceDate, allowed)
        if (allocations.isEmpty()) return@withTransaction emptyList()

        val now = System.currentTimeMillis()
        allocationDao.insertAll(
            allocations.map {
                SanctionHourAllocation(
                    studentId = studentId,
                    sanctionId = it.sanctionId,
                    attemptId = attemptId,
                    allocatedMinutes = it.minutes,
                    allocationDate = serviceDate,
                    createdAt = now
                )
            }
        )
        refreshStatuses(studentId)
        allocations
    }

    /**
     * Reverses every credit that came from one attempt (used when an attempt is voided).
     * Rows are stamped, never deleted. Returns how many allocation rows were reversed.
     */
    suspend fun reverseAttempt(attemptId: Long, reason: String): Int = db.withTransaction {
        val affected = allocationDao.activeForAttempt(attemptId)
        if (affected.isEmpty()) return@withTransaction 0

        val count = allocationDao.reverseForAttempt(attemptId, System.currentTimeMillis(), reason)
        affected.map { it.studentId }.distinct().forEach { refreshStatuses(it) }
        count
    }

    suspend fun latestActiveAttemptId(studentId: Long): Long? =
        allocationDao.latestActiveAttemptId(studentId)

    /** Completed or exempted when nothing remains; reopened when something is owed again. */
    suspend fun refreshStatuses(studentId: Long) {
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        sanctionDao.getRows(studentId).forEach { row ->
            val s = row.sanction
            val newStatus = AllocationEngine.resolveStatus(
                s.status, s.assignedMinutes, row.allocatedMinutes, s.endDate, today, row.exemptedMinutes
            )
            if (newStatus != s.status) {
                sanctionDao.updateStatus(
                    s.id,
                    newStatus,
                    if (newStatus == SanctionStatus.COMPLETED) now else null
                )
            }
        }
    }
}