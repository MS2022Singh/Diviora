package com.diviora.music.scheduler

import com.diviora.music.core.model.ExecutionResult
import com.diviora.music.data.db.AppDatabase
import com.diviora.music.data.db.RoutineExecutionEntity

class ExecutionLog(private val db: AppDatabase) {
    /** Claims an occurrence. Returns false if it was already handled (de-duplication by slot and occurrence). */
    suspend fun claim(routineId: Long, slotId: Long, occurrenceMs: Long): Long? {
        val id = db.routines().insertExecution(
            RoutineExecutionEntity(routineId = routineId, slotId = slotId, occurrenceAtMs = occurrenceMs,
                firedAtMs = System.currentTimeMillis(), result = ExecutionResult.FAILED, reason = "In progress"),
        )
        return id.takeIf { it > 0 }
    }

    suspend fun finish(id: Long, routineId: Long, slotId: Long, occurrenceMs: Long, result: ExecutionResult, reason: String?, items: Int) {
        db.routines().updateExecution(
            RoutineExecutionEntity(id = id, routineId = routineId, slotId = slotId, occurrenceAtMs = occurrenceMs,
                firedAtMs = System.currentTimeMillis(), result = result, reason = reason, itemsQueued = items),
        )
    }

    suspend fun recordFailure(slotId: Long, occurrenceMs: Long, reason: String) {
        val slot = db.routines().slot(slotId) ?: return
        db.routines().insertExecution(
            RoutineExecutionEntity(routineId = slot.routineId, slotId = slotId, occurrenceAtMs = occurrenceMs,
                firedAtMs = System.currentTimeMillis(), result = ExecutionResult.FAILED, reason = reason),
        )
    }
}
