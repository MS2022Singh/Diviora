package com.diviora.music.scheduler

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.diviora.music.DivioraApp
import java.util.concurrent.TimeUnit

/** Refreshes the developer content catalog every hour when online (plus at app start), so new content reaches users without an app update. */
class CatalogSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as DivioraApp).container.catalogs.syncAll()
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("catalog-sync") // old 6-hour schedule
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "catalog-sync-hourly", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<CatalogSyncWorker>(1, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build(),
            )
        }
    }
}
