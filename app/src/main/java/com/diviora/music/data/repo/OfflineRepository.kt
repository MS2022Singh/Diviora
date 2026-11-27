package com.diviora.music.data.repo

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.diviora.music.BuildConfig
import com.diviora.music.container
import com.diviora.music.data.db.AppDatabase
import com.diviora.music.data.db.MediaEntity
import com.diviora.music.security.encryptingSink
import com.diviora.music.security.offlineDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Developer's test override for downloads on this device (only effective in developer mode). */
enum class DownloadOverride { FOLLOW_CATALOG, ALWAYS_ALLOW, NEVER_ALLOW }

/**
 * Offline downloads of online (catalog) items, allowed only when the developer's catalog says so.
 *
 * Security: files are AES-encrypted *while downloading* (no plaintext ever touches storage) into app-private internal
 * storage, with a per-install key wrapped by the Android Keystore. They play only inside DIVIORA on this phone; copying
 * them elsewhere yields unreadable data. Turning downloads off in the catalog deletes them at the next sync.
 */
class OfflineRepository(private val context: Context, private val db: AppDatabase, private val settings: SettingsStore) {
    private val dao get() = db.media()

    suspend fun isAllowed(m: MediaEntity): Boolean {
        if (!m.uri.startsWith("http") || m.drmJson != null) return false
        if (isStreamUrl(m.uri)) return false
        val s = settings.settings.first()
        if (BuildConfig.DEBUG || s.developerUnlocked) when (s.downloadOverride) {
            DownloadOverride.ALWAYS_ALLOW -> return true
            DownloadOverride.NEVER_ALLOW -> return false
            DownloadOverride.FOLLOW_CATALOG -> Unit
        }
        return m.downloadAllowed
    }

    suspend fun download(m: MediaEntity): Boolean = withContext(Dispatchers.IO) {
        if (!isAllowed(m)) return@withContext false
        dao.setOffline(m.id, PENDING, null)
        WorkManager.getInstance(context).enqueueUniqueWork(
            "offline-${m.id}", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SecureDownloadWorker>()
                .setInputData(workDataOf("mediaId" to m.id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
        true
    }

    suspend fun remove(m: MediaEntity) = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelUniqueWork("offline-${m.id}")
        m.offlinePath?.let { runCatching { File(it).delete() } }
        File(offlineDir(context), "${m.id}.enc.part").delete()
        dao.setOffline(m.id, null, null)
    }

    /** Deletes copies the developer no longer allows, and any old unencrypted copies from versions before 0.9. */
    suspend fun enforcePolicy() = withContext(Dispatchers.IO) {
        val dir = offlineDir(context).canonicalPath
        for (m in dao.withOffline()) {
            val legacyPlain = m.offlinePath != null && !File(m.offlinePath).canonicalPath.startsWith(dir)
            if (legacyPlain || !isAllowed(m)) remove(m)
        }
    }

    companion object {
        /** downloadId value meaning "download in progress" (the worker is identified by the media id). */
        const val PENDING = 1L
        fun isStreamUrl(u: String) = u.lowercase().substringBefore('?').let { it.endsWith(".m3u8") || it.endsWith(".mpd") }
    }
}

/** Downloads one item, encrypting on the fly. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SecureDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val id = inputData.getLong("mediaId", -1)
        val m = c.db.media().get(id) ?: return Result.failure()
        val part = File(offlineDir(applicationContext), "$id.enc.part")
        val target = File(offlineDir(applicationContext), "$id.enc")
        runCatching { setForeground(foreground(m.title, 0)) }
        return try {
            // Refresh a signed URL that is about to expire before downloading.
            val fresh = if (c.catalogs.needsRefresh(listOf(m))) { c.catalogs.syncAll(); c.db.media().get(id) ?: m } else m
            val conn = (URL(fresh.uri).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
                com.diviora.music.security.ProtectedDataSourceFactory.contentHeaders().forEach { (k, v) -> setRequestProperty(k, v) }
            }
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            // The nonce must match the final file name, so write under the final name's key via a temp path of the same name stem.
            val (sink, spec) = encryptingSink(c.vault, target)
            sink.open(spec.buildUpon().setUri(android.net.Uri.fromFile(part)).build())
            try {
                conn.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024); var done = 0L; var n: Int; var lastPct = -1
                    while (input.read(buf).also { n = it } >= 0) {
                        if (isStopped) error("cancelled")
                        sink.write(buf, 0, n); done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else -1
                        if (pct != lastPct && pct % 5 == 0) { lastPct = pct; runCatching { setForeground(foreground(m.title, pct)) } }
                    }
                }
            } finally { sink.close(); conn.disconnect() }
            if (!part.renameTo(target)) error("could not finalise file")
            c.db.media().setOffline(id, null, target.path)
            Result.success()
        } catch (e: Exception) {
            Log.w("SecureDownload", "download of $id failed: ${e.message}")
            part.delete()
            c.db.media().setOffline(id, null, null)
            if (runAttemptCount < 2 && e.message != "cancelled") Result.retry() else Result.failure()
        }
    }

    private fun foreground(title: String, pct: Int): ForegroundInfo {
        com.diviora.music.scheduler.RoutineNotifications.ensureChannel(applicationContext)
        val n = androidx.core.app.NotificationCompat.Builder(applicationContext, com.diviora.music.scheduler.RoutineNotifications.CHANNEL)
            .setSmallIcon(com.diviora.music.R.drawable.ic_stat_diviora)
            .setContentTitle("Saving for offline: $title")
            .setProgress(100, pct.coerceAtLeast(0), pct < 0)
            .setOngoing(true).setSilent(true).build()
        return if (android.os.Build.VERSION.SDK_INT >= 29)
            ForegroundInfo(7000 + (id.hashCode() and 0xff), n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(7000 + (id.hashCode() and 0xff), n)
    }
}
