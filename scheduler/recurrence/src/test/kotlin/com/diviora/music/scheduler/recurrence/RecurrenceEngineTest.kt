package com.diviora.music.scheduler.recurrence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RecurrenceEngineTest {
    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val newYork = ZoneId.of("America/New_York")
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int, z: ZoneId) = ZonedDateTime.of(y, m, d, h, min, 0, 0, z).toInstant()

    @Test fun onceFiresOnlyOnStartDate() {
        val s = SlotSpec(7 * 60, Repeat.Once, day(2026, 11, 20))
        assertEquals(at(2026, 11, 20, 7, 0, kolkata), RecurrenceEngine.nextOccurrence(s, at(2026, 11, 18, 12, 0, kolkata), kolkata))
        assertNull(RecurrenceEngine.nextOccurrence(s, at(2026, 11, 20, 7, 0, kolkata), kolkata))
    }

    @Test fun dailyLaterTodayThenTomorrow() {
        val s = SlotSpec(21 * 60 + 30, Repeat.Daily(), day(2026, 1, 1))
        assertEquals(at(2026, 11, 18, 21, 30, kolkata), RecurrenceEngine.nextOccurrence(s, at(2026, 11, 18, 10, 0, kolkata), kolkata))
        assertEquals(at(2026, 11, 19, 21, 30, kolkata), RecurrenceEngine.nextOccurrence(s, at(2026, 11, 18, 21, 30, kolkata), kolkata))
    }

    @Test fun everyThirdDay() {
        val s = SlotSpec(6 * 60, Repeat.Daily(3), day(2026, 11, 1))
        val next = RecurrenceEngine.upcoming(s, at(2026, 11, 1, 0, 0, kolkata), 3, kolkata)
        assertEquals(listOf(at(2026, 11, 1, 6, 0, kolkata), at(2026, 11, 4, 6, 0, kolkata), at(2026, 11, 7, 6, 0, kolkata)), next)
    }

    @Test fun weekdaysSkipWeekend() {
        val s = SlotSpec(7 * 60, Repeat.Weekly(setOf(1, 2, 3, 4, 5)), day(2026, 1, 1))
        // 2026-11-20 is a Friday -> next after Friday 08:00 is Monday 23rd
        assertEquals(at(2026, 11, 23, 7, 0, kolkata), RecurrenceEngine.nextOccurrence(s, at(2026, 11, 20, 8, 0, kolkata), kolkata))
    }

    @Test fun biWeeklySaturday() {
        val s = SlotSpec(11 * 60, Repeat.Weekly(setOf(6), interval = 2), day(2026, 11, 21))
        assertEquals(listOf(at(2026, 11, 21, 11, 0, kolkata), at(2026, 12, 5, 11, 0, kolkata)),
            RecurrenceEngine.upcoming(s, at(2026, 11, 18, 0, 0, kolkata), 2, kolkata))
    }

    @Test fun monthly31stClampsToMonthEnd() {
        val s = SlotSpec(9 * 60, Repeat.Monthly(31), day(2027, 1, 1))
        assertEquals(listOf(at(2027, 1, 31, 9, 0, kolkata), at(2027, 2, 28, 9, 0, kolkata), at(2027, 3, 31, 9, 0, kolkata)),
            RecurrenceEngine.upcoming(s, at(2027, 1, 1, 0, 0, kolkata), 3, kolkata))
    }

    @Test fun exclusionsAndEndDateAndCount() {
        val ex = SlotSpec(8 * 60, Repeat.Daily(), day(2026, 12, 24), excludedEpochDays = setOf(day(2026, 12, 25)))
        assertEquals(at(2026, 12, 26, 8, 0, kolkata), RecurrenceEngine.nextOccurrence(ex, at(2026, 12, 24, 9, 0, kolkata), kolkata))
        val end = SlotSpec(8 * 60, Repeat.Daily(), day(2026, 12, 1), endEpochDay = day(2026, 12, 2))
        assertNull(RecurrenceEngine.nextOccurrence(end, at(2026, 12, 2, 9, 0, kolkata), kolkata))
        val count = SlotSpec(8 * 60, Repeat.Daily(), day(2026, 12, 1), maxOccurrences = 2)
        assertEquals(2, RecurrenceEngine.upcoming(count, at(2026, 11, 1, 0, 0, kolkata), 10, kolkata).size)
    }

    @Test fun dstSpringForwardGapMovesForward() {
        // 2027-03-14 02:30 does not exist in New York; fires at 03:30 EDT.
        val s = SlotSpec(2 * 60 + 30, Repeat.Daily(), day(2027, 3, 13))
        val next = RecurrenceEngine.upcoming(s, at(2027, 3, 13, 0, 0, newYork), 3, newYork)
        assertEquals(at(2027, 3, 14, 3, 30, newYork), next[1])
        assertEquals(at(2027, 3, 15, 2, 30, newYork), next[2])
    }

    @Test fun dstFallBackFiresOnce() {
        // 2026-11-01 01:30 occurs twice in New York; must fire exactly once that day.
        val s = SlotSpec(90, Repeat.Daily(), day(2026, 10, 31))
        val next = RecurrenceEngine.upcoming(s, at(2026, 10, 31, 12, 0, newYork), 2, newYork)
        assertEquals(LocalDate.of(2026, 11, 1), next[0].atZone(newYork).toLocalDate())
        assertEquals(LocalDate.of(2026, 11, 2), next[1].atZone(newYork).toLocalDate())
    }

    @Test fun fixedZoneIgnoresDeviceZone() {
        val s = SlotSpec(7 * 60, Repeat.Daily(), day(2026, 1, 1), zoneId = "Asia/Kolkata")
        assertEquals(at(2026, 11, 19, 7, 0, kolkata), RecurrenceEngine.nextOccurrence(s, at(2026, 11, 18, 12, 0, kolkata), newYork))
    }

    @Test fun conflictsDetectedAcrossRoutinesOnly() {
        val a = SlotSpec(7 * 60, Repeat.Daily(), day(2026, 11, 1))
        val b = SlotSpec(7 * 60 + 2, Repeat.Daily(), day(2026, 11, 1))
        val c = SlotSpec(7 * 60 + 1, Repeat.Daily(), day(2026, 11, 1))
        val conflicts = ConflictPlanner.conflicts(
            listOf(Triple(1L, 10L, a), Triple(2L, 20L, b), Triple(1L, 11L, c)),
            at(2026, 11, 18, 0, 0, kolkata), horizonDays = 1, deviceZone = kolkata,
        )
        assertTrue(conflicts.isNotEmpty())
        assertTrue(conflicts.all { it.first.routineId != it.second.routineId })
    }

    @Test(expected = IllegalArgumentException::class) fun invalidWeeklyRejected() {
        SlotSpec(0, Repeat.Weekly(emptySet()), 0)
    }
}
