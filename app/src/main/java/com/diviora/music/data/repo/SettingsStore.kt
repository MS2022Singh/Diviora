package com.diviora.music.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class AppSettings(val theme: ThemeMode = ThemeMode.DARK, val dynamicColor: Boolean = false, val onboarded: Boolean = false, val developerUnlocked: Boolean = false,
    val downloadOverride: DownloadOverride = DownloadOverride.FOLLOW_CATALOG)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val dynamicKey = booleanPreferencesKey("dynamic")
    private val onboardedKey = booleanPreferencesKey("onboarded")
    private val devKey = booleanPreferencesKey("developer")
    private val dlKey = stringPreferencesKey("downloadOverride")

    val settings: Flow<AppSettings> = context.dataStore.data.map {
        AppSettings(
            theme = it[themeKey]?.let { v -> runCatching { ThemeMode.valueOf(v) }.getOrNull() } ?: ThemeMode.DARK,
            dynamicColor = it[dynamicKey] ?: false,
            onboarded = it[onboardedKey] ?: false,
            developerUnlocked = it[devKey] ?: false,
            downloadOverride = it[dlKey]?.let { v -> runCatching { DownloadOverride.valueOf(v) }.getOrNull() } ?: DownloadOverride.FOLLOW_CATALOG,
        )
    }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[themeKey] = mode.name }
    suspend fun setDynamic(on: Boolean) = context.dataStore.edit { it[dynamicKey] = on }
    suspend fun setOnboarded() = context.dataStore.edit { it[onboardedKey] = true }
    suspend fun setDeveloper(on: Boolean) = context.dataStore.edit { it[devKey] = on }
    suspend fun setDownloadOverride(o: DownloadOverride) = context.dataStore.edit { it[dlKey] = o.name }
}
