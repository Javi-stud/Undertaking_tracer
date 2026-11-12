package com.school.servicemonitor.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.school.servicemonitor.data.entity.ActivityLogRow
import com.school.servicemonitor.data.repository.ActivityLogger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

const val ACTIVITY_LOG_LIMIT = 300

class ActivityLogViewModel(logger: ActivityLogger) : ViewModel() {

    // null = still loading
    val rows: StateFlow<List<ActivityLogRow>?> = logger.observeRecent(ACTIVITY_LOG_LIMIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}