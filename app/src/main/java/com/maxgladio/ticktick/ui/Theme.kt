package com.maxgladio.ticktick.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Paper = Color(0xFFF6F5EF)
val Ink = Color(0xFF252C24)
val Muted = Color(0xFF74796D)
val Olive = Color(0xFF485D39)
val Lime = Color(0xFFD9E9AA)
val Line = Color(0xFFE2E4D9)
val Card = Color(0xFFFFFFFF)

@Composable
fun TickTickTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Olive, onPrimary = Color.White,
            secondary = Olive, secondaryContainer = Lime, onSecondaryContainer = Ink,
            background = Paper, onBackground = Ink,
            surface = Paper, onSurface = Ink, surfaceVariant = Line, onSurfaceVariant = Muted,
            outline = Muted, outlineVariant = Line,
        ),
        content = content,
    )
}
