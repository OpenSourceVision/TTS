package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class MiuixColorPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceSecondary: Color,
    val surfaceHighlight: Color,
    val navBar: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val switchTrackOff: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val error: Color,
    val errorContainer: Color
)

private val LightMiuixPalette = MiuixColorPalette(
    isDark = false,
    background = MiuixBackgroundLight,
    surface = MiuixSurfaceLight,
    surfaceSecondary = MiuixSurfaceVariantLight,
    surfaceHighlight = MiuixSurfaceContainerHighLight,
    navBar = MiuixNavBarLight,
    primary = MiuixPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = MiuixPrimaryContainerLight,
    onPrimaryContainer = MiuixOnPrimaryContainerLight,
    textPrimary = MiuixTextPrimaryLight,
    textSecondary = MiuixTextSecondaryLight,
    textTertiary = MiuixTextTertiaryLight,
    divider = MiuixDividerLight,
    switchTrackOff = MiuixSwitchTrackOffLight,
    success = MiuixSuccess,
    successContainer = MiuixSuccessContainerLight,
    warning = MiuixWarning,
    warningContainer = MiuixWarningContainerLight,
    error = MiuixError,
    errorContainer = MiuixErrorContainerLight
)

private val DarkMiuixPalette = MiuixColorPalette(
    isDark = true,
    background = MiuixBackgroundDark,
    surface = MiuixSurfaceDark,
    surfaceSecondary = MiuixSurfaceVariantDark,
    surfaceHighlight = MiuixSurfaceContainerHighDark,
    navBar = MiuixNavBarDark,
    primary = MiuixPrimaryDark,
    onPrimary = Color.White,
    primaryContainer = MiuixPrimaryContainerDark,
    onPrimaryContainer = MiuixOnPrimaryContainerDark,
    textPrimary = MiuixTextPrimaryDark,
    textSecondary = MiuixTextSecondaryDark,
    textTertiary = MiuixTextTertiaryDark,
    divider = MiuixDividerDark,
    switchTrackOff = MiuixSwitchTrackOffDark,
    success = MiuixSuccess,
    successContainer = MiuixSuccessContainerDark,
    warning = MiuixWarning,
    warningContainer = MiuixWarningContainerDark,
    error = MiuixError,
    errorContainer = MiuixErrorContainerDark
)

val LocalMiuixColors = staticCompositionLocalOf { LightMiuixPalette }

object MiuixTheme {
    val colors: MiuixColorPalette
        @Composable get() = LocalMiuixColors.current
}

private val DarkColorScheme = darkColorScheme(
    primary = MiuixPrimaryDark,
    secondary = MiuixPrimaryDark,
    tertiary = MiuixSuccess,
    background = MiuixBackgroundDark,
    surface = MiuixSurfaceDark,
    surfaceVariant = MiuixSurfaceVariantDark,
    surfaceContainer = MiuixSurfaceDark,
    surfaceContainerLow = MiuixSurfaceDark,
    surfaceContainerHigh = MiuixSurfaceContainerHighDark,
    primaryContainer = MiuixPrimaryContainerDark,
    onPrimaryContainer = MiuixOnPrimaryContainerDark,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = MiuixTextPrimaryDark,
    onSurface = MiuixTextPrimaryDark,
    onSurfaceVariant = MiuixTextSecondaryDark,
    error = MiuixError,
    errorContainer = MiuixErrorContainerDark,
    onErrorContainer = MiuixError,
    outline = MiuixDividerDark,
    outlineVariant = MiuixDividerDark
)

private val LightColorScheme = lightColorScheme(
    primary = MiuixPrimaryLight,
    secondary = MiuixPrimaryLight,
    tertiary = MiuixSuccess,
    background = MiuixBackgroundLight,
    surface = MiuixSurfaceLight,
    surfaceVariant = MiuixSurfaceVariantLight,
    surfaceContainer = MiuixSurfaceLight,
    surfaceContainerLow = MiuixSurfaceLight,
    surfaceContainerHigh = MiuixSurfaceContainerHighLight,
    primaryContainer = MiuixPrimaryContainerLight,
    onPrimaryContainer = MiuixOnPrimaryContainerLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = MiuixTextPrimaryLight,
    onSurface = MiuixTextPrimaryLight,
    onSurfaceVariant = MiuixTextSecondaryLight,
    error = MiuixError,
    errorContainer = MiuixErrorContainerLight,
    onErrorContainer = MiuixError,
    outline = MiuixDividerLight,
    outlineVariant = MiuixDividerLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val miuixPalette = if (darkTheme) DarkMiuixPalette else LightMiuixPalette
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalMiuixColors provides miuixPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
