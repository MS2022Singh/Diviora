package com.diviora.music.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diviora.music.core.catalog.PlatformLinkException
import com.diviora.music.core.model.ConflictPolicy
import com.diviora.music.core.model.ExecutionResult
import com.diviora.music.core.model.MediaKind
import com.diviora.music.core.model.MediaSource
import com.diviora.music.core.model.RoutineRules
import com.diviora.music.scheduler.recurrence.RecurrenceEngine
import com.diviora.music.scheduler.recurrence.Repeat
import com.diviora.music.scheduler.recurrence.SlotSpec
import javafx.embed.swing.JFXPanel
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.media.MediaView
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.JFileChooser

class AppServices(val store: Store, val library: Library, val player: Player, val scheduler: Scheduler) {
    var message by mutableStateOf<String?>(null)
    var update by mutableStateOf<com.diviora.music.core.catalog.UpdateInfo?>(null)
    /** Picture-in-picture: a small always-on-top video window. */
    var pip by mutableStateOf(false)
}

private val Violet = Color(0xFF8B5CF6)
private val Rose = Color(0xFFF472B6)
val DivioraColors = darkColorScheme(
    primary = Color(0xFFC4B5FD), onPrimary = Color(0xFF2E1065), primaryContainer = Color(0xFF4C1D95), onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = Rose, background = Color(0xFF0E0B14), surface = Color(0xFF0E0B14), surfaceVariant = Color(0xFF221B2E),
    surfaceContainer = Color(0xFF17121F), surfaceContainerHigh = Color(0xFF221B2E), onSurface = Color(0xFFF5F3FF), onBackground = Color(0xFFF5F3FF),
    tertiary = Color(0xFF5EEAD4),
)
private val whenFmt = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")
fun fmtWhen(ms: Long): String = whenFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
fun fmtDur(ms: Long): String { if (ms <= 0) return "--:--"; val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

private enum class Screen(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home), LIBRARY("Library", Icons.Filled.LibraryMusic), ROUTINES("Routines", Icons.Filled.Schedule),
    HISTORY("History", Icons.Filled.History), SETTINGS("Settings", Icons.Filled.Settings)
}

@Composable
fun DivioraDesktopApp(s: AppServices, frame: () -> Frame?) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var editing by remember { mutableStateOf<DRoutine?>(null) }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column {
            Row(Modifier.weight(1f)) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Violet, Rose))), contentAlignment = Alignment.Center) {
                        Text("D", fontWeight = FontWeight.Black, color = Color.White)
                    }
                    Spacer(Modifier.height(12.dp))
                    Screen.entries.forEach { sc ->
                        NavigationRailItem(selected = screen == sc, onClick = { screen = sc }, icon = { Icon(sc.icon, null) }, label = { Text(sc.label) })
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
                    when (screen) {
                        Screen.HOME -> HomeView(s) { editing = it }
                        Screen.LIBRARY -> LibraryView(s, frame) { editing = it }
                        Screen.ROUTINES -> RoutinesView(s) { editing = it }
                        Screen.HISTORY -> HistoryView(s)
                        Screen.SETTINGS -> SettingsView(s)
                    }
                }
            }
            s.update?.let { u ->
                Surface(color = MaterialTheme.colorScheme.tertiary) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("DIVIORA Music ${u.versionName} is available. Download it, then run the installer (your library is kept).",
                            Modifier.weight(1f), color = Color(0xFF042F2E))
                        Button(onClick = { runCatching { Desktop.getDesktop().browse(URI(u.desktopUrlFor(System.getProperty("os.name")) ?: u.page ?: "")) } }) { Text("Download update") }
                        IconButton(onClick = { s.update = null }) { Icon(Icons.Filled.Close, "Later") }
                    }
                }
            }
            s.message?.let { msg ->
                Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(msg, Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer)
                        IconButton(onClick = { s.message = null }) { Icon(Icons.Filled.Close, "Dismiss") }
                    }
                }
            }
            PlayerBar(s)
        }
    }
    editing?.let { r -> RoutineEditor(s, r, onDone = { editing = null }) }
}

