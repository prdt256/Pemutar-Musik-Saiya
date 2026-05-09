package com.example.pemutarmusik.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Warna kustom terinspirasi Nomad Music
val Pink500 = Color(0xFFE91E63)
val Pink700 = Color(0xFFC2185B)
val Pink200 = Color(0xFFF48FB1)

val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkSurfaceVariant = Color(0xFF2A2A2A)

private val DarkColorScheme = darkColorScheme(
    primary = Pink500,
    onPrimary = Color.White,
    primaryContainer = Pink700,
    onPrimaryContainer = Color.White,
    secondary = Pink200,
    onSecondary = Color.Black,
    background = DarkBackground,
    onBackground = Color.White,
    surface = DarkSurface,
    onSurface = Color.White,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB0B0B0),
    outline = Color(0xFF444444)
)

@Composable
fun PemutarMusikTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
