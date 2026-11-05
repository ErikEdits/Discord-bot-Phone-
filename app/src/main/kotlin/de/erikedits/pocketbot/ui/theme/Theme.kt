package de.erikedits.pocketbot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Discord-like dark palette (SPEC 9).
object PbColors {
    val Blurple = Color(0xFF5865F2)
    val BlurpleDark = Color(0xFF4752C4)
    val Green = Color(0xFF23A55A)
    val Red = Color(0xFFF23F43)
    val Yellow = Color(0xFFF0B232)
    val Grey = Color(0xFF80848E)
    val Bg0 = Color(0xFF1E1F22)
    val Bg1 = Color(0xFF2B2D31)
    val Bg2 = Color(0xFF313338)
    val Bg3 = Color(0xFF383A40)
    val Text = Color(0xFFF2F3F5)
    val TextMuted = Color(0xFFB5BAC1)
}

private val scheme = darkColorScheme(
    primary = PbColors.Blurple,
    onPrimary = Color.White,
    primaryContainer = PbColors.BlurpleDark,
    onPrimaryContainer = Color.White,
    secondary = PbColors.Green,
    onSecondary = Color.White,
    tertiary = PbColors.Yellow,
    onTertiary = Color.Black,
    error = PbColors.Red,
    onError = Color.White,
    background = PbColors.Bg2,
    onBackground = PbColors.Text,
    surface = PbColors.Bg2,
    onSurface = PbColors.Text,
    surfaceVariant = PbColors.Bg1,
    onSurfaceVariant = PbColors.TextMuted,
    surfaceContainerLowest = PbColors.Bg0,
    surfaceContainerLow = PbColors.Bg1,
    surfaceContainer = PbColors.Bg1,
    surfaceContainerHigh = PbColors.Bg3,
    surfaceContainerHighest = PbColors.Bg3,
    outline = PbColors.Grey,
    outlineVariant = PbColors.Bg3,
)

@Composable
fun PocketBotTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