/** A new, unsaved routine pre-filled with [mediaIds]. The user picks the time. */
fun draftRoutine(s: AppServices, mediaIds: List<Long>): DRoutine {
    val first = s.store.value.media.firstOrNull { it.id == mediaIds.firstOrNull() }
    return DRoutine(id = 0, name = first?.title?.let { if (mediaIds.size > 1) "$it + ${mediaIds.size - 1} more" else it } ?: "", mediaIds = mediaIds)
}

@Composable
private fun HomeView(s: AppServices, onEdit: (DRoutine) -> Unit) {
    val data by s.store.data.collectAsState()
    val ps by s.player.state.collectAsState()
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("DIVIORA Music", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("${data.media.count { it.available }} items · ${data.routines.size} routines", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if (ps.current?.kind == MediaKind.VIDEO) {
            if (!s.pip) VideoView(s, Modifier.fillMaxWidth().height(360.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { s.pip = !s.pip }) {
                    Icon(Icons.Filled.PictureInPictureAlt, null); Text(if (s.pip) "  Back to main window" else "  Picture-in-picture")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Next AutoPlay routines", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                val up = remember(data) { s.scheduler.upcoming(5) }
                if (up.isEmpty()) Text("Nothing scheduled. Open Library, pick something, press the alarm icon and choose your time.",
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                up.forEach { u -> Text("${fmtWhen(u.at.toEpochMilli())} · ${u.routine.name}", color = MaterialTheme.colorScheme.onPrimaryContainer) }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onEdit(DRoutine(0, "")) }) { Icon(Icons.Filled.Alarm, null); Text("  New routine") }
            }
        }
        val online = data.media.filter { it.source == MediaSource.CATALOG && it.available }
        if (online.isNotEmpty()) {
            Text("Online", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            online.take(8).forEachIndexed { i, m -> MediaRow(s, m, ps.current?.id == m.id, onPlay = { s.player.playQueue(online, i) }, onSchedule = { onEdit(draftRoutine(s, listOf(m.id))) }) }
        }
    }
}

private enum class LibTab(val label: String) { ALL("All"), MUSIC("Music"), VIDEO("Videos"), STREAMS("Streams"), ONLINE("Online"), FAV("Favourites") }

@Composable
private fun LibraryView(s: AppServices, frame: () -> Frame?, onEdit: (DRoutine) -> Unit) {
    val data by s.store.data.collectAsState()
    val ps by s.player.state.collectAsState()
    var tab by remember { mutableStateOf(LibTab.ALL) }
    var query by remember { mutableStateOf("") }
    var addLink by remember { mutableStateOf(false) }
    val list = data.media.filter { it.available }.filter {
        when (tab) {
            LibTab.ALL -> true; LibTab.MUSIC -> it.kind == MediaKind.AUDIO && it.source != MediaSource.CATALOG
            LibTab.VIDEO -> it.kind == MediaKind.VIDEO; LibTab.STREAMS -> it.kind == MediaKind.STREAM
            LibTab.ONLINE -> it.source == MediaSource.CATALOG; LibTab.FAV -> it.favorite
        }
    }.filter { query.isBlank() || listOfNotNull(it.title, it.artist, it.album).any { f -> f.contains(query, ignoreCase = true) } }
        .sortedBy { it.title.lowercase() }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Search") }, modifier = Modifier.weight(1f))
            Button(onClick = {
                val fd = FileDialog(frame(), "Add music or video files", FileDialog.LOAD).apply { isMultipleMode = true; isVisible = true }
                val n = s.library.addFiles(fd.files.toList()); s.message = "Added $n file(s)"
            }) { Icon(Icons.Filled.Add, null); Text(" Files") }
            OutlinedButton(onClick = {
                val ch = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY; dialogTitle = "Add a folder (all music and videos inside)" }
                if (ch.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) { val n = s.library.addFiles(listOf(ch.selectedFile)); s.message = "Added $n file(s) from ${ch.selectedFile.name}" }
            }) { Text("Folder") }
            OutlinedButton(onClick = { addLink = true }) { Icon(Icons.Filled.Link, null); Text(" Link") }
        }
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LibTab.entries.forEach { t -> FilterChip(tab == t, { tab = t }, label = { Text(t.label) }) }
            Spacer(Modifier.weight(1f))
            if (list.isNotEmpty()) {
                TextButton(onClick = { s.player.playQueue(list) }) { Text("Play all") }
                TextButton(onClick = { onEdit(draftRoutine(s, list.take(200).map { it.id })) }) { Icon(Icons.Filled.Alarm, null); Text(" Schedule all") }
            }
        }
        if (list.isEmpty()) Text("Nothing here yet. Add files, a folder or a link above. Supported on desktop: MP3, M4A/AAC, WAV, AIFF, MP4 and HLS streams.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn {
            items(list, key = { it.id }) { m ->
                MediaRow(s, m, ps.current?.id == m.id, onPlay = { s.player.playQueue(list, list.indexOf(m)) },
                    onSchedule = { onEdit(draftRoutine(s, listOf(m.id))) })
            }
        }
    }
    if (addLink) AddLinkDialog(s) { addLink = false }
}

