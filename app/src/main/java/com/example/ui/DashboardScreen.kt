package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.HistoryEntity
import com.example.data.SettingsEntity
import com.example.service.TtsServerService
import com.example.viewmodel.TtsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: TtsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settingsState.collectAsState()
    val history by viewModel.historyState.collectAsState()
    val engines by viewModel.engines.collectAsState()
    val isServerRunning by TtsServerService.isServerRunning.collectAsState()

    var testText by remember { mutableStateOf("天真烂漫的微风吹拂着山谷，少年抬起头看向远方的地平线，心中充满了无限的希望与勇气。") }
    val isTesting by viewModel.isTesting.collectAsState()
    var showPortDialog by remember { mutableStateOf(false) }
    var showSpeechRateDialog by remember { mutableStateOf(false) }

    // Load engines initially if not already loaded
    LaunchedEffect(Unit) {
        if (viewModel.engines.value.isEmpty()) {
            viewModel.loadEngines(context)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        AppHeader()

        // Server Status Card
        ServerStatusCard(
            isServerRunning = isServerRunning,
            port = settings.port,
            onToggleServer = { shouldStart ->
                val intent = Intent(context, TtsServerService::class.java).apply {
                    action = if (shouldStart) TtsServerService.ACTION_START_SERVER else TtsServerService.ACTION_STOP_SERVER
                }
                if (shouldStart && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        )

        // Engine Selection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "引擎选择",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(
                        onClick = { viewModel.loadEngines(context) },
                        modifier = Modifier.testTag("refresh_engines_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "刷新系统TTS引擎",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                // Engine Dropdown Selector
                var expanded by remember { mutableStateOf(false) }
                val selectedEngineLabel = engines.firstOrNull { it.packageName == settings.targetEnginePackage }?.label ?: settings.targetEnginePackage

                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable { expanded = true }
                            .padding(horizontal = 12.dp)
                            .testTag("engine_dropdown_trigger"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedEngineLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "展开选择",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        engines.forEach { engine ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(text = engine.label, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            text = engine.packageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.updateSettings(settings.copy(targetEnginePackage = engine.packageName))
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(10.dp))

                // Speech Rate Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showSpeechRateDialog = true }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "语速设置",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "当前语速: ${String.format("%.1f", settings.speechRate)}x",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { showSpeechRateDialog = true },
                        modifier = Modifier.testTag("edit_rate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "修改语速",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(10.dp))

                // Port Selection / Setting
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showPortDialog = true }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "监听端口",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "当前使用: http://127.0.0.1:${settings.port}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { showPortDialog = true },
                        modifier = Modifier.testTag("edit_port_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "修改端口",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Test Synthesis Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "朗读测试",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (isTesting) {
                        IconButton(
                            onClick = {
                                viewModel.stopTest()
                            },
                            modifier = Modifier.testTag("stop_test_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "停止",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                viewModel.playTest(context, testText, settings.targetEnginePackage, settings.speechRate, settings.pitch)
                            },
                            modifier = Modifier.testTag("start_test_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PlayArrow,
                                contentDescription = "播放",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = testText,
                    onValueChange = { testText = it },
                    label = { Text("测试文本") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(85.dp)
                        .testTag("test_text_field"),
                    maxLines = 3
                )
            }
        }

        // Import & Export Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "TTS转发",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.copyLegadoConfig(context) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("copy_config_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "复制",
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text("复制配置")
                    }

                    OutlinedButton(
                        onClick = { viewModel.importToLegado(context) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_config_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Send,
                            contentDescription = "导入",
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text("导入阅读")
                    }
                }
            }
        }

        // Speech Rate Setting Dialog
        if (showSpeechRateDialog) {
            var rateInputInternal by remember { mutableStateOf(String.format(java.util.Locale.US, "%.1f", settings.speechRate)) }
            var sliderValue by remember { mutableStateOf(settings.speechRate.coerceIn(0.1f, 4.0f)) }

            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showSpeechRateDialog = false },
                title = {
                    Text(
                        text = "设置语速",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "调整语音合成的播放速度 (当前: ${String.format("%.1f", sliderValue)}x)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Slider(
                            value = sliderValue,
                            onValueChange = { newValue ->
                                sliderValue = Math.round(newValue * 10f) / 10f
                                rateInputInternal = String.format(java.util.Locale.US, "%.1f", sliderValue)
                            },
                            valueRange = 0.1f..4.0f,
                            steps = 38, // 0.1 to 4.0 with steps of 0.1 is 38 intermediate steps
                            modifier = Modifier.testTag("rate_dialog_slider")
                        )

                        OutlinedTextField(
                            value = rateInputInternal,
                            onValueChange = { input ->
                                val filtered = input.filter { char -> char.isDigit() || char == '.' }
                                rateInputInternal = filtered
                                filtered.toFloatOrNull()?.let {
                                    if (it in 0.1f..4.0f) {
                                        sliderValue = it
                                    }
                                }
                            },
                            label = { Text("语速数值") },
                            placeholder = { Text("1.0") },
                            supportingText = { Text("有效范围: 0.1 - 4.0") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("rate_dialog_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val finalRate = rateInputInternal.toFloatOrNull() ?: sliderValue
                            val roundedRate = (Math.round(finalRate * 10f) / 10f).coerceIn(0.1f, 4.0f)
                            viewModel.updateSettings(settings.copy(speechRate = roundedRate))
                            showSpeechRateDialog = false
                        },
                        modifier = Modifier.testTag("rate_dialog_confirm")
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showSpeechRateDialog = false },
                        modifier = Modifier.testTag("rate_dialog_cancel")
                    ) {
                        Text("取消")
                    }
                }
            )
        }

        // Port Setting Dialog
        if (showPortDialog) {
            var portInputInternal by remember { mutableStateOf(settings.port.toString()) }

            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showPortDialog = false },
                title = {
                    Text(
                        text = "设置监听端口",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "设置本地HTTP服务的端口号。修改端口后，请重启服务生效。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = portInputInternal,
                            onValueChange = { portInputInternal = it.filter { char -> char.isDigit() } },
                            label = { Text("端口号") },
                            placeholder = { Text("8080") },
                            supportingText = { Text("有效范围: 1024 - 65535") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("port_dialog_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val newPort = portInputInternal.toIntOrNull() ?: 8080
                            val validatedPort = newPort.coerceIn(1024, 65535)
                            viewModel.updateSettings(settings.copy(port = validatedPort))
                            showPortDialog = false
                        },
                        modifier = Modifier.testTag("port_dialog_confirm")
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showPortDialog = false },
                        modifier = Modifier.testTag("port_dialog_cancel")
                    ) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

@Composable
fun AppHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "主页",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ServerStatusCard(
    isServerRunning: Boolean,
    port: Int,
    onToggleServer: (Boolean) -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (isServerRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        label = "containerColor"
    )
    val textColor by animateColorAsState(
        targetValue = if (isServerRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
        label = "textColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isServerRunning) "服务正在运行" else "服务已经关闭",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isServerRunning) "正在本机端口 $port 上提供音频流" else "请点击右侧开关启动本地HTTP服务器",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.8f)
                )
            }

            Switch(
                checked = isServerRunning,
                onCheckedChange = onToggleServer,
                modifier = Modifier.testTag("server_toggle_switch")
            )
        }
    }
}
