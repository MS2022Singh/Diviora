package com.diviora.music.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateInfoTest {
    private val sample = """{"versionCode":7,"versionName":"0.7.0","notes":"x","android":"https://h/a.apk","windows":"https://h/w.msi",
        "macos":"https://h/m.dmg","linux":"https://h/l.deb","page":"https://h/p","extra":true}"""

    @Test fun parseAndCompare() {
        val u = UpdateInfo.parse(sample)!!
        assertTrue(u.isNewerThan(6)); assertFalse(u.isNewerThan(7))
        assertEquals("https://h/w.msi", u.desktopUrlFor("Windows 11"))
        assertEquals("https://h/m.dmg", u.desktopUrlFor("Mac OS X"))
        assertEquals("https://h/l.deb", u.desktopUrlFor("Linux"))
    }

    @Test fun invalidRejected() {
        assertNull(UpdateInfo.parse("<html>"))
        assertNull(UpdateInfo.parse("""{"versionCode":0,"versionName":"x"}"""))
    }
}