@Composable
private fun MediaRow(s: AppServices, m: DMedia, playing: Boolean, onPlay: () -> Unit, onSchedule: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onPlay).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(Violet, Rose))), contentAlignment = Alignment.Center) {
            Icon(when (m.kind) { MediaKind.VIDEO -> Icons.Filled.Movie; MediaKind.STREAM -> Icons.Filled.Radio; else -> Icons.Filled.MusicNote }, null, tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal)
            Text(listOfNotNull(m.artist, m.album, fmtDur(m.durationMs).takeIf { m.durationMs > 0 }).joinToString(" · "), maxLines = 1,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onSchedule) { Icon(Icons.Filled.Alarm, "Schedule this") }
        IconButton(onClick = { s.library.setFavorite(m.id, !m.favorite) }) {
            Icon(if (m.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favourite", tint = if (m.favorite) Rose else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (m.source != MediaSource.CATALOG) IconButton(onClick = { s.library.remove(m.id) }) { Icon(Icons.Filled.Delete, "Remove") }
    }
}

@Composable
private fun AddLinkDialog(s: AppServices, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add a media link") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Direct audio/video file link (MP3, M4A, MP4…), HLS .m3u8 stream or internet radio. YouTube/Spotify page links open in your browser instead.")
            OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("https://…") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(title, { title = it }, singleLine = true, label = { Text("Title (optional)") }, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = {
        TextButton(onClick = {
            runCatching { s.library.addStream(url, title) }
                .onSuccess { s.message = "Added ${it.title}"; onDismiss() }
                .onFailure { e ->
                    s.message = e.message
                    if (e is PlatformLinkException) runCatching { Desktop.getDesktop().browse(URI(e.url)) }
                }
        }) { Text("Add") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun RoutinesView(s: AppServices, onEdit: (DRoutine) -> Unit) {
    val data by s.store.data.collectAsState()
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AutoPlay Routines", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("You choose what plays and when: one-time, daily, weekdays, chosen days or monthly, at as many times as you like.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { onEdit(DRoutine(0, "")) }) { Icon(Icons.Filled.Add, null); Text(" New routine") }
        }
        Text("Keep DIVIORA running (it stays in the system tray when you close the window) so routines can play.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 8.dp))
        if (data.routines.isEmpty()) Text("No routines yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        val upcoming = remember(data) { s.scheduler.upcoming(200) }
        data.routines.forEach { r ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onEdit(r) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium)
                        Text("${r.mediaIds.size} item(s) · " + r.slots.joinToString("; ") { describe(it.spec) }, style = MaterialTheme.typography.bodySmall)
                        val next = upcoming.firstOrNull { it.routine.id == r.id }
                        Text(if (!r.enabled) "Paused" else next?.let { "Next: ${fmtWhen(it.at.toEpochMilli())}" } ?: "No upcoming time",
                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = { s.scheduler.runNow(r) }) { Text("Run now") }
                    TextButton(onClick = {
                        val n = upcoming.firstOrNull { it.routine.id == r.id } ?: return@TextButton
                        s.store.update { d -> d.copy(routines = d.routines.map { x -> if (x.id == r.id) x.copy(slots = x.slots.map { if (it.id == n.slot.id) it.copy(skipOccurrenceAtMs = n.at.toEpochMilli()) else it }) else x }) }
                        s.message = "Next run of ${r.name} will be skipped"
                    }) { Text("Skip next") }
                    Switch(r.enabled, { on -> s.store.update { d -> d.copy(routines = d.routines.map { if (it.id == r.id) it.copy(enabled = on) else it }) } })
                    IconButton(onClick = { s.store.update { d -> d.copy(routines = d.routines.filterNot { it.id == r.id }) } }) { Icon(Icons.Filled.Delete, "Delete") }
                }
            }
        }
    }
}

private val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
fun describe(spec: SlotSpec): String {
    val t = "%02d:%02d".format(spec.minuteOfDay / 60, spec.minuteOfDay % 60)
    return t + " · " + when (val r = spec.repeat) {
        Repeat.Once -> "once on ${LocalDate.ofEpochDay(spec.startEpochDay)}"
        is Repeat.Daily -> if (r.interval == 1) "every day" else "every ${r.interval} days"
        is Repeat.Weekly -> if (r.days == setOf(1, 2, 3, 4, 5)) "weekdays" else r.days.sorted().joinToString { dayNames[it - 1] }
        is Repeat.Monthly -> "monthly on day ${r.dayOfMonth}"
    }
}

private enum class RepeatKind { ONCE, DAILY, WEEKDAYS, WEEKLY, MONTHLY }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineEditor(s: AppServices, initial: DRoutine, onDone: () -> Unit) {
    val data by s.store.data.collectAsState()
    var name by remember { mutableStateOf(initial.name) }
    val mediaIds = remember { mutableStateListOf<Long>().apply { addAll(initial.mediaIds) } }
    val slots = remember { mutableStateListOf<DSlot>().apply { addAll(initial.slots) } }
    var rules by remember { mutableStateOf(initial.rules) }
    var picking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Time slot being built
    val soon = LocalTime.now().plusMinutes(5)
    var time by remember { mutableStateOf("%02d:%02d".format(soon.hour, soon.minute)) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var kind by remember { mutableStateOf(RepeatKind.ONCE) }
    val days = remember { mutableStateListOf(LocalDate.now().dayOfWeek.value) }
    var everyN by remember { mutableStateOf("1") }

    AlertDialog(onDismissRequest = onDone, modifier = Modifier.width(760.dp),
        title = { Text(if (initial.id == 0L) "New routine" else "Edit routine") },
        text = {
            Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Routine name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("1. What to play (${mediaIds.size})", style = MaterialTheme.typography.titleMedium)
                mediaIds.toList().forEachIndexed { i, id ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}. " + (data.media.firstOrNull { it.id == id }?.title ?: "Missing item"), Modifier.weight(1f), maxLines = 1)
                        IconButton(onClick = { mediaIds.removeAt(i) }) { Icon(Icons.Filled.Close, "Remove") }
                    }
                }
                OutlinedButton(onClick = { picking = true }) { Icon(Icons.Filled.Add, null); Text(" Add songs / videos / streams") }
                HorizontalDivider()
                Text("2. When to play (${slots.size}): add as many times as you like", style = MaterialTheme.typography.titleMedium)
                slots.toList().forEachIndexed { i, sl ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(describe(sl.spec), Modifier.weight(1f))
                        IconButton(onClick = { slots.removeAt(i) }) { Icon(Icons.Filled.Delete, "Remove time") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(time, { time = it.take(5) }, label = { Text("Time (HH:mm)") }, singleLine = true, modifier = Modifier.width(140.dp))
                    OutlinedTextField(date, { date = it.take(10) }, label = { Text(if (kind == RepeatKind.ONCE) "Date (YYYY-MM-DD)" else "Starting (YYYY-MM-DD)") },
                        singleLine = true, modifier = Modifier.width(220.dp))
                    if (kind == RepeatKind.DAILY) OutlinedTextField(everyN, { everyN = it.filter(Char::isDigit).take(3) }, label = { Text("Every N days") }, singleLine = true, modifier = Modifier.width(140.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RepeatKind.entries.forEach { k -> FilterChip(kind == k, { kind = k }, label = { Text(k.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
                }
                if (kind == RepeatKind.WEEKLY) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayNames.forEachIndexed { i, d -> FilterChip((i + 1) in days, { if ((i + 1) in days) days.remove(i + 1) else days.add(i + 1) }, label = { Text(d) }) }
                }
                OutlinedButton(onClick = {
                    val t = runCatching { LocalTime.parse(time.padStart(5, '0')) }.getOrNull()
                    val d = runCatching { LocalDate.parse(date) }.getOrNull()
                    if (t == null || d == null) { error = "Enter time as HH:mm (e.g. 07:30) and date as YYYY-MM-DD"; return@OutlinedButton }
                    val repeat = when (kind) {
                        RepeatKind.ONCE -> Repeat.Once
                        RepeatKind.DAILY -> Repeat.Daily((everyN.toIntOrNull() ?: 1).coerceAtLeast(1))
                        RepeatKind.WEEKDAYS -> Repeat.Weekly(setOf(1, 2, 3, 4, 5))
                        RepeatKind.WEEKLY -> Repeat.Weekly(days.toSet().ifEmpty { setOf(d.dayOfWeek.value) })
                        RepeatKind.MONTHLY -> Repeat.Monthly(d.dayOfMonth)
                    }
                    val spec = SlotSpec(t.hour * 60 + t.minute, repeat, d.toEpochDay())
                    if (RecurrenceEngine.nextOccurrence(spec, Instant.now()) == null) { error = "That time is in the past"; return@OutlinedButton }
                    slots += DSlot(s.store.newId(), spec); error = null
                }) { Icon(Icons.Filled.Alarm, null); Text(" Add this time") }
                HorizontalDivider()
                Text("3. Options", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(rules.shuffle, { rules = rules.copy(shuffle = it) }); Text("Shuffle") ; Spacer(Modifier.width(16.dp))
                    Checkbox(rules.repeatQueue, { rules = rules.copy(repeatQueue = it) }); Text("Repeat") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(rules.volumePercent != null, { rules = rules.copy(volumePercent = if (it) 60 else null) }); Text("Set volume ")
                    rules.volumePercent?.let { v -> Slider(v / 100f, { rules = rules.copy(volumePercent = (it * 100).toInt()) }, Modifier.width(240.dp)); Text("$v%") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(rules.fadeInSeconds.toString(), { rules = rules.copy(fadeInSeconds = it.filter(Char::isDigit).take(3).toIntOrNull() ?: 0) },
                        label = { Text("Fade in (sec)") }, singleLine = true, modifier = Modifier.width(150.dp))
                    OutlinedTextField(rules.stopAfterMinutes?.toString() ?: "", { rules = rules.copy(stopAfterMinutes = it.filter(Char::isDigit).take(4).toIntOrNull()) },
                        label = { Text("Stop after (min)") }, singleLine = true, modifier = Modifier.width(170.dp))
                }
                Text("If something is already playing:")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ConflictPolicy.entries.forEach { p ->
                        FilterChip(rules.conflictPolicy == p, { rules = rules.copy(conflictPolicy = p) }, label = {
                            Text(when (p) { ConflictPolicy.REPLACE -> "Replace"; ConflictPolicy.APPEND -> "Add to queue"; ConflictPolicy.SKIP_IF_PLAYING -> "Skip" })
                        })
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    name.isBlank() -> error = "Give the routine a name"
                    mediaIds.isEmpty() -> error = "Add at least one item to play"
                    slots.isEmpty() -> error = "Add at least one time (press “Add this time”)"
                    else -> {
                        val r = initial.copy(id = if (initial.id == 0L) s.store.newId() else initial.id, name = name.trim(),
                            mediaIds = mediaIds.toList(), slots = slots.toList(), rules = rules)
                        s.store.update { d -> d.copy(routines = d.routines.filterNot { it.id == r.id } + r) }
                        s.message = "Saved ${r.name}" + (s.scheduler.upcoming(200).firstOrNull { it.routine.id == r.id }?.let { " · next ${fmtWhen(it.at.toEpochMilli())}" } ?: "")
                        onDone()
                    }
                }
            }) { Text("Save routine") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
    if (picking) PickerDialog(s, mediaIds.toSet(), onDismiss = { picking = false }) { ids -> mediaIds += ids.filterNot { it in mediaIds }; picking = false }
}

@Composable
private fun PickerDialog(s: AppServices, existing: Set<Long>, onDismiss: () -> Unit, onPick: (List<Long>) -> Unit) {
    val data by s.store.data.collectAsState()
    var q by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<Long>() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Choose what to play") }, text = {
        Column(Modifier.width(560.dp)) {
            OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Search") }, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(data.media.filter { it.available && (q.isBlank() || it.title.contains(q, true)) }.sortedBy { it.title.lowercase() }, key = { it.id }) { m ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = m.id !in existing) { if (m.id in picked) picked -= m.id else picked += m.id },
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(m.id in picked || m.id in existing, null, enabled = m.id !in existing); Text(m.title, maxLines = 1)
                    }
                }
            }
        }
    }, confirmButton = { Button(onClick = { onPick(picked.toList()) }) { Text("Add ${picked.size}") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun HistoryView(s: AppServices) {
    val data by s.store.data.collectAsState()
    LazyColumn {
        item { Text("Routine history", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (data.executions.isEmpty()) item { Text("No runs yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(data.executions.reversed()) { e ->
            val name = data.routines.firstOrNull { it.id == e.routineId }?.name ?: "Deleted routine"
            val color = when (e.result) { ExecutionResult.PLAYED -> MaterialTheme.colorScheme.tertiary; ExecutionResult.FAILED -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant }
            Column(Modifier.padding(vertical = 6.dp)) {
                Text("$name · ${e.result.name.lowercase()}", color = color, fontWeight = FontWeight.SemiBold)
                Text("Scheduled ${fmtWhen(e.occurrenceAtMs)}" + (if (e.items > 0) " · ${e.items} item(s)" else "") + (e.reason?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SettingsView(s: AppServices) {
    val data by s.store.data.collectAsState()
    val scope = rememberCoroutineScope()
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Keep running in the system tray when the window is closed (needed for routines)", Modifier.weight(1f))
            Switch(data.settings.keepRunningInTray, { on -> s.store.update { it.copy(settings = it.settings.copy(keepRunningInTray = on)) } })
        }
        if (Library.developerMode) DeveloperSources(s, data, scope)
        HorizontalDivider()
        Text("Data folder: ${Store.dataDir()}", style = MaterialTheme.typography.bodySmall)
        Text("DIVIORA Music ${System.getProperty("diviora.version") ?: "dev"} (desktop). Proprietary. Media playback by OpenJFX (GPLv2 + Classpath Exception).",
            style = MaterialTheme.typography.bodySmall)
    }
}

/** Developer-only: content sources (shown only when DIVIORA_DEVELOPER=1). */
@Composable
private fun DeveloperSources(s: AppServices, data: AppData, scope: kotlinx.coroutines.CoroutineScope) {
    var url by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("Developer options · Content sources", style = MaterialTheme.typography.titleMedium)
        Text("Online audio/video published by the developer or any content server (DIVIORA catalog JSON over HTTPS).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        data.catalogs.forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name + if (c.builtIn) " (built-in)" else "")
                    Text("${c.itemCount} items · " + (c.lastSyncMs?.let { "updated ${fmtWhen(it)}" } ?: "not synced") + (c.lastError?.let { " · ⚠ $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall)
                }
                if (!c.builtIn) TextButton(onClick = { s.library.removeSource(c.url) }) { Text("Remove") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("https://…/catalog.json") }, modifier = Modifier.weight(1f))
            Button(onClick = { scope.launch { runCatching { s.library.addSource(url) }.onSuccess { s.message = "Added ${it.name}: ${it.itemCount} items"; url = "" }
                .onFailure { s.message = "Could not add source: ${it.message}" } } }) { Text("Add source") }
            OutlinedButton(onClick = { scope.launch { s.library.syncAll(); s.message = "Content sources refreshed" } }) { Text("Refresh") }
        }
    }
}

@Composable
private fun PlayerBar(s: AppServices) {
    val ps by s.player.state.collectAsState()
    val cur = ps.current ?: return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(280.dp)) {
                    Text(cur.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(ps.error ?: cur.artist ?: cur.album ?: "", maxLines = 1, style = MaterialTheme.typography.bodySmall,
                        color = if (ps.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = s.player::previous) { Icon(Icons.Filled.SkipPrevious, "Previous") }
                IconButton(onClick = s.player::toggle) { Icon(if (ps.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause") }
                IconButton(onClick = s.player::next) { Icon(Icons.Filled.SkipNext, "Next") }
                IconButton(onClick = s.player::toggleRepeat) { Icon(Icons.Filled.Repeat, "Repeat", tint = if (ps.repeat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(fmtDur(ps.positionMs), style = MaterialTheme.typography.labelSmall)
                Slider(if (ps.durationMs > 0) (ps.positionMs.toFloat() / ps.durationMs).coerceIn(0f, 1f) else 0f,
                    { if (ps.durationMs > 0) s.player.seek((it * ps.durationMs).toLong()) }, Modifier.weight(1f).padding(horizontal = 8.dp), enabled = ps.durationMs > 0)
                Text(when { ps.durationMs > 0 -> fmtDur(ps.durationMs); cur.kind == MediaKind.STREAM && ps.error == null -> "LIVE"; else -> "--:--" },
                    style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.AutoMirrored.Filled.VolumeUp, null)
                Slider(ps.volume.toFloat(), { v -> s.player.setVolume(v.toDouble()); s.store.update { it.copy(settings = it.settings.copy(volume = v.toDouble())) } }, Modifier.width(120.dp))
                if (cur.kind == MediaKind.VIDEO) IconButton(onClick = { s.pip = !s.pip }) { Icon(Icons.Filled.PictureInPictureAlt, "Picture-in-picture") }
                IconButton(onClick = { s.player.close(); s.pip = false }) { Icon(Icons.Filled.Close, "Close player") }
            }
        }
    }
}

/** Video area: a JavaFX MediaView inside Swing, inside Compose. Scales to the panel size. */
@Composable
fun VideoView(s: AppServices, modifier: Modifier = Modifier.fillMaxWidth().height(360.dp)) {
    val listener = remember { arrayOfNulls<(javafx.scene.media.MediaPlayer?) -> Unit>(1) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { listener[0]?.let { s.player.playerListeners.remove(it) } } }
    SwingPanel(
        modifier = modifier.background(Color.Black),
        factory = {
            JFXPanel().also { panel ->
                Player.fx {
                    val view = MediaView(s.player.mediaPlayer).apply { isPreserveRatio = true }
                    val root = javafx.scene.layout.StackPane(view).apply { style = "-fx-background-color: black;" }
                    view.fitWidthProperty().bind(root.widthProperty()); view.fitHeightProperty().bind(root.heightProperty())
                    panel.scene = Scene(root, javafx.scene.paint.Color.BLACK)
                    val l: (javafx.scene.media.MediaPlayer?) -> Unit = { mp -> Player.fx { view.mediaPlayer = mp } }
                    listener[0] = l; s.player.playerListeners.add(l)
                }
            }
        },
    )
}
