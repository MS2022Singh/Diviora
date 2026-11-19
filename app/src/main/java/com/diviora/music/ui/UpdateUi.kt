package com.diviora.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diviora.music.BuildConfig
import com.diviora.music.container
import com.diviora.music.update.AppUpdater.State
import kotlinx.coroutines.launch

/** Home-screen banner shown when a newer version is published. */
@Composable
fun UpdateBanner(onOpen: () -> Unit) {
    val c = LocalContext.current.container
    val st by c.updater.state.collectAsStateWithLifecycle()
    val info = when (val s = st) { is State.Available -> s.info; is State.Downloading -> s.info; is State.ReadyToInstall -> s.info; else -> null } ?: return
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SystemUpdate, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Update available: ${info.versionName}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text("Tap to update. Your library and routines are kept.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}

/** Download → (allow unknown apps if needed) → Android installer. */
@Composable
fun UpdateDialog(onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val st by c.updater.state.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.SystemUpdate, null) },
        title = { Text("App update") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Installed: ${BuildConfig.VERSION_NAME}")
                when (val s = st) {
                    is State.Available -> { Text("New: ${s.info.versionName}"); if (s.info.notes.isNotBlank()) Text("\n" + s.info.notes, style = MaterialTheme.typography.bodySmall) }
                    is State.Downloading -> { Text("Downloading ${s.info.versionName}… ${s.percent}%"); LinearProgressIndicator(progress = { s.percent / 100f }, Modifier.fillMaxWidth().padding(top = 8.dp)) }
                    is State.ReadyToInstall -> Text(if (c.updater.canInstall()) "Ready. Tap Install, then confirm on the next screen."
                        else "Android needs your permission once: tap Allow, switch on “Allow from this source”, come back and tap Install.")
                    is State.Checking -> Text("Checking…")
                    is State.UpToDate -> Text("You have the latest version.")
                    is State.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
                    State.Idle -> Text("Tap Check to look for a new version.")
                }
            }
        },
        confirmButton = {
            when (val s = st) {
                is State.Available -> TextButton(onClick = { scope.launch { c.updater.download(s.info) } }) { Text("Download") }
                is State.ReadyToInstall -> if (c.updater.canInstall()) TextButton(onClick = { c.updater.install(s.apk) }) { Text("Install") }
                    else TextButton(onClick = { c.updater.openInstallPermissionSettings() }) { Text("Allow") }
                is State.Downloading, is State.Checking -> {}
                else -> TextButton(onClick = { scope.launch { c.updater.check() } }) { Text("Check") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
