package com.school.servicemonitor.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.school.servicemonitor.data.repository.AttemptRepository
import com.school.servicemonitor.data.repository.SanctionRepository
import com.school.servicemonitor.data.repository.SettingsRepository
import com.school.servicemonitor.data.repository.StudentRepository
import com.school.servicemonitor.domain.DailyStatus
import com.school.servicemonitor.domain.DashboardBuilder
import com.school.servicemonitor.domain.DashboardEntry
import com.school.servicemonitor.domain.DayRecordInfo
import com.school.servicemonitor.domain.ScheduleCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class DashboardUiState(
    val date: LocalDate,
    val isServiceDay: Boolean,
    val entries: List<DashboardEntry>,
    val loading: Boolean = false
) {
    fun entriesFor(status: DailyStatus): List<DashboardEntry> =
        entries.filter { it.status == status }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    studentRepository: StudentRepository,
    sanctionRepository: SanctionRepository,
    attemptRepository: AttemptRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    private val _tab = MutableStateFlow(DailyStatus.PENDING)
    val tab: StateFlow<DailyStatus> = _tab.asStateFlow()

    // Rebuilds when the date changes OR any student, sanction, daily record or setting changes.
    val state: StateFlow<DashboardUiState> = _date
        .flatMapLatest { date ->
            combine(
                studentRepository.observeActive(),
                sanctionRepository.observeAllForActiveStudents(),
                attemptRepository.observeDayRecords(date),
                settingsRepository.observeAll()
            ) { students, sanctionRows, dayRows, settings ->
                val serviceDays =
                    ScheduleCalculator.parseServiceDays(settings[SettingsRepository.KEY_SERVICE_DAYS])
                val isServiceDay = ScheduleCalculator.isServiceDay(date, serviceDays)
                DashboardUiState(
                    date = date,
                    isServiceDay = isServiceDay,
                    entries = DashboardBuilder.build(
                        students = students,
                        sanctionRows = sanctionRows,
                        dayRecords = dayRows.associate {
                            it.record.studentId to DayRecordInfo(
                                status = it.record.status,
                                creditedMinutes = it.record.creditedMinutes,
                                attemptCount = it.attemptCount
                            )
                        },
                        date = date,
                        isServiceDay = isServiceDay
                    )
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DashboardUiState(LocalDate.now(), isServiceDay = true, entries = emptyList(), loading = true)
        )

    fun onDateChange(date: LocalDate) { _date.value = date }
    fun previousDay() { _date.value = _date.value.minusDays(1) }
    fun nextDay() { _date.value = _date.value.plusDays(1) }
    fun goToToday() { _date.value = LocalDate.now() }
    fun onTabChange(status: DailyStatus) { _tab.value = status }
}