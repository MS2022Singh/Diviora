package com.diviora.music.core.model

import kotlinx.serialization.Serializable

/** Kind of playable media. */
enum class MediaKind { AUDIO, VIDEO, STREAM }

/** Where a media item came from. Each source maps to a provider adapter. */
/** EMBED = a YouTube/Spotify/SoundCloud link played in that service's official embedded player (on screen only). */
enum class MediaSource { MEDIA_STORE, IMPORTED_FILE, URL, CATALOG, EMBED }

/** What a routine does if something is already playing when it fires. */
@Serializable
enum class ConflictPolicy { REPLACE, APPEND, SKIP_IF_PLAYING }

/** Which audio stream a routine uses. ALARM plays under Do Not Disturb and when media volume is muted. */
@Serializable
enum class AudioStream { MEDIA, ALARM }

/** Playback rules applied when a routine fires. */
@Serializable
data class RoutineRules(
    val shuffle: Boolean = false,
    val repeatQueue: Boolean = false,
    /** 0..100; null = leave the current volume unchanged. */
    val volumePercent: Int? = null,
    val fadeInSeconds: Int = 0,
    /** Stop after N minutes; null = play to the end of the queue. */
    val stopAfterMinutes: Int? = null,
    val conflictPolicy: ConflictPolicy = ConflictPolicy.REPLACE,
    val audioStream: AudioStream = AudioStream.MEDIA,
    /** Play an occurrence that was missed (device off) if we are at most this late. 0 = never. */
    val lateGraceMinutes: Int = 0,
)

/** Result of a routine execution, kept in the execution history. */
enum class ExecutionResult { PLAYED, SKIPPED, FAILED, MISSED }
