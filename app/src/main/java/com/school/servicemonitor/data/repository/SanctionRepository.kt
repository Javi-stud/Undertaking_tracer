package com.school.servicemonitor.data.repository

import com.school.servicemonitor.data.dao.SanctionDao
import com.school.servicemonitor.data.dao.StudentDao
import com.school.servicemonitor.data.dao.ViolationDao
import com.school.servicemonitor.data.entity.Sanction
import com.school.servicemonitor.data.entity.SanctionRow
import com.school.servicemonitor.data.entity.Student
import com.school.servicemonitor.data.entity.StudentStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

sealed interface AssignResult {
    data class Success(val sanctionId: Long) : AssignResult
    data class Error(val message: String) : AssignResult
}

class SanctionRepository(
    private val sanctionDao: SanctionDao,
    private val studentDao: StudentDao,
    private val violationDao: ViolationDao,
    private val settings: SettingsRepository
) {

    fun observeAllForActiveStudents(): Flow<List<SanctionRow>> =
        sanctionDao.observeAllForActiveStudents()

    fun observeForStudent(studentId: Long): Flow<List<SanctionRow>> =
        sanctionDao.observeForStudent(studentId)

    /** 1 for regular students, the SSG multiplier from settings (default 2) for SSG members. */
    suspend fun multiplierFor(student: Student): Int =
        if (student.isSsg) {
            settings.getInt(SettingsRepository.KEY_SSG_MULTIPLIER, 2).coerceAtLeast(1)
        } else 1

    suspend fun assign(
        studentId: Long,
        violationId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): AssignResult {
        val student = studentDao.getById(studentId)
            ?: return AssignResult.Error("Student not found.")
        if (student.status != StudentStatus.ACTIVE) {
            return AssignResult.Error("Archived students cannot receive new sanctions. Restore the student first.")
        }
        val violation = violationDao.getById(violationId)
            ?: return AssignResult.Error("Violation not found.")
        if (!violation.isActive) {
            return AssignResult.Error("This violation is inactive.")
        }
        if (endDate.isBefore(startDate)) {
            return AssignResult.Error("The deadline cannot be before the start date.")
        }

        val multiplier = multiplierFor(student)
        val now = System.currentTimeMillis()

        val id = sanctionDao.insert(
            Sanction(
                studentId = studentId,
                violationId = violationId,
                baseHours = violation.standardHours,
                multiplierApplied = multiplier,
                assignedMinutes = violation.standardHours * multiplier * 60,
                assignedDate = LocalDate.now(),
                startDate = startDate,
                endDate = endDate,
                createdAt = now
            )
        )
        return AssignResult.Success(id)
    }
}