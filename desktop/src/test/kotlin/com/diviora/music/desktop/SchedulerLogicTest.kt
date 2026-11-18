package com.diviora.music.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StoreAndLibraryTest {
    private fun tmpStore() = Store(File(Files.createTempDirectory("dv").toFile(), "library.json"))

    @Test fun storePersistsAcrossInstances() {
        val dir = Files.createTempDirectory("dv").toFile()
        val a = Store(File(dir, "library.json"))
        val id = a.newId()
        a.update { it.copy(routines = listOf(DRoutine(id, "Morning"))) }
        assertEquals("Morning", Store(File(dir, "library.json")).value.routines.single().name)
    }

    @Test fun platformLinksRejectedAndDirectLinksAdded() {
        val lib = Library(tmpStore())
        assertEquals("song.mp3", lib.addStream("https://example.com/a/song.mp3").title)
        val e = runCatching { lib.addStream("https://www.youtube.com/watch?v=x") }.exceptionOrNull()
        assertEquals("com.diviora.music.core.catalog.PlatformLinkException", e?.javaClass?.name)
    }

    @Test fun kindDetection() {
        assertEquals(com.diviora.music.core.model.MediaKind.AUDIO, Library.kindOf("x.MP3"))
        assertEquals(com.diviora.music.core.model.MediaKind.VIDEO, Library.kindOf("x.mp4"))
        assertNull(Library.kindOf("x.txt"))
    }
}
