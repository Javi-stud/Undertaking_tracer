package com.school.servicemonitor.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.school.servicemonitor.data.entity.ExemptionRow
import com.school.servicemonitor.data.entity.SanctionExemption
import kotlinx.coroutines.flow.Flow

@Dao
interface ExemptionDao {

    @Insert
    suspend fun insert(exemption: SanctionExemption): Long

    @Query(
        """
        SELECT e.*, v.name AS violation_name
        FROM sanction_exemptions e
        INNER JOIN sanctions s ON s.id = e.sanction_id
        INNER JOIN violations v ON v.id = s.violation_id
        WHERE e.student_id = :studentId
        ORDER BY e.created_at DESC, e.id DESC
        """
    )
    fun observeForStudent(studentId: Long): Flow<List<ExemptionRow>>
}