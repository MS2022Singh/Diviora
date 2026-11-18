package com.diviora.music.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MediaEntity::class, PlaylistEntity::class, PlaylistEntryEntity::class, HistoryEntity::class,
        RoutineEntity::class, RoutineItemEntity::class, TimeSlotEntity::class,
        ScheduledAlarmEntity::class, RoutineExecutionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun media(): MediaDao
    abstract fun playlists(): PlaylistDao
    abstract fun history(): HistoryDao
    abstract fun routines(): RoutineDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "diviora.db").build()
    }
}
