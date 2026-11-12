package com.school.servicemonitor.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.school.servicemonitor.data.entity.SanctionHistory

@Dao
interface SanctionHistoryDao {

    @Insert
    suspend fun insert(entry: SanctionHistory): Long

    @Query("SELECT * FROM sanction_history WHERE sanction_id = :sanctionId ORDER BY id ASC")
    suspend fun getForSanction(sanctionId: Long): List<SanctionHistory>
}