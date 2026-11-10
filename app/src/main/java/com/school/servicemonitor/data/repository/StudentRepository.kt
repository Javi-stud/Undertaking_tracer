package com.school.servicemonitor.data.repository

import com.school.servicemonitor.data.dao.SanctionDao
import com.school.servicemonitor.data.dao.StudentDao
import com.school.servicemonitor.data.entity.Student
import com.school.servicemonitor.data.entity.StudentStatus
import kotlinx.coroutines.flow.Flow

enum class DeleteResult { DELETED, NOT_ARCHIVED, HAS_RECORDS, NOT_FOUND }

class StudentRepository(
    private val dao: StudentDao,
    private val sanctionDao: SanctionDao
) {
    fun observeActive(): Flow<List<Student>> = dao.observeActive()
    fun search(query: String, includeArchived: Boolean): Flow<List<Student>> =
        dao.search(query.trim(), includeArchived)

    fun observe(id: Long): Flow<Student?> = dao.observeById(id)

    suspend fun get(id: Long): Student? = dao.getById(id)

    suspend fun isNumberTaken(number: String, excludeId: Long): Boolean =
        dao.countByNumber(number, excludeId) > 0

    suspend fun add(student: Student): Long = dao.insert(student)

    suspend fun update(student: Student) = dao.update(student)

    suspend fun archive(id: Long) =
        dao.setStatus(id, StudentStatus.ARCHIVED, System.currentTimeMillis())

    suspend fun restore(id: Long) =
        dao.setStatus(id, StudentStatus.ACTIVE, System.currentTimeMillis())

    /** Permanent delete is only allowed for archived students with no sanction records. */
    suspend fun deletePermanently(id: Long): DeleteResult {
        val student = dao.getById(id) ?: return DeleteResult.NOT_FOUND
        if (student.status != StudentStatus.ARCHIVED) return DeleteResult.NOT_ARCHIVED
        if (sanctionDao.countByStudent(id) > 0) return DeleteResult.HAS_RECORDS
        dao.deleteById(id)
        return DeleteResult.DELETED
    }
}