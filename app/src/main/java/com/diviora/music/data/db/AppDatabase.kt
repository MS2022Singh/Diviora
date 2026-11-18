package com.diviora.music.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MediaEntity::class, PlaylistEntity::class, PlaylistEntryEntity::class, HistoryEntity::class,
        RoutineEntity::class, RoutineItemEntity::class, TimeSlotEntity::class,
        ScheduledAlarmEntity::class, RoutineExecutionEntity::class, CatalogSourceEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun media(): MediaDao
    abstract fun playlists(): PlaylistDao
    abstract fun history(): HistoryDao
    abstract fun routines(): RoutineDao
    abstract fun catalogs(): CatalogDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "diviora.db").addMigrations(MIGRATION_1_2).build()
    }
}

/** v2: developer content catalogs. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE media ADD COLUMN catalogUrl TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS catalog_source (url TEXT NOT NULL, name TEXT NOT NULL, builtIn INTEGER NOT NULL, " +
                "itemCount INTEGER NOT NULL, lastSyncMs INTEGER, lastError TEXT, PRIMARY KEY(url))",
        )
    }
}
