package com.diviora.music.security

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.random.Random

/** Encrypted offline copies: what the downloader writes, the player can read (with seeking), and nothing else can. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class OfflineEncryptionTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val key = Random(7).nextBytes(16)
    // Looks like an MP3/MP4 to a sniffer: plaintext must not be visible on disk.
    private val plain = "ID3".toByteArray() + ByteArray(8) + "ftypisom".toByteArray() + Random(1).nextBytes(300_000)

    private fun writeEncrypted(target: File) {
        val part = File(target.parentFile, target.name + ".part")
        val (sink, spec) = encryptingSink(key, target)
        sink.open(spec.buildUpon().setUri(Uri.fromFile(part)).build())
        var i = 0
        // The sink encrypts in place (like the downloader's reusable buffer), so give it a copy of each chunk.
        while (i < plain.size) { val n = minOf(65_536, plain.size - i); sink.write(plain.copyOfRange(i, i + n), 0, n); i += n }
        sink.close()
        assertTrue(part.renameTo(target))
    }

    private fun readAll(spec: DataSpec, factory: ProtectedDataSourceFactory): ByteArray {
        val ds = factory.createDataSource()
        ds.open(spec)
        val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(8192)
        while (true) { val n = ds.read(buf, 0, buf.size); if (n == C.RESULT_END_OF_INPUT) break; out.write(buf, 0, n) }
        ds.close()
        return out.toByteArray()
    }

    @Test fun roundTripSeekAndCiphertextOnDisk() {
        val target = File(offlineDir(context), "42.enc")
        writeEncrypted(target)
        val disk = target.readBytes()
        assertEquals(plain.size, disk.size)
        assertFalse("file on disk must not be plaintext", disk.copyOfRange(0, 64).contentEquals(plain.copyOfRange(0, 64)))
        assertFalse("no media header visible on disk", String(disk.copyOfRange(0, 32), Charsets.ISO_8859_1).contains("ID3"))

        val factory = ProtectedDataSourceFactory(context) { key }
        assertArrayEquals("player decrypts the whole file", plain, readAll(DataSpec(Uri.fromFile(target)), factory))
        val mid = 123_457L
        val tail = readAll(DataSpec.Builder().setUri(Uri.fromFile(target)).setPosition(mid).build(), factory)
        assertArrayEquals("seeking into the middle decrypts correctly", plain.copyOfRange(mid.toInt(), plain.size), tail)
    }

    @Test fun wrongKeyYieldsGarbage() {
        val target = File(offlineDir(context), "43.enc")
        writeEncrypted(target)
        val other = ProtectedDataSourceFactory(context) { Random(99).nextBytes(16) }
        assertFalse(plain.contentEquals(readAll(DataSpec(Uri.fromFile(target)), other)))
    }

    @Test fun filesOutsideOfflineDirAreNotDecrypted() {
        val f = File(context.cacheDir, "plain.bin").apply { writeBytes(plain) }
        assertArrayEquals(plain, readAll(DataSpec(Uri.fromFile(f)), ProtectedDataSourceFactory(context) { key }))
    }
}
