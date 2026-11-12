package com.school.servicemonitor.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.school.servicemonitor.data.entity.ActivityLog
import com.school.servicemonitor.data.entity.ActivityLogRow
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityLogDao {

    @Insert
    suspend fun insert(log: ActivityLog): Long

    @Query(
        """
        SELECT l.*,
               s.first_name AS first_name,
               s.middle_name AS middle_name,
               s.last_name AS last_name
        FROM activity_logs l
        LEFT JOIN students s ON s.id = l.student_id
        ORDER BY l.created_at DESC, l.id DESC
        LIMIT :limit
        """
    )
    fun observeRecent(limit: Int): Flow<List<ActivityLogRow>>
}