package com.diviora.music.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.diviora.music.core.model.MediaKind
import com.diviora.music.data.db.MediaEntity

const val EXTRA_KIND = "diviora.kind"

/** Converts a library row to a Media3 item. The URI also goes in requestMetadata so it survives the controller IPC hop. */
fun MediaEntity.toMediaItem(): MediaItem {
    // Offline copy (developer-allowed download) plays without network.
    val offline = offlinePath?.let { java.io.File(it) }?.takeIf { it.exists() }
    val uri = offline?.let(Uri::fromFile) ?: Uri.parse(uri)
    val extras = android.os.Bundle().apply { putString(EXTRA_KIND, kind.name) }
    val meta = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(artworkUri?.let(Uri::parse))
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .setMediaType(if (kind == MediaKind.VIDEO) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_MUSIC)
        .setExtras(extras)
        .build()
    val b = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(meta)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).setExtras(extras).build())
    // Help the extractor for adaptive streams that have no file extension.
    // A MIME type helps the extractor for extension-less streams and is required by Cast receivers.
    (mimeType ?: guessMime(this.uri, kind))?.let { b.setMimeType(it) }
    // Widevine DRM for protected online items (offline copies are AES-encrypted on the device instead).
    if (offline == null) drmJson?.let { json ->
        runCatching { com.diviora.music.data.repo.DivioraJson.decodeFromString(com.diviora.music.core.catalog.CatalogDrm.serializer(), json) }
            .getOrNull()?.let { d ->
                b.setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(androidx.media3.common.C.WIDEVINE_UUID)
                        .setLicenseUri(d.licenseUrl)
                        .setLicenseRequestHeaders(d.headers + com.diviora.music.security.ProtectedDataSourceFactory.contentHeaders())
                        .build(),
                )
            }
    }
    return b.build()
}

fun MediaItem.isVideo(): Boolean =
    mediaMetadata.extras?.getString(EXTRA_KIND) == MediaKind.VIDEO.name ||
        mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_VIDEO

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun guessMime(uri: String, kind: MediaKind): String? {
    val u = uri.lowercase().substringBefore('?')
    return when {
        ".m3u8" in u -> MimeTypes.APPLICATION_M3U8
        ".mpd" in u -> MimeTypes.APPLICATION_MPD
        u.endsWith(".mp3") -> MimeTypes.AUDIO_MPEG
        u.endsWith(".m4a") || u.endsWith(".aac") -> MimeTypes.AUDIO_MP4
        u.endsWith(".ogg") || u.endsWith(".opus") -> MimeTypes.AUDIO_OGG
        u.endsWith(".flac") -> MimeTypes.AUDIO_FLAC
        u.endsWith(".wav") -> MimeTypes.AUDIO_WAV
        u.endsWith(".mp4") || u.endsWith(".m4v") -> MimeTypes.VIDEO_MP4
        u.endsWith(".webm") -> MimeTypes.VIDEO_WEBM
        u.endsWith(".mkv") -> MimeTypes.VIDEO_MATROSKA
        kind == MediaKind.STREAM -> MimeTypes.AUDIO_MPEG
        else -> null
    }
}
