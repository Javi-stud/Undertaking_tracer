package com.school.servicemonitor.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.school.servicemonitor.data.entity.ServiceAttempt
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface AttemptDao {

    @Insert
    suspend fun insert(attempt: ServiceAttempt): Long

    @Query("SELECT COALESCE(MAX(attempt_no), 0) + 1 FROM service_attempts WHERE daily_record_id = :recordId")
    suspend fun nextAttemptNo(recordId: Long): Int

    @Query("SELECT * FROM service_attempts WHERE daily_record_id = :recordId ORDER BY attempt_no ASC")
    suspend fun getForRecord(recordId: Long): List<ServiceAttempt>

    @Query(
        """
        SELECT a.* FROM service_attempts a
        INNER JOIN daily_service_records d ON d.id = a.daily_record_id
        WHERE d.student_id = :studentId AND d.service_date = :date
        ORDER BY a.attempt_no ASC
        """
    )
    fun observeForStudentDate(studentId: Long, date: LocalDate): Flow<List<ServiceAttempt>>
}