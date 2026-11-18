package com.diviora.music.data.repo

import androidx.room.withTransaction
import com.diviora.music.core.model.RoutineRules
import com.diviora.music.data.db.AppDatabase
import com.diviora.music.data.db.RoutineEntity
import com.diviora.music.data.db.RoutineItemEntity
import com.diviora.music.data.db.TimeSlotEntity
import com.diviora.music.scheduler.AlarmScheduler
import com.diviora.music.scheduler.recurrence.SlotSpec
import kotlinx.serialization.json.Json

val DivioraJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun RoutineEntity.rules(): RoutineRules = runCatching { DivioraJson.decodeFromString<RoutineRules>(rulesJson) }.getOrDefault(RoutineRules())
fun TimeSlotEntity.spec(): SlotSpec? = runCatching { DivioraJson.decodeFromString<SlotSpec>(specJson) }.getOrNull()

/** Editable routine draft used by the editor screen. */
data class RoutineDraft(
    val id: Long = 0,
    val name: String = "",
    val enabled: Boolean = true,
    val mediaIds: List<Long> = emptyList(),
    val slots: List<SlotSpec> = emptyList(),
    val rules: RoutineRules = RoutineRules(),
)

class RoutineRepository(private val db: AppDatabase, private val scheduler: AlarmScheduler) {
    private val dao get() = db.routines()

    fun observeRoutines() = dao.observeRoutines()
    fun observeExecutions(limit: Int = 200) = dao.observeExecutions(limit)
    fun observeNextAlarm() = dao.observeNextAlarm()
    fun observeAllSlots() = dao.observeAllSlots()

    suspend fun load(id: Long): RoutineDraft? {
        val r = dao.get(id) ?: return null
        return RoutineDraft(r.id, r.name, r.enabled, dao.items(id).map { it.id }, dao.slots(id).mapNotNull { it.spec() }, r.rules())
    }

    /** Saves the routine, its items and slots, then (re)registers its alarms. Returns the routine id. */
    suspend fun save(d: RoutineDraft): Long {
        require(d.name.isNotBlank()) { "Name is required" }
        val rulesJson = DivioraJson.encodeToString(RoutineRules.serializer(), d.rules)
        val id = db.withTransaction {
            val id = if (d.id == 0L) dao.insert(RoutineEntity(name = d.name.trim(), enabled = d.enabled, rulesJson = rulesJson))
            else d.id.also { dao.update(RoutineEntity(id = it, name = d.name.trim(), enabled = d.enabled, rulesJson = rulesJson)) }
            dao.deleteItems(id)
            dao.insertItems(d.mediaIds.mapIndexed { i, m -> RoutineItemEntity(id, i, m) })
            dao.slots(id).forEach { scheduler.cancel(it.id) }
            dao.deleteSlots(id)
            dao.insertSlots(d.slots.map { TimeSlotEntity(routineId = id, specJson = DivioraJson.encodeToString(SlotSpec.serializer(), it)) })
            id
        }
        scheduler.scheduleRoutine(id)
        return id
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) { dao.setEnabled(id, enabled); scheduler.scheduleRoutine(id) }

    suspend fun delete(id: Long) { dao.slots(id).forEach { scheduler.cancel(it.id) }; dao.delete(id) }

    suspend fun duplicate(id: Long): Long? = load(id)?.let { save(it.copy(id = 0, name = it.name + " (copy)")) }

    /** Skips the next pending occurrence of every slot of this routine. */
    suspend fun skipNext(id: Long) {
        val alarms = dao.scheduledAlarms().filter { it.routineId == id }
        for (a in alarms) dao.setSkip(a.slotId, a.triggerAtMs)
    }
}
