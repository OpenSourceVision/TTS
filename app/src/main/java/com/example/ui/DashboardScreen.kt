package com.example.ui

import android.content.Intent
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.service.TtsServerService
import com.example.ui.miuix.MiuixActionButton
import com.example.ui.miuix.MiuixBentoActionCard
import com.example.ui.miuix.MiuixBentoMetricCard
import com.example.ui.miuix.MiuixButtonVariant
import com.example.ui.miuix.MiuixCard
import com.example.ui.miuix.MiuixDialog
import com.example.ui.miuix.MiuixDropdownMenu
import com.example.ui.miuix.MiuixDropdownMenuItem
import com.example.ui.miuix.MiuixIconButton
import com.example.ui.miuix.MiuixLargeHeader
import com.example.ui.miuix.MiuixSlider
import com.example.ui.miuix.MiuixSmallTitle
import com.example.ui.miuix.MiuixSuperSwitchRow
import com.example.ui.miuix.MiuixTextField
import com.example.ui.theme.MiuixTheme
import com.example.viewmodel.TtsViewModel
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: TtsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settingsState.collectAsState()
    val engines by viewModel.engines.collectAsState()
    val isServerRunning by TtsServerService.isServerRunning.collectAsState()

    var testText by remember { mutableStateOf("世界是你们的，也是我们的，但是归根结底还是你们的。") }
    val isTesting by viewModel.isTesting.collectAsState()
    var showPortDialog by remember { mutableStateOf(false) }
    var showSpeechRateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (viewModel.engines.value.isEmpty()) {
            viewModel.loadEngines(context)
        }
    }

    val colors = MiuixTheme.colors

    fun toggleServer(shouldStart: Boolean) {
        val intent = Intent(context, TtsServerService::class.java).apply {
            action = if (shouldStart) TtsServerService.ACTION_START_SERVER else TtsServerService.ACTION_STOP_SERVER
        }
        if (shouldStart && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        MiuixLargeHeader(
            title = "主页",
            actions = {
                val badgeBg by animateColorAsState(
                    targetValue = if (isServerRunning) colors.successContainer else colors.surface,
                    animationSpec = tween(200),
                    label = "headerStatusBg"
                )
                val badgeDot by animateColorAsState(
                    targetValue = if (isServerRunning) colors.success else colors.textTertiary,
                    animationSpec = tween(200),
                    label = "headerStatusDot"
                )
                val badgeText by animateColorAsState(
                    targetValue = if (isServerRunning) colors.success else colors.textSecondary,
                    animationSpec = tween(200),
                    label = "headerStatusText"
                )

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(badgeBg)
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(badgeDot)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isServerRunning) "运行中 · :${settings.port}" else "已停止",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeText
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 1. Standalone Server Switch Card
        MiuixCard {
            MiuixSuperSwitchRow(
                title = "服务开关",
                summary = if (isServerRunning) "127.0.0.1:${settings.port}" else null,
                checked = isServerRunning,
                onCheckedChange = ::toggleServer,
                minHeight = 84.dp,
                switchModifier = Modifier.testTag("server_toggle_switch")
            )
        }

        // 2. Service Configuration (Engine + 2-Column Bento Metric Cards)
        MiuixSmallTitle(text = "配置")

        MiuixCard {
            val selectedEngineLabel = engines.firstOrNull {
                it.packageName == settings.targetEnginePackage
            }?.label ?: settings.targetEnginePackage.ifBlank { "未选择" }
            var engineDropdownExpanded by remember { mutableStateOf(false) }

            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 84.dp)
                        .clickable { engineDropdownExpanded = true }
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
                        .testTag("engine_dropdown_trigger"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "引擎",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = selectedEngineLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.UnfoldMore,
                            contentDescription = "展开选择",
                            tint = colors.textTertiary,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .width(0.75.dp)
                                .height(18.dp)
                                .background(colors.divider)
                        )
                        MiuixIconButton(
                            onClick = { viewModel.loadEngines(context) },
                            icon = Icons.Rounded.Refresh,
                            contentDescription = "刷新引擎",
                            tint = colors.textSecondary,
                            iconSize = 19.dp,
                            modifier = Modifier.testTag("refresh_engines_button")
                        )
                    }
                }

                MiuixDropdownMenu(
                    expanded = engineDropdownExpanded,
                    onDismissRequest = { engineDropdownExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.88f)
                ) {
                    if (engines.isEmpty()) {
                        MiuixDropdownMenuItem(
                            text = "无可用引擎",
                            onClick = { engineDropdownExpanded = false }
                        )
                    } else {
                        engines.forEach { engine ->
                            MiuixDropdownMenuItem(
                                text = engine.label,
                                subtitle = engine.packageName,
                                selected = engine.packageName == settings.targetEnginePackage,
                                onClick = {
                                    viewModel.updateSettings(settings.copy(targetEnginePackage = engine.packageName))
                                    engineDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MiuixBentoMetricCard(
                label = "语速",
                value = "${String.format(Locale.US, "%.1f", settings.speechRate)}x",
                onClick = { showSpeechRateDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .testTag("edit_rate_button")
            )
            MiuixBentoMetricCard(
                label = "端口",
                value = settings.port.toString(),
                onClick = { showPortDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .testTag("edit_port_button")
            )
        }

        // 3. Compact Speech Test Card
        MiuixSmallTitle(text = "朗读测试")

        MiuixCard {
            Column(modifier = Modifier.padding(14.dp)) {
                MiuixTextField(
                    value = testText,
                    onValueChange = { testText = it },
                    placeholder = "输入测试文本",
                    singleLine = false,
                    minLines = 4,
                    maxLines = 5,
                    modifier = Modifier.testTag("test_text_field")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (isTesting) {
                        MiuixActionButton(
                            text = "停止",
                            onClick = { viewModel.stopTest() },
                            variant = MiuixButtonVariant.Destructive,
                            icon = Icons.Rounded.Stop,
                            height = 36.dp,
                            modifier = Modifier.testTag("stop_test_button")
                        )
                    } else {
                        MiuixActionButton(
                            text = "试听",
                            onClick = {
                                viewModel.playTest(
                                    context,
                                    testText,
                                    settings.targetEnginePackage,
                                    settings.speechRate,
                                    settings.pitch
                                )
                            },
                            variant = MiuixButtonVariant.TonalPrimary,
                            icon = Icons.Rounded.PlayArrow,
                            height = 36.dp,
                            modifier = Modifier.testTag("start_test_button")
                        )
                    }
                }
            }
        }

        // 4. Legado Export / Quick Actions (2-Column Bento Tiles)
        MiuixSmallTitle(text = "转发")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MiuixBentoActionCard(
                title = "复制",
                icon = Icons.Rounded.ContentCopy,
                isPrimary = false,
                onClick = { viewModel.copyLegadoConfig(context) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("copy_config_button")
            )
            MiuixBentoActionCard(
                title = "导入",
                icon = Icons.AutoMirrored.Rounded.Send,
                isPrimary = false,
                onClick = { viewModel.importToLegado(context) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("import_config_button")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    // Speech Rate Dialog
    if (showSpeechRateDialog) {
        var rateInputInternal by remember {
            mutableStateOf(String.format(Locale.US, "%.1f", settings.speechRate))
        }
        var sliderValue by remember {
            mutableStateOf(settings.speechRate.coerceIn(0.1f, 4.0f))
        }

        MiuixDialog(
            onDismissRequest = { showSpeechRateDialog = false },
            title = "语速",
            subtitle = "${String.format(Locale.US, "%.1f", sliderValue)}x",
            content = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    MiuixSlider(
                        value = sliderValue,
                        onValueChange = { newValue ->
                            sliderValue = Math.round(newValue * 10f) / 10f
                            rateInputInternal = String.format(Locale.US, "%.1f", sliderValue)
                        },
                        valueRange = 0.1f..4.0f,
                        steps = 38,
                        modifier = Modifier.testTag("rate_dialog_slider")
                    )

                    MiuixTextField(
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
                        placeholder = "1.0",
                        supportingText = "范围 0.1 - 4.0",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.testTag("rate_dialog_input")
                    )
                }
            },
            buttons = {
                MiuixActionButton(
                    text = "取消",
                    onClick = { showSpeechRateDialog = false },
                    variant = MiuixButtonVariant.Secondary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("rate_dialog_cancel")
                )
                MiuixActionButton(
                    text = "确定",
                    onClick = {
                        val finalRate = rateInputInternal.toFloatOrNull() ?: sliderValue
                        val roundedRate = (Math.round(finalRate * 10f) / 10f).coerceIn(0.1f, 4.0f)
                        viewModel.updateSettings(settings.copy(speechRate = roundedRate))
                        showSpeechRateDialog = false
                    },
                    variant = MiuixButtonVariant.Primary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("rate_dialog_confirm")
                )
            }
        )
    }

    // Port Dialog
    if (showPortDialog) {
        var portInputInternal by remember { mutableStateOf(settings.port.toString()) }

        MiuixDialog(
            onDismissRequest = { showPortDialog = false },
            title = "监听端口",
            subtitle = "修改后重启服务生效",
            content = {
                MiuixTextField(
                    value = portInputInternal,
                    onValueChange = { portInputInternal = it.filter { char -> char.isDigit() } },
                    placeholder = "8080",
                    supportingText = "范围 1024 - 65535",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.testTag("port_dialog_input")
                )
            },
            buttons = {
                MiuixActionButton(
                    text = "取消",
                    onClick = { showPortDialog = false },
                    variant = MiuixButtonVariant.Secondary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("port_dialog_cancel")
                )
                MiuixActionButton(
                    text = "确定",
                    onClick = {
                        val newPort = portInputInternal.toIntOrNull() ?: 8080
                        val validatedPort = newPort.coerceIn(1024, 65535)
                        viewModel.updateSettings(settings.copy(port = validatedPort))
                        showPortDialog = false
                    },
                    variant = MiuixButtonVariant.Primary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("port_dialog_confirm")
                )
            }
        )
    }
}
