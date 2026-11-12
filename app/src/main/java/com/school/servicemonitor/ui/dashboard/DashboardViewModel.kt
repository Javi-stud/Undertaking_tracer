package com.school.servicemonitor.ui.dashboard

import com.school.servicemonitor.data.repository.DeadlineRepository
import kotlinx.coroutines.flow.map
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.school.servicemonitor.data.entity.Excuse
import com.school.servicemonitor.data.entity.fullName
import com.school.servicemonitor.data.repository.AttemptOutcome
import com.school.servicemonitor.data.repository.AttemptRepository
import com.school.servicemonitor.data.repository.ExcuseOutcome
import com.school.servicemonitor.data.repository.ExcuseRepository
import com.school.servicemonitor.data.repository.SanctionRepository
import com.school.servicemonitor.data.repository.SettingsRepository
import com.school.servicemonitor.data.repository.StudentRepository
import com.school.servicemonitor.domain.BulkPassItem
import com.school.servicemonitor.domain.BulkPassPlanner
import com.school.servicemonitor.domain.BulkRowCheck
import com.school.servicemonitor.domain.DailyStatus
import com.school.servicemonitor.domain.DashboardBuilder
import com.school.servicemonitor.domain.DashboardEntry
import com.school.servicemonitor.domain.DayRecordInfo
import com.school.servicemonitor.domain.ScheduleCalculator
import com.school.servicemonitor.domain.TimeRangeInput
import com.school.servicemonitor.ui.components.formatMinutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DashboardUiState(
    val date: LocalDate,
    val isServiceDay: Boolean,
    val entries: List<DashboardEntry>,
    val excuses: Map<Long, Excuse> = emptyMap(),   // active excuses on this date, by student id
    val loading: Boolean = false
) {
    fun entriesFor(status: DailyStatus): List<DashboardEntry> =
        entries.filter { it.status == status }
}

data class BulkRow(val studentId: Long, val name: String, val studentNumber: String)

data class BulkResultLine(val name: String, val saved: Boolean, val message: String)

/** Everything the bulk PASS screen shows. */
data class BulkPassState(
    val rows: List<BulkRow>,
    val common: TimeRangeInput = TimeRangeInput.default(),
    val overrides: Map<Long, TimeRangeInput> = emptyMap(),
    val requiredMinutes: Int = 60,
    val saving: Boolean = false,
    val results: List<BulkResultLine>? = null,
    val error: String? = null
) {
    /** A student's own time if the operator adjusted it, otherwise the common time. */
    fun timeFor(studentId: Long): TimeRangeInput = overrides[studentId] ?: common

    private fun itemFor(row: BulkRow): BulkPassItem {
        val t = timeFor(row.studentId)
        return BulkPassItem(row.studentId, t.start, t.end)
    }

    private val plan: BulkPassPlanner.Plan
        get() = BulkPassPlanner.plan(rows.map(::itemFor), requiredMinutes)

    val validItems: List<BulkPassItem> get() = plan.valid
    val problemCount: Int get() = plan.problems.size

    fun problemFor(studentId: Long): String? {
        val row = rows.firstOrNull { it.studentId == studentId } ?: return null
        return (BulkPassPlanner.check(itemFor(row), requiredMinutes) as? BulkRowCheck.Problem)?.message
    }
}

/** The daily excuse dialog (one or many students). */
data class ExcuseState(
    val rows: List<BulkRow>,
    val saving: Boolean = false,
    val error: String? = null,
    val results: List<BulkResultLine>? = null
)

