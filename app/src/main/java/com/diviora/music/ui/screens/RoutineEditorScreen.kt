package com.diviora.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.diviora.music.container
import com.diviora.music.core.model.AudioStream
import com.diviora.music.core.model.ConflictPolicy
import com.diviora.music.core.model.RoutineRules
import com.diviora.music.data.db.MediaEntity
import com.diviora.music.data.repo.RoutineDraft
import com.diviora.music.scheduler.recurrence.RecurrenceEngine
import com.diviora.music.scheduler.recurrence.Repeat
import com.diviora.music.scheduler.recurrence.SlotSpec
import com.diviora.music.ui.UiEvents
import com.diviora.music.ui.components.kindIcon
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun describe(spec: SlotSpec): String {
    val t = "%02d:%02d".format(spec.minuteOfDay / 60, spec.minuteOfDay % 60)
    val r = when (val rep = spec.repeat) {
        Repeat.Once -> "once on ${LocalDate.ofEpochDay(spec.startEpochDay)}"
        is Repeat.Daily -> if (rep.interval == 1) "every day" else "every ${rep.interval} days"
        is Repeat.Weekly -> when (rep.days) {
            setOf(1, 2, 3, 4, 5) -> "weekdays"
            setOf(6, 7) -> "weekends"
            else -> rep.days.sorted().joinToString { dayNames[it - 1] }
        } + if (rep.interval > 1) " every ${rep.interval} weeks" else ""
        is Repeat.Monthly -> "monthly on day ${rep.dayOfMonth}"
    }
    val end = buildList {
        spec.endEpochDay?.let { add("until ${LocalDate.ofEpochDay(it)}") }
        spec.maxOccurrences?.let { add("$it times") }
    }.joinToString(", ")
    return "$t · $r" + if (end.isNotEmpty()) " · $end" else ""
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutineEditorScreen(nav: NavHostController, routineId: Long) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(routineId == 0L) }
    var name by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }
    val mediaIds = remember { mutableStateListOf<Long>() }
    val slots = remember { mutableStateListOf<SlotSpec>() }
    var rules by remember { mutableStateOf(RoutineRules()) }
    var media by remember { mutableStateOf<Map<Long, MediaEntity>>(emptyMap()) }
    var pickItems by remember { mutableStateOf(false) }
    var editSlot by remember { mutableStateOf<Int?>(null) } // -1 = new

    LaunchedEffect(routineId) {
        if (routineId != 0L) c.routines.load(routineId)?.let { d ->
            name = d.name; enabled = d.enabled; mediaIds.addAll(d.mediaIds); slots.addAll(d.slots); rules = d.rules
        }
        loaded = true
    }
    LaunchedEffect(mediaIds.toList()) { media = c.db.media().getAll(mediaIds.toList()).associateBy { it.id } }
    if (!loaded) return

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(if (routineId == 0L) "New routine" else "Edit routine", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                when {
                    name.isBlank() -> UiEvents.message("Give the routine a name")
                    mediaIds.isEmpty() -> UiEvents.message("Add at least one item to play")
                    slots.isEmpty() -> UiEvents.message("Add at least one time")
                    else -> scope.launch {
                        runCatching { c.routines.save(RoutineDraft(routineId, name, enabled, mediaIds.toList(), slots.toList(), rules)) }
                            .onSuccess { UiEvents.message("Routine saved"); nav.popBackStack() }
                            .onFailure { UiEvents.message("Save failed: ${it.message}") }
                    }
                }
            }) { Text("Save") }
        }
        OutlinedTextField(name, { name = it }, label = { Text("Routine name") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), placeholder = { Text("e.g. Morning playlist") })
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Enabled", Modifier.weight(1f)); Switch(enabled, { enabled = it })
        }

        Header("What to play (${mediaIds.size})") { TextButton(onClick = { pickItems = true }) { Icon(Icons.Filled.Add, null); Text(" Add") } }
        mediaIds.forEachIndexed { i, id ->
            val m = media[id]
            ListItem(
                leadingContent = { Icon(kindIcon(m?.kind), null) },
                headlineContent = { Text(m?.title ?: "Missing item", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { m?.artist?.let { Text(it, maxLines = 1) } },
                trailingContent = {
                    Row {
                        IconButton(enabled = i > 0, onClick = { mediaIds.add(i - 1, mediaIds.removeAt(i)) }) { Icon(Icons.Filled.ArrowUpward, "Move up") }
                        IconButton(enabled = i < mediaIds.lastIndex, onClick = { mediaIds.add(i + 1, mediaIds.removeAt(i)) }) { Icon(Icons.Filled.ArrowDownward, "Move down") }
                        IconButton(onClick = { mediaIds.removeAt(i) }) { Icon(Icons.Filled.Close, "Remove") }
                    }
                },
            )
        }

        Header("When to play (${slots.size})") { TextButton(onClick = { editSlot = -1 }) { Icon(Icons.Filled.Add, null); Text(" Add time") } }
        if (slots.isEmpty()) Text("Add one or more times. Each time can repeat on its own schedule.",
            Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        slots.forEachIndexed { i, s ->
            ListItem(
                headlineContent = { Text(describe(s)) },
                modifier = Modifier.clickable { editSlot = i },
                trailingContent = { IconButton(onClick = { slots.removeAt(i) }) { Icon(Icons.Filled.Delete, "Delete time") } },
            )
        }
        if (slots.isNotEmpty()) {
            val now = Instant.now()
            val upcoming = slots.flatMap { RecurrenceEngine.upcoming(it, now, 5) }.sorted().take(5)
            Text("Next runs: " + if (upcoming.isEmpty()) "none (all times are in the past)" else upcoming.joinToString(" · ") { formatWhen(it.toEpochMilli()) },
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        Header("Playback rules") {}
        RulesEditor(rules) { rules = it }
    }

    if (pickItems) ItemPickerDialog(initial = mediaIds.toSet(), onDismiss = { pickItems = false }) { ids ->
        ids.filter { it !in mediaIds }.forEach { mediaIds += it }; pickItems = false
    }
    editSlot?.let { idx ->
        SlotDialog(initial = slots.getOrNull(idx), onDismiss = { editSlot = null }) { spec ->
            if (idx >= 0) slots[idx] = spec else slots += spec
            editSlot = null
        }
    }
}

@Composable
private fun Header(text: String, action: @Composable () -> Unit) {
    HorizontalDivider(Modifier.padding(top = 12.dp))
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); action()
    }
}

@Composable
private fun RulesEditor(rules: RoutineRules, onChange: (RoutineRules) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ToggleRow("Shuffle", rules.shuffle) { onChange(rules.copy(shuffle = it)) }
        ToggleRow("Repeat the queue", rules.repeatQueue) { onChange(rules.copy(repeatQueue = it)) }
        ToggleRow("Set volume", rules.volumePercent != null) { onChange(rules.copy(volumePercent = if (it) 60 else null)) }
        rules.volumePercent?.let { v ->
            Text("Volume $v%", style = MaterialTheme.typography.bodySmall)
            Slider(v / 100f, { onChange(rules.copy(volumePercent = (it * 100).toInt())) })
        }
        NumberField("Fade in (seconds, 0 = off)", rules.fadeInSeconds) { onChange(rules.copy(fadeInSeconds = (it ?: 0).coerceIn(0, 120))) }
        NumberField("Stop after (minutes, empty = end of queue)", rules.stopAfterMinutes) { onChange(rules.copy(stopAfterMinutes = it?.coerceIn(1, 24 * 60))) }
        NumberField("Still play if late by up to (minutes)", rules.lateGraceMinutes) { onChange(rules.copy(lateGraceMinutes = (it ?: 0).coerceIn(0, 720))) }
        Choice("If something is already playing", rules.conflictPolicy, ConflictPolicy.entries, {
            when (it) { ConflictPolicy.REPLACE -> "Replace it"; ConflictPolicy.APPEND -> "Add to the queue"; ConflictPolicy.SKIP_IF_PLAYING -> "Skip this run" }
        }) { onChange(rules.copy(conflictPolicy = it)) }
        Choice("Audio channel", rules.audioStream, AudioStream.entries, {
            when (it) { AudioStream.MEDIA -> "Media volume"; AudioStream.ALARM -> "Alarm volume (plays in Do Not Disturb)" }
        }) { onChange(rules.copy(audioStream = it)) }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(value, onChange) }
}

