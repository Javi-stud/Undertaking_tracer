package com.school.servicemonitor.ui.students

import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.school.servicemonitor.data.entity.Student
import com.school.servicemonitor.data.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class FormErrors(
    val studentNumber: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val gradeLevel: String? = null,
    val section: String? = null
) {
    val hasAny: Boolean
        get() = listOf(studentNumber, firstName, lastName, gradeLevel, section).any { it != null }
}

data class StudentFormState(
    val studentNumber: String = "",
    val firstName: String = "",
    val middleName: String = "",
    val lastName: String = "",
    val gradeLevel: String = "",
    val section: String = "",
    val isSsg: Boolean = false,
    val errors: FormErrors = FormErrors(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val notFound: Boolean = false
)

/** studentId = 0 means "register a new student"; anything else means "edit". */
class StudentFormViewModel(
    private val repository: StudentRepository,
    private val studentId: Long
) : ViewModel() {

    val isEditing: Boolean = studentId != 0L

    private val _state = MutableStateFlow(StudentFormState(isLoading = isEditing))
    val state: StateFlow<StudentFormState> = _state.asStateFlow()

    private var original: Student? = null

    init {
        if (isEditing) {
            viewModelScope.launch {
                val student = repository.get(studentId)
                if (student == null) {
                    _state.update { it.copy(isLoading = false, notFound = true) }
                } else {
                    original = student
                    _state.update {
                        it.copy(
                            studentNumber = student.studentNumber,
                            firstName = student.firstName,
                            middleName = student.middleName.orEmpty(),
                            lastName = student.lastName,
                            gradeLevel = student.gradeLevel,
                            section = student.section,
                            isSsg = student.isSsg,
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    fun onStudentNumberChange(v: String) =
        _state.update { it.copy(studentNumber = v, errors = it.errors.copy(studentNumber = null)) }

    fun onFirstNameChange(v: String) =
        _state.update { it.copy(firstName = v, errors = it.errors.copy(firstName = null)) }

    fun onMiddleNameChange(v: String) =
        _state.update { it.copy(middleName = v) }

    fun onLastNameChange(v: String) =
        _state.update { it.copy(lastName = v, errors = it.errors.copy(lastName = null)) }

    fun onGradeLevelChange(v: String) =
        _state.update { it.copy(gradeLevel = v, errors = it.errors.copy(gradeLevel = null)) }

    fun onSectionChange(v: String) =
        _state.update { it.copy(section = v, errors = it.errors.copy(section = null)) }

    fun onSsgChange(v: Boolean) =
        _state.update { it.copy(isSsg = v) }

    fun save() {
        val current = _state.value
        if (current.isSaving || current.isLoading) return

        val number = current.studentNumber.trim()
        val first = current.firstName.trim()
        val middle = current.middleName.trim().ifEmpty { null }
        val last = current.lastName.trim()
        val grade = current.gradeLevel.trim()
        val section = current.section.trim()

        val errors = FormErrors(
            studentNumber = if (number.isEmpty()) "Student number is required" else null,
            firstName = if (first.isEmpty()) "First name is required" else null,
            lastName = if (last.isEmpty()) "Last name is required" else null,
            gradeLevel = if (grade.isEmpty()) "Grade level is required" else null,
            section = if (section.isEmpty()) "Section is required" else null
        )
        if (errors.hasAny) {
            _state.update { it.copy(errors = errors) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                if (repository.isNumberTaken(number, studentId)) {
                    _state.update {
                        it.copy(
                            isSaving = false,
                            errors = FormErrors(studentNumber = "This student number is already used")
                        )
                    }
                    return@launch
                }

                val now = System.currentTimeMillis()
                val base = original
                if (base == null) {
                    repository.add(
                        Student(
                            studentNumber = number,
                            firstName = first,
                            middleName = middle,
                            lastName = last,
                            gradeLevel = grade,
                            section = section,
                            isSsg = current.isSsg,
                            registrationDate = LocalDate.now(),
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                } else {
                    repository.update(
                        base.copy(
                            studentNumber = number,
                            firstName = first,
                            middleName = middle,
                            lastName = last,
                            gradeLevel = grade,
                            section = section,
                            isSsg = current.isSsg,
                            updatedAt = now
                        )
                    )
                }
                _state.update { it.copy(isSaving = false, saved = true) }
            } catch (e: SQLiteConstraintException) {
                // Safety net in case two saves race each other.
                _state.update {
                    it.copy(
                        isSaving = false,
                        errors = FormErrors(studentNumber = "This student number is already used")
                    )
                }
            }
        }
    }
}