package com.diviora.music.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.diviora.music.data.repo.ThemeMode

// Original DIVIORA palette: violet primary, rose accent, deep ink background.
private val Violet = Color(0xFF8B5CF6)
private val VioletLight = Color(0xFFC4B5FD)
private val Rose = Color(0xFFF472B6)
private val Ink = Color(0xFF0E0B14)
private val InkSurface = Color(0xFF17121F)
private val InkSurfaceHigh = Color(0xFF221B2E)

private val Dark = darkColorScheme(
    primary = VioletLight, onPrimary = Color(0xFF2E1065), primaryContainer = Color(0xFF4C1D95), onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = Rose, onSecondary = Color(0xFF500724), tertiary = Color(0xFF5EEAD4),
    background = Ink, onBackground = Color(0xFFF5F3FF), surface = Ink, onSurface = Color(0xFFF5F3FF),
    surfaceVariant = InkSurfaceHigh, onSurfaceVariant = Color(0xFFCBC3DA),
    surfaceContainer = InkSurface, surfaceContainerHigh = InkSurfaceHigh, surfaceContainerLow = Color(0xFF130F1A),
    outline = Color(0xFF6B6180), error = Color(0xFFFCA5A5),
)

private val Light = lightColorScheme(
    primary = Color(0xFF6D28D9), onPrimary = Color.White, primaryContainer = Color(0xFFEDE9FE), onPrimaryContainer = Color(0xFF2E1065),
    secondary = Color(0xFFDB2777), tertiary = Color(0xFF0F766E),
    background = Color(0xFFFCFAFF), surface = Color(0xFFFCFAFF), surfaceVariant = Color(0xFFEDE7F6),
    surfaceContainer = Color(0xFFF4EFFA), surfaceContainerHigh = Color(0xFFEDE7F6),
)

@Composable
fun DivioraTheme(mode: ThemeMode, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

val BrandViolet = Violet
val BrandRose = Rose
