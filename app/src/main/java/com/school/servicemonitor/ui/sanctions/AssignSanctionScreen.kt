package com.school.servicemonitor.ui.sanctions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.school.servicemonitor.ui.components.DatePickerField
import com.school.servicemonitor.ui.components.formatMinutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignSanctionScreen(
    studentId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val container = LocalContext.current.appContainer()
    val viewModel: AssignSanctionViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                AssignSanctionViewModel(
                    container.studentRepository,
                    container.violationRepository,
                    container.sanctionRepository,
                    studentId
                )
            }
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assign sanction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val student = state.student
        when {
            state.loading -> Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            student == null -> Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { Text("This student could not be found.") }

            student.status != StudentStatus.ACTIVE -> Box(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) { Text("Archived students cannot receive new sanctions. Restore the student first.") }

            else -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(student.fullName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (student.isSsg) "SSG member (multiplier x${state.multiplier})"
                            else "Regular student",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (state.violations.isEmpty()) {
                    Text(
                        "There are no active violations. Add one in the Sanctions tab first.",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = state.selectedViolation?.name.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Violation") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            state.violations.forEach { violation ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${violation.name} (${formatMinutes(violation.standardHours * 60)})")
                                    },
                                    onClick = {
                                        viewModel.onViolationSelected(violation)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    DatePickerField(
                        label = "Start date",
                        date = state.startDate,
                        onDateSelected = viewModel::onStartDateChange
                    )
                    DatePickerField(
                        label = "Deadline",
                        date = state.endDate,
                        onDateSelected = viewModel::onEndDateChange
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fixed deadline", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Excuses will not move this sanction's end date.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.deadlineLocked,
                            onCheckedChange = viewModel::onDeadlineLockedChange
                        )
                    }

                    state.selectedViolation?.let { violation ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Sanction preview", style = MaterialTheme.typography.titleSmall)
                                Text("Standard: ${formatMinutes(violation.standardHours * 60)}")
                                if (state.multiplier > 1) {
                                    Text("SSG multiplier: x${state.multiplier}")
                                }
                                Text(
                                    "Final sanction: ${formatMinutes(state.previewMinutes ?: 0)}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "This amount is saved with the sanction and will not change later.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }

                    Button(
                        onClick = viewModel::save,
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Assign sanction") }
                }
            }
        }
    }
}
