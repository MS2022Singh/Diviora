package com.diviora.music.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.diviora.music.MainActivity
import com.diviora.music.core.model.ExecutionResult
import com.diviora.music.data.db.AppDatabase
import com.diviora.music.data.db.RoutineExecutionEntity
import com.diviora.music.data.db.ScheduledAlarmEntity
import com.diviora.music.data.db.TimeSlotEntity
import com.diviora.music.data.repo.rules
import com.diviora.music.data.repo.spec
import com.diviora.music.scheduler.recurrence.RecurrenceEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * Registers one AlarmManager alarm per enabled time slot (its next occurrence only) and keeps the
 * `scheduled_alarm` table as a mirror for diagnostics and recovery.
 */
class AlarmScheduler(private val context: Context, private val db: AppDatabase) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val dao get() = db.routines()
    private val lock = Mutex()

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Re-registers every slot. It never starts playback, so it is safe to call from BOOT_COMPLETED (Android 15 rule). */
    suspend fun rescheduleAll(reason: String) = lock.withLock {
        Log.i(TAG, "rescheduleAll: $reason")
        val now = System.currentTimeMillis()
        val routines = dao.all().associateBy { it.id }
        val graced = HashSet<Long>()
        // 1. Occurrences that passed without firing (device off, app force-stopped).
        for (a in dao.scheduledAlarms()) {
            if (a.triggerAtMs < now - LATE_TOLERANCE_MS && dao.executionCount(a.slotId, a.triggerAtMs) == 0) {
                val grace = routines[a.routineId]?.rules()?.lateGraceMinutes ?: 0
                if (grace > 0 && now - a.triggerAtMs <= grace * 60_000L) {
                    // Still inside the grace window: fire soon through an alarm, which is allowed to start playback.
                    setAlarm(a.slotId, a.routineId, a.triggerAtMs, fireAt = now + 3_000)
                    graced += a.slotId
                    continue
                }
                dao.insertExecution(
                    RoutineExecutionEntity(routineId = a.routineId, slotId = a.slotId, occurrenceAtMs = a.triggerAtMs,
                        firedAtMs = now, result = ExecutionResult.MISSED, reason = "Device was off or the app was stopped"),
                )
            }
            if (a.triggerAtMs < now - LATE_TOLERANCE_MS) dao.deleteAlarm(a.slotId)
        }
        // 2. Register the next occurrence of every slot.
        val slots = dao.allSlots()
        val liveSlotIds = slots.map { it.id }.toSet()
        dao.scheduledAlarms().filter { it.slotId !in liveSlotIds }.forEach { cancelInternal(it.slotId) }
        for (slot in slots) if (slot.id !in graced) scheduleSlotInternal(slot, after = now)
    }

    suspend fun scheduleRoutine(routineId: Long) = lock.withLock {
        val now = System.currentTimeMillis()
        dao.slots(routineId).forEach { scheduleSlotInternal(it, now) }
    }

    /** Called after an occurrence was handled. Registers the occurrence that follows [afterMs]. */
    suspend fun scheduleNext(slotId: Long, afterMs: Long) = lock.withLock {
        val slot = dao.slot(slotId) ?: return@withLock cancelInternal(slotId)
        scheduleSlotInternal(slot, maxOf(afterMs, System.currentTimeMillis()))
    }

    suspend fun cancel(slotId: Long) = cancelInternal(slotId)

    private suspend fun scheduleSlotInternal(slot: TimeSlotEntity, after: Long) {
        val routine = dao.get(slot.routineId)
        val spec = slot.spec()
        if (routine == null || !routine.enabled || !slot.enabled || spec == null) return cancelInternal(slot.id)
        val next = RecurrenceEngine.nextOccurrence(spec, Instant.ofEpochMilli(after))?.toEpochMilli()
            ?: return cancelInternal(slot.id)
        setAlarm(slot.id, slot.routineId, next, fireAt = next)
    }

    private suspend fun setAlarm(slotId: Long, routineId: Long, occurrenceMs: Long, fireAt: Long) {
        val pi = alarmIntent(slotId, occurrenceMs)
        val exact = canScheduleExact()
        try {
            if (exact) {
                val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN, "routines"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(fireAt, show), pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "exact alarm refused, falling back", se)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
        }
        dao.upsertAlarm(ScheduledAlarmEntity(slotId, routineId, occurrenceMs, exact))
    }

    private suspend fun cancelInternal(slotId: Long) {
        alarmManager.cancel(alarmIntent(slotId, 0))
        dao.deleteAlarm(slotId)
    }

    private fun alarmIntent(slotId: Long, occurrenceMs: Long): PendingIntent {
        val i = Intent(context, RoutineAlarmReceiver::class.java)
            .setAction(RoutineAlarmReceiver.ACTION_FIRE)
            .putExtra(EXTRA_SLOT_ID, slotId)
            .putExtra(EXTRA_OCCURRENCE, occurrenceMs)
        return PendingIntent.getBroadcast(context, slotId.toInt(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        private const val TAG = "AlarmScheduler"
        const val EXTRA_SLOT_ID = "slotId"
        const val EXTRA_OCCURRENCE = "occurrenceAtMs"
        const val EXTRA_MANUAL = "manual"
        private const val LATE_TOLERANCE_MS = 2 * 60_000L
    }
}
