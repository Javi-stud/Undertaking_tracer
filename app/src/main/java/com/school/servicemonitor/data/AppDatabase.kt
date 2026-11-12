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
    version = 9,
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
        const val DB_NAME = "service_monitor.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                // DEVELOPMENT ONLY: wipes and rebuilds the database when the schema changes.
                // Will be replaced with real migrations before real data is used.
                .fallbackToDestructiveMigration()
                .build()
    }
}