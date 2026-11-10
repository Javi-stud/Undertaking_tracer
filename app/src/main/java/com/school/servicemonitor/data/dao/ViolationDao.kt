package com.school.servicemonitor.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.school.servicemonitor.data.entity.Violation
import kotlinx.coroutines.flow.Flow

@Dao
interface ViolationDao {

    @Insert
    suspend fun insert(violation: Violation): Long

    @Update
    suspend fun update(violation: Violation)

    @Query("SELECT * FROM violations ORDER BY is_active DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Violation>>

    @Query("SELECT * FROM violations WHERE is_active = 1 ORDER BY name COLLATE NOCASE ASC")
    suspend fun getActive(): List<Violation>

    @Query("SELECT * FROM violations WHERE id = :id")
    suspend fun getById(id: Long): Violation?

    @Query("SELECT COUNT(*) FROM violations WHERE name = :name COLLATE NOCASE AND id != :excludeId")
    suspend fun countByName(name: String, excludeId: Long): Int
}