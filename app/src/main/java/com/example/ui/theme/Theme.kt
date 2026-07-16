package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme =
  darkColorScheme(
    primary = AccentDark,
    secondary = AccentDark,
    tertiary = SuccessColor,
    background = BackgroundDark,
    surface = CardDark,
    surfaceVariant = CardDark,
    surfaceContainer = CardDark,
    surfaceContainerLow = CardDark,
    surfaceContainerHigh = CardDark,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = PrimaryTextDark,
    onSurface = PrimaryTextDark,
    onSurfaceVariant = SecondaryTextDark,
    outline = DividerDark,
    outlineVariant = DividerDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = AccentLight,
    secondary = AccentLight,
    tertiary = SuccessColor,
    background = BackgroundLight,
    surface = CardLight,
    surfaceVariant = CardLight,
    surfaceContainer = CardLight,
    surfaceContainerLow = CardLight,
    surfaceContainerHigh = CardLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = PrimaryTextLight,
    onSurface = PrimaryTextLight,
    onSurfaceVariant = SecondaryTextLight,
    outline = DividerLight,
    outlineVariant = DividerLight
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as Activity).window
      window.statusBarColor = colorScheme.background.toArgb()
      window.navigationBarColor = colorScheme.background.toArgb()
      val insetsController = WindowCompat.getInsetsController(window, view)
      insetsController.isAppearanceLightStatusBars = !darkTheme
      insetsController.isAppearanceLightNavigationBars = !darkTheme
    }
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
