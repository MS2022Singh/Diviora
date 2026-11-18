package com.diviora.music.scheduler.recurrence

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Slot specs are stored as JSON in Room. Every Repeat variant must survive a round trip. */
class SlotSpecJsonTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun roundTripAllVariants() {
        listOf(Repeat.Once, Repeat.Daily(2), Repeat.Weekly(setOf(1, 3, 5), 2), Repeat.Monthly(31)).forEach { r ->
            val spec = SlotSpec(415, r, 20_000, endEpochDay = 21_000, maxOccurrences = 7, excludedEpochDays = setOf(20_010), zoneId = "Asia/Kolkata")
            assertEquals(spec, json.decodeFromString(SlotSpec.serializer(), json.encodeToString(SlotSpec.serializer(), spec)))
        }
    }
}
