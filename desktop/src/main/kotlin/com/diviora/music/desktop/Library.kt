package com.diviora.music.desktop

import com.diviora.music.core.catalog.CatalogException
import com.diviora.music.core.catalog.CatalogParser
import com.diviora.music.core.catalog.PlatformLinkException
import com.diviora.music.core.catalog.PlatformLinks
import com.diviora.music.core.catalog.PlaylistParser
import com.diviora.music.core.model.MediaKind
import com.diviora.music.core.model.MediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Desktop library: add files and folders, stream links, playlist files, and developer content catalogs. */
class Library(private val store: Store) {
    private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build()

    fun addFiles(files: List<File>): Int {
        var added = 0
        for (f in files) {
            if (f.isDirectory) { added += addFiles(f.walkTopDown().filter { it.isFile }.toList()); continue }
            val kind = kindOf(f.name) ?: if (f.extension.lowercase() in PLAYLIST_EXT) { added += importPlaylistText(f.readText()); null } else null
            kind ?: continue
            val uri = f.toURI().toString()
            if (store.value.media.any { it.uri == uri }) continue
            val id = store.newId()
            store.update { it.copy(media = it.media + DMedia(id, uri, kind, MediaSource.IMPORTED_FILE, f.nameWithoutExtension, album = f.parentFile?.name)) }
            added++
        }
        return added
    }

    /** Adds a direct media/stream link. Throws [PlatformLinkException] for YouTube/Spotify/… page links. */
    fun addStream(url: String, title: String? = null): DMedia {
        val u = url.trim()
        require(u.startsWith("http://", true) || u.startsWith("https://", true)) { "Only http(s) links are supported" }
        PlatformLinks.platformFor(u)?.let { throw PlatformLinkException(it.name, u) }
        store.value.media.firstOrNull { it.uri == u }?.let { return it }
        val kind = kindOf(URI(u).path ?: "") ?: MediaKind.STREAM
        val m = DMedia(store.newId(), u, kind, MediaSource.URL, title?.takeIf { it.isNotBlank() } ?: URI(u).path?.substringAfterLast('/')?.ifBlank { null } ?: u,
            artist = URI(u).host)
        store.update { it.copy(media = it.media + m) }
        return m
    }

    fun importPlaylistText(text: String): Int = PlaylistParser.parse(text).count { runCatching { addStream(it.url, it.title) }.isSuccess }

    fun setFavorite(id: Long, fav: Boolean) = store.update { d -> d.copy(media = d.media.map { if (it.id == id) it.copy(favorite = fav) else it }) }

    fun remove(id: Long) = store.update { d ->
        d.copy(media = d.media.filterNot { it.id == id }, routines = d.routines.map { r -> r.copy(mediaIds = r.mediaIds - id) })
    }

    // ---- Developer content catalogs ----
    val builtInUrl: String get() = System.getProperty("diviora.catalogUrl").orEmpty().ifBlank { DEMO_URL }

    suspend fun addSource(url: String): DCatalogSource {
        if (!developerMode) throw CatalogException("Only the developer can add content sources")
        val u = url.trim()
        if (!u.startsWith("https://", true)) throw CatalogException("Content sources must use https://")
        val catalog = CatalogParser.parse(fetch(u)).first
        return apply(u, catalog, builtIn = false)
    }

    fun removeSource(url: String) = removeSourceInternal(url)

    private fun removeSourceInternal(url: String) = store.update { d ->
        val gone = d.media.filter { it.catalogUrl == url }.map { it.id }.toSet()
        d.copy(catalogs = d.catalogs.filterNot { it.url == url && !it.builtIn }, media = d.media.filterNot { it.id in gone },
            routines = d.routines.map { r -> r.copy(mediaIds = r.mediaIds.filterNot { it in gone }) })
    }

    suspend fun syncAll() {
        // Outside developer mode only the developer's built-in catalog is kept.
        if (!developerMode) store.value.catalogs.filter { it.url != builtInUrl }.forEach { removeSourceInternal(it.url) }
        if (store.value.catalogs.none { it.url == builtInUrl }) {
            store.update { it.copy(catalogs = listOf(DCatalogSource(builtInUrl, "Featured", builtIn = true)) + it.catalogs.filterNot { c -> c.builtIn }) }
        }
        for (src in store.value.catalogs) {
            runCatching { apply(src.url, CatalogParser.parse(fetch(src.url)).first, src.builtIn) }.onFailure { e ->
                store.update { d -> d.copy(catalogs = d.catalogs.map { if (it.url == src.url) it.copy(lastError = e.message ?: e.javaClass.simpleName) else it }) }
            }
        }
    }

    private fun apply(url: String, c: com.diviora.music.core.catalog.Catalog, builtIn: Boolean): DCatalogSource {
        val now = System.currentTimeMillis()
        val source = DCatalogSource(url, c.name, builtIn, c.items.size, now, null)
        store.update { d ->
            val existing = d.media.filter { it.catalogUrl == url }.associateBy { it.uri }
            var next = d.nextId
            val fresh = c.items.map { i ->
                val kind = when (i.type) { "video" -> MediaKind.VIDEO; "stream" -> MediaKind.STREAM; else -> MediaKind.AUDIO }
                val old = existing[i.url]
                DMedia(old?.id ?: next++, i.url, kind, MediaSource.CATALOG, i.title, i.artist, i.album ?: c.name, i.durationMs,
                    i.artwork, favorite = old?.favorite ?: false, catalogUrl = url, addedAtMs = old?.addedAtMs ?: now)
            }
            val keepIds = fresh.map { it.id }.toSet()
            val dropped = existing.values.map { it.id }.filterNot { it in keepIds }.toSet()
            d.copy(
                nextId = next,
                media = d.media.filterNot { it.catalogUrl == url } + fresh,
                routines = d.routines.map { r -> r.copy(mediaIds = r.mediaIds.filterNot { it in dropped }) },
                catalogs = d.catalogs.filterNot { it.url == url } + source,
            )
        }
        return source
    }

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        if (url == DEMO_URL) return@withContext Library::class.java.getResource("/demo-catalog.json")!!.readText()
        val res = http.send(
            HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(20)).header("Accept", "application/json")
                .header("User-Agent", "DIVIORA-Music-Desktop/${System.getProperty("diviora.version") ?: "dev"}").build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )
        if (res.statusCode() !in 200..299) throw CatalogException("Server answered HTTP ${res.statusCode()}")
        res.body().toString(Charsets.UTF_8)
    }

    companion object {
        const val DEMO_URL = "asset://catalog/demo-catalog.json"
        /** Developer mode: set DIVIORA_DEVELOPER=1 (or -Ddiviora.developer=true) when starting the app. Users never see sources. */
        val developerMode: Boolean get() = System.getenv("DIVIORA_DEVELOPER") == "1" || System.getProperty("diviora.developer") == "true"
        private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "wav", "aif", "aiff")
        private val VIDEO_EXT = setOf("mp4", "m4v", "flv", "fxm")
        private val PLAYLIST_EXT = setOf("m3u", "m3u8", "pls")
        /** Formats JavaFX media plays on every desktop OS. */
        val SUPPORTED_EXT = AUDIO_EXT + VIDEO_EXT
        fun kindOf(name: String): MediaKind? = when (name.substringAfterLast('.', "").lowercase()) {
            in AUDIO_EXT -> MediaKind.AUDIO
            in VIDEO_EXT -> MediaKind.VIDEO
            else -> null
        }
    }
}
