package com.school.servicemonitor.ui.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.school.servicemonitor.appContainer
import com.school.servicemonitor.data.entity.StudentStatus
import com.school.servicemonitor.data.entity.fullName
import com.school.servicemonitor.report.ReportFormat
import com.school.servicemonitor.ui.components.DatePickerField
import com.school.servicemonitor.ui.components.NoInsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen() {
    val context = LocalContext.current
    val container = context.appContainer()
    val viewModel: ReportsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ReportsViewModel(container.reportRepository) }
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    var showPicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // The system "save as" screen. We never ask for storage permission.
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ReportFormat.PDF.mimeType)
    ) { uri ->
        if (uri != null) viewModel.export(uri, context.contentResolver) else viewModel.cancelPending()
    }
    val docxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ReportFormat.DOCX.mimeType)
    ) { uri ->
        if (uri != null) viewModel.export(uri, context.contentResolver) else viewModel.cancelPending()
    }

    fun start(kind: ReportKind, format: ReportFormat) {
        val name = viewModel.prepare(kind, format) ?: return
        if (format == ReportFormat.PDF) pdfLauncher.launch(name) else docxLauncher.launch(name)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Reports") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = NoInsets
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Creating the report...", style = MaterialTheme.typography.bodySmall)
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Individual student report", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Student information, hours, sanctions, repeats, attendance, failed and voided " +
                                "attempts, excuses and exemptions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(state.student?.let { "${it.fullName} (No. ${it.studentNumber})" } ?: "Choose student")
                    }
                    FormatButtons(
                        enabled = !state.busy,
                        onPdf = { start(ReportKind.STUDENT, ReportFormat.PDF) },
                        onWord = { start(ReportKind.STUDENT, ReportFormat.DOCX) }
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Daily attendance report", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Scheduled, passed, failed, excused, recorded cleaning times and credited hours for one date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DatePickerField(label = "Date", date = state.date, onDateSelected = viewModel::onDate)
                    FormatButtons(
                        enabled = !state.busy,
                        onPdf = { start(ReportKind.DAILY, ReportFormat.PDF) },
                        onWord = { start(ReportKind.DAILY, ReportFormat.DOCX) }
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sanction report", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Every sanction with assigned, completed and remaining hours, dates and repeat links.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Only sanctions that still owe hours", modifier = Modifier.weight(1f))
                        Switch(checked = state.onlyOutstanding, onCheckedChange = viewModel::onOnlyOutstanding)
                    }
                    FormatButtons(
                        enabled = !state.busy,
                        onPdf = { start(ReportKind.SANCTION, ReportFormat.PDF) },
                        onWord = { start(ReportKind.SANCTION, ReportFormat.DOCX) }
                    )
                }
            }

            Text(
                "After tapping PDF or Word you choose where to save the file. " +
                        "No storage permission is needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Choose student") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::onQuery,
                        singleLine = true,
                        placeholder = { Text("Search by name or number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (results.isEmpty()) {
                        Text("No students found.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            items(results, key = { it.id }) { student ->
                                TextButton(
                                    onClick = {
                                        viewModel.pickStudent(student)
                                        showPicker = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "${student.fullName}  (No. ${student.studentNumber})" +
                                                if (student.status == StudentStatus.ARCHIVED) "  - archived" else "",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun FormatButtons(enabled: Boolean, onPdf: () -> Unit, onWord: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onPdf, enabled = enabled, modifier = Modifier.weight(1f)) { Text("Save as PDF") }
        Button(onClick = onWord, enabled = enabled, modifier = Modifier.weight(1f)) { Text("Save as Word") }
    }
}