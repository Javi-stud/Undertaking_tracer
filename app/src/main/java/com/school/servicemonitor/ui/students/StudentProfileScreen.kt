package com.school.servicemonitor.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.school.servicemonitor.appContainer
import com.school.servicemonitor.data.entity.SanctionRow
import com.school.servicemonitor.data.entity.SanctionStatus
import com.school.servicemonitor.data.entity.Student
import com.school.servicemonitor.data.entity.StudentStatus
import com.school.servicemonitor.data.entity.fullName
import com.school.servicemonitor.domain.HourSummary
import com.school.servicemonitor.domain.SanctionCalculator
import com.school.servicemonitor.ui.components.StatusChip
import com.school.servicemonitor.ui.components.display
import com.school.servicemonitor.ui.components.formatMinutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentProfileScreen(
    studentId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAssignSanction: () -> Unit
) {
    val container = LocalContext.current.appContainer()
    val viewModel: StudentProfileViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                StudentProfileViewModel(
                    container.studentRepository,
                    container.sanctionRepository,
                    studentId
                )
            }
        }
    )

    val state by viewModel.state.collectAsStateWithLifecycle()
    val sanctions by viewModel.sanctions.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(deleted) {
        if (deleted) onBack()
    }
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
                title = { Text("Student profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state is ProfileState.Found) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit student")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (val s = state) {
            ProfileState.Loading -> Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            ProfileState.NotFound -> Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { Text("This student could not be found.") }

            is ProfileState.Found -> ProfileContent(
                student = s.student,
                sanctions = sanctions,
                summary = summary,
                modifier = Modifier.padding(padding),
                onArchive = { showArchiveDialog = true },
                onRestore = viewModel::restore,
                onDelete = { showDeleteDialog = true },
                onAssignSanction = onAssignSanction
            )
        }
    }

    if (showArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            title = { Text("Archive student?") },
            text = {
                Text("The student will be hidden from the main list. All of their records are kept, and you can restore them any time.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showArchiveDialog = false
                    viewModel.archive()
                }) { Text("Archive") }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete permanently?") },
            text = {
                Text("This permanently removes the student. This cannot be undone. Students with sanction records cannot be deleted.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deletePermanently()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProfileContent(
    student: Student,
    sanctions: List<SanctionRow>,
    summary: HourSummary,
    modifier: Modifier,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onAssignSanction: () -> Unit
) {
    val isArchived = student.status == StudentStatus.ARCHIVED

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(student.fullName, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (student.isSsg) {
                        StatusChip(
                            "SSG member",
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        StatusChip(
                            "Regular student",
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    if (isArchived) {
                        StatusChip(
                            "Archived",
                            MaterialTheme.colorScheme.errorContainer,
                            MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                InfoRow("Student number", student.studentNumber)
                InfoRow("Grade level", student.gradeLevel)
                InfoRow("Section", student.section)
                InfoRow("SSG member", if (student.isSsg) "Yes" else "No")
                InfoRow("Registered", student.registrationDate.display())
            }
        }

        HoursSummaryCard(summary)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sanctions", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                if (sanctions.isEmpty()) {
                    Text(
                        "No sanctions assigned.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    sanctions.forEachIndexed { index, row ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SanctionItem(row)
                    }
                }

                if (!isArchived) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onAssignSanction, modifier = Modifier.fillMaxWidth()) {
                        Text("Assign sanction")
                    }
                }
            }
        }

        StudentExcusesSection(studentId = student.id, canGrant = !isArchived)

        StudentAttendanceSection(studentId = student.id)

        if (isArchived) {

            Button(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                Text("Restore student")
            }
            OutlinedButton(
                onClick = onDelete,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete permanently")
            }
        } else {
            OutlinedButton(onClick = onArchive, modifier = Modifier.fillMaxWidth()) {
                Text("Archive student")
            }
        }
    }
}

@Composable
private fun HoursSummaryCard(summary: HourSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (summary.outstandingMinutes > 0)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Outstanding service hours", style = MaterialTheme.typography.titleSmall)
            Text(
                formatMinutes(summary.outstandingMinutes),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                "Across ${summary.activeSanctionCount} active sanction" +
                        if (summary.activeSanctionCount == 1) "" else "s",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            InfoRow("Completed", formatMinutes(summary.completedMinutes))
            InfoRow("Total assigned", formatMinutes(summary.totalAssignedMinutes))
            if (summary.exemptedMinutes > 0) InfoRow("Exempted", formatMinutes(summary.exemptedMinutes))
        }
    }
}

@Composable
private fun SanctionItem(row: SanctionRow) {
    val s = row.sanction
    val remaining = SanctionCalculator.remainingMinutes(row)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.violationName,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            val (label, container, content) = when (s.status) {
                SanctionStatus.ACTIVE -> Triple(
                    "Active",
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer
                )
                SanctionStatus.COMPLETED -> Triple(
                    "Completed",
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer
                )
                SanctionStatus.OVERDUE -> Triple(
                    "Overdue",
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer
                )
                SanctionStatus.EXEMPTED -> Triple(
                    "Exempted",
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            StatusChip(label, container, content)
        }
        Text(
            buildString {
                append("Assigned: ${formatMinutes(s.assignedMinutes)}")
                if (s.multiplierApplied > 1) {
                    append(" (${formatMinutes(s.baseHours * 60)} x${s.multiplierApplied} SSG)")
                }
            },
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "Completed: ${formatMinutes(row.allocatedMinutes)}   |   Remaining: ${formatMinutes(remaining)}",
            style = MaterialTheme.typography.bodyMedium
        )
        if (row.exemptedMinutes > 0) {
            Text(
                "Exempted: ${formatMinutes(row.exemptedMinutes)}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            "${s.startDate.display()}  to  ${s.endDate.display()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f)
        )
    }
}