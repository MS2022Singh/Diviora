package com.diviora.music.ui.screens

import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.diviora.music.container
import com.diviora.music.core.model.MediaKind
import com.diviora.music.playback.PlayerUiState
import com.diviora.music.ui.UiEvents
import com.diviora.music.ui.components.Artwork
import com.diviora.music.ui.components.MediaRow
import com.diviora.music.ui.components.formatDuration
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Now playing. Uses the whole screen: artwork/video grows to the space available, landscape and tablets get a
 * side-by-side layout, and video can go fullscreen (landscape, system bars hidden).
 */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(nav: NavHostController) {
    val c = LocalContext.current.container
    val s by c.player.state.collectAsStateWithLifecycle()
    var showQueue by remember { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    if (fullscreen && s.isVideo) { FullscreenVideo(onExit = { fullscreen = false }); return }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight && maxWidth >= 560.dp
        Column(Modifier.fillMaxSize()) {
            TopBar(s, onClose = { nav.popBackStack() }, onQueue = { showQueue = true }, onStop = { c.player.close(); nav.popBackStack() })
            if (s.mediaId == null) {
                Text("Nothing is playing. Pick something from your library.", Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            if (wide) {
                Row(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { MediaArea(s, onFullscreen = { fullscreen = true }) }
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                        InfoAndControls(s)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    if (s.isVideo) {
                        MediaArea(s, onFullscreen = { fullscreen = true })
                        Spacer(Modifier.height(12.dp))
                        InfoAndControls(s)
                        UpNext(s, Modifier.weight(1f))
                    } else {
                        // Artwork takes all the space that is left, as a centred square.
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Artwork(s.artworkUri, min(maxWidth, maxHeight) - 8.dp, MediaKind.AUDIO, corner = 24.dp)
                        }
                        Spacer(Modifier.height(16.dp))
                        InfoAndControls(s)
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
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

@Composable
private fun TopBar(s: PlayerUiState, onClose: () -> Unit, onQueue: () -> Unit, onStop: () -> Unit) {
    val ctx = LocalContext.current
    val activity = LocalActivity.current as? com.diviora.music.MainActivity
    var more by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "Close player") }
        Spacer(Modifier.weight(1f))
        CastButton()
        IconButton(onClick = { showOutputSwitcher(ctx) }) { Icon(Icons.Filled.Speaker, "Play on (Bluetooth, speaker, headphones)") }
        if (s.isVideo) IconButton(onClick = { if (activity?.enterPip() != true) activity?.explainPipUnavailable() }) {
            Icon(Icons.Filled.PictureInPictureAlt, "Picture-in-picture")
        }
        IconButton(onClick = onQueue) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue") }
        Box {
            IconButton(onClick = { more = true }) { Icon(Icons.Filled.MoreVert, "More") }
            DropdownMenu(more, { more = false }) {
                DropdownMenuItem(text = { Text("Stop and close player") }, leadingIcon = { Icon(Icons.Filled.StopCircle, null) },
                    onClick = { more = false; onStop() })
            }
        }
    }
}

/** Chromecast / Cast devices. Hidden on phones without Google Play services. */
@OptIn(UnstableApi::class)
@Composable
private fun CastButton() {
    val ctx = LocalContext.current
    val available = remember {
        runCatching {
            com.google.android.gms.common.GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx) ==
                com.google.android.gms.common.ConnectionResult.SUCCESS
        }.getOrDefault(false)
    }
    if (available) androidx.media3.cast.MediaRouteButton()
}

/** Android's own "play on" picker: Bluetooth, wired headphones, phone speaker (and Cast devices on Android 14+). */
fun showOutputSwitcher(ctx: android.content.Context) {
    val shown = runCatching { androidx.mediarouter.app.SystemOutputSwitcherDialogController.showDialog(ctx) }.getOrDefault(false)
    if (shown) return
    val panel = Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
        .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { ctx.startActivity(panel) }.isSuccess) return
    runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun MediaArea(s: PlayerUiState, onFullscreen: () -> Unit) {
    if (s.isVideo) {
        val ratio = if (s.videoWidth > 0 && s.videoHeight > 0) s.videoWidth.toFloat() / s.videoHeight else 16f / 9f
        Box(Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.5f, 2.4f)).background(Color.Black)) {
            VideoSurface(Modifier.fillMaxSize(), showControls = false)
            IconButton(onClick = onFullscreen, modifier = Modifier.align(Alignment.BottomEnd)) {
                Icon(Icons.Filled.Fullscreen, "Full screen", tint = Color.White)
            }
        }
    } else {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Artwork(s.artworkUri, min(maxWidth, maxHeight), MediaKind.AUDIO, corner = 24.dp)
        }
    }
}

