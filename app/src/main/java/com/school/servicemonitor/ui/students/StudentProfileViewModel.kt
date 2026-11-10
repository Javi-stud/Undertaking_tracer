package com.school.servicemonitor.ui.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.school.servicemonitor.data.entity.SanctionRow
import com.school.servicemonitor.data.entity.Student
import com.school.servicemonitor.data.repository.DeleteResult
import com.school.servicemonitor.data.repository.SanctionRepository
import com.school.servicemonitor.data.repository.StudentRepository
import com.school.servicemonitor.domain.HourSummary
import com.school.servicemonitor.domain.SanctionCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProfileState {
    data object Loading : ProfileState
    data object NotFound : ProfileState
    data class Found(val student: Student) : ProfileState
}

class StudentProfileViewModel(
    private val repository: StudentRepository,
    sanctionRepository: SanctionRepository,
    private val studentId: Long
) : ViewModel() {

    val state: StateFlow<ProfileState> = repository.observe(studentId)
        .map { student ->
            if (student == null) ProfileState.NotFound else ProfileState.Found(student)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileState.Loading)

    private val sanctionFlow = sanctionRepository.observeForStudent(studentId)

    val sanctions: StateFlow<List<SanctionRow>> = sanctionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary: StateFlow<HourSummary> = sanctionFlow
        .map { SanctionCalculator.summarize(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SanctionCalculator.summarize(emptyList())
        )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun consumeMessage() { _message.value = null }

    fun archive() {
        viewModelScope.launch {
            repository.archive(studentId)
            _message.value = "Student archived."
        }
    }

    fun restore() {
        viewModelScope.launch {
            repository.restore(studentId)
            _message.value = "Student restored."
        }
    }

    fun deletePermanently() {
        viewModelScope.launch {
            when (repository.deletePermanently(studentId)) {
                DeleteResult.DELETED -> _deleted.value = true
                DeleteResult.NOT_ARCHIVED ->
                    _message.value = "Archive the student first before deleting."
                DeleteResult.HAS_RECORDS ->
                    _message.value = "This student has sanction records and cannot be deleted. Keep them archived instead."
                DeleteResult.NOT_FOUND ->
                    _message.value = "Student not found."
            }
        }
    }
}