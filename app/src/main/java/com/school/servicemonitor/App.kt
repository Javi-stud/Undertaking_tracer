package com.school.servicemonitor

import android.app.Application
import com.school.servicemonitor.backup.RestoreApplier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // A confirmed restore is applied now, before the database is opened.
        RestoreApplier.applyPendingIfAny(this)

        container = AppContainer(this)

        // Create default settings the first time the app runs.
        appScope.launch { container.settingsRepository.ensureDefaults() }

        // Check sanction deadlines now, and again whenever sanctions or credits change.
        container.deadlineRepository.startMonitoring(appScope)
    }
}