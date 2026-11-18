package com.diviora.music.desktop

import com.diviora.music.core.model.ExecutionResult
import com.diviora.music.core.model.MediaKind
import com.diviora.music.core.model.MediaSource
import com.diviora.music.core.model.RoutineRules
import com.diviora.music.scheduler.recurrence.SlotSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class DMedia(
    val id: Long,
    val uri: String,
    val kind: MediaKind,
    val source: MediaSource,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0,
    val artworkUri: String? = null,
    val favorite: Boolean = false,
    val available: Boolean = true,
    val catalogUrl: String? = null,
    val addedAtMs: Long = System.currentTimeMillis(),
)

@Serializable
data class DSlot(val id: Long, val spec: SlotSpec, val enabled: Boolean = true, val skipOccurrenceAtMs: Long? = null)

@Serializable
data class DRoutine(
    val id: Long,
    val name: String,
    val enabled: Boolean = true,
    val mediaIds: List<Long> = emptyList(),
    val slots: List<DSlot> = emptyList(),
    val rules: RoutineRules = RoutineRules(),
)

@Serializable
data class DExecution(
    val routineId: Long, val slotId: Long, val occurrenceAtMs: Long, val firedAtMs: Long,
    val result: ExecutionResult, val reason: String? = null, val items: Int = 0,
)

@Serializable
data class DCatalogSource(
    val url: String, val name: String, val builtIn: Boolean = false, val itemCount: Int = 0,
    val lastSyncMs: Long? = null, val lastError: String? = null,
)

@Serializable
data class DSettings(val keepRunningInTray: Boolean = true, val volume: Double = 0.8)

@Serializable
data class AppData(
    val nextId: Long = 1,
    val media: List<DMedia> = emptyList(),
    val routines: List<DRoutine> = emptyList(),
    val executions: List<DExecution> = emptyList(),
    val catalogs: List<DCatalogSource> = emptyList(),
    val settings: DSettings = DSettings(),
)

/**
 * The desktop app's single source of truth. It is persisted as JSON in the user's data folder (atomic writes).
 * Windows: %APPDATA%\DIVIORA Music · macOS: ~/Library/Application Support/DIVIORA Music · Linux: ~/.local/share/diviora-music
 */
class Store(private val file: File = defaultFile()) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data.asStateFlow()
    val value: AppData get() = _data.value

    @Synchronized
    fun update(block: (AppData) -> AppData) {
        _data.update(block)
        save(_data.value)
    }

    @Synchronized
    fun newId(): Long {
        var id = 0L
        update { id = it.nextId; it.copy(nextId = it.nextId + 1) }
        return id
    }

    private fun load(): AppData = runCatching {
        if (file.exists()) json.decodeFromString(AppData.serializer(), file.readText()) else AppData()
    }.getOrElse {
        // Keep a copy of an unreadable file instead of silently losing the user's data.
        runCatching { file.copyTo(File(file.parentFile, file.name + ".corrupt-" + System.currentTimeMillis())) }
        AppData()
    }

    private fun save(d: AppData) {
        file.parentFile.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(AppData.serializer(), d.copy(executions = d.executions.takeLast(500))))
        runCatching { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            .onFailure { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    }

    companion object {
        fun dataDir(): File {
            System.getProperty("diviora.dataDir")?.let { return File(it) }
            val os = System.getProperty("os.name").lowercase()
            val home = System.getProperty("user.home")
            return when {
                "win" in os -> File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "DIVIORA Music")
                "mac" in os -> File(home, "Library/Application Support/DIVIORA Music")
                else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "diviora-music")
            }
        }
        fun defaultFile() = File(dataDir(), "library.json")
    }
}
