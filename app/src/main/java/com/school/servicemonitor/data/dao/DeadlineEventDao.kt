package com.school.servicemonitor.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.school.servicemonitor.data.entity.DeadlineEventRow
import com.school.servicemonitor.data.entity.DeadlineRepeatEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface DeadlineEventDao {

    // IGNORE + the unique index = a second event for the same deadline is silently refused.
    // Returns the new row id, or -1 when the event already existed.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(event: DeadlineRepeatEvent): Long

    @Query("SELECT * FROM deadline_repeat_events WHERE id = :id")
    suspend fun getById(id: Long): DeadlineRepeatEvent?

    @Query(
        """
        UPDATE deadline_repeat_events
        SET status = 'CONFIRMED', proposed_sanction_id = :proposedId, confirmed_at = :now
        WHERE id = :id AND status = 'DETECTED'
        """
    )
    suspend fun markConfirmed(id: Long, proposedId: Long, now: Long): Int

    @Query(
        """
        UPDATE deadline_repeat_events
        SET status = 'DISMISSED', dismissed_at = :now, dismiss_reason = :reason
        WHERE id = :id AND status = 'DETECTED'
        """
    )
    suspend fun markDismissed(id: Long, reason: String, now: Long): Int

    // Events waiting for a decision, for active students only.
    @Query(
        """
        SELECT e.*,
               s.student_id AS student_id,
               st.student_number AS student_number,
               st.first_name AS first_name,
               st.middle_name AS middle_name,
               st.last_name AS last_name,
               v.name AS violation_name,
               s.assigned_minutes AS assigned_minutes,
               COALESCE((
                   SELECT SUM(a.allocated_minutes) FROM sanction_hour_allocations a
                   WHERE a.sanction_id = s.id AND a.reversed_at IS NULL
               ), 0) AS allocated_minutes,
               COALESCE((
                   SELECT SUM(x.exempted_minutes) FROM sanction_exemptions x
                   WHERE x.sanction_id = s.id
               ), 0) AS exempted_minutes
        FROM deadline_repeat_events e
        INNER JOIN sanctions s ON s.id = e.original_sanction_id
        INNER JOIN students st ON st.id = s.student_id
        INNER JOIN violations v ON v.id = s.violation_id
        WHERE e.status = 'DETECTED' AND st.status = 'ACTIVE'
        ORDER BY e.triggering_deadline ASC, e.id ASC
        """
    )
    fun observePending(): Flow<List<DeadlineEventRow>>

    // Every event of one student, any status, newest deadline first.
    @Query(
        """
        SELECT e.*,
               s.student_id AS student_id,
               st.student_number AS student_number,
               st.first_name AS first_name,
               st.middle_name AS middle_name,
               st.last_name AS last_name,
               v.name AS violation_name,
               s.assigned_minutes AS assigned_minutes,
               COALESCE((
                   SELECT SUM(a.allocated_minutes) FROM sanction_hour_allocations a
                   WHERE a.sanction_id = s.id AND a.reversed_at IS NULL
               ), 0) AS allocated_minutes,
               COALESCE((
                   SELECT SUM(x.exempted_minutes) FROM sanction_exemptions x
                   WHERE x.sanction_id = s.id
               ), 0) AS exempted_minutes
        FROM deadline_repeat_events e
        INNER JOIN sanctions s ON s.id = e.original_sanction_id
        INNER JOIN students st ON st.id = s.student_id
        INNER JOIN violations v ON v.id = s.violation_id
        WHERE s.student_id = :studentId
        ORDER BY e.triggering_deadline DESC, e.id DESC
        """
    )
    fun observeForStudent(studentId: Long): Flow<List<DeadlineEventRow>>

    @Query(
        """
        SELECT COUNT(*) FROM deadline_repeat_events
        WHERE original_sanction_id = :sanctionId AND status = 'CONFIRMED'
        """
    )
    suspend fun countConfirmed(sanctionId: Long): Int

    // Proposals whose sanction's deadline is no longer in the past (an excuse moved it).
    @Query(
        """
        SELECT e.* FROM deadline_repeat_events e
        INNER JOIN sanctions s ON s.id = e.original_sanction_id
        WHERE e.status = 'DETECTED' AND s.end_date >= :today
        """
    )
    suspend fun detectedNoLongerOverdue(today: java.time.LocalDate): List<DeadlineRepeatEvent>
}