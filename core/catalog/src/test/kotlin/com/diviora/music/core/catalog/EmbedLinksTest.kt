package com.diviora.music.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmbedLinksTest {
    @Test fun youtubeForms() {
        for (u in listOf("https://youtu.be/TKl-Kg1qa5c", "https://www.youtube.com/watch?v=TKl-Kg1qa5c&t=3", "https://m.youtube.com/watch?v=TKl-Kg1qa5c",
            "https://music.youtube.com/watch?v=TKl-Kg1qa5c", "https://youtube.com/shorts/TKl-Kg1qa5c", "https://www.youtube.com/live/TKl-Kg1qa5c")) {
            val e = PlatformLinks.embedFor(u)
            assertEquals(u, "TKl-Kg1qa5c", e?.id)
            assertEquals("https://www.youtube.com/embed/TKl-Kg1qa5c", e?.embedUrl)
        }
        assertNull(PlatformLinks.embedFor("https://www.youtube.com/@somechannel"))
    }

    @Test fun spotifyAndSoundcloud() {
        val s = PlatformLinks.embedFor("https://open.spotify.com/intl-en/track/4uLU6hMCjMI75M1A2tKUQC?si=x")
        assertEquals("https://open.spotify.com/embed/track/4uLU6hMCjMI75M1A2tKUQC", s?.embedUrl)
        assertEquals("SoundCloud", PlatformLinks.embedFor("https://soundcloud.com/artist/a-song")?.platform)
    }

    @Test fun nonEmbeddablePlatforms() {
        assertNull(PlatformLinks.embedFor("https://gaana.com/song/x"))
        assertNull(PlatformLinks.embedFor("https://music.amazon.in/albums/x"))
        assertNull(PlatformLinks.embedFor("https://example.com/a.mp3"))
    }
}
