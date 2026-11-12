package com.school.servicemonitor.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.school.servicemonitor.data.entity.ExcuseExtension

@Dao
interface ExcuseExtensionDao {

    @Insert
    suspend fun insert(extension: ExcuseExtension): Long

    @Query("SELECT * FROM excuse_extensions WHERE excuse_id = :excuseId AND reverted_at IS NULL")
    suspend fun activeForExcuse(excuseId: Long): List<ExcuseExtension>

    @Query("UPDATE excuse_extensions SET reverted_at = :now WHERE id = :id")
    suspend fun markReverted(id: Long, now: Long)
}