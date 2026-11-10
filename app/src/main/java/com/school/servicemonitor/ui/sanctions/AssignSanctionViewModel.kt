package com.school.servicemonitor.ui.sanctions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.school.servicemonitor.data.entity.Student
import com.school.servicemonitor.data.entity.Violation
import com.school.servicemonitor.data.repository.AssignResult
import com.school.servicemonitor.data.repository.SanctionRepository
import com.school.servicemonitor.data.repository.StudentRepository
import com.school.servicemonitor.data.repository.ViolationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AssignState(
    val loading: Boolean = true,
    val student: Student? = null,
    val violations: List<Violation> = emptyList(),
    val selectedViolation: Violation? = null,
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val multiplier: Int = 1,
    val error: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false
) {
    /** Preview of the final sanction, in minutes. */
    val previewMinutes: Int?
        get() = selectedViolation?.let { it.standardHours * multiplier * 60 }
}

class AssignSanctionViewModel(
    private val studentRepository: StudentRepository,
    private val violationRepository: ViolationRepository,
    private val sanctionRepository: SanctionRepository,
    private val studentId: Long
) : ViewModel() {

    private val _state = MutableStateFlow(AssignState())
    val state: StateFlow<AssignState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val student = studentRepository.get(studentId)
            val violations = violationRepository.getActive()
            val multiplier = student?.let { sanctionRepository.multiplierFor(it) } ?: 1
            _state.update {
                it.copy(
                    loading = false,
                    student = student,
                    violations = violations,
                    multiplier = multiplier
                )
            }
        }
    }

    fun onViolationSelected(v: Violation) =
        _state.update { it.copy(selectedViolation = v, error = null) }

    fun onStartDateChange(date: LocalDate) = _state.update {
        // If the old deadline is now before the new start date, make the operator pick again.
        val end = it.endDate?.takeIf { e -> !e.isBefore(date) }
        it.copy(startDate = date, endDate = end, error = null)
    }

    fun onEndDateChange(date: LocalDate) =
        _state.update { it.copy(endDate = date, error = null) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val violation = s.selectedViolation
        val end = s.endDate
        when {
            violation == null -> {
                _state.update { it.copy(error = "Choose a violation.") }
                return
            }
            end == null -> {
                _state.update { it.copy(error = "Select the deadline (end date).") }
                return
            }
        }

        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            when (val result = sanctionRepository.assign(studentId, violation!!.id, s.startDate, end!!)) {
                is AssignResult.Success -> _state.update { it.copy(saving = false, saved = true) }
                is AssignResult.Error -> _state.update { it.copy(saving = false, error = result.message) }
            }
        }
    }
}