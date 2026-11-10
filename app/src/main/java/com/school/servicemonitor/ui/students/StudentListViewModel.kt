package com.school.servicemonitor.ui.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.school.servicemonitor.data.entity.Student
import com.school.servicemonitor.data.repository.StudentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class StudentListViewModel(private val repository: StudentRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _includeArchived = MutableStateFlow(false)
    val includeArchived: StateFlow<Boolean> = _includeArchived.asStateFlow()

    // null = still loading, so the screen doesn't flash "no students" at startup.
    val students: StateFlow<List<Student>?> =
        combine(_query, _includeArchived) { q, archived -> q to archived }
            .flatMapLatest { (q, archived) -> repository.search(q, archived) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onQueryChange(value: String) { _query.value = value }
    fun onIncludeArchivedChange(value: Boolean) { _includeArchived.value = value }
}