package com.diviora.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.diviora.music.container
import com.diviora.music.data.db.RoutineWithCounts
import com.diviora.music.data.repo.spec
import com.diviora.music.playback.PlaybackService
import com.diviora.music.scheduler.recurrence.ConflictPlanner
import com.diviora.music.ui.UiEvents
import com.diviora.music.ui.components.EmptyState
import com.diviora.music.ui.openRoutine
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun RoutinesScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val c = ctx.container
    val routines by remember { c.routines.observeRoutines() }.collectAsStateWithLifecycle(emptyList())
    val slots by remember { c.routines.observeAllSlots() }.collectAsStateWithLifecycle(emptyList())
    val exact = remember { c.scheduler.canScheduleExact() }
    val enabledIds = routines.filter { it.enabled }.map { it.id }.toSet()
    val conflicts = remember(slots, enabledIds) {
        ConflictPlanner.conflicts(
            slots.filter { it.enabled && it.routineId in enabledIds }.mapNotNull { s -> s.spec()?.let { Triple(s.routineId, s.id, it) } },
            Instant.now(),
        )
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("AutoPlay Routines", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Schedule songs, playlists, videos and streams to play by themselves.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { nav.navigate("history") }) { Icon(Icons.Filled.History, "History") }
                }
            }
            if (!exact) item {
                WarningCard("Exact alarms are off. Routines may start several minutes late.", "Fix") { nav.navigate("settings") }
            }
            if (conflicts.isNotEmpty()) item {
                val names = conflicts.take(1).joinToString { (a, b) ->
                    "${routines.firstOrNull { it.id == a.routineId }?.name} and ${routines.firstOrNull { it.id == b.routineId }?.name} at ${formatWhen(a.at.toEpochMilli())}"
                }
                WarningCard("${conflicts.size} overlapping start(s) in the next 14 days: $names. The later routine follows its conflict rule.", null) {}
            }
            if (routines.isEmpty()) item {
                EmptyState(Icons.Filled.Alarm, "No routines yet",
                    "Tap New routine, pick songs/videos/playlists, then choose your own date and time (once, daily, weekdays, chosen days or monthly). Tip: any song\u2019s ⋮ menu has Schedule…")
            }
            items(routines, key = { it.id }) { r -> RoutineCard(r, nav, slotCount = slots.count { it.routineId == r.id }) }
        }
        ExtendedFloatingActionButton(
            onClick = { nav.openRoutine() }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New routine") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun RoutineCard(r: RoutineWithCounts, nav: NavHostController, slotCount: Int) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { nav.openRoutine(r.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${r.itemCount} item(s) · $slotCount time slot(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    when { !r.enabled -> "Paused"; r.nextTriggerAtMs == null -> "No upcoming time"; else -> "Next: ${formatWhen(r.nextTriggerAtMs)}" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (r.enabled && r.nextTriggerAtMs != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = r.enabled, onCheckedChange = { on -> scope.launch { c.routines.setEnabled(r.id, on) } })
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Routine actions") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Run now") }, onClick = {
                        menu = false
                        scope.launch {
                            val slot = c.db.routines().slots(r.id).firstOrNull()
                            if (slot == null) UiEvents.message("Add a time slot first")
                            else ContextCompat.startForegroundService(ctx, PlaybackService.runSlotIntent(ctx, slot.id, System.currentTimeMillis(), manual = true))
                        }
                    })
                    DropdownMenuItem(text = { Text("Skip next run") }, onClick = { menu = false; scope.launch { c.routines.skipNext(r.id); UiEvents.message("Next run will be skipped") } })
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; nav.openRoutine(r.id) })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; scope.launch { c.routines.duplicate(r.id) } })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; scope.launch { c.routines.delete(r.id); UiEvents.message("Routine deleted") } })
                }
            }
        }
    }
}

@Composable
fun WarningCard(text: String, action: String?, onAction: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(8.dp))
            Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            if (action != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}
