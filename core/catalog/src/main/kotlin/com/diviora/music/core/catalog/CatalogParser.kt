package com.diviora.music.core.catalog

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Catalog format v1 (no size or item limit), published by a developer or content provider (any HTTPS server or static host).
 * See docs/catalog.md for the full specification and docs/catalog/sample-catalog.json for an example.
 */
@Serializable
data class Catalog(
    val version: Int = 1,
    val name: String,
    val description: String? = null,
    val items: List<CatalogItem> = emptyList(),
    val playlists: List<CatalogPlaylist> = emptyList(),
    /** Developer switch: may users save items for offline playback? (default: no, streaming only) */
    val allowDownload: Boolean = false,
)

@Serializable
data class CatalogItem(
    /** Stable id, unique inside the catalog. */
    val id: String,
    val title: String,
    /** Direct media URL: MP3/AAC/OGG/FLAC/WAV/MP4/WebM file, HLS (.m3u8), DASH (.mpd) or Icecast stream. */
    val url: String,
    /** "audio", "video" or "stream" (live). */
    val type: String = "audio",
    val artist: String? = null,
    val album: String? = null,
    val artwork: String? = null,
    val durationMs: Long = 0,
    val license: String? = null,
    /** Per-item override of [Catalog.allowDownload]. */
    val download: Boolean? = null,
)

@Serializable
data class CatalogPlaylist(val id: String, val title: String, val itemIds: List<String>)

class CatalogException(message: String) : Exception(message)

object CatalogParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    /** Parses and validates a catalog. Invalid items are dropped; an unusable catalog throws [CatalogException]. */
    fun parse(text: String): Pair<Catalog, List<String>> {
        val raw = runCatching { json.decodeFromString(Catalog.serializer(), text) }
            .getOrElse { throw CatalogException("Not a valid DIVIORA catalog: ${it.message?.take(120)}") }
        if (raw.version != 1) throw CatalogException("Unsupported catalog version ${raw.version}")
        if (raw.name.isBlank()) throw CatalogException("Catalog has no name")
        val warnings = ArrayList<String>()
        val seen = HashSet<String>()
        // No size or item-count limit: catalogs of any size are accepted.
        val items = raw.items.filter { i ->
            val ok = i.id.isNotBlank() && i.title.isNotBlank() && isHttp(i.url) && i.type in TYPES && seen.add(i.id)
            if (!ok) warnings += "Skipped item '${i.id}': needs unique id, title, http(s) url and type audio|video|stream"
            ok
        }.map { if (it.artwork != null && !isHttp(it.artwork)) it.copy(artwork = null) else it }
        return raw.copy(name = raw.name.trim().take(80), items = items) to warnings
    }

    private val TYPES = setOf("audio", "video", "stream")
    private fun isHttp(u: String) = u.startsWith("https://", true) || u.startsWith("http://", true)
}
