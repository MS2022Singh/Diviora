package com.diviora.music.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.diviora.music.BuildConfig
import com.diviora.music.container
import com.diviora.music.core.model.RoutineRules
import com.diviora.music.data.repo.AppSettings
import com.diviora.music.data.repo.RoutineDraft
import com.diviora.music.data.repo.ThemeMode
import com.diviora.music.scheduler.recurrence.Repeat
import com.diviora.music.scheduler.recurrence.SlotSpec
import com.diviora.music.ui.RescanRequest
import com.diviora.music.ui.UiEvents
import com.diviora.music.ui.requiredPermissions
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@Composable
fun SettingsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ } // re-check permissions when returning from system settings
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++; RescanRequest.request() }

    val recheck = tick
    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    val mediaOk = if (Build.VERSION.SDK_INT >= 33) granted(Manifest.permission.READ_MEDIA_AUDIO) else granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    val notifOk = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
    val exactOk = c.scheduler.canScheduleExact()
    val batteryOk = recheck >= 0 && ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))

        Section("Routine reliability check")
        Check("Media access", "Needed to find music and videos on this device", mediaOk, "Allow") { permLauncher.launch(requiredPermissions()) }
        Check("Notifications", "Playback controls and routine alerts", notifOk, "Allow") { permLauncher.launch(requiredPermissions()) }
        Check("Exact alarms", "Routines start on the exact minute", exactOk, "Open") {
            if (Build.VERSION.SDK_INT >= 31) ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
        }
        Check("Battery optimisation off", "Stops some phones from killing scheduled playback", batteryOk, "Open") {
            ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        Text("Note: if you force-stop the app, Android cancels all routines until you open DIVIORA again.",
            Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = {
                scope.launch {
                    val ids = c.db.media().recentLocalIds()
                    if (ids.isEmpty()) { UiEvents.message("Add some media first"); return@launch }
                    // Next whole minute at least 60 s away, so a slow save can never land in the past.
                    val t = LocalDateTime.now().plusSeconds(119).withSecond(0).withNano(0)
                    c.routines.save(RoutineDraft(name = "Test routine ${t.toLocalTime().withNano(0).withSecond(0)}", mediaIds = ids.take(3),
                        slots = listOf(SlotSpec(t.hour * 60 + t.minute, Repeat.Once, t.toLocalDate().toEpochDay())), rules = RoutineRules(fadeInSeconds = 3)))
                    UiEvents.message("Test routine will play at ${t.toLocalTime().withSecond(0).withNano(0)}. Check Routines → History afterwards.")
                }
            }) { Text("Quick test: auto-schedule a routine ~2 minutes from now") }
            TextButton(onClick = { scope.launch { c.scheduler.rescheduleAll("manual"); UiEvents.message("Alarms re-registered") } }) { Text("Re-register alarms") }
        }

        if (BuildConfig.DEBUG || settings.developerUnlocked) {
            Section("Developer options")
            Text("Only for the app developer. Users of release builds never see this section.",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ContentSources()
            if (!BuildConfig.DEBUG) TextButton(onClick = { scope.launch { c.settings.setDeveloper(false); c.catalogs.syncAll(); UiEvents.message("Developer options locked") } },
                modifier = Modifier.padding(horizontal = 8.dp)) { Text("Lock developer options") }
        }

        Section("Library")
        ListItem(headlineContent = { Text("Rescan device") }, supportingContent = { Text("Find new music and videos in device storage") },
            trailingContent = { TextButton(onClick = { RescanRequest.request() }) { Text("Scan") } })

        Section("Appearance")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { m -> FilterChip(settings.theme == m, { scope.launch { c.settings.setTheme(m) } }, label = { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
        }
        if (Build.VERSION.SDK_INT >= 31) ListItem(headlineContent = { Text("Wallpaper colours (Material You)") },
            trailingContent = { Switch(settings.dynamicColor, { on -> scope.launch { c.settings.setDynamic(on) } }) })

        Section("App updates")
        ListItem(headlineContent = { Text("Installed version ${BuildConfig.VERSION_NAME}") },
            supportingContent = { Text(if (c.updater.enabled) "DIVIORA checks for updates every day and when it starts." else "Updates are off in developer builds.") },
            trailingContent = { if (c.updater.enabled) TextButton(onClick = { UiEvents.showUpdate(); scope.launch { c.updater.check() } }) { Text("Check now") } })

        Section("About")
        var taps by remember { mutableIntStateOf(0) }
        var askPass by remember { mutableStateOf(false) }
        if (askPass) DeveloperUnlockDialog(onDismiss = { askPass = false })
        ListItem(modifier = Modifier.clickable { if (++taps >= 7 && !settings.developerUnlocked && !BuildConfig.DEBUG) { taps = 0; askPass = true } },
            headlineContent = { Text("DIVIORA Music ${BuildConfig.VERSION_NAME}") },
            supportingContent = { Text("Local-first player with AutoPlay Routines. Proprietary; third-party components under Apache-2.0 / MIT.") })
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
}

@Composable
private fun Check(title: String, body: String, ok: Boolean, action: String, onFix: () -> Unit) {
    ListItem(
        leadingContent = { Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error, null,
            tint = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error) },
        headlineContent = { Text(title) }, supportingContent = { Text(body) },
        trailingContent = { if (!ok) TextButton(onClick = onFix) { Text(action) } },
    )
}

/** Developer / provider content servers (catalog JSON over HTTPS). */
@Composable
private fun ContentSources() {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val sources by remember { c.catalogs.observeSources() }.collectAsStateWithLifecycle(emptyList())
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Text("Content sources", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp))
    Text("The built-in source is set in the build (diviora.catalogUrl) and reaches every user. Sources added here exist only on this device, for testing.",
        Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    sources.forEach { s ->
        ListItem(
            headlineContent = { Text(s.name + if (s.builtIn) "  (built-in)" else "") },
            supportingContent = {
                Text("${s.itemCount} items · " + (s.lastSyncMs?.let { "updated ${formatWhen(it)}" } ?: "not synced yet") +
                    (s.lastError?.let { "\n⚠ $it" } ?: "") + "\n" + s.url.take(80))
            },
            trailingContent = {
                if (!s.builtIn) TextButton(onClick = { scope.launch { runCatching { c.catalogs.removeSource(s.url) }.onFailure { UiEvents.message(it.message ?: "Failed") } } }) { Text("Remove") }
            },
        )
    }
    OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("Add source URL (https://…/catalog.json)") },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
    Row(Modifier.padding(horizontal = 8.dp)) {
        TextButton(enabled = url.isNotBlank() && !busy, onClick = {
            busy = true
            scope.launch {
                runCatching { c.catalogs.addSource(url) }
                    .onSuccess { UiEvents.message("Added ${it.name}: ${it.itemCount} items"); url = "" }
                    .onFailure { UiEvents.message("Could not add source: ${it.message}") }
                busy = false
            }
        }) { Text("Add source") }
        TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch { val r = c.catalogs.syncAll(); UiEvents.message("Refreshed ${r.size} source(s)" + if (r.any { it.lastError != null }) ", some failed" else ""); busy = false }
        }) { Text("Refresh all") }
    }
}

/** Release builds: unlock developer options with the passphrase whose hash is baked into the build. */
@Composable
private fun DeveloperUnlockDialog(onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var pass by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Developer options") },
        text = {
            Column {
                Text("Enter the developer passphrase.")
                OutlinedTextField(pass, { pass = it }, singleLine = true, label = { Text("Passphrase") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrectEnabled = false,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    BuildConfig.DEV_UNLOCK_HASH.isEmpty() -> UiEvents.message(
                        "Developer unlock isn't set up in this build. Add the DIVIORA_DEV_PASSPHRASE secret on GitHub, rebuild, then install the new APK.")
                    java.security.MessageDigest.getInstance("SHA-256").digest("diviora-dev-v1:${pass.trim()}".toByteArray())
                        .joinToString("") { "%02x".format(it) } == BuildConfig.DEV_UNLOCK_HASH ->
                        scope.launch { c.settings.setDeveloper(true); UiEvents.message("Developer options unlocked") }
                    else -> UiEvents.message("Wrong passphrase (it is case-sensitive)")
                }
                onDismiss()
            }) { Text("Unlock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
