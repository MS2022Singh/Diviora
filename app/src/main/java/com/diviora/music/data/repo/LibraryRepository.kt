package com.diviora.music.data.repo

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import com.diviora.music.core.catalog.*
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.room.withTransaction
import com.diviora.music.core.model.MediaKind
import com.diviora.music.core.model.MediaSource
import com.diviora.music.data.db.AppDatabase
import com.diviora.music.data.db.MediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Result of an import operation, shown to the user. */
data class ImportResult(val added: Int, val updated: Int, val failed: Int, val ids: List<Long> = emptyList())

/**
 * Owns the media library: MediaStore scanning, file/folder import ("upload"), share-sheet import and stream URLs.
 * Files stay where they are. Only items shared from other apps (whose access grant is temporary) are copied
 * into app storage, so routines can still play them later.
 */
class LibraryRepository(private val context: Context, private val db: AppDatabase) {
    private val resolver: ContentResolver get() = context.contentResolver
    private val dao get() = db.media()

    suspend fun scanMediaStore(): ImportResult = withContext(Dispatchers.IO) {
        val found = ArrayList<MediaEntity>()
        runCatching { found += queryAudio() }.onFailure { Log.w(TAG, "audio scan failed", it) }
        runCatching { found += queryVideo() }.onFailure { Log.w(TAG, "video scan failed", it) }
        var added = 0
        var updated = 0
        db.withTransaction {
            val existing = dao.mediaStoreUris().associate { it.uri to it.id }
            for (m in found) {
                val id = existing[m.uri]
                if (id == null) {
                    if (dao.insert(m) > 0) added++
                } else {
                    dao.refreshMetadata(id, m.title, m.artist, m.album, m.durationMs, m.artworkUri, m.mimeType, m.sizeBytes)
                    updated++
                }
            }
            val foundUris = found.mapTo(HashSet()) { it.uri }
            val gone = existing.filterKeys { it !in foundUris }.values.toList()
            if (gone.isNotEmpty()) gone.chunked(500).forEach { dao.markUnavailable(it) }
        }
        ImportResult(added, updated, 0)
    }

