package com.school.servicemonitor.ui.attempts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.school.servicemonitor.data.entity.AttemptResultType
import com.school.servicemonitor.domain.formatClock
import com.school.servicemonitor.ui.components.formatMinutes

@Composable
fun VoidDialog(
    state: VoidUiState,
    onConfirm: (String) -> Unit,
    onClose: () -> Unit
) {
    val a = state.attempt
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!state.saving) onClose() },
        title = { Text("Void attempt #${a.attemptNo}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${if (a.result == AttemptResultType.PASS) "PASS" else "FAIL"}, " +
                            "${a.startTime.formatClock()} to ${a.endTime.formatClock()}, " +
                            "${formatMinutes(a.creditedMinutes)} credited.",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "The attempt stays in the history, marked VOIDED, with its times and result kept. " +
                            if (a.creditedMinutes > 0)
                                "Its ${formatMinutes(a.creditedMinutes)} credit will be taken back, " +
                                        "and the student can be recorded again."
                            else "The student can be recorded again.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (required)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason) },
                enabled = !state.saving,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text(if (state.saving) "Saving..." else "Void attempt") }
        },
        dismissButton = {
            TextButton(onClick = onClose, enabled = !state.saving) { Text("Cancel") }
        }
    )
}