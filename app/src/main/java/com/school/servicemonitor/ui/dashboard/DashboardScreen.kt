package com.school.servicemonitor.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onOpenEntry: (Long, LocalDate) -> Unit) {
    val container = LocalContext.current.appContainer()
    val viewModel: DashboardViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                DashboardViewModel(
                    container.studentRepository,
                    container.sanctionRepository,
                    container.attemptRepository,
                    container.settingsRepository
                )
            }
        }
    )

    val state by viewModel.state.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Dashboard") }) },
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

            TabRow(selectedTabIndex = tabs.indexOfFirst { it.first == tab }.coerceAtLeast(0)) {
                tabs.forEach { (status, label) ->
                    Tab(
                        selected = tab == status,
                        onClick = { viewModel.onTabChange(status) },
                        text = {
                            Text(
                                "$label (${state.entriesFor(status).size})",
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            val list = state.entriesFor(tab)
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
                        EntryCard(entry, state.date, onClick = { onOpenEntry(entry.student.id, state.date) })
                    }
                }
            }
        }
    }
}

private fun emptyMessage(tab: DailyStatus, isServiceDay: Boolean): String = when (tab) {
    DailyStatus.PENDING ->
        if (isServiceDay) "No students are pending for this date."
        else "This is not a service day, so no one is required to clean."
    DailyStatus.PASSED -> "No students have passed on this date."
    DailyStatus.FAILED_REPEAT -> "No failed attempts on this date."
    DailyStatus.EXCUSED -> "No excused students. Excuses arrive in Stage 14."
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
private fun EntryCard(entry: DashboardEntry, date: LocalDate, onClick: () -> Unit) {
    val s = entry.student
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
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