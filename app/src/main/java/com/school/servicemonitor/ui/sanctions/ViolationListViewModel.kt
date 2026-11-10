package com.school.servicemonitor.ui.sanctions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.school.servicemonitor.data.entity.Violation
import com.school.servicemonitor.data.repository.ViolationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ViolationListViewModel(repository: ViolationRepository) : ViewModel() {

    // null = still loading
    val violations: StateFlow<List<Violation>?> =
        repository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}