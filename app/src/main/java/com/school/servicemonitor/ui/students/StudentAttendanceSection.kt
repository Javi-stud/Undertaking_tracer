package com.school.servicemonitor.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.school.servicemonitor.appContainer
import com.school.servicemonitor.data.repository.AttemptRepository
import com.school.servicemonitor.domain.HistoryBuilder
import com.school.servicemonitor.domain.StudentDayHistory
import com.school.servicemonitor.ui.components.AttemptLine
import com.school.servicemonitor.ui.components.display
import com.school.servicemonitor.ui.components.formatMinutes
import com.school.servicemonitor.ui.history.OutcomeChip
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class StudentHistoryViewModel(
    attemptRepository: AttemptRepository,
    studentId: Long
) : ViewModel() {
    val days: StateFlow<List<StudentDayHistory>> = attemptRepository
        .observeHistoryForStudent(studentId)
        .map { HistoryBuilder.group(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

private const val PREVIEW_DAYS = 10

@Composable
fun StudentAttendanceSection(studentId: Long) {
    val container = LocalContext.current.appContainer()
    val viewModel: StudentHistoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { StudentHistoryViewModel(container.attemptRepository, studentId) }
        }
    )
    val days by viewModel.days.collectAsStateWithLifecycle()
    var showAll by remember { mutableStateOf(false) }

    val summary = HistoryBuilder.summarize(days)
    val visible = if (showAll) days else days.take(PREVIEW_DAYS)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Attendance history", style = MaterialTheme.typography.titleMedium)

            if (days.isEmpty()) {
                Text(
                    "No cleaning attempts recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "${summary.daysPassed} day(s) passed  |  ${summary.failedAttempts} failed attempt(s)  |  " +
                            "${summary.voidedAttempts} voided",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Total credited: ${formatMinutes(summary.creditedMinutes)}",
                    style = MaterialTheme.typography.bodyMedium
                )

                visible.forEach { day ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            day.date.display(),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        OutcomeChip(day.outcome)
                    }
                    day.attempts.forEach { attempt ->
                        AttemptLine(attempt, modifier = Modifier.padding(top = 6.dp))
                    }
                }

                if (days.size > PREVIEW_DAYS) {
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(if (showAll) "Show fewer" else "Show all ${days.size} days")
                    }
                }
            }
        }
    }
}