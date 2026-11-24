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
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlin.math.abs
import kotlin.math.absoluteValue
import com.example.data.AppDatabase
import com.example.service.TtsServerService
import com.example.ui.DashboardScreen
import com.example.ui.LogsScreen
import com.example.ui.RulesScreen
import com.example.ui.SettingsScreen
import com.example.ui.coui.CouiNavigationBar
import com.example.ui.coui.CouiNavigationItem
import com.example.ui.theme.CouiTheme
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

        val pagerState = rememberPagerState(initialPage = initialTab, pageCount = { 4 })
        val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
        val dragPosition = if (isDragged) {
            (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 3f)
        } else {
            null
        }
        val couiEasing = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }
        var isProgrammaticScroll by remember { mutableStateOf(false) }

        // Keep backstack synced only when user swipes horizontally
        LaunchedEffect(pagerState.settledPage) {
            val settled = pagerState.settledPage
            if (!isProgrammaticScroll && selectedTab != settled) {
                navigateToTab(settled)
            }
        }

        // Smoothly glide pager when tab changes via bottom bar tap or BackHandler
        LaunchedEffect(selectedTab) {
            if (pagerState.currentPage != selectedTab) {
                isProgrammaticScroll = true
                try {
                    if (abs(selectedTab - pagerState.currentPage) > 1) {
                        val neighbor = if (selectedTab > pagerState.currentPage) selectedTab - 1 else selectedTab + 1
                        pagerState.scrollToPage(neighbor)
                    }
                    pagerState.animateScrollToPage(
                        page = selectedTab,
                        animationSpec = tween(durationMillis = 300, easing = couiEasing)
                    )
                } finally {
                    isProgrammaticScroll = false
                }
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = CouiTheme.colors.background,
            contentWindowInsets = WindowInsets.statusBars,
            bottomBar = {
                CouiNavigationBar(
                    selectedIndex = selectedTab,
                    itemCount = 4,
                    dragPosition = dragPosition
                ) {
                    CouiNavigationItem(
                        selected = selectedTab == 0,
                        onClick = { navigateToTab(0) },
                        icon = Icons.Outlined.Home,
                        selectedIcon = Icons.Rounded.Home,
                        label = "主页"
                    )
                    CouiNavigationItem(
                        selected = selectedTab == 1,
                        onClick = { navigateToTab(1) },
                        icon = Icons.AutoMirrored.Outlined.Subject,
                        selectedIcon = Icons.AutoMirrored.Rounded.Subject,
                        label = "日志"
                    )
                    CouiNavigationItem(
                        selected = selectedTab == 2,
                        onClick = { navigateToTab(2) },
                        icon = Icons.Outlined.Tune,
                        selectedIcon = Icons.Rounded.Tune,
                        label = "规则"
                    )
                    CouiNavigationItem(
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
                    .background(CouiTheme.colors.background)
                    .padding(top = innerPadding.calculateTopPadding()),
                contentAlignment = Alignment.TopCenter
            ) {
                val contentModifier = Modifier.widthIn(max = 760.dp)
                val bottomBarSpace = innerPadding.calculateBottomPadding()
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 3,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                        .absoluteValue
                        .coerceIn(0f, 1f)
                    val activeProgress = 1f - pageOffset
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val scale = 0.985f + 0.015f * activeProgress
                                scaleX = scale
                                scaleY = scale
                                alpha = 0.65f + 0.35f * activeProgress
                            },
                        contentAlignment = Alignment.TopCenter
                    ) {
                        when (page) {
                            0 -> DashboardScreen(
                                viewModel = viewModel,
                                bottomPadding = bottomBarSpace,
                                modifier = contentModifier
                            )
                            1 -> LogsScreen(
                                viewModel = viewModel,
                                bottomPadding = bottomBarSpace,
                                modifier = contentModifier
                            )
                            2 -> RulesScreen(
                                viewModel = viewModel,
                                bottomPadding = bottomBarSpace,
                                modifier = contentModifier
                            )
                            3 -> SettingsScreen(
                                viewModel = viewModel,
                                bottomPadding = bottomBarSpace,
                                modifier = contentModifier
                            )
                        }
                    }
                }
            }
        }
    }
}
