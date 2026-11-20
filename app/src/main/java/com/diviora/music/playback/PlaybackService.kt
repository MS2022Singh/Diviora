package com.diviora.music.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.diviora.music.DivioraApp
import com.diviora.music.MainActivity
import com.diviora.music.core.model.AudioStream
import com.diviora.music.data.db.HistoryEntity
import com.diviora.music.scheduler.AlarmScheduler
import com.diviora.music.scheduler.RoutineNotifications
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Media3 session service. It handles background playback, the media notification, lock-screen, headset and Bluetooth
 * controls, and runs AutoPlay Routines when an alarm fires.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    /** Local ExoPlayer (audio attributes, wake mode). */
    private lateinit var exo: ExoPlayer
    /** The session's player: plays locally, or on a Cast device while one is connected. */
    lateinit var player: Player
        private set
    private var stopJob: Job? = null
    private var fadeJob: Job? = null
    private val container get() = (application as DivioraApp).container

    override fun onCreate() {
        super.onCreate()
        exo = ExoPlayer.Builder(this)
            .setAudioAttributes(attributesFor(AudioStream.MEDIA), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player = runCatching { androidx.media3.cast.CastPlayer.Builder(this).setLocalPlayer(exo).build() as Player }
            .getOrElse { Log.w(TAG, "Cast player unavailable, local only", it); exo }
        player.addListener(historyListener)
        player.addListener(errorSkipper)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, "player"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(open).setCallback(SessionCallback()).build()
        instance = this
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RUN_SLOT) {
            // Must enter the foreground right away. Media3 takes over the notification once playback starts.
            ServiceCompat.startForeground(
                this, RoutineNotifications.ID_STARTING, RoutineNotifications.starting(this, "Starting routine…"),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
            )
            runSlot(
                intent.getLongExtra(AlarmScheduler.EXTRA_SLOT_ID, -1),
                intent.getLongExtra(AlarmScheduler.EXTRA_OCCURRENCE, System.currentTimeMillis()),
                intent.getBooleanExtra(AlarmScheduler.EXTRA_MANUAL, false),
                fromForegroundStart = true,
            )
        }
        return super.onStartCommand(intent, flags, startId)
    }

    fun isForegroundPlaying(): Boolean = player.isPlaying

    /** Runs a routine slot occurrence. Safe to call repeatedly: occurrences are de-duplicated in the DB. */
    fun runSlot(slotId: Long, occurrenceMs: Long, manual: Boolean, fromForegroundStart: Boolean = false) {
        scope.launch {
            val outcome = runCatching { RoutineRunner(this@PlaybackService, container).run(slotId, occurrenceMs, manual) }
                .getOrElse { Log.e(TAG, "routine failed", it); RoutineRunner.Outcome(false, null) }
            if (fromForegroundStart) {
                if (outcome.started) {
                    // Media3 has posted its own foreground notification. Remove the placeholder.
                    delay(1500)
                    NotificationManagerCompat.from(this@PlaybackService).cancel(RoutineNotifications.ID_STARTING)
                } else {
                    ServiceCompat.stopForeground(this@PlaybackService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    if (!player.isPlaying) stopSelf()
                }
            }
        }
    }

    /** Applies a routine's queue and rules to the player. Called on the main thread. */
    fun startRoutinePlayback(
        items: List<MediaItem>, append: Boolean, shuffle: Boolean, repeat: Boolean, stream: AudioStream,
        volumePercent: Int?, fadeInSeconds: Int, stopAfterMinutes: Int?,
    ) {
        stopJob?.cancel(); fadeJob?.cancel()
        exo.setAudioAttributes(attributesFor(stream), true)
        volumePercent?.let { setStreamVolume(stream, it) }
        if (append && player.mediaItemCount > 0) {
            player.addMediaItems(items)
        } else {
            player.setMediaItems(items, 0, 0L)
        }
        player.shuffleModeEnabled = shuffle
        player.repeatMode = if (repeat) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        player.prepare()
        if (fadeInSeconds > 0) {
            player.volume = 0f
            fadeJob = scope.launch {
                val steps = fadeInSeconds * 10
                for (i in 1..steps) { delay(100); if (!isActive) break; player.volume = i / steps.toFloat() }
                player.volume = 1f
            }
        } else {
            player.volume = 1f
        }
        consecutiveErrors = 0
        player.play()
        stopAfterMinutes?.let { minutes ->
            stopJob = scope.launch {
                delay(minutes * 60_000L - FADE_OUT_MS)
                val start = player.volume
                for (i in 1..20) { delay(FADE_OUT_MS / 20); player.volume = start * (1 - i / 20f) }
                player.pause(); player.volume = 1f
            }
        }
    }

    private fun setStreamVolume(stream: AudioStream, percent: Int) {
        val am = getSystemService(AudioManager::class.java)
        val type = if (stream == AudioStream.ALARM) AudioManager.STREAM_ALARM else AudioManager.STREAM_MUSIC
        runCatching {
            val max = am.getStreamMaxVolume(type)
            am.setStreamVolume(type, (max * percent.coerceIn(0, 100) / 100f).toInt().coerceAtLeast(if (percent > 0) 1 else 0), 0)
        }.onFailure { Log.w(TAG, "volume change refused (Do Not Disturb?)", it) }
    }

    private fun attributesFor(stream: AudioStream): AudioAttributes = AudioAttributes.Builder()
        .setUsage(if (stream == AudioStream.ALARM) C.USAGE_ALARM else C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    /** Media3 stops on an error. Skip a broken item (dead link, unsupported file) and keep the queue going. */
    private var consecutiveErrors = 0
    private val errorSkipper = object : Player.Listener {
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.w(TAG, "playback error on ${player.currentMediaItem?.mediaId}: ${error.errorCodeName}")
            consecutiveErrors++
            // A timeout means the device was briefly too busy: retry the same item once before skipping it.
            if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT && consecutiveErrors == 1) {
                player.prepare(); player.play(); return
            }
            if (consecutiveErrors < player.mediaItemCount && player.hasNextMediaItem()) {
                player.seekToNextMediaItem(); player.prepare(); player.play()
            }
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) consecutiveErrors = 0 }
    }

    private val historyListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = saveHistory(countPlay = true, item = mediaItem)
        override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) saveHistory(countPlay = false, item = player.currentMediaItem) }
    }

    private fun saveHistory(countPlay: Boolean, item: MediaItem?) {
        val id = item?.mediaId?.toLongOrNull() ?: return
        val pos = player.currentPosition
        scope.launch(Dispatchers.IO) {
            val dao = container.db.history()
            val old = dao.get(id)
            runCatching {
                dao.upsert(HistoryEntity(id, if (countPlay) 0 else pos, System.currentTimeMillis(),
                    (old?.playCount ?: 0) + if (countPlay) 1 else 0))
            }
        }
    }

    /** Session callback: rebuild URIs that controllers stripped, and reset to the media stream for manual playback. */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private inner class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            mediaItems.map { item ->
                val uri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri
                if (uri == null) item else item.buildUpon().setUri(uri).build()
            }.toMutableList(),
        )

        override fun onSetMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
            startIndex: Int, startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            stopJob?.cancel(); fadeJob?.cancel(); player.volume = 1f
            exo.setAudioAttributes(attributesFor(AudioStream.MEDIA), true)
            return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        session?.run { player.release(); if (player !== exo) exo.release(); release() }
        session = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val FADE_OUT_MS = 4_000L
        const val ACTION_RUN_SLOT = "com.diviora.music.action.RUN_SLOT"

        @Volatile var instance: PlaybackService? = null
            private set

        fun runSlotIntent(context: Context, slotId: Long, occurrence: Long, manual: Boolean) =
            Intent(context, PlaybackService::class.java).setAction(ACTION_RUN_SLOT)
                .putExtra(AlarmScheduler.EXTRA_SLOT_ID, slotId)
                .putExtra(AlarmScheduler.EXTRA_OCCURRENCE, occurrence)
                .putExtra(AlarmScheduler.EXTRA_MANUAL, manual)
    }
}
