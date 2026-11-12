package com.school.servicemonitor.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.school.servicemonitor.appContainer
import com.school.servicemonitor.backup.BackupRules
import com.school.servicemonitor.backup.restartApp
import com.school.servicemonitor.data.AppDatabase
import com.school.servicemonitor.domain.SettingsValidator
import com.school.servicemonitor.ui.components.NoInsets
import com.school.servicemonitor.ui.components.formatTimestamp
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val container = context.appContainer()
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(container.settingsRepository, container.backupManager) }
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(state.restartRequested) {
        if (state.restartRequested) restartApp(context)
    }

    // The system "save as" and "open" screens. We never ask for storage permission.
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) viewModel.exportTo(uri) }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.stageRestore(uri) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings and Backup") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = NoInsets
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(state.busyLabel, style = MaterialTheme.typography.bodySmall)
            }

            // ---------------- Service rules ----------------
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Service rules", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = state.creditText,
                        onValueChange = viewModel::onCreditChange,
                        label = { Text("Daily credited service (minutes)") },
                        isError = state.errors.credit != null,
                        supportingText = {
                            Text(
                                state.errors.credit
                                    ?: "Credited for one PASS, and the shortest session that can be a PASS. " +
                                    "${SettingsValidator.MIN_CREDIT_MINUTES} to ${SettingsValidator.MAX_CREDIT_MINUTES}. Default 60."
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = state.multiplierText,
                        onValueChange = viewModel::onMultiplierChange,
                        label = { Text("SSG multiplier") },
                        isError = state.errors.multiplier != null,
                        supportingText = {
                            Text(
                                state.errors.multiplier
                                    ?: "SSG members get this many times the standard hours. " +
                                    "${SettingsValidator.MIN_MULTIPLIER} to ${SettingsValidator.MAX_MULTIPLIER}. Default 2."
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("School service days", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsValidator.SELECTABLE_DAYS.forEach { day ->
                            FilterChip(
                                selected = day in state.days,
                                onClick = { viewModel.toggleDay(day) },
                                label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) }
                            )
                        }
                    }
                    state.errors.days?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    Text(
                        "Saturday and Sunday are never service days. For holidays, use Excused on the " +
                                "Dashboard. Changes apply from now on: sanctions already assigned keep the " +
                                "hours saved with them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = viewModel::resetToDefaults, enabled = !state.busy) {
                            Text("Reset to defaults")
                        }
                        Button(
                            onClick = viewModel::saveRules,
                            enabled = state.isDirty && !state.savingRules && !state.busy,
                            modifier = Modifier.weight(1f)
                        ) { Text(if (state.savingRules) "Saving..." else "Save settings") }
                    }
                }
            }

            // ---------------- Backup ----------------
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backup", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Saves a copy of ALL data (students, sanctions, attempts, excuses, settings) " +
                                "to a place you choose, such as Downloads or a USB drive.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Last backup: " + (state.lastBackupAt?.let(::formatTimestamp) ?: "never"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { backupLauncher.launch(viewModel.suggestedBackupName()) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Back up now") }
                    Text(
                        "Tip: back up at the end of each school day, and copy the file off this device " +
                                "now and then. Back up when nobody is recording.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---------------- Restore ----------------
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Restore", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Replaces everything in the app with the data from a backup file. The file is " +
                                "checked first, and you see what it contains before anything changes. " +
                                "The app restarts to finish.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Restore from backup") }
                }
            }

            Text(
                "Database version ${AppDatabase.VERSION}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    state.staged?.let { summary ->
        var understood by remember(summary) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!state.busy) viewModel.cancelRestore() },
            title = { Text("Replace all data?") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "This backup contains ${summary.students} student(s), ${summary.sanctions} " +
                                "sanction(s) and ${summary.attempts} attempt(s) " +
                                "(database version ${summary.version}, ${BackupRules.formatSize(summary.sizeBytes)}).",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Restoring REPLACES everything currently in the app, and this cannot be undone. " +
                                "Anything recorded since this backup was made will be lost. The app will restart.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = { backupLauncher.launch(viewModel.suggestedBackupName()) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Back up current data first") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = understood, onCheckedChange = { understood = it })
                        Text("I understand that all current data will be replaced.")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmRestore,
                    enabled = understood && !state.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(if (state.busy) "Working..." else "Replace data and restart") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelRestore, enabled = !state.busy) { Text("Cancel") }
            }
        )
    }
}