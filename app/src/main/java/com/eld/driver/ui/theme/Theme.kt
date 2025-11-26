package com.eld.driver.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Blue600,
    onPrimary = BgPrimary,
    primaryContainer = Blue500,
    onPrimaryContainer = BgPrimary,
    secondary = AccentGreen,
    onSecondary = BgPrimary,
    tertiary = AccentOrange,
    onTertiary = BgPrimary,
    background = BgSecondary,
    onBackground = TextPrimary,
    surface = BgPrimary,
    onSurface = TextPrimary,
    surfaceVariant = BgSecondary,
    onSurfaceVariant = TextSecondary,
    error = AccentRed,
    onError = BgPrimary,
    outline = BorderMedium,
    outlineVariant = BorderLight
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue500,
    onPrimary = BgPrimaryDark,
    primaryContainer = Blue700,
    onPrimaryContainer = BgPrimaryDark,
    secondary = AccentGreen,
    onSecondary = BgPrimaryDark,
    tertiary = AccentOrange,
    onTertiary = BgPrimaryDark,
    background = BgSecondaryDark,
    onBackground = TextPrimaryDark,
    surface = BgPrimaryDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = BgSecondaryDark,
    onSurfaceVariant = TextSecondaryDark,
    error = AccentRed,
    onError = BgPrimaryDark,
    outline = BorderMedium,
    outlineVariant = BorderDark
)

@Composable
fun ELDDriverTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
