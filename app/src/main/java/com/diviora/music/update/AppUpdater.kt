package com.diviora.music.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.diviora.music.BuildConfig
import com.diviora.music.core.catalog.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * App self-update for sideloaded installs. Reads version.json from the latest GitHub release and, when it is newer,
 * downloads the APK and opens Android's installer. Android always asks the user to confirm the install.
 */
class AppUpdater(private val context: Context) {
    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val info: UpdateInfo) : State
        data class Downloading(val info: UpdateInfo, val percent: Int) : State
        data class ReadyToInstall(val info: UpdateInfo, val apk: File) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Debug builds have their own package id, so a release APK would install as a second app: no updates there. */
    val enabled: Boolean get() = !BuildConfig.DEBUG && BuildConfig.UPDATE_URL.isNotBlank()

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext null
        _state.value = State.Checking
        val info = runCatching { UpdateInfo.parse(httpGet(BuildConfig.UPDATE_URL)) }.getOrNull()
        _state.value = when {
            info == null -> State.Failed("Could not check for updates (offline?)")
            info.isNewerThan(BuildConfig.VERSION_CODE) && info.android != null -> State.Available(info)
            else -> State.UpToDate
        }
        info?.takeIf { it.isNewerThan(BuildConfig.VERSION_CODE) && it.android != null }
    }

    suspend fun download(info: UpdateInfo): File? = withContext(Dispatchers.IO) {
        val url = info.android ?: return@withContext null
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "DIVIORA-Music-${info.versionName}.apk")
        try {
            var conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true; conn.connectTimeout = 15_000; conn.readTimeout = 30_000
            // GitHub release downloads redirect across hosts; follow manually if needed.
            var hops = 0
            while (conn.responseCode in 301..308 && hops++ < 5) {
                val next = conn.getHeaderField("Location"); conn.disconnect()
                conn = (URL(URL(url), next).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 30_000 }
            }
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024); var done = 0L; var n: Int
                    while (input.read(buf).also { n = it } >= 0) {
                        o.write(buf, 0, n); done += n
                        if (total > 0) _state.value = State.Downloading(info, (done * 100 / total).toInt())
                    }
                }
            }
            conn.disconnect()
            // Safety: it must be DIVIORA, and newer than what is installed.
            val pkg = context.packageManager.getPackageArchiveInfo(out.path, 0) ?: error("Downloaded file is not a valid app")
            val code = if (Build.VERSION.SDK_INT >= 28) pkg.longVersionCode.toInt() else @Suppress("DEPRECATION") pkg.versionCode
            if (pkg.packageName != context.packageName) error("Downloaded app has the wrong package")
            if (code <= BuildConfig.VERSION_CODE) error("Downloaded app is not newer")
            _state.value = State.ReadyToInstall(info, out)
            out
        } catch (e: Exception) {
            Log.w(TAG, "update download failed", e)
            out.delete()
            _state.value = State.Failed("Download failed: ${e.message}")
            null
        }
    }

    /** True if Android lets DIVIORA open the installer; otherwise the user must allow "Install unknown apps" first. */
    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true; connectTimeout = 15_000; readTimeout = 15_000
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            var c = conn; var hops = 0
            while (c.responseCode in 301..308 && hops++ < 5) {
                val next = c.getHeaderField("Location"); c.disconnect()
                c = (URL(URL(url), next).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 15_000 }
            }
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText().take(100_000) }
        } finally { conn.disconnect() }
    }

    companion object { private const val TAG = "AppUpdater" }
}
