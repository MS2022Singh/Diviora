package com.diviora.music.scheduler.recurrence

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** How a time slot repeats. All variants are serializable so they can be stored as JSON in Room. */
@Serializable
sealed interface Repeat {
    /** Fires once, on [SlotSpec.startEpochDay]. */
    @Serializable data object Once : Repeat

    /** Every [interval] days. */
    @Serializable data class Daily(val interval: Int = 1) : Repeat

    /** On the given ISO days of week (1 = Monday … 7 = Sunday), every [interval] weeks. */
    @Serializable data class Weekly(val days: Set<Int>, val interval: Int = 1) : Repeat

    /** On [dayOfMonth] every month. Months that are too short use their last day. */
    @Serializable data class Monthly(val dayOfMonth: Int) : Repeat
}

/**
 * One scheduled time of a routine. A routine may have many of these.
 *
 * @param minuteOfDay local wall-clock time (0..1439).
 * @param zoneId fixed IANA zone, or null to follow the device's current zone.
 * @param startEpochDay first allowed date (local).
 * @param endEpochDay last allowed date (inclusive) or null.
 * @param maxOccurrences stop after this many occurrences counted from the start, or null.
 * @param excludedEpochDays dates to skip (holidays etc.).
 */
@Serializable
data class SlotSpec(
    val minuteOfDay: Int,
    val repeat: Repeat,
    val startEpochDay: Long,
    val endEpochDay: Long? = null,
    val maxOccurrences: Int? = null,
    val excludedEpochDays: Set<Long> = emptySet(),
    val zoneId: String? = null,
) {
    init {
        require(minuteOfDay in 0 until 24 * 60) { "minuteOfDay out of range: $minuteOfDay" }
        when (repeat) {
            is Repeat.Daily -> require(repeat.interval >= 1)
            is Repeat.Weekly -> require(repeat.interval >= 1 && repeat.days.isNotEmpty() && repeat.days.all { it in 1..7 })
            is Repeat.Monthly -> require(repeat.dayOfMonth in 1..31)
            Repeat.Once -> Unit
        }
    }

    val localTime: LocalTime get() = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
}

object RecurrenceEngine {
    /** Upper bound on how far ahead we search, so a malformed rule can never loop forever. */
    private const val MAX_DAYS_SCAN = 366L * 5

    /**
     * The first occurrence strictly after [after], or null if the slot has no further occurrences.
     *
     * DST: a wall time that falls in a spring-forward gap is moved forward by the gap length (02:30 → 03:30).
     * In an autumn overlap the earlier offset is used, so a slot fires once, not twice.
     */
    fun nextOccurrence(spec: SlotSpec, after: Instant, deviceZone: ZoneId = ZoneId.systemDefault()): Instant? {
        val zone = spec.zoneId?.let(ZoneId::of) ?: deviceZone
        val start = LocalDate.ofEpochDay(spec.startEpochDay)
        val afterDate = after.atZone(zone).toLocalDate()
        var date = if (afterDate.isAfter(start)) afterDate.minusDays(1) else start
        var scanned = 0L
        while (scanned++ < MAX_DAYS_SCAN) {
            if (spec.endEpochDay != null && date.toEpochDay() > spec.endEpochDay) return null
            if (matches(spec, start, date)) {
                val instant = resolve(date, spec.localTime, zone)
                if (instant.isAfter(after) && date.toEpochDay() !in spec.excludedEpochDays) {
                    if (spec.maxOccurrences == null || indexOf(spec, start, date) < spec.maxOccurrences) return instant
                    return null
                }
            }
            if (spec.repeat == Repeat.Once && date.isAfter(start)) return null
            date = date.plusDays(1)
        }
        return null
    }

    /** The next [count] occurrences after [after], for previews and conflict planning. */
    fun upcoming(spec: SlotSpec, after: Instant, count: Int, deviceZone: ZoneId = ZoneId.systemDefault()): List<Instant> {
        val out = ArrayList<Instant>(count)
        var cursor = after
        while (out.size < count) {
            val next = nextOccurrence(spec, cursor, deviceZone) ?: break
            out += next
            cursor = next
        }
        return out
    }

    internal fun matches(spec: SlotSpec, start: LocalDate, date: LocalDate): Boolean {
        if (date.isBefore(start)) return false
        return when (val r = spec.repeat) {
            Repeat.Once -> date == start
            is Repeat.Daily -> ChronoUnit.DAYS.between(start, date) % r.interval == 0L
            is Repeat.Weekly -> {
                if (date.dayOfWeek.value !in r.days) return false
                val weekStart = start.with(DayOfWeek.MONDAY)
                val weeks = ChronoUnit.WEEKS.between(weekStart, date.with(DayOfWeek.MONDAY))
                weeks % r.interval == 0L
            }
            is Repeat.Monthly -> date.dayOfMonth == minOf(r.dayOfMonth, date.lengthOfMonth())
        }
    }

    /** Zero-based index of the occurrence on [date], counting matching dates from [start] (exclusions included). */
    private fun indexOf(spec: SlotSpec, start: LocalDate, date: LocalDate): Int {
        var d = start
        var i = 0
        while (d.isBefore(date)) {
            if (matches(spec, start, d)) i++
            d = d.plusDays(1)
        }
        return i
    }

    private fun resolve(date: LocalDate, time: LocalTime, zone: ZoneId): Instant =
        ZonedDateTime.ofLocal(date.atTime(time), zone, null).toInstant()
}

/** A single planned firing, used by the conflict planner and calendar preview. */
data class PlannedOccurrence(val routineId: Long, val slotId: Long, val at: Instant)

object ConflictPlanner {
    /**
     * Expands every slot over [horizonDays] and returns pairs of occurrences from different routines that start
     * within [windowMinutes] of each other.
     */
    fun conflicts(
        slots: List<Triple<Long, Long, SlotSpec>>,
        from: Instant,
        horizonDays: Long = 14,
        windowMinutes: Long = 5,
        deviceZone: ZoneId = ZoneId.systemDefault(),
    ): List<Pair<PlannedOccurrence, PlannedOccurrence>> {
        val until = from.plus(horizonDays, ChronoUnit.DAYS)
        val all = slots.flatMap { (routineId, slotId, spec) ->
            RecurrenceEngine.upcoming(spec, from, 500, deviceZone).takeWhile { it.isBefore(until) }
                .map { PlannedOccurrence(routineId, slotId, it) }
        }.sortedBy { it.at }
        val out = mutableListOf<Pair<PlannedOccurrence, PlannedOccurrence>>()
        for (i in all.indices) {
            var j = i + 1
            while (j < all.size && ChronoUnit.MINUTES.between(all[i].at, all[j].at) < windowMinutes) {
                if (all[i].routineId != all[j].routineId) out += all[i] to all[j]
                j++
            }
        }
        return out
    }
}
