package com.diviora.music.desktop

import com.diviora.music.core.model.ExecutionResult
import com.diviora.music.scheduler.recurrence.Repeat
import com.diviora.music.scheduler.recurrence.SlotSpec
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.sin
import kotlin.system.exitProcess

/**
 * `--selftest`: headless end-to-end check used by CI. Creates a temp library with a generated WAV, schedules a
 * one-time routine at the next minute, waits for it to fire, and checks it was logged as PLAYED and playback began.
 */
object SelfTest {
    fun run() {
        val dir = Files.createTempDirectory("diviora-selftest").toFile()
        val wav = File(dir, "tone.wav").also { writeTone(it) }
        val store = Store(File(dir, "library.json"))
        val player = Player(0.0)
        val library = Library(store)
        val scheduler = Scheduler(store, player)
        check(library.addFiles(listOf(wav)) == 1) { "import failed" }
        val at = LocalDateTime.now().plusMinutes(1).withSecond(0).withNano(0)
        val r = DRoutine(store.newId(), "Self-test", mediaIds = store.value.media.map { it.id },
            slots = listOf(DSlot(store.newId(), SlotSpec(at.hour * 60 + at.minute, Repeat.Once, at.toLocalDate().toEpochDay(), zoneId = ZoneId.systemDefault().id))))
        store.update { it.copy(routines = listOf(r)) }
        println("selftest: routine scheduled for $at; next=${scheduler.upcoming().firstOrNull()?.at}")
        scheduler.start()
        val deadline = System.currentTimeMillis() + 150_000
        while (System.currentTimeMillis() < deadline && store.value.executions.isEmpty()) Thread.sleep(500)
        val e = store.value.executions.firstOrNull()
        println("selftest: execution=$e")
        Thread.sleep(4000)
        val s = player.state.value
        println("selftest: player current=${s.current?.title} playing=${s.playing} pos=${s.positionMs} error=${s.error}")
        val ok = e?.result == ExecutionResult.PLAYED && s.current != null
        println(if (ok) "SELFTEST PASS" else "SELFTEST FAIL")
        exitProcess(if (ok) 0 else 1)
    }

    private fun writeTone(f: File) {
        val rate = 22050; val n = rate * 20
        val data = java.io.ByteArrayOutputStream()
        fun le(v: Int, bytes: Int) { for (i in 0 until bytes) data.write((v shr (8 * i)) and 0xff) }
        data.write("RIFF".toByteArray()); le(36 + n * 2, 4); data.write("WAVEfmt ".toByteArray()); le(16, 4); le(1, 2); le(1, 2)
        le(rate, 4); le(rate * 2, 4); le(2, 2); le(16, 2); data.write("data".toByteArray()); le(n * 2, 4)
        for (i in 0 until n) le((6000 * sin(2 * PI * 440 * i / rate)).toInt(), 2)
        f.writeBytes(data.toByteArray())
    }
}
