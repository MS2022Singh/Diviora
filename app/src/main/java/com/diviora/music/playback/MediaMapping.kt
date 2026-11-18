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
    val uri = Uri.parse(uri)
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
    if (kind == MediaKind.STREAM) {
        val u = this.uri.lowercase()
        when {
            ".m3u8" in u -> b.setMimeType(MimeTypes.APPLICATION_M3U8)
            ".mpd" in u -> b.setMimeType(MimeTypes.APPLICATION_MPD)
        }
    }
    return b.build()
}

fun MediaItem.isVideo(): Boolean =
    mediaMetadata.extras?.getString(EXTRA_KIND) == MediaKind.VIDEO.name ||
        mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_VIDEO
