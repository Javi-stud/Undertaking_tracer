package com.school.servicemonitor.data.repository

import com.school.servicemonitor.data.dao.AttemptDao
import com.school.servicemonitor.data.dao.DailyRecordDao
import com.school.servicemonitor.data.dao.ExcuseDao
import com.school.servicemonitor.data.entity.DailyServiceRecord
import com.school.servicemonitor.domain.DailyRecordResolver
import java.time.LocalDate

class DailyRecordSync(
    private val recordDao: DailyRecordDao,
    private val attemptDao: AttemptDao,
    private val excuseDao: ExcuseDao
) {

    /** The daily record for this student and date. Created if it does not exist yet. */
    suspend fun ensureRecord(studentId: Long, date: LocalDate): DailyServiceRecord {
        recordDao.find(studentId, date)?.let { return it }
        val now = System.currentTimeMillis()
        val record = DailyServiceRecord(
            studentId = studentId,
            serviceDate = date,
            createdAt = now,
            updatedAt = now
        )
        return record.copy(id = recordDao.insert(record))
    }

    /** Recomputes the cached status from the attempts and the active excuse. */
    suspend fun refresh(record: DailyServiceRecord) {
        val resolved = DailyRecordResolver.resolve(
            attempts = attemptDao.getForRecord(record.id),
            excused = excuseDao.findActive(record.studentId, record.serviceDate) != null
        )
        recordDao.updateSummary(
            record.id,
            resolved.status,
            resolved.creditedMinutes,
            System.currentTimeMillis()
        )
    }
}