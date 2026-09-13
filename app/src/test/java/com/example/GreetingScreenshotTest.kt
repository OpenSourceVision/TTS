package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.ui.DashboardScreen
import com.example.ui.RulesScreen
import com.example.ui.LogsScreen
import com.example.ui.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.TtsViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val database = AppDatabase.getDatabase(context)
    val viewModel = TtsViewModel(context, database)
    
    composeTestRule.setContent {
      MyApplicationTheme {
        DashboardScreen(viewModel = viewModel)
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/dashboard.png")
  }

  @Test
  fun rules_screenshot() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val database = AppDatabase.getDatabase(context)
    val viewModel = TtsViewModel(context, database)
    
    composeTestRule.setContent {
      MyApplicationTheme {
        RulesScreen(viewModel = viewModel)
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/rules.png")
  }

  @Test
  fun logs_screenshot() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val database = AppDatabase.getDatabase(context)
    val viewModel = TtsViewModel(context, database)
    
    composeTestRule.setContent {
      MyApplicationTheme {
        LogsScreen(viewModel = viewModel)
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/logs.png")
  }

  @Test
  fun settings_screenshot() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val database = AppDatabase.getDatabase(context)
    val viewModel = TtsViewModel(context, database)
    
    composeTestRule.setContent {
      MyApplicationTheme {
        SettingsScreen(viewModel = viewModel)
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/settings.png")
  }
}
