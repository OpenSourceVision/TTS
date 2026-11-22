package com.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.service.TtsServerService
import com.example.ui.DashboardScreen
import com.example.ui.LogsScreen
import com.example.ui.RulesScreen
import com.example.ui.SettingsScreen
import com.example.ui.miuix.MiuixNavigationBar
import com.example.ui.miuix.MiuixNavigationItem
import com.example.ui.theme.MiuixTheme
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.TtsViewModel
import com.example.viewmodel.TtsViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val viewModel: TtsViewModel by viewModels {
        TtsViewModelFactory(this, database)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request POST_NOTIFICATIONS permission for foreground service on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        // Handle Toast event subscription
        lifecycleScope.launch {
            viewModel.toastEvent.collectLatest { message ->
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        // Auto start TTS Server if configured
        lifecycleScope.launch {
            val settings = withContext(Dispatchers.IO) { database.appDao().getSettings() }
            if (settings != null && settings.autoStartServer && !TtsServerService.isServerRunning.value) {
                val intent = Intent(this@MainActivity, TtsServerService::class.java).apply {
                    action = TtsServerService.ACTION_START_SERVER
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
        }

        setContent {
            MainApp(viewModel = viewModel)
        }
    }
}

@Composable
fun MainApp(
    viewModel: TtsViewModel,
    initialTab: Int = 0
) {
    val settings by viewModel.settingsState.collectAsState()
    val darkTheme = when (settings.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    MyApplicationTheme(
        darkTheme = darkTheme
    ) {
        var navigationStack by remember { mutableStateOf(listOf(initialTab)) }
        val selectedTab = navigationStack.last()

        BackHandler(enabled = navigationStack.size > 1) {
            navigationStack = navigationStack.dropLast(1)
        }

        fun navigateToTab(tab: Int) {
            if (selectedTab != tab) {
                navigationStack = navigationStack.filter { it != tab } + tab
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MiuixTheme.colors.background,
            contentWindowInsets = WindowInsets.statusBars,
            bottomBar = {
                MiuixNavigationBar {
                    MiuixNavigationItem(
                        selected = selectedTab == 0,
                        onClick = { navigateToTab(0) },
                        icon = Icons.Outlined.Home,
                        selectedIcon = Icons.Rounded.Home,
                        label = "主页"
                    )
                    MiuixNavigationItem(
                        selected = selectedTab == 1,
                        onClick = { navigateToTab(1) },
                        icon = Icons.AutoMirrored.Outlined.Subject,
                        selectedIcon = Icons.AutoMirrored.Rounded.Subject,
                        label = "日志"
                    )
                    MiuixNavigationItem(
                        selected = selectedTab == 2,
                        onClick = { navigateToTab(2) },
                        icon = Icons.Outlined.Tune,
                        selectedIcon = Icons.Rounded.Tune,
                        label = "规则"
                    )
                    MiuixNavigationItem(
                        selected = selectedTab == 3,
                        onClick = { navigateToTab(3) },
                        icon = Icons.Outlined.Settings,
                        selectedIcon = Icons.Rounded.Settings,
                        label = "设置"
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colors.background)
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                val contentModifier = Modifier.widthIn(max = 760.dp)
                when (selectedTab) {
                    0 -> DashboardScreen(
                        viewModel = viewModel,
                        modifier = contentModifier
                    )
                    1 -> LogsScreen(
                        viewModel = viewModel,
                        modifier = contentModifier
                    )
                    2 -> RulesScreen(
                        viewModel = viewModel,
                        modifier = contentModifier
                    )
                    3 -> SettingsScreen(
                        viewModel = viewModel,
                        modifier = contentModifier
                    )
                }
            }
        }
    }
}
