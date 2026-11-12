package com.school.servicemonitor.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.school.servicemonitor.appContainer
import com.school.servicemonitor.data.entity.ActivityLogRow
import com.school.servicemonitor.domain.HistoryBuilder
import com.school.servicemonitor.ui.components.formatTimestamp

private fun actionLabel(action: String): String = when (action) {
    "ATTEMPT_PASS" -> "PASS recorded"
    "ATTEMPT_FAIL" -> "FAIL recorded"
    "ATTEMPT_VOIDED" -> "Attempt voided"
    "EXCUSE_CREATED" -> "Daily excuse"
    "EXCUSE_REVOKED" -> "Excuse revoked"
    "EXEMPTION_GRANTED" -> "Permanent exemption"
    "REPEAT_CONFIRMED" -> "Repeat confirmed"
    "REPEAT_DISMISSED" -> "Repeat dismissed"
    else -> action.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(
    onBack: () -> Unit,
    onOpenStudent: (Long) -> Unit
) {
    val container = LocalContext.current.appContainer()
    val viewModel: ActivityLogViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ActivityLogViewModel(container.activityLogger) }
        }
    )
    val rows by viewModel.rows.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val list = rows
        when {
            list == null -> Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            list.isEmpty() -> Box(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Nothing has been logged yet.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "Newest first. Shows the last $ACTIVITY_LOG_LIMIT entries. " +
                                "Entries are never edited or deleted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(list, key = { it.log.id }) { row ->
                    LogCard(row, onOpenStudent)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogCard(row: ActivityLogRow, onOpenStudent: (Long) -> Unit) {
    val log = row.log
    val studentId = log.studentId
    val name = if (row.lastName != null && row.firstName != null)
        HistoryBuilder.displayName(row.lastName, row.firstName, row.middleName)
    else null

    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(actionLabel(log.action), style = MaterialTheme.typography.titleSmall)
            name?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(log.details, style = MaterialTheme.typography.bodyMedium)
            Text(
                formatTimestamp(log.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (studentId != null) {
        Card(onClick = { onOpenStudent(studentId) }, modifier = Modifier.fillMaxWidth()) { content() }
    } else {
        Card(modifier = Modifier.fillMaxWidth()) { content() }
    }
}