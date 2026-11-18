package com.diviora.music.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.diviora.music.DivioraApp
import java.util.concurrent.TimeUnit

/** Periodic safety net: re-derives every slot's next alarm so drift or OEM alarm loss repairs itself. */
class WatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as DivioraApp).container.scheduler.rescheduleAll("watchdog")
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "routine-watchdog", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WatchdogWorker>(12, TimeUnit.HOURS).build(),
            )
        }
    }
}
