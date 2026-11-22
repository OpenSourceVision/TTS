package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.miuix.MiuixActionButton
import com.example.ui.miuix.MiuixButtonVariant
import com.example.ui.miuix.MiuixCard
import com.example.ui.miuix.MiuixDialog
import com.example.ui.miuix.MiuixDropdownMenu
import com.example.ui.miuix.MiuixDropdownMenuItem
import com.example.ui.miuix.MiuixHairlineDivider
import com.example.ui.miuix.MiuixIconButton
import com.example.ui.miuix.MiuixLargeHeader
import com.example.ui.miuix.MiuixSearchBar
import com.example.ui.miuix.MiuixSwitch
import com.example.ui.miuix.MiuixTextField
import com.example.ui.theme.MiuixTheme
import com.example.viewmodel.GroupSortOrder
import com.example.viewmodel.TtsViewModel

@Composable
fun RulesScreen(
    viewModel: TtsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = MiuixTheme.colors
    val rulesList by viewModel.rulesState.collectAsState()
    val ruleGroupsList by viewModel.ruleGroupsState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val sortOrder by viewModel.ruleSortOrder.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showClearAllConfirmDialog by remember { mutableStateOf(false) }
    var showResetDefaultConfirmDialog by remember { mutableStateOf(false) }

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
    var userModifiedReplacement by remember { mutableStateOf(false) }

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

    // 搜索时，分组默认折叠
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            expandedGroups.clear()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 14.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Section
            item {
                MiuixLargeHeader(
                    title = "规则",
                    actions = {
                        Box {
                            MiuixIconButton(
                                onClick = { showSortMenu = true },
                                icon = Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = "排序",
                                tint = colors.textPrimary,
                                modifier = Modifier.testTag("sort_groups_button")
                            )

                            MiuixDropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                GroupSortOrder.values().forEach { order ->
                                    MiuixDropdownMenuItem(
                                        text = order.label,
                                        selected = sortOrder == order,
                                        onClick = {
                                            viewModel.updateRuleSortOrder(order)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        MiuixIconButton(
                            onClick = {
                                editingGroup = null
                                groupNameInput = ""
                                groupReplacementInput = ""
                                showGroupDialog = true
                            },
                            icon = Icons.Rounded.Add,
                            contentDescription = "新增分组",
                            tint = colors.primary,
                            modifier = Modifier.testTag("add_group_button")
                        )

                        Box {
                            MiuixIconButton(
                                onClick = { showMoreMenu = true },
                                icon = Icons.Rounded.MoreHoriz,
                                contentDescription = "更多选项",
                                tint = colors.textPrimary,
                                modifier = Modifier.testTag("more_rules_options_button")
                            )

                            MiuixDropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                MiuixDropdownMenuItem(
                                    text = "恢复默认规则",
                                    leadingIcon = Icons.Rounded.Refresh,
                                    onClick = {
                                        showMoreMenu = false
                                        showResetDefaultConfirmDialog = true
                                    }
                                )
                                MiuixDropdownMenuItem(
                                    text = "清空所有规则",
                                    leadingIcon = Icons.Rounded.DeleteOutline,
                                    isDestructive = true,
                                    onClick = {
                                        showMoreMenu = false
                                        showClearAllConfirmDialog = true
                                    }
                                )
                            }
                        }
                    }
                )
            }

            // Miuix Pill Search Bar
            item {
                MiuixSearchBar(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "搜索",
                    modifier = Modifier.testTag("search_rules_input")
                )
            }

            // Groups and Inner Rules Listing
            if (sortedGroups.isEmpty()) {
                item {
                    MiuixCard {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isEmpty()) "暂无规则" else "无匹配结果",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            } else {
                items(sortedGroups, key = { it.id }) { group ->
                    val isExpanded = expandedGroups[group.id] ?: false
                    val arrowRotation by animateFloatAsState(
                        targetValue = if (isExpanded) 180f else 0f,
                        label = "groupArrow"
                    )

                    val groupRules = rulesList.filter { rule ->
                        rule.groupId == group.id && (
                            searchQuery.isBlank() ||
                                rule.target.contains(searchQuery, ignoreCase = true) ||
                                rule.replacement.contains(searchQuery, ignoreCase = true) ||
                                group.name.contains(searchQuery, ignoreCase = true) ||
                                group.replacement.contains(searchQuery, ignoreCase = true)
                            )
                    }

                    MiuixCard(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .testTag("group_card_${group.id}")
                    ) {
                        Column {
                            // Group Header Row
                            var showGroupMenu by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedGroups[group.id] = !isExpanded }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Folder,
                                        contentDescription = "Group",
                                        tint = colors.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = formatGroupTitle(group),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(colors.surfaceSecondary)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${groupRules.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.textSecondary
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            targetGroupIdForRule = group.id
                                            editingRule = null
                                            ruleTargetInput = ""
                                            ruleReplacementInput = ""
                                            userModifiedReplacement = false
                                            showRuleDialog = true
                                        },
                                        modifier = Modifier
                                            .size(34.dp)
                                            .testTag("add_rule_in_group_${group.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = "在此分组新增规则",
                                            tint = colors.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Box {
                                        IconButton(
                                            onClick = { showGroupMenu = true },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.MoreHoriz,
                                                contentDescription = "分组操作",
                                                tint = colors.textSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        MiuixDropdownMenu(
                                            expanded = showGroupMenu,
                                            onDismissRequest = { showGroupMenu = false }
                                        ) {
                                            MiuixDropdownMenuItem(
                                                text = "编辑分组",
                                                leadingIcon = Icons.Rounded.Edit,
                                                onClick = {
                                                    showGroupMenu = false
                                                    editingGroup = group
                                                    groupNameInput = group.name
                                                    groupReplacementInput = group.replacement
                                                    showGroupDialog = true
                                                }
                                            )
                                            MiuixDropdownMenuItem(
                                                text = "删除分组",
                                                leadingIcon = Icons.Rounded.DeleteOutline,
                                                isDestructive = true,
                                                onClick = {
                                                    showGroupMenu = false
                                                    viewModel.deleteRuleGroup(group.id)
                                                }
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = if (isExpanded) "收起" else "展开",
                                        tint = colors.textTertiary,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .rotate(arrowRotation)
                                    )
                                }
                            }

                            // Expandable Rules List
                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                ) {
                                    if (groupRules.isEmpty()) {
                                        MiuixHairlineDivider(insetStart = 14.dp, insetEnd = 14.dp)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "暂无规则",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.textSecondary
                                            )
                                        }
                                    } else {
                                        groupRules.forEach { rule ->
                                            MiuixHairlineDivider(insetStart = 14.dp, insetEnd = 14.dp)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(7.dp))
                                                            .background(colors.primaryContainer)
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = rule.target,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = colors.onPrimaryContainer
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.width(6.dp))

                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                                        contentDescription = "替换为",
                                                        tint = colors.textTertiary,
                                                        modifier = Modifier.size(13.dp)
                                                    )

                                                    Spacer(modifier = Modifier.width(6.dp))

                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(7.dp))
                                                            .background(colors.successContainer)
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = rule.replacement,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = colors.success
                                                        )
                                                    }
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    MiuixSwitch(
                                                        checked = rule.isEnabled,
                                                        onCheckedChange = { viewModel.toggleRuleEnabled(rule) },
                                                        modifier = Modifier.scale(0.82f)
                                                    )

                                                    IconButton(
                                                        onClick = {
                                                            targetGroupIdForRule = group.id
                                                            editingRule = rule
                                                            ruleTargetInput = rule.target
                                                            ruleReplacementInput = rule.replacement
                                                            userModifiedReplacement = true
                                                            showRuleDialog = true
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Edit,
                                                            contentDescription = "编辑规则",
                                                            tint = colors.textSecondary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = { viewModel.deleteRule(rule.id) },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.DeleteOutline,
                                                            contentDescription = "删除规则",
                                                            tint = colors.error,
                                                            modifier = Modifier.size(17.dp)
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
        MiuixDialog(
            onDismissRequest = { showGroupDialog = false },
            title = if (editingGroup == null) "新建分组" else "编辑分组",
            content = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MiuixTextField(
                        value = groupNameInput,
                        onValueChange = { groupNameInput = it },
                        label = "分组名",
                        placeholder = "如：重-虫",
                        singleLine = true,
                        modifier = Modifier.testTag("group_name_input")
                    )

                    MiuixTextField(
                        value = groupReplacementInput,
                        onValueChange = { groupReplacementInput = it },
                        label = "替代词（可选）",
                        placeholder = "如：虫",
                        singleLine = true,
                        modifier = Modifier.testTag("group_replacement_input")
                    )
                }
            },
            buttons = {
                MiuixActionButton(
                    text = "取消",
                    onClick = { showGroupDialog = false },
                    variant = MiuixButtonVariant.Secondary,
                    modifier = Modifier.weight(1f)
                )
                MiuixActionButton(
                    text = "保存",
                    onClick = {
                        val name = groupNameInput.trim()
                        val replacement = groupReplacementInput.trim()
                        if (name.isEmpty()) {
                            Toast.makeText(context, "分组字不能为空", Toast.LENGTH_SHORT).show()
                            return@MiuixActionButton
                        }

                        val group = editingGroup
                        if (group == null) {
                            viewModel.addRuleGroup(name, replacement)
                        } else {
                            viewModel.updateRuleGroup(group.id, name, replacement)
                        }
                        showGroupDialog = false
                    },
                    variant = MiuixButtonVariant.Primary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_group_button")
                )
            }
        )
    }

    // Dialog: Add / Edit Rule
    if (showRuleDialog) {
        val currentGroupForRule = remember(targetGroupIdForRule, ruleGroupsList) {
            ruleGroupsList.firstOrNull { it.id == targetGroupIdForRule }
        }
        val groupMapping = remember(currentGroupForRule) {
            extractGroupMapping(currentGroupForRule)
        }

        val trimmedTarget = ruleTargetInput.trim()
        val currentEditingRule = editingRule
        val duplicateRule = remember(trimmedTarget, rulesList, currentEditingRule) {
            if (trimmedTarget.isEmpty()) null
            else {
                rulesList.firstOrNull { rule ->
                    (currentEditingRule == null || rule.id != currentEditingRule.id) &&
                        rule.target.trim().equals(trimmedTarget, ignoreCase = true)
                }
            }
        }
        val duplicateGroupName = remember(duplicateRule, ruleGroupsList) {
            duplicateRule?.let { rule ->
                val g = ruleGroupsList.firstOrNull { it.id == rule.groupId }
                if (g != null) formatGroupTitle(g) else "默认分组"
            } ?: ""
        }

        MiuixDialog(
            onDismissRequest = { showRuleDialog = false },
            title = if (editingRule == null) "添加发音规则" else "修改发音规则",
            content = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (currentGroupForRule != null) {
                        val groupTitle = formatGroupTitle(currentGroupForRule)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.primaryContainer.copy(alpha = 0.7f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "分组: $groupTitle",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    MiuixTextField(
                        value = ruleTargetInput,
                        onValueChange = { newTarget ->
                            ruleTargetInput = newTarget
                            if (groupMapping != null && !userModifiedReplacement) {
                                val smartRepl = computeSmartReplacement(newTarget, groupMapping)
                                if (smartRepl != null) {
                                    ruleReplacementInput = smartRepl
                                } else if (newTarget.isEmpty()) {
                                    ruleReplacementInput = ""
                                }
                            }
                        },
                        label = "匹配词",
                        placeholder = "如：一重",
                        isError = duplicateRule != null,
                        singleLine = false,
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.testTag("rule_target_input")
                    )

                    AnimatedVisibility(visible = duplicateRule != null) {
                        duplicateRule?.let { dup ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.errorContainer)
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = "重复警告",
                                    tint = colors.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "「${dup.target}」已在「$duplicateGroupName」中存在",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.error
                                )
                            }
                        }
                    }

                    MiuixTextField(
                        value = ruleReplacementInput,
                        onValueChange = { newRepl ->
                            ruleReplacementInput = newRepl
                            userModifiedReplacement = newRepl.isNotBlank()
                        },
                        label = "替换为",
                        placeholder = "如：一虫",
                        singleLine = true,
                        trailingIcon = {
                            val smartRepl = if (groupMapping != null && ruleTargetInput.isNotBlank()) {
                                computeSmartReplacement(ruleTargetInput, groupMapping)
                            } else null

                            if (smartRepl != null) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoFixHigh,
                                    contentDescription = "自动补齐",
                                    tint = colors.primary,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable {
                                            ruleReplacementInput = smartRepl
                                            userModifiedReplacement = false
                                        }
                                )
                            }
                        },
                        modifier = Modifier.testTag("rule_replacement_input")
                    )
                }
            },
            buttons = {
                MiuixActionButton(
                    text = "取消",
                    onClick = { showRuleDialog = false },
                    variant = MiuixButtonVariant.Secondary,
                    modifier = Modifier.weight(1f)
                )
                MiuixActionButton(
                    text = "保存",
                    onClick = {
                        val target = ruleTargetInput.trim()
                        val replacement = ruleReplacementInput.trim()
                        val groupId = targetGroupIdForRule

                        if (target.isEmpty() || replacement.isEmpty()) {
                            Toast.makeText(context, "输入不能为空", Toast.LENGTH_SHORT).show()
                            return@MiuixActionButton
                        }

                        if (duplicateRule != null) {
                            Toast.makeText(
                                context,
                                "匹配词「$target」已在「$duplicateGroupName」中存在，请勿重复添加！",
                                Toast.LENGTH_LONG
                            ).show()
                            return@MiuixActionButton
                        }

                        try {
                            Regex(target)
                        } catch (e: Exception) {
                            Toast.makeText(context, "正则表达式无效: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            return@MiuixActionButton
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
                    enabled = duplicateRule == null,
                    variant = MiuixButtonVariant.Primary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_rule_button")
                )
            }
        )
    }

    // Dialog: Confirm Clear All Rules
    if (showClearAllConfirmDialog) {
        MiuixDialog(
            onDismissRequest = { showClearAllConfirmDialog = false },
            title = "清空规则",
            content = {
                Text(
                    text = "确定清空所有分组与规则？不可撤销。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            },
            buttons = {
                // 左侧：确认按钮（警示色，防止误触）
                MiuixActionButton(
                    text = "确认",
                    onClick = {
                        viewModel.clearAllRules()
                        showClearAllConfirmDialog = false
                    },
                    variant = MiuixButtonVariant.Destructive,
                    modifier = Modifier.weight(1f)
                )
                // 右侧：取消按钮（安全选项）
                MiuixActionButton(
                    text = "取消",
                    onClick = { showClearAllConfirmDialog = false },
                    variant = MiuixButtonVariant.Primary,
                    modifier = Modifier.weight(1f)
                )
            }
        )
    }

    // Dialog: Confirm Reset Default Rules
    if (showResetDefaultConfirmDialog) {
        MiuixDialog(
            onDismissRequest = { showResetDefaultConfirmDialog = false },
            title = "恢复默认",
            content = {
                Text(
                    text = "原有规则将被默认模板覆盖。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            },
            buttons = {
                MiuixActionButton(
                    text = "取消",
                    onClick = { showResetDefaultConfirmDialog = false },
                    variant = MiuixButtonVariant.Secondary,
                    modifier = Modifier.weight(1f)
                )
                MiuixActionButton(
                    text = "恢复",
                    onClick = {
                        viewModel.resetToDefaultTemplate()
                        showResetDefaultConfirmDialog = false
                    },
                    variant = MiuixButtonVariant.Primary,
                    modifier = Modifier.weight(1f)
                )
            }
        )
    }
}

/**
 * 格式化分组标题：
 * 若组名本身已经包含连接符（如 "重-虫"、"长-尝"、"重—众"），则直接显示组名，避免出现 "长-尝 → 尝" 等冗余显示；
 * 仅当组名未含连接符且填写了替代音字段（如 name="重", replacement="虫"）时，才显示 "重 → 虫"。
 */
internal fun formatGroupTitle(group: RuleGroupEntity): String {
    val name = group.name.trim()
    val repl = group.replacement.trim()
    val splitRegex = Regex("""[—―–\-\->→:：/～~转]+""")
    if (name.contains(splitRegex)) {
        return name
    }
    return if (repl.isNotBlank()) "$name → $repl" else name
}

/**
 * 从规则分组中提取「源字符/词」到「替换字符/词」的映射对
 */
internal fun extractGroupMapping(group: RuleGroupEntity?): Pair<String, String>? {
    if (group == null) return null
    val rawName = group.name.trim()
    val rawRepl = group.replacement.trim()

    val splitRegex = Regex("""[—―–\-\->→:：/～~转]+""")
    val parts = rawName.split(splitRegex).map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size >= 2) {
        val src = parts[0]
        val dst = if (rawRepl.isNotEmpty()) rawRepl else parts[1]
        return Pair(src, dst)
    }

    if (rawName.isNotEmpty() && rawRepl.isNotEmpty()) {
        return Pair(rawName, rawRepl)
    }

    return null
}

/**
 * 智能推导替换词
 */
internal fun computeSmartReplacement(target: String, groupMapping: Pair<String, String>?): String? {
    if (groupMapping == null || target.isBlank()) return null
    val (src, dst) = groupMapping
    if (target.contains(src)) {
        return target.replace(src, dst)
    }
    if (src.length == dst.length && src.length > 1) {
        val diffIndices = src.indices.filter { src[it] != dst[it] }
        if (diffIndices.size == 1) {
            val charSrc = src[diffIndices[0]].toString()
            val charDst = dst[diffIndices[0]].toString()
            if (target.contains(charSrc)) {
                return target.replace(charSrc, charDst)
            }
        }
    }
    return null
}
