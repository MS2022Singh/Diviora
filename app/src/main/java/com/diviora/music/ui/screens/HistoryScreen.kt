package com.diviora.music.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.diviora.music.container
import com.diviora.music.core.model.ExecutionResult
import com.diviora.music.ui.components.EmptyState

@Composable
fun HistoryScreen(nav: NavHostController) {
    val c = LocalContext.current.container
    val list by remember { c.routines.observeExecutions() }.collectAsStateWithLifecycle(emptyList())
    val routines by remember { c.routines.observeRoutines() }.collectAsStateWithLifecycle(emptyList())
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Routine history", style = MaterialTheme.typography.titleLarge)
        }
        if (list.isEmpty()) EmptyState(Icons.Filled.History, "No runs yet", "Every routine run is listed here: played, skipped, failed or missed.")
        LazyColumn {
            items(list, key = { it.id }) { e ->
                val (icon, tint) = when (e.result) {
                    ExecutionResult.PLAYED -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.tertiary
                    ExecutionResult.SKIPPED -> Icons.Filled.SkipNext to MaterialTheme.colorScheme.onSurfaceVariant
                    ExecutionResult.MISSED -> Icons.Filled.Warning to MaterialTheme.colorScheme.secondary
                    ExecutionResult.FAILED -> Icons.Filled.Error to MaterialTheme.colorScheme.error
                }
                ListItem(
                    leadingContent = { Icon(icon, e.result.name, tint = tint) },
                    headlineContent = { Text(routines.firstOrNull { it.id == e.routineId }?.name ?: "Deleted routine") },
                    supportingContent = {
                        Text("${e.result.name.lowercase().replaceFirstChar { it.uppercase() }} · scheduled ${formatWhen(e.occurrenceAtMs)}" +
                            (if (e.itemsQueued > 0) " · ${e.itemsQueued} item(s)" else "") + (e.reason?.let { "\n$it" } ?: ""))
                    },
                )
            }
        }
    }
}