@Composable
private fun NumberField(label: String, value: Int?, onChange: (Int?) -> Unit) {
    var text by remember(value) { mutableStateOf(value?.toString() ?: "") }
    OutlinedTextField(text, { t -> text = t.filter(Char::isDigit).take(4); onChange(text.toIntOrNull()) }, label = { Text(label) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
}

@Composable
private fun <T> Choice(label: String, value: T, options: List<T>, text: (T) -> String, onChange: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { open = true }) { Text(text(value)) }
        DropdownMenu(open, { open = false }) { options.forEach { o -> DropdownMenuItem(text = { Text(text(o)) }, onClick = { onChange(o); open = false }) } }
    }
}

/** Multi-select picker over the whole library and playlists (a playlist expands into its items). */
@Composable
private fun ItemPickerDialog(initial: Set<Long>, onDismiss: () -> Unit, onConfirm: (List<Long>) -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val all by remember(query) { if (query.isBlank()) c.db.media().observeAll() else c.db.media().search(query.trim()) }.collectAsStateWithLifecycle(emptyList())
    val playlists by remember { c.db.playlists().observeAll() }.collectAsStateWithLifecycle(emptyList())
    val picked = remember { mutableStateListOf<Long>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose what to play") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Search") }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 420.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    if (query.isBlank() && playlists.isNotEmpty()) {
                        item { Text("Playlists", style = MaterialTheme.typography.labelLarge) }
                        items(playlists, key = { "p${it.id}" }) { p ->
                            ListItem(headlineContent = { Text(p.name) }, supportingContent = { Text("Add all items") },
                                modifier = Modifier.clickable { scope.launch { c.db.playlists().itemIds(p.id).forEach { if (it !in picked) picked += it }; UiEvents.message("Added ${p.name}") } })
                        }
                        item { Text("Library", style = MaterialTheme.typography.labelLarge) }
                    }
                    items(all, key = { it.id }) { m ->
                        val checked = m.id in picked || m.id in initial
                        Row(Modifier.fillMaxWidth().clickable(enabled = m.id !in initial) { if (m.id in picked) picked -= m.id else picked += m.id },
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked, null, enabled = m.id !in initial)
                            Icon(kindIcon(m.kind), null)
                            Text("  " + m.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(picked.toList()) }) { Text("Add ${picked.size}") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private enum class RepeatKind { ONCE, DAILY, WEEKDAYS, WEEKLY, MONTHLY }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SlotDialog(initial: SlotSpec?, onDismiss: () -> Unit, onSave: (SlotSpec) -> Unit) {
    val nowTime = java.time.LocalTime.now().plusMinutes(2)
    val time = rememberTimePickerState(initial?.minuteOfDay?.div(60) ?: nowTime.hour, initial?.minuteOfDay?.rem(60) ?: nowTime.minute, is24Hour = true)
    var kind by remember {
        mutableStateOf(when (val r = initial?.repeat) {
            null, is Repeat.Daily -> if (initial == null) RepeatKind.ONCE else RepeatKind.DAILY
            Repeat.Once -> RepeatKind.ONCE
            is Repeat.Weekly -> if (r.days == setOf(1, 2, 3, 4, 5) && r.interval == 1) RepeatKind.WEEKDAYS else RepeatKind.WEEKLY
            is Repeat.Monthly -> RepeatKind.MONTHLY
        })
    }
    var startDay by remember { mutableStateOf(initial?.startEpochDay ?: LocalDate.now().toEpochDay()) }
    val days = remember { mutableStateListOf<Int>().apply { addAll((initial?.repeat as? Repeat.Weekly)?.days ?: setOf(LocalDate.now().dayOfWeek.value)) } }
    var interval by remember { mutableStateOf(((initial?.repeat as? Repeat.Daily)?.interval ?: (initial?.repeat as? Repeat.Weekly)?.interval ?: 1).toString()) }
    var dom by remember { mutableStateOf(((initial?.repeat as? Repeat.Monthly)?.dayOfMonth ?: LocalDate.now().dayOfMonth).toString()) }
    var count by remember { mutableStateOf(initial?.maxOccurrences?.toString() ?: "") }
    var pickDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add time" else "Edit time") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TimePicker(time)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RepeatKind.entries.forEach { k ->
                        FilterChip(kind == k, { kind = k }, label = { Text(k.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
                OutlinedButton(onClick = { pickDate = true }) {
                    Text((if (kind == RepeatKind.ONCE) "Date: " else "Starting: ") + LocalDate.ofEpochDay(startDay))
                }
                when (kind) {
                    RepeatKind.DAILY -> OutlinedTextField(interval, { interval = it.filter(Char::isDigit).take(3) }, label = { Text("Every N days") }, singleLine = true)
                    RepeatKind.WEEKLY -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            dayNames.forEachIndexed { i, d ->
                                FilterChip((i + 1) in days, { if ((i + 1) in days) days.remove(i + 1) else days.add(i + 1) }, label = { Text(d) })
                            }
                        }
                        OutlinedTextField(interval, { interval = it.filter(Char::isDigit).take(2) }, label = { Text("Every N weeks") }, singleLine = true)
                    }
                    RepeatKind.MONTHLY -> OutlinedTextField(dom, { dom = it.filter(Char::isDigit).take(2) }, label = { Text("Day of month (1–31)") }, singleLine = true)
                    else -> Unit
                }
                if (kind != RepeatKind.ONCE) OutlinedTextField(count, { count = it.filter(Char::isDigit).take(4) },
                    label = { Text("Stop after N runs (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val repeat = when (kind) {
                    RepeatKind.ONCE -> Repeat.Once
                    RepeatKind.DAILY -> Repeat.Daily((interval.toIntOrNull() ?: 1).coerceAtLeast(1))
                    RepeatKind.WEEKDAYS -> Repeat.Weekly(setOf(1, 2, 3, 4, 5))
                    RepeatKind.WEEKLY -> Repeat.Weekly(days.toSet().ifEmpty { setOf(LocalDate.now().dayOfWeek.value) }, (interval.toIntOrNull() ?: 1).coerceAtLeast(1))
                    RepeatKind.MONTHLY -> Repeat.Monthly((dom.toIntOrNull() ?: 1).coerceIn(1, 31))
                }
                onSave(SlotSpec(time.hour * 60 + time.minute, repeat, startDay,
                    maxOccurrences = if (kind == RepeatKind.ONCE) null else count.toIntOrNull()?.takeIf { it > 0 },
                    excludedEpochDays = initial?.excludedEpochDays ?: emptySet(), zoneId = initial?.zoneId))
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = LocalDate.ofEpochDay(startDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { pickDate = false }, confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { startDay = it / 86_400_000L }; pickDate = false }) { Text("OK") }
        }) { DatePicker(state) }
    }
}
