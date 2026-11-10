package com.school.servicemonitor.data.repository

import com.school.servicemonitor.data.dao.AppSettingDao
import com.school.servicemonitor.data.entity.AppSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dao: AppSettingDao) {

    companion object {
        const val KEY_DAILY_CREDIT_MINUTES = "daily_credit_minutes"
        const val KEY_SSG_MULTIPLIER = "ssg_multiplier"
        const val KEY_SERVICE_DAYS = "service_days"

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

    suspend fun setValue(key: String, value: String) = dao.upsert(AppSetting(key, value))

    suspend fun getString(key: String): String? = dao.getValue(key)
}