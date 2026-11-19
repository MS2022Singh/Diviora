package com.diviora.music.core.catalog

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `version.json`, published with every release (Releases → latest). Apps compare [versionCode] with their own to offer
 * an update.
 */
@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: String = "",
    val android: String? = null,
    val windows: String? = null,
    val macos: String? = null,
    val linux: String? = null,
    val page: String? = null,
) {
    fun isNewerThan(installedVersionCode: Int) = versionCode > installedVersionCode

    /** Installer URL for the running desktop OS ([osName] = System.getProperty("os.name")). */
    fun desktopUrlFor(osName: String): String? = when {
        osName.contains("win", true) -> windows
        osName.contains("mac", true) -> macos
        else -> linux
    } ?: page

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): UpdateInfo? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
            ?.takeIf { it.versionCode > 0 && it.versionName.isNotBlank() }
        const val DEFAULT_URL = "https://github.com/MS2022Singh/Diviora/releases/latest/download/version.json"
    }
}
