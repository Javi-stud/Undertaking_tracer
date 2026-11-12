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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.school.servicemonitor.data.entity.DeadlineEventRow
import com.school.servicemonitor.data.entity.RepeatEventStatus
import com.school.servicemonitor.data.repository.DeadlineRepository
import com.school.servicemonitor.ui.components.StatusChip
import com.school.servicemonitor.ui.components.display
import com.school.servicemonitor.ui.components.formatMinutes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class StudentDeadlineViewModel(
    repository: DeadlineRepository,
    studentId: Long
) : ViewModel() {
    val events: StateFlow<List<DeadlineEventRow>> = repository.observeForStudent(studentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Shows missed deadlines and what was decided. Hidden when the student has none. */
@Composable
fun StudentDeadlineSection(studentId: Long) {
    val container = LocalContext.current.appContainer()
    val viewModel: StudentDeadlineViewModel = viewModel(
        factory = viewModelFactory {
            initializer { StudentDeadlineViewModel(container.deadlineRepository, studentId) }
        }
    )
    val events by viewModel.events.collectAsStateWithLifecycle()
    if (events.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Deadlines and repeats", style = MaterialTheme.typography.titleMedium)

            events.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                val e = row.event
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.violationName,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    when (e.status) {
                        RepeatEventStatus.DETECTED -> StatusChip(
                            "Action required",
                            MaterialTheme.colorScheme.errorContainer,
                            MaterialTheme.colorScheme.onErrorContainer
                        )
                        RepeatEventStatus.CONFIRMED -> StatusChip(
                            "Repeat confirmed",
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        RepeatEventStatus.DISMISSED -> StatusChip(
                            "Dismissed",
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Text(
                    "Deadline ${e.triggeringDeadline.display()} passed. " +
                            "Original: ${formatMinutes(row.assignedMinutes)} assigned, " +
                            "${formatMinutes(row.allocatedMinutes)} completed.",
                    style = MaterialTheme.typography.bodyMedium
                )
                when (e.status) {
                    RepeatEventStatus.DETECTED -> Text(
                        "A repeat of ${formatMinutes(row.assignedMinutes)} is proposed. " +
                                "Decide from the banner on the Dashboard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    RepeatEventStatus.CONFIRMED -> Text(
                        "A repeated sanction of ${formatMinutes(row.assignedMinutes)} was created " +
                                "with its own deadline.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    RepeatEventStatus.DISMISSED -> Text(
                        "Reason: ${e.dismissReason.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}