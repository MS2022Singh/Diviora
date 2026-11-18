package com.diviora.music.desktop

import com.diviora.music.core.model.ConflictPolicy
import com.diviora.music.core.model.ExecutionResult
import com.diviora.music.scheduler.recurrence.RecurrenceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI
import java.time.Instant

data class Upcoming(val routine: DRoutine, val slot: DSlot, val at: Instant)

/**
 * Desktop AutoPlay Routines. Uses the same RecurrenceEngine as Android. The app must be running (it keeps running
 * in the system tray when the window is closed). After sleep or a clock change, overdue occurrences are played if they
 * are inside the routine's late grace window (minimum 2 min), or else logged as MISSED.
 */
class Scheduler(private val store: Store, private val player: Player, private val clock: () -> Long = System::currentTimeMillis) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var lastCheckMs = clock()

    fun start() {
        scope.launch {
            while (isActive) {
                tick()
                delay(1_000)
            }
        }
    }

    /** One scheduling pass: fire every occurrence that fell due since the previous pass. Public for tests. */
    fun tick() {
        val now = clock()
        val from = Instant.ofEpochMilli(lastCheckMs - 1)
        for (r in store.value.routines) {
            if (!r.enabled) continue
            for (slot in r.slots) {
                if (!slot.enabled) continue
                var at = RecurrenceEngine.nextOccurrence(slot.spec, from) ?: continue
                while (at.toEpochMilli() <= now) {
                    fire(r, slot, at.toEpochMilli(), now)
                    at = RecurrenceEngine.nextOccurrence(slot.spec, at) ?: break
                }
            }
        }
        lastCheckMs = now
    }

    fun upcoming(limit: Int = 20): List<Upcoming> {
        val now = Instant.ofEpochMilli(clock())
        return store.value.routines.filter { it.enabled }.flatMap { r ->
            r.slots.filter { it.enabled }.flatMap { s -> RecurrenceEngine.upcoming(s.spec, now, 3).map { Upcoming(r, s, it) } }
        }.sortedBy { it.at }.take(limit)
    }

    fun runNow(routine: DRoutine) {
        val slot = routine.slots.firstOrNull() ?: DSlot(-1, com.diviora.music.scheduler.recurrence.SlotSpec(0,
            com.diviora.music.scheduler.recurrence.Repeat.Once, 0))
        fire(routine, slot, clock(), clock(), manual = true)
    }

    private fun fire(r: DRoutine, slot: DSlot, occurrenceMs: Long, now: Long, manual: Boolean = false) {
        if (!manual && store.value.executions.any { it.slotId == slot.id && it.occurrenceAtMs == occurrenceMs }) return
        fun log(result: ExecutionResult, reason: String?, items: Int = 0) = store.update {
            it.copy(executions = it.executions + DExecution(r.id, slot.id, occurrenceMs, now, result, reason, items))
        }
        val rules = r.rules
        if (!manual) {
            if (slot.skipOccurrenceAtMs == occurrenceMs) {
                store.update { d -> d.copy(routines = d.routines.map { x -> if (x.id == r.id) x.copy(slots = x.slots.map { if (it.id == slot.id) it.copy(skipOccurrenceAtMs = null) else it }) else x }) }
                return log(ExecutionResult.SKIPPED, "Skipped by user")
            }
            val late = now - occurrenceMs
            if (late > maxOf(rules.lateGraceMinutes * 60_000L, 2 * 60_000L)) return log(ExecutionResult.MISSED, "Computer was asleep or the app was closed")
        }
        if (rules.conflictPolicy == ConflictPolicy.SKIP_IF_PLAYING && player.isPlaying) return log(ExecutionResult.SKIPPED, "Something else was playing")
        val byId = store.value.media.associateBy { it.id }
        val all = r.mediaIds.mapNotNull { byId[it] }
        val playable = all.filter { m -> m.available && (m.uri.startsWith("http") || runCatching { File(URI(m.uri)).canRead() }.getOrDefault(false)) }
        if (playable.isEmpty()) return log(ExecutionResult.FAILED, if (all.isEmpty()) "Routine has no items" else "Files are missing")
        rules.volumePercent?.let { player.setVolume(it / 100.0) }
        if (rules.conflictPolicy == ConflictPolicy.APPEND && player.state.value.queue.isNotEmpty()) player.append(playable)
        else player.playQueue(playable, shuffle = rules.shuffle)
        if (rules.repeatQueue != player.state.value.repeat) player.toggleRepeat()
        player.applyRoutineTiming(rules.fadeInSeconds, rules.stopAfterMinutes)
        Notifier.show("${r.name} is playing", playable.first().title)
        log(ExecutionResult.PLAYED, (all.size - playable.size).takeIf { it > 0 }?.let { "$it unavailable item(s) skipped" }, playable.size)
    }
}

/** Desktop notification through the system tray, when available. */
object Notifier {
    var tray: ((String, String) -> Unit)? = null
    fun show(title: String, text: String) { runCatching { tray?.invoke(title, text) } }
}
