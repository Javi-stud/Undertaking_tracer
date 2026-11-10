package com.school.servicemonitor.ui.sanctions

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import com.school.servicemonitor.data.entity.Violation
import com.school.servicemonitor.ui.components.NoInsets
import com.school.servicemonitor.ui.components.StatusChip
import com.school.servicemonitor.ui.components.formatMinutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViolationListScreen(
    onOpenViolation: (Long) -> Unit,
    onAddViolation: () -> Unit
) {
    val container = LocalContext.current.appContainer()
    val viewModel: ViolationListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ViolationListViewModel(container.violationRepository) }
        }
    )
    val violations by viewModel.violations.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Violations and Sanctions") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddViolation) {
                Icon(Icons.Default.Add, contentDescription = "Add violation")
            }
        },
        contentWindowInsets = NoInsets
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Predefined violations. To give a student a sanction, open the student's profile " +
                        "and tap Assign sanction.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            when (val list = violations) {
                null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                else -> if (list.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No violations yet.\nTap + to add one, for example\n\"Not wearing the school uniform\" (5 hours).",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(list, key = { it.id }) { violation ->
                            ViolationRow(violation, onClick = { onOpenViolation(violation.id) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViolationRow(violation: Violation, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(violation.name, style = MaterialTheme.typography.titleMedium)
                if (violation.description.isNotBlank()) {
                    Text(
                        violation.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Text(
                    "Standard sanction: ${formatMinutes(violation.standardHours * 60)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (!violation.isActive) {
                StatusChip(
                    "Inactive",
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}