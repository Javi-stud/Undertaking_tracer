package com.school.servicemonitor.ui.history

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.school.servicemonitor.appContainer
import com.school.servicemonitor.data.entity.ExcuseHistoryRow
import com.school.servicemonitor.domain.DayOutcome
import com.school.servicemonitor.domain.HistoryBuilder
import com.school.servicemonitor.domain.StudentDayHistory
import com.school.servicemonitor.ui.components.AttemptLine
import com.school.servicemonitor.ui.components.DatePickerField
import com.school.servicemonitor.ui.components.NoInsets
import com.school.servicemonitor.ui.components.StatusChip
import com.school.servicemonitor.ui.components.formatMinutes
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenStudent: (Long) -> Unit,
    onOpenActivityLog: () -> Unit
) {
    val container = LocalContext.current.appContainer()
    val viewModel: HistoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HistoryViewModel(container.attemptRepository, container.excuseRepository) }
        }
    )
    val date by viewModel.date.collectAsStateWithLifecycle()
    val days by viewModel.days.collectAsStateWithLifecycle()
    val excuses by viewModel.excuses.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attendance History") },
                actions = { TextButton(onClick = onOpenActivityLog) { Text("Activity log") } }
            )
        },
        contentWindowInsets = NoInsets
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                IconButton(onClick = viewModel::previousDay) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                }
                DatePickerField(
                    label = "Date",
                    date = date,
                    onDateSelected = viewModel::onDateChange,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = viewModel::nextDay) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                if (date != LocalDate.now()) {
                    TextButton(onClick = viewModel::goToToday) { Text("Today") }
                }
            }

            val list = days
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                list.isEmpty() && excuses.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No attempts or excuses were recorded on this date.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                else -> {
                    val s = HistoryBuilder.summarize(list)
                    val activeExcuses = excuses.count { it.excuse.revokedAt == null }
                    Text(
                        "${list.size} student(s)  |  ${s.daysPassed} passed  |  " +
                                "${s.failedAttempts} failed attempt(s)  |  ${s.voidedAttempts} voided  |  " +
                                "$activeExcuses excused  |  ${formatMinutes(s.creditedMinutes)} credited",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(list, key = { "${it.studentId}-${it.date}" }) { day ->
                            DayCard(day, onClick = { onOpenStudent(day.studentId) })
                        }
                        if (excuses.isNotEmpty()) {
                            item(key = "excuse-header") {
                                Text(
                                    "Daily excuses",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            items(excuses, key = { "excuse-${it.excuse.id}" }) { row ->
                                ExcuseCard(row, onClick = { onOpenStudent(row.excuse.studentId) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayCard(day: StudentDayHistory, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(day.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "No. ${day.studentNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutcomeChip(day.outcome)
            }
            day.attempts.forEach { attempt ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                AttemptLine(attempt)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExcuseCard(row: ExcuseHistoryRow, onClick: () -> Unit) {
    val e = row.excuse
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        HistoryBuilder.displayName(row.lastName, row.firstName, row.middleName),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "No. ${row.studentNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (e.revokedAt == null) {
                    StatusChip(
                        "EXCUSED",
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer
                    )
                } else {
                    StatusChip(
                        "REVOKED",
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

@Composable
fun OutcomeChip(outcome: DayOutcome) {
    when (outcome) {
        DayOutcome.PASSED -> StatusChip(
            "Passed",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        DayOutcome.FAILED -> StatusChip(
            "Repeat needed",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        DayOutcome.ALL_VOIDED -> StatusChip(
            "All voided",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}