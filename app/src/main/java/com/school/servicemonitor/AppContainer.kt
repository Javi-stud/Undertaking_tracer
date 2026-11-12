package com.school.servicemonitor

import com.school.servicemonitor.data.repository.ReportRepository
import android.content.Context
import com.school.servicemonitor.data.AppDatabase
import com.school.servicemonitor.data.repository.ActivityLogger
import com.school.servicemonitor.data.repository.AllocationRepository
import com.school.servicemonitor.data.repository.AttemptRepository
import com.school.servicemonitor.data.repository.DailyRecordSync
import com.school.servicemonitor.data.repository.DeadlineRepository
import com.school.servicemonitor.data.repository.ExcuseRepository
import com.school.servicemonitor.data.repository.ExemptionRepository
import com.school.servicemonitor.data.repository.SanctionRepository
import com.school.servicemonitor.data.repository.SettingsRepository
import com.school.servicemonitor.data.repository.StudentRepository
import com.school.servicemonitor.data.repository.ViolationRepository
import com.school.servicemonitor.data.repository.VoidRepository

class AppContainer(context: Context) {

    val database: AppDatabase = AppDatabase.build(context.applicationContext)

    val settingsRepository: SettingsRepository =
        SettingsRepository(database.appSettingDao())

    val activityLogger: ActivityLogger =
        ActivityLogger(database.activityLogDao())

    val studentRepository: StudentRepository =
        StudentRepository(database.studentDao(), database.sanctionDao())

    val violationRepository: ViolationRepository =
        ViolationRepository(database.violationDao())

    val sanctionRepository: SanctionRepository =
        SanctionRepository(
            sanctionDao = database.sanctionDao(),
            studentDao = database.studentDao(),
            violationDao = database.violationDao(),
            settings = settingsRepository
        )

    val allocationRepository: AllocationRepository =
        AllocationRepository(
            db = database,
            sanctionDao = database.sanctionDao(),
            allocationDao = database.allocationDao(),
            settings = settingsRepository
        )

    private val dailyRecordSync: DailyRecordSync =
        DailyRecordSync(
            recordDao = database.dailyRecordDao(),
            attemptDao = database.attemptDao(),
            excuseDao = database.excuseDao()
        )

    val attemptRepository: AttemptRepository =
        AttemptRepository(
            db = database,
            recordDao = database.dailyRecordDao(),
            attemptDao = database.attemptDao(),
            studentDao = database.studentDao(),
            sanctionDao = database.sanctionDao(),
            excuseDao = database.excuseDao(),
            allocationRepository = allocationRepository,
            sync = dailyRecordSync,
            settings = settingsRepository,
            logger = activityLogger
        )

    val voidRepository: VoidRepository =
        VoidRepository(
            db = database,
            attemptDao = database.attemptDao(),
            recordDao = database.dailyRecordDao(),
            allocationDao = database.allocationDao(),
            allocationRepository = allocationRepository,
            eventDao = database.deadlineEventDao(),
            sync = dailyRecordSync,
            logger = activityLogger
        )

    val excuseRepository: ExcuseRepository =
        ExcuseRepository(
            db = database,
            excuseDao = database.excuseDao(),
            extensionDao = database.excuseExtensionDao(),
            historyDao = database.sanctionHistoryDao(),
            attemptDao = database.attemptDao(),
            recordDao = database.dailyRecordDao(),
            studentDao = database.studentDao(),
            sanctionDao = database.sanctionDao(),
            sync = dailyRecordSync,
            settings = settingsRepository,
            logger = activityLogger
        )

    val exemptionRepository: ExemptionRepository =
        ExemptionRepository(
            db = database,
            exemptionDao = database.exemptionDao(),
            sanctionDao = database.sanctionDao(),
            studentDao = database.studentDao(),
            allocationRepository = allocationRepository,
            logger = activityLogger
        )

    val deadlineRepository: DeadlineRepository =
        DeadlineRepository(
            db = database,
            eventDao = database.deadlineEventDao(),
            historyDao = database.sanctionHistoryDao(),
            sanctionDao = database.sanctionDao(),
            studentDao = database.studentDao(),
            logger = activityLogger
        )
    val reportRepository: ReportRepository =
        ReportRepository(
            students = studentRepository,
            sanctions = sanctionRepository,
            attempts = attemptRepository,
            excuses = excuseRepository,
            exemptions = exemptionRepository,
            deadlines = deadlineRepository,
            settings = settingsRepository
        )
}

/** Shortcut so any screen can write: LocalContext.current.appContainer() */
fun Context.appContainer(): AppContainer = (applicationContext as App).container