package com.diviora.music.data.repo

import android.content.Context
import com.diviora.music.core.catalog.*
import android.util.Log
import androidx.room.withTransaction
import com.diviora.music.BuildConfig
import com.diviora.music.core.model.MediaKind
import com.diviora.music.core.model.MediaSource
import com.diviora.music.data.db.AppDatabase
import com.diviora.music.data.db.CatalogSourceEntity
import com.diviora.music.data.db.MediaEntity
import com.diviora.music.data.db.PlaylistEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Developer content: loads catalogs (JSON) from content servers and mirrors their items into the library, where they
 * can be played, favourited, added to playlists and scheduled in routines like any other media.
 *
 * - Built-in source: `diviora.catalogUrl` in gradle.properties (the app developer's server). If it is empty, the
 *   bundled demo catalog (assets/catalog/demo-catalog.json) is used.
 * - Users may add more sources in Settings → Content sources.
 */
class CatalogRepository(private val context: Context, private val db: AppDatabase, private val settings: SettingsStore) {
    /** Only developers may add content sources: debug builds, or release builds unlocked with the developer passphrase. */
    suspend fun developerMode(): Boolean = BuildConfig.DEBUG || settings.settings.first().developerUnlocked
    private val lock = Mutex()
    fun observeSources() = db.catalogs().observeAll()
    fun observeItems() = db.media().observeCatalogItems()

    val builtInUrl: String get() = BuildConfig.CATALOG_URL.ifBlank { DEMO_URL }

    suspend fun ensureBuiltIn() {
        if (db.catalogs().get(builtInUrl) == null) db.catalogs().upsert(CatalogSourceEntity(builtInUrl, "Featured", builtIn = true))
    }

    /** Validates and adds a user content source. Throws on an invalid URL or catalog. */
    suspend fun addSource(url: String): CatalogSourceEntity {
        val u = url.trim()
        if (!developerMode()) throw CatalogException("Only the developer can add content sources")
        if (!u.startsWith("https://", true)) throw CatalogException("Content sources must use https://")
        val text = fetch(u)
        val (catalog, _) = CatalogParser.parse(text)
        db.catalogs().upsert(CatalogSourceEntity(u, catalog.name))
        return applyCatalog(u, catalog, builtIn = false)
    }

    suspend fun removeSource(url: String) = lock.withLock {
        val src = db.catalogs().get(url) ?: return@withLock
        if (src.builtIn) throw CatalogException("The built-in source can't be removed")
        db.withTransaction { db.media().deleteCatalog(url); db.catalogs().delete(url) }
    }

    /** Refreshes every source, then removes offline copies the developer no longer allows. Failures never throw. */
    suspend fun syncAll(): List<CatalogSourceEntity> = syncAllInternal().also {
        runCatching { (context.applicationContext as com.diviora.music.DivioraApp).container.offline.enforcePolicy() }
    }

    private suspend fun syncAllInternal(): List<CatalogSourceEntity> {
        ensureBuiltIn()
        // Users never keep extra sources: outside developer mode only the built-in (developer's) catalog stays.
        if (!developerMode()) db.catalogs().all().filter { !it.builtIn || it.url != builtInUrl }.forEach { src ->
            db.withTransaction { db.media().deleteCatalog(src.url); db.catalogs().delete(src.url) }
        }
        return db.catalogs().all().map { src ->
            runCatching { applyCatalog(src.url, CatalogParser.parse(fetch(src.url)).first, src.builtIn) }
                .getOrElse { e ->
                    Log.w(TAG, "sync failed ${src.url}", e)
                    src.copy(lastError = e.message ?: e.javaClass.simpleName).also { db.catalogs().upsert(it) }
                }
        }
    }

    private suspend fun applyCatalog(url: String, catalog: Catalog, builtIn: Boolean): CatalogSourceEntity = lock.withLock {
        val dao = db.media()
        db.withTransaction {
            val existing = dao.catalogUris(url).associate { it.uri to it.id }
            val idByItem = HashMap<String, Long>()
            for (item in catalog.items) {
                val key = itemUri(url, item)
                val kind = when (item.type) { "video" -> MediaKind.VIDEO; "stream" -> MediaKind.STREAM; else -> MediaKind.AUDIO }
                val row = MediaEntity(
                    uri = key, kind = kind, source = MediaSource.CATALOG, title = item.title.take(200),
                    artist = item.artist?.take(120), album = item.album ?: catalog.name, durationMs = item.durationMs.coerceAtLeast(0),
                    artworkUri = item.artwork, catalogUrl = url, downloadAllowed = item.download ?: catalog.allowDownload,
                )
                val id = existing[key]
                val mediaId = if (id == null) dao.insert(row).takeIf { it > 0 } ?: dao.idForUri(key) ?: continue
                else {
                    dao.refreshMetadata(id, row.title, row.artist, row.album, row.durationMs, row.artworkUri, null, null)
                    id
                }
                dao.setDownloadAllowed(mediaId, row.downloadAllowed)
                idByItem[item.id] = mediaId
            }
            val keep = catalog.items.mapTo(HashSet()) { itemUri(url, it) }
            existing.filterKeys { it !in keep }.values.toList().takeIf { it.isNotEmpty() }?.let { dao.markUnavailable(it) }
            // Catalog playlists become library playlists named "<catalog> · <playlist>".
            val playlists = db.playlists()
            for (p in catalog.playlists) {
                val name = "${catalog.name} · ${p.title}".take(100)
                val ids = p.itemIds.mapNotNull { idByItem[it] }
                if (ids.isEmpty()) continue
                val pid = playlists.findByName(name) ?: playlists.insert(PlaylistEntity(name = name))
                playlists.replaceItems(pid, ids)
            }
        }
        CatalogSourceEntity(url, catalog.name, builtIn, catalog.items.size, System.currentTimeMillis(), null, catalog.allowDownload)
            .also { db.catalogs().upsert(it) }
    }

    /** Library key of a catalog item: its media URL (the media table enforces one row per URL). */
    @Suppress("UNUSED_PARAMETER")
    private fun itemUri(catalogUrl: String, item: CatalogItem) = item.url.trim()

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        if (url == DEMO_URL) return@withContext context.assets.open("catalog/demo-catalog.json").bufferedReader().use { it.readText() }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 20_000; instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "DIVIORA-Music/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (conn.responseCode !in 200..299) throw CatalogException("Server answered HTTP ${conn.responseCode}")
            conn.inputStream.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
        } finally { conn.disconnect() }
    }

    companion object {
        private const val TAG = "CatalogRepository"
        const val DEMO_URL = "asset://catalog/demo-catalog.json"
    }
}
