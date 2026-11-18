package com.diviora.music.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.diviora.music.data.db.MediaEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class QueueEntry(val index: Int, val mediaId: String, val title: String, val artist: String?)

data class PlayerUiState(
    val connected: Boolean = false,
    val mediaId: String? = null,
    val title: String = "",
    val artist: String? = null,
    val artworkUri: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val isVideo: Boolean = false,
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = 0,
    val error: String? = null,
    val sleepTimerEndsAtMs: Long? = null,
)

/** UI-side connection to [PlaybackService] through a Media3 MediaController. */
class PlayerConnection(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()
    var controller: MediaController? = null
        private set
    private var ticker: Job? = null
    private var sleepJob: Job? = null
    private val pending = ArrayList<(MediaController) -> Unit>()

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            refresh()
            pending.forEach { it(c) }; pending.clear()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else { pending += block; connect() }
    }

    fun playItems(items: List<MediaEntity>, startIndex: Int = 0) = withController { c ->
        if (items.isEmpty()) return@withController
        c.setMediaItems(items.map { it.toMediaItem() }, startIndex.coerceIn(0, items.lastIndex), 0L)
        c.prepare(); c.play()
    }

    fun addToQueue(items: List<MediaEntity>, next: Boolean = false) = withController { c ->
        val mapped = items.map { it.toMediaItem() }
        if (c.mediaItemCount == 0) { c.setMediaItems(mapped); c.prepare(); return@withController }
        if (next) c.addMediaItems(c.currentMediaItemIndex + 1, mapped) else c.addMediaItems(mapped)
    }

    fun togglePlay() = withController { c -> if (c.isPlaying) c.pause() else { if (c.playbackState == Player.STATE_IDLE) c.prepare(); c.play() } }
    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun jumpTo(index: Int) = withController { it.seekTo(index, 0); it.play() }
    fun removeAt(index: Int) = withController { it.removeMediaItem(index) }
    fun moveItem(from: Int, to: Int) = withController { it.moveMediaItem(from, to) }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    fun setSpeed(speed: Float) = withController { it.playbackParameters = PlaybackParameters(speed) }

    /** Sleep timer: pauses after [minutes]. Pass null to cancel it. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) { _state.update { it.copy(sleepTimerEndsAtMs = null) }; return }
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _state.update { it.copy(sleepTimerEndsAtMs = endsAt) }
        sleepJob = scope.launch(Dispatchers.Main) {
            delay(minutes * 60_000L)
            controller?.pause()
            _state.update { it.copy(sleepTimerEndsAtMs = null) }
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    private fun refresh() {
        val c = controller ?: return
        val item: MediaItem? = c.currentMediaItem
        val queue = (0 until c.mediaItemCount).map { i ->
            val m = c.getMediaItemAt(i)
            QueueEntry(i, m.mediaId, m.mediaMetadata.title?.toString() ?: "", m.mediaMetadata.artist?.toString())
        }
        _state.update {
            it.copy(
                connected = true,
                mediaId = item?.mediaId,
                title = item?.mediaMetadata?.title?.toString() ?: "",
                artist = item?.mediaMetadata?.artist?.toString(),
                artworkUri = item?.mediaMetadata?.artworkUri?.toString(),
                isPlaying = c.isPlaying,
                isBuffering = c.playbackState == Player.STATE_BUFFERING,
                positionMs = c.currentPosition.coerceAtLeast(0),
                durationMs = c.duration.takeIf { d -> d > 0 } ?: 0,
                shuffle = c.shuffleModeEnabled,
                repeatMode = c.repeatMode,
                speed = c.playbackParameters.speed,
                isVideo = item?.isVideo() == true,
                queue = queue,
                currentIndex = c.currentMediaItemIndex,
                error = c.playerError?.let { e -> e.errorCodeName + (e.message?.let { m -> ": $m" } ?: "") },
            )
        }
        if (c.isPlaying && ticker?.isActive != true) {
            ticker = scope.launch(Dispatchers.Main) {
                while (isActive && controller?.isPlaying == true) {
                    _state.update { s -> s.copy(positionMs = controller?.currentPosition ?: 0) }
                    delay(500)
                }
            }
        }
    }
}
