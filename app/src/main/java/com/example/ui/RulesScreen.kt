package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RuleEntity
import com.example.data.RuleGroupEntity
import com.example.viewmodel.TtsViewModel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

val CustomSortIcon: ImageVector
    get() = ImageVector.Builder(
        name = "CustomSort",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round
    ) {
        moveTo(4f, 6f)
        lineTo(20f, 6f)
        moveTo(4f, 12f)
        lineTo(16f, 12f)
        moveTo(4f, 18f)
        lineTo(10f, 18f)
    }.build()

enum class RuleSortType(val label: String) {
    DEFAULT("默认排序"),
    NAME_ASC("按目标字 A-Z"),
    NAME_DESC("按目标字 Z-A"),
    RULE_COUNT_DESC("按规则数量 ⬇")
}

@Composable
fun RulesScreen(
    viewModel: TtsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val ruleGroups by viewModel.ruleGroupsState.collectAsState()
    val rules by viewModel.rulesState.collectAsState()
    
    // Expanded Groups tracking (all closed by default)
    var expandedGroupIds by remember { mutableStateOf(setOf<Long>()) }

    var currentSortType by remember { mutableStateOf(RuleSortType.DEFAULT) }
    var showSortMenu by remember { mutableStateOf(false) }

    val sortedRuleGroups = remember(ruleGroups, rules, currentSortType) {
        when (currentSortType) {
            RuleSortType.DEFAULT -> ruleGroups
            RuleSortType.NAME_ASC -> ruleGroups.sortedBy { it.name }
            RuleSortType.NAME_DESC -> ruleGroups.sortedByDescending { it.name }
            RuleSortType.RULE_COUNT_DESC -> ruleGroups.sortedByDescending { group ->
                rules.count { it.groupId == group.id }
            }
        }
    }
    
    var showAddGroupDialog by remember { mutableStateOf(false) }
    var showEditGroupDialogForGroup by remember { mutableStateOf<RuleGroupEntity?>(null) }
    var groupNameInput by remember { mutableStateOf("") }
    var groupReplacementInput by remember { mutableStateOf("") }
    
    var showAddRuleDialogForGroup by remember { mutableStateOf<RuleGroupEntity?>(null) }
    var showEditRuleDialogForRule by remember { mutableStateOf<RuleEntity?>(null) }
    var ruleReplacementInput by remember { mutableStateOf("") }
    var ruleMatchInput by remember { mutableStateOf("") }
    var ruleIsForwardMatch by remember { mutableStateOf(true) }
    
    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }
    
    var showExportDialog by remember { mutableStateOf(false) }
    var exportJsonText by remember { mutableStateOf("") }
    
    var showMenu by remember { mutableStateOf(false) }
    
    // File Import launcher
    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val stringBuilder = java.lang.StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stringBuilder.append(line).append("\n")
                    }
                    val jsonContent = stringBuilder.toString()
                    viewModel.importRulesFromJson(jsonContent) { success ->
                        if (success) {
                            showImportDialog = false
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "读取文件失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // File Export Launcher (creates custom text/json file)
    val fileExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                coroutineScope.launch {
                    val jsonContent = viewModel.exportRulesToJsonString()
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(jsonContent.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "导出至文件成功", Toast.LENGTH_SHORT).show()
                    showExportDialog = false
                }
            } catch (e: Exception) {
                Toast.makeText(context, "写入文件失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "规则",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Sort Menu
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = CustomSortIcon,
                            contentDescription = "排序",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        RuleSortType.values().forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = type.label)
                                        if (currentSortType == type) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "已选择",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    currentSortType = type
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }

                // Options Menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多选项",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("新增分组") },
                            onClick = {
                                showMenu = false
                                groupNameInput = ""
                                groupReplacementInput = ""
                                showAddGroupDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("导入规则") },
                            onClick = {
                                showMenu = false
                                importJsonText = ""
                                showImportDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("导出规则") },
                            onClick = {
                                showMenu = false
                                coroutineScope.launch {
                                    exportJsonText = viewModel.exportRulesToJsonString()
                                    showExportDialog = true
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Rules List
        if (ruleGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "无规则",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "暂无替换规则",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "点击“新增分组”为要修复的多音字创建分组并输入替换字（例如：重 ➔ 虫），然后添加具体前后文匹配规则（例如：一重 -> 一虫）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sortedRuleGroups, key = { it.id }) { group ->
                    val groupRules = rules.filter { it.groupId == group.id }
                    val isExpanded = expandedGroupIds.contains(group.id)
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Group Header (Clickable area to fold/unfold)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedGroupIds = if (isExpanded) {
                                            expandedGroupIds - group.id
                                        } else {
                                            expandedGroupIds + group.id
                                        }
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Expand/Collapse Indicator
                                    Text(
                                        text = if (isExpanded) "▼" else "▶",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )

                                    Text(
                                        text = if (group.replacement.isNotEmpty()) "${group.name} ➔ ${group.replacement}" else group.name,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "(${groupRules.size} 条)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }

                                Row {
                                    IconButton(
                                        onClick = {
                                            // Pre-populate target rule replacement with the group replacement default
                                            ruleReplacementInput = group.replacement
                                            ruleMatchInput = ""
                                            ruleIsForwardMatch = true
                                            showAddRuleDialogForGroup = group
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = "在组中新增规则",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            groupNameInput = group.name
                                            groupReplacementInput = group.replacement
                                            showEditGroupDialogForGroup = group
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "修改分组",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.deleteRuleGroup(group.id)
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "删除分组",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }

                            // Only show body if group is expanded
                            if (isExpanded) {
                                if (groupRules.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        groupRules.forEach { rule ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Direction Tag
                                                val directionText = if (rule.matchWord.isEmpty()) "全局替换" 
                                                                    else if (rule.isForwardMatch) "向前匹配" 
                                                                    else "向后匹配"
                                                val directionBg = if (rule.matchWord.isEmpty()) MaterialTheme.colorScheme.secondaryContainer
                                                                  else if (rule.isForwardMatch) MaterialTheme.colorScheme.primaryContainer
                                                                  else MaterialTheme.colorScheme.tertiaryContainer
                                                val directionColor = if (rule.matchWord.isEmpty()) MaterialTheme.colorScheme.onSecondaryContainer
                                                                     else if (rule.isForwardMatch) MaterialTheme.colorScheme.onPrimaryContainer
                                                                     else MaterialTheme.colorScheme.onTertiaryContainer
                                                
                                                Text(
                                                    text = directionText,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = directionColor,
                                                    modifier = Modifier
                                                        .background(directionBg, RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                )

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    RadioButton(
                                                        selected = rule.isEnabled,
                                                        onClick = { viewModel.toggleRuleEnabled(rule) }
                                                    )
                                                    IconButton(
                                                        onClick = {
                                                            ruleReplacementInput = rule.replacement
                                                            ruleMatchInput = rule.matchWord
                                                            ruleIsForwardMatch = rule.isForwardMatch
                                                            showEditRuleDialogForRule = rule
                                                        },
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Edit,
                                                            contentDescription = "修改规则",
                                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { viewModel.deleteRule(rule.id) },
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Delete,
                                                            contentDescription = "删除规则",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "字组内暂无匹配规则，点击右上角加号新增规则",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs ---

    // 1. Add Group Dialog
    if (showAddGroupDialog) {
        AlertDialog(
            onDismissRequest = { showAddGroupDialog = false },
            title = { Text("新增多音字分组") },
            text = {
                Column {
                    Text(
                        "输入需要修复拼音的多音字（如：重、还、长、行 等）以及其首选/默认的替换发音字（如：虫、孩、常、航）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = groupNameInput,
                        onValueChange = { groupNameInput = it.take(10) },
                        label = { Text("多音字目标字") },
                        placeholder = { Text("例如: 重") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = groupReplacementInput,
                        onValueChange = { groupReplacementInput = it },
                        label = { Text("默认替换发音字") },
                        placeholder = { Text("例如: 虫") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addRuleGroup(groupNameInput.trim(), groupReplacementInput.trim())
                        showAddGroupDialog = false
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddGroupDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 1b. Edit Group Dialog
    showEditGroupDialogForGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { showEditGroupDialogForGroup = null },
            title = { Text("修改多音字分组") },
            text = {
                Column {
                    Text(
                        "修改多音字目标字以及其首选/默认的替换发音字。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = groupNameInput,
                        onValueChange = { groupNameInput = it.take(10) },
                        label = { Text("多音字目标字") },
                        placeholder = { Text("例如: 重") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = groupReplacementInput,
                        onValueChange = { groupReplacementInput = it },
                        label = { Text("默认替换发音字") },
                        placeholder = { Text("例如: 虫") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateRuleGroup(group.id, groupNameInput.trim(), groupReplacementInput.trim())
                        showEditGroupDialogForGroup = null
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditGroupDialogForGroup = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 2. Add Rule Dialog
    showAddRuleDialogForGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { showAddRuleDialogForGroup = null },
            title = { Text("新增替换规则") },
            text = {
                Column {
                    // SLIDING SWITCH FOR MATCH DIRECTION AT THE TOP!
                    Text(
                        "匹配方向 (滑动选择):",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    
                    // GORGEOUS CUSTOM SLIDING SWITCH / SEGMENTED TAB SELECTOR AT THE TOP
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Forward Match option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (ruleIsForwardMatch) MaterialTheme.colorScheme.primary
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                                .clickable { ruleIsForwardMatch = true }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "向前匹配",
                                color = if (ruleIsForwardMatch) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (ruleIsForwardMatch) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }

                        // Backward Match option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (!ruleIsForwardMatch) MaterialTheme.colorScheme.primary
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                                .clickable { ruleIsForwardMatch = false }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "向后匹配",
                                color = if (!ruleIsForwardMatch) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (!ruleIsForwardMatch) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Display target group name
                    Text(
                        text = "所属多音字字组: ${group.name}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = ruleReplacementInput,
                        onValueChange = { ruleReplacementInput = it.trim() },
                        label = { Text("替换发音为 (替换字)") },
                        placeholder = { Text("例如: 虫") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = ruleMatchInput,
                        onValueChange = { ruleMatchInput = it },
                        label = { Text("当出现这些字时 (匹配字/前后文)") },
                        placeholder = { Text("例如: 一|二|三") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text(
                        "留空则为无条件全局替换；多字匹配请用 | 符号分隔，如“一|二|三”",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    if (ruleMatchInput.isNotEmpty()) {
                        Text(
                            text = if (ruleIsForwardMatch) {
                                "💡 效果: 当 text 匹配到“[${ruleMatchInput}]${group.name}”时，把其中的“${group.name}”发音替换为“${ruleReplacementInput}”。(例如: 一${group.name} -> 一${ruleReplacementInput})"
                            } else {
                                "💡 效果: 当 text 匹配到“${group.name}[${ruleMatchInput}]”时，把其中的“${group.name}”发音替换为“${ruleReplacementInput}”。(例如: ${group.name}量 -> ${ruleReplacementInput}量)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        Text(
                            text = "💡 效果: 将句子中所有的“${group.name}”全局替换为“${ruleReplacementInput}”。",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addRule(
                            groupId = group.id,
                            target = group.name,
                            replacement = ruleReplacementInput.trim(),
                            matchWord = ruleMatchInput.trim(),
                            isForwardMatch = ruleIsForwardMatch
                        )
                        showAddRuleDialogForGroup = null
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRuleDialogForGroup = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 2b. Edit Rule Dialog
    showEditRuleDialogForRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { showEditRuleDialogForRule = null },
            title = { Text("修改替换规则") },
            text = {
                Column {
                    // SLIDING SWITCH FOR MATCH DIRECTION AT THE TOP!
                    Text(
                        "匹配方向 (滑动选择):",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    
                    // GORGEOUS CUSTOM SLIDING SWITCH / SEGMENTED TAB SELECTOR AT THE TOP
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Forward Match option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (ruleIsForwardMatch) MaterialTheme.colorScheme.primary
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                                .clickable { ruleIsForwardMatch = true }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "向前匹配",
                                color = if (ruleIsForwardMatch) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (ruleIsForwardMatch) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }

                        // Backward Match option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (!ruleIsForwardMatch) MaterialTheme.colorScheme.primary
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                                .clickable { ruleIsForwardMatch = false }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "向后匹配",
                                color = if (!ruleIsForwardMatch) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (!ruleIsForwardMatch) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Display target group name
                    Text(
                        text = "所属多音字目标: ${rule.target}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = ruleReplacementInput,
                        onValueChange = { ruleReplacementInput = it.trim() },
                        label = { Text("替换发音为 (替换字)") },
                        placeholder = { Text("例如: 虫") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = ruleMatchInput,
                        onValueChange = { ruleMatchInput = it },
                        label = { Text("当出现这些字时 (匹配字/前后文)") },
                        placeholder = { Text("例如: 一|二|三") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text(
                        "留空则为无条件全局替换；多字匹配请用 | 符号分隔，如“一|二|三”",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    if (ruleMatchInput.isNotEmpty()) {
                        Text(
                            text = if (ruleIsForwardMatch) {
                                "💡 效果: 当 text 匹配到“[${ruleMatchInput}]${rule.target}”时，把其中的“${rule.target}”发音替换为“${ruleReplacementInput}”。(例如: 一${rule.target} -> 一${ruleReplacementInput})"
                            } else {
                                "💡 效果: 当 text 匹配到“${rule.target}[${ruleMatchInput}]”时，把其中的“${rule.target}”发音替换为“${ruleReplacementInput}”。(例如: ${rule.target}量 -> ${ruleReplacementInput}量)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        Text(
                            text = "💡 效果: 将句子中所有的“${rule.target}”全局替换为“${ruleReplacementInput}”。",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateRule(
                            ruleId = rule.id,
                            replacement = ruleReplacementInput.trim(),
                            matchWord = ruleMatchInput.trim(),
                            isForwardMatch = ruleIsForwardMatch
                        )
                        showEditRuleDialogForRule = null
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditRuleDialogForRule = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 3. Import Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入规则配置") },
            text = {
                Column {
                    Text(
                        "支持粘贴 JSON 文本或直接选择 JSON 文件导入规则配置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clipData = clipboard.primaryClip
                                    if (clipData != null && clipData.itemCount > 0) {
                                        importJsonText = clipData.getItemAt(0).text.toString()
                                        Toast.makeText(context, "已从剪贴板读取", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "获取剪贴板失败", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("粘贴剪贴板", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                fileImportLauncher.launch("application/json")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("选择JSON文件", fontSize = 12.sp)
                        }
                    }

                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = { importJsonText = it },
                        label = { Text("JSON 文本内容") },
                        placeholder = { Text("请在此粘贴 JSON 配置...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importRulesFromJson(importJsonText.trim()) { success ->
                            if (success) {
                                showImportDialog = false
                            }
                        }
                    }
                ) {
                    Text("开始导入")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 4. Export Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("导出规则配置") },
            text = {
                Column {
                    Text(
                        "您可以直接复制下方的配置 JSON，或导出到文件以便迁移和备份。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = exportJsonText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("JSON 配置文件内容") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("TTS Forwarder Rules", exportJsonText)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "配置已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "复制失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("复制到剪贴板", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                fileExportLauncher.launch("TTS_Rules_${System.currentTimeMillis() / 1000}.json")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("导出到文件", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showExportDialog = false }
                ) {
                    Text("关闭")
                }
            }
        )
    }
}

// Simple modifier extension for scaling switches
@Composable
private fun Modifier.scaleSwitch(scale: Float): Modifier = this.then(
    Modifier.size(
        width = (52 * scale).dp,
        height = (32 * scale).dp
    )
)
