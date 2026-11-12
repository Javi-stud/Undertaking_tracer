package com.school.servicemonitor.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.school.servicemonitor.ui.components.display
import java.time.LocalDate

/** Asks for one reason, then shows who was excused and who could not be. */
@Composable
fun ExcuseDialog(
    state: ExcuseState,
    date: LocalDate,
    onSave: (String) -> Unit,
    onClose: () -> Unit
) {
    val results = state.results
    if (results != null) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Excuse result") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${results.count { it.saved }} of ${results.size} excused.",
                        style = MaterialTheme.typography.titleSmall
                    )
                    results.forEach {
                        Text(
                            "${it.name}: ${it.message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (it.saved) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = onClose) { Text("Done") } }
        )
        return
    }

    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!state.saving) onClose() },
        title = {
            Text(if (state.rows.size == 1) "Excuse student" else "Excuse ${state.rows.size} students")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(date.display(), style = MaterialTheme.typography.titleSmall)
                Text(
                    state.rows.take(5).joinToString("\n") { it.name } +
                            if (state.rows.size > 5) "\n+ ${state.rows.size - 5} more" else "",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (required)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "A daily excuse deducts no hours and cancels no sanction. " +
                            "The student returns to the schedule on the next school day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(reason) }, enabled = !state.saving) {
                Text(if (state.saving) "Saving..." else "Save excuse")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose, enabled = !state.saving) { Text("Cancel") }
        }
    )
}

@Composable
fun RevokeDialog(
    state: RevokeState,
    onConfirm: (String) -> Unit,
    onClose: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!state.saving) onClose() },
        title = { Text("Revoke excuse?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${state.name} will return to Pending or Repeat. " +
                            "The original excuse stays in the history, marked as revoked.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for revoking (required)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(reason) }, enabled = !state.saving) {
                Text(if (state.saving) "Saving..." else "Revoke excuse")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose, enabled = !state.saving) { Text("Cancel") }
        }
    )
}