package com.syncbridge.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Blue = Color(0xFF3B82F6)
val BlueDark = Color(0xFF2563EB)
val Green = Color(0xFF10B981)
val Red = Color(0xFFEF4444)
val Orange = Color(0xFFF59E0B)
val Purple = Color(0xFF8B5CF6)
val BgDark = Color(0xFF0F172A)
val CardDark = Color(0xFF1E293B)
val BorderDark = Color(0xFF334155)
val TextPrimary = Color(0xFFF8FAFC)
val TextMuted = Color(0xFF94A3B8)

private val DarkColorScheme = darkColorScheme(
    primary = Blue,
    secondary = Green,
    tertiary = Orange,
    background = BgDark,
    surface = CardDark,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = BorderDark,
    error = Red
)

@Composable
fun SyncBridgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
