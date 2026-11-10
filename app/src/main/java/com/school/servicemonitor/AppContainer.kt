package com.school.servicemonitor

import android.content.Context
import com.school.servicemonitor.data.AppDatabase
import com.school.servicemonitor.data.repository.AllocationRepository
import com.school.servicemonitor.data.repository.AttemptRepository
import com.school.servicemonitor.data.repository.SanctionRepository
import com.school.servicemonitor.data.repository.SettingsRepository
import com.school.servicemonitor.data.repository.StudentRepository
import com.school.servicemonitor.data.repository.ViolationRepository

class AppContainer(context: Context) {

    val database: AppDatabase = AppDatabase.build(context.applicationContext)

    val settingsRepository: SettingsRepository =
        SettingsRepository(database.appSettingDao())

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

    val attemptRepository: AttemptRepository =
        AttemptRepository(
            db = database,
            recordDao = database.dailyRecordDao(),
            attemptDao = database.attemptDao(),
            studentDao = database.studentDao(),
            sanctionDao = database.sanctionDao(),
            allocationRepository = allocationRepository,
            settings = settingsRepository
        )
}

/** Shortcut so any screen can write: LocalContext.current.appContainer() */
fun Context.appContainer(): AppContainer = (applicationContext as App).container