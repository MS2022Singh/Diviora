package com.diviora.music

import com.diviora.music.core.catalog.CatalogException
import com.diviora.music.core.catalog.CatalogParser
import com.diviora.music.core.catalog.PlatformLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogAndLinksTest {
    @Test fun bundledDemoCatalogIsValid() {
        val f = listOf("src/main/assets/catalog/demo-catalog.json", "app/src/main/assets/catalog/demo-catalog.json").map(::File).first { it.exists() }
        val (c, warnings) = CatalogParser.parse(f.readText())
        assertEquals("DIVIORA Demo", c.name)
        assertEquals(7, c.items.size)
        assertTrue(warnings.isEmpty())
    }

    @Test fun invalidItemsDroppedWithWarnings() {
        val (c, w) = CatalogParser.parse(
            """{"version":1,"name":"X","items":[
               {"id":"a","title":"A","url":"https://h/a.mp3"},
               {"id":"a","title":"Dup","url":"https://h/b.mp3"},
               {"id":"c","title":"C","url":"ftp://h/c.mp3"},
               {"id":"d","title":"D","url":"https://h/d.mp4","type":"movie"},
               {"id":"e","title":"E","url":"https://h/e.mp4","type":"video","artwork":"javascript:x","extra":1}]}""",
        )
        assertEquals(listOf("a", "e"), c.items.map { it.id })
        assertNull(c.items[1].artwork)
        assertEquals(3, w.size)
    }

    @Test(expected = CatalogException::class) fun wrongVersionRejected() { CatalogParser.parse("""{"version":2,"name":"X"}""") }
    @Test(expected = CatalogException::class) fun garbageRejected() { CatalogParser.parse("<html>") }

    @Test fun platformLinksDetected() {
        assertEquals("YouTube", PlatformLinks.platformFor("https://www.youtube.com/watch?v=abc")?.name)
        assertEquals("YouTube", PlatformLinks.platformFor("https://youtu.be/abc")?.name)
        assertEquals("YouTube", PlatformLinks.platformFor("https://music.youtube.com/watch?v=x")?.name)
        assertEquals("Spotify", PlatformLinks.platformFor("https://open.spotify.com/track/1")?.name)
        assertEquals("Gaana", PlatformLinks.platformFor("https://gaana.com/song/x")?.name)
        assertEquals("Amazon Music", PlatformLinks.platformFor("https://music.amazon.in/albums/x")?.name)
        assertNull(PlatformLinks.platformFor("https://ice1.somafm.com/groovesalad-128-mp3"))
        assertNull(PlatformLinks.platformFor("https://example.com/song.mp3"))
        assertNull(PlatformLinks.platformFor("https://notyoutube.com/x"))
    }
}
