package com.diviora.music

import android.app.Application
import android.content.Context
import com.diviora.music.data.db.AppDatabase
import com.diviora.music.data.repo.CatalogRepository
import com.diviora.music.data.repo.LibraryRepository
import com.diviora.music.data.repo.RoutineRepository
import com.diviora.music.data.repo.SettingsStore
import com.diviora.music.playback.PlayerConnection
import com.diviora.music.scheduler.AlarmScheduler
import com.diviora.music.scheduler.ExecutionLog
import com.diviora.music.scheduler.RoutineNotifications
import com.diviora.music.scheduler.WatchdogWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Hand-written dependency container (see ADR 0004). */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.build(context)
    val scheduler = AlarmScheduler(context, db)
    val executionLog = ExecutionLog(db)
    val library = LibraryRepository(context, db)
    val routines = RoutineRepository(db, scheduler)
    val settings = SettingsStore(context)
    val catalogs = CatalogRepository(context, db, settings)
    val player = PlayerConnection(context, appScope)
}

class DivioraApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Keep the main thread free at startup: the playback service and first frame share it.
        container.appScope.launch {
            RoutineNotifications.ensureChannel(this@DivioraApp)
            WatchdogWorker.enqueue(this@DivioraApp)
            container.scheduler.rescheduleAll("app start")
            com.diviora.music.scheduler.CatalogSyncWorker.enqueue(this@DivioraApp)
            container.catalogs.syncAll()
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as DivioraApp).container
