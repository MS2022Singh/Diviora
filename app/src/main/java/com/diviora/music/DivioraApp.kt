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
    val updater = com.diviora.music.update.AppUpdater(context)
    val offline = com.diviora.music.data.repo.OfflineRepository(context, db, settings)
    val vault = com.diviora.music.security.KeyVault(context)
    val player = PlayerConnection(context, appScope).also { p ->
        // Signed (expiring) URLs are refreshed from the server just before playback.
        p.beforePlay = { items -> if (catalogs.needsRefresh(items)) { catalogs.syncAll(); db.media().getAll(items.map { it.id }).let { fresh -> items.map { i -> fresh.firstOrNull { it.id == i.id } ?: i } } } else items }
    }
}

class DivioraApp : Application() {
    lateinit var container: AppContainer
        private set

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Chromecast / Cast devices (needs Google Play services; harmless without them). The Cast button opens the
        // system output switcher where available, which also lists Bluetooth and wired outputs.
        runCatching {
            androidx.media3.cast.Cast.getSingletonInstance(this).initialize(
                androidx.media3.cast.CastParams.Builder().setShowSystemOutputSwitcherOnCastButtonClick(true).build(),
            )
        }.onFailure { android.util.Log.w("DivioraApp", "Cast unavailable", it) }
        // Keep the main thread free at startup: the playback service and first frame share it.
        container.appScope.launch {
            RoutineNotifications.ensureChannel(this@DivioraApp)
            WatchdogWorker.enqueue(this@DivioraApp)
            container.scheduler.rescheduleAll("app start")
            com.diviora.music.scheduler.CatalogSyncWorker.enqueue(this@DivioraApp)
            if (container.updater.enabled) com.diviora.music.update.UpdateWorker.enqueue(this@DivioraApp)
            container.catalogs.syncAll()
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as DivioraApp).container
