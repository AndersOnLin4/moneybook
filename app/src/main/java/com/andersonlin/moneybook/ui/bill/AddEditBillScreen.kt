package com.andersonlin.moneybook.ui.bill

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import coil.compose.AsyncImage
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andersonlin.moneybook.MainActivity
import com.andersonlin.moneybook.MoneyBookApp
import com.andersonlin.moneybook.data.model.Bill
import com.andersonlin.moneybook.ui.AppViewModelProvider
import com.andersonlin.moneybook.util.fullDateLabel

/** 从任意 Context 向上找 Activity */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditBillScreen(
    billId: Long,
    defaultType: Int,
    initialDate: Long = 0L,
    onBack: () -> Unit,
    viewModel: AddEditBillViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val amountFocusRequester = remember { FocusRequester() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val appContext = context.applicationContext
    val activity = remember(context) { context.findActivity() as? MainActivity }

    LaunchedEffect(billId) { viewModel.init(billId, defaultType, initialDate) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AddEditEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                AddEditEvent.Saved -> {
                    (appContext as? MoneyBookApp)?.requestWidgetUpdate()
                    onBack()
                }
                AddEditEvent.SavedContinue -> {
                    (appContext as? MoneyBookApp)?.requestWidgetUpdate()
                    snackbarHostState.showSnackbar("已保存，继续记下一笔")
                    amountFocusRequester.requestFocus()
                }
                AddEditEvent.Deleted -> {
                    (appContext as? MoneyBookApp)?.requestWidgetUpdate()
                    onBack()
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEdit) "编辑账单" else "记一笔") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.isEdit) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            // 支出 / 收入切换
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                SegmentedButton(
                    selected = state.type == Bill.TYPE_EXPENSE,
                    onClick = { viewModel.setType(Bill.TYPE_EXPENSE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("支出") }
                )
                SegmentedButton(
                    selected = state.type == Bill.TYPE_INCOME,
                    onClick = { viewModel.setType(Bill.TYPE_INCOME) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("收入") }
                )
            }

            Spacer(Modifier.height(16.dp))

            // 金额
            OutlinedTextField(
                value = state.amountText,
                onValueChange = viewModel::setAmount,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(amountFocusRequester),
                label = { Text("金额") },
                prefix = { Text("¥") },
                isError = state.amountError,
                supportingText = if (state.amountError) {
                    { Text("请输入有效金额") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.headlineSmall,
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))

            // 分类
            Text("分类", style = MaterialTheme.typography.titleSmall)
            if (state.categories.isEmpty()) {
                Text(
                    text = "暂无分类，请先到「设置-分类管理」添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.categories.forEach { category ->
                        FilterChip(
                            selected = state.categoryId == category.id,
                            onClick = { viewModel.setCategory(category.id) },
                            label = { Text("${category.icon} ${category.name}") }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 账户
            Text("账户", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.accounts.forEach { account ->
                    FilterChip(
                        selected = state.accountId == account.id,
                        onClick = { viewModel.setAccount(account.id) },
                        label = { Text("${account.icon} ${account.name}") }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 备注
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备注（可选）") },
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))

            // 日期
            Surface(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = fullDateLabel(state.dateEpochDay),
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

            Spacer(Modifier.height(16.dp))

            // 附件
            Text("附件（可选）", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        activity?.pickImage { uri ->
                            if (uri != null) {
                                // 查询真实 MIME（content:// 图库 Uri 无扩展名）
                                val mime = runCatching {
                                    context.contentResolver.getType(uri)
                                }.getOrNull() ?: "image/*"
                                viewModel.setAttachment(uri.toString(), mime)
                            }
                        }
                    },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("截图 / 相册")
                }
                OutlinedButton(
                    onClick = {
                        activity?.pickAttachment { uri ->
                            if (uri != null) {
                                val mime = runCatching {
                                    context.contentResolver.getType(uri)
                                }.getOrNull() ?: "application/octet-stream"
                                viewModel.setAttachment(uri.toString(), mime)
                            }
                        }
                    },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("附件")
                }
            }

            if (state.hasAttachment) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.attachmentIsImage) {
                            AsyncImage(
                                model = Uri.parse(state.attachmentUri),
                                contentDescription = "附件图片",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Icon(
                                Icons.Filled.InsertDriveFile,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = state.attachmentUri
                                ?.substringAfterLast('/')
                                ?.takeIf { it.isNotBlank() } ?: "附件",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = viewModel::clearAttachment) {
                            Icon(Icons.Filled.Close, contentDescription = "移除附件")
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (state.isEdit) {
                Button(
                    onClick = { viewModel.save(andContinue = false) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text("保存", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                // 连续记账：继续记（留在本页）/ 记完了（返回）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.save(andContinue = true) },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(26.dp)
                    ) {
                        Text("继续记", style = MaterialTheme.typography.titleMedium)
                    }
                    Button(
                        onClick = { viewModel.save(andContinue = false) },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(26.dp)
                    ) {
                        Text("记完了", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.dateEpochDay * 86_400_000L
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        viewModel.setDate(millis / 86_400_000L)
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

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除账单") },
            text = { Text("确定要删除这笔账单吗？删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}
