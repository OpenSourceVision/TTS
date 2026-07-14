package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Switch
import android.widget.Toast
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import com.example.data.SettingsEntity
import com.example.viewmodel.TtsViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.PasswordVisualTransformation

data class LanguageOption(val label: String, val language: String, val country: String)

val languageOptions = listOf(
    LanguageOption("中文 (中国)", "zh", "CN")
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
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Settings Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            IconButton(
                onClick = {
                    try {
                        uriHandler.openUri("https://github.com/OpenSourceVision/TTS")
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法打开网页: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.size(48.dp).testTag("github_logo_button")
            ) {
                Icon(
                    painter = painterResource(id = com.example.R.drawable.ic_github),
                    contentDescription = "GitHub 项目地址",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 1. General Settings Section (Theme & Language combined)
        Text(
            text = "通用设置",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column {
                // Theme Selection Row
                var dropdownExpanded by remember { mutableStateOf(false) }
                val themeLabel = when (settings.themeMode) {
                    1 -> "浅色模式"
                    2 -> "深色模式"
                    else -> "跟随系统 (自动)"
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dropdownExpanded = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "主题模式",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = themeLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "选择主题",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("跟随系统 (自动)") },
                            onClick = {
                                viewModel.updateSettings(settings.copy(themeMode = 0))
                                dropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("浅色模式") },
                            onClick = {
                                viewModel.updateSettings(settings.copy(themeMode = 1))
                                dropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("深色模式") },
                            onClick = {
                                viewModel.updateSettings(settings.copy(themeMode = 2))
                                dropdownExpanded = false
                            }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))

                // Dynamic Theme Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "动态主题",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = settings.useDynamicColor,
                        onCheckedChange = { isChecked ->
                            viewModel.updateSettings(settings.copy(useDynamicColor = isChecked))
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))

                // Language Selection Row
                var langDropdownExpanded by remember { mutableStateOf(false) }
                val currentOption = languageOptions.find { it.language.lowercase() == settings.language.lowercase() && it.country.lowercase() == settings.country.lowercase() }
                    ?: LanguageOption("${settings.language} (${settings.country})", settings.language, settings.country)

                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { langDropdownExpanded = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "默认语言",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = currentOption.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "选择语言",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = langDropdownExpanded,
                        onDismissRequest = { langDropdownExpanded = false }
                    ) {
                        languageOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    viewModel.updateSettings(settings.copy(language = option.language, country = option.country))
                                    langDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 3. Battery Optimization Card
        Text(
            text = "电池优化",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val context = LocalContext.current
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

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "后台保护设置",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "加入电池优化白名单，保障后台稳定运行。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!isBatteryOptimizing) {
                        OutlinedButton(
                            onClick = {
                                viewModel.requestIgnoreBatteryOptimizations(context)
                                isBatteryOptimizing = viewModel.isIgnoringBatteryOptimizations(context)
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("battery_optimization_button")
                        ) {
                            Text("加入", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "已加入",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "已加入",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 4. Backup & Restore Card
        Text(
            text = "备份恢复",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

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
                        Toast.makeText(context, "备份成功", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "备份失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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
                            val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                            val stringBuilder = StringBuilder()
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                stringBuilder.append(line)
                            }
                            viewModel.restoreFromLocalString(stringBuilder.toString()) { result ->
                                if (!result.isSuccess) {
                                    Toast.makeText(context, "恢复失败: ${result.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "读取备份文件失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Local Backup Row
                Row(
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "本地备份",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val formattedDate = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                            fileExportLauncher.launch("TTS_Forwarder_Backup_$formattedDate.json")
                        },
                        modifier = Modifier.weight(1f).testTag("local_backup_button")
                    ) {
                        Text("备份", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(
                        onClick = {
                            fileImportLauncher.launch("application/json")
                        },
                        modifier = Modifier.weight(1f).testTag("local_restore_button")
                    ) {
                        Text("恢复", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(16.dp))

                // WebDav Backup Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "WebDAV",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    TextButton(
                        onClick = { showWebdavConfigDialog = true },
                        modifier = Modifier.height(32.dp).testTag("webdav_config_trigger"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "WebDAV 配置",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WebDAV 配置", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var syncingBackup by remember { mutableStateOf(false) }
                    var syncingRestore by remember { mutableStateOf(false) }

                    Button(
                        onClick = {
                            if (settings.webdavUrl.isBlank()) {
                                Toast.makeText(context, "请先配置 WebDAV 服务器", Toast.LENGTH_SHORT).show()
                                showWebdavConfigDialog = true
                                return@Button
                            }
                            syncingBackup = true
                            viewModel.backupToWebDav { result ->
                                syncingBackup = false
                                if (!result.isSuccess) {
                                    Toast.makeText(context, "备份失败: ${result.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !syncingBackup,
                        modifier = Modifier.weight(1f).testTag("webdav_backup_now_button")
                    ) {
                        Text(if (syncingBackup) "备份中..." else "备份", style = MaterialTheme.typography.bodySmall)
                    }

                    OutlinedButton(
                        onClick = {
                            if (settings.webdavUrl.isBlank()) {
                                Toast.makeText(context, "请先配置 WebDAV 服务器", Toast.LENGTH_SHORT).show()
                                showWebdavConfigDialog = true
                                return@OutlinedButton
                            }
                            syncingRestore = true
                            viewModel.restoreFromWebDav { result ->
                                syncingRestore = false
                                if (!result.isSuccess) {
                                    Toast.makeText(context, "恢复失败: ${result.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !syncingRestore,
                        modifier = Modifier.weight(1f).testTag("webdav_restore_now_button")
                    ) {
                        Text(if (syncingRestore) "恢复中..." else "恢复", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (showWebdavConfigDialog) {
            AlertDialog(
                onDismissRequest = { showWebdavConfigDialog = false },
                title = {
                    Text(
                        text = "配置 WebDAV",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = webdavUrl,
                            onValueChange = { webdavUrl = it.trim() },
                            label = { Text("WebDAV 服务器地址") },
                            placeholder = { Text("如: https://dav.jianguoyun.com/dav/") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("webdav_url_input")
                        )

                        OutlinedTextField(
                            value = webdavUsername,
                            onValueChange = { webdavUsername = it.trim() },
                            label = { Text("用户名/邮箱") },
                            placeholder = { Text("WebDAV 账号") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("webdav_username_input")
                        )

                        OutlinedTextField(
                            value = webdavPassword,
                            onValueChange = { webdavPassword = it.trim() },
                            label = { Text("密码/应用授权密码") },
                            placeholder = { Text("WebDAV 密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().testTag("webdav_password_input")
                        )

                        OutlinedTextField(
                            value = webdavDir,
                            onValueChange = { webdavDir = it.trim() },
                            label = { Text("云端备份目录") },
                            placeholder = { Text("TTS") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("webdav_dir_input")
                        )

                        OutlinedTextField(
                            value = webdavPath,
                            onValueChange = { webdavPath = it.trim() },
                            label = { Text("云端备份文件名") },
                            placeholder = { Text("tts_rules_backup.json") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("webdav_path_input")
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        var testingConnection by remember { mutableStateOf(false) }

                        OutlinedButton(
                            onClick = {
                                if (webdavUrl.isBlank()) {
                                    Toast.makeText(context, "请先输入服务器地址", Toast.LENGTH_SHORT).show()
                                    return@OutlinedButton
                                }
                                testingConnection = true
                                viewModel.testWebDavConnection(webdavUrl, webdavUsername, webdavPassword, webdavDir) { result ->
                                    testingConnection = false
                                    if (result.isSuccess) {
                                        Toast.makeText(context, "连接测试成功！配置正确", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "连接测试失败: ${result.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !testingConnection,
                            modifier = Modifier.fillMaxWidth().testTag("webdav_test_button")
                        ) {
                            Text(if (testingConnection) "连接测试中..." else "测试连接")
                        }
                    }
                },
                confirmButton = {
                    Button(
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
                            Toast.makeText(context, "同步配置已保存", Toast.LENGTH_SHORT).show()
                            showWebdavConfigDialog = false
                        },
                        modifier = Modifier.testTag("webdav_save_button")
                    ) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showWebdavConfigDialog = false },
                        modifier = Modifier.testTag("webdav_config_cancel")
                    ) {
                        Text("取消")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 5. Cache Clearing Card
        Text(
            text = "缓存清理",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        var cacheSize by remember { mutableStateOf("0.00 B") }
        LaunchedEffect(Unit) {
            cacheSize = viewModel.getCacheSize(context)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TTS音频缓存",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "已占用: $cacheSize",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            viewModel.clearCache(context) {
                                cacheSize = viewModel.getCacheSize(context)
                            }
                        },
                        modifier = Modifier.testTag("clear_cache_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "清理",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 6. Version Info Card
        Text(
            text = "版本信息",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val packageInfo = remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (e: Exception) {
                null
            }
        }
        val versionName = packageInfo?.versionName ?: "1.0.0"

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "当前版本",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "v$versionName",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

    }
}
