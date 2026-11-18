package com.diviora.music.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.diviora.music.core.model.ExecutionResult
import com.diviora.music.core.model.MediaKind
import com.diviora.music.core.model.MediaSource

@Entity(tableName = "media", indices = [Index(value = ["uri"], unique = true), Index("album"), Index("artist")])
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val kind: MediaKind,
    val source: MediaSource,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0,
    val artworkUri: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val dateAddedMs: Long = System.currentTimeMillis(),
    val favorite: Boolean = false,
    val available: Boolean = true,
    /** For [MediaSource.CATALOG] items: the catalog source URL they came from. */
    val catalogUrl: String? = null,
)

@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtMs: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "playlist_entry",
    primaryKeys = ["playlistId", "position"],
    foreignKeys = [
        ForeignKey(PlaylistEntity::class, ["id"], ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(MediaEntity::class, ["id"], ["mediaId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("mediaId")],
)
data class PlaylistEntryEntity(val playlistId: Long, val position: Int, val mediaId: Long)

@Entity(
    tableName = "history",
    foreignKeys = [ForeignKey(MediaEntity::class, ["id"], ["mediaId"], onDelete = ForeignKey.CASCADE)],
)
data class HistoryEntity(
    @PrimaryKey val mediaId: Long,
    val lastPositionMs: Long,
    val lastPlayedAtMs: Long,
    val playCount: Int,
)

@Entity(tableName = "routine")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    /** JSON-encoded [com.diviora.music.core.model.RoutineRules]. */
    val rulesJson: String,
    val createdAtMs: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "routine_item",
    primaryKeys = ["routineId", "position"],
    foreignKeys = [
        ForeignKey(RoutineEntity::class, ["id"], ["routineId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(MediaEntity::class, ["id"], ["mediaId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("mediaId")],
)
data class RoutineItemEntity(val routineId: Long, val position: Int, val mediaId: Long)

@Entity(
    tableName = "time_slot",
    foreignKeys = [ForeignKey(RoutineEntity::class, ["id"], ["routineId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("routineId")],
)
data class TimeSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    /** JSON-encoded [com.diviora.music.scheduler.recurrence.SlotSpec]. */
    val specJson: String,
    val enabled: Boolean = true,
    /** Occurrence (epoch ms) that the user asked to skip, if any. */
    val skipOccurrenceAtMs: Long? = null,
)

/** Mirror of the alarm registered with AlarmManager for each slot (one pending occurrence per slot). */
@Entity(tableName = "scheduled_alarm")
data class ScheduledAlarmEntity(
    @PrimaryKey val slotId: Long,
    val routineId: Long,
    val triggerAtMs: Long,
    val exact: Boolean,
    val registeredAtMs: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "routine_execution",
    indices = [Index(value = ["slotId", "occurrenceAtMs"], unique = true), Index("routineId")],
)
data class RoutineExecutionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val slotId: Long,
    val occurrenceAtMs: Long,
    val firedAtMs: Long,
    val result: ExecutionResult,
    val reason: String? = null,
    val itemsQueued: Int = 0,
)

/** A developer/content-provider catalog (JSON over HTTPS) whose items appear in every user's library. */
@Entity(tableName = "catalog_source")
data class CatalogSourceEntity(
    @PrimaryKey val url: String,
    val name: String,
    /** Built-in sources come from the app build (BuildConfig.CATALOG_URL or the bundled demo) and cannot be removed. */
    val builtIn: Boolean = false,
    val itemCount: Int = 0,
    val lastSyncMs: Long? = null,
    val lastError: String? = null,
)