/** The revoke-excuse dialog. */
data class RevokeState(
    val excuseId: Long,
    val name: String,
    val saving: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    studentRepository: StudentRepository,
    sanctionRepository: SanctionRepository,
    private val attemptRepository: AttemptRepository,
    private val excuseRepository: ExcuseRepository,
    private val settingsRepository: SettingsRepository,
    private val deadlineRepository: DeadlineRepository
) : ViewModel() {

    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    private val _tab = MutableStateFlow(DailyStatus.PENDING)
    val tab: StateFlow<DailyStatus> = _tab.asStateFlow()

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    private val _bulk = MutableStateFlow<BulkPassState?>(null)
    val bulk: StateFlow<BulkPassState?> = _bulk.asStateFlow()

    private val _excuse = MutableStateFlow<ExcuseState?>(null)
    val excuse: StateFlow<ExcuseState?> = _excuse.asStateFlow()

    private val _revoke = MutableStateFlow<RevokeState?>(null)
    val revoke: StateFlow<RevokeState?> = _revoke.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // How many overdue sanctions are waiting for a repeat decision.
    val pendingRepeats: StateFlow<Int> = deadlineRepository.observePending()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        // Re-check deadlines whenever the Dashboard opens (covers a date change while the app was open).
        viewModelScope.launch {
            try {
                deadlineRepository.scan()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The background monitor will try again on the next change.
            }
        }
    }

    // Rebuilds when the date changes OR any student, sanction, daily record, excuse or setting changes.
    val state: StateFlow<DashboardUiState> = _date
        .flatMapLatest { date ->
            combine(
                studentRepository.observeActive(),
                sanctionRepository.observeAllForActiveStudents(),
                attemptRepository.observeDayRecords(date),
                excuseRepository.observeActiveForDate(date),
                settingsRepository.observeAll()
            ) { students, sanctionRows, dayRows, excuses, settings ->
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
                    ),
                    excuses = excuses.associateBy { it.studentId }
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DashboardUiState(LocalDate.now(), isServiceDay = true, entries = emptyList(), loading = true)
        )

    fun onDateChange(date: LocalDate) { clearSelection(); _date.value = date }
    fun previousDay() { clearSelection(); _date.value = _date.value.minusDays(1) }
    fun nextDay() { clearSelection(); _date.value = _date.value.plusDays(1) }
    fun goToToday() { clearSelection(); _date.value = LocalDate.now() }
    fun onTabChange(status: DailyStatus) { clearSelection(); _tab.value = status }

    fun consumeMessage() { _message.value = null }

    // ---- Selection ----

    fun startSelection(studentId: Long) { _selected.value = setOf(studentId) }

    fun toggleSelection(studentId: Long) {
        _selected.update { if (studentId in it) it - studentId else it + studentId }
    }

    fun selectAll(ids: Collection<Long>) { _selected.value = ids.toSet() }

    fun clearSelection() { _selected.value = emptySet() }

    // ---- Bulk PASS ----

    fun startBulkPass(entries: List<DashboardEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            val required = settingsRepository.getInt(SettingsRepository.KEY_DAILY_CREDIT_MINUTES, 60)
            _bulk.value = BulkPassState(
                rows = entries.map {
                    BulkRow(it.student.id, it.student.fullName, it.student.studentNumber)
                },
                requiredMinutes = required
            )
        }
    }

    fun onBulkCommonChange(value: TimeRangeInput) =
        _bulk.update { it?.copy(common = value, error = null) }

    fun onBulkOverride(studentId: Long, value: TimeRangeInput) =
        _bulk.update { it?.copy(overrides = it.overrides + (studentId to value), error = null) }

    fun onBulkResetOverride(studentId: Long) =
        _bulk.update { it?.copy(overrides = it.overrides - studentId) }

    fun closeBulk() {
        val hadResults = _bulk.value?.results != null
        _bulk.value = null
        if (hadResults) clearSelection()
    }

    fun saveBulkPass() {
        val s = _bulk.value ?: return
        if (s.saving || s.results != null) return
        val valid = s.validItems
        if (valid.isEmpty()) {
            _bulk.update { it?.copy(error = "No student has a valid time. Adjust the times first.") }
            return
        }
        _bulk.update { it?.copy(saving = true, error = null) }
        val date = _date.value

        viewModelScope.launch {
            try {
                val outcomes = attemptRepository.recordBulkPass(valid, date).associateBy { it.studentId }
                val lines = s.rows.map { row ->
                    val result = outcomes[row.studentId]
                    if (result == null) {
                        // Never sent to the database: it failed the time check.
                        BulkResultLine(row.name, false, "Skipped. ${s.problemFor(row.studentId) ?: ""}".trim())
                    } else {
                        when (val out = result.outcome) {
                            is AttemptOutcome.Saved -> BulkResultLine(
                                row.name, true,
                                "PASS saved. ${formatMinutes(out.creditedMinutes)} credited."
                            )
                            is AttemptOutcome.Rejected -> BulkResultLine(row.name, false, out.message)
                        }
                    }
                }
                _bulk.update { it?.copy(saving = false, results = lines) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bulk.update {
                    it?.copy(
                        saving = false,
                        error = "Could not save: ${e.message ?: "unknown error"}. Nothing was changed."
                    )
                }
            }
        }
    }

    // ---- Daily excuse (single or bulk) ----

    fun startExcuse(entries: List<DashboardEntry>) {
        if (entries.isEmpty()) return
        _excuse.value = ExcuseState(
            rows = entries.map { BulkRow(it.student.id, it.student.fullName, it.student.studentNumber) }
        )
    }

    fun closeExcuse() {
        val hadResults = _excuse.value?.results != null
        _excuse.value = null
        if (hadResults) clearSelection()
    }

    fun saveExcuse(reason: String) {
        val s = _excuse.value ?: return
        if (s.saving || s.results != null) return
        if (reason.isBlank()) {
            _excuse.update { it?.copy(error = "A reason is required.") }
            return
        }
        _excuse.update { it?.copy(saving = true, error = null) }
        val date = _date.value

        viewModelScope.launch {
            try {
                val outcomes = excuseRepository
                    .excuseStudents(s.rows.map { it.studentId }, date, reason)
                    .associateBy { it.studentId }
                val lines = s.rows.map { row ->
                    when (val out = outcomes[row.studentId]?.outcome) {
                        ExcuseOutcome.Saved -> {
                            val moved = outcomes[row.studentId]?.extendedCount ?: 0
                            BulkResultLine(
                                row.name, true,
                                "Excused. No hours were deducted." +
                                        if (moved > 0) " Deadline moved 1 school day on $moved sanction(s)." else ""
                            )
                        }
                        is ExcuseOutcome.Rejected -> BulkResultLine(row.name, false, out.message)
                        null -> BulkResultLine(row.name, false, "No result.")
                    }
                }
                _excuse.update { it?.copy(saving = false, results = lines) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _excuse.update {
                    it?.copy(
                        saving = false,
                        error = "Could not save: ${e.message ?: "unknown error"}. Nothing was changed."
                    )
                }
            }
        }
    }

    // ---- Revoke an excuse ----

    fun startRevoke(excuse: Excuse, name: String) {
        _revoke.value = RevokeState(excuseId = excuse.id, name = name)
    }

    fun closeRevoke() { _revoke.value = null }

    fun confirmRevoke(reason: String) {
        val s = _revoke.value ?: return
        if (s.saving) return
        if (reason.isBlank()) {
            _revoke.update { it?.copy(error = "A reason is required.") }
            return
        }
        _revoke.update { it?.copy(saving = true, error = null) }

        viewModelScope.launch {
            try {
                when (val out = excuseRepository.revoke(s.excuseId, reason)) {
                    ExcuseOutcome.Saved -> {
                        _revoke.value = null
                        _message.value = "Excuse revoked for ${s.name}."
                    }
                    is ExcuseOutcome.Rejected ->
                        _revoke.update { it?.copy(saving = false, error = out.message) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _revoke.update {
                    it?.copy(
                        saving = false,
                        error = "Could not save: ${e.message ?: "unknown error"}. Nothing was changed."
                    )
                }
            }
        }
    }
}