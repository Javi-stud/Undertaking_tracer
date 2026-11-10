package com.school.servicemonitor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.school.servicemonitor.domain.ClockTime
import com.school.servicemonitor.domain.PassCheck
import com.school.servicemonitor.domain.TimeRangeCalculator
import com.school.servicemonitor.domain.TimeRangeCheck
import com.school.servicemonitor.domain.TimeRangeInput
import com.school.servicemonitor.domain.formatClock
import java.time.LocalTime

/**
 * Start and end time fields plus the calculated duration.
 * This only records what the operator observed. There is no timer.
 */
@Composable
fun TimeRangeInputField(
    value: TimeRangeInput,
    onValueChange: (TimeRangeInput) -> Unit,
    requiredMinutesForPass: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeField(
            label = "Start time",
            time = value.start,
            onTimeChange = { onValueChange(value.withStart(it)) }
        )
        TimeField(
            label = "End time",
            time = value.end,
            onTimeChange = { onValueChange(value.withEnd(it)) }
        )
        DurationSummary(value.start, value.end, requiredMinutesForPass)
    }
}

@Composable
fun TimeField(
    label: String,
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier
) {
    val clock = ClockTime.from(time)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NumberDropdown(
                selected = clock.hour12,
                options = (1..12).toList(),
                display = { it.toString() },
                onPick = { onTimeChange(clock.copy(hour12 = it).toLocalTime()) }
            )
            Text(":", style = MaterialTheme.typography.titleLarge)
            NumberDropdown(
                selected = clock.minute,
                options = (0..59).toList(),
                display = { it.toString().padStart(2, '0') },
                onPick = { onTimeChange(clock.copy(minute = it).toLocalTime()) }
            )
            FilterChip(
                selected = !clock.isPm,
                onClick = { onTimeChange(clock.copy(isPm = false).toLocalTime()) },
                label = { Text("AM") }
            )
            FilterChip(
                selected = clock.isPm,
                onClick = { onTimeChange(clock.copy(isPm = true).toLocalTime()) },
                label = { Text("PM") }
            )
        }
    }
}

@Composable
private fun NumberDropdown(
    selected: Int,
    options: List<Int>,
    display: (Int) -> String,
    onPick: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        OutlinedButton(onClick = { expanded = true }) { Text(display(selected)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option)) },
                    onClick = {
                        onPick(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DurationSummary(start: LocalTime, end: LocalTime, requiredMinutes: Int) {
    val range = TimeRangeCalculator.check(start, end)
    val pass = TimeRangeCalculator.passCheck(start, end, requiredMinutes)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            "${start.formatClock()}  to  ${end.formatClock()}",
            style = MaterialTheme.typography.bodyMedium
        )
        when (range) {
            TimeRangeCheck.EndNotAfterStart -> Text(
                "The end time must be later than the start time.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )

            is TimeRangeCheck.Valid -> Text(
                "Recorded duration: ${formatMinutes(range.durationMinutes)}",
                style = MaterialTheme.typography.titleMedium
            )
        }
        when (pass) {
            PassCheck.Allowed -> Text(
                "PASS is allowed. Only $requiredMinutes minutes will be credited, even if the session was longer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )

            is PassCheck.TooShort -> Text(
                "PASS is not allowed: at least ${pass.requiredMinutes} minutes must be recorded " +
                        "(currently ${pass.recordedMinutes}).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            PassCheck.InvalidRange -> Unit   // the message above already explains it
        }
    }
}