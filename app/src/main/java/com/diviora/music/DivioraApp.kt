package com.diviora.music

import android.app.Application
import android.content.Context
import com.diviora.music.data.db.AppDatabase
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
    val player = PlayerConnection(context, appScope)
}

class DivioraApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        RoutineNotifications.ensureChannel(this)
        WatchdogWorker.enqueue(this)
        container.appScope.launch { container.scheduler.rescheduleAll("app start") }
    }
}

val Context.container: AppContainer get() = (applicationContext as DivioraApp).container
