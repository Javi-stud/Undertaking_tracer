package com.school.servicemonitor.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.school.servicemonitor.data.entity.AppSetting
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingDao {

    // IGNORE means: if the key already exists, leave it alone.
    // We use this to create defaults without overwriting the operator's choices.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(settings: List<AppSetting>)

    // Insert, or update if the key already exists.
    @Upsert
    suspend fun upsert(setting: AppSetting)

    @Query("SELECT setting_value FROM app_settings WHERE setting_key = :key")
    suspend fun getValue(key: String): String?

    // A Flow re-emits automatically whenever the table changes.
    @Query("SELECT * FROM app_settings")
    fun observeAll(): Flow<List<AppSetting>>

    // Saves several settings in one step: all of them are written, or none.
    @Upsert
    suspend fun upsertAll(settings: List<AppSetting>)
}