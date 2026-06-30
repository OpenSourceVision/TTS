package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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

    // Load engines initially
    LaunchedEffect(Unit) {
        viewModel.loadEngines(context)
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
        AppHeader(
            onRefreshEngines = {
                viewModel.loadEngines(context)
            }
        )

        // Server Status Card
        ServerStatusCard(
            isServerRunning = isServerRunning,
            port = settings.port,
            onToggleServer = { shouldStart ->
                val intent = Intent(context, TtsServerService::class.java).apply {
                    action = if (shouldStart) TtsServerService.ACTION_START_SERVER else TtsServerService.ACTION_STOP_SERVER
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "引擎选择",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Engine Dropdown Selector
                var expanded by remember { mutableStateOf(false) }
                val selectedEngineLabel = engines.firstOrNull { it.packageName == settings.targetEnginePackage }?.label ?: settings.targetEnginePackage

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedEngineLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("目标TTS合成引擎") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = true }
                            .testTag("engine_dropdown_trigger"),
                        enabled = false, // Disable typing, tap is handled by parent Box clickable
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "展开选择")
                        },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    // Transparent overlay to detect clicks
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { expanded = true }
                    )

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

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))

                // Port Selection / Setting
                Row(
                    modifier = Modifier.fillMaxWidth(),
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

                    var portInputInternal by remember { mutableStateOf(settings.port.toString()) }
                    var isEditingPort by remember { mutableStateOf(false) }

                    if (isEditingPort) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = portInputInternal,
                                onValueChange = { portInputInternal = it.filter { char -> char.isDigit() } },
                                modifier = Modifier
                                    .width(90.dp)
                                    .testTag("port_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    val newPort = portInputInternal.toIntOrNull() ?: 8080
                                    viewModel.updateSettings(settings.copy(port = newPort))
                                    isEditingPort = false
                                },
                                modifier = Modifier.testTag("save_port_button")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "保存", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                portInputInternal = settings.port.toString()
                                isEditingPort = true
                            },
                            modifier = Modifier.testTag("edit_port_button")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "修改端口", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("修改")
                        }
                    }
                }
            }
        }

        // Test Synthesis Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "测试朗读",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

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

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (isTesting) {
                        Button(
                            onClick = {
                                viewModel.stopTest()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("stop_test_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "停止")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("停止测试")
                        }
                    } else {
                        Button(
                            onClick = {
                                viewModel.playTest(context, testText, settings.targetEnginePackage, settings.speechRate, settings.pitch)
                            },
                            modifier = Modifier.testTag("start_test_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "播放")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("开始测试")
                        }
                    }
                }
            }
        }

        // Import & Export Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "阅读联动",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.copyLegadoConfig(context) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("copy_config_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "复制")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("复制配置")
                    }

                    Button(
                        onClick = { viewModel.importToLegado(context) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_config_button")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "导入")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("导入阅读")
                    }
                }
            }
        }
    }
}

@Composable
fun AppHeader(onRefreshEngines: () -> Unit) {
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
                color = MaterialTheme.colorScheme.primary
            )
        }

        IconButton(
            onClick = onRefreshEngines,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .testTag("refresh_engines_button")
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "刷新系统TTS引擎")
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
