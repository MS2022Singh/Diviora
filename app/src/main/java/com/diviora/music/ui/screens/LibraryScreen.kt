package com.diviora.music.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.diviora.music.container
import com.diviora.music.core.model.MediaKind
import com.diviora.music.core.model.MediaSource
import com.diviora.music.data.db.MediaEntity
import com.diviora.music.data.db.PlaylistEntity
import com.diviora.music.ui.RescanRequest
import com.diviora.music.ui.UiEvents
import com.diviora.music.ui.components.Artwork
import com.diviora.music.ui.components.EmptyState
import com.diviora.music.ui.components.MediaRow
import com.diviora.music.ui.components.formatDuration
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private val libraryTabs = listOf("Online", "Songs", "Videos", "Albums", "Artists", "Streams", "Playlists", "Favourites")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(nav: NavHostController) {
    val c = LocalContext.current.container
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    if (showImport) ImportSheet(onDismiss = { showImport = false })
    val player by c.player.state.collectAsStateWithLifecycle()

    Column {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.weight(1f),
                placeholder = { Text("Search songs, artists, albums, videos") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") } },
            )
            IconButton(onClick = { showImport = true }) { Icon(Icons.Filled.Add, "Import") }
        }
        if (query.isNotBlank()) {
            val results by remember(query) { c.db.media().search(query.trim()) }.collectAsStateWithLifecycle(emptyList())
            MediaList(results, player.mediaId, emptyTitle = "No matches for “$query”")
            return@Column
        }
        PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            libraryTabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i; group = null }, text = { Text(t) }) }
        }
        val dao = c.db.media()
        when (tab - 1) {
            -1 -> {
                val l by remember { c.catalogs.observeItems() }.collectAsStateWithLifecycle(emptyList())
                if (l.isEmpty()) EmptyState(Icons.Filled.Link, "No online content yet",
                    "Content from the developer's server and sources you add in Settings → Content sources appears here.")
                else MediaList(l, player.mediaId, header = true)
            }
            0 -> { val l by remember { dao.observeByKind(MediaKind.AUDIO) }.collectAsStateWithLifecycle(emptyList()); MediaList(l, player.mediaId, header = true) }
            1 -> { val l by remember { dao.observeByKind(MediaKind.VIDEO) }.collectAsStateWithLifecycle(emptyList()); MediaList(l, player.mediaId, header = true) }
            2, 3 -> {
                val g = group
                if (g == null) {
                    val groups by remember(tab) { if (tab == 3) dao.observeAlbums() else dao.observeArtists() }.collectAsStateWithLifecycle(emptyList())
                    if (groups.isEmpty()) EmptyState(Icons.Filled.MusicNote, "Nothing here yet", "Scan or import music with tags.")
                    LazyColumn {
                        items(groups, key = { it.name }) { gc ->
                            MediaRow(gc.name, "${gc.count} songs", gc.artworkUri, MediaKind.AUDIO, onClick = { group = gc.name })
                        }
                    }
                } else {
                    val l by remember(g, tab) { if (tab == 3) dao.observeAlbum(g) else dao.observeArtist(g) }.collectAsStateWithLifecycle(emptyList())
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { group = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                            Text(g, style = MaterialTheme.typography.titleMedium)
                        }
                        MediaList(l, player.mediaId, header = true)
                    }
                }
            }
            4 -> {
                val l by remember { dao.observeByKind(MediaKind.STREAM) }.collectAsStateWithLifecycle(emptyList())
                if (l.isEmpty()) EmptyState(Icons.Filled.Link, "No streams yet", "Add an internet radio, HLS/DASH or direct audio/video URL.") {
                    FilledTonalButton(onClick = { showImport = true }) { Text("Add stream URL") }
                } else MediaList(l, player.mediaId)
            }
            5 -> PlaylistsTab(nav)
            6 -> { val l by remember { dao.observeFavorites() }.collectAsStateWithLifecycle(emptyList()); MediaList(l, player.mediaId, header = true, emptyTitle = "No favourites yet") }
        }
    }
}

