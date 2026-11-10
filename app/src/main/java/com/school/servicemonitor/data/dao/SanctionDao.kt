package com.school.servicemonitor.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.school.servicemonitor.data.entity.Sanction
import com.school.servicemonitor.data.entity.SanctionRow
import com.school.servicemonitor.data.entity.SanctionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SanctionDao {

    @Insert
    suspend fun insert(sanction: Sanction): Long

    // Earliest deadline first, then oldest assigned: the same order the allocation engine uses.
    // allocated_minutes counts only allocations that have not been reversed (voided).
    @Query(
        """
        SELECT s.*,
               v.name AS violation_name,
               COALESCE((
                   SELECT SUM(a.allocated_minutes)
                   FROM sanction_hour_allocations a
                   WHERE a.sanction_id = s.id AND a.reversed_at IS NULL
               ), 0) AS allocated_minutes
        FROM sanctions s
        INNER JOIN violations v ON v.id = s.violation_id
        WHERE s.student_id = :studentId
        ORDER BY s.end_date ASC, s.assigned_date ASC, s.id ASC
        """
    )
    fun observeForStudent(studentId: Long): Flow<List<SanctionRow>>

    // Same query, but a one-time read (used inside transactions).
    @Query(
        """
        SELECT s.*,
               v.name AS violation_name,
               COALESCE((
                   SELECT SUM(a.allocated_minutes)
                   FROM sanction_hour_allocations a
                   WHERE a.sanction_id = s.id AND a.reversed_at IS NULL
               ), 0) AS allocated_minutes
        FROM sanctions s
        INNER JOIN violations v ON v.id = s.violation_id
        WHERE s.student_id = :studentId
        ORDER BY s.end_date ASC, s.assigned_date ASC, s.id ASC
        """
    )
    suspend fun getRows(studentId: Long): List<SanctionRow>

    @Query("UPDATE sanctions SET status = :status, completed_at = :completedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: SanctionStatus, completedAt: Long?)

    @Query("SELECT COUNT(*) FROM sanctions WHERE student_id = :studentId")
    suspend fun countByStudent(studentId: Long): Int

    // Every sanction of every active student, for the dashboard.
    @Query(
        """
        SELECT s.*,
               v.name AS violation_name,
               COALESCE((
                   SELECT SUM(a.allocated_minutes)
                   FROM sanction_hour_allocations a
                   WHERE a.sanction_id = s.id AND a.reversed_at IS NULL
               ), 0) AS allocated_minutes
        FROM sanctions s
        INNER JOIN violations v ON v.id = s.violation_id
        INNER JOIN students st ON st.id = s.student_id
        WHERE st.status = 'ACTIVE'
        ORDER BY s.end_date ASC, s.assigned_date ASC, s.id ASC
        """
    )
    fun observeAllForActiveStudents(): Flow<List<SanctionRow>>
}