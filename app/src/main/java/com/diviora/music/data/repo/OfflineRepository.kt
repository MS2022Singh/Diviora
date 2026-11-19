package com.diviora.music.data.repo

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.diviora.music.BuildConfig
import com.diviora.music.container
import com.diviora.music.data.db.AppDatabase
import com.diviora.music.data.db.MediaEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Developer's test override for downloads on this device (only effective in developer mode). */
enum class DownloadOverride { FOLLOW_CATALOG, ALWAYS_ALLOW, NEVER_ALLOW }

/**
 * Offline downloads of online (catalog) items. The developer decides: catalog.json `"allowDownload": true` (or per item
 * `"download": true/false`). When the developer switches downloads off, saved copies are deleted at the next sync.
 * Files live in the app's private storage (Android/data/<app>/files/offline) and are removed when the app is uninstalled.
 */
class OfflineRepository(private val context: Context, private val db: AppDatabase, private val settings: SettingsStore) {
    private val dm get() = context.getSystemService(DownloadManager::class.java)
    private val dao get() = db.media()
    private val dir get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "offline").apply { mkdirs() }

    suspend fun isAllowed(m: MediaEntity): Boolean {
        val s = settings.settings.first()
        if (BuildConfig.DEBUG || s.developerUnlocked) when (s.downloadOverride) {
            DownloadOverride.ALWAYS_ALLOW -> return m.uri.startsWith("http")
            DownloadOverride.NEVER_ALLOW -> return false
            DownloadOverride.FOLLOW_CATALOG -> Unit
        }
        return m.downloadAllowed && m.uri.startsWith("http")
    }

    suspend fun download(m: MediaEntity): Boolean = withContext(Dispatchers.IO) {
        if (!isAllowed(m)) return@withContext false
        val ext = Uri.parse(m.uri).lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.length in 2..5 } ?: "bin"
        val target = File(dir, "${m.id}.$ext")
        val req = DownloadManager.Request(Uri.parse(m.uri))
            .setTitle(m.title).setDescription("DIVIORA offline copy")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(target))
            .setAllowedOverMetered(true)
        val id = dm.enqueue(req)
        dao.setOffline(m.id, id, null)
        true
    }

    suspend fun remove(m: MediaEntity) = withContext(Dispatchers.IO) {
        m.downloadId?.let { runCatching { dm.remove(it) } }
        m.offlinePath?.let { runCatching { File(it).delete() } }
        dao.setOffline(m.id, null, null)
    }

    /** Called when a download finishes. */
    suspend fun onDownloadComplete(downloadId: Long) = withContext(Dispatchers.IO) {
        val m = dao.byDownloadId(downloadId) ?: return@withContext
        dm.query(DownloadManager.Query().setFilterById(downloadId))?.use { c ->
            if (c.moveToFirst() && c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
                val local = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                dao.setOffline(m.id, null, Uri.parse(local).path)
                return@withContext
            }
        }
        Log.w(TAG, "download $downloadId for ${m.title} failed")
        dao.setOffline(m.id, null, null)
    }

    /** Deletes offline copies the developer no longer allows (run after every catalog sync and override change). */
    suspend fun enforcePolicy() = withContext(Dispatchers.IO) {
        for (m in dao.withOffline()) if (!isAllowed(m)) remove(m)
    }

    companion object { private const val TAG = "OfflineRepository" }
}

class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1).takeIf { it >= 0 } ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { context.container.offline.onDownloadComplete(id) } finally { pending.finish() }
        }
    }
}
