package com.andersonlin.moneybook.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.data.model.RecurringBill
import com.andersonlin.moneybook.ui.AppViewModelProvider
import com.andersonlin.moneybook.ui.components.CategoryIconBadge
import com.andersonlin.moneybook.ui.components.EmptyState
import com.andersonlin.moneybook.util.formatCents
import com.andersonlin.moneybook.util.fullDateLabel
import com.andersonlin.moneybook.util.toCents
import java.time.LocalDate

fun cycleLabel(cycle: Int): String = when (cycle) {
    RecurringBill.CYCLE_WEEKLY -> "每周"
    RecurringBill.CYCLE_MONTHLY -> "每月"
    RecurringBill.CYCLE_YEARLY -> "每年"
    else -> "每月"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    onBack: () -> Unit,
    viewModel: RecurringViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RecurringBill?>(null) }
    var deleting by remember { mutableStateOf<RecurringBill?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RecurringEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("周期账单") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editing = null
                        showEditor = true
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "添加周期账单")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item(key = "hint") {
                Text(
                    text = "设定固定收支（如每月房租），App 打开时自动补记到期账单。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            if (state.items.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Filled.DateRange,
                        title = "暂无周期账单",
                        subtitle = "点击右上角 + 添加"
                    )
                }
            } else {
                items(state.items, key = { it.id }) { item ->
                    RecurringRow(
                        item = item,
                        category = state.categories[item.categoryId],
                        account = state.accounts[item.accountId],
                        ledgerName = state.ledgers[item.ledgerId]?.name,
                        onToggle = { enabled -> viewModel.toggleEnabled(item, enabled) },
                        onClick = {
                            editing = item
                            showEditor = true
                        },
                        onDelete = { deleting = item }
                    )
                }
            }
        }
    }

    if (showEditor) {
        RecurringEditorDialog(
            editing = editing,
            categories = state.categories.values.toList(),
            accounts = state.accounts.values.toList(),
            ledgers = state.ledgers.values.toList(),
            onDismiss = { showEditor = false },
            onConfirm = { result ->
                if (result.id > 0L) {
                    viewModel.update(result)
                } else {
                    viewModel.add(result)
                }
                showEditor = false
            }
        )
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除周期账单") },
            text = { Text("确定删除这条周期账单吗？已生成的账单不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(item)
                    deleting = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun RecurringRow(
    item: RecurringBill,
    category: com.andersonlin.moneybook.data.model.Category?,
    account: com.andersonlin.moneybook.data.model.Account?,
    ledgerName: String?,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIconBadge(category?.icon ?: "❓", size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = (category?.name ?: "未知分类") + " · " + cycleLabel(item.cycle),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                val subtitle = listOfNotNull(
                    ledgerName,
                    account?.name,
                    item.note.takeIf { it.isNotBlank() },
                    "开始 " + fullDateLabel(item.startEpochDay)
                ).joinToString(" · ")
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                text = (if (item.type == Bill.TYPE_EXPENSE) "-" else "+") + formatCents(item.amountCents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (item.type == Bill.TYPE_EXPENSE) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Switch(
                checked = item.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.padding(start = 4.dp)
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecurringEditorDialog(
    editing: RecurringBill?,
    categories: List<com.andersonlin.moneybook.data.model.Category>,
    accounts: List<com.andersonlin.moneybook.data.model.Account>,
    ledgers: List<com.andersonlin.moneybook.data.model.Ledger>,
    onDismiss: () -> Unit,
    onConfirm: (RecurringBill) -> Unit
) {
    var type by remember { mutableStateOf(editing?.type ?: Bill.TYPE_EXPENSE) }
    var amountText by remember {
        mutableStateOf(editing?.let { formatCents(it.amountCents).replace(",", "") } ?: "")
    }
    var categoryId by remember { mutableStateOf(editing?.categoryId) }
    var accountId by remember { mutableStateOf(editing?.accountId ?: Bill.DEFAULT_ACCOUNT_ID) }
    var ledgerId by remember { mutableStateOf(editing?.ledgerId ?: 0L) }
    var note by remember { mutableStateOf(editing?.note ?: "") }
    var cycle by remember { mutableStateOf(editing?.cycle ?: RecurringBill.CYCLE_MONTHLY) }
    var startEpochDay by remember { mutableStateOf(editing?.startEpochDay ?: LocalDate.now().toEpochDay()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val typeCategories = categories.filter { it.type == type }
    // 类型切换后自动选中第一个分类
    val effectiveCategoryId = if (typeCategories.any { it.id == categoryId }) {
        categoryId
    } else {
        typeCategories.firstOrNull()?.id
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "添加周期账单" else "编辑周期账单") },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp)
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = type == Bill.TYPE_EXPENSE,
                        onClick = { type = Bill.TYPE_EXPENSE },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        label = { Text("支出") }
                    )
                    SegmentedButton(
                        selected = type == Bill.TYPE_INCOME,
                        onClick = { type = Bill.TYPE_INCOME },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        label = { Text("收入") }
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { text ->
                        if (text.isBlank() || text.matches(Regex("^\\d{0,7}(\\.\\d{0,2})?$"))) {
                            amountText = text
                        }
                    },
                    label = { Text("金额") },
                    prefix = { Text("¥") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Text("分类", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    typeCategories.forEach { cat ->
                        FilterChip(
                            selected = effectiveCategoryId == cat.id,
                            onClick = { categoryId = cat.id },
                            label = { Text("${cat.icon} ${cat.name}") }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("账户", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    accounts.forEach { acc ->
                        FilterChip(
                            selected = accountId == acc.id,
                            onClick = { accountId = acc.id },
                            label = { Text("${acc.icon} ${acc.name}") }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("账本（未选择则使用当前账本）", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    ledgers.forEach { ledger ->
                        FilterChip(
                            selected = ledgerId == ledger.id,
                            onClick = { ledgerId = ledger.id },
                            label = { Text("${ledger.icon} ${ledger.name}") }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("重复周期", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    listOf(
                        RecurringBill.CYCLE_WEEKLY to "每周",
                        RecurringBill.CYCLE_MONTHLY to "每月",
                        RecurringBill.CYCLE_YEARLY to "每年"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = cycle == value,
                            onClick = { cycle = value },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(30) },
                    label = { Text("备注（可选）") },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "开始日期 " + fullDateLabel(startEpochDay),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = "选择日期",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "首个账单将在开始日期的下一个周期自动生成",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = amountText.toCents() ?: return@TextButton
                val catId = effectiveCategoryId ?: return@TextButton
                onConfirm(
                    RecurringBill(
                        id = editing?.id ?: 0L,
                        type = type,
                        amountCents = cents,
                        categoryId = catId,
                        accountId = accountId,
                        // 新增未选择时 0 = 当前活动账本（由 ViewModel 填充）
                        ledgerId = ledgerId,
                        note = note.trim(),
                        cycle = cycle,
                        startEpochDay = startEpochDay,
                        lastGeneratedEpochDay = editing?.lastGeneratedEpochDay ?: startEpochDay,
                        enabled = editing?.enabled ?: true
                    )
                )
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startEpochDay * 86_400_000L
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        startEpochDay = millis / 86_400_000L
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
