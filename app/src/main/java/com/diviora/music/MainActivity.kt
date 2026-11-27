package com.diviora.music

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.diviora.music.data.repo.AppSettings
import com.diviora.music.playback.PlaybackService
import com.diviora.music.scheduler.AlarmScheduler
import com.diviora.music.ui.DivioraRoot
import com.diviora.music.ui.UiEvents
import com.diviora.music.ui.theme.DivioraTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var inPip by mutableStateOf(false)
    private var openRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Content protection: screenshots and screen recordings of DIVIORA show a black screen.
        if (BuildConfig.SECURE_MODE) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        container.player.connect()
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
            // Status/navigation bar icons must contrast with the app theme (not the system theme).
            val dark = when (settings.theme) {
                com.diviora.music.data.repo.ThemeMode.DARK -> true
                com.diviora.music.data.repo.ThemeMode.LIGHT -> false
                com.diviora.music.data.repo.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            androidx.compose.runtime.DisposableEffect(dark) {
                val t = android.graphics.Color.TRANSPARENT
                val style = if (dark) androidx.activity.SystemBarStyle.dark(t) else androidx.activity.SystemBarStyle.light(t, t)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            DivioraTheme(settings.theme, settings.dynamicColor) {
                DivioraRoot(inPip = inPip, openRoute = openRoute, onRouteConsumed = { openRoute = null })
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
        // Keep picture-in-picture settings in sync with what is playing (auto-enter on Android 12+).
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(container.player.state, com.diviora.music.ui.PipState.embedActive) { s, e -> s to e }
                .collect { (s, e) -> updatePipParams(s, e) }
        }
    }

    private fun pipParams(s: com.diviora.music.playback.PlayerUiState, embed: Boolean = com.diviora.music.ui.PipState.embedActive.value): PictureInPictureParams {
        val ratio = if (s.videoWidth > 0 && s.videoHeight > 0) {
            val r = s.videoWidth.toFloat() / s.videoHeight
            when { r > 2.39f -> Rational(239, 100); r < 1 / 2.39f -> Rational(100, 239); else -> Rational(s.videoWidth, s.videoHeight) }
        } else Rational(16, 9)
        val b = PictureInPictureParams.Builder().setAspectRatio(if (embed) Rational(16, 9) else ratio)
            .setActions(if (embed) emptyList() else pipActions(s.isPlaying))
        if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(embed || (s.isVideo && s.isPlaying)).setSeamlessResizeEnabled(true)
        return b.build()
    }

    private fun pipActions(playing: Boolean): List<android.app.RemoteAction> {
        fun action(icon: Int, title: String, cmd: String, req: Int) = android.app.RemoteAction(
            android.graphics.drawable.Icon.createWithResource(this, icon), title, title,
            android.app.PendingIntent.getBroadcast(this, req, Intent(this, PipActionReceiver::class.java).setAction(cmd),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT),
        )
        return listOf(
            action(android.R.drawable.ic_media_previous, "Previous", PipActionReceiver.PREV, 31),
            if (playing) action(android.R.drawable.ic_media_pause, "Pause", PipActionReceiver.TOGGLE, 32)
            else action(android.R.drawable.ic_media_play, "Play", PipActionReceiver.TOGGLE, 32),
            action(android.R.drawable.ic_media_next, "Next", PipActionReceiver.NEXT, 33),
        )
    }

    private fun updatePipParams(s: com.diviora.music.playback.PlayerUiState, embed: Boolean) {
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        runCatching { setPictureInPictureParams(pipParams(s, embed)) }
    }

    /** False if the user (or the phone maker's default, e.g. on Vivo) switched picture-in-picture off for DIVIORA. */
    private fun pipAllowed(): Boolean = runCatching {
        val ops = getSystemService(android.app.AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_PICTURE_IN_PICTURE, applicationInfo.uid, packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_PICTURE_IN_PICTURE, applicationInfo.uid, packageName)
        mode == android.app.AppOpsManager.MODE_ALLOWED
    }.getOrDefault(true)

    /** Explains why PiP did not start and opens the right settings page. */
    fun explainPipUnavailable() {
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            UiEvents.message("This device does not support picture-in-picture"); return
        }
        UiEvents.message("Picture-in-picture is turned off for DIVIORA. Switch it on: Settings → Apps → DIVIORA Music → Picture-in-picture.")
        val pkg = Uri.parse("package:$packageName")
        runCatching { startActivity(Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS", pkg)) }
            .recoverCatching { startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)) }
    }

    /** Enter picture-in-picture now (the PiP button in Now Playing). */
    fun enterPip(): Boolean {
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
        if (!pipAllowed()) return false
        return runCatching { enterPictureInPictureMode(pipParams(container.player.state.value)) }.getOrDefault(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_OPEN)?.let { openRoute = it }
        val runSlot = intent.getLongExtra(EXTRA_RUN_SLOT, -1)
        if (runSlot >= 0) {
            ContextCompat.startForegroundService(this, PlaybackService.runSlotIntent(this, runSlot,
                intent.getLongExtra(AlarmScheduler.EXTRA_OCCURRENCE, System.currentTimeMillis()), manual = true))
            openRoute = "player"
            return
        }
        val lib = container.library
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { uri ->
                lifecycleScope.launch {
                    runCatching {
                        if (uri.scheme == "http" || uri.scheme == "https") {
                            val id = lib.addStream(uri.toString(), null); playIds(listOf(id))
                        } else {
                            val r = lib.importShared(listOf(uri)); playIds(r.ids)
                        }
                    }.onFailure { com.diviora.music.ui.handleAddStreamFailure(this@MainActivity, it) }
                }
            }
            Intent.ACTION_SEND -> {
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                lifecycleScope.launch {
                    runCatching {
                        when {
                            stream != null -> { val r = lib.importShared(listOf(stream)); UiEvents.message("Imported ${r.ids.size} file(s)"); playIds(r.ids) }
                            text != null -> {
                                val url = Regex("https?://\\S+").find(text)?.value ?: error("No link found in shared text")
                                val id = lib.addStream(url, null); UiEvents.message("Stream added"); playIds(listOf(id))
                            }
                        }
                    }.onFailure { com.diviora.music.ui.handleAddStreamFailure(this@MainActivity, it) }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                lifecycleScope.launch {
                    val r = lib.importShared(uris)
                    UiEvents.message("Imported ${r.ids.size} file(s)" + if (r.failed > 0) ", ${r.failed} failed" else "")
                }
            }
        }
    }

    private suspend fun playIds(ids: List<Long>) {
        val items = container.db.media().getAll(ids)
        if (items.isNotEmpty()) { container.player.playItems(items); openRoute = "player" }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val s = container.player.state.value
        // Android 8–11: enter PiP when leaving the app during video (12+ uses auto-enter).
        if (((s.isVideo && s.isPlaying) || com.diviora.music.ui.PipState.embedActive.value) && Build.VERSION.SDK_INT < 31) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val EXTRA_RUN_SLOT = "runSlot"
    }
}

/** Buttons inside the picture-in-picture window. */
class PipActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: Intent) {
        val p = context.container.player
        when (intent.action) { TOGGLE -> p.togglePlay(); NEXT -> p.next(); PREV -> p.previous() }
    }
    companion object {
        const val TOGGLE = "com.diviora.music.pip.TOGGLE"
        const val NEXT = "com.diviora.music.pip.NEXT"
        const val PREV = "com.diviora.music.pip.PREV"
    }
}