@Composable
fun MediaList(items: List<MediaEntity>, playingId: String?, header: Boolean = false, emptyTitle: String = "Nothing here yet", playlistId: Long? = null) {
    val c = LocalContext.current.container
    if (items.isEmpty()) { EmptyState(Icons.Filled.MusicNote, emptyTitle, "Use Import (+) or allow media access in Settings."); return }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        if (header) item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { c.player.playItems(items) }) { Icon(Icons.Filled.PlayArrow, null); Text(" Play all") }
                FilledTonalButton(onClick = { c.player.playItems(items.shuffled()) }) { Icon(Icons.Filled.Shuffle, null); Text(" Shuffle") }
                FilledTonalButton(onClick = { UiEvents.scheduleItems(items.take(200).map { it.id }) }) { Icon(Icons.Filled.Alarm, null); Text(" Schedule") }
            }
        }
        items(items.size, key = { items[it].id }) { i ->
            val m = items[i]
            MediaRow(
                m.title, listOfNotNull(m.artist, formatDuration(m.durationMs).takeIf { m.durationMs > 0 }).joinToString(" · ").ifEmpty { m.album },
                m.artworkUri, m.kind, playing = playingId == m.id.toString(),
                onClick = { c.player.playItems(items, i) },
                trailing = { MediaItemMenu(m, playlistId) },
            )
        }
    }
}

@Composable
fun MediaItemMenu(m: MediaEntity, playlistId: Long? = null) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var pickPlaylist by remember { mutableStateOf(false) }
    IconButton(onClick = { scope.launch { c.library.setFavorite(m.id, !m.favorite) } }) {
        Icon(if (m.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favourite",
            tint = if (m.favorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
    IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "More") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text("Schedule…") }, leadingIcon = { Icon(Icons.Filled.Alarm, null) },
            onClick = { open = false; UiEvents.scheduleItems(listOf(m.id)) })
        DropdownMenuItem(text = { Text("Play next") }, onClick = { open = false; c.player.addToQueue(listOf(m), next = true) })
        DropdownMenuItem(text = { Text("Add to queue") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
            onClick = { open = false; c.player.addToQueue(listOf(m)); UiEvents.message("Added to queue") })
        DropdownMenuItem(text = { Text("Add to playlist…") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
            onClick = { open = false; pickPlaylist = true })
        if (playlistId != null) DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = {
            open = false
            scope.launch { val ids = c.db.playlists().itemIds(playlistId); c.db.playlists().replaceItems(playlistId, ids.filter { it != m.id }) }
        })
        if (m.source != MediaSource.MEDIA_STORE) DropdownMenuItem(text = { Text("Remove from library") }, onClick = {
            open = false; scope.launch { c.library.remove(m.id); UiEvents.message("Removed") }
        })
    }
    if (pickPlaylist) AddToPlaylistDialog(listOf(m.id)) { pickPlaylist = false }
}

