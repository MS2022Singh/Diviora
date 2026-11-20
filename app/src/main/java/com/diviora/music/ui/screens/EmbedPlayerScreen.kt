package com.diviora.music.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.diviora.music.container
import com.diviora.music.core.catalog.PlatformLinks
import com.diviora.music.ui.UiEvents
import com.diviora.music.ui.openInPlatformApp
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * Plays YouTube, Spotify and SoundCloud links inside DIVIORA through each platform's official embedded player.
 * Platform terms require these to play on screen (no background or locked-screen playback, no ad removal).
 */
@Composable
fun EmbedPlayerScreen(nav: NavHostController, mediaId: Long) {
    val ctx = LocalContext.current
    val c = ctx.container
    val media by remember(mediaId) { c.db.media().observe(mediaId) }.collectAsStateWithLifecycle(null)
    val m = media ?: return
    val embed = remember(m.uri) { PlatformLinks.embedFor(m.uri) }
    val inPip = com.diviora.music.ui.LocalInPip.current
    val activity = androidx.activity.compose.LocalActivity.current as? com.diviora.music.MainActivity
    androidx.compose.runtime.LaunchedEffect(m.id) { c.library.refreshEmbedTitle(m) }
    androidx.compose.runtime.DisposableEffect(embed?.isVideo) {
        com.diviora.music.ui.PipState.embedActive.value = embed?.isVideo == true
        onDispose { com.diviora.music.ui.PipState.embedActive.value = false }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = if (inPip) 0.dp else 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!inPip) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.KeyboardArrowDown, "Close") }
            Text(embed?.platform ?: "Player", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            if (embed?.isVideo == true) IconButton(onClick = { if (activity?.enterPip() != true) activity?.explainPipUnavailable() }) {
                Icon(Icons.Filled.PictureInPictureAlt, "Picture-in-picture")
            }
            IconButton(onClick = { openInPlatformApp(ctx, m.uri) }) { Icon(Icons.Filled.OpenInNew, "Open in app") }
        }
        if (embed == null) { Text("This link can't be embedded."); return@Column }
        if (embed.platform == "YouTube") YouTubeEmbed(embed.id) else WebEmbed(embed.embedUrl, tall = embed.kind != "track")
        if (inPip) return@Column
        Text(m.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp), textAlign = TextAlign.Center)
        m.artist?.takeIf { it != embed.platform }?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(
            when (embed.platform) {
                "YouTube" -> "Playing with YouTube's official player. YouTube allows on-screen playback only, so it stops when you leave this screen or lock the phone."
                "Spotify" -> "Spotify's official player plays 30-second previews unless you're signed in to Spotify inside it. For full tracks tap ↗ to open the Spotify app."
                else -> "Playing with ${embed.platform}'s official player (on screen only)."
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { UiEvents.scheduleItems(listOf(m.id)) }) { Icon(Icons.Filled.Alarm, null); Text(" Schedule") }
            FilledTonalButton(onClick = { openInPlatformApp(ctx, m.uri) }) { Icon(Icons.Filled.OpenInNew, null); Text(" Open in ${embed.platform}") }
        }
    }
}

@Composable
private fun YouTubeEmbed(videoId: String) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    AndroidView(
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
        factory = { ctx ->
            YouTubePlayerView(ctx).apply {
                lifecycle.addObserver(this)
                addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) { youTubePlayer.loadVideo(videoId, 0f) }
                })
            }
        },
        onRelease = { lifecycle.removeObserver(it); it.release() },
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebEmbed(url: String, tall: Boolean) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(if (tall) 420.dp else 180.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                loadUrl(url)
            }
        },
        onRelease = { it.stopLoading(); it.destroy() },
    )
}
