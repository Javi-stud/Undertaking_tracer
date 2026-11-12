package com.school.servicemonitor.ui.deadlines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.school.servicemonitor.appContainer
import com.school.servicemonitor.data.entity.DeadlineEventRow
import com.school.servicemonitor.domain.DeadlineMonitor
import com.school.servicemonitor.ui.components.DatePickerField
import com.school.servicemonitor.ui.components.display
import com.school.servicemonitor.ui.components.formatMinutes
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionRequiredScreen(
    onBack: () -> Unit,
    onOpenStudent: (Long) -> Unit
) {
    val container = LocalContext.current.appContainer()
    val viewModel: ActionRequiredViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ActionRequiredViewModel(container.deadlineRepository) }
        }
    )
    val events by viewModel.events.collectAsStateWithLifecycle()
    val confirm by viewModel.confirm.collectAsStateWithLifecycle()
    val dismiss by viewModel.dismiss.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Action required") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val list = events
        when {
            list == null -> Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            list.isEmpty() -> Box(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No overdue sanctions need a decision.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "These sanctions passed their deadline with hours still owed. " +
                                "A repeat adds the full original hours again, but only after you confirm " +
                                "new dates. Nothing has been added yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(list, key = { it.event.id }) { row ->
                    EventCard(
                        row = row,
                        onOpenStudent = { onOpenStudent(row.studentId) },
                        onConfirm = { viewModel.startConfirm(row) },
                        onDismiss = { viewModel.startDismiss(row) }
                    )
                }
            }
        }
    }

    confirm?.let { state ->
        ConfirmDialog(
            state = state,
            onStartChange = viewModel::onStartChange,
            onEndChange = viewModel::onEndChange,
            onSave = viewModel::saveConfirm,
            onClose = viewModel::closeConfirm
        )
    }
    dismiss?.let { state ->
        DismissDialog(state, onSave = viewModel::saveDismiss, onClose = viewModel::closeDismiss)
    }
}

@Composable
private fun EventCard(
    row: DeadlineEventRow,
    onOpenStudent: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val today = LocalDate.now()
    val days = DeadlineMonitor.daysOverdue(row.event.triggeringDeadline, today)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.studentName(), style = MaterialTheme.typography.titleMedium)
            Text(
                "No. ${row.studentNumber}  |  ${row.violationName}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Deadline was ${row.event.triggeringDeadline.display()} " +
                        "($days day${if (days == 1) "" else "s"} ago)",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Original: assigned ${formatMinutes(row.assignedMinutes)}  |  " +
                        "completed ${formatMinutes(row.allocatedMinutes)}  |  " +
                        "remaining ${formatMinutes(row.remainingMinutes)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Proposed repeat: the full ${formatMinutes(row.assignedMinutes)} again, same violation.",
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Dismiss") }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("Confirm repeat") }
            }
            TextButton(onClick = onOpenStudent) { Text("Open student profile") }
        }
    }
}

@Composable
private fun ConfirmDialog(
    state: ConfirmState,
    onStartChange: (LocalDate) -> Unit,
    onEndChange: (LocalDate) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    val row = state.row
    AlertDialog(
        onDismissRequest = { if (!state.saving) onClose() },
        title = { Text("Confirm repeated sanction") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(row.studentName(), style = MaterialTheme.typography.titleSmall)
                Text("${row.violationName} (repeat)", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "New sanction: the full ${formatMinutes(row.assignedMinutes)}, " +
                            "not just the ${formatMinutes(row.remainingMinutes)} that remain.",
                    style = MaterialTheme.typography.bodyMedium
                )
                DatePickerField(label = "New start date", date = state.start, onDateSelected = onStartChange)
                DatePickerField(label = "New deadline", date = state.end, onDateSelected = onEndChange)
                Text(
                    "After you confirm, ${formatMinutes(row.assignedMinutes)} is added to this " +
                            "student's outstanding hours. The original sanction keeps its remaining " +
                            "${formatMinutes(row.remainingMinutes)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !state.saving && state.end != null) {
                Text(if (state.saving) "Saving..." else "Confirm and add hours")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose, enabled = !state.saving) { Text("Cancel") }
        }
    )
}

@Composable
private fun DismissDialog(
    state: DismissState,
    onSave: (String) -> Unit,
    onClose: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!state.saving) onClose() },
        title = { Text("Dismiss repeat?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "No repeated sanction will be created for ${state.row.studentName()}. " +
                            "The original keeps its remaining ${formatMinutes(state.row.remainingMinutes)}. " +
                            "The decision is saved in the history.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (required)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(reason) }, enabled = !state.saving) {
                Text(if (state.saving) "Saving..." else "Dismiss repeat")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose, enabled = !state.saving) { Text("Cancel") }
        }
    )
}