    private fun queryAudio(): List<MediaEntity> {
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cols = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_ADDED,
        )
        val out = ArrayList<MediaEntity>()
        resolver.query(base, cols, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val albumId = c.getLong(4)
                out += MediaEntity(
                    uri = ContentUris.withAppendedId(base, id).toString(),
                    kind = MediaKind.AUDIO,
                    source = MediaSource.MEDIA_STORE,
                    title = c.getString(1) ?: "Unknown",
                    artist = c.getString(2)?.takeUnless { it == MediaStore.UNKNOWN_STRING },
                    album = c.getString(3),
                    durationMs = c.getLong(5),
                    artworkUri = ContentUris.withAppendedId(ALBUM_ART, albumId).toString(),
                    mimeType = c.getString(6),
                    sizeBytes = c.getLong(7),
                    dateAddedMs = c.getLong(8) * 1000,
                )
            }
        }
        return out
    }

    private fun queryVideo(): List<MediaEntity> {
        val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val cols = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.MIME_TYPE, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        )
        val out = ArrayList<MediaEntity>()
        resolver.query(base, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val uri = ContentUris.withAppendedId(base, c.getLong(0)).toString()
                out += MediaEntity(
                    uri = uri, kind = MediaKind.VIDEO, source = MediaSource.MEDIA_STORE,
                    title = c.getString(1) ?: "Video", album = c.getString(6), durationMs = c.getLong(2),
                    mimeType = c.getString(3), sizeBytes = c.getLong(4), dateAddedMs = c.getLong(5) * 1000,
                )
            }
        }
        return out
    }

    /** Import files picked with the system picker (ACTION_OPEN_DOCUMENT). Access is kept across reboots. */
    suspend fun importDocuments(uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        var added = 0; var failed = 0; val ids = ArrayList<Long>()
        for (uri in uris) {
            runCatching {
                persist(uri)
                val (id, isNew) = upsertFromUri(uri, MediaSource.IMPORTED_FILE)
                ids += id; if (isNew) added++
            }.onFailure { Log.w(TAG, "import failed $uri", it); failed++ }
        }
        ImportResult(added, ids.size - added, failed, ids)
    }

    /** Import every audio/video file under a folder picked with ACTION_OPEN_DOCUMENT_TREE (recursive). */
    suspend fun importTree(treeUri: Uri): ImportResult = withContext(Dispatchers.IO) {
        persist(treeUri)
        val files = ArrayList<Uri>()
        walk(treeUri, DocumentsContract.getTreeDocumentId(treeUri), files, depth = 0)
        var added = 0; var failed = 0; val ids = ArrayList<Long>()
        for (uri in files) {
            runCatching {
                val (id, isNew) = upsertFromUri(uri, MediaSource.IMPORTED_FILE); ids += id; if (isNew) added++
            }.onFailure { failed++ }
        }
        ImportResult(added, ids.size - added, failed, ids)
    }

    private fun walk(tree: Uri, docId: String, out: MutableList<Uri>, depth: Int) {
        if (depth > 12) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE)
        resolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0); val mime = c.getString(1) ?: ""
                when {
                    mime == DocumentsContract.Document.MIME_TYPE_DIR -> walk(tree, id, out, depth + 1)
                    mime.startsWith("audio/") || mime.startsWith("video/") ->
                        out += DocumentsContract.buildDocumentUriUsingTree(tree, id)
                }
            }
        }
    }

    /**
     * Import files shared from another app or opened with "Open with". The grant is temporary, so the file is
     * copied into app storage.
     */
    suspend fun importShared(uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        var added = 0; var failed = 0; val ids = ArrayList<Long>()
        val dir = File(context.filesDir, "imported").apply { mkdirs() }
        for (uri in uris) {
            runCatching {
                val name = displayName(uri) ?: "shared-${UUID.randomUUID()}"
                val safe = name.replace(Regex("[^A-Za-z0-9._ -]"), "_")
                val target = File(dir, "${System.currentTimeMillis()}-$safe")
                resolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
                val mime = resolver.getType(uri)
                val (id, isNew) = upsertFromUri(Uri.fromFile(target), MediaSource.IMPORTED_FILE, fallbackName = name, mimeHint = mime)
                ids += id; if (isNew) added++
            }.onFailure { Log.w(TAG, "shared import failed $uri", it); failed++ }
        }
        ImportResult(added, ids.size - added, failed, ids)
    }

    /** Add a network stream (HTTP(S) audio/video, HLS, DASH, Icecast). */
    suspend fun addStream(url: String, title: String?): Long = withContext(Dispatchers.IO) {
        val clean = url.trim()
        require(clean.startsWith("http://") || clean.startsWith("https://")) { "Only http(s) URLs are supported" }
        PlatformLinks.platformFor(clean)?.let { throw PlatformLinkException(it.name, clean) }
        dao.idForUri(clean) ?: dao.insert(
            MediaEntity(
                uri = clean, kind = MediaKind.STREAM, source = MediaSource.URL,
                title = title?.takeIf { it.isNotBlank() } ?: Uri.parse(clean).lastPathSegment ?: clean,
                artist = Uri.parse(clean).host,
            ),
        )
    }

    /** Parse an .m3u/.m3u8/.pls text and add every http(s) entry as a stream. */
    suspend fun importPlaylistText(text: String): ImportResult {
        // (platform links inside playlists are skipped by addStream)
        val entries = PlaylistParser.parse(text)
        var added = 0; val ids = ArrayList<Long>()
        for (e in entries) runCatching { ids += addStream(e.url, e.title); added++ }
        return ImportResult(added, 0, entries.size - added, ids)
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    /** Remove an item from the library. Files stay on disk, except copies the app made itself. */
    suspend fun remove(id: Long) = withContext(Dispatchers.IO) {
        val m = dao.get(id) ?: return@withContext
        if (m.uri.startsWith("file://") && m.uri.contains("/imported/")) runCatching { File(Uri.parse(m.uri).path!!).delete() }
        dao.delete(id)
    }

    /** True if the item can currently be opened (file present and permission still granted). */
    fun isReadable(m: MediaEntity): Boolean = when (m.source) {
        MediaSource.URL, MediaSource.CATALOG -> true
        else -> runCatching {
            val uri = Uri.parse(m.uri)
            if (uri.scheme == "file") File(uri.path!!).canRead()
            else resolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
    }

    private fun persist(uri: Uri) {
        runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private suspend fun upsertFromUri(
        uri: Uri, source: MediaSource, fallbackName: String? = null, mimeHint: String? = null,
    ): Pair<Long, Boolean> {
        val key = uri.toString()
        dao.idForUri(key)?.let { return it to false }
        val mime = mimeHint ?: resolver.getType(uri) ?: guessMime(key)
        val name = fallbackName ?: displayName(uri) ?: uri.lastPathSegment ?: "Untitled"
        var title = name.substringBeforeLast('.')
        var artist: String? = null; var album: String? = null; var duration = 0L
        runCatching {
            MediaMetadataRetriever().apply {
                setDataSource(context, uri)
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
                artist = extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                album = extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                duration = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                release()
            }
        }
        val kind = if (mime?.startsWith("video/") == true) MediaKind.VIDEO else MediaKind.AUDIO
        val id = dao.insert(
            MediaEntity(uri = key, kind = kind, source = source, title = title, artist = artist, album = album,
                durationMs = duration, mimeType = mime),
        )
        return id to true
    }

    private fun displayName(uri: Uri): String? = runCatching {
        if (uri.scheme == "file") return@runCatching uri.lastPathSegment
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun guessMime(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mpeg"; "m4a", "aac" -> "audio/mp4"; "flac" -> "audio/flac"; "wav" -> "audio/wav"
        "ogg", "opus" -> "audio/ogg"; "mp4", "m4v" -> "video/mp4"; "mkv" -> "video/x-matroska"; "webm" -> "video/webm"
        "3gp" -> "video/3gpp"; else -> null
    }

    companion object {
        private const val TAG = "LibraryRepository"
        private val ALBUM_ART: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}
