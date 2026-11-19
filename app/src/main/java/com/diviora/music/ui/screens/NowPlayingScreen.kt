package com.diviora.music.ui.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.diviora.music.container
import com.diviora.music.core.model.MediaKind
import com.diviora.music.ui.components.Artwork
import com.diviora.music.ui.components.MediaRow
import com.diviora.music.ui.components.formatDuration

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(nav: NavHostController) {
    val c = LocalContext.current.container
    val s by c.player.state.collectAsStateWithLifecycle()
    var showQueue by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableFloatStateOf(0f) }
    var addToPlaylist by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val current by remember(s.mediaId) { s.mediaId?.toLongOrNull()?.let { c.db.media().observe(it) } ?: kotlinx.coroutines.flow.flowOf(null) }
        .collectAsStateWithLifecycle(null)
    if (addToPlaylist) current?.let { m -> AddToPlaylistDialog(listOf(m.id)) { addToPlaylist = false } }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.KeyboardArrowDown, "Close player") }
            Text("Now playing", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = { c.player.close(); nav.popBackStack() }) { Icon(Icons.Filled.StopCircle, "Stop and close player") }
            IconButton(onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue") }
        }
        Spacer(Modifier.height(12.dp))
        if (s.mediaId == null) {
            Text("Nothing is playing. Pick something from your library.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        if (s.isVideo) {
            VideoSurface(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black))
        } else {
            Artwork(s.artworkUri, 300.dp, MediaKind.AUDIO, corner = 24.dp)
        }
        Spacer(Modifier.height(24.dp))
        Text(s.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        s.artist?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        s.error?.let { Text("Playback error: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(16.dp))
        val dur = s.durationMs.coerceAtLeast(1)
        Slider(
            value = if (dragging) dragPos else (s.positionMs.toFloat() / dur).coerceIn(0f, 1f),
            onValueChange = { dragging = true; dragPos = it },
            onValueChangeFinished = { c.player.seekTo((dragPos * dur).toLong()); dragging = false },
            enabled = s.durationMs > 0,
        )
        Box(Modifier.fillMaxWidth()) {
            Text(formatDuration(s.positionMs), style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterStart))
            if (s.queue.size > 1) Text("${s.currentIndex + 1} of ${s.queue.size}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.Center))
            Text(when { s.durationMs > 0 -> formatDuration(s.durationMs); current?.kind == MediaKind.STREAM && s.error == null -> "LIVE"; else -> "--:--" },
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterEnd))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = c.player::toggleShuffle) {
                Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (s.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = c.player::previous) { Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(36.dp)) }
            FilledIconButton(onClick = c.player::togglePlay, modifier = Modifier.size(72.dp), colors = IconButtonDefaults.filledIconButtonColors()) {
                Icon(if (s.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (s.isPlaying) "Pause" else "Play", Modifier.size(40.dp))
            }
            IconButton(onClick = c.player::next) { Icon(Icons.Filled.SkipNext, "Next", Modifier.size(36.dp)) }
            IconButton(onClick = c.player::cycleRepeat) {
                Icon(if (s.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat",
                    tint = if (s.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // One balanced row of secondary controls: back 10 s · favourite · add to playlist · schedule · speed · sleep · forward 10 s
        val small = Modifier.size(44.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { c.player.seekBy(-10_000) }, enabled = s.durationMs > 0, modifier = small) { Icon(Icons.Filled.Replay10, "Back 10 seconds") }
            IconButton(onClick = { current?.let { m -> scope.launch { c.library.setFavorite(m.id, !m.favorite) } } }, modifier = small) {
                Icon(if (current?.favorite == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favourite",
                    tint = if (current?.favorite == true) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { addToPlaylist = true }, modifier = small) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist") }
            IconButton(onClick = { s.mediaId?.toLongOrNull()?.let { com.diviora.music.ui.UiEvents.scheduleItems(listOf(it)) } }, modifier = small) {
                Icon(Icons.Filled.Alarm, "Schedule this")
            }
            Box {
                IconButton(onClick = { speedMenu = true }, modifier = small) {
                    Icon(Icons.Filled.Speed, "Playback speed", tint = if (s.speed != 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(speedMenu, { speedMenu = false }) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp ->
                        DropdownMenuItem(text = { Text("${sp}x" + if (sp == s.speed) "  ✓" else "") }, onClick = { c.player.setSpeed(sp); speedMenu = false })
                    }
                }
            }
            Box {
                IconButton(onClick = { sleepMenu = true }, modifier = small) {
                    Icon(Icons.Filled.Bedtime, "Sleep timer", tint = if (s.sleepTimerEndsAtMs != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(sleepMenu, { sleepMenu = false }) {
                    listOf(5, 15, 30, 45, 60, 90).forEach { m ->
                        DropdownMenuItem(text = { Text("$m minutes") }, onClick = { c.player.setSleepTimer(m); sleepMenu = false })
                    }
                    if (s.sleepTimerEndsAtMs != null) DropdownMenuItem(text = { Text("Turn off timer") }, onClick = { c.player.setSleepTimer(null); sleepMenu = false })
                }
            }
            IconButton(onClick = { c.player.seekBy(10_000) }, enabled = s.durationMs > 0, modifier = small) { Icon(Icons.Filled.Forward10, "Forward 10 seconds") }
        }
        if (s.speed != 1f) Text("Speed ${s.speed}x", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        s.sleepTimerEndsAtMs?.let { Text("Sleep timer ends at ${formatWhen(it).substringAfter(", ")}", style = MaterialTheme.typography.labelMedium) }
    }

    if (showQueue) ModalBottomSheet(onDismissRequest = { showQueue = false }) {
        Text("Up next (${s.queue.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        LazyColumn {
            items(s.queue, key = { "${it.index}-${it.mediaId}" }) { q ->
                MediaRow(q.title, q.artist, null, MediaKind.AUDIO, playing = q.index == s.currentIndex, onClick = { c.player.jumpTo(q.index) }) {
                    IconButton(onClick = { c.player.removeAt(q.index) }) { Icon(Icons.Filled.Close, "Remove") }
                }
            }
        }
    }
}

/** Renders video from the session player via the MediaController (works in-app and in picture-in-picture). */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(modifier: Modifier = Modifier) {
    val c = LocalContext.current.container
    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { useController = true; setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING) } },
        update = { it.player = c.player.controller },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}
