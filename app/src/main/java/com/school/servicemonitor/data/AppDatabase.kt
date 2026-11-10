package com.school.servicemonitor.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.school.servicemonitor.data.dao.AllocationDao
import com.school.servicemonitor.data.dao.AppSettingDao
import com.school.servicemonitor.data.dao.AttemptDao
import com.school.servicemonitor.data.dao.DailyRecordDao
import com.school.servicemonitor.data.dao.SanctionDao
import com.school.servicemonitor.data.dao.StudentDao
import com.school.servicemonitor.data.dao.ViolationDao
import com.school.servicemonitor.data.entity.AppSetting
import com.school.servicemonitor.data.entity.DailyServiceRecord
import com.school.servicemonitor.data.entity.Sanction
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
        SanctionHourAllocation::class
    ],
    version = 5,
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