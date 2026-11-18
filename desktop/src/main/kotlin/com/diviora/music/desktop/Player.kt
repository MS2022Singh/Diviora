package com.diviora.music.desktop

import com.diviora.music.core.model.MediaKind
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class PlayerState(
    val queue: List<DMedia> = emptyList(),
    val index: Int = -1,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Double = 0.8,
    val shuffle: Boolean = false,
    val repeat: Boolean = false,
    val error: String? = null,
) {
    val current: DMedia? get() = queue.getOrNull(index)
}

/** Desktop playback on JavaFX media. Every JavaFX call runs on the FX thread. */
class Player(initialVolume: Double) {
    private val _state = MutableStateFlow(PlayerState(volume = initialVolume))
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile var mediaPlayer: MediaPlayer? = null
        private set
    /** Called whenever a new MediaPlayer is created, so a visible video view can attach to it. */
    var onPlayerChanged: ((MediaPlayer?) -> Unit)? = null
    private var gain = 1.0
    private var stopJob: Job? = null
    private var fadeJob: Job? = null

    init {
        startFx()
        scope.launch {
            while (isActive) {
                delay(500)
                val mp = mediaPlayer ?: continue
                fx {
                    _state.update { it.copy(positionMs = mp.currentTime.toMillis().toLong(),
                        durationMs = mp.totalDuration?.toMillis()?.takeIf { d -> d.isFinite() && d > 0 }?.toLong() ?: 0) }
                }
            }
        }
    }

    fun playQueue(items: List<DMedia>, start: Int = 0, shuffle: Boolean = false) {
        if (items.isEmpty()) return
        val q = if (shuffle) items.shuffled() else items
        cancelRoutineTimers(); gain = 1.0
        _state.update { it.copy(queue = q, index = start.coerceIn(0, q.lastIndex), shuffle = shuffle, error = null) }
        load(autoplay = true)
    }

    fun append(items: List<DMedia>) {
        if (_state.value.queue.isEmpty()) { playQueue(items); return }
        _state.update { it.copy(queue = it.queue + items) }
    }

    /** Stop playback and clear the queue (player bar close button). */
    fun close() {
        cancelRoutineTimers()
        fx { mediaPlayer?.let { it.stop(); it.dispose() }; mediaPlayer = null; onPlayerChanged?.invoke(null) }
        _state.update { it.copy(queue = emptyList(), index = -1, playing = false, positionMs = 0, durationMs = 0, error = null) }
    }

    fun toggle() = fx { val mp = mediaPlayer ?: return@fx; if (mp.status == MediaPlayer.Status.PLAYING) mp.pause() else mp.play() }
    fun pause() = fx { mediaPlayer?.pause() }
    fun next() { if (advance(+1)) load(true) }
    fun previous() {
        if (_state.value.positionMs > 3000) { seek(0); return }
        if (advance(-1)) load(true)
    }
    fun jump(i: Int) { _state.update { it.copy(index = i.coerceIn(0, it.queue.lastIndex)) }; load(true) }
    fun seek(ms: Long) = fx { mediaPlayer?.seek(Duration.millis(ms.toDouble())) }
    fun setVolume(v: Double) { _state.update { it.copy(volume = v.coerceIn(0.0, 1.0)) }; fx { mediaPlayer?.volume = effectiveVolume() } }
    fun toggleRepeat() = _state.update { it.copy(repeat = !it.repeat) }

    /** Routine helpers: fade in over [seconds] and pause after [minutes]. */
    fun applyRoutineTiming(fadeInSeconds: Int, stopAfterMinutes: Int?) {
        cancelRoutineTimers()
        if (fadeInSeconds > 0) {
            gain = 0.0; fx { mediaPlayer?.volume = 0.0 }
            fadeJob = scope.launch {
                val steps = fadeInSeconds * 10
                for (i in 1..steps) { delay(100); gain = i / steps.toDouble(); fx { mediaPlayer?.volume = effectiveVolume() } }
            }
        }
        stopAfterMinutes?.let { m ->
            stopJob = scope.launch {
                delay(m * 60_000L - 4_000)
                for (i in 1..20) { delay(200); gain = 1 - i / 20.0; fx { mediaPlayer?.volume = effectiveVolume() } }
                pause(); gain = 1.0; fx { mediaPlayer?.volume = effectiveVolume() }
            }
        }
    }

    private fun cancelRoutineTimers() { stopJob?.cancel(); fadeJob?.cancel() }
    private fun effectiveVolume() = _state.value.volume * gain

    private fun advance(delta: Int): Boolean {
        val s = _state.value
        if (s.queue.isEmpty()) return false
        var i = s.index + delta
        if (i > s.queue.lastIndex) { if (s.repeat) i = 0 else { _state.update { it.copy(playing = false) }; return false } }
        if (i < 0) i = 0
        _state.update { it.copy(index = i) }
        return true
    }

    private fun load(autoplay: Boolean) {
        val item = _state.value.current ?: return
        fx {
            mediaPlayer?.let { it.stop(); it.dispose() }
            val mp = try { MediaPlayer(Media(item.uri)) } catch (e: Exception) {
                _state.update { it.copy(error = "${item.title}: ${e.message ?: "unsupported media"}", playing = false) }
                scope.launch { delay(800); next() }
                return@fx
            }
            mediaPlayer = mp
            mp.volume = effectiveVolume()
            mp.setOnPlaying { _state.update { it.copy(playing = true, error = null) } }
            mp.setOnPaused { _state.update { it.copy(playing = false) } }
            mp.setOnStopped { _state.update { it.copy(playing = false) } }
            mp.setOnEndOfMedia { next() }
            mp.setOnError {
                val msg = mp.error?.message ?: "Playback error"
                _state.update { it.copy(error = "${item.title}: $msg", playing = false) }
                scope.launch { delay(800); next() }
            }
            onPlayerChanged?.invoke(mp)
            if (autoplay) mp.play()
        }
    }

    val isPlaying: Boolean get() = _state.value.playing
    val currentIsVideo: Boolean get() = _state.value.current?.kind == MediaKind.VIDEO

    companion object {
        @Volatile private var fxStarted = false
        fun startFx() {
            if (fxStarted) return
            synchronized(this) {
                if (fxStarted) return
                val latch = CountDownLatch(1)
                try { Platform.startup { latch.countDown() } } catch (_: IllegalStateException) { latch.countDown() }
                latch.await(20, TimeUnit.SECONDS)
                Platform.setImplicitExit(false)
                fxStarted = true
            }
        }
        fun fx(block: () -> Unit) { if (Platform.isFxApplicationThread()) block() else Platform.runLater(block) }
    }
}
