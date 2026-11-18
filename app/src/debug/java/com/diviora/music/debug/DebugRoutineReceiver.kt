package com.diviora.music.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.diviora.music.container
import com.diviora.music.core.model.RoutineRules
import com.diviora.music.data.repo.RoutineDraft
import com.diviora.music.scheduler.recurrence.Repeat
import com.diviora.music.scheduler.recurrence.SlotSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Debug-only test hook. Scans the library, then creates a one-time routine with up to 3 local items at the next
 * whole minute that is at least 60 s away. Requires the DUMP permission, which only the adb shell holds.
 */
class DebugRoutineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val c = context.container
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                c.library.scanMediaStore()
                val ids = c.db.media().recentLocalIds()
                val t = LocalDateTime.now().plusSeconds(119).withSecond(0).withNano(0)
                val id = c.routines.save(
                    RoutineDraft(name = "CLI test ${t.toLocalTime()}", mediaIds = ids.take(3),
                        slots = listOf(SlotSpec(t.hour * 60 + t.minute, Repeat.Once, t.toLocalDate().toEpochDay())),
                        rules = RoutineRules(fadeInSeconds = 2)),
                )
                Log.i(TAG, "created routine=$id items=${ids.take(3).size} at=$t")
            } catch (e: Exception) {
                Log.e(TAG, "failed", e)
            } finally { pending.finish() }
        }
    }
    companion object { private const val TAG = "DebugRoutine" }
}
