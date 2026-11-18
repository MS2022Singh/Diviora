package com.diviora.music.scheduler

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.diviora.music.DivioraApp
import com.diviora.music.playback.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Fires when a routine time slot is due. Hands the work to the playback foreground service. */
class RoutineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val slotId = intent.getLongExtra(AlarmScheduler.EXTRA_SLOT_ID, -1)
        val occurrence = intent.getLongExtra(AlarmScheduler.EXTRA_OCCURRENCE, System.currentTimeMillis())
        if (slotId < 0) return
        Log.i(TAG, "alarm fired slot=$slotId occurrence=$occurrence")
        // Already playing in the foreground: run directly, no new foreground-service start needed.
        PlaybackService.instance?.let { svc ->
            if (svc.isForegroundPlaying()) { svc.runSlot(slotId, occurrence, manual = false); return }
        }
        val svcIntent = PlaybackService.runSlotIntent(context, slotId, occurrence, manual = false)
        try {
            ContextCompat.startForegroundService(context, svcIntent)
        } catch (e: Exception) {
            // Happens when the alarm was inexact (exact-alarm permission revoked), so background starts are not allowed.
            val notAllowed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException
            Log.w(TAG, "cannot start playback from background (notAllowed=$notAllowed)", e)
            val app = context.applicationContext as DivioraApp
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    RoutineNotifications.showTapToStart(context, slotId, occurrence)
                    app.container.executionLog.recordFailure(slotId, occurrence,
                        "Android blocked background start; tap the notification. Enable exact alarms in Settings.")
                    app.container.scheduler.scheduleNext(slotId, occurrence)
                } finally { pending.finish() }
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.diviora.music.action.FIRE_ROUTINE"
        private const val TAG = "RoutineAlarmReceiver"
    }
}

/** Re-registers alarms after reboot, app update, clock/time-zone change and exact-alarm permission change. */
class RecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as DivioraApp
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { app.container.scheduler.rescheduleAll(intent.action ?: "recovery") } finally { pending.finish() }
        }
    }
}
