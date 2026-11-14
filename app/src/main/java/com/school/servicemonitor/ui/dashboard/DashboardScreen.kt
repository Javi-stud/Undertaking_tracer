package com.school.servicemonitor.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.school.servicemonitor.appContainer
import com.school.servicemonitor.data.entity.Excuse
import com.school.servicemonitor.data.entity.fullName
import com.school.servicemonitor.domain.DailyStatus
import com.school.servicemonitor.domain.DashboardEntry
import com.school.servicemonitor.ui.components.DatePickerField
import com.school.servicemonitor.ui.components.NoInsets
import com.school.servicemonitor.ui.components.StatusChip
import com.school.servicemonitor.ui.components.display
import com.school.servicemonitor.ui.components.formatMinutes
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val tabs = listOf(
    DailyStatus.PENDING to "Pending",
    DailyStatus.PASSED to "Passed",
    DailyStatus.FAILED_REPEAT to "Repeat",
    DailyStatus.EXCUSED to "Excused"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    onOpenEntry: (Long, LocalDate) -> Unit,
    onOpenActionRequired: () -> Unit
) {
    val container = LocalContext.current.appContainer()
    val viewModel: DashboardViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                DashboardViewModel(
                    container.studentRepository,
                    container.sanctionRepository,
                    container.attemptRepository,
                    container.excuseRepository,
                    container.settingsRepository,
                    container.deadlineRepository
                )
            }
        }
    )

    val pendingRepeats by viewModel.pendingRepeats.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val bulk by viewModel.bulk.collectAsStateWithLifecycle()
    val excuseState by viewModel.excuse.collectAsStateWithLifecycle()
    val revokeState by viewModel.revoke.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val list = state.entriesFor(tab)

    // Only students who still need to pass can be selected for bulk actions.
    val canSelect = state.isServiceDay && !state.date.isAfter(today) &&
            (tab == DailyStatus.PENDING || tab == DailyStatus.FAILED_REPEAT)
    val selectedEntries = if (canSelect) list.filter { it.student.id in selected } else emptyList()
    val selecting = selectedEntries.isNotEmpty()

    // Drop stale ids (for example a student who has just been excused).
    LaunchedEffect(selectedEntries.size, selected.size) {
        if (selectedEntries.isEmpty() && selected.isNotEmpty()) viewModel.clearSelection()
    }
    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionBar(
                    count = selectedEntries.size,
                    onClose = viewModel::clearSelection,
                    onSelectAll = { viewModel.selectAll(list.map { it.student.id }) },
                    onExcuse = { viewModel.startExcuse(selectedEntries) },
                    onPass = { viewModel.startBulkPass(selectedEntries) }
                )
            } else {
                TopAppBar(title = { Text("Dashboard") })
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = NoInsets
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // Date selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                IconButton(onClick = viewModel::previousDay) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                }
                DatePickerField(
                    label = "Date",
                    date = state.date,
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
                    state.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                            if (!state.isServiceDay) " (no service)" else "",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                if (state.date != today) {
                    TextButton(onClick = viewModel::goToToday) { Text("Today") }
                }
            }

            when {
                state.date.isAfter(today) -> Note("Future date: view only.")
                state.date.isBefore(today) -> Note(
                    "Past date: the list uses each student's current outstanding hours, " +
                            "so students who have since finished may not appear."
                )
            }
            if (canSelect && list.isNotEmpty() && !selecting) {
                Note("Tip: long-press a student to select one or several for Pass or Excuse.")
            }

            if (pendingRepeats > 0) {
                RepeatBanner(count = pendingRepeats, onOpen = onOpenActionRequired)
            }



            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEach { (status, label) ->
                    com.school.servicemonitor.ui.components.StatTile(
                        label = label,
                        value = state.entriesFor(status).size,
                        selected = tab == status,
                        onClick = { viewModel.onTabChange(status) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                list.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        emptyMessage(tab, state.isServiceDay),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(list, key = { it.student.id }) { entry ->
                        val id = entry.student.id
                        val excuse = state.excuses[id]
                        EntryCard(
                            entry = entry,
                            date = state.date,
                            excuse = if (entry.status == DailyStatus.EXCUSED) excuse else null,
                            selected = selecting && id in selected,
                            selecting = selecting,
                            onClick = {
                                when {
                                    selecting -> viewModel.toggleSelection(id)
                                    entry.status == DailyStatus.EXCUSED -> Unit   // use Revoke excuse instead
                                    else -> onOpenEntry(id, state.date)
                                }
                            },
                            onLongClick = {
                                if (canSelect) {
                                    if (selecting) viewModel.toggleSelection(id)
                                    else viewModel.startSelection(id)
                                }
                            },
                            onRevoke = {
                                excuse?.let { viewModel.startRevoke(it, entry.student.fullName) }
                            }
                        )
                    }
                }
            }
        }
    }

    bulk?.let { bulkState ->
        BulkPassDialog(
            state = bulkState,
            date = state.date,
            onCommonChange = viewModel::onBulkCommonChange,
            onOverride = viewModel::onBulkOverride,
            onResetOverride = viewModel::onBulkResetOverride,
            onSave = viewModel::saveBulkPass,
            onClose = viewModel::closeBulk
        )
    }

    excuseState?.let {
        ExcuseDialog(
            state = it,
            date = state.date,
            onSave = viewModel::saveExcuse,
            onClose = viewModel::closeExcuse
        )
    }

    revokeState?.let {
        RevokeDialog(
            state = it,
            onConfirm = viewModel::confirmRevoke,
            onClose = viewModel::closeRevoke
        )
    }
}

