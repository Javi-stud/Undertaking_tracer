package com.school.servicemonitor.data.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.school.servicemonitor.data.entity.SanctionHourAllocation
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Minutes credited to one student on one date. */
data class StudentCredit(
    @ColumnInfo(name = "student_id") val studentId: Long,
    @ColumnInfo(name = "minutes") val minutes: Int
)

@Dao
interface AllocationDao {

    @Insert
    suspend fun insertAll(allocations: List<SanctionHourAllocation>)

    // Minutes already credited to this student on this date (reversed rows do not count).
    @Query(
        """
        SELECT COALESCE(SUM(allocated_minutes), 0)
        FROM sanction_hour_allocations
        WHERE student_id = :studentId AND allocation_date = :date AND reversed_at IS NULL
        """
    )
    suspend fun creditedOn(studentId: Long, date: LocalDate): Int

    // Live list of who was credited on a date. The dashboard uses it for the Passed section.
    @Query(
        """
        SELECT student_id AS student_id, SUM(allocated_minutes) AS minutes
        FROM sanction_hour_allocations
        WHERE allocation_date = :date AND reversed_at IS NULL
        GROUP BY student_id
        """
    )
    fun observeCreditedByStudent(date: LocalDate): Flow<List<StudentCredit>>

    @Query("SELECT * FROM sanction_hour_allocations WHERE attempt_id = :attemptId AND reversed_at IS NULL")
    suspend fun activeForAttempt(attemptId: Long): List<SanctionHourAllocation>

    // Reversal never deletes: it stamps the time and the reason.
    @Query(
        """
        UPDATE sanction_hour_allocations
        SET reversed_at = :now, reversal_reason = :reason
        WHERE attempt_id = :attemptId AND reversed_at IS NULL
        """
    )
    suspend fun reverseForAttempt(attemptId: Long, now: Long, reason: String): Int

    @Query(
        """
        SELECT attempt_id FROM sanction_hour_allocations
        WHERE student_id = :studentId AND reversed_at IS NULL
        ORDER BY id DESC LIMIT 1
        """
    )
    suspend fun latestActiveAttemptId(studentId: Long): Long?
}