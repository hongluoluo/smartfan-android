package com.ekko.smartfan.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Purple,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = PurpleSoft,
    onPrimaryContainer = PurpleDark,
    secondary = PurpleLight,
    background = BgLight,
    onBackground = TextPrimary,
    surface = SurfaceLight,
    onSurface = TextPrimary,
    surfaceVariant = PurpleCard,
    onSurfaceVariant = TextSecondary,
    error = AccentRed
)

private val DarkColors = darkColorScheme(
    primary = PurpleLight,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF241E33),
    primaryContainer = CardDark,
    onPrimaryContainer = PurpleLight,
    secondary = Purple,
    background = BgDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = CardDark,
    onSurfaceVariant = TextSecondaryDark,
    error = AccentRed
)

@Composable
fun SmartFanTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
