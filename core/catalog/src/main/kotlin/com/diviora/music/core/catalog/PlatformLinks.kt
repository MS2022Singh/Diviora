package com.diviora.music.core.catalog

import java.net.URI

/**
 * Recognises links to streaming platforms whose content cannot legally be played inside DIVIORA's own player
 * (protected streams, no public playback API). These links are handed off to the platform's own app instead.
 */
object PlatformLinks {
    data class Platform(val name: String, val hosts: List<String>)

    private val platforms = listOf(
        Platform("YouTube", listOf("youtube.com", "youtu.be", "music.youtube.com", "youtube-nocookie.com")),
        Platform("Spotify", listOf("spotify.com", "spotify.link")),
        Platform("Amazon Music", listOf("music.amazon.com", "music.amazon.in", "music.amazon.co.uk", "amazon.com/music")),
        Platform("Apple Music", listOf("music.apple.com")),
        Platform("Gaana", listOf("gaana.com")),
        Platform("JioSaavn", listOf("jiosaavn.com", "saavn.com")),
        Platform("Wynk", listOf("wynk.in")),
        Platform("SoundCloud", listOf("soundcloud.com", "on.soundcloud.com")),
        Platform("Deezer", listOf("deezer.com", "deezer.page.link")),
        Platform("Tidal", listOf("tidal.com")),
        Platform("Netflix", listOf("netflix.com")),
        Platform("Prime Video", listOf("primevideo.com")),
    )

    /** An official embeddable player for a platform link. Plays on screen only (platform terms forbid background play). */
    data class Embed(val platform: String, val kind: String, val id: String, val embedUrl: String, val isVideo: Boolean)

    /**
     * YouTube (single videos), Spotify (track/album/playlist/episode/show) and SoundCloud links can play inside the app
     * through the platform's own embedded player. Other platforms return null and are opened in their app instead.
     */
    fun embedFor(url: String): Embed? {
        val u = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val host = u.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
        val path = u.path.orEmpty().trim('/')
        val query = u.rawQuery.orEmpty().split('&').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        fun ytId(id: String?) = id?.takeIf { YT_ID.matches(it) }
        when {
            host == "youtu.be" -> ytId(path.substringBefore('/'))
            host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com") -> when {
                path == "watch" -> ytId(query["v"])
                path.startsWith("shorts/") || path.startsWith("embed/") || path.startsWith("live/") || path.startsWith("v/") -> ytId(path.substringAfter('/').substringBefore('/'))
                else -> null
            }
            else -> null
        }?.let { return Embed("YouTube", "video", it, "https://www.youtube.com/embed/$it", isVideo = true) }

        if (host == "open.spotify.com") {
            val parts = path.split('/').filter { it.isNotEmpty() && !it.startsWith("intl-") }
            val type = parts.getOrNull(0); val id = parts.getOrNull(1)
            if (type in SPOTIFY_TYPES && id != null && SPOTIFY_ID.matches(id)) {
                return Embed("Spotify", type!!, id, "https://open.spotify.com/embed/$type/$id", isVideo = type == "episode" || type == "show")
            }
        }
        if (host == "soundcloud.com" && path.count { it == '/' } >= 1) {
            val enc = java.net.URLEncoder.encode("https://soundcloud.com/$path", "UTF-8")
            return Embed("SoundCloud", "track", path, "https://w.soundcloud.com/player/?url=$enc&auto_play=true&visual=true", isVideo = false)
        }
        return null
    }

    private val YT_ID = Regex("[A-Za-z0-9_-]{11}")
    private val SPOTIFY_ID = Regex("[A-Za-z0-9]{22}")
    private val SPOTIFY_TYPES = setOf("track", "album", "playlist", "episode", "show", "artist")

    /** The platform for [url], or null if it looks like an ordinary web/media link. */
    fun platformFor(url: String): Platform? {
        val host = runCatching { URI(url.trim()).host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") }.getOrNull() ?: return null
        return platforms.firstOrNull { p -> p.hosts.any { h -> val hh = h.substringBefore('/'); host == hh || host.endsWith(".$hh") } }
    }
}

class PlatformLinkException(val platform: String, val url: String) :
    Exception("$platform has no embeddable player, so this link opens in the $platform app.")
