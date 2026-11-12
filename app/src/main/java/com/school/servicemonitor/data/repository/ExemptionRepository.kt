package com.school.servicemonitor.data.repository

import androidx.room.withTransaction
import com.school.servicemonitor.data.AppDatabase
import com.school.servicemonitor.data.dao.ExemptionDao
import com.school.servicemonitor.data.dao.SanctionDao
import com.school.servicemonitor.data.dao.StudentDao
import com.school.servicemonitor.data.entity.ExemptionRow
import com.school.servicemonitor.data.entity.SanctionExemption
import com.school.servicemonitor.data.entity.SanctionStatus
import com.school.servicemonitor.data.entity.StudentStatus
import com.school.servicemonitor.domain.SanctionCalculator
import com.school.servicemonitor.ui.components.formatMinutes
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

sealed interface ExemptionOutcome {
    data object Saved : ExemptionOutcome
    data class Rejected(val message: String) : ExemptionOutcome
}

class ExemptionRepository(
    private val db: AppDatabase,
    private val exemptionDao: ExemptionDao,
    private val sanctionDao: SanctionDao,
    private val studentDao: StudentDao,
    private val allocationRepository: AllocationRepository
) {

    fun observeForStudent(studentId: Long): Flow<List<ExemptionRow>> =
        exemptionDao.observeForStudent(studentId)

    /**
     * Permanently cancels [minutes] of the REMAINING minutes of one sanction.
     * Needs a reason and an approver. Cannot exceed what is still owed.
     */
    suspend fun grant(
        studentId: Long,
        sanctionId: Long,
        minutes: Int,
        reason: String,
        approvedBy: String,
        approvalDate: LocalDate
    ): ExemptionOutcome = db.withTransaction {
        val cleanReason = reason.trim()
        val cleanApprover = approvedBy.trim()
        if (cleanReason.isEmpty()) return@withTransaction ExemptionOutcome.Rejected("A reason is required.")
        if (cleanApprover.isEmpty()) {
            return@withTransaction ExemptionOutcome.Rejected("Enter who approved the exemption.")
        }
        if (minutes <= 0) return@withTransaction ExemptionOutcome.Rejected("Enter how much to exempt.")
        if (approvalDate.isAfter(LocalDate.now())) {
            return@withTransaction ExemptionOutcome.Rejected("The approval date cannot be in the future.")
        }

        val student = studentDao.getById(studentId)
            ?: return@withTransaction ExemptionOutcome.Rejected("Student not found.")
        if (student.status != StudentStatus.ACTIVE) {
            return@withTransaction ExemptionOutcome.Rejected("Restore the student before granting an exemption.")
        }

        val row = sanctionDao.getRows(studentId).firstOrNull { it.sanction.id == sanctionId }
            ?: return@withTransaction ExemptionOutcome.Rejected("Sanction not found for this student.")
        if (row.sanction.status != SanctionStatus.ACTIVE && row.sanction.status != SanctionStatus.OVERDUE) {
            return@withTransaction ExemptionOutcome.Rejected("This sanction has nothing left to exempt.")
        }

        val remaining = SanctionCalculator.remainingMinutes(row)
        if (minutes > remaining) {
            return@withTransaction ExemptionOutcome.Rejected(
                "Only ${formatMinutes(remaining)} remains on this sanction."
            )
        }

        exemptionDao.insert(
            SanctionExemption(
                studentId = studentId,
                sanctionId = sanctionId,
                exemptedMinutes = minutes,
                reason = cleanReason,
                approvedBy = cleanApprover,
                approvalDate = approvalDate,
                createdAt = System.currentTimeMillis()
            )
        )
        allocationRepository.refreshStatuses(studentId)
        ExemptionOutcome.Saved
    }
}