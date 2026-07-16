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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.service.TtsServerService
import com.example.ui.DashboardScreen
import com.example.ui.LogsScreen
import com.example.ui.RulesScreen
import com.example.ui.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.TtsViewModel
import com.example.viewmodel.TtsViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
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
            val settings by viewModel.settingsState.collectAsState()
            val darkTheme = when (settings.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.useDynamicColor
            ) {
                // Keep track of navigation history as a back-stack list of tab indices.
                var navigationStack by remember { mutableStateOf(listOf(0)) }
                val selectedTab = navigationStack.last()

                // Intercept back gesture (swipe-back) if we are not on the first screen.
                BackHandler(enabled = navigationStack.size > 1) {
                    navigationStack = navigationStack.dropLast(1)
                }

                fun navigateToTab(tab: Int) {
                    if (selectedTab != tab) {
                        // Remove previous occurrence if any, and append the new tab to the top of backstack
                        navigationStack = navigationStack.filter { it != tab } + tab
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { navigateToTab(0) },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == 0) Icons.Filled.Home else Icons.Outlined.Home,
                                        contentDescription = "主页"
                                    )
                                },
                                label = { Text("主页") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { navigateToTab(1) },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == 1) Icons.Filled.List else Icons.Outlined.List,
                                        contentDescription = "日志"
                                    )
                                },
                                label = { Text("日志") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { navigateToTab(2) },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == 2) Icons.Filled.Edit else Icons.Outlined.Edit,
                                        contentDescription = "规则"
                                    )
                                },
                                label = { Text("规则") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { navigateToTab(3) },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == 3) Icons.Filled.Settings else Icons.Outlined.Settings,
                                        contentDescription = "设置"
                                    )
                                },
                                label = { Text("设置") }
                            )
                        }
                    }
                ) { innerPadding ->
                    when (selectedTab) {
                        0 -> DashboardScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                        1 -> LogsScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                        2 -> RulesScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                        3 -> SettingsScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
