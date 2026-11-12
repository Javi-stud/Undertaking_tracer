package com.school.servicemonitor.data.repository

import com.school.servicemonitor.data.dao.ActivityLogDao
import com.school.servicemonitor.data.entity.ActivityLog
import com.school.servicemonitor.data.entity.ActivityLogRow
import kotlinx.coroutines.flow.Flow

class ActivityLogger(private val dao: ActivityLogDao) {

    /** Call this inside the same transaction as the action, so the log and the action stay together. */
    suspend fun log(
        entityType: String,
        entityId: Long,
        action: String,
        studentId: Long?,
        details: String
    ) {
        dao.insert(
            ActivityLog(
                entityType = entityType,
                entityId = entityId,
                studentId = studentId,
                action = action,
                details = details,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    fun observeRecent(limit: Int): Flow<List<ActivityLogRow>> = dao.observeRecent(limit)
}