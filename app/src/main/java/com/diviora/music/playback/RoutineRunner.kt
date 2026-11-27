package com.diviora.music.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.diviora.music.AppContainer
import com.diviora.music.core.model.ConflictPolicy
import com.diviora.music.core.model.ExecutionResult
import com.diviora.music.core.model.MediaKind
import com.diviora.music.data.repo.rules
import com.diviora.music.scheduler.RoutineNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Executes one routine occurrence. Every decision is written to the execution history. */
class RoutineRunner(private val service: PlaybackService, private val c: AppContainer) {
    data class Outcome(val started: Boolean, val reason: String?)

    suspend fun run(slotId: Long, occurrenceMs: Long, manual: Boolean): Outcome {
        val dao = c.db.routines()
        val slot = withContext(Dispatchers.IO) { dao.slot(slotId) } ?: return Outcome(false, "Slot deleted")
        val routine = withContext(Dispatchers.IO) { dao.get(slot.routineId) } ?: return Outcome(false, "Routine deleted")
        // Manual runs ("Run now") use a unique occurrence key so they never collide with scheduled ones.
        val occurrence = if (manual) System.currentTimeMillis() else occurrenceMs
        val execId = withContext(Dispatchers.IO) { c.executionLog.claim(routine.id, slotId, occurrence) }
            ?: return Outcome(false, "Already handled").also { Log.i(TAG, "duplicate occurrence ignored") }
        val rules = routine.rules()

        suspend fun finish(result: ExecutionResult, reason: String?, items: Int = 0): Outcome {
            Log.i(TAG, "routine=${routine.id} slot=$slotId occurrence=$occurrence result=$result items=$items reason=$reason")
            withContext(Dispatchers.IO) {
                c.executionLog.finish(execId, routine.id, slotId, occurrence, result, reason, items)
                if (slot.skipOccurrenceAtMs == occurrenceMs) dao.setSkip(slotId, null)
                if (!manual) c.scheduler.scheduleNext(slotId, occurrenceMs)
            }
            if (result == ExecutionResult.FAILED) RoutineNotifications.showFailure(service, "${routine.name}: $reason")
            return Outcome(result == ExecutionResult.PLAYED, reason)
        }

        if (!manual) {
            if (!routine.enabled || !slot.enabled) return finish(ExecutionResult.SKIPPED, "Routine disabled")
            if (slot.skipOccurrenceAtMs == occurrenceMs) return finish(ExecutionResult.SKIPPED, "Skipped by user")
            val lateMs = System.currentTimeMillis() - occurrenceMs
            val allowed = maxOf(rules.lateGraceMinutes * 60_000L, DEFAULT_LATE_MS)
            if (lateMs > allowed) return finish(ExecutionResult.MISSED, "Fired ${lateMs / 60_000} min late")
        }
        if (rules.conflictPolicy == ConflictPolicy.SKIP_IF_PLAYING && service.player.isPlaying) {
            return finish(ExecutionResult.SKIPPED, "Something else was playing")
        }

        val withEmbeds = withContext(Dispatchers.IO) { dao.items(routine.id) }
        val embeds = withEmbeds.filter { it.source == com.diviora.music.core.model.MediaSource.EMBED }
        val allRaw = withEmbeds - embeds.toSet()
        // Signed (expiring) URLs: refresh from the server before the routine plays.
        val all = withContext(Dispatchers.IO) {
            if (c.catalogs.needsRefresh(allRaw)) { c.catalogs.syncAll(); dao.items(routine.id).filter { it.source != com.diviora.music.core.model.MediaSource.EMBED } } else allRaw
        }
        if (all.isEmpty() && embeds.isNotEmpty()) {
            // YouTube/Spotify/SoundCloud may only play on screen: ask the user to tap.
            RoutineNotifications.showOpenRoute(service, "${routine.name}: tap to play", embeds.first().title, "embed/${embeds.first().id}")
            return finish(ExecutionResult.SKIPPED, "${embeds.first().artist} plays on screen only; a tap-to-play notification was shown")
        }
        val online = isOnline(service)
        val playable = withContext(Dispatchers.IO) {
            all.filter { m -> m.available && (m.offlinePath?.let { java.io.File(it).exists() } == true || (if (m.uri.startsWith("http")) online else c.library.isReadable(m))) }
        }
        if (playable.isEmpty()) {
            val why = when {
                all.isEmpty() -> "Routine has no items"
                all.any { it.uri.startsWith("http") } && !online -> "Offline; no local items available"
                else -> "Files are missing or access was revoked"
            }
            return finish(ExecutionResult.FAILED, why)
        }
        service.startRoutinePlayback(
            items = playable.map { it.toMediaItem() },
            append = rules.conflictPolicy == ConflictPolicy.APPEND,
            shuffle = rules.shuffle, repeat = rules.repeatQueue, stream = rules.audioStream,
            volumePercent = rules.volumePercent, fadeInSeconds = rules.fadeInSeconds, stopAfterMinutes = rules.stopAfterMinutes,
        )
        // Confirm audio really started: dead links or unsupported files must not be reported as PLAYED.
        val started = withTimeoutOrNull(START_TIMEOUT_MS) {
            while (!service.player.isPlaying) {
                val err = service.player.playerError
                if (err != null && !service.player.hasNextMediaItem()) return@withTimeoutOrNull false
                delay(250)
            }
            true
        } ?: false
        if (!started) {
            val why = service.player.playerError?.errorCodeName?.let { "Could not play: $it" } ?: "Playback did not start within 30 s"
            return finish(ExecutionResult.FAILED, why)
        }
        playable.firstOrNull { it.kind == MediaKind.VIDEO }?.let { RoutineNotifications.showVideoReady(service, it.title) }
        val skipped = all.size - playable.size
        return finish(ExecutionResult.PLAYED, if (skipped > 0) "$skipped unavailable item(s) skipped" else null, playable.size)
    }

    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val TAG = "RoutineRunner"
        private const val DEFAULT_LATE_MS = 10 * 60_000L
        private const val START_TIMEOUT_MS = 30_000L
    }
}
