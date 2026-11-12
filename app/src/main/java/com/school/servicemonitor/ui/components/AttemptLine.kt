package com.school.servicemonitor.ui.components

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.school.servicemonitor.data.entity.AttemptResultType
import com.school.servicemonitor.data.entity.AttemptState
import com.school.servicemonitor.data.entity.ServiceAttempt
import com.school.servicemonitor.domain.formatClock

@Composable
fun AttemptLine(a: ServiceAttempt, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "#${a.attemptNo}   ${a.startTime.formatClock()} to ${a.endTime.formatClock()}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            when {
                a.recordState == AttemptState.VOIDED -> StatusChip(
                    "VOIDED",
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer
                )
                a.result == AttemptResultType.PASS -> StatusChip(
                    "PASS",
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer
                )
                else -> StatusChip(
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
        a.voidedAt?.let { Text("Voided: ${formatTimestamp(it)}", style = MaterialTheme.typography.bodySmall) }
    }
}