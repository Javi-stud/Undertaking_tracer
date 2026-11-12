package com.school.servicemonitor.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.school.servicemonitor.domain.TimeRangeCalculator
import com.school.servicemonitor.domain.TimeRangeCheck
import com.school.servicemonitor.domain.TimeRangeInput
import com.school.servicemonitor.domain.formatClock
import com.school.servicemonitor.ui.components.StatusChip
import com.school.servicemonitor.ui.components.TimeRangeInputField
import com.school.servicemonitor.ui.components.display
import com.school.servicemonitor.ui.components.formatMinutes
import java.time.LocalDate

@Composable
fun BulkPassDialog(
    state: BulkPassState,
    date: LocalDate,
    onCommonChange: (TimeRangeInput) -> Unit,
    onOverride: (Long, TimeRangeInput) -> Unit,
    onResetOverride: (Long) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = { if (!state.saving) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            val results = state.results
            if (results != null) {
                ResultsView(results, onDone = onClose)
            } else {
                ReviewView(state, date, onCommonChange, onOverride, onResetOverride, onSave, onClose)
            }
        }
    }
}

@Composable
private fun ReviewView(
    state: BulkPassState,
    date: LocalDate,
    onCommonChange: (TimeRangeInput) -> Unit,
    onOverride: (Long, TimeRangeInput) -> Unit,
    onResetOverride: (Long) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    val validCount = state.validItems.size
    val problemCount = state.problemCount

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Bulk PASS (${state.rows.size} students)", style = MaterialTheme.typography.titleLarge)
            Text(
                date.display(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Common time for everyone", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Enter the times you observed. There is no timer. Students you adjust " +
                                    "below keep their own time. Each student gets a separate attempt, " +
                                    "and at most ${state.requiredMinutes} minutes are credited each.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TimeRangeInputField(
                            value = state.common,
                            onValueChange = onCommonChange,
                            requiredMinutesForPass = state.requiredMinutes
                        )
                    }
                }
            }

            items(state.rows, key = { it.studentId }) { row ->
                StudentRowCard(
                    row = row,
                    state = state,
                    onOverride = { onOverride(row.studentId, it) },
                    onReset = { onResetOverride(row.studentId) }
                )
            }
        }

        HorizontalDivider()
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (problemCount == 0) "All $validCount students are ready."
                else "$validCount ready, $problemCount with a problem (they will be skipped).",
                style = MaterialTheme.typography.bodyMedium,
                color = if (problemCount == 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onClose,
                    enabled = !state.saving,
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }
                Button(
                    onClick = onSave,
                    enabled = validCount > 0 && !state.saving,
                    modifier = Modifier.weight(2f)
                ) {
                    Text(
                        when {
                            state.saving -> "Saving..."
                            problemCount == 0 -> "Save PASS for all $validCount"
                            else -> "Save $validCount valid, skip $problemCount"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StudentRowCard(
    row: BulkRow,
    state: BulkPassState,
    onOverride: (TimeRangeInput) -> Unit,
    onReset: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val time = state.timeFor(row.studentId)
    val adjusted = row.studentId in state.overrides
    val problem = state.problemFor(row.studentId)
    val duration = (TimeRangeCalculator.check(time.start, time.end) as? TimeRangeCheck.Valid)
        ?.durationMinutes

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "No. ${row.studentNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (problem == null) {
                    StatusChip(
                        "OK",
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    StatusChip(
                        "Problem",
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Text(
                "${time.start.formatClock()} to ${time.end.formatClock()}" +
                        (duration?.let { "  (${formatMinutes(it)})" } ?: "") +
                        if (adjusted) "  - adjusted" else "",
                style = MaterialTheme.typography.bodyMedium
            )
            problem?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide adjustment" else "Adjust time")
                }
                if (adjusted) {
                    TextButton(onClick = onReset) { Text("Use common time") }
                }
            }
            if (expanded) {
                TimeRangeInputField(
                    value = time,
                    onValueChange = onOverride,
                    requiredMinutesForPass = state.requiredMinutes
                )
            }
        }
    }
}

@Composable
private fun ResultsView(results: List<BulkResultLine>, onDone: () -> Unit) {
    val saved = results.count { it.saved }
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Bulk PASS result", style = MaterialTheme.typography.titleLarge)
            Text(
                "$saved of ${results.size} saved.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (saved == results.size) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results) { line ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(line.name, style = MaterialTheme.typography.titleSmall)
                            Text(line.message, style = MaterialTheme.typography.bodySmall)
                        }
                        if (line.saved) {
                            StatusChip(
                                "Saved",
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            StatusChip(
                                "Not saved",
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) { Text("Done") }
    }
}