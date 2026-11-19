package com.diviora.music.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.Notification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** App icon drawn in code (original DIVIORA gradient mark). */
object DivioraIcon : Painter() {
    override val intrinsicSize = Size(64f, 64f)
    override fun DrawScope.onDraw() {
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFF472B6))), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension / 4))
        val w = size.width; val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.38f, h * 0.28f); lineTo(w * 0.74f, h * 0.5f); lineTo(w * 0.38f, h * 0.72f); close() }
        drawPath(path, Color.White)
    }
}

fun main(args: Array<String>) {
    if ("--selftest" in args) { SelfTest.run(); return }
    val store = Store()
    val player = Player(store.value.settings.volume)
    val library = Library(store)
    val scheduler = Scheduler(store, player).also { it.start() }
    val services = AppServices(store, library, player, scheduler)
    CoroutineScope(Dispatchers.IO).launch {
        while (true) { runCatching { library.syncAll() }; delay(60 * 60 * 1000L) } // developer content refresh, hourly
    }
    CoroutineScope(Dispatchers.IO).launch {
        while (true) {
            Updater.check()?.let { info -> services.update = info; Notifier.show("DIVIORA Music ${info.versionName} is available", "Open DIVIORA to download the update") }
            delay(6 * 60 * 60 * 1000L)
        }
    }
    application {
        var visible by remember { mutableStateOf(true) }
        val trayState = rememberTrayState()
        Notifier.tray = { t, m -> trayState.sendNotification(Notification(t, m, Notification.Type.Info)) }
        val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)
        Tray(icon = DivioraIcon, state = trayState, tooltip = "DIVIORA Music", onAction = { visible = true }, menu = {
            Item("Open DIVIORA Music", onClick = { visible = true })
            Item("Play / Pause", onClick = { player.toggle() })
            Item("Next", onClick = { player.next() })
            Separator()
            Item("Quit (routines stop)", onClick = { exitApplication(); kotlin.system.exitProcess(0) })
        })
        Window(
            onCloseRequest = { if (store.value.settings.keepRunningInTray) visible = false else { exitApplication(); kotlin.system.exitProcess(0) } },
            visible = visible, title = "DIVIORA Music", icon = DivioraIcon, state = windowState,
        ) {
            MaterialTheme(colorScheme = DivioraColors) { DivioraDesktopApp(services) { window } }
        }
    }
}
