package com.diviora.music.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogDownloadTest {
    @Test fun defaultsToStreamOnly() {
        val (c, _) = CatalogParser.parse("""{"version":1,"name":"X","items":[{"id":"a","title":"A","url":"https://h/a.mp3"}]}""")
        assertFalse(c.allowDownload)
        assertEquals(null, c.items[0].download)
    }

    @Test fun catalogAndItemSwitches() {
        val (c, _) = CatalogParser.parse("""{"version":1,"name":"X","allowDownload":true,"items":[
            {"id":"a","title":"A","url":"https://h/a.mp3"},{"id":"b","title":"B","url":"https://h/b.mp3","download":false}]}""")
        assertTrue(c.allowDownload)
        assertEquals(listOf(true, false), c.items.map { it.download ?: c.allowDownload })
    }

    @Test fun noSizeOrCountLimit() {
        val items = (1..20_000).joinToString(",") { """{"id":"i$it","title":"T$it","url":"https://h/$it.mp3"}""" }
        val (c, w) = CatalogParser.parse("""{"version":1,"name":"Big","items":[$items]}""")
        assertEquals(20_000, c.items.size); assertTrue(w.isEmpty())
    }
}

class CatalogSecurityTest {
    @Test fun drmAndExpiryParsed() {
        val (c, w) = CatalogParser.parse("""{"version":1,"name":"X","items":[
            {"id":"a","title":"A","url":"https://cdn/a.mpd","type":"video","expiresAt":1900000000000,
             "drm":{"scheme":"widevine","licenseUrl":"https://lic/wv","headers":{"Authorization":"t"}}},
            {"id":"b","title":"B","url":"https://cdn/b.mpd","drm":{"scheme":"playready","licenseUrl":"https://lic"}}]}""")
        org.junit.Assert.assertEquals("https://lic/wv", c.items[0].drm?.licenseUrl)
        org.junit.Assert.assertEquals(1900000000000L, c.items[0].expiresAt)
        org.junit.Assert.assertNull("unsupported DRM scheme dropped", c.items[1].drm)
        org.junit.Assert.assertEquals(1, w.size)
    }
}
