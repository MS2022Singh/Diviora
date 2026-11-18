package com.diviora.music

import com.diviora.music.core.catalog.PlaylistParser
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistParserTest {
    @Test fun extendedM3u() {
        val r = PlaylistParser.parse("#EXTM3U\n#EXTINF:-1,Radio One\nhttps://example.org/one.mp3\n/local/file.mp3\nhttp://x.test/live.m3u8\n")
        assertEquals(listOf(PlaylistParser.Entry("https://example.org/one.mp3", "Radio One"), PlaylistParser.Entry("http://x.test/live.m3u8", null)), r)
    }

    @Test fun pls() {
        val r = PlaylistParser.parse("[playlist]\nFile1=https://a.test/s\nTitle1=A\nFile2=https://b.test/s\nNumberOfEntries=2\n")
        assertEquals(listOf(PlaylistParser.Entry("https://a.test/s", "A"), PlaylistParser.Entry("https://b.test/s", null)), r)
    }
}
