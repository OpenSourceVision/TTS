package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.HistoryEntity
import com.example.ui.miuix.MiuixActionButton
import com.example.ui.miuix.MiuixButtonVariant
import com.example.ui.miuix.MiuixCard
import com.example.ui.miuix.MiuixDialog
import com.example.ui.miuix.MiuixDropdownMenu
import com.example.ui.miuix.MiuixDropdownMenuItem
import com.example.ui.miuix.MiuixHairlineDivider
import com.example.ui.miuix.MiuixIconButton
import com.example.ui.miuix.MiuixLargeHeader
import com.example.ui.theme.MiuixTheme
import com.example.viewmodel.TtsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(
    viewModel: TtsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val history by viewModel.historyState.collectAsState()
    val listState = rememberLazyListState()
    val colors = MiuixTheme.colors

    var selectedLogForDetail by remember { mutableStateOf<HistoryEntity?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    LaunchedEffect(history) {
        if (history.isNotEmpty()) {
            listState.scrollToItem(history.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        MiuixLargeHeader(
            title = "日志",
            actions = {
                if (history.isNotEmpty()) {
                    MiuixIconButton(
                        onClick = { viewModel.clearHistory() },
                        icon = Icons.Rounded.DeleteOutline,
                        contentDescription = "清空",
                        tint = colors.error,
                        modifier = Modifier.testTag("clear_history_log_page_button")
                    )
                }

                Box {
                    MiuixIconButton(
                        onClick = { showOverflowMenu = true },
                        icon = Icons.Rounded.MoreHoriz,
                        contentDescription = "更多",
                        tint = colors.textPrimary,
                        modifier = Modifier.testTag("overflow_menu_button")
                    )

                    MiuixDropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        MiuixDropdownMenuItem(
                            text = "复制日志",
                            leadingIcon = Icons.Rounded.ContentCopy,
                            onClick = {
                                showOverflowMenu = false
                                viewModel.copyLogsToClipboard(context)
                            },
                            modifier = Modifier.testTag("copy_logs_menu_item")
                        )
                        MiuixDropdownMenuItem(
                            text = "导出日志",
                            leadingIcon = Icons.Rounded.Share,
                            onClick = {
                                showOverflowMenu = false
                                viewModel.shareLogReport(context)
                            },
                            modifier = Modifier.testTag("export_logs_menu_item")
                        )
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = "暂无日志",
                            tint = colors.textTertiary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "暂无日志",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history, key = { it.id }) { log ->
                    val isCrash = log.status == "CRASH"
                    val isSuccess = log.status == "SUCCESS"
                    val isPartial = log.status == "PARTIAL_SUCCESS"

                    val statusColor = when {
                        isCrash -> colors.error
                        isSuccess -> colors.success
                        isPartial -> colors.warning
                        else -> colors.error
                    }
                    val statusContainer = when {
                        isCrash -> colors.errorContainer
                        isSuccess -> colors.successContainer
                        isPartial -> colors.warningContainer
                        else -> colors.errorContainer
                    }
                    val statusText = when (log.status) {
                        "CRASH" -> "崩溃"
                        "SUCCESS" -> "成功"
                        "PARTIAL_SUCCESS" -> "部分成功"
                        "SENTENCE_FAILED" -> "跳过"
                        else -> "失败"
                    }

                    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                    val formattedTime = timeFormat.format(Date(log.timestamp))
                    val hits = remember(log.hitsJson) { log.parseHits() }

                    MiuixCard(
                        containerColor = if (isCrash) colors.errorContainer.copy(alpha = 0.45f) else colors.surface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { selectedLogForDetail = log }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Row(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(statusContainer)
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(statusColor)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = statusText,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = statusColor
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${log.durationMs}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textSecondary
                                    )
                                }

                                Text(
                                    text = "${log.length}字",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = log.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary
                            )

                            if (hits.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    hits.forEach { hit ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(colors.primaryContainer)
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = "${hit.ruleTarget} → ${hit.replacement}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.onPrimaryContainer,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            MiuixHairlineDivider(insetStart = 0.dp, insetEnd = 0.dp)
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = log.enginePackage.substringAfterLast('.'),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary
                                )
                                Text(
                                    text = formattedTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textTertiary
                                )
                            }

                            if (!log.errorMsg.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.errorContainer)
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Rounded.WarningAmber,
                                                contentDescription = null,
                                                tint = colors.error,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "异常 · 点击查看",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.error,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = log.errorMsg,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textPrimary,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedLogForDetail?.let { log ->
        val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
        val formattedTime = timeFormat.format(Date(log.timestamp))

        MiuixDialog(
            onDismissRequest = { selectedLogForDetail = null },
            title = if (log.status == "CRASH") "崩溃堆栈" else "日志详情",
            subtitle = formattedTime,
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = log.enginePackage,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                    Text(
                        text = log.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary
                    )

                    val detailHits = remember(log.hitsJson) { log.parseHits() }
                    if (detailHits.isNotEmpty()) {
                        detailHits.forEach { hit ->
                            Text(
                                text = "${hit.ruleTarget} → ${hit.replacement}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (!log.errorMsg.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceSecondary)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = log.errorMsg,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            },
            buttons = {
                MiuixActionButton(
                    text = "关闭",
                    onClick = { selectedLogForDetail = null },
                    variant = MiuixButtonVariant.Secondary,
                    modifier = Modifier.weight(1f)
                )
                MiuixActionButton(
                    text = "复制",
                    icon = Icons.Rounded.ContentCopy,
                    onClick = {
                        val detailText = buildString {
                            append("时间: $formattedTime\n")
                            append("引擎: ${log.enginePackage}\n")
                            append("文本: ${log.text}")
                            val detailHits = log.parseHits()
                            if (detailHits.isNotEmpty()) {
                                append("\n规则: ")
                                append(detailHits.joinToString { "${it.ruleTarget} → ${it.replacement}" })
                            }
                            if (!log.errorMsg.isNullOrBlank()) {
                                append("\n异常:\n${log.errorMsg}")
                            }
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Log Detail", detailText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    },
                    variant = MiuixButtonVariant.Primary,
                    modifier = Modifier.weight(1f)
                )
            }
        )
    }
}
