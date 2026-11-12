package com.school.servicemonitor.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.school.servicemonitor.data.entity.AttemptHistoryRow
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

    // Every attempt on one date, including voided ones, for the History tab.
    @Query(
        """
        SELECT a.*,
               d.service_date AS service_date,
               s.student_number AS student_number,
               s.first_name AS first_name,
               s.middle_name AS middle_name,
               s.last_name AS last_name
        FROM service_attempts a
        INNER JOIN daily_service_records d ON d.id = a.daily_record_id
        INNER JOIN students s ON s.id = a.student_id
        WHERE d.service_date = :date
        ORDER BY s.last_name COLLATE NOCASE ASC, s.first_name COLLATE NOCASE ASC, a.attempt_no ASC
        """
    )
    fun observeHistoryForDate(date: LocalDate): Flow<List<AttemptHistoryRow>>

    // Every attempt of one student, newest date first.
    @Query(
        """
        SELECT a.*,
               d.service_date AS service_date,
               s.student_number AS student_number,
               s.first_name AS first_name,
               s.middle_name AS middle_name,
               s.last_name AS last_name
        FROM service_attempts a
        INNER JOIN daily_service_records d ON d.id = a.daily_record_id
        INNER JOIN students s ON s.id = a.student_id
        WHERE a.student_id = :studentId
        ORDER BY d.service_date DESC, a.attempt_no ASC
        """
    )
    fun observeHistoryForStudent(studentId: Long): Flow<List<AttemptHistoryRow>>

    @Query("SELECT * FROM service_attempts WHERE id = :id")
    suspend fun getById(id: Long): ServiceAttempt?

    // Voiding never deletes or edits the original data: only these three columns change.
    @Query(
        """
        UPDATE service_attempts
        SET record_state = 'VOIDED', void_reason = :reason, voided_at = :now
        WHERE id = :id AND record_state = 'RECORDED'
        """
    )
    suspend fun markVoided(id: Long, reason: String, now: Long): Int
}