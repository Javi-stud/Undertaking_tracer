package com.school.servicemonitor.ui.attempts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.school.servicemonitor.data.entity.AttemptResultType
import com.school.servicemonitor.data.entity.AttemptState
import com.school.servicemonitor.data.entity.ServiceAttempt
import com.school.servicemonitor.data.entity.Student
import com.school.servicemonitor.data.repository.AttemptOutcome
import com.school.servicemonitor.data.repository.AttemptRepository
import com.school.servicemonitor.data.repository.SanctionRepository
import com.school.servicemonitor.data.repository.SettingsRepository
import com.school.servicemonitor.data.repository.StudentRepository
import com.school.servicemonitor.domain.SanctionCalculator
import com.school.servicemonitor.domain.ScheduleCalculator
import com.school.servicemonitor.domain.TimeRangeInput
import com.school.servicemonitor.ui.components.formatMinutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AttemptUiState(
    val loading: Boolean = true,
    val student: Student? = null,
    val outstandingMinutes: Int = 0,
    val activeSanctionCount: Int = 0,
    val attempts: List<ServiceAttempt> = emptyList(),
    val time: TimeRangeInput = TimeRangeInput.default(),
    val failReason: String = "",
    val reasonError: String? = null,
    val error: String? = null,
    val notice: String? = null,
    val saving: Boolean = false,
    val requiredMinutes: Int = 60,
    val isServiceDay: Boolean = true
) {
    val passedAttempt: ServiceAttempt?
        get() = attempts.firstOrNull {
            it.result == AttemptResultType.PASS && it.recordState == AttemptState.RECORDED
        }
}

class AttemptViewModel(
    studentRepository: StudentRepository,
    sanctionRepository: SanctionRepository,
    private val attemptRepository: AttemptRepository,
    private val settingsRepository: SettingsRepository,
    private val studentId: Long,
    val date: LocalDate
) : ViewModel() {

    private data class Local(
        val time: TimeRangeInput = TimeRangeInput.default(),
        val failReason: String = "",
        val reasonError: String? = null,
        val error: String? = null,
        val notice: String? = null,
        val saving: Boolean = false,
        val requiredMinutes: Int = 60,
        val isServiceDay: Boolean = true
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<AttemptUiState> = combine(
        studentRepository.observe(studentId),
        sanctionRepository.observeForStudent(studentId),
        attemptRepository.observeAttempts(studentId, date),
        local
    ) { student, rows, attempts, l ->
        val summary = SanctionCalculator.summarize(rows)
        AttemptUiState(
            loading = false,
            student = student,
            outstandingMinutes = summary.outstandingMinutes,
            activeSanctionCount = summary.activeSanctionCount,
            attempts = attempts,
            time = l.time,
            failReason = l.failReason,
            reasonError = l.reasonError,
            error = l.error,
            notice = l.notice,
            saving = l.saving,
            requiredMinutes = l.requiredMinutes,
            isServiceDay = l.isServiceDay
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AttemptUiState())

    init {
        viewModelScope.launch {
            val required = settingsRepository.getInt(SettingsRepository.KEY_DAILY_CREDIT_MINUTES, 60)
            val days = ScheduleCalculator.parseServiceDays(
                settingsRepository.getString(SettingsRepository.KEY_SERVICE_DAYS)
            )
            local.update {
                it.copy(
                    requiredMinutes = required,
                    isServiceDay = ScheduleCalculator.isServiceDay(date, days)
                )
            }
        }
    }

    fun onTimeChange(value: TimeRangeInput) =
        local.update { it.copy(time = value, error = null, notice = null) }

    fun onReasonChange(value: String) =
        local.update { it.copy(failReason = value, reasonError = null, error = null) }

    fun pass() {
        val l = local.value
        if (l.saving) return
        local.update { it.copy(saving = true, error = null, notice = null, reasonError = null) }
        viewModelScope.launch {
            val outcome = safely {
                attemptRepository.recordPass(studentId, date, l.time.start, l.time.end)
            }
            local.update {
                when (outcome) {
                    is AttemptOutcome.Saved -> it.copy(
                        saving = false,
                        notice = "PASS saved. ${formatMinutes(outcome.creditedMinutes)} credited."
                    )
                    is AttemptOutcome.Rejected -> it.copy(saving = false, error = outcome.message)
                }
            }
        }
    }

    fun fail() {
        val l = local.value
        if (l.saving) return
        if (l.failReason.isBlank()) {
            local.update { it.copy(reasonError = "A reason is required for FAIL") }
            return
        }
        local.update { it.copy(saving = true, error = null, notice = null, reasonError = null) }
        viewModelScope.launch {
            val outcome = safely {
                attemptRepository.recordFail(studentId, date, l.time.start, l.time.end, l.failReason)
            }
            local.update {
                when (outcome) {
                    is AttemptOutcome.Saved -> it.copy(
                        saving = false,
                        failReason = "",
                        notice = "FAIL saved. The student must repeat cleaning today."
                    )
                    is AttemptOutcome.Rejected -> it.copy(saving = false, error = outcome.message)
                }
            }
        }
    }

    /** Turns an unexpected crash (for example a rolled-back transaction) into a visible message. */
    private suspend fun safely(block: suspend () -> AttemptOutcome): AttemptOutcome =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AttemptOutcome.Rejected("Could not save: ${e.message ?: "unknown error"}. Nothing was changed.")
        }
}