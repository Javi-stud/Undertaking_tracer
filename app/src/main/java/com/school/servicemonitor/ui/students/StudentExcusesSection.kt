package com.school.servicemonitor.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.school.servicemonitor.appContainer
import com.school.servicemonitor.data.entity.Excuse
import com.school.servicemonitor.data.entity.ExemptionRow
import com.school.servicemonitor.data.entity.SanctionRow
import com.school.servicemonitor.data.repository.ExcuseRepository
import com.school.servicemonitor.data.repository.ExemptionOutcome
import com.school.servicemonitor.data.repository.ExemptionRepository
import com.school.servicemonitor.data.repository.SanctionRepository
import com.school.servicemonitor.domain.SanctionCalculator
import com.school.servicemonitor.ui.components.DatePickerField
import com.school.servicemonitor.ui.components.StatusChip
import com.school.servicemonitor.ui.components.display
import com.school.servicemonitor.ui.components.formatMinutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class StudentExcusesViewModel(
    excuseRepository: ExcuseRepository,
    private val exemptionRepository: ExemptionRepository,
    sanctionRepository: SanctionRepository,
    private val studentId: Long
) : ViewModel() {

    val excuses: StateFlow<List<Excuse>> = excuseRepository.observeForStudent(studentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val exemptions: StateFlow<List<ExemptionRow>> = exemptionRepository.observeForStudent(studentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Sanctions that still owe minutes, so they can be exempted.
    val exemptible: StateFlow<List<SanctionRow>> = sanctionRepository.observeForStudent(studentId)
        .map { rows -> rows.filter { SanctionCalculator.remainingMinutes(it) > 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** [onResult] receives null on success, or an error message. */
    fun grant(
        sanctionId: Long,
        minutes: Int,
        reason: String,
        approvedBy: String,
        date: LocalDate,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            val message = try {
                when (val out = exemptionRepository.grant(studentId, sanctionId, minutes, reason, approvedBy, date)) {
                    ExemptionOutcome.Saved -> null
                    is ExemptionOutcome.Rejected -> out.message
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Could not save: ${e.message ?: "unknown error"}. Nothing was changed."
            }
            onResult(message)
        }
    }
}

@Composable
fun StudentExcusesSection(studentId: Long, canGrant: Boolean) {
    val container = LocalContext.current.appContainer()
    val viewModel: StudentExcusesViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                StudentExcusesViewModel(
                    container.excuseRepository,
                    container.exemptionRepository,
                    container.sanctionRepository,
                    studentId
                )
            }
        }
    )
    val excuses by viewModel.excuses.collectAsStateWithLifecycle()
    val exemptions by viewModel.exemptions.collectAsStateWithLifecycle()
    val exemptible by viewModel.exemptible.collectAsStateWithLifecycle()
    var showGrant by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Daily excuses", style = MaterialTheme.typography.titleMedium)
            Text(
                "A daily excuse never reduces hours or cancels a sanction.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (excuses.isEmpty()) {
                Text("No excuses recorded.", style = MaterialTheme.typography.bodyMedium)
            } else {
                excuses.forEach { e ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            e.excuseDate.display(),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        if (e.revokedAt == null) {
                            StatusChip(
                                "Excused",
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        } else {
                            StatusChip(
                                "Revoked",
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Text("Reason: ${e.reason}", style = MaterialTheme.typography.bodyMedium)
                    e.revokeReason?.let {
                        Text("Revoke reason: $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Permanent exemptions", style = MaterialTheme.typography.titleMedium)
            Text(
                "An approved cancellation of some or all remaining hours of one sanction.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (exemptions.isEmpty()) {
                Text("No exemptions granted.", style = MaterialTheme.typography.bodyMedium)
            } else {
                exemptions.forEach { row ->
                    val x = row.exemption
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Text(row.violationName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${formatMinutes(x.exemptedMinutes)} exempted on ${x.approvalDate.display()}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("Approved by: ${x.approvedBy}", style = MaterialTheme.typography.bodySmall)
                    Text("Reason: ${x.reason}", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (canGrant && exemptible.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showGrant = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("Grant exemption") }
            }
        }
    }

    if (showGrant) {
        GrantExemptionDialog(
            sanctions = exemptible,
            onGrant = viewModel::grant,
            onClose = { showGrant = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GrantExemptionDialog(
    sanctions: List<SanctionRow>,
    onGrant: (Long, Int, String, String, LocalDate, (String?) -> Unit) -> Unit,
    onClose: () -> Unit
) {
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var all by remember { mutableStateOf(true) }
    var hours by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var approver by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val selected = sanctions.firstOrNull { it.sanction.id == selectedId }
    val remaining = selected?.let { SanctionCalculator.remainingMinutes(it) } ?: 0

    AlertDialog(
        onDismissRequest = { if (!saving) onClose() },
        title = { Text("Grant permanent exemption") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selected?.let {
                            "${it.violationName} (${formatMinutes(remaining)} left)"
                        }.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sanction") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        sanctions.forEach { row ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${row.violationName} - due ${row.sanction.endDate.display()} " +
                                                "(${formatMinutes(SanctionCalculator.remainingMinutes(row))} left)"
                                    )
                                },
                                onClick = {
                                    selectedId = row.sanction.id
                                    expanded = false
                                    error = null
                                }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Cancel all remaining hours" +
                                if (selected != null) " (${formatMinutes(remaining)})" else "",
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = all, onCheckedChange = { all = it; error = null })
                }
                if (!all) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = hours,
                            onValueChange = { hours = it.filter(Char::isDigit).take(3) },
                            label = { Text("Hours") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minutes,
                            onValueChange = { minutes = it.filter(Char::isDigit).take(2) },
                            label = { Text("Minutes") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (required)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = approver,
                    onValueChange = { approver = it },
                    label = { Text("Approved by (required)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                DatePickerField(label = "Approval date", date = date, onDateSelected = { date = it })

                Text(
                    "This permanently cancels hours. It cannot be undone in this version.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving,
                onClick = {
                    val sel = selected
                    val amount = if (all) remaining
                    else (hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)
                    when {
                        sel == null -> error = "Choose a sanction."
                        amount <= 0 -> error = "Enter how much to exempt."
                        else -> {
                            saving = true
                            error = null
                            onGrant(sel.sanction.id, amount, reason, approver, date) { message ->
                                saving = false
                                if (message == null) onClose() else error = message
                            }
                        }
                    }
                }
            ) { Text(if (saving) "Saving..." else "Grant exemption") }
        },
        dismissButton = {
            TextButton(onClick = onClose, enabled = !saving) { Text("Cancel") }
        }
    )
}