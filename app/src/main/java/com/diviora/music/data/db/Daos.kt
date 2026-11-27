package com.diviora.music.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.diviora.music.core.model.MediaKind
import kotlinx.coroutines.flow.Flow

data class UriId(val uri: String, val id: Long)

data class GroupCount(val name: String, val count: Int, val artworkUri: String?)

data class HistoryMedia(val mediaId: Long, val lastPlayedAtMs: Long, val playCount: Int)

@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE available = 1 AND kind = :kind ORDER BY title COLLATE NOCASE")
    fun observeByKind(kind: MediaKind): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE available = 1 ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE available = 1 AND favorite = 1 ORDER BY title COLLATE NOCASE")
    fun observeFavorites(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE available = 1 ORDER BY dateAddedMs DESC LIMIT :limit")
    fun observeRecentlyAdded(limit: Int): Flow<List<MediaEntity>>

    @Query(
        """SELECT m.* FROM media m JOIN history h ON h.mediaId = m.id
           WHERE m.available = 1 ORDER BY h.lastPlayedAtMs DESC LIMIT :limit""",
    )
    fun observeRecentlyPlayed(limit: Int): Flow<List<MediaEntity>>

    @Query(
        """SELECT * FROM media WHERE available = 1 AND
           (title LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%' OR album LIKE '%' || :q || '%')
           ORDER BY title COLLATE NOCASE LIMIT 300""",
    )
    fun search(q: String): Flow<List<MediaEntity>>

    @Query(
        """SELECT album AS name, COUNT(*) AS count, MAX(artworkUri) AS artworkUri FROM media
           WHERE available = 1 AND kind = 'AUDIO' AND album IS NOT NULL GROUP BY album ORDER BY album COLLATE NOCASE""",
    )
    fun observeAlbums(): Flow<List<GroupCount>>

    @Query(
        """SELECT artist AS name, COUNT(*) AS count, MAX(artworkUri) AS artworkUri FROM media
           WHERE available = 1 AND kind = 'AUDIO' AND artist IS NOT NULL GROUP BY artist ORDER BY artist COLLATE NOCASE""",
    )
    fun observeArtists(): Flow<List<GroupCount>>

    @Query("SELECT * FROM media WHERE available = 1 AND album = :album ORDER BY title COLLATE NOCASE")
    fun observeAlbum(album: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE available = 1 AND artist = :artist ORDER BY album, title COLLATE NOCASE")
    fun observeArtist(artist: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun get(id: Long): MediaEntity?

    /** Local files only (no online/catalog/stream items), newest first: used by the quick test routine. */
    @Query("SELECT id FROM media WHERE available = 1 AND uri NOT LIKE 'http%' ORDER BY dateAddedMs DESC LIMIT 20")
    suspend fun recentLocalIds(): List<Long>

    @Query("SELECT * FROM media WHERE id IN (:ids)")
    suspend fun getAll(ids: List<Long>): List<MediaEntity>

    @Query("SELECT * FROM media WHERE id = :id")
    fun observe(id: Long): Flow<MediaEntity?>

    @Query("SELECT * FROM media WHERE available = 1 AND source = 'CATALOG' ORDER BY catalogUrl, dateAddedMs")
    fun observeCatalogItems(): Flow<List<MediaEntity>>

    @Query("SELECT uri, id FROM media WHERE catalogUrl = :catalogUrl")
    suspend fun catalogUris(catalogUrl: String): List<UriId>

    @Query("UPDATE media SET drmJson = :drmJson, urlExpiresAt = :expiresAt, uri = :uri WHERE id = :id")
    suspend fun setProtection(id: Long, uri: String, drmJson: String?, expiresAt: Long?)

    @Query("SELECT * FROM media WHERE catalogUrl = :catalogUrl AND kind = :kind AND title = :title LIMIT 1")
    suspend fun findCatalogItem(catalogUrl: String, kind: com.diviora.music.core.model.MediaKind, title: String): MediaEntity?

    @Query("UPDATE media SET downloadAllowed = :allowed WHERE id = :id")
    suspend fun setDownloadAllowed(id: Long, allowed: Boolean)

    @Query("UPDATE media SET downloadId = :downloadId, offlinePath = :path WHERE id = :id")
    suspend fun setOffline(id: Long, downloadId: Long?, path: String?)

    @Query("SELECT * FROM media WHERE downloadId = :downloadId")
    suspend fun byDownloadId(downloadId: Long): MediaEntity?

    @Query("SELECT * FROM media WHERE offlinePath IS NOT NULL OR downloadId IS NOT NULL")
    suspend fun withOffline(): List<MediaEntity>

    @Query("SELECT * FROM media WHERE catalogUrl = :catalogUrl AND (offlinePath IS NOT NULL OR downloadId IS NOT NULL)")
    suspend fun offlineInCatalog(catalogUrl: String): List<MediaEntity>

    @Query("DELETE FROM media WHERE catalogUrl = :catalogUrl")
    suspend fun deleteCatalog(catalogUrl: String)

    @Query("SELECT uri, id FROM media WHERE source = 'MEDIA_STORE'")
    suspend fun mediaStoreUris(): List<UriId>

    @Query("SELECT id FROM media WHERE uri = :uri")
    suspend fun idForUri(uri: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: MediaEntity): Long

    @Update
    suspend fun update(item: MediaEntity)

    @Query(
        """UPDATE media SET title = :title, artist = :artist, album = :album, durationMs = :durationMs,
           artworkUri = :artworkUri, mimeType = :mimeType, sizeBytes = :sizeBytes, available = 1 WHERE id = :id""",
    )
    suspend fun refreshMetadata(
        id: Long, title: String, artist: String?, album: String?, durationMs: Long,
        artworkUri: String?, mimeType: String?, sizeBytes: Long?,
    )

    @Query("UPDATE media SET available = 0 WHERE id IN (:ids)")
    suspend fun markUnavailable(ids: List<Long>)

    @Query("UPDATE media SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM media WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM media WHERE available = 1")
    fun observeCount(): Flow<Int>
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlist ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Insert suspend fun insert(p: PlaylistEntity): Long

    @Query("SELECT id FROM playlist WHERE name = :name LIMIT 1") suspend fun findByName(name: String): Long?

    @Query("DELETE FROM playlist WHERE id = :id") suspend fun delete(id: Long)

    @Query("UPDATE playlist SET name = :name WHERE id = :id") suspend fun rename(id: Long, name: String)

    @Query(
        """SELECT m.* FROM playlist_entry e JOIN media m ON m.id = e.mediaId
           WHERE e.playlistId = :playlistId ORDER BY e.position""",
    )
    fun observeItems(playlistId: Long): Flow<List<MediaEntity>>

    @Query("SELECT mediaId FROM playlist_entry WHERE playlistId = :playlistId ORDER BY position")
    suspend fun itemIds(playlistId: Long): List<Long>

    @Query("DELETE FROM playlist_entry WHERE playlistId = :playlistId") suspend fun clear(playlistId: Long)

    @Insert suspend fun insertEntries(entries: List<PlaylistEntryEntity>)

    @Transaction
    suspend fun replaceItems(playlistId: Long, mediaIds: List<Long>) {
        clear(playlistId)
        insertEntries(mediaIds.mapIndexed { i, id -> PlaylistEntryEntity(playlistId, i, id) })
    }
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history WHERE mediaId = :mediaId") suspend fun get(mediaId: Long): HistoryEntity?
    @Upsert suspend fun upsert(h: HistoryEntity)
}

data class RoutineWithCounts(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val rulesJson: String,
    val itemCount: Int,
    val nextTriggerAtMs: Long?,
)

@Dao
interface RoutineDao {
    @Query(
        """SELECT r.id, r.name, r.enabled, r.rulesJson,
             (SELECT COUNT(*) FROM routine_item i WHERE i.routineId = r.id) AS itemCount,
             (SELECT MIN(a.triggerAtMs) FROM scheduled_alarm a WHERE a.routineId = r.id) AS nextTriggerAtMs
           FROM routine r ORDER BY r.name COLLATE NOCASE""",
    )
    fun observeRoutines(): Flow<List<RoutineWithCounts>>

    @Query("SELECT * FROM routine WHERE id = :id") suspend fun get(id: Long): RoutineEntity?
    @Query("SELECT * FROM routine") suspend fun all(): List<RoutineEntity>
    @Insert suspend fun insert(r: RoutineEntity): Long
    @Update suspend fun update(r: RoutineEntity)
    @Query("UPDATE routine SET enabled = :enabled WHERE id = :id") suspend fun setEnabled(id: Long, enabled: Boolean)
    @Query("DELETE FROM routine WHERE id = :id") suspend fun delete(id: Long)

    @Query("SELECT * FROM time_slot WHERE routineId = :routineId ORDER BY id") suspend fun slots(routineId: Long): List<TimeSlotEntity>
    @Query("SELECT * FROM time_slot WHERE routineId = :routineId ORDER BY id") fun observeSlots(routineId: Long): Flow<List<TimeSlotEntity>>
    @Query("SELECT * FROM time_slot") suspend fun allSlots(): List<TimeSlotEntity>
    @Query("SELECT * FROM time_slot") fun observeAllSlots(): Flow<List<TimeSlotEntity>>
    @Query("SELECT * FROM time_slot WHERE id = :id") suspend fun slot(id: Long): TimeSlotEntity?
    @Insert suspend fun insertSlots(slots: List<TimeSlotEntity>): List<Long>
    @Query("DELETE FROM time_slot WHERE routineId = :routineId") suspend fun deleteSlots(routineId: Long)
    @Query("UPDATE time_slot SET skipOccurrenceAtMs = :at WHERE id = :slotId") suspend fun setSkip(slotId: Long, at: Long?)

    @Query(
        """SELECT m.* FROM routine_item i JOIN media m ON m.id = i.mediaId
           WHERE i.routineId = :routineId ORDER BY i.position""",
    )
    suspend fun items(routineId: Long): List<MediaEntity>
    @Query("DELETE FROM routine_item WHERE routineId = :routineId") suspend fun deleteItems(routineId: Long)
    @Insert suspend fun insertItems(items: List<RoutineItemEntity>)

    @Query("SELECT * FROM scheduled_alarm") suspend fun scheduledAlarms(): List<ScheduledAlarmEntity>
    @Query("SELECT * FROM scheduled_alarm ORDER BY triggerAtMs LIMIT 1") fun observeNextAlarm(): Flow<ScheduledAlarmEntity?>
    @Upsert suspend fun upsertAlarm(a: ScheduledAlarmEntity)
    @Query("DELETE FROM scheduled_alarm WHERE slotId = :slotId") suspend fun deleteAlarm(slotId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertExecution(e: RoutineExecutionEntity): Long
    @Update suspend fun updateExecution(e: RoutineExecutionEntity)
    @Query("SELECT COUNT(*) FROM routine_execution WHERE slotId = :slotId AND occurrenceAtMs = :at") suspend fun executionCount(slotId: Long, at: Long): Int
    @Query("SELECT * FROM routine_execution ORDER BY firedAtMs DESC LIMIT :limit") fun observeExecutions(limit: Int): Flow<List<RoutineExecutionEntity>>
}

@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalog_source ORDER BY builtIn DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<CatalogSourceEntity>>
    @Query("SELECT * FROM catalog_source") suspend fun all(): List<CatalogSourceEntity>
    @Query("SELECT * FROM catalog_source WHERE url = :url") suspend fun get(url: String): CatalogSourceEntity?
    @Upsert suspend fun upsert(c: CatalogSourceEntity)
    @Query("DELETE FROM catalog_source WHERE url = :url") suspend fun delete(url: String)
}
