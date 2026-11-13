package com.school.servicemonitor.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.school.servicemonitor.data.dao.ActivityLogDao
import com.school.servicemonitor.data.dao.AllocationDao
import com.school.servicemonitor.data.dao.AppSettingDao
import com.school.servicemonitor.data.dao.AttemptDao
import com.school.servicemonitor.data.dao.DailyRecordDao
import com.school.servicemonitor.data.dao.DeadlineEventDao
import com.school.servicemonitor.data.dao.ExcuseDao
import com.school.servicemonitor.data.dao.ExcuseExtensionDao
import com.school.servicemonitor.data.dao.ExemptionDao
import com.school.servicemonitor.data.dao.SanctionDao
import com.school.servicemonitor.data.dao.SanctionHistoryDao
import com.school.servicemonitor.data.dao.StudentDao
import com.school.servicemonitor.data.dao.ViolationDao
import com.school.servicemonitor.data.entity.ActivityLog
import com.school.servicemonitor.data.entity.AppSetting
import com.school.servicemonitor.data.entity.DailyServiceRecord
import com.school.servicemonitor.data.entity.DeadlineRepeatEvent
import com.school.servicemonitor.data.entity.Excuse
import com.school.servicemonitor.data.entity.ExcuseExtension
import com.school.servicemonitor.data.entity.Sanction
import com.school.servicemonitor.data.entity.SanctionExemption
import com.school.servicemonitor.data.entity.SanctionHistory
import com.school.servicemonitor.data.entity.SanctionHourAllocation
import com.school.servicemonitor.data.entity.ServiceAttempt
import com.school.servicemonitor.data.entity.Student
import com.school.servicemonitor.data.entity.Violation

@Database(
    entities = [
        AppSetting::class,
        Student::class,
        Violation::class,
        Sanction::class,
        DailyServiceRecord::class,
        ServiceAttempt::class,
        SanctionHourAllocation::class,
        Excuse::class,
        SanctionExemption::class,
        DeadlineRepeatEvent::class,
        SanctionHistory::class,
        ExcuseExtension::class,
        ActivityLog::class
    ],
    version = AppDatabase.VERSION,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appSettingDao(): AppSettingDao
    abstract fun studentDao(): StudentDao
    abstract fun violationDao(): ViolationDao
    abstract fun sanctionDao(): SanctionDao
    abstract fun allocationDao(): AllocationDao
    abstract fun dailyRecordDao(): DailyRecordDao
    abstract fun attemptDao(): AttemptDao
    abstract fun excuseDao(): ExcuseDao
    abstract fun exemptionDao(): ExemptionDao
    abstract fun deadlineEventDao(): DeadlineEventDao
    abstract fun sanctionHistoryDao(): SanctionHistoryDao
    abstract fun excuseExtensionDao(): ExcuseExtensionDao
    abstract fun activityLogDao(): ActivityLogDao

    companion object {
        /** Raise this by one for every schema change, and add a Migration in Migrations.kt. */
        const val VERSION = 10
        const val DB_NAME = "service_monitor.db"

        /** The shared setup, including every real migration. */
        fun builder(context: Context, name: String = DB_NAME): Builder<AppDatabase> =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(*Migrations.ALL)

        fun build(context: Context): AppDatabase =
            builder(context)
                // Only databases older than version 9 are rebuilt. They were development
                // versions that never held real data. Version 9 and newer are NEVER wiped.
                .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6, 7, 8)
                .build()
    }
}