@Composable
fun AddToPlaylistDialog(mediaIds: List<Long>, onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val playlists by remember { c.db.playlists().observeAll() }.collectAsStateWithLifecycle(emptyList())
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column {
                playlists.forEach { p ->
                    ListItem(headlineContent = { Text(p.name) }, modifier = Modifier.clickable {
                        scope.launch {
                            val ids = c.db.playlists().itemIds(p.id)
                            c.db.playlists().replaceItems(p.id, ids + mediaIds.filter { it !in ids })
                            UiEvents.message("Added to ${p.name}"); onDismiss()
                        }
                    })
                }
                OutlinedTextField(newName, { newName = it }, label = { Text("New playlist") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = newName.isNotBlank(), onClick = {
                scope.launch {
                    val id = c.db.playlists().insert(PlaylistEntity(name = newName.trim()))
                    c.db.playlists().replaceItems(id, mediaIds); UiEvents.message("Created ${newName.trim()}"); onDismiss()
                }
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PlaylistsTab(nav: NavHostController) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val playlists by remember { c.db.playlists().observeAll() }.collectAsStateWithLifecycle(emptyList())
    var create by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    LazyColumn {
        item { ListItem(headlineContent = { Text("New playlist") }, leadingContent = { Icon(Icons.Filled.Add, null) }, modifier = Modifier.clickable { create = true }) }
        items(playlists, key = { it.id }) { p ->
            MediaRow(p.name, "Playlist", null, MediaKind.AUDIO, onClick = { nav.navigate("playlist/${p.id}") })
        }
    }
    if (create) AlertDialog(
        onDismissRequest = { create = false }, title = { Text("New playlist") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            scope.launch { c.db.playlists().insert(PlaylistEntity(name = name.trim())); name = ""; create = false }
        }) { Text("Create") } },
        dismissButton = { TextButton(onClick = { create = false }) { Text("Cancel") } },
    )
}

@Composable
fun PlaylistScreen(nav: NavHostController, id: Long) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val items by remember(id) { c.db.playlists().observeItems(id) }.collectAsStateWithLifecycle(emptyList())
    val playlists by remember { c.db.playlists().observeAll() }.collectAsStateWithLifecycle(emptyList())
    val player by c.player.state.collectAsStateWithLifecycle()
    val p = playlists.firstOrNull { it.id == id }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(p?.name ?: "Playlist", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { scope.launch { c.db.playlists().delete(id); nav.popBackStack() } }) { Text("Delete") }
        }
        MediaList(items, player.mediaId, header = true, emptyTitle = "Empty playlist", playlistId = id)
    }
}

/** Import sheet: files, a folder, a stream URL or pasted M3U/PLS text, plus a MediaStore rescan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var playlistText by remember { mutableStateOf("") }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) scope.launch {
            val r = c.library.importDocuments(uris)
            UiEvents.message("Imported ${r.added} new file(s)" + if (r.failed > 0) ", ${r.failed} failed" else ""); onDismiss()
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) scope.launch {
            UiEvents.message("Importing folder…")
            val r = c.library.importTree(uri)
            UiEvents.message("Folder: ${r.added} new, ${r.updated} already present" + if (r.failed > 0) ", ${r.failed} failed" else ""); onDismiss()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Add to library", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { pickFiles.launch(arrayOf("audio/*", "video/*")) }) { Icon(Icons.Filled.UploadFile, null); Text(" Files") }
                FilledTonalButton(onClick = { pickFolder.launch(null) }) { Icon(Icons.Filled.Folder, null); Text(" Folder") }
                FilledTonalButton(onClick = { RescanRequest.request(); onDismiss() }) { Text("Rescan device") }
            }
            Text("Media link: direct MP3/AAC/MP4/WebM file, HLS .m3u8, DASH .mpd or internet radio. " +
                "YouTube/Spotify page links open in their own app.", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(url, { url = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("https://…") })
            OutlinedTextField(title, { title = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Title (optional)") })
            FilledTonalButton(enabled = url.isNotBlank(), onClick = {
                scope.launch {
                    runCatching { c.library.addStream(url, title) }
                        .onSuccess { UiEvents.message("Stream added"); onDismiss() }
                        .onFailure { com.diviora.music.ui.handleAddStreamFailure(ctx, it) }
                }
            }) { Icon(Icons.Filled.Link, null); Text(" Add stream") }
            Text("Or paste an M3U / PLS playlist", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(playlistText, { playlistText = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5)
            FilledTonalButton(enabled = playlistText.isNotBlank(), onClick = {
                scope.launch { val r = c.library.importPlaylistText(playlistText); UiEvents.message("Added ${r.added} stream(s)"); onDismiss() }
            }) { Text("Import playlist text") }
            Text("Other apps: use Share → DIVIORA Music, or Open with DIVIORA Music.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