private fun emptyMessage(tab: DailyStatus, isServiceDay: Boolean): String = when (tab) {
    DailyStatus.PENDING ->
        if (isServiceDay) "No students are pending for this date."
        else "This is not a service day, so no one is required to clean."
    DailyStatus.PASSED -> "No students have passed on this date."
    DailyStatus.FAILED_REPEAT -> "No failed attempts on this date."
    DailyStatus.EXCUSED -> "No excused students on this date."
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onExcuse: () -> Unit,
    onPass: () -> Unit
) {
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Cancel selection")
            }
        },
        actions = {
            TextButton(onClick = onSelectAll) { Text("All") }
            TextButton(onClick = onExcuse) { Text("Excuse") }
            Button(onClick = onPass) { Text("Pass") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryCard(
    entry: DashboardEntry,
    date: LocalDate,
    excuse: Excuse?,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRevoke: () -> Unit
) {
    val s = entry.student
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = null)
                Spacer(Modifier.padding(end = 12.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(s.fullName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "No. ${s.studentNumber}  |  ${s.gradeLevel} - ${s.section}" +
                            if (s.isSsg) "  |  SSG" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Outstanding: ${formatMinutes(entry.outstandingMinutes)} " +
                            "(${entry.activeSanctionCount} active sanction" +
                            if (entry.activeSanctionCount == 1) ")" else "s)",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (entry.status == DailyStatus.PASSED) {
                    Text(
                        "Credited: ${formatMinutes(entry.creditedMinutes)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (entry.attemptCount > 0) {
                    Text(
                        "Attempts today: ${entry.attemptCount}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                entry.nextDeadline?.let { deadline ->
                    val late = deadline.isBefore(date)
                    Text(
                        if (late) "Deadline passed: ${deadline.display()}"
                        else "Next deadline: ${deadline.display()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (late) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (excuse != null) {
                    Text(
                        "Excuse: ${excuse.reason}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = onRevoke) { Text("Revoke excuse") }
                }
            }
            StatusChip(
                text = when (entry.status) {
                    DailyStatus.PENDING -> "Pending"
                    DailyStatus.PASSED -> "Passed"
                    DailyStatus.FAILED_REPEAT -> "Repeat"
                    DailyStatus.EXCUSED -> "Excused"
                },
                containerColor = when (entry.status) {
                    DailyStatus.PASSED -> MaterialTheme.colorScheme.primaryContainer
                    DailyStatus.FAILED_REPEAT -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.secondaryContainer
                },
                contentColor = when (entry.status) {
                    DailyStatus.PASSED -> MaterialTheme.colorScheme.onPrimaryContainer
                    DailyStatus.FAILED_REPEAT -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                }
            )
        }
    }
}
@Composable
private fun RepeatBanner(count: Int, onOpen: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
        ) {
            Text(
                "$count overdue sanction${if (count == 1) "" else "s"} need a repeat decision.",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onOpen) { Text("Review") }
        }
    }
}