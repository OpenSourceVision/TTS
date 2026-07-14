package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.RuleEntity
import com.example.data.RuleGroupEntity
import com.example.viewmodel.TtsViewModel
import com.example.viewmodel.GroupSortOrder

@Composable
fun RulesScreen(
    viewModel: TtsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rulesList by viewModel.rulesState.collectAsState()
    val ruleGroupsList by viewModel.ruleGroupsState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val sortOrder by viewModel.ruleSortOrder.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showClearAllConfirmDialog by remember { mutableStateOf(false) }

    // Dialog state for Groups
    var showGroupDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<RuleGroupEntity?>(null) }
    var groupNameInput by remember { mutableStateOf("") }
    var groupReplacementInput by remember { mutableStateOf("") }

    // Dialog state for Rules
    var showRuleDialog by remember { mutableStateOf(false) }
    var targetGroupIdForRule by remember { mutableStateOf<Long?>(null) }
    var editingRule by remember { mutableStateOf<RuleEntity?>(null) }
    var ruleTargetInput by remember { mutableStateOf("") }
    var ruleReplacementInput by remember { mutableStateOf("") }

    // Track expanded state for each group (collapsed by default)
    val expandedGroups = remember { mutableStateMapOf<Long, Boolean>() }

    // Filter groups based on search query
    val filteredGroups = remember(ruleGroupsList, rulesList, searchQuery) {
        if (searchQuery.isBlank()) {
            ruleGroupsList
        } else {
            ruleGroupsList.filter { group ->
                val groupMatches = group.name.contains(searchQuery, ignoreCase = true) ||
                        group.replacement.contains(searchQuery, ignoreCase = true)
                val hasMatchingRule = rulesList.any { rule ->
                    rule.groupId == group.id && (
                        rule.target.contains(searchQuery, ignoreCase = true) ||
                        rule.replacement.contains(searchQuery, ignoreCase = true)
                    )
                }
                groupMatches || hasMatchingRule
            }
        }
    }

    // Sort filtered groups
    val sortedGroups = remember(filteredGroups, rulesList, sortOrder) {
        when (sortOrder) {
            GroupSortOrder.TIME_ASC -> filteredGroups.sortedByDescending { it.id }
            GroupSortOrder.TIME_DESC -> filteredGroups.sortedBy { it.id }
            GroupSortOrder.NAME_ASC -> filteredGroups.sortedBy { it.name }
            GroupSortOrder.NAME_DESC -> filteredGroups.sortedByDescending { it.name }
            GroupSortOrder.COUNT_DESC -> filteredGroups.sortedByDescending { g ->
                rulesList.count { it.groupId == g.id }
            }
            GroupSortOrder.COUNT_ASC -> filteredGroups.sortedBy { g ->
                rulesList.count { it.groupId == g.id }
            }
        }
    }

    // Auto expand groups when searching
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            ruleGroupsList.forEach { group ->
                val hasMatchingRule = rulesList.any { rule ->
                    rule.groupId == group.id && (
                        rule.target.contains(searchQuery, ignoreCase = true) ||
                        rule.replacement.contains(searchQuery, ignoreCase = true)
                    )
                }
                if (hasMatchingRule) {
                    expandedGroups[group.id] = true
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Section: Title, Sort Dropdown & Compact Add Group Button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "规则",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Sort Button
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier
                                    .testTag("sort_groups_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "排序",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                GroupSortOrder.values().forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.label, style = MaterialTheme.typography.bodyMedium) },
                                        onClick = {
                                            viewModel.updateRuleSortOrder(order)
                                            showSortMenu = false
                                        },
                                        leadingIcon = {
                                            if (sortOrder == order) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "已选择",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Compact Add Group IconButton ("只显示一个+号")
                        IconButton(
                            onClick = {
                                editingGroup = null
                                groupNameInput = ""
                                groupReplacementInput = ""
                                showGroupDialog = true
                            },
                            modifier = Modifier
                                .testTag("add_group_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "新增分组",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // More options menu for built-in rules & clearing
                        Box {
                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier.testTag("more_rules_options_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "更多选项",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("清空所有规则", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMoreMenu = false
                                        showClearAllConfirmDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "清空",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Search Filter Text Field (Comfortable height)
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索分组或词条", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(16.dp)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("search_rules_input"),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                )
            }

            // Groups and Inner Rules Listing
            if (sortedGroups.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isEmpty()) "暂无规则分组，请点击右上角新增" else "没有找到匹配的分组或词条",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(sortedGroups, key = { it.id }) { group ->
                    val isExpanded = expandedGroups[group.id] ?: false
                    val arrowRotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f)

                    // Retrieve rules under this group (filtered by search if any)
                    val groupRules = rulesList.filter { rule ->
                        rule.groupId == group.id && (
                            searchQuery.isBlank() ||
                            rule.target.contains(searchQuery, ignoreCase = true) ||
                            rule.replacement.contains(searchQuery, ignoreCase = true) ||
                            group.name.contains(searchQuery, ignoreCase = true) ||
                            group.replacement.contains(searchQuery, ignoreCase = true)
                        )
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("group_card_${group.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                    ) {
                        Column {
                            // Group Title Row (clickable to collapse/expand - comfortably spacious heights & paddings)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedGroups[group.id] = !isExpanded }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = "Group",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (group.replacement.isNotBlank()) "${group.name} → ${group.replacement}" else group.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "(${groupRules.size})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Add rule inside this group
                                    IconButton(
                                        onClick = {
                                            targetGroupIdForRule = group.id
                                            editingRule = null
                                            ruleTargetInput = ""
                                            ruleReplacementInput = group.replacement
                                            showRuleDialog = true
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("add_rule_in_group_${group.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "在此分组新增规则",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Edit Group
                                    IconButton(
                                        onClick = {
                                            editingGroup = group
                                            groupNameInput = group.name
                                            groupReplacementInput = group.replacement
                                            showGroupDialog = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "编辑分组",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Delete Group
                                    IconButton(
                                        onClick = {
                                            viewModel.deleteRuleGroup(group.id)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "删除分组",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isExpanded) "收起" else "展开",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .rotate(arrowRotation)
                                    )
                                }
                            }

                            // Expandable rules list
                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                                        .padding(bottom = 6.dp)
                                ) {
                                    if (groupRules.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "该分组暂无规则，点击上方 '+' 图标添加词条",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        groupRules.forEach { rule ->
                                            Divider(
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                                                thickness = 0.5.dp
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                // Target -> Replacement Badge Box (comfortably spacious)
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(
                                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                                                RoundedCornerShape(4.dp)
                                                            )
                                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                                    ) {
                                                        Text(
                                                            text = rule.target,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.width(6.dp))

                                                    Icon(
                                                        imageVector = Icons.Default.ArrowForward,
                                                        contentDescription = "替换为",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(12.dp)
                                                    )

                                                    Spacer(modifier = Modifier.width(6.dp))

                                                    Box(
                                                        modifier = Modifier
                                                            .background(
                                                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
                                                                RoundedCornerShape(4.dp)
                                                            )
                                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                                    ) {
                                                        Text(
                                                            text = rule.replacement,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.tertiary
                                                        )
                                                    }
                                                }

                                                // Individual Rule toggle switches & actions
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Switch(
                                                        checked = rule.isEnabled,
                                                        onCheckedChange = { viewModel.toggleRuleEnabled(rule) },
                                                        modifier = Modifier.scale(0.8f)
                                                    )

                                                    IconButton(
                                                        onClick = {
                                                            targetGroupIdForRule = group.id
                                                            editingRule = rule
                                                            ruleTargetInput = rule.target
                                                            ruleReplacementInput = rule.replacement
                                                            showRuleDialog = true
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Edit,
                                                            contentDescription = "编辑规则",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            viewModel.deleteRule(rule.id)
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "删除规则",
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(16.dp)
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
                }
            }
        }
    }

    // Dialog: Add / Edit Group
    if (showGroupDialog) {
        AlertDialog(
            onDismissRequest = { showGroupDialog = false },
            title = {
                Text(
                    text = if (editingGroup == null) "添加规则分组" else "修改规则分组",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "分组用于归类管理，例如：多音字字头归类。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = groupNameInput,
                        onValueChange = { groupNameInput = it },
                        label = { Text("分组字 / 目标字 (如：重)") },
                        modifier = Modifier.fillMaxWidth().testTag("group_name_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = groupReplacementInput,
                        onValueChange = { groupReplacementInput = it },
                        label = { Text("替代音字 (如：众，可选)") },
                        modifier = Modifier.fillMaxWidth().testTag("group_replacement_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = groupNameInput.trim()
                        val replacement = groupReplacementInput.trim()
                        if (name.isEmpty()) {
                            Toast.makeText(context, "分组字不能为空", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val group = editingGroup
                        if (group == null) {
                            viewModel.addRuleGroup(name, replacement)
                        } else {
                            viewModel.updateRuleGroup(group.id, name, replacement)
                        }
                        showGroupDialog = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("save_group_button")
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGroupDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Dialog: Add / Edit Rule
    if (showRuleDialog) {
        AlertDialog(
            onDismissRequest = { showRuleDialog = false },
            title = {
                Text(
                    text = if (editingRule == null) "添加发音规则" else "修改发音规则",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = ruleTargetInput,
                        onValueChange = { ruleTargetInput = it },
                        label = { Text("匹配词 / 正则表达式") },
                        placeholder = { Text("例如：重心 或 (\\b行\\b)") },
                        modifier = Modifier.fillMaxWidth().testTag("rule_target_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = ruleReplacementInput,
                        onValueChange = { ruleReplacementInput = it },
                        label = { Text("替换为") },
                        placeholder = { Text("例如：众心 或 形") },
                        modifier = Modifier.fillMaxWidth().testTag("rule_replacement_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = ruleTargetInput.trim()
                        val replacement = ruleReplacementInput.trim()
                        val groupId = targetGroupIdForRule

                        if (target.isEmpty() || replacement.isEmpty()) {
                            Toast.makeText(context, "输入不能为空", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        // Verify valid regex
                        try {
                            Regex(target)
                        } catch (e: Exception) {
                            Toast.makeText(context, "正则表达式无效: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            return@Button
                        }

                        val rule = editingRule
                        if (rule == null) {
                            if (groupId != null) {
                                viewModel.addRule(
                                    groupId = groupId,
                                    target = target,
                                    replacement = replacement,
                                    matchWord = "",
                                    isForwardMatch = true
                                )
                            }
                        } else {
                            viewModel.updateRule(
                                ruleId = rule.id,
                                target = target,
                                replacement = replacement
                            )
                        }
                        showRuleDialog = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("save_rule_button")
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRuleDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Dialog: Confirm Clear All Rules
    if (showClearAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirmDialog = false },
            title = {
                Text(
                    text = "确认清空",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "确定要清空所有发音规则和分组吗？此操作无法撤销。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧：确认按钮（警示色，改名为确认，移到左边防止误触）
                    Button(
                        onClick = {
                            viewModel.clearAllRules()
                            showClearAllConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("确认")
                    }

                    // 右侧：取消按钮（安全选项，移到右边）
                    Button(
                        onClick = { showClearAllConfirmDialog = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("取消")
                    }
                }
            }
        )
    }
}
