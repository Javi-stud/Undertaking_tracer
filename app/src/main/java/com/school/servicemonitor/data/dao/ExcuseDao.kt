package com.school.servicemonitor.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.school.servicemonitor.data.entity.Excuse
import com.school.servicemonitor.data.entity.ExcuseHistoryRow
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ExcuseDao {

    @Insert
    suspend fun insert(excuse: Excuse): Long

    @Query("SELECT * FROM excuses WHERE id = :id")
    suspend fun getById(id: Long): Excuse?

    // The one active (not revoked) excuse for a student on a date, if any.
    @Query(
        """
        SELECT * FROM excuses
        WHERE student_id = :studentId AND excuse_date = :date AND revoked_at IS NULL
        LIMIT 1
        """
    )
    suspend fun findActive(studentId: Long, date: LocalDate): Excuse?

    // Revoking never deletes: it stamps the time and the reason.
    @Query("UPDATE excuses SET revoked_at = :now, revoke_reason = :reason WHERE id = :id AND revoked_at IS NULL")
    suspend fun revoke(id: Long, now: Long, reason: String): Int

    @Query("SELECT * FROM excuses WHERE excuse_date = :date AND revoked_at IS NULL")
    fun observeActiveForDate(date: LocalDate): Flow<List<Excuse>>

    @Query("SELECT * FROM excuses WHERE student_id = :studentId ORDER BY excuse_date DESC, id DESC")
    fun observeForStudent(studentId: Long): Flow<List<Excuse>>

    // Everything on one date, including revoked excuses, for the History tab.
    @Query(
        """
        SELECT e.*,
               s.student_number AS student_number,
               s.first_name AS first_name,
               s.middle_name AS middle_name,
               s.last_name AS last_name
        FROM excuses e
        INNER JOIN students s ON s.id = e.student_id
        WHERE e.excuse_date = :date
        ORDER BY s.last_name COLLATE NOCASE ASC, s.first_name COLLATE NOCASE ASC, e.id ASC
        """
    )
    fun observeHistoryForDate(date: LocalDate): Flow<List<ExcuseHistoryRow>>
}