@Composable
private fun ColumnScope.InfoAndControls(s: PlayerUiState) {
    val c = LocalContext.current.container
    val nav = remember { UiEvents }
    var dragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableFloatStateOf(0f) }
    var speedMenu by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    var addToPlaylist by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val current by remember(s.mediaId) { s.mediaId?.toLongOrNull()?.let { c.db.media().observe(it) } ?: flowOf(null) }
        .collectAsStateWithLifecycle(null)
    if (addToPlaylist) current?.let { m -> AddToPlaylistDialog(listOf(m.id)) { addToPlaylist = false } }

    Text(s.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    s.artist?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    s.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) }
    Spacer(Modifier.height(8.dp))
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
        FilledIconButton(onClick = c.player::togglePlay, modifier = Modifier.size(72.dp)) {
            Icon(if (s.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (s.isPlaying) "Pause" else "Play", Modifier.size(40.dp))
        }
        IconButton(onClick = c.player::next) { Icon(Icons.Filled.SkipNext, "Next", Modifier.size(36.dp)) }
        IconButton(onClick = c.player::cycleRepeat) {
            Icon(if (s.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat",
                tint = if (s.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val small = Modifier.size(44.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { c.player.seekBy(-10_000) }, enabled = s.durationMs > 0, modifier = small) { Icon(Icons.Filled.Replay10, "Back 10 seconds") }
        IconButton(onClick = { current?.let { m -> scope.launch { c.library.setFavorite(m.id, !m.favorite) } } }, modifier = small) {
            Icon(if (current?.favorite == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favourite",
                tint = if (current?.favorite == true) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { addToPlaylist = true }, modifier = small) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist") }
        IconButton(onClick = { s.mediaId?.toLongOrNull()?.let { nav.scheduleItems(listOf(it)) } }, modifier = small) { Icon(Icons.Filled.Alarm, "Schedule this") }
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
    val extra = listOfNotNull(
        s.speed.takeIf { it != 1f }?.let { "Speed ${it}x" },
        s.sleepTimerEndsAtMs?.let { "Sleep at ${formatWhen(it).substringAfter(", ")}" },
    )
    if (extra.isNotEmpty()) Text(extra.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

/** Under a video: the rest of the queue fills the remaining space. */
@Composable
private fun UpNext(s: PlayerUiState, modifier: Modifier) {
    val c = LocalContext.current.container
    val rest = s.queue.drop(s.currentIndex + 1)
    // Nothing queued: fill the space with other videos from the library.
    val videos by remember { c.db.media().observeByKind(MediaKind.VIDEO) }.collectAsStateWithLifecycle(emptyList())
    val more = remember(videos, s.mediaId) { videos.filter { it.id.toString() != s.mediaId }.take(30) }
    Column(modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (rest.isNotEmpty()) {
            Text("Up next", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 4.dp))
            LazyColumn {
                items(rest, key = { "${it.index}-${it.mediaId}" }) { q ->
                    MediaRow(q.title, q.artist, null, MediaKind.AUDIO, onClick = { c.player.jumpTo(q.index) })
                }
            }
        } else if (more.isNotEmpty()) {
            Text("More to watch", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 4.dp))
            LazyColumn {
                items(more, key = { it.id }) { m ->
                    MediaRow(m.title, m.artist ?: m.album, m.artworkUri, m.kind, onClick = { c.player.playItems(more, more.indexOf(m)) })
                }
            }
        }
    }
}

/** Fullscreen video: landscape, system bars hidden, standard video controls; Back or ⤡ exits. */
@Composable
private fun FullscreenVideo(onExit: () -> Unit) {
    val activity = LocalActivity.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val oldOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = oldOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    BackHandler(onBack = onExit)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(Modifier.fillMaxSize(), showControls = true)
        IconButton(onClick = onExit, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.Filled.FullscreenExit, "Exit full screen", tint = Color.White)
        }
    }
}

/** Renders video from the session player via the MediaController (works in-app, fullscreen and in picture-in-picture). */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(modifier: Modifier = Modifier, showControls: Boolean = true) {
    val c = LocalContext.current.container
    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING) } },
        update = { it.player = c.player.controller; it.useController = showControls },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}

