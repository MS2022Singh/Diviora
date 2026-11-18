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
        container.player.connect()
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
            DivioraTheme(settings.theme, settings.dynamicColor) {
                DivioraRoot(inPip = inPip, openRoute = openRoute, onRouteConsumed = { openRoute = null })
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
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
        if (s.isVideo && s.isPlaying && Build.VERSION.SDK_INT >= 26) {
            runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
        }
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
