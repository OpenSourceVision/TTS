package com.example.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.miuix.MiuixActionButton
import com.example.ui.miuix.MiuixButtonVariant
import com.example.ui.miuix.MiuixCard
import com.example.ui.miuix.MiuixDialog
import com.example.ui.miuix.MiuixDropdownMenu
import com.example.ui.miuix.MiuixDropdownMenuItem
import com.example.ui.miuix.MiuixHairlineDivider
import com.example.ui.miuix.MiuixLargeHeader
import com.example.ui.miuix.MiuixSmallTitle
import com.example.ui.miuix.MiuixSuperArrowRow
import com.example.ui.miuix.MiuixSuperSwitchRow
import com.example.ui.miuix.MiuixTextField
import com.example.ui.theme.MiuixTheme
import com.example.viewmodel.TtsViewModel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LanguageOption(val label: String, val language: String, val country: String)

val languageOptions = listOf(
    LanguageOption("简体中文", "zh", "CN")
)

@Composable
fun SettingsScreen(
    viewModel: TtsViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settingsState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val colors = MiuixTheme.colors

    var webdavUrl by remember { mutableStateOf(settings.webdavUrl) }
    var webdavUsername by remember { mutableStateOf(settings.webdavUsername) }
    var webdavPassword by remember { mutableStateOf(settings.webdavPassword) }
    var webdavPath by remember { mutableStateOf(settings.webdavPath) }
    var webdavDir by remember { mutableStateOf(settings.webdavDir) }
    var showWebdavConfigDialog by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        webdavUrl = settings.webdavUrl
        webdavUsername = settings.webdavUsername
        webdavPassword = settings.webdavPassword
        webdavPath = settings.webdavPath
        webdavDir = settings.webdavDir
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        MiuixLargeHeader(
            title = "设置",
            actions = {
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable {
                            try {
                                uriHandler.openUri("https://github.com/OpenSourceVision/TTS")
                            } catch (e: Exception) {
                                Toast.makeText(context, "无法打开网页: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .testTag("github_logo_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_github),
                        contentDescription = "GitHub",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }
        )

        // 1. General
        MiuixSmallTitle(text = "通用")

        MiuixCard {
            var themeDropdownExpanded by remember { mutableStateOf(false) }
            val themeLabel = when (settings.themeMode) {
                1 -> "浅色"
                2 -> "深色"
                else -> "自动"
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                MiuixSuperArrowRow(
                    title = "主题",
                    endValue = themeLabel,
                    isDropdown = true,
                    onClick = { themeDropdownExpanded = true }
                )

                MiuixDropdownMenu(
                    expanded = themeDropdownExpanded,
                    onDismissRequest = { themeDropdownExpanded = false }
                ) {
                    MiuixDropdownMenuItem(
                        text = "跟随系统",
                        selected = settings.themeMode == 0,
                        onClick = {
                            viewModel.updateSettings(settings.copy(themeMode = 0))
                            themeDropdownExpanded = false
                        }
                    )
                    MiuixDropdownMenuItem(
                        text = "浅色",
                        selected = settings.themeMode == 1,
                        onClick = {
                            viewModel.updateSettings(settings.copy(themeMode = 1))
                            themeDropdownExpanded = false
                        }
                    )
                    MiuixDropdownMenuItem(
                        text = "深色",
                        selected = settings.themeMode == 2,
                        onClick = {
                            viewModel.updateSettings(settings.copy(themeMode = 2))
                            themeDropdownExpanded = false
                        }
                    )
                }
            }

            MiuixHairlineDivider(insetStart = 16.dp)

            var langDropdownExpanded by remember { mutableStateOf(false) }
            val currentOption = languageOptions.find {
                it.language.lowercase() == settings.language.lowercase() &&
                    it.country.lowercase() == settings.country.lowercase()
            } ?: LanguageOption("${settings.language} (${settings.country})", settings.language, settings.country)

            Box(modifier = Modifier.fillMaxWidth()) {
                MiuixSuperArrowRow(
                    title = "语言",
                    endValue = currentOption.label,
                    isDropdown = true,
                    onClick = { langDropdownExpanded = true }
                )

                MiuixDropdownMenu(
                    expanded = langDropdownExpanded,
                    onDismissRequest = { langDropdownExpanded = false }
                ) {
                    languageOptions.forEach { option ->
                        MiuixDropdownMenuItem(
                            text = option.label,
                            selected = option.language == currentOption.language && option.country == currentOption.country,
                            onClick = {
                                viewModel.updateSettings(
                                    settings.copy(language = option.language, country = option.country)
                                )
                                langDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // 2. Background / Battery
        MiuixSmallTitle(text = "后台")

        var isBatteryOptimizing by remember { mutableStateOf(viewModel.isIgnoringBatteryOptimizations(context)) }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    isBatteryOptimizing = viewModel.isIgnoringBatteryOptimizations(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        MiuixCard {
            MiuixSuperSwitchRow(
                title = "忽略电池优化",
                checked = isBatteryOptimizing,
                onCheckedChange = {
                    viewModel.requestIgnoreBatteryOptimizations(context)
                },
                switchModifier = Modifier.testTag("battery_optimization_button")
            )
        }

        // 3. Backup & Restore
        MiuixSmallTitle(text = "备份")

        val coroutineScope = rememberCoroutineScope()

        val fileExportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    try {
                        val jsonContent = viewModel.backupToLocalString()
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(jsonContent.toByteArray(Charsets.UTF_8))
                        }
                        Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "导出失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val fileImportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val reader = BufferedReader(InputStreamReader(inputStream))
                            val stringBuilder = StringBuilder()
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                stringBuilder.append(line)
                            }
                            viewModel.restoreFromLocalString(stringBuilder.toString()) { result ->
                                if (!result.isSuccess) {
                                    Toast.makeText(
                                        context,
                                        "恢复失败: ${result.exceptionOrNull()?.localizedMessage}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "读取失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        MiuixCard {
            MiuixSuperArrowRow(
                title = "本地备份",
                endValue = "导出",
                onClick = {
                    val formattedDate = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    fileExportLauncher.launch("TTS_Forwarder_Backup_$formattedDate.json")
                },
                modifier = Modifier.testTag("local_backup_button")
            )

            MiuixHairlineDivider(insetStart = 16.dp)

            MiuixSuperArrowRow(
                title = "本地恢复",
                endValue = "导入",
                onClick = {
                    fileImportLauncher.launch("application/json")
                },
                modifier = Modifier.testTag("local_restore_button")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        MiuixCard {
            MiuixSuperArrowRow(
                title = "WebDAV",
                endValue = if (settings.webdavUrl.isBlank()) "未配置" else "已配置",
                onClick = { showWebdavConfigDialog = true },
                modifier = Modifier.testTag("webdav_config_trigger")
            )

            MiuixHairlineDivider(insetStart = 16.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var syncingBackup by remember { mutableStateOf(false) }
                var syncingRestore by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(enabled = !syncingBackup) {
                            if (settings.webdavUrl.isBlank()) {
                                Toast.makeText(context, "请先配置 WebDAV", Toast.LENGTH_SHORT).show()
                                showWebdavConfigDialog = true
                                return@clickable
                            }
                            syncingBackup = true
                            viewModel.backupToWebDav { result ->
                                syncingBackup = false
                                if (!result.isSuccess) {
                                    Toast.makeText(
                                        context,
                                        "备份失败: ${result.exceptionOrNull()?.localizedMessage}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                        .testTag("webdav_backup_now_button"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudUpload,
                        contentDescription = null,
                        tint = if (syncingBackup) colors.textTertiary else colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (syncingBackup) "备份中..." else "备份",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (syncingBackup) colors.textTertiary else colors.primary
                    )
                }

                Box(
                    modifier = Modifier
                        .width(0.5.dp)
                        .height(22.dp)
                        .background(colors.divider)
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(enabled = !syncingRestore) {
                            if (settings.webdavUrl.isBlank()) {
                                Toast.makeText(context, "请先配置 WebDAV", Toast.LENGTH_SHORT).show()
                                showWebdavConfigDialog = true
                                return@clickable
                            }
                            syncingRestore = true
                            viewModel.restoreFromWebDav { result ->
                                syncingRestore = false
                                if (!result.isSuccess) {
                                    Toast.makeText(
                                        context,
                                        "恢复失败: ${result.exceptionOrNull()?.localizedMessage}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                        .testTag("webdav_restore_now_button"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDownload,
                        contentDescription = null,
                        tint = if (syncingRestore) colors.textTertiary else colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (syncingRestore) "恢复中..." else "恢复",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (syncingRestore) colors.textTertiary else colors.textPrimary
                    )
                }
            }
        }

        // 4. Version
        MiuixSmallTitle(text = "关于")

        val packageInfo = remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (e: Exception) {
                null
            }
        }
        val versionName = packageInfo?.versionName ?: "1.0.0"

        MiuixCard {
            MiuixSuperArrowRow(
                title = "版本",
                endValue = "v$versionName",
                showArrow = false
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showWebdavConfigDialog) {
        MiuixDialog(
            onDismissRequest = { showWebdavConfigDialog = false },
            title = "WebDAV",
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MiuixTextField(
                        value = webdavUrl,
                        onValueChange = { webdavUrl = it.trim() },
                        label = "服务器地址",
                        placeholder = "https://dav.jianguoyun.com/dav/",
                        singleLine = true,
                        modifier = Modifier.testTag("webdav_url_input")
                    )

                    MiuixTextField(
                        value = webdavUsername,
                        onValueChange = { webdavUsername = it.trim() },
                        label = "账号",
                        placeholder = "用户名或邮箱",
                        singleLine = true,
                        modifier = Modifier.testTag("webdav_username_input")
                    )

                    MiuixTextField(
                        value = webdavPassword,
                        onValueChange = { webdavPassword = it.trim() },
                        label = "密码",
                        placeholder = "应用授权密码",
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.testTag("webdav_password_input")
                    )

                    MiuixTextField(
                        value = webdavDir,
                        onValueChange = { webdavDir = it.trim() },
                        label = "目录",
                        placeholder = "TTS",
                        singleLine = true,
                        modifier = Modifier.testTag("webdav_dir_input")
                    )

                    MiuixTextField(
                        value = webdavPath,
                        onValueChange = { webdavPath = it.trim() },
                        label = "文件名",
                        placeholder = "tts_rules_backup.json",
                        singleLine = true,
                        modifier = Modifier.testTag("webdav_path_input")
                    )

                    var testingConnection by remember { mutableStateOf(false) }

                    MiuixActionButton(
                        text = if (testingConnection) "测试中..." else "测试连接",
                        onClick = {
                            if (webdavUrl.isBlank()) {
                                Toast.makeText(context, "请输入服务器地址", Toast.LENGTH_SHORT).show()
                                return@MiuixActionButton
                            }
                            testingConnection = true
                            viewModel.testWebDavConnection(webdavUrl, webdavUsername, webdavPassword, webdavDir) { result ->
                                testingConnection = false
                                if (result.isSuccess) {
                                    Toast.makeText(context, "连接成功", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "连接失败: ${result.exceptionOrNull()?.localizedMessage}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        enabled = !testingConnection,
                        variant = MiuixButtonVariant.TonalPrimary,
                        height = 42.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("webdav_test_button")
                    )
                }
            },
            buttons = {
                MiuixActionButton(
                    text = "取消",
                    onClick = { showWebdavConfigDialog = false },
                    variant = MiuixButtonVariant.Secondary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("webdav_config_cancel")
                )
                MiuixActionButton(
                    text = "保存",
                    onClick = {
                        viewModel.updateSettings(
                            settings.copy(
                                webdavUrl = webdavUrl,
                                webdavUsername = webdavUsername,
                                webdavPassword = webdavPassword,
                                webdavPath = webdavPath,
                                webdavDir = webdavDir
                            )
                        )
                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                        showWebdavConfigDialog = false
                    },
                    variant = MiuixButtonVariant.Primary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("webdav_save_button")
                )
            }
        )
    }
}
