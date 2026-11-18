package com.diviora.music.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.diviora.music.container
import com.diviora.music.data.repo.AppSettings
import com.diviora.music.ui.components.MiniPlayer
import com.diviora.music.ui.screens.HistoryScreen
import com.diviora.music.ui.screens.HomeScreen
import com.diviora.music.ui.screens.LibraryScreen
import com.diviora.music.ui.screens.NowPlayingScreen
import com.diviora.music.ui.screens.PlaylistScreen
import com.diviora.music.ui.screens.RoutineEditorScreen
import com.diviora.music.ui.screens.RoutinesScreen
import com.diviora.music.ui.screens.SettingsScreen
import com.diviora.music.ui.screens.VideoSurface
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** App-wide one-shot messages shown as snackbars. */
object UiEvents {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()
    fun message(text: String) { _messages.tryEmit(text) }

    private val _routes = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val routes = _routes.asSharedFlow()
    /** Opens the routine editor pre-filled with these items, so the user picks the date and time. */
    fun scheduleItems(mediaIds: List<Long>) { _routes.tryEmit("routine/0?media=" + mediaIds.joinToString(",")) }
    fun open(route: String) { _routes.tryEmit(route) }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)
private val tabs = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("library", "Library", Icons.Filled.LibraryMusic),
    Tab("routines", "Routines", Icons.Filled.Schedule),
    Tab("settings", "Settings", Icons.Filled.Settings),
)

@Composable
fun DivioraRoot(inPip: Boolean, openRoute: String?, onRouteConsumed: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val player by c.player.state.collectAsStateWithLifecycle()
    if (inPip) { VideoSurface(Modifier.fillMaxSize()); return }

    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { UiEvents.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { UiEvents.routes.collect { nav.navigate(it) { launchSingleTop = true } } }
    LaunchedEffect(openRoute) { openRoute?.let { nav.navigate(it) { launchSingleTop = true }; onRouteConsumed() } }

    // First-run permissions: media read + notifications. Then scan the library.
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings(onboarded = true))
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        UiEvents.message("Scanning your music…")
        RescanRequest.request()
    }
    LaunchedEffect(settings.onboarded) {
        if (!settings.onboarded) {
            c.settings.setOnboarded()
            permLauncher.launch(requiredPermissions())
        }
    }
    LaunchedEffect(Unit) {
        RescanRequest.requests.collect {
            val r = c.library.scanMediaStore()
            UiEvents.message("Library updated: ${r.added} new, ${r.updated} refreshed")
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showChrome = route in tabs.map { it.route } || route?.startsWith("playlist") == true || route == "history"
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showChrome) Column {
                MiniPlayer(player, onOpen = { nav.navigate("player") }, onToggle = c.player::togglePlay, onNext = c.player::next, onClose = c.player::close)
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { nav.navigate(t.route) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } },
                            icon = { Icon(t.icon, null) }, label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") { HomeScreen(nav) }
            composable("library") { LibraryScreen(nav) }
            composable("routines") { RoutinesScreen(nav) }
            composable("settings") { SettingsScreen(nav) }
            composable("history") { HistoryScreen(nav) }
            composable("player") { NowPlayingScreen(nav) }
            composable("embed/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                com.diviora.music.ui.screens.EmbedPlayerScreen(nav, it.arguments?.getLong("id") ?: 0L)
            }
            composable(
                "routine/{id}?media={media}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("media") { type = NavType.StringType; defaultValue = "" },
                ),
            ) {
                RoutineEditorScreen(nav, it.arguments?.getLong("id") ?: 0L,
                    prefillMediaIds = it.arguments?.getString("media").orEmpty().split(',').mapNotNull(String::toLongOrNull))
            }
            composable("playlist/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                PlaylistScreen(nav, it.arguments?.getLong("id") ?: 0L)
            }
        }
    }
}

fun requiredPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= 33) {
        add(Manifest.permission.READ_MEDIA_AUDIO); add(Manifest.permission.READ_MEDIA_VIDEO); add(Manifest.permission.POST_NOTIFICATIONS)
    } else add(Manifest.permission.READ_EXTERNAL_STORAGE)
}.toTypedArray()

/** Requests a MediaStore rescan from anywhere in the UI. */
object RescanRequest {
    private val _requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requests = _requests.asSharedFlow()
    fun request() { _requests.tryEmit(Unit) }
}

fun NavHostController.openRoutine(id: Long = 0) = navigate("routine/$id")

/** Hands a protected-platform link (YouTube, Spotify, …) to that platform's own app or the browser. */
fun openInPlatformApp(context: android.content.Context, url: String) {
    val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        .addCategory(android.content.Intent.CATEGORY_BROWSABLE).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(i) }.onFailure { UiEvents.message("No app can open this link") }
}

/** Shows the right message for a failed "add stream", handing platform links off to their app. */
fun handleAddStreamFailure(context: android.content.Context, e: Throwable) {
    if (e is com.diviora.music.core.catalog.PlatformLinkException) {
        UiEvents.message(e.message ?: "Opening in ${e.platform}"); openInPlatformApp(context, e.url)
    } else UiEvents.message(e.message ?: "Invalid URL")
}
