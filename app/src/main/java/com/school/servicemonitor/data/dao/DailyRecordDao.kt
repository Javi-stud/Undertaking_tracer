package com.school.servicemonitor.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.school.servicemonitor.data.entity.DailyRecordRow
import com.school.servicemonitor.data.entity.DailyRecordStatus
import com.school.servicemonitor.data.entity.DailyServiceRecord
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface DailyRecordDao {

    @Insert
    suspend fun insert(record: DailyServiceRecord): Long

    @Query("SELECT * FROM daily_service_records WHERE student_id = :studentId AND service_date = :date")
    suspend fun find(studentId: Long, date: LocalDate): DailyServiceRecord?

    @Query(
        """
        UPDATE daily_service_records
        SET status = :status, credited_minutes = :credited, updated_at = :now
        WHERE id = :id
        """
    )
    suspend fun updateSummary(id: Long, status: DailyRecordStatus, credited: Int, now: Long)

    @Query(
        """
        SELECT d.*,
               (SELECT COUNT(*) FROM service_attempts a
                WHERE a.daily_record_id = d.id AND a.record_state = 'RECORDED') AS attempt_count
        FROM daily_service_records d
        WHERE d.service_date = :date
        """
    )
    fun observeForDate(date: LocalDate): Flow<List<DailyRecordRow>>

    @Query("SELECT * FROM daily_service_records WHERE id = :id")
    suspend fun getById(id: Long): DailyServiceRecord?
}