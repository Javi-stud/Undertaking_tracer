package com.school.servicemonitor.ui.deadlines

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.school.servicemonitor.data.entity.DeadlineEventRow
import com.school.servicemonitor.data.repository.DeadlineRepository
import com.school.servicemonitor.data.repository.RepeatOutcome
import com.school.servicemonitor.domain.DeadlineMonitor
import com.school.servicemonitor.domain.HistoryBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

fun DeadlineEventRow.studentName(): String =
    HistoryBuilder.displayName(lastName, firstName, middleName)

data class ConfirmState(
    val row: DeadlineEventRow,
    val start: LocalDate = LocalDate.now(),
    val end: LocalDate? = null,          // the operator must choose this
    val saving: Boolean = false,
    val error: String? = null
)

data class DismissState(
    val row: DeadlineEventRow,
    val saving: Boolean = false,
    val error: String? = null
)

class ActionRequiredViewModel(private val repository: DeadlineRepository) : ViewModel() {

    // null = still loading
    val events: StateFlow<List<DeadlineEventRow>?> = repository.observePending()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _confirm = MutableStateFlow<ConfirmState?>(null)
    val confirm: StateFlow<ConfirmState?> = _confirm.asStateFlow()

    private val _dismiss = MutableStateFlow<DismissState?>(null)
    val dismiss: StateFlow<DismissState?> = _dismiss.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        // Opening this screen also re-checks the deadlines (covers a date change while the app was open).
        viewModelScope.launch {
            try {
                repository.scan()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ActionRequired", "Deadline scan failed", e)
            }
        }
    }

    fun consumeMessage() { _message.value = null }

    // ---- Confirm ----

    fun startConfirm(row: DeadlineEventRow) { _confirm.value = ConfirmState(row) }
    fun closeConfirm() { _confirm.value = null }

    fun onStartChange(date: LocalDate) = _confirm.update {
        // If the chosen deadline is now before the new start, make the operator pick it again.
        it?.copy(start = date, end = it.end?.takeIf { e -> !e.isBefore(date) }, error = null)
    }

    fun onEndChange(date: LocalDate) = _confirm.update { it?.copy(end = date, error = null) }

    fun saveConfirm() {
        val s = _confirm.value ?: return
        if (s.saving) return
        val end = s.end
        if (end == null) {
            _confirm.update { it?.copy(error = "Select the new deadline (end date).") }
            return
        }
        DeadlineMonitor.validateRepeatDates(s.start, end, LocalDate.now())?.let { message ->
            _confirm.update { it?.copy(error = message) }
            return
        }
        _confirm.update { it?.copy(saving = true, error = null) }

        viewModelScope.launch {
            try {
                when (val out = repository.confirm(s.row.event.id, s.start, end)) {
                    is RepeatOutcome.Saved -> {
                        _confirm.value = null
                        _message.value = "Repeat sanction created for ${s.row.studentName()}."
                    }
                    is RepeatOutcome.Rejected ->
                        _confirm.update { it?.copy(saving = false, error = out.message) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _confirm.update {
                    it?.copy(saving = false, error = "Could not save: ${e.message ?: "unknown error"}. Nothing was changed.")
                }
            }
        }
    }

    // ---- Dismiss ----

    fun startDismiss(row: DeadlineEventRow) { _dismiss.value = DismissState(row) }
    fun closeDismiss() { _dismiss.value = null }

    fun saveDismiss(reason: String) {
        val s = _dismiss.value ?: return
        if (s.saving) return
        if (reason.isBlank()) {
            _dismiss.update { it?.copy(error = "A reason is required.") }
            return
        }
        _dismiss.update { it?.copy(saving = true, error = null) }

        viewModelScope.launch {
            try {
                when (val out = repository.dismiss(s.row.event.id, reason)) {
                    is RepeatOutcome.Saved -> {
                        _dismiss.value = null
                        _message.value = "Repeat dismissed for ${s.row.studentName()}."
                    }
                    is RepeatOutcome.Rejected ->
                        _dismiss.update { it?.copy(saving = false, error = out.message) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _dismiss.update {
                    it?.copy(saving = false, error = "Could not save: ${e.message ?: "unknown error"}. Nothing was changed.")
                }
            }
        }
    }
}