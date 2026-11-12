package com.school.servicemonitor.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.school.servicemonitor.backup.BackupManager
import com.school.servicemonitor.backup.BackupOutcome
import com.school.servicemonitor.backup.BackupRules
import com.school.servicemonitor.backup.BackupSummary
import com.school.servicemonitor.backup.InspectResult
import com.school.servicemonitor.data.repository.SettingsRepository
import com.school.servicemonitor.domain.SettingsCheck
import com.school.servicemonitor.domain.SettingsErrors
import com.school.servicemonitor.domain.SettingsInput
import com.school.servicemonitor.domain.SettingsValidator
import com.school.servicemonitor.domain.SettingsValues
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime

data class SettingsUiState(
    val loaded: Boolean = false,
    val creditText: String = "60",
    val multiplierText: String = "2",
    val days: Set<DayOfWeek> = SettingsValidator.defaults().serviceDays,
    val saved: SettingsValues? = null,
    val errors: SettingsErrors = SettingsErrors(),
    val savingRules: Boolean = false,
    val busy: Boolean = false,
    val busyLabel: String = "",
    val lastBackupAt: Long? = null,
    val staged: BackupSummary? = null,
    val message: String? = null,
    val restartRequested: Boolean = false
) {
    /** True when the form differs from what is saved. */
    val isDirty: Boolean
        get() {
            val s = saved ?: return false
            return creditText.trim() != s.dailyCreditMinutes.toString() ||
                    multiplierText.trim() != s.ssgMultiplier.toString() ||
                    days != s.serviceDays
        }
}

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val backup: BackupManager
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        // The result of a restore that was applied while the app was starting.
        backup.takeRestoreResult()?.let { msg -> _state.update { it.copy(message = msg) } }

        viewModelScope.launch {
            settings.observeAll().collect { map ->
                val values = SettingsValidator.fromStored(
                    map[SettingsRepository.KEY_DAILY_CREDIT_MINUTES],
                    map[SettingsRepository.KEY_SSG_MULTIPLIER],
                    map[SettingsRepository.KEY_SERVICE_DAYS]
                )
                val last = map[SettingsRepository.KEY_LAST_BACKUP_AT]?.toLongOrNull()
                _state.update { s ->
                    if (!s.loaded) {
                        s.copy(
                            loaded = true,
                            creditText = values.dailyCreditMinutes.toString(),
                            multiplierText = values.ssgMultiplier.toString(),
                            days = values.serviceDays,
                            saved = values,
                            lastBackupAt = last
                        )
                    } else {
                        s.copy(saved = values, lastBackupAt = last)
                    }
                }
            }
        }
    }

    fun consumeMessage() { _state.update { it.copy(message = null) } }

    // ---- Service rules ----

    fun onCreditChange(v: String) = _state.update {
        it.copy(creditText = v.filter(Char::isDigit).take(3), errors = it.errors.copy(credit = null))
    }

    fun onMultiplierChange(v: String) = _state.update {
        it.copy(multiplierText = v.filter(Char::isDigit).take(1), errors = it.errors.copy(multiplier = null))
    }

    fun toggleDay(day: DayOfWeek) = _state.update {
        val days = if (day in it.days) it.days - day else it.days + day
        it.copy(days = days, errors = it.errors.copy(days = null))
    }

    /** Fills the form with the defaults. They are saved only when the operator taps Save. */
    fun resetToDefaults() {
        val d = SettingsValidator.defaults()
        _state.update {
            it.copy(
                creditText = d.dailyCreditMinutes.toString(),
                multiplierText = d.ssgMultiplier.toString(),
                days = d.serviceDays,
                errors = SettingsErrors()
            )
        }
    }

    fun saveRules() {
        val s = _state.value
        if (s.savingRules || s.busy) return
        when (val check = SettingsValidator.validate(SettingsInput(s.creditText, s.multiplierText, s.days))) {
            is SettingsCheck.Invalid -> _state.update { it.copy(errors = check.errors) }
            is SettingsCheck.Valid -> {
                _state.update { it.copy(savingRules = true, errors = SettingsErrors()) }
                viewModelScope.launch {
                    val message = try {
                        settings.saveRules(check.values)
                        "Settings saved. They apply from now on. Existing sanctions keep their saved hours."
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        "Could not save: ${e.message ?: "unknown error"}. Nothing was changed."
                    }
                    _state.update {
                        it.copy(
                            savingRules = false,
                            message = message,
                            creditText = check.values.dailyCreditMinutes.toString(),
                            multiplierText = check.values.ssgMultiplier.toString(),
                            days = check.values.serviceDays
                        )
                    }
                }
            }
        }
    }

    // ---- Backup ----

    fun suggestedBackupName(): String = BackupRules.suggestedFileName(LocalDateTime.now())

    fun exportTo(uri: Uri) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, busyLabel = "Creating the backup...") }
        viewModelScope.launch {
            val message = when (val r = backup.export(uri)) {
                is BackupOutcome.Success -> "Backup saved (${BackupRules.formatSize(r.sizeBytes)})."
                is BackupOutcome.Failed -> r.message
            }
            _state.update { it.copy(busy = false, message = message) }
        }
    }

    // ---- Restore ----

    fun stageRestore(uri: Uri) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, busyLabel = "Checking the backup file...") }
        viewModelScope.launch {
            when (val r = backup.stage(uri)) {
                is InspectResult.Valid -> _state.update { it.copy(busy = false, staged = r.summary) }
                is InspectResult.Invalid -> _state.update { it.copy(busy = false, message = r.message) }
            }
        }
    }

    fun cancelRestore() {
        backup.discardStaged()
        _state.update { it.copy(staged = null) }
    }

    fun confirmRestore() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, busyLabel = "Preparing the restore...") }
        viewModelScope.launch {
            val error = backup.commitStaged()
            if (error != null) {
                _state.update { it.copy(busy = false, staged = null, message = error) }
            } else {
                _state.update { it.copy(busy = false, staged = null, restartRequested = true) }
            }
        }
    }
}