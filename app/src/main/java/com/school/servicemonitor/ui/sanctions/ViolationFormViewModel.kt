package com.school.servicemonitor.ui.sanctions

import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.school.servicemonitor.data.entity.Violation
import com.school.servicemonitor.data.repository.ViolationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ViolationFormState(
    val name: String = "",
    val description: String = "",
    val hours: String = "",
    val isActive: Boolean = true,
    val nameError: String? = null,
    val hoursError: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val notFound: Boolean = false
)

/** violationId = 0 means "new violation". */
class ViolationFormViewModel(
    private val repository: ViolationRepository,
    private val violationId: Long
) : ViewModel() {

    val isEditing: Boolean = violationId != 0L

    private val _state = MutableStateFlow(ViolationFormState(isLoading = isEditing))
    val state: StateFlow<ViolationFormState> = _state.asStateFlow()

    private var original: Violation? = null

    init {
        if (isEditing) {
            viewModelScope.launch {
                val v = repository.get(violationId)
                if (v == null) {
                    _state.update { it.copy(isLoading = false, notFound = true) }
                } else {
                    original = v
                    _state.update {
                        it.copy(
                            name = v.name,
                            description = v.description,
                            hours = v.standardHours.toString(),
                            isActive = v.isActive,
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(v: String) = _state.update { it.copy(name = v, nameError = null) }
    fun onDescriptionChange(v: String) = _state.update { it.copy(description = v) }
    fun onHoursChange(v: String) =
        _state.update { it.copy(hours = v.filter(Char::isDigit).take(3), hoursError = null) }
    fun onActiveChange(v: Boolean) = _state.update { it.copy(isActive = v) }

    fun save() {
        val current = _state.value
        if (current.isSaving || current.isLoading) return

        val name = current.name.trim()
        val description = current.description.trim()
        val hours = current.hours.toIntOrNull()

        val nameError = if (name.isEmpty()) "Violation name is required" else null
        val hoursError = if (hours == null || hours < 1) "Enter at least 1 hour" else null
        if (nameError != null || hoursError != null) {
            _state.update { it.copy(nameError = nameError, hoursError = hoursError) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                if (repository.isNameTaken(name, violationId)) {
                    _state.update {
                        it.copy(isSaving = false, nameError = "A violation with this name already exists")
                    }
                    return@launch
                }

                val base = original
                if (base == null) {
                    repository.add(
                        Violation(
                            name = name,
                            description = description,
                            standardHours = hours!!,
                            isActive = current.isActive,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    repository.update(
                        base.copy(
                            name = name,
                            description = description,
                            standardHours = hours!!,
                            isActive = current.isActive
                        )
                    )
                }
                _state.update { it.copy(isSaving = false, saved = true) }
            } catch (e: SQLiteConstraintException) {
                _state.update {
                    it.copy(isSaving = false, nameError = "A violation with this name already exists")
                }
            }
        }
    }
}