package com.diviora.music.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.diviora.music.DivioraApp
import com.diviora.music.scheduler.RoutineNotifications
import java.util.concurrent.TimeUnit

/** Checks for a new app version once a day and notifies the user. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val info = (applicationContext as DivioraApp).container.updater.check() ?: return Result.success()
        RoutineNotifications.showOpenRoute(applicationContext, "DIVIORA Music ${info.versionName} is available", "Tap to update", "update")
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "app-update-check", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build(),
            )
        }
    }
}
