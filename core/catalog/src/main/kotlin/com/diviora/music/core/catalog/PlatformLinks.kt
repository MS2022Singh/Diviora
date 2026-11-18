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

    /** The platform for [url], or null if it looks like an ordinary web/media link. */
    fun platformFor(url: String): Platform? {
        val host = runCatching { URI(url.trim()).host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") }.getOrNull() ?: return null
        return platforms.firstOrNull { p -> p.hosts.any { h -> val hh = h.substringBefore('/'); host == hh || host.endsWith(".$hh") } }
    }
}

class PlatformLinkException(val platform: String, val url: String) :
    Exception("$platform links can't play inside DIVIORA (protected service). Opening it in the $platform app instead.")
