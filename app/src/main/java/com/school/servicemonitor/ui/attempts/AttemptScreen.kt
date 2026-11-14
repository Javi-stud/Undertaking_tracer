package com.school.servicemonitor.ui.attempts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.school.servicemonitor.appContainer
import com.school.servicemonitor.data.entity.AttemptResultType
import com.school.servicemonitor.data.entity.AttemptState
import com.school.servicemonitor.data.entity.ServiceAttempt
import com.school.servicemonitor.data.entity.StudentStatus
import com.school.servicemonitor.data.entity.fullName
import com.school.servicemonitor.domain.PassCheck
import com.school.servicemonitor.domain.TimeRangeCalculator
import com.school.servicemonitor.domain.TimeRangeCheck
import com.school.servicemonitor.domain.formatClock
import com.school.servicemonitor.ui.components.InitialsAvatar
import com.school.servicemonitor.ui.components.NoInsets
import com.school.servicemonitor.ui.components.StatusChip
import com.school.servicemonitor.ui.components.TimeRangeInputField
import com.school.servicemonitor.ui.components.display
import com.school.servicemonitor.ui.components.formatMinutes
import com.school.servicemonitor.ui.components.formatTimestamp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttemptScreen(
    studentId: Long,
    date: LocalDate,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val container = LocalContext.current.appContainer()
    val viewModel: AttemptViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                AttemptViewModel(
                    container.studentRepository,
                    container.sanctionRepository,
                    container.attemptRepository,
                    container.voidRepository,
                    container.settingsRepository,
                    studentId,
                    date
                )
            }
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val voidState by viewModel.voidState.collectAsStateWithLifecycle()
    val student = state.student
    val isFuture = date.isAfter(LocalDate.now())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record cleaning") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        contentWindowInsets = NoInsets
    ) { padding ->
        when {
            state.loading -> Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            student == null -> Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { Text("This student could not be found.") }

            else -> {
                val passed = state.passedAttempt
                val canRecord = student.status == StudentStatus.ACTIVE &&
                        !isFuture && state.isServiceDay && passed == null && state.outstandingMinutes > 0

                val range = TimeRangeCalculator.check(state.time.start, state.time.end)
                val passAllowed = TimeRangeCalculator.passCheck(
                    state.time.start, state.time.end, state.requiredMinutes
                ) == PassCheck.Allowed

                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Student header
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                InitialsAvatar(student.firstName, student.lastName, diameter = 52.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(student.fullName, style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        "No. ${student.studentNumber}  |  ${student.gradeLevel} - ${student.section}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (student.isSsg) {
                                    StatusChip(
                                        "SSG",
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                formatMinutes(state.outstandingMinutes),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "outstanding across ${state.activeSanctionCount} active sanction" +
                                        if (state.activeSanctionCount == 1) "" else "s",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                                        ", " + date.display(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Why recording may be unavailable
                    when {
                        student.status != StudentStatus.ACTIVE ->
                            Warning("This student is archived. Restore the student to record cleaning.")
                        isFuture -> Warning("Future date: view only.")
                        !state.isServiceDay -> Warning("Not a service day. Use bulk Excused for holidays.")
                        passed != null -> Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Passed for this date", style = MaterialTheme.typography.titleMedium)
                                Text("Credited: ${formatMinutes(passed.creditedMinutes)}")
                                Text(
                                    "This student is finished for today. If this was a mistake, " +
                                            "tap Void attempt on the attempt below.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        state.outstandingMinutes <= 0 ->
                            Warning("This student has no outstanding hours.")
                        date.isBefore(LocalDate.now()) ->
                            Warning("This is a past date. Check the date before saving.")
                    }

                    state.notice?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    if (canRecord) {
                        Text(
                            "Enter the times you observed. There is no timer: this only records what you saw.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TimeRangeInputField(
                            value = state.time,
                            onValueChange = viewModel::onTimeChange,
                            requiredMinutesForPass = state.requiredMinutes
                        )
                        OutlinedTextField(
                            value = state.failReason,
                            onValueChange = viewModel::onReasonChange,
                            label = { Text("Reason (required for FAIL)") },
                            isError = state.reasonError != null,
                            supportingText = state.reasonError?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = viewModel::pass,
                                enabled = passAllowed && !state.saving,
                                modifier = Modifier.weight(1f).height(56.dp)
                            ) { Text("PASS", style = MaterialTheme.typography.titleMedium) }
                            Button(
                                onClick = viewModel::fail,
                                enabled = range is TimeRangeCheck.Valid && !state.saving,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                modifier = Modifier.weight(1f).height(56.dp)
                            ) { Text("FAIL", style = MaterialTheme.typography.titleMedium) }
                        }
                    }

                    // Attempts
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Attempts on this date", style = MaterialTheme.typography.titleMedium)
                            if (state.attempts.isEmpty()) {
                                Text(
                                    "No attempts recorded.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                state.attempts.forEachIndexed { index, attempt ->
                                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                    AttemptItem(attempt, onVoid = { viewModel.startVoid(attempt) })
                                }
                            }
                        }
                    }

                    OutlinedButton(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth()) {
                        Text("Open student profile")
                    }
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }

    voidState?.let {
        VoidDialog(state = it, onConfirm = viewModel::confirmVoid, onClose = viewModel::closeVoid)
    }
}

@Composable
private fun Warning(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun AttemptItem(a: ServiceAttempt, onVoid: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "#${a.attemptNo}   ${a.startTime.formatClock()} to ${a.endTime.formatClock()}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            if (a.recordState == AttemptState.VOIDED) {
                StatusChip(
                    "VOIDED",
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer
                )
            } else if (a.result == AttemptResultType.PASS) {
                StatusChip(
                    "PASS",
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                StatusChip(
                    "FAIL",
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        Text(
            "Duration: ${formatMinutes(a.durationMinutes)}   |   Credited: ${formatMinutes(a.creditedMinutes)}",
            style = MaterialTheme.typography.bodyMedium
        )
        a.failReason?.let { Text("Reason: $it", style = MaterialTheme.typography.bodySmall) }
        a.voidReason?.let { Text("Void reason: $it", style = MaterialTheme.typography.bodySmall) }
        a.voidedAt?.let {
            Text("Voided: ${formatTimestamp(it)}", style = MaterialTheme.typography.bodySmall)
        }
        if (a.recordState == AttemptState.RECORDED) {
            TextButton(onClick = onVoid) { Text("Void attempt") }
        }
    }
}