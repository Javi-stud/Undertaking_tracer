package com.school.servicemonitor.data.repository

import com.school.servicemonitor.data.dao.AppSettingDao
import com.school.servicemonitor.data.entity.AppSetting
import com.school.servicemonitor.domain.SettingsValidator
import com.school.servicemonitor.domain.SettingsValues
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dao: AppSettingDao) {

    companion object {
        const val KEY_DAILY_CREDIT_MINUTES = "daily_credit_minutes"
        const val KEY_SSG_MULTIPLIER = "ssg_multiplier"
        const val KEY_SERVICE_DAYS = "service_days"
        const val KEY_LAST_BACKUP_AT = "last_backup_at"

        val DEFAULTS = listOf(
            AppSetting(KEY_DAILY_CREDIT_MINUTES, "60"),
            AppSetting(KEY_SSG_MULTIPLIER, "2"),
            AppSetting(KEY_SERVICE_DAYS, "MON,TUE,WED,THU,FRI")
        )
    }

    /** Safe to call on every app start: existing values are never overwritten. */
    suspend fun ensureDefaults() = dao.insertIfAbsent(DEFAULTS)

    fun observeAll(): Flow<Map<String, String>> =
        dao.observeAll().map { list -> list.associate { it.key to it.value } }

    suspend fun getInt(key: String, default: Int): Int =
        dao.getValue(key)?.toIntOrNull() ?: default

    suspend fun getString(key: String): String? = dao.getValue(key)

    suspend fun setValue(key: String, value: String) = dao.upsert(AppSetting(key, value))

    /** Saves the three service rules together. Sanctions already assigned are not touched. */
    suspend fun saveRules(values: SettingsValues) = dao.upsertAll(
        listOf(
            AppSetting(KEY_DAILY_CREDIT_MINUTES, values.dailyCreditMinutes.toString()),
            AppSetting(KEY_SSG_MULTIPLIER, values.ssgMultiplier.toString()),
            AppSetting(KEY_SERVICE_DAYS, SettingsValidator.encodeDays(values.serviceDays))
        )
    )
}