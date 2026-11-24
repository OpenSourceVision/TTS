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
data class CouiColorPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceSecondary: Color,
    val surfaceHighlight: Color,
    val cardBorder: Color,
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

private val LightCouiPalette = CouiColorPalette(
    isDark = false,
    background = CouiBackgroundLight,
    surface = CouiSurfaceLight,
    surfaceSecondary = CouiSurfaceVariantLight,
    surfaceHighlight = CouiSurfaceContainerHighLight,
    cardBorder = CouiCardBorderLight,
    navBar = CouiNavBarLight,
    primary = CouiPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = CouiPrimaryContainerLight,
    onPrimaryContainer = CouiOnPrimaryContainerLight,
    textPrimary = CouiTextPrimaryLight,
    textSecondary = CouiTextSecondaryLight,
    textTertiary = CouiTextTertiaryLight,
    divider = CouiDividerLight,
    switchTrackOff = CouiSwitchTrackOffLight,
    success = CouiSuccess,
    successContainer = CouiSuccessContainerLight,
    warning = CouiWarning,
    warningContainer = CouiWarningContainerLight,
    error = CouiError,
    errorContainer = CouiErrorContainerLight
)

private val DarkCouiPalette = CouiColorPalette(
    isDark = true,
    background = CouiBackgroundDark,
    surface = CouiSurfaceDark,
    surfaceSecondary = CouiSurfaceVariantDark,
    surfaceHighlight = CouiSurfaceContainerHighDark,
    cardBorder = CouiCardBorderDark,
    navBar = CouiNavBarDark,
    primary = CouiPrimaryDark,
    onPrimary = Color.White,
    primaryContainer = CouiPrimaryContainerDark,
    onPrimaryContainer = CouiOnPrimaryContainerDark,
    textPrimary = CouiTextPrimaryDark,
    textSecondary = CouiTextSecondaryDark,
    textTertiary = CouiTextTertiaryDark,
    divider = CouiDividerDark,
    switchTrackOff = CouiSwitchTrackOffDark,
    success = CouiSuccess,
    successContainer = CouiSuccessContainerDark,
    warning = CouiWarning,
    warningContainer = CouiWarningContainerDark,
    error = CouiError,
    errorContainer = CouiErrorContainerDark
)

val LocalCouiColors = staticCompositionLocalOf { LightCouiPalette }

object CouiTheme {
    val colors: CouiColorPalette
        @Composable get() = LocalCouiColors.current
}

private val DarkColorScheme = darkColorScheme(
    primary = CouiPrimaryDark,
    secondary = CouiPrimaryDark,
    tertiary = CouiSuccess,
    background = CouiBackgroundDark,
    surface = CouiSurfaceDark,
    surfaceVariant = CouiSurfaceVariantDark,
    surfaceContainer = CouiSurfaceDark,
    surfaceContainerLow = CouiSurfaceDark,
    surfaceContainerHigh = CouiSurfaceContainerHighDark,
    primaryContainer = CouiPrimaryContainerDark,
    onPrimaryContainer = CouiOnPrimaryContainerDark,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = CouiTextPrimaryDark,
    onSurface = CouiTextPrimaryDark,
    onSurfaceVariant = CouiTextSecondaryDark,
    error = CouiError,
    errorContainer = CouiErrorContainerDark,
    onErrorContainer = CouiError,
    outline = CouiDividerDark,
    outlineVariant = CouiDividerDark
)

private val LightColorScheme = lightColorScheme(
    primary = CouiPrimaryLight,
    secondary = CouiPrimaryLight,
    tertiary = CouiSuccess,
    background = CouiBackgroundLight,
    surface = CouiSurfaceLight,
    surfaceVariant = CouiSurfaceVariantLight,
    surfaceContainer = CouiSurfaceLight,
    surfaceContainerLow = CouiSurfaceLight,
    surfaceContainerHigh = CouiSurfaceContainerHighLight,
    primaryContainer = CouiPrimaryContainerLight,
    onPrimaryContainer = CouiOnPrimaryContainerLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = CouiTextPrimaryLight,
    onSurface = CouiTextPrimaryLight,
    onSurfaceVariant = CouiTextSecondaryLight,
    error = CouiError,
    errorContainer = CouiErrorContainerLight,
    onErrorContainer = CouiError,
    outline = CouiDividerLight,
    outlineVariant = CouiDividerLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val couiPalette = if (darkTheme) DarkCouiPalette else LightCouiPalette
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

    CompositionLocalProvider(LocalCouiColors provides couiPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
