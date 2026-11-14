package com.school.servicemonitor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.school.servicemonitor.ui.theme.AppColors
import java.time.LocalTime

/**
 * Start and end time cards plus the calculated duration.
 * This only records what the operator observed. There is no timer.
 */
@Composable
fun TimeRangeInputField(
    value: TimeRangeInput,
    onValueChange: (TimeRangeInput) -> Unit,
    requiredMinutesForPass: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, AppColors.Border),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(time.formatClock(), style = MaterialTheme.typography.headlineMedium)
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
}

@Composable
private fun NumberDropdown(
    selected: Int,
    options: List<Int>,
    display: (Int) -> String,
    onPick: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
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
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (range) {
            TimeRangeCheck.EndNotAfterStart -> Text(
                "The end time must be later than the start time.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )

            is TimeRangeCheck.Valid -> StatusChip(
                "Recorded: ${formatMinutes(range.durationMinutes)}",
                if (pass == PassCheck.Allowed) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer,
                if (pass == PassCheck.Allowed) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onErrorContainer
            )
        }
        when (pass) {
            PassCheck.Allowed -> Text(
                "PASS is allowed. Only $requiredMinutes minutes are credited, even if the session was longer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            is PassCheck.TooShort -> Text(
                "PASS is not allowed: at least ${pass.requiredMinutes} minutes must be recorded " +
                        "(currently ${pass.recordedMinutes}).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            PassCheck.InvalidRange -> Unit
        }
    }
}