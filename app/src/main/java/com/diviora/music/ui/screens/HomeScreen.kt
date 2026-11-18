package com.diviora.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.diviora.music.container
import com.diviora.music.data.db.MediaEntity
import com.diviora.music.ui.components.Artwork
import com.diviora.music.ui.components.EmptyState
import com.diviora.music.ui.components.SectionTitle
import com.diviora.music.ui.openRoutine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalTime

@Composable
fun HomeScreen(nav: NavHostController) {
    val c = LocalContext.current.container
    val recentPlayed by remember { c.db.media().observeRecentlyPlayed(20) }.collectAsStateWithLifecycle(emptyList())
    val recentAdded by remember { c.db.media().observeRecentlyAdded(20) }.collectAsStateWithLifecycle(emptyList())
    val favorites by remember { c.db.media().observeFavorites() }.collectAsStateWithLifecycle(emptyList())
    val count by remember { c.db.media().observeCount() }.collectAsStateWithLifecycle(0)
    val next by remember { c.routines.observeNextAlarm() }.collectAsStateWithLifecycle(null)
    val routines by remember { c.routines.observeRoutines() }.collectAsStateWithLifecycle(emptyList())
    var showImport by rememberSaveable { mutableStateOf(false) }
    if (showImport) ImportSheet(onDismiss = { showImport = false })

    val greeting = when (LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text(greeting, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("$count items in your library", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { nav.navigate("routines") },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Alarm, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Next AutoPlay routine", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        val n = next
                        val name = routines.firstOrNull { it.id == n?.routineId }?.name
                        Text(
                            if (n == null) "No routine scheduled. Tap to create one."
                            else "$name · ${formatWhen(n.triggerAtMs)}",
                            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { showImport = true }, label = { Text("Import") }, leadingIcon = { Icon(Icons.Filled.UploadFile, null) })
                AssistChip(onClick = { showImport = true }, label = { Text("Add stream") }, leadingIcon = { Icon(Icons.Filled.Link, null) })
                AssistChip(onClick = { nav.openRoutine() }, label = { Text("New routine") }, leadingIcon = { Icon(Icons.Filled.Alarm, null) })
            }
        }
        if (count == 0) item {
            EmptyState(Icons.Filled.LibraryMusic, "Your library is empty",
                "Allow media access in Settings, or import files, a folder or a stream URL.")
        }
        if (recentPlayed.isNotEmpty()) { item { SectionTitle("Recently played") }; item { MediaCarousel(recentPlayed) { i -> c.player.playItems(recentPlayed, i) } } }
        if (favorites.isNotEmpty()) { item { SectionTitle("Favourites") }; item { MediaCarousel(favorites) { i -> c.player.playItems(favorites, i) } } }
        if (recentAdded.isNotEmpty()) { item { SectionTitle("Recently added") }; item { MediaCarousel(recentAdded) { i -> c.player.playItems(recentAdded, i) } } }
    }
}

@Composable
fun MediaCarousel(items: List<MediaEntity>, onPlay: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items.size, key = { items[it].id }) { i ->
            val m = items[i]
            Column(Modifier.width(132.dp).clickable { onPlay(i) }) {
                Artwork(m.artworkUri, 132.dp, m.kind, corner = 14.dp)
                Spacer(Modifier.height(6.dp))
                Text(m.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(m.artist ?: m.album ?: m.kind.name.lowercase(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val whenFmt = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")
fun formatWhen(ms: Long): String = whenFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
