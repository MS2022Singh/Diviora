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
                    val t = LocalDateTime.now().plusMinutes(1)
                    c.routines.save(RoutineDraft(name = "Test routine ${t.toLocalTime().withNano(0).withSecond(0)}", mediaIds = ids.take(3),
                        slots = listOf(SlotSpec(t.hour * 60 + t.minute, Repeat.Once, t.toLocalDate().toEpochDay())), rules = RoutineRules(fadeInSeconds = 3)))
                    UiEvents.message("Test routine will play at ${t.toLocalTime().withSecond(0).withNano(0)}")
                }
            }) { Text("Schedule a test routine in 1 minute") }
            TextButton(onClick = { scope.launch { c.scheduler.rescheduleAll("manual"); UiEvents.message("Alarms re-registered") } }) { Text("Re-register alarms") }
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

        Section("About")
        ListItem(headlineContent = { Text("DIVIORA Music ${BuildConfig.VERSION_NAME}") },
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
