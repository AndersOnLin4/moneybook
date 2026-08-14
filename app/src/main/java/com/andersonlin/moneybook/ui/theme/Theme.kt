package com.andersonlin.moneybook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.andersonlin.moneybook.data.settings.ThemeMode

/** 统计饼图配色（循环使用） */
val ChartColors = listOf(
    Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFF2196F3), Color(0xFFE91E63),
    Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFF44336), Color(0xFF795548),
    Color(0xFF3F51B5), Color(0xFFFFC107), Color(0xFF009688), Color(0xFF8BC34A)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F0B5),
    onPrimaryContainer = Color(0xFF002107),
    secondary = Color(0xFF52634F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E8CF),
    onSecondaryContainer = Color(0xFF101F10),
    tertiary = Color(0xFF38656A),
    background = Color(0xFFF6FBF1),
    onBackground = Color(0xFF171D16),
    surface = Color(0xFFF6FBF1),
    onSurface = Color(0xFF171D16),
    surfaceVariant = Color(0xFFDEE5D8),
    onSurfaceVariant = Color(0xFF424940),
    outline = Color(0xFF727970),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9CD69B),
    onPrimary = Color(0xFF00390D),
    primaryContainer = Color(0xFF14521F),
    onPrimaryContainer = Color(0xFFB8F0B5),
    secondary = Color(0xFFB9CCB3),
    onSecondary = Color(0xFF243425),
    secondaryContainer = Color(0xFF3A4B3A),
    onSecondaryContainer = Color(0xFFD5E8CF),
    tertiary = Color(0xFFA1CED4),
    background = Color(0xFF0F1510),
    onBackground = Color(0xFFDEE4DA),
    surface = Color(0xFF0F1510),
    onSurface = Color(0xFFDEE4DA),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BD),
    outline = Color(0xFF8C9388),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val AppTypography = Typography()

@Composable
fun MoneyBookTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        content = content
    )
